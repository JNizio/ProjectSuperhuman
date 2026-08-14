package com.projectsuperhuman.next

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object TrudyPcm16 {
    fun sample(value: Float): Short {
        val safe = when {
            value.isNaN() -> 0f
            value == Float.POSITIVE_INFINITY -> 1f
            value == Float.NEGATIVE_INFINITY -> -1f
            else -> value.coerceIn(-1f, 1f)
        }
        return when {
            safe >= 1f -> Short.MAX_VALUE
            safe <= -1f -> Short.MIN_VALUE
            else -> (safe * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun convert(samples: FloatArray): ShortArray = ShortArray(samples.size) { sample(samples[it]) }
}

/**
 * Keeps one MODE_STREAM AudioTrack for the complete utterance. Each call writes into the same
 * hardware buffer and returns once that chunk has been accepted, allowing the next ready chunk to
 * be queued before the current one reaches the speaker. drain() is the only per-utterance tail wait.
 */
class AndroidTrudyAudioSink : TrudyAudioSink {
    private class PlaybackSession(
        val track: AudioTrack,
        val sampleRateHz: Int,
        val bufferSizeBytes: Int
    ) {
        @Volatile var writtenSamples: Long = 0L
    }

    private val sessionLock = Any()
    @Volatile private var activeSession: PlaybackSession? = null

    override suspend fun play(audio: TrudyPcmAudio) = withContext(Dispatchers.IO) {
        require(audio.channels == 1)
        require(audio.sampleRateHz > 0)
        if (audio.samples.isEmpty()) return@withContext

        val pcm = TrudyPcm16.convert(audio.samples)
        val session = sessionFor(audio.sampleRateHz)
        runCatching { session.track.play() }.getOrElse { failure ->
            releaseIfActive(session, flush = true)
            throw failure
        }

        var offset = 0
        val writeChunkSamples = (session.bufferSizeBytes / 2).coerceAtLeast(1)
        while (offset < pcm.size) {
            coroutineContext.ensureActive()
            if (activeSession !== session) return@withContext
            val count = minOf(writeChunkSamples, pcm.size - offset)
            val written = session.track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) {
                if (activeSession !== session) return@withContext
                releaseIfActive(session, flush = true)
                error("AudioTrack write failed: $written")
            }
            offset += written
            session.writtenSamples += written.toLong()
        }
    }

    override fun bufferedAudioDurationMs(): Long {
        val session = activeSession ?: return 0L
        val playedSamples = runCatching {
            session.track.playbackHeadPosition.toLong() and 0xffff_ffffL
        }.getOrDefault(session.writtenSamples)
        val remainingSamples = (session.writtenSamples - playedSamples).coerceAtLeast(0L)
        return remainingSamples * 1000L / session.sampleRateHz
    }

    override suspend fun drain() = withContext(Dispatchers.IO) {
        val session = activeSession ?: return@withContext
        val expectedSamples = session.writtenSamples
        val expectedRemainingMs = bufferedAudioDurationMs()
        val deadlineMs = nowMs() + expectedRemainingMs + 750L
        try {
            while (activeSession === session) {
                coroutineContext.ensureActive()
                val played = runCatching {
                    session.track.playbackHeadPosition.toLong() and 0xffff_ffffL
                }.getOrElse { break }
                if (played >= expectedSamples) break
                if (nowMs() >= deadlineMs) {
                    DeveloperDiagnostics.log(
                        "voice.stream.playback_drain_timeout",
                        "played=$played expected=$expectedSamples bufferedMs=${bufferedAudioDurationMs()}"
                    )
                    break
                }
                delay(10)
            }
        } finally {
            releaseIfActive(session, flush = false)
        }
    }

    override fun stop() {
        val session = synchronized(sessionLock) {
            activeSession.also { activeSession = null }
        } ?: return
        release(session, flush = true)
    }

    private fun sessionFor(sampleRateHz: Int): PlaybackSession = synchronized(sessionLock) {
        activeSession?.takeIf { it.sampleRateHz == sampleRateHz }?.let { return@synchronized it }
        activeSession?.let {
            activeSession = null
            release(it, flush = true)
        }

        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4_096)
        val streamBufferBytes = (minBufferBytes * 2).coerceAtLeast(8_192)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(streamBufferBytes)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
        PlaybackSession(track, sampleRateHz, streamBufferBytes).also { activeSession = it }
    }

    private fun releaseIfActive(session: PlaybackSession, flush: Boolean) {
        val shouldRelease = synchronized(sessionLock) {
            if (activeSession === session) {
                activeSession = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) release(session, flush)
    }

    private fun release(session: PlaybackSession, flush: Boolean) {
        if (flush) {
            runCatching { session.track.pause() }
            runCatching { session.track.flush() }
        }
        runCatching { session.track.stop() }
        runCatching { session.track.release() }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L
}
