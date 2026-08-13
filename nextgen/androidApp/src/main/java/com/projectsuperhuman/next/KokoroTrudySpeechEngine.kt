package com.projectsuperhuman.next

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Model files live in app-private storage and are supplied by a model-store implementation. */
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

data class KokoroInferenceRequest(
    val text: String,
    val voiceId: String,
    val speed: Float
)

data class KokoroInferenceOutput(
    val samples: FloatArray,
    val sampleRateHz: Int = 24_000
)

/** Runtime-specific implementation point. Sherpa/ONNX/JNI types stay behind this boundary. */
interface KokoroInferenceBackend {
    val backendId: String
    suspend fun initialize(files: KokoroModelFiles)
    suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput
    fun cancelCurrent() {}
    suspend fun close()
}

/**
 * Kokoro's raw graph consumes phoneme token IDs. The Android runtime intentionally delegates
 * English G2P/tokenization to sherpa-onnx's eSpeak-ng Kokoro frontend; this interface keeps
 * text normalization/chunking deterministic without pretending that character mapping is G2P.
 */
interface KokoroTextFrontend {
    val phonemizerId: String
    fun prepare(raw: String): String
    fun chunks(prepared: String): List<String>
}

class SherpaEspeakKokoroTextFrontend(
    private val maxChunkChars: Int = 240
) : KokoroTextFrontend {
    init { require(maxChunkChars >= 80) }
    override val phonemizerId: String = "sherpa-espeak-ng"

    override fun prepare(raw: String): String = TrudySpeechText.prepare(raw)

    override fun chunks(prepared: String): List<String> = KokoroTextChunker.chunk(prepared, maxChunkChars)
}

object KokoroTextChunker {
    private val sentenceBoundary = Regex("(?<=[.!?;:])\\s+")

    fun chunk(text: String, maxChars: Int = 240): List<String> {
        require(maxChars >= 40)
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
                require(word.length <= maxChars) { "A speech token exceeds the safe Kokoro chunk bound" }
                val extra = if (current.isEmpty()) word.length else word.length + 1
                if (current.length + extra > maxChars) flush()
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }

        for (sentence in sentences) {
            if (sentence.length > maxChars) {
                flush()
                appendWordBounded(sentence)
                flush()
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
        val value = samples[index]
        when {
            value.isNaN() -> 0f
            value == Float.POSITIVE_INFINITY -> 1f
            value == Float.NEGATIVE_INFINITY -> -1f
            else -> value.coerceIn(-1f, 1f)
        }
    }
}

/**
 * Lazy local Kokoro adapter. Model/runtime initialization happens on first synthesis, not app launch.
 * Initialization and synthesis are serialized so a single native session owns one generation at a time.
 */
class KokoroTrudySpeechEngine(
    private val config: TrudyVoiceConfig,
    private val modelStore: KokoroModelStore,
    private val backend: KokoroInferenceBackend,
    private val textFrontend: KokoroTextFrontend = SherpaEspeakKokoroTextFrontend(),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : TrudySpeechEngine {
    override val engineId: String = "kokoro-local:${backend.backendId}"
    private val initMutex = Mutex()
    private val synthesisMutex = Mutex()
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
        pieces.forEach { piece -> piece.copyInto(joined, offset); offset += piece.size }
        val finalDiagnostics = requireNotNull(diagnostics)
        return TrudySpeechResult(TrudyPcmAudio(joined, finalDiagnostics.sampleRateHz), finalDiagnostics)
    }

    override suspend fun synthesizeStreaming(
        request: TrudySpeechRequest,
        onChunk: suspend (TrudySpeechResult) -> Unit
    ): TrudySpeechDiagnostics = synthesisMutex.withLock {
        require(config.mode == TrudyVoiceMode.KOKORO_LOCAL) { "Kokoro local voice is disabled" }
        ensureInitialized()
        val prepared = textFrontend.prepare(request.text)
        val chunks = textFrontend.chunks(prepared)
        require(chunks.isNotEmpty()) { "No speakable text remains after preprocessing" }

        val started = nowMs()
        var generatedSamples = 0L
        var sampleRate = 24_000
        for (chunk in chunks) {
            currentCoroutineContext().ensureActive()
            val output = backend.synthesize(KokoroInferenceRequest(chunk, request.voiceId, request.speed))
            currentCoroutineContext().ensureActive()
            require(output.samples.isNotEmpty()) { "Kokoro returned empty audio" }
            require(output.sampleRateHz == 24_000) { "Unexpected Kokoro sample rate: ${output.sampleRateHz}" }
            sampleRate = output.sampleRateHz
            val safeSamples = KokoroAudioSanitizer.sanitize(output.samples)
            generatedSamples += safeSamples.size.toLong()
            val elapsed = (nowMs() - started).coerceAtLeast(0L)
            val audioMs = generatedSamples * 1000L / sampleRate
            onChunk(
                TrudySpeechResult(
                    TrudyPcmAudio(safeSamples, sampleRate),
                    diagnostics(request, sampleRate, elapsed, audioMs)
                )
            )
        }
        val elapsed = (nowMs() - started).coerceAtLeast(0L)
        val audioMs = generatedSamples * 1000L / sampleRate
        diagnostics(request, sampleRate, elapsed, audioMs)
    }

    private fun diagnostics(
        request: TrudySpeechRequest,
        sampleRate: Int,
        elapsedMs: Long,
        audioMs: Long
    ) = TrudySpeechDiagnostics(
        engineId = engineId,
        modelId = config.modelId,
        voiceId = request.voiceId,
        sampleRateHz = sampleRate,
        synthesisDurationMs = elapsedMs,
        modelLoadDurationMs = modelLoadDurationMs,
        generatedAudioDurationMs = audioMs,
        realTimeFactor = if (audioMs > 0L) elapsedMs.toDouble() / audioMs.toDouble() else null,
        modelBytesOnDisk = modelBytesOnDisk
    )

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

    override fun cancelCurrent() = backend.cancelCurrent()

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

    fun prepare(raw: String): String = raw
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_#>]"), " ")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_CHARS)
}
