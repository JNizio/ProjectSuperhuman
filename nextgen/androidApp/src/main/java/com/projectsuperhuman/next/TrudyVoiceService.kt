package com.projectsuperhuman.next

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrudyVoiceService(
    val config: TrudyVoiceConfig,
    private val engine: TrudySpeechEngine,
    private val audioSink: TrudyAudioSink
) {
    private val speakMutex = Mutex()

    suspend fun isAvailable(): Boolean = runCatching { engine.isAvailable() }.getOrDefault(false)

    /** Explicit first-use preparation hook; heavy engine initialization stays off the UI thread. */
    suspend fun prepare() = engine.prepare()

    suspend fun speak(
        text: String,
        onSynthesisComplete: (TrudySpeechDiagnostics) -> Unit = {}
    ): TrudySpeechDiagnostics = speakMutex.withLock {
        require(text.isNotBlank())
        audioSink.stop()
        val result = engine.synthesize(
            TrudySpeechRequest(text = text, voiceId = config.voiceId, speed = config.speed)
        )
        onSynthesisComplete(result.diagnostics)
        audioSink.play(result.audio)
        result.diagnostics
    }

    fun stop() = audioSink.stop()

    suspend fun close() {
        audioSink.close()
        engine.close()
    }
}

data class TrudyVoiceRuntimeDiagnostics(
    val requestedMode: TrudyVoiceMode,
    val activeMode: TrudyVoiceMode,
    val engineId: String?,
    val modelId: String?,
    val voiceId: String?,
    val available: Boolean,
    val reason: String? = null
)

data class TrudyVoiceRuntime(
    val service: TrudyVoiceService?,
    val diagnostics: TrudyVoiceRuntimeDiagnostics
)

/**
 * Voice composition root. Kokoro remains optional and lazy: without an installed backend/model,
 * Trudy text mode continues unchanged.
 */
object TrudyVoiceRuntimeFactory {
    suspend fun create(
        config: TrudyVoiceConfig,
        modelStore: KokoroModelStore? = null,
        kokoroBackend: KokoroInferenceBackend? = null,
        audioSink: TrudyAudioSink = AndroidTrudyAudioSink()
    ): TrudyVoiceRuntime {
        if (config.mode == TrudyVoiceMode.OFF) {
            return unavailable(config, "Voice is disabled.")
        }
        if (modelStore == null || kokoroBackend == null) {
            return unavailable(config, "Kokoro runtime backend is not installed yet.")
        }

        val engine = KokoroTrudySpeechEngine(config, modelStore, kokoroBackend)
        val service = TrudyVoiceService(config, engine, audioSink)
        val available = service.isAvailable()
        return TrudyVoiceRuntime(
            service = service.takeIf { available },
            diagnostics = TrudyVoiceRuntimeDiagnostics(
                requestedMode = config.mode,
                activeMode = if (available) TrudyVoiceMode.KOKORO_LOCAL else TrudyVoiceMode.OFF,
                engineId = if (available) engine.engineId else null,
                modelId = config.modelId,
                voiceId = config.voiceId,
                available = available,
                reason = if (available) null else "Kokoro model is not installed."
            )
        )
    }

    private fun unavailable(config: TrudyVoiceConfig, reason: String) = TrudyVoiceRuntime(
        service = null,
        diagnostics = TrudyVoiceRuntimeDiagnostics(
            requestedMode = config.mode,
            activeMode = TrudyVoiceMode.OFF,
            engineId = null,
            modelId = config.modelId,
            voiceId = config.voiceId,
            available = false,
            reason = reason
        )
    )
}
