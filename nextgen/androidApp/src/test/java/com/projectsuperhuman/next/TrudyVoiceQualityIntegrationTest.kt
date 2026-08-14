package com.projectsuperhuman.next

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TrudyVoiceQualityIntegrationTest {
    @Test
    fun playbackStarvationWaitsForNextChunkWithoutReorderingOrPrematureDrain() = runTest {
        val engine = DelayedSecondChunkEngine()
        val sink = ZeroBufferedRecordingSink()
        val service = TrudyVoiceService(
            config = TrudyVoiceConfig(
                mode = TrudyVoiceMode.KOKORO_LOCAL,
                modelId = "test-model",
                voiceId = "Bella"
            ),
            engine = engine,
            audioSink = sink,
            lookaheadChunks = 2
        )

        val job = launch { service.speak("A long answer whose second chunk is deliberately late") }
        runCurrent()
        sink.firstPlayback.await()

        assertEquals(listOf(0), sink.played)
        assertEquals(TrudyVoiceRuntimeState.SPEAKING, service.runtimeState())
        assertEquals(0, sink.drainCalls)

        advanceTimeBy(99)
        runCurrent()
        assertEquals(listOf(0), sink.played)
        assertEquals(0, sink.drainCalls)

        advanceUntilIdle()
        job.join()

        assertEquals(listOf(0, 1), sink.played)
        assertEquals(1, sink.drainCalls)
        assertEquals(TrudyVoiceRuntimeState.READY, service.runtimeState())
    }

    @Test
    fun fiveHundredCharacterAnswerStillUsesProgressiveBoundedChunks() {
        val text = (1..100).joinToString(" ") { "health$it" }
        val chunks = KokoroTextChunker.progressiveChunk(text)

        kotlin.test.assertTrue(text.length > 500)
        kotlin.test.assertTrue(chunks.first().length <= 36)
        kotlin.test.assertTrue(chunks[1].length <= 50)
        kotlin.test.assertTrue(chunks.drop(2).all { it.length <= 64 })
        assertEquals(text, chunks.joinToString(" "))
    }

    private class DelayedSecondChunkEngine : TrudySpeechEngine {
        override val engineId: String = "delayed-second"

        override suspend fun isAvailable(): Boolean = true

        override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult =
            error("streaming only")

        override suspend fun synthesizeStreaming(
            request: TrudySpeechRequest,
            onBeforeChunk: suspend (index: Int) -> Unit,
            onChunk: suspend (TrudySpeechResult) -> Unit
        ): TrudySpeechDiagnostics {
            val diagnostics = diagnostics(request)
            onBeforeChunk(0)
            onChunk(result(0, diagnostics))
            delay(100)
            onBeforeChunk(1)
            onChunk(result(1, diagnostics))
            return diagnostics
        }
    }

    private class ZeroBufferedRecordingSink : TrudyAudioSink {
        val firstPlayback = CompletableDeferred<Unit>()
        val played = mutableListOf<Int>()
        var drainCalls = 0

        override suspend fun play(audio: TrudyPcmAudio) {
            val index = audio.samples.first().toInt()
            played += index
            if (index == 0) firstPlayback.complete(Unit)
        }

        override suspend fun drain() {
            drainCalls++
        }

        override fun bufferedAudioDurationMs(): Long = 0L
        override fun stop() = Unit
    }

    private companion object {
        fun result(index: Int, diagnostics: TrudySpeechDiagnostics) = TrudySpeechResult(
            audio = TrudyPcmAudio(FloatArray(24) { index.toFloat() }, 24_000),
            diagnostics = diagnostics
        )

        fun diagnostics(request: TrudySpeechRequest) = TrudySpeechDiagnostics(
            engineId = "test",
            modelId = "test-model",
            voiceId = request.voiceId,
            sampleRateHz = 24_000,
            synthesisDurationMs = 100,
            generatedAudioDurationMs = 1
        )
    }
}
