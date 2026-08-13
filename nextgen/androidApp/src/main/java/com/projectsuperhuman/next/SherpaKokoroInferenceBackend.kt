package com.projectsuperhuman.next

import android.os.Build
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class KokoroVoiceDescriptor(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val speakerId: Int
)

object KokoroVoiceCatalog {
    val voices: List<KokoroVoiceDescriptor> = listOf(
        KokoroVoiceDescriptor("af_alloy", "Alloy", "en-US", 0),
        KokoroVoiceDescriptor("af_aoede", "Aoede", "en-US", 1),
        KokoroVoiceDescriptor("af_bella", "Bella", "en-US", 2),
        KokoroVoiceDescriptor("af_heart", "Heart", "en-US", 3),
        KokoroVoiceDescriptor("af_jessica", "Jessica", "en-US", 4),
        KokoroVoiceDescriptor("af_kore", "Kore", "en-US", 5),
        KokoroVoiceDescriptor("af_nicole", "Nicole", "en-US", 6),
        KokoroVoiceDescriptor("af_nova", "Nova", "en-US", 7),
        KokoroVoiceDescriptor("af_river", "River", "en-US", 8),
        KokoroVoiceDescriptor("af_sarah", "Sarah", "en-US", 9),
        KokoroVoiceDescriptor("af_sky", "Sky", "en-US", 10),
        KokoroVoiceDescriptor("am_adam", "Adam", "en-US", 11),
        KokoroVoiceDescriptor("am_echo", "Echo", "en-US", 12),
        KokoroVoiceDescriptor("am_eric", "Eric", "en-US", 13),
        KokoroVoiceDescriptor("am_fenrir", "Fenrir", "en-US", 14),
        KokoroVoiceDescriptor("am_liam", "Liam", "en-US", 15),
        KokoroVoiceDescriptor("am_michael", "Michael", "en-US", 16),
        KokoroVoiceDescriptor("am_onyx", "Onyx", "en-US", 17),
        KokoroVoiceDescriptor("am_puck", "Puck", "en-US", 18),
        KokoroVoiceDescriptor("am_santa", "Santa", "en-US", 19),
        KokoroVoiceDescriptor("bf_alice", "Alice", "en-GB", 20),
        KokoroVoiceDescriptor("bf_emma", "Emma", "en-GB", 21),
        KokoroVoiceDescriptor("bf_isabella", "Isabella", "en-GB", 22),
        KokoroVoiceDescriptor("bf_lily", "Lily", "en-GB", 23),
        KokoroVoiceDescriptor("bm_daniel", "Daniel", "en-GB", 24),
        KokoroVoiceDescriptor("bm_fable", "Fable", "en-GB", 25),
        KokoroVoiceDescriptor("bm_george", "George", "en-GB", 26),
        KokoroVoiceDescriptor("bm_lewis", "Lewis", "en-GB", 27)
    )

    fun requireVoice(id: String): KokoroVoiceDescriptor =
        voices.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unsupported Kokoro voice: $id")
}

class KokoroInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SherpaKokoroInferenceBackend(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
) : KokoroInferenceBackend {
    override val backendId: String = "sherpa-onnx-${KokoroAndroidRuntimeContract.SHERPA_ONNX_VERSION}"

    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private val cancellationEpoch = AtomicLong(0L)
    @Volatile private var tts: OfflineTts? = null

    override suspend fun initialize(files: KokoroModelFiles) = lifecycleMutex.withLock {
        if (tts != null) return@withLock
        DeveloperDiagnostics.log("kokoro.init.begin", "threads=$threads")
        validateAbi()
        val model = requireFile(files.modelPath, "model.onnx")
        val voices = requireFile(files.voicesPath, "voices.bin")
        val tokens = requireFile(files.tokenizerPath, "tokens.txt")
        val dataDir = requireDirectory(files.phonemizerDataDir, "espeak-ng-data")
        val lexicon = requireFile(files.lexiconPath, "English lexicon")
        DeveloperDiagnostics.log(
            "kokoro.init.assets_ok",
            "model=${model.length() / (1024L * 1024L)}MB voices=${voices.length() / (1024L * 1024L)}MB"
        )

        val created = try {
            DeveloperDiagnostics.log("kokoro.init.native_enter")
            withContext(dispatcher) {
                OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kokoro = OfflineTtsKokoroModelConfig(
                                model = model.absolutePath,
                                voices = voices.absolutePath,
                                tokens = tokens.absolutePath,
                                dataDir = dataDir.absolutePath,
                                lexicon = lexicon.absolutePath
                            ),
                            numThreads = threads,
                            debug = false,
                            provider = KokoroAndroidRuntimeContract.PROVIDER
                        ),
                        maxNumSentences = 1,
                        silenceScale = 0.2f
                    )
                )
            }.also { DeveloperDiagnostics.log("kokoro.init.native_returned") }
        } catch (oom: OutOfMemoryError) {
            DeveloperDiagnostics.log("kokoro.init.oom", oom.message)
            throw KokoroInferenceException("Kokoro native runtime could not allocate enough memory", oom)
        } catch (failure: Throwable) {
            DeveloperDiagnostics.log("kokoro.init.error", "${failure.javaClass.simpleName}: ${failure.message}")
            throw KokoroInferenceException("Kokoro native runtime initialization failed", failure)
        }

        try {
            val sampleRate = withContext(dispatcher) { created.sampleRate() }
            val speakers = withContext(dispatcher) { created.numSpeakers() }
            require(sampleRate == Kokoro82MModelContract.SAMPLE_RATE_HZ) { "Unexpected Kokoro sample rate: $sampleRate" }
            require(speakers == Kokoro82MModelContract.VOICE_COUNT) {
                "Kokoro voice table mismatch: expected ${Kokoro82MModelContract.VOICE_COUNT}, found $speakers"
            }
            tts = created
            DeveloperDiagnostics.log("kokoro.init.ready", "sampleRate=$sampleRate speakers=$speakers")
        } catch (failure: Throwable) {
            DeveloperDiagnostics.log("kokoro.init.metadata_error", "${failure.javaClass.simpleName}: ${failure.message}")
            runCatching { created.release() }
            throw KokoroInferenceException("Kokoro model metadata validation failed", failure)
        }
    }

    override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput = generationMutex.withLock {
        val runtime = tts ?: throw KokoroInferenceException("Kokoro runtime is not initialized")
        val voice = KokoroVoiceCatalog.requireVoice(request.voiceId)
        val requestEpoch = cancellationEpoch.get()
        DeveloperDiagnostics.log(
            "kokoro.synth.begin",
            "chars=${request.text.length} voice=${request.voiceId} speed=${request.speed} threads=$threads"
        )
        val generated = try {
            DeveloperDiagnostics.log("kokoro.synth.native_enter")
            withContext(dispatcher) {
                runtime.generateWithConfigAndCallback(
                    text = request.text,
                    config = GenerationConfig(
                        silenceScale = 0.2f,
                        speed = request.speed,
                        sid = voice.speakerId
                    )
                ) { _ -> if (cancellationEpoch.get() == requestEpoch) 1 else 0 }
            }.also { DeveloperDiagnostics.log("kokoro.synth.native_returned") }
        } catch (cancel: CancellationException) {
            DeveloperDiagnostics.log("kokoro.synth.cancelled")
            throw cancel
        } catch (oom: OutOfMemoryError) {
            DeveloperDiagnostics.log("kokoro.synth.oom", oom.message)
            throw KokoroInferenceException("Kokoro synthesis ran out of memory", oom)
        } catch (failure: Throwable) {
            DeveloperDiagnostics.log("kokoro.synth.error", "${failure.javaClass.simpleName}: ${failure.message}")
            if (cancellationEpoch.get() != requestEpoch) {
                val cancelledException = CancellationException("Kokoro synthesis cancelled")
                cancelledException.initCause(failure)
                throw cancelledException
            }
            throw KokoroInferenceException("Kokoro synthesis failed", failure)
        }
        if (cancellationEpoch.get() != requestEpoch) throw CancellationException("Kokoro synthesis cancelled")
        if (generated.samples.isEmpty() || generated.sampleRate <= 0) {
            DeveloperDiagnostics.log("kokoro.synth.malformed")
            throw KokoroInferenceException("Kokoro returned malformed or empty audio")
        }
        DeveloperDiagnostics.log(
            "kokoro.synth.complete",
            "samples=${generated.samples.size} rate=${generated.sampleRate}"
        )
        KokoroInferenceOutput(generated.samples, generated.sampleRate)
    }

    override fun cancelCurrent() {
        cancellationEpoch.incrementAndGet()
        DeveloperDiagnostics.log("kokoro.cancel")
    }

    override suspend fun close() {
        DeveloperDiagnostics.log("kokoro.close.begin")
        cancelCurrent()
        generationMutex.withLock {
            lifecycleMutex.withLock {
                val current = tts
                tts = null
                if (current != null) withContext(dispatcher) { runCatching { current.release() } }
            }
        }
        DeveloperDiagnostics.log("kokoro.close.complete")
    }

    private fun validateAbi() {
        if (Build.SUPPORTED_ABIS.none { it in KokoroAndroidRuntimeContract.supportedAbis }) {
            throw KokoroInferenceException("This device ABI is not supported by the local Kokoro runtime")
        }
    }

    private fun requireFile(path: String?, label: String): File {
        val file = path?.let(::File) ?: throw KokoroInferenceException("Missing Kokoro $label path")
        if (!file.isFile || file.length() <= 0L) throw KokoroInferenceException("Invalid Kokoro $label asset")
        return file
    }

    private fun requireDirectory(path: String?, label: String): File {
        val file = path?.let(::File) ?: throw KokoroInferenceException("Missing Kokoro $label path")
        if (!file.isDirectory || file.list()?.isEmpty() != false) throw KokoroInferenceException("Invalid Kokoro $label directory")
        return file
    }
}
