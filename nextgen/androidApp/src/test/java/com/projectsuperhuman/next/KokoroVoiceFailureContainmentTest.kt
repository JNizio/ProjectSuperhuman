package com.projectsuperhuman.next

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KokoroVoiceFailureContainmentTest {
    @Test
    fun initializationFailureDoesNotProduceAudioAndCanReleaseBackend() = runTest {
        val backend = object : KokoroInferenceBackend {
            override val backendId = "init-failure"
            var closeCalls = 0
            override suspend fun initialize(files: KokoroModelFiles) {
                throw KokoroInferenceException("native session failed")
            }
            override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput =
                error("synthesis must not run")
            override suspend fun close() { closeCalls++ }
        }
        val engine = KokoroTrudySpeechEngine(localConfig(), InstalledStore, backend)

        assertFailsWith<KokoroInferenceException> {
            engine.synthesize(TrudySpeechRequest("Hello", "af_heart"))
        }
        assertEquals(1, backend.closeCalls)
    }

    @Test
    fun catchableInitializationOomBecomesVoiceLevelFailureAndClosesBackend() = runTest {
        val backend = object : KokoroInferenceBackend {
            override val backendId = "oom"
            var closeCalls = 0
            override suspend fun initialize(files: KokoroModelFiles) {
                throw OutOfMemoryError("fixture")
            }
            override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput =
                error("synthesis must not run")
            override suspend fun close() { closeCalls++ }
        }
        val engine = KokoroTrudySpeechEngine(localConfig(), InstalledStore, backend)

        val failure = assertFailsWith<IllegalStateException> {
            engine.synthesize(TrudySpeechRequest("Hello", "af_heart"))
        }
        assertTrue(failure.message.orEmpty().contains("memory", ignoreCase = true))
        assertEquals(1, backend.closeCalls)
    }

    @Test
    fun audioSinkFailureStaysInsideVoiceServiceState() = runTest {
        val engine = object : TrudySpeechEngine {
            override val engineId = "fixture"
            override suspend fun isAvailable() = true
            override suspend fun synthesize(request: TrudySpeechRequest) =
                TrudySpeechResult(
                    TrudyPcmAudio(floatArrayOf(0f, 0.1f), 24_000),
                    TrudySpeechDiagnostics(engineId, "model", request.voiceId, 24_000)
                )
        }
        val sink = object : TrudyAudioSink {
            override suspend fun play(audio: TrudyPcmAudio) {
                throw IllegalStateException("AudioTrack unavailable")
            }
            override fun stop() = Unit
        }
        val service = TrudyVoiceService(localConfig(), engine, sink)

        assertFailsWith<IllegalStateException> { service.speak("Hello") }
        assertEquals(TrudyVoiceRuntimeState.ERROR, service.runtimeState())
    }

    private fun localConfig() = TrudyVoiceConfig(
        mode = TrudyVoiceMode.KOKORO_LOCAL,
        modelId = "test/kokoro",
        voiceId = "af_heart"
    )

    private object InstalledStore : KokoroModelStore {
        override suspend fun isInstalled(modelId: String) = true
        override suspend fun resolve(modelId: String) = KokoroModelFiles(
            modelPath = "/private/model.onnx",
            voicesPath = "/private/voices.bin",
            tokenizerPath = "/private/tokens.txt",
            phonemizerDataDir = "/private/espeak-ng-data",
            lexiconPath = "/private/lexicon-us-en.txt"
        )
    }
}
