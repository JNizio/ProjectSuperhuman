package com.projectsuperhuman.next

import android.os.Build
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class KittenVoiceDescriptor(
    val id: String,
    val displayName: String,
    val speakerId: Int
)

/**
 * sherpa-onnx v0.8 voices.bin is written in this exact order by generate_voices_bin.py.
 * User-facing names are the aliases published by KittenML.
 */
object KittenVoiceCatalog {
    val voices = listOf(
        KittenVoiceDescriptor("Jasper", "Jasper", 0),
        KittenVoiceDescriptor("Bella", "Bella", 1),
        KittenVoiceDescriptor("Bruno", "Bruno", 2),
        KittenVoiceDescriptor("Luna", "Luna", 3),
        KittenVoiceDescriptor("Hugo", "Hugo", 4),
        KittenVoiceDescriptor("Rosie", "Rosie", 5),
        KittenVoiceDescriptor("Leo", "Leo", 6),
        KittenVoiceDescriptor("Kiki", "Kiki", 7)
    )

    private val legacyVoiceFallbacks = mapOf(
        "af_heart" to "Bella",
        "af_jessica" to "Luna",
        "af_bella" to "Bella",
        "af_sarah" to "Rosie",
        "af_nova" to "Kiki",
        "am_adam" to "Jasper",
        "am_michael" to "Bruno",
        "bm_daniel" to "Hugo"
    )

    fun resolve(id: String): KittenVoiceDescriptor {
        val mapped = legacyVoiceFallbacks[id] ?: id
        return voices.firstOrNull { it.id.equals(mapped, ignoreCase = true) }
            ?: voices.first { it.id == "Bella" }
    }
}

/**
 * Fast mobile Trudy backend: KittenTTS Nano v0.8 INT8 through sherpa-onnx.
 * It deliberately uses the same non-callback native API that proved stable for Kokoro on the
 * Xiaomi test device. Four CPU threads matched the best stable Kokoro measurements and are kept
 * here as the initial Kitten setting; DeveloperDiagnostics records actual RTF for tuning.
 */
class SherpaKittenInferenceBackend(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val threads: Int = 4
) : KokoroInferenceBackend {
    override val backendId = "sherpa-kitten-int8-${KokoroAndroidRuntimeContract.SHERPA_ONNX_VERSION}"

    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private val cancellationEpoch = AtomicLong(0L)
    @Volatile private var tts: OfflineTts? = null

    override suspend fun initialize(files: KokoroModelFiles) = lifecycleMutex.withLock {
        if (tts != null) return@withLock
        DeveloperDiagnostics.log("kitten.init.begin", "threads=$threads model=int8 path=non_callback")
        validateAbi()
        val model = requireFile(files.modelPath, "model.int8.onnx")
        val voices = requireFile(files.voicesPath, "voices.bin")
        val tokens = requireFile(files.tokenizerPath, "tokens.txt")
        val dataDir = requireDirectory(files.phonemizerDataDir, "espeak-ng-data")
        DeveloperDiagnostics.log(
            "kitten.init.assets_ok",
            "model=${model.length() / (1024L * 1024L)}MB voices=${voices.length() / (1024L * 1024L)}MB"
        )

        val created = try {
            DeveloperDiagnostics.log("kitten.init.native_enter")
            withContext(dispatcher) {
                OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kitten = OfflineTtsKittenModelConfig(
                                model = model.absolutePath,
                                voices = voices.absolutePath,
                                tokens = tokens.absolutePath,
                                dataDir = dataDir.absolutePath
                            ),
                            numThreads = threads,
                            debug = false,
                            provider = KokoroAndroidRuntimeContract.PROVIDER
                        ),
                        maxNumSentences = 1,
                        silenceScale = 0.2f
                    )
                )
            }.also { DeveloperDiagnostics.log("kitten.init.native_returned") }
        } catch (oom: OutOfMemoryError) {
            DeveloperDiagnostics.log("kitten.init.oom", oom.message)
            throw KokoroInferenceException("Kitten voice runtime could not allocate enough memory", oom)
        } catch (failure: Throwable) {
            DeveloperDiagnostics.log("kitten.init.error", "${failure.javaClass.simpleName}: ${failure.message}")
            throw KokoroInferenceException("Kitten voice runtime initialization failed", failure)
        }

        try {
            val sampleRate = withContext(dispatcher) { created.sampleRate() }
            val speakers = withContext(dispatcher) { created.numSpeakers() }
            require(sampleRate == Kokoro82MModelContract.SAMPLE_RATE_HZ) { "Unexpected Kitten sample rate: $sampleRate" }
            require(speakers >= KittenVoiceCatalog.voices.size) {
                "Kitten voice table mismatch: expected at least ${KittenVoiceCatalog.voices.size}, found $speakers"
            }
            tts = created
            DeveloperDiagnostics.log("kitten.init.ready", "sampleRate=$sampleRate speakers=$speakers threads=$threads")
        } catch (failure: Throwable) {
            DeveloperDiagnostics.log("kitten.init.metadata_error", "${failure.javaClass.simpleName}: ${failure.message}")
            runCatching { created.release() }
            throw KokoroInferenceException("Kitten voice metadata validation failed", failure)
        }
    }

    override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput = generationMutex.withLock {
        val runtime = tts ?: throw KokoroInferenceException("Kitten voice runtime is not initialized")
        val voice = KittenVoiceCatalog.resolve(request.voiceId)
        val requestEpoch = cancellationEpoch.get()
        DeveloperDiagnostics.log(
            "kitten.synth.begin",
            "chars=${request.text.length} voice=${voice.id} sid=${voice.speakerId} speed=${request.speed} threads=$threads"
        )

        val startedNs = System.nanoTime()
        val generated = try {
            DeveloperDiagnostics.log("kitten.synth.native_enter", "api=generateWithConfig")
            withContext(dispatcher) {
                runtime.generateWithConfig(
                    text = request.text,
                    config = GenerationConfig(
                        silenceScale = 0.2f,
                        speed = request.speed,
                        sid = voice.speakerId
                    )
                )
            }.also { DeveloperDiagnostics.log("kitten.synth.native_returned", "api=generateWithConfig") }
        } catch (cancel: CancellationException) {
            DeveloperDiagnostics.log("kitten.synth.cancelled")
            throw cancel
        } catch (oom: OutOfMemoryError) {
            DeveloperDiagnostics.log("kitten.synth.oom", oom.message)
            throw KokoroInferenceException("Kitten synthesis ran out of memory", oom)
        } catch (failure: Throwable) {
            DeveloperDiagnostics.log("kitten.synth.error", "${failure.javaClass.simpleName}: ${failure.message}")
            if (cancellationEpoch.get() != requestEpoch) {
                throw CancellationException("Kitten synthesis cancelled").also { it.initCause(failure) }
            }
            throw KokoroInferenceException("Kitten synthesis failed", failure)
        }

        if (cancellationEpoch.get() != requestEpoch) {
            DeveloperDiagnostics.log("kitten.synth.discarded_after_cancel")
            throw CancellationException("Kitten synthesis cancelled")
        }
        if (generated.samples.isEmpty() || generated.sampleRate <= 0) {
            throw KokoroInferenceException("Kitten returned malformed or empty audio")
        }
        val synthesisMs = (System.nanoTime() - startedNs) / 1_000_000L
        val audioMs = generated.samples.size * 1000L / generated.sampleRate
        val rtf = if (audioMs > 0L) synthesisMs.toDouble() / audioMs else 0.0
        DeveloperDiagnostics.log(
            "kitten.synth.complete",
            "samples=${generated.samples.size} rate=${generated.sampleRate} synthesisMs=$synthesisMs audioMs=$audioMs rtf=${"%.2f".format(rtf)}"
        )
        KokoroInferenceOutput(generated.samples, generated.sampleRate)
    }

    override fun cancelCurrent() {
        cancellationEpoch.incrementAndGet()
        DeveloperDiagnostics.log("kitten.cancel")
    }

    override suspend fun close() {
        DeveloperDiagnostics.log("kitten.close.begin")
        cancelCurrent()
        generationMutex.withLock {
            lifecycleMutex.withLock {
                val current = tts
                tts = null
                if (current != null) withContext(dispatcher) { runCatching { current.release() } }
            }
        }
        DeveloperDiagnostics.log("kitten.close.complete")
    }

    private fun validateAbi() {
        if (Build.SUPPORTED_ABIS.none { it in KokoroAndroidRuntimeContract.supportedAbis }) {
            throw KokoroInferenceException("This device ABI is not supported by the local voice runtime")
        }
    }

    private fun requireFile(path: String?, label: String): File {
        val file = path?.let(::File) ?: throw KokoroInferenceException("Missing Kitten $label path")
        if (!file.isFile || file.length() <= 0L) throw KokoroInferenceException("Invalid Kitten $label asset")
        return file
    }

    private fun requireDirectory(path: String?, label: String): File {
        val file = path?.let(::File) ?: throw KokoroInferenceException("Missing Kitten $label path")
        if (!file.isDirectory || file.list()?.isEmpty() != false) throw KokoroInferenceException("Invalid Kitten $label directory")
        return file
    }
}
