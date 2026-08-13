package com.projectsuperhuman.next

import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
                coroutineScope {
                    val playbackQueue = Channel<TrudySpeechResult>(capacity = 2)
                    var firstAudio = true
                    var finalDiagnostics: TrudySpeechDiagnostics? = null

                    val playbackJob = launch {
                        for (chunk in playbackQueue) {
                            if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                            if (firstAudio) {
                                firstAudio = false
                                state = TrudyVoiceRuntimeState.SPEAKING
                                onSynthesisComplete(chunk.diagnostics)
                                DeveloperDiagnostics.log("voice.playback.first_audio")
                            }
                            audioSink.play(chunk.audio)
                            if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                        }
                    }

                    try {
                        finalDiagnostics = engine.synthesizeStreaming(
                            TrudySpeechRequest(text = text, voiceId = config.voiceId, speed = config.speed)
                        ) { chunk ->
                            if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                            playbackQueue.send(chunk)
                            DeveloperDiagnostics.log(
                                "voice.playback.chunk_queued",
                                "audioMs=${chunk.audio.samples.size * 1000L / chunk.audio.sampleRateHz}"
                            )
                        }
                    } finally {
                        playbackQueue.close()
                    }
                    playbackJob.join()

                    val diagnostics = requireNotNull(finalDiagnostics)
                    if (firstAudio) onSynthesisComplete(diagnostics)
                    state = TrudyVoiceRuntimeState.READY
                    DeveloperDiagnostics.log(
                        "voice.speech.complete",
                        "engine=${diagnostics.engineId} synthesisMs=${diagnostics.synthesisDurationMs ?: -1} audioMs=${diagnostics.generatedAudioDurationMs ?: -1}"
                    )
                    diagnostics
                }
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

object TrudyVoiceRuntimeFactory {
    suspend fun create(
        config: TrudyVoiceConfig,
        modelStore: KokoroModelStore? = null,
        kokoroBackend: KokoroInferenceBackend? = null,
        audioSink: TrudyAudioSink = AndroidTrudyAudioSink(),
        modelManager: TrudyVoiceModelManager? = modelStore as? TrudyVoiceModelManager
    ): TrudyVoiceRuntime {
        if (config.mode == TrudyVoiceMode.OFF) return unavailable(config, "Voice is disabled.", modelManager)
        if (modelStore == null || kokoroBackend == null) return unavailable(config, "Local voice runtime is not installed yet.", modelManager)
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
                reason = if (available) null else "Local voice model is not installed."
            ),
            modelManager = modelManager
        )
    }

    suspend fun createAndroid(context: Context, config: TrudyVoiceConfig): TrudyVoiceRuntime {
        if (config.mode == TrudyVoiceMode.OFF) return unavailable(config, "Voice is disabled.")
        val store = AndroidKittenModelStore(context)
        val activeConfig = config.copy(
            modelId = KittenAndroidDistribution.logicalModelId,
            voiceId = KittenVoiceCatalog.resolve(config.voiceId).id
        )
        return create(
            config = activeConfig,
            modelStore = store,
            kokoroBackend = SherpaKittenInferenceBackend(threads = 4),
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
