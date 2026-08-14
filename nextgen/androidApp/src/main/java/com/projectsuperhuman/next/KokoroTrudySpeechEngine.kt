package com.projectsuperhuman.next

import java.text.Normalizer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

data class KokoroModelFiles(
    val modelPath: String,
    val voicesPath: String? = null,
    val tokenizerPath: String? = null,
    val phonemizerDataDir: String? = null,
    val lexiconPath: String? = null,
    val modelVersion: String? = null,
    val modelBytesOnDisk: Long? = null
)

interface KokoroModelStore {
    suspend fun isInstalled(modelId: String): Boolean
    suspend fun resolve(modelId: String): KokoroModelFiles
}

data class KokoroInferenceRequest(val text: String, val voiceId: String, val speed: Float)
data class KokoroInferenceOutput(val samples: FloatArray, val sampleRateHz: Int = Kokoro82MModelContract.SAMPLE_RATE_HZ)

interface KokoroInferenceBackend {
    val backendId: String
    suspend fun initialize(files: KokoroModelFiles)
    suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput
    fun cancelCurrent() {}
    suspend fun close()
}

interface KokoroTextFrontend {
    val phonemizerId: String
    fun prepare(raw: String): String
    fun chunks(prepared: String): List<String>
}

class SherpaEspeakKokoroTextFrontend(
    firstChunkChars: Int = 36,
    laterChunkChars: Int = 64,
    maxChunkChars: Int? = null
) : KokoroTextFrontend {
    private val effectiveFirstChunkChars = maxChunkChars?.let { minOf(firstChunkChars, it) } ?: firstChunkChars
    private val effectiveLaterChunkChars = maxChunkChars ?: laterChunkChars

    init {
        require(effectiveFirstChunkChars >= 28)
        require(effectiveLaterChunkChars >= effectiveFirstChunkChars)
    }

    override val phonemizerId: String = "sherpa-espeak-ng"
    override fun prepare(raw: String): String = TrudySpeechText.prepare(raw)
    override fun chunks(prepared: String): List<String> = KokoroTextChunker.progressiveChunk(
        prepared,
        effectiveFirstChunkChars,
        effectiveLaterChunkChars
    )
}

/**
 * Conversational chunk planner. It prefers complete sentences, then clauses, then word boundaries.
 * The first two chunks ramp up so first audio is quick without handing the native runtime a giant
 * second chunk that cannot finish before playback catches it.
 */
object KokoroTextChunker {
    private val whitespace = Regex("\\s+")

    fun chunk(text: String, maxChars: Int = 240): List<String> {
        require(maxChars >= 28)
        return plan(text, firstMaxChars = maxChars, secondMaxChars = maxChars, laterMaxChars = maxChars)
    }

    fun progressiveChunk(
        text: String,
        firstChunkChars: Int = 36,
        laterChunkChars: Int = 64
    ): List<String> {
        require(firstChunkChars >= 28)
        require(laterChunkChars >= firstChunkChars)
        val secondChunkChars = ((firstChunkChars + laterChunkChars) / 2)
            .coerceIn(firstChunkChars, laterChunkChars)
        return plan(text, firstChunkChars, secondChunkChars, laterChunkChars)
    }

    private fun plan(
        text: String,
        firstMaxChars: Int,
        secondMaxChars: Int,
        laterMaxChars: Int
    ): List<String> {
        val normalized = text.replace(whitespace, " ").trim()
        if (normalized.isEmpty()) return emptyList()

        val output = mutableListOf<String>()
        var start = 0
        var index = 0
        while (start < normalized.length) {
            val maxChars = when (index) {
                0 -> firstMaxChars
                1 -> secondMaxChars
                else -> laterMaxChars
            }
            val targetChars = when (index) {
                0 -> maxChars
                1 -> (maxChars * 9 / 10).coerceAtLeast(firstMaxChars)
                else -> (maxChars * 7 / 8).coerceAtLeast(secondMaxChars)
            }
            val minimumChars = when (index) {
                0 -> minOf(24, maxChars)
                1 -> minOf(30, maxChars)
                else -> minOf(36, maxChars)
            }
            val cut = chooseCut(normalized, start, minimumChars, targetChars, maxChars)
            val piece = normalized.substring(start, cut).trim()
            if (piece.isNotEmpty()) output += piece
            start = cut
            while (start < normalized.length && normalized[start].isWhitespace()) start++
            index++
        }
        return output
    }

    private fun chooseCut(
        text: String,
        start: Int,
        minimumChars: Int,
        targetChars: Int,
        maxChars: Int
    ): Int {
        val remaining = text.length - start
        if (remaining <= maxChars) return text.length

        val minimumEnd = (start + minimumChars).coerceAtMost(text.lastIndex)
        val maximumEnd = (start + maxChars).coerceAtMost(text.lastIndex)
        var bestCut = -1
        var bestScore = Int.MIN_VALUE
        for (cut in minimumEnd..maximumEnd) {
            if (!text[cut].isWhitespace()) continue
            val prior = text[cut - 1]
            val punctuationScore = when (prior) {
                '.', '!', '?' -> 300
                ';', ':' -> 220
                ',' -> 140
                else -> 0
            }
            val length = cut - start
            val score = punctuationScore + 100 - abs(length - targetChars)
            if (score > bestScore || score == bestScore && cut > bestCut) {
                bestScore = score
                bestCut = cut
            }
        }
        if (bestCut >= 0) return bestCut

        // A single over-limit token is kept intact. Splitting a word damages pronunciation more
        // than allowing this rare chunk to exceed the soft character limit.
        val nextSpace = text.indexOf(' ', start + maxChars)
        return if (nextSpace >= 0) nextSpace else text.length
    }
}

object KokoroAudioSanitizer {
    fun sanitize(samples: FloatArray): FloatArray = FloatArray(samples.size) { index ->
        when (val value = samples[index]) {
            Float.POSITIVE_INFINITY -> 1f
            Float.NEGATIVE_INFINITY -> -1f
            else -> if (value.isNaN()) 0f else value.coerceIn(-1f, 1f)
        }
    }
}

class KokoroTrudySpeechEngine(
    private val config: TrudyVoiceConfig,
    private val modelStore: KokoroModelStore,
    private val backend: KokoroInferenceBackend,
    private val textFrontend: KokoroTextFrontend = SherpaEspeakKokoroTextFrontend(),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : TrudySpeechEngine {
    override val engineId: String = "kokoro-local:${backend.backendId}"
    private val initMutex = Mutex()
    private val synthesisMutex = Mutex()
    private val cancellationEpoch = AtomicLong(0L)
    @Volatile private var initialized = false
    @Volatile private var modelLoadDurationMs: Long? = null
    @Volatile private var modelBytesOnDisk: Long? = null

    override suspend fun isAvailable(): Boolean =
        config.mode == TrudyVoiceMode.KOKORO_LOCAL && modelStore.isInstalled(config.modelId)

    override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult {
        val pieces = mutableListOf<FloatArray>()
        var diagnostics: TrudySpeechDiagnostics? = null
        synthesizeStreaming(request) { result ->
            pieces += result.audio.samples
            diagnostics = result.diagnostics
        }
        val totalSize = pieces.sumOf { it.size }
        require(totalSize > 0) { "Kokoro returned empty audio" }
        val joined = FloatArray(totalSize)
        var offset = 0
        pieces.forEach { piece ->
            piece.copyInto(joined, offset)
            offset += piece.size
        }
        val finalDiagnostics = requireNotNull(diagnostics)
        return TrudySpeechResult(TrudyPcmAudio(joined, finalDiagnostics.sampleRateHz), finalDiagnostics)
    }

    override suspend fun synthesizeStreaming(
        request: TrudySpeechRequest,
        onChunk: suspend (TrudySpeechResult) -> Unit
    ): TrudySpeechDiagnostics = synthesizeStreamingInternal(request, onBeforeChunk = {}, onChunk = onChunk)

    override suspend fun synthesizeStreaming(
        request: TrudySpeechRequest,
        onBeforeChunk: suspend (index: Int) -> Unit,
        onChunk: suspend (TrudySpeechResult) -> Unit
    ): TrudySpeechDiagnostics = synthesizeStreamingInternal(request, onBeforeChunk, onChunk)

    private suspend fun synthesizeStreamingInternal(
        request: TrudySpeechRequest,
        onBeforeChunk: suspend (index: Int) -> Unit,
        onChunk: suspend (TrudySpeechResult) -> Unit
    ): TrudySpeechDiagnostics = synthesisMutex.withLock {
        require(config.mode == TrudyVoiceMode.KOKORO_LOCAL) { "Kokoro local voice is disabled" }
        val requestEpoch = cancellationEpoch.get()
        ensureNotCancelled(requestEpoch)
        ensureInitialized()
        ensureNotCancelled(requestEpoch)
        val prepared = textFrontend.prepare(request.text)
        val chunks = textFrontend.chunks(prepared)
        require(chunks.isNotEmpty()) { "No speakable text remains after preprocessing" }
        DeveloperDiagnostics.log(
            "voice.stream.plan_created",
            "chars=${prepared.length} chunks=${chunks.size} firstChars=${chunks.first().length} " +
                "secondChars=${chunks.getOrNull(1)?.length ?: 0} maxChars=${chunks.maxOf { it.length }}"
        )

        var synthesisMs = 0L
        var generatedSamples = 0L
        var sampleRate = Kokoro82MModelContract.SAMPLE_RATE_HZ
        var completedChunks = 0
        try {
            for ((index, chunk) in chunks.withIndex()) {
                currentCoroutineContext().ensureActive()
                ensureNotCancelled(requestEpoch)
                onBeforeChunk(index)
                currentCoroutineContext().ensureActive()
                ensureNotCancelled(requestEpoch)
                DeveloperDiagnostics.log(
                    if (index == 0) "voice.stream.first_chunk_synthesis_start" else "voice.stream.chunk_synthesis_start",
                    "index=$index chars=${chunk.length}"
                )
                val inferenceStarted = nowMs()
                val output = backend.synthesize(KokoroInferenceRequest(chunk, request.voiceId, request.speed))
                val chunkMs = (nowMs() - inferenceStarted).coerceAtLeast(0L)
                synthesisMs += chunkMs
                currentCoroutineContext().ensureActive()
                ensureNotCancelled(requestEpoch)
                require(output.samples.isNotEmpty()) { "Kokoro returned empty audio" }
                require(output.sampleRateHz == Kokoro82MModelContract.SAMPLE_RATE_HZ) {
                    "Unexpected Kokoro sample rate: ${output.sampleRateHz}"
                }
                sampleRate = output.sampleRateHz
                val safeSamples = KokoroAudioSanitizer.sanitize(output.samples)
                val chunkAudioMs = safeSamples.size * 1000L / sampleRate
                generatedSamples += safeSamples.size.toLong()
                val cumulativeAudioMs = generatedSamples * 1000L / sampleRate
                DeveloperDiagnostics.log(
                    if (index == 0) "voice.stream.first_chunk_ready" else "voice.stream.chunk_ready",
                    "index=$index chars=${chunk.length} synthesisMs=$chunkMs audioMs=$chunkAudioMs"
                )
                onChunk(
                    TrudySpeechResult(
                        TrudyPcmAudio(safeSamples, sampleRate),
                        diagnostics(request, sampleRate, synthesisMs, cumulativeAudioMs)
                    )
                )
                completedChunks++
                ensureNotCancelled(requestEpoch)
            }
        } catch (cancelled: CancellationException) {
            DeveloperDiagnostics.log(
                "voice.stream.synthesis_cancelled",
                "completedChunks=$completedChunks totalChunks=${chunks.size} synthesisMs=$synthesisMs"
            )
            throw cancelled
        }
        val audioMs = generatedSamples * 1000L / sampleRate
        val finalDiagnostics = diagnostics(request, sampleRate, synthesisMs, audioMs)
        DeveloperDiagnostics.log(
            "voice.stream.synthesis_complete",
            "chunks=${chunks.size} synthesisMs=$synthesisMs audioMs=$audioMs " +
                "rtf=${finalDiagnostics.realTimeFactor ?: -1.0}"
        )
        finalDiagnostics
    }

    private fun diagnostics(
        request: TrudySpeechRequest,
        sampleRate: Int,
        synthesisMs: Long,
        audioMs: Long
    ) = TrudySpeechDiagnostics(
        engineId = engineId,
        modelId = config.modelId,
        voiceId = request.voiceId,
        sampleRateHz = sampleRate,
        synthesisDurationMs = synthesisMs,
        modelLoadDurationMs = modelLoadDurationMs,
        generatedAudioDurationMs = audioMs,
        realTimeFactor = if (audioMs > 0L) synthesisMs.toDouble() / audioMs.toDouble() else null,
        modelBytesOnDisk = modelBytesOnDisk
    )

    private fun ensureNotCancelled(requestEpoch: Long) {
        if (cancellationEpoch.get() != requestEpoch) {
            throw CancellationException("Kokoro speech stopped")
        }
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return@withLock
            require(modelStore.isInstalled(config.modelId)) { "Kokoro model is not installed" }
            val files = modelStore.resolve(config.modelId)
            val started = nowMs()
            try {
                backend.initialize(files)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (oom: OutOfMemoryError) {
                runCatching { backend.close() }
                throw IllegalStateException("Kokoro could not allocate enough memory", oom)
            } catch (failure: Throwable) {
                runCatching { backend.close() }
                throw failure
            }
            modelLoadDurationMs = (nowMs() - started).coerceAtLeast(0L)
            modelBytesOnDisk = files.modelBytesOnDisk
            initialized = true
        }
    }

    override fun cancelCurrent() {
        cancellationEpoch.incrementAndGet()
        backend.cancelCurrent()
    }

    override suspend fun close() {
        cancelCurrent()
        synthesisMutex.withLock {
            initMutex.withLock {
                if (initialized) backend.close()
                initialized = false
                modelLoadDurationMs = null
                modelBytesOnDisk = null
            }
        }
    }
}

object TrudySpeechText {
    private const val MAX_CHARS = 12_000

    fun prepare(raw: String): String {
        val cleaned = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?i)metric\\(s\\)"), "metrics")
            .replace(Regex("(?i)feature\\(s\\)"), "features")
            .replace(Regex("(?i)insight\\(s\\)"), "insights")
            .replace(Regex("[_/]"), " ")
            .replace(Regex("\\b([A-Z][A-Z0-9]{2,}(?:\\s+[A-Z0-9]{2,})*)\\b")) {
                it.value.lowercase().replaceFirstChar(Char::uppercase)
            }
            .replace(Regex("[*#>]"), " ")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length <= MAX_CHARS) return cleaned
        val prefix = cleaned.take(MAX_CHARS)
        return prefix.substringBeforeLast(' ', prefix).trim()
    }
}
