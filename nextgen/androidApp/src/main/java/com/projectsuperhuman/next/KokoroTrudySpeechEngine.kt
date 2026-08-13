package com.projectsuperhuman.next

import java.text.Normalizer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val firstChunkChars: Int = 36,
    private val laterChunkChars: Int = 180
) : KokoroTextFrontend {
    init { require(firstChunkChars >= 28); require(laterChunkChars >= firstChunkChars) }
    override val phonemizerId: String = "sherpa-espeak-ng"
    override fun prepare(raw: String): String = TrudySpeechText.prepare(raw)
    override fun chunks(prepared: String): List<String> = KokoroTextChunker.progressiveChunk(prepared, firstChunkChars, laterChunkChars)
}

object KokoroTextChunker {
    private val sentenceBoundary = Regex("(?<=[.!?;:])\\s+")

    fun chunk(text: String, maxChars: Int = 240): List<String> = chunkWithLimit(text, maxChars)

    fun progressiveChunk(text: String, firstChunkChars: Int = 36, laterChunkChars: Int = 180): List<String> {
        require(firstChunkChars >= 28)
        require(laterChunkChars >= firstChunkChars)
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= firstChunkChars) return listOf(normalized)

        val before = normalized.lastIndexOf(' ', firstChunkChars).takeIf { it > 0 }
        val after = normalized.indexOf(' ', firstChunkChars).takeIf { it > 0 }
        val splitAt = before ?: after ?: return listOf(normalized)
        val first = normalized.substring(0, splitAt).trim()
        val rest = normalized.substring(splitAt + 1).trim()
        return listOf(first) + chunkWithLimit(rest, laterChunkChars)
    }

    private fun chunkWithLimit(text: String, maxChars: Int): List<String> {
        require(maxChars >= 28)
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        val sentences = normalized.split(sentenceBoundary).filter { it.isNotBlank() }
        val output = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                output += current.toString().trim()
                current = StringBuilder()
            }
        }

        fun appendWordBounded(part: String) {
            val words = part.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            for (word in words) {
                if (word.length > maxChars) {
                    flush()
                    output += word
                    continue
                }
                val extra = if (current.isEmpty()) word.length else word.length + 1
                if (current.length + extra > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }

        for (sentence in sentences) {
            if (sentence.length > maxChars) {
                flush(); appendWordBounded(sentence); flush()
            } else {
                val extra = if (current.isEmpty()) sentence.length else sentence.length + 1
                if (current.length + extra > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        flush()
        return output
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

    override suspend fun isAvailable(): Boolean = config.mode == TrudyVoiceMode.KOKORO_LOCAL && modelStore.isInstalled(config.modelId)

    override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult {
        val pieces = mutableListOf<FloatArray>()
        var diagnostics: TrudySpeechDiagnostics? = null
        synthesizeStreaming(request) { result -> pieces += result.audio.samples; diagnostics = result.diagnostics }
        val totalSize = pieces.sumOf { it.size }
        require(totalSize > 0) { "Kokoro returned empty audio" }
        val joined = FloatArray(totalSize)
        var offset = 0
        pieces.forEach { piece -> piece.copyInto(joined, offset); offset += piece.size }
        val finalDiagnostics = requireNotNull(diagnostics)
        return TrudySpeechResult(TrudyPcmAudio(joined, finalDiagnostics.sampleRateHz), finalDiagnostics)
    }

    override suspend fun synthesizeStreaming(request: TrudySpeechRequest, onChunk: suspend (TrudySpeechResult) -> Unit): TrudySpeechDiagnostics = synthesisMutex.withLock {
        require(config.mode == TrudyVoiceMode.KOKORO_LOCAL) { "Kokoro local voice is disabled" }
        val requestEpoch = cancellationEpoch.get()
        ensureNotCancelled(requestEpoch)
        ensureInitialized()
        ensureNotCancelled(requestEpoch)
        val prepared = textFrontend.prepare(request.text)
        val chunks = textFrontend.chunks(prepared)
        require(chunks.isNotEmpty()) { "No speakable text remains after preprocessing" }
        DeveloperDiagnostics.log("kokoro.speech.plan", "chars=${prepared.length} chunks=${chunks.size} firstChars=${chunks.first().length}")

        var synthesisMs = 0L
        var generatedSamples = 0L
        var sampleRate = Kokoro82MModelContract.SAMPLE_RATE_HZ
        for ((index, chunk) in chunks.withIndex()) {
            currentCoroutineContext().ensureActive()
            ensureNotCancelled(requestEpoch)
            val inferenceStarted = nowMs()
            val output = backend.synthesize(KokoroInferenceRequest(chunk, request.voiceId, request.speed))
            val chunkMs = (nowMs() - inferenceStarted).coerceAtLeast(0L)
            synthesisMs += chunkMs
            DeveloperDiagnostics.log("kokoro.speech.chunk_ready", "index=$index chars=${chunk.length} synthesisMs=$chunkMs")
            currentCoroutineContext().ensureActive()
            ensureNotCancelled(requestEpoch)
            require(output.samples.isNotEmpty()) { "Kokoro returned empty audio" }
            require(output.sampleRateHz == Kokoro82MModelContract.SAMPLE_RATE_HZ) { "Unexpected Kokoro sample rate: ${output.sampleRateHz}" }
            sampleRate = output.sampleRateHz
            val safeSamples = KokoroAudioSanitizer.sanitize(output.samples)
            generatedSamples += safeSamples.size.toLong()
            val audioMs = generatedSamples * 1000L / sampleRate
            onChunk(TrudySpeechResult(TrudyPcmAudio(safeSamples, sampleRate), diagnostics(request, sampleRate, synthesisMs, audioMs)))
            ensureNotCancelled(requestEpoch)
        }
        val audioMs = generatedSamples * 1000L / sampleRate
        diagnostics(request, sampleRate, synthesisMs, audioMs)
    }

    private fun diagnostics(request: TrudySpeechRequest, sampleRate: Int, synthesisMs: Long, audioMs: Long) = TrudySpeechDiagnostics(
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
        if (cancellationEpoch.get() != requestEpoch) throw CancellationException("Kokoro speech stopped")
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

    override fun cancelCurrent() { cancellationEpoch.incrementAndGet(); backend.cancelCurrent() }

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
            .replace(Regex("\\b([A-Z][A-Z0-9]{2,}(?:\\s+[A-Z0-9]{2,})*)\\b")) { it.value.lowercase().replaceFirstChar(Char::uppercase) }
            .replace(Regex("[*#>]"), " ")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length <= MAX_CHARS) return cleaned
        val prefix = cleaned.take(MAX_CHARS)
        return prefix.substringBeforeLast(' ', prefix).trim()
    }
}
