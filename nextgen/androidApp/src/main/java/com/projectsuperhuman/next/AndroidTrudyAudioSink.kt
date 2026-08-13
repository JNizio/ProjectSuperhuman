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

/** Streams synthesized mono PCM without retaining AudioTrack resources between utterance chunks. */
class AndroidTrudyAudioSink : TrudyAudioSink {
    @Volatile private var activeTrack: AudioTrack? = null

    override suspend fun play(audio: TrudyPcmAudio) = withContext(Dispatchers.IO) {
        require(audio.channels == 1)
        require(audio.sampleRateHz > 0)
        if (audio.samples.isEmpty()) return@withContext

        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4_096)

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
                    .setSampleRate(audio.sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()

        activeTrack = track
        try {
            val pcm = TrudyPcm16.convert(audio.samples)
            track.play()
            var offset = 0
            val writeChunkSamples = (minBuffer / 2).coerceAtLeast(1)
            while (offset < pcm.size) {
                coroutineContext.ensureActive()
                if (activeTrack !== track) break
                val count = minOf(writeChunkSamples, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }

            // Blocking writes only guarantee delivery into AudioTrack's buffer. Wait for the
            // playback head so the final phoneme is not cut off when this chunk is released.
            while (activeTrack === track && offset == pcm.size) {
                coroutineContext.ensureActive()
                val played = try {
                    track.playbackHeadPosition.toLong()
                } catch (_: Throwable) {
                    break
                }
                if (played >= pcm.size.toLong()) break
                delay(10)
            }
        } finally {
            if (activeTrack === track) activeTrack = null
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    override fun stop() {
        activeTrack?.let { track ->
            activeTrack = null
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
