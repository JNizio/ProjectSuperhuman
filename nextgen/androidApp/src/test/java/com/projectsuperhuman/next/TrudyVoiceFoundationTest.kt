package com.projectsuperhuman.next

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrudyVoiceFoundationTest {
    @Test
    fun kokoroInitializesLazilyAndOnlyOnce() = runTest {
        val store = FakeStore(installed = true)
        val backend = FakeBackend()
        val engine = KokoroTrudySpeechEngine(
            config = TrudyVoiceConfig(mode = TrudyVoiceMode.KOKORO_LOCAL),
            modelStore = store,
            backend = backend,
            nowMs = { 100L }
        )

        assertTrue(engine.isAvailable())
        assertEquals(0, backend.initializeCalls)
        engine.synthesize(TrudySpeechRequest("Hello", "af_heart"))
        engine.synthesize(TrudySpeechRequest("Again", "af_heart"))
        assertEquals(1, backend.initializeCalls)
        assertEquals(2, backend.synthesisCalls)
    }

    @Test
    fun unavailableModelDoesNotInitializeBackend() = runTest {
        val backend = FakeBackend()
        val engine = KokoroTrudySpeechEngine(
            config = TrudyVoiceConfig(mode = TrudyVoiceMode.KOKORO_LOCAL),
            modelStore = FakeStore(installed = false),
            backend = backend
        )
        assertFalse(engine.isAvailable())
        assertEquals(0, backend.initializeCalls)
    }

    @Test
    fun voiceRuntimeFallsBackToTextWhenBackendIsMissing() = runTest {
        val runtime = TrudyVoiceRuntimeFactory.create(
            config = TrudyVoiceConfig(mode = TrudyVoiceMode.KOKORO_LOCAL),
            modelStore = FakeStore(installed = true),
            kokoroBackend = null,
            audioSink = FakeSink()
        )
        assertFalse(runtime.diagnostics.available)
        assertEquals(TrudyVoiceMode.OFF, runtime.diagnostics.activeMode)
        assertEquals(null, runtime.service)
    }

    @Test
    fun markdownIsReducedBeforeSpeech() {
        val prepared = TrudySpeechText.prepare("## Result **good** [details](https://example.test) `42`")
        assertEquals("Result good details 42", prepared)
    }

    @Test
    fun voiceServiceUsesConfiguredVoiceAndPlaysResult() = runTest {
        val engine = CapturingEngine()
        val sink = FakeSink()
        val service = TrudyVoiceService(
            TrudyVoiceConfig(mode = TrudyVoiceMode.KOKORO_LOCAL, voiceId = "bf_emma", speed = 1.1f),
            engine,
            sink
        )
        service.speak("Hi Trudy")
        assertEquals("bf_emma", engine.lastRequest?.voiceId)
        assertEquals(1.1f, engine.lastRequest?.speed)
        assertEquals(1, sink.playCalls)
    }

    private class FakeStore(private val installed: Boolean) : KokoroModelStore {
        override suspend fun isInstalled(modelId: String) = installed
        override suspend fun resolve(modelId: String) = KokoroModelFiles("/tmp/model.onnx")
    }

    private class FakeBackend : KokoroInferenceBackend {
        override val backendId = "fake"
        var initializeCalls = 0
        var synthesisCalls = 0
        override suspend fun initialize(files: KokoroModelFiles) { initializeCalls++ }
        override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput {
            synthesisCalls++
            return KokoroInferenceOutput(floatArrayOf(0f, 0.25f, -0.25f))
        }
        override suspend fun close() = Unit
    }

    private class CapturingEngine : TrudySpeechEngine {
        override val engineId = "capture"
        var lastRequest: TrudySpeechRequest? = null
        override suspend fun isAvailable() = true
        override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult {
            lastRequest = request
            return TrudySpeechResult(
                TrudyPcmAudio(floatArrayOf(0f), 24_000),
                TrudySpeechDiagnostics(engineId, "model", request.voiceId, 24_000)
            )
        }
    }

    private class FakeSink : TrudyAudioSink {
        var playCalls = 0
        override suspend fun play(audio: TrudyPcmAudio) { playCalls++ }
        override fun stop() = Unit
    }
}
