package com.projectsuperhuman.next

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Streams synthesized mono PCM without retaining AudioTrack resources between utterances. */
class AndroidTrudyAudioSink : TrudyAudioSink {
    @Volatile private var activeTrack: AudioTrack? = null

    override suspend fun play(audio: TrudyPcmAudio) = withContext(Dispatchers.IO) {
        require(audio.channels == 1)
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
            val pcm = ShortArray(audio.samples.size) { index ->
                (audio.samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            track.play()
            var offset = 0
            val chunkSamples = (minBuffer / 2).coerceAtLeast(1)
            while (offset < pcm.size) {
                coroutineContext.ensureActive()
                val count = minOf(chunkSamples, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
        } finally {
            if (activeTrack === track) activeTrack = null
            runCatching { track.stop() }
            track.release()
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
