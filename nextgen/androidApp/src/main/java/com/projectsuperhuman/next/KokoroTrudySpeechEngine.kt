package com.projectsuperhuman.next

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Model files live in app-private storage and are supplied by a model-store implementation. */
data class KokoroModelFiles(
    val modelPath: String,
    val voicesPath: String? = null,
    val tokenizerPath: String? = null
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

/** Runtime-specific implementation point (for example ONNX Runtime). */
interface KokoroInferenceBackend {
    val backendId: String
    suspend fun initialize(files: KokoroModelFiles)
    suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput
    suspend fun close()
}

/**
 * Lazy local Kokoro adapter. Model/runtime initialization happens on first synthesis, not app launch.
 * This keeps Trudy startup and normal UI frame work independent from the TTS model.
 */
class KokoroTrudySpeechEngine(
    private val config: TrudyVoiceConfig,
    private val modelStore: KokoroModelStore,
    private val backend: KokoroInferenceBackend,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : TrudySpeechEngine {
    override val engineId: String = "kokoro-local:${backend.backendId}"
    private val initMutex = Mutex()
    @Volatile private var initialized = false

    override suspend fun isAvailable(): Boolean =
        config.mode == TrudyVoiceMode.KOKORO_LOCAL && modelStore.isInstalled(config.modelId)

    override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult {
        require(config.mode == TrudyVoiceMode.KOKORO_LOCAL) { "Kokoro local voice is disabled" }
        ensureInitialized()
        val started = nowMs()
        val output = backend.synthesize(
            KokoroInferenceRequest(
                text = TrudySpeechText.prepare(request.text),
                voiceId = request.voiceId,
                speed = request.speed
            )
        )
        require(output.samples.isNotEmpty()) { "Kokoro returned empty audio" }
        require(output.sampleRateHz > 0) { "Kokoro returned invalid sample rate" }
        return TrudySpeechResult(
            audio = TrudyPcmAudio(output.samples, output.sampleRateHz),
            diagnostics = TrudySpeechDiagnostics(
                engineId = engineId,
                modelId = config.modelId,
                voiceId = request.voiceId,
                sampleRateHz = output.sampleRateHz,
                synthesisDurationMs = (nowMs() - started).coerceAtLeast(0L)
            )
        )
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return@withLock
            require(modelStore.isInstalled(config.modelId)) { "Kokoro model is not installed" }
            backend.initialize(modelStore.resolve(config.modelId))
            initialized = true
        }
    }

    override suspend fun close() {
        initMutex.withLock {
            if (initialized) backend.close()
            initialized = false
        }
    }
}

object TrudySpeechText {
    private const val MAX_CHARS = 4_000

    fun prepare(raw: String): String = raw
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_#>]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_CHARS)
}
