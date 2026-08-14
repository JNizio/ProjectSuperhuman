package com.projectsuperhuman.next

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrudyVoiceStreamingPerformanceTest {
    @Test
    fun conversationalPlanUsesSentenceAndClauseBoundaries() {
        val text = "Sleep improved overnight. Your recovery is trending upward, while resting heart rate remains stable. " +
            "Hydration is slightly below your usual range, so drink steadily this afternoon."

        val chunks = KokoroTextChunker.progressiveChunk(text)

        assertEquals("Sleep improved overnight.", chunks.first())
        assertTrue(chunks.any { it.endsWith(',') })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun firstTwoChunksRampIntoBoundedConversationalChunksWithoutDuplication() {
        val text = (1..90).joinToString(" ") { "word$it" }

        val chunks = KokoroTextChunker.progressiveChunk(text)

        assertTrue(chunks.size > 8)
        assertTrue(chunks.first().length <= 36)
        assertTrue(chunks[1].length <= 50)
        assertTrue(chunks.drop(2).all { it.length <= 64 })
        assertEquals(text, chunks.joinToString(" "))
        assertEquals(chunks.size, chunks.distinct().size)
    }

    @Test
    fun producerStaysBoundedTwoChunksAheadAndPlaybackIsOrderedOnce() = runTest {
        val engine = BurstStreamingEngine(chunkCount = 7)
        val sink = BlockingFirstSink()
        val service = TrudyVoiceService(localConfig(), engine, sink, lookaheadChunks = 2)

        val speech = launch { service.speak("A sufficiently long streaming response") }
        sink.firstPlaybackStarted.await()
        runCurrent()

        // One chunk is in playback and only two more can have crossed the producer boundary.
        assertEquals(listOf(0, 1, 2), engine.produced)
        sink.releaseFirstPlayback.complete(Unit)
        speech.join()

        assertEquals((0..6).toList(), sink.played)
        assertEquals((0..6).toList(), engine.produced)
        assertEquals(1, sink.drainCalls)
    }

    @Test
    fun stoppedResponseCannotLeakAStaleChunkIntoTheNextResponse() = runTest {
        val engine = StaleGuardEngine()
        val sink = RecordingSink()
        val service = TrudyVoiceService(localConfig(), engine, sink)

        var firstCancelled = false
        val first = launch {
            try {
                service.speak("first response")
            } catch (_: CancellationException) {
                firstCancelled = true
            }
        }
        sink.firstPlayback.await()
        service.stop()
        first.join()

        service.speak("replacement response")

        assertTrue(firstCancelled)
        assertEquals(listOf(1, 2), sink.played)
        assertTrue(99 !in sink.played)
        assertEquals(TrudyVoiceRuntimeState.READY, service.runtimeState())
    }

    private class BurstStreamingEngine(private val chunkCount: Int) : TrudySpeechEngine {
        override val engineId = "burst"
        val produced = mutableListOf<Int>()

        override suspend fun isAvailable() = true
        override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult = error("streaming only")

        override suspend fun synthesizeStreaming(
            request: TrudySpeechRequest,
            onBeforeChunk: suspend (index: Int) -> Unit,
            onChunk: suspend (TrudySpeechResult) -> Unit
        ): TrudySpeechDiagnostics {
            val diagnostics = diagnostics(request)
            repeat(chunkCount) { index ->
                onBeforeChunk(index)
                onChunk(result(index, diagnostics))
                produced += index
            }
            return diagnostics
        }
    }

    private class BlockingFirstSink : TrudyAudioSink {
        val firstPlaybackStarted = CompletableDeferred<Unit>()
        val releaseFirstPlayback = CompletableDeferred<Unit>()
        val played = mutableListOf<Int>()
        var drainCalls = 0

        override suspend fun play(audio: TrudyPcmAudio) {
            val index = audio.samples.first().toInt()
            if (index == 0) {
                firstPlaybackStarted.complete(Unit)
                releaseFirstPlayback.await()
            }
            played += index
        }

        override suspend fun drain() { drainCalls++ }
        override fun bufferedAudioDurationMs() = 1_000L
        override fun stop() = Unit
    }

    private class StaleGuardEngine : TrudySpeechEngine {
        override val engineId = "stale-guard"
        private val invocation = AtomicInteger(0)
        private val releaseFirst = CompletableDeferred<Unit>()
        @Volatile private var cancelled = false

        override suspend fun isAvailable() = true
        override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult = error("streaming only")

        override suspend fun synthesizeStreaming(
            request: TrudySpeechRequest,
            onChunk: suspend (TrudySpeechResult) -> Unit
        ): TrudySpeechDiagnostics {
            val diagnostics = diagnostics(request)
            if (invocation.getAndIncrement() == 0) {
                onChunk(result(1, diagnostics))
                releaseFirst.await()
                if (cancelled) throw CancellationException("stopped")
                onChunk(result(99, diagnostics))
            } else {
                onChunk(result(2, diagnostics))
            }
            return diagnostics
        }

        override fun cancelCurrent() {
            cancelled = true
            releaseFirst.complete(Unit)
        }
    }

    private class RecordingSink : TrudyAudioSink {
        val firstPlayback = CompletableDeferred<Unit>()
        val played = mutableListOf<Int>()

        override suspend fun play(audio: TrudyPcmAudio) {
            val marker = audio.samples.first().toInt()
            played += marker
            if (marker == 1) firstPlayback.complete(Unit)
        }

        override fun stop() = Unit
    }

    private fun localConfig() = TrudyVoiceConfig(
        mode = TrudyVoiceMode.KOKORO_LOCAL,
        modelId = "test-model",
        voiceId = "Bella"
    )

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
            synthesisDurationMs = 10,
            generatedAudioDurationMs = 1
        )
    }
}
