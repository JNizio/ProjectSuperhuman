package com.projectsuperhuman.next

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class TrudyVoiceService(
    val config: TrudyVoiceConfig,
    private val engine: TrudySpeechEngine,
    private val audioSink: TrudyAudioSink,
    private val lookaheadChunks: Int = 2,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class QueuedSpeechChunk(val index: Int, val result: TrudySpeechResult)

    init { require(lookaheadChunks in 1..2) { "Voice lookahead must stay bounded to one or two chunks" } }

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
            if (requestEpoch != stopEpoch.get()) {
                throw CancellationException("Speech request was stopped before it started")
            }
            audioSink.stop()
            state = TrudyVoiceRuntimeState.LOADING
            val pipelineStartedMs = nowMs()
            DeveloperDiagnostics.log(
                "voice.stream.pipeline_started",
                "chars=${text.length} lookahead=$lookaheadChunks epoch=$requestEpoch"
            )
            try {
                coroutineScope {
                    val playbackQueue = Channel<QueuedSpeechChunk>(capacity = lookaheadChunks)
                    val lookaheadPermits = Semaphore(lookaheadChunks)
                    val queueDepth = AtomicInteger(0)
                    val synthesisFinished = AtomicBoolean(false)
                    var nextSynthesisIndex = 0
                    var nextPlaybackIndex = 0
                    var starvationCount = 0
                    var firstAudio = true
                    var finalDiagnostics: TrudySpeechDiagnostics? = null

                    val producerJob = launch {
                        try {
                            finalDiagnostics = engine.synthesizeStreaming(
                                request = TrudySpeechRequest(text = text, voiceId = config.voiceId, speed = config.speed),
                                onBeforeChunk = { index ->
                                    lookaheadPermits.acquire()
                                    DeveloperDiagnostics.log(
                                        "voice.stream.lookahead_reserved",
                                        "index=$index permits=$lookaheadChunks"
                                    )
                                }
                            ) { chunk ->
                                if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                                val queued = QueuedSpeechChunk(nextSynthesisIndex++, chunk)
                                queueDepth.incrementAndGet()
                                try {
                                    playbackQueue.send(queued)
                                } catch (failure: Throwable) {
                                    queueDepth.decrementAndGet()
                                    lookaheadPermits.release()
                                    throw failure
                                }
                                DeveloperDiagnostics.log(
                                    "voice.stream.queue_depth",
                                    "event=enqueue index=${queued.index} depth=${queueDepth.get().coerceAtMost(lookaheadChunks)} " +
                                        "capacity=$lookaheadChunks"
                                )
                            }
                        } finally {
                            synthesisFinished.set(true)
                            playbackQueue.close()
                        }
                    }

                    try {
                        while (true) {
                            val queued = receiveNextChunk(
                                queue = playbackQueue,
                                synthesisFinished = synthesisFinished,
                                playbackStarted = !firstAudio,
                                queueDepth = queueDepth
                            ) { bufferedMs ->
                                starvationCount++
                                DeveloperDiagnostics.log(
                                    "voice.stream.playback_starvation",
                                    "nextIndex=$nextPlaybackIndex bufferedMs=$bufferedMs " +
                                        "queueDepth=${queueDepth.get().coerceAtLeast(0)}"
                                )
                            } ?: break

                            val depthAfterDequeue = queueDepth.decrementAndGet().coerceAtLeast(0)
                            lookaheadPermits.release()
                            DeveloperDiagnostics.log(
                                "voice.stream.queue_depth",
                                "event=dequeue index=${queued.index} depth=$depthAfterDequeue capacity=$lookaheadChunks"
                            )
                            if (queued.index != nextPlaybackIndex) {
                                error("Voice chunk order violation: expected $nextPlaybackIndex, got ${queued.index}")
                            }
                            if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                            if (firstAudio) {
                                firstAudio = false
                                state = TrudyVoiceRuntimeState.SPEAKING
                                onSynthesisComplete(queued.result.diagnostics)
                                DeveloperDiagnostics.log(
                                    "voice.stream.first_playback_start",
                                    "index=${queued.index} latencyMs=${(nowMs() - pipelineStartedMs).coerceAtLeast(0L)}"
                                )
                            }
                            audioSink.play(queued.result.audio)
                            nextPlaybackIndex++
                            if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                        }
                        producerJob.join()
                        audioSink.drain()
                        if (requestEpoch != stopEpoch.get()) throw CancellationException("Speech stopped")
                    } finally {
                        if (!producerJob.isCompleted) producerJob.cancel()
                        playbackQueue.cancel()
                    }

                    val diagnostics = requireNotNull(finalDiagnostics)
                    if (firstAudio) onSynthesisComplete(diagnostics)
                    state = TrudyVoiceRuntimeState.READY
                    DeveloperDiagnostics.log(
                        "voice.stream.speech_complete",
                        "chunks=$nextPlaybackIndex synthesisMs=${diagnostics.synthesisDurationMs ?: -1} " +
                            "audioMs=${diagnostics.generatedAudioDurationMs ?: -1} " +
                            "wallMs=${(nowMs() - pipelineStartedMs).coerceAtLeast(0L)} starvations=$starvationCount"
                    )
                    diagnostics
                }
            } catch (cancelled: CancellationException) {
                engine.cancelCurrent()
                audioSink.stop()
                state = TrudyVoiceRuntimeState.READY
                DeveloperDiagnostics.log(
                    "voice.stream.cancelled",
                    "requestEpoch=$requestEpoch stopEpoch=${stopEpoch.get()}"
                )
                throw cancelled
            } catch (failure: Throwable) {
                engine.cancelCurrent()
                audioSink.stop()
                state = TrudyVoiceRuntimeState.ERROR
                DeveloperDiagnostics.log(
                    "voice.stream.failed",
                    "type=${failure::class.simpleName} message=${failure.message.orEmpty()}"
                )
                throw failure
            }
        }
    }

    private suspend fun receiveNextChunk(
        queue: Channel<QueuedSpeechChunk>,
        synthesisFinished: AtomicBoolean,
        playbackStarted: Boolean,
        queueDepth: AtomicInteger,
        onStarvation: (bufferedMs: Long) -> Unit
    ): QueuedSpeechChunk? {
        val immediate = queue.tryReceive()
        if (immediate.isSuccess) return immediate.getOrNull()
        if (immediate.isClosed) return null
        if (!playbackStarted || synthesisFinished.get()) return queue.receiveCatching().getOrNull()

        val bufferedMs = audioSink.bufferedAudioDurationMs().coerceAtLeast(0L)
        if (bufferedMs == 0L) {
            if (!synthesisFinished.get()) onStarvation(0L)
            return queue.receiveCatching().getOrNull()
        }

        val beforePlaybackDrains = withTimeoutOrNull(bufferedMs.coerceAtLeast(1L)) {
            queue.receiveCatching()
        }
        if (beforePlaybackDrains != null) return beforePlaybackDrains.getOrNull()
        if (!synthesisFinished.get() && queueDepth.get() == 0) onStarvation(bufferedMs)
        return queue.receiveCatching().getOrNull()
    }

    fun stop() {
        val epoch = stopEpoch.incrementAndGet()
        DeveloperDiagnostics.log("voice.stream.cancel_requested", "stopEpoch=$epoch")
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
        if (config.mode == TrudyVoiceMode.OFF) {
            return unavailable(config, "Voice is disabled.", modelManager)
        }
        if (modelStore == null || kokoroBackend == null) {
            return unavailable(config, "Local voice runtime is not installed yet.", modelManager)
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
