package com.projectsuperhuman.next

import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrudyVoiceService(
    val config: TrudyVoiceConfig,
    private val engine: TrudySpeechEngine,
    private val audioSink: TrudyAudioSink
) {
    private val speakMutex = Mutex()
    private val stopEpoch = AtomicLong(0L)
    @Volatile private var state = TrudyVoiceRuntimeState.READY

    suspend fun isAvailable(): Boolean = runCatching { engine.isAvailable() }.getOrDefault(false)
    fun runtimeState(): TrudyVoiceRuntimeState = state

    /** Explicit first-use preparation. Heavy Kokoro initialization remains off app startup. */
    suspend fun prepare() {
        state = TrudyVoiceRuntimeState.LOADING
        try {
            engine.prepare()
            state = TrudyVoiceRuntimeState.READY
        } catch (failure: Throwable) {
            state = TrudyVoiceRuntimeState.ERROR
            throw failure
        }
    }

    suspend fun speak(
        text: String,
        onSynthesisComplete: (TrudySpeechDiagnostics) -> Unit = {}
    ): TrudySpeechDiagnostics {
        require(text.isNotBlank())
        val requestEpoch = stopEpoch.get()
        return speakMutex.withLock {
            if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech request was stopped before it started")
            audioSink.stop()
            state = TrudyVoiceRuntimeState.LOADING
            try {
                var firstAudio = true
                val diagnostics = engine.synthesizeStreaming(
                    TrudySpeechRequest(text = text, voiceId = config.voiceId, speed = config.speed)
                ) { chunk ->
                    if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                    if (firstAudio) {
                        firstAudio = false
                        state = TrudyVoiceRuntimeState.SPEAKING
                        onSynthesisComplete(chunk.diagnostics)
                    }
                    audioSink.play(chunk.audio)
                    if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                }
                if (firstAudio) onSynthesisComplete(diagnostics)
                state = TrudyVoiceRuntimeState.READY
                diagnostics
            } catch (cancelled: CancellationException) {
                state = TrudyVoiceRuntimeState.READY
                throw cancelled
            } catch (failure: Throwable) {
                state = TrudyVoiceRuntimeState.ERROR
                throw failure
            }
        }
    }

    fun stop() {
        stopEpoch.incrementAndGet()
        engine.cancelCurrent()
        audioSink.stop()
        state = TrudyVoiceRuntimeState.READY
    }

    suspend fun close() {
        stop()
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
    val diagnostics: TrudyVoiceRuntimeDiagnostics,
    val modelManager: TrudyVoiceModelManager? = null
)

/**
 * Voice composition root. Construction is lightweight: it never initializes sherpa-onnx and never
 * downloads a model. Explicit installation is exposed separately through [TrudyVoiceModelManager].
 */
object TrudyVoiceRuntimeFactory {
    suspend fun create(
        config: TrudyVoiceConfig,
        modelStore: KokoroModelStore? = null,
        kokoroBackend: KokoroInferenceBackend? = null,
        audioSink: TrudyAudioSink = AndroidTrudyAudioSink(),
        modelManager: TrudyVoiceModelManager? = modelStore as? TrudyVoiceModelManager
    ): TrudyVoiceRuntime {
        if (config.mode == TrudyVoiceMode.OFF) {
            return unavailable(config, "Voice is disabled.", modelManager)
        }
        if (modelStore == null || kokoroBackend == null) {
            return unavailable(config, "Kokoro runtime backend is not installed yet.", modelManager)
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
            ),
            modelManager = modelManager
        )
    }

    /** Production Android path. Does not load native libraries or touch the network. */
    suspend fun createAndroid(
        context: Context,
        config: TrudyVoiceConfig
    ): TrudyVoiceRuntime {
        if (config.mode == TrudyVoiceMode.OFF) return unavailable(config, "Voice is disabled.")
        val store = AndroidKokoroModelStore(context)
        return create(
            config = config,
            modelStore = store,
            kokoroBackend = SherpaKokoroInferenceBackend(),
            audioSink = AndroidTrudyAudioSink(),
            modelManager = store
        )
    }

    private fun unavailable(
        config: TrudyVoiceConfig,
        reason: String,
        modelManager: TrudyVoiceModelManager? = null
    ) = TrudyVoiceRuntime(
        service = null,
        diagnostics = TrudyVoiceRuntimeDiagnostics(
            requestedMode = config.mode,
            activeMode = TrudyVoiceMode.OFF,
            engineId = null,
            modelId = config.modelId,
            voiceId = config.voiceId,
            available = false,
            reason = reason
        ),
        modelManager = modelManager
    )
}
