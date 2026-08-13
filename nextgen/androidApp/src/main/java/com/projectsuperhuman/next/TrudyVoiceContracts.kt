package com.projectsuperhuman.next

/** Local-first voice contract. Trudy core never depends on a TTS vendor or inference runtime. */
enum class TrudyVoiceMode { OFF, KOKORO_LOCAL }

data class TrudyVoiceConfig(
    val mode: TrudyVoiceMode = TrudyVoiceMode.OFF,
    val modelId: String = "onnx-community/Kokoro-82M-v1.0-ONNX",
    val voiceId: String = "af_heart",
    val speed: Float = 1.0f,
    val autoSpeak: Boolean = false
) {
    init {
        require(modelId.isNotBlank())
        require(voiceId.isNotBlank())
        require(speed in 0.5f..2.0f)
    }
}

data class TrudySpeechRequest(
    val text: String,
    val voiceId: String,
    val speed: Float = 1.0f
) {
    init {
        require(text.isNotBlank())
        require(voiceId.isNotBlank())
        require(speed in 0.5f..2.0f)
    }
}

data class TrudyPcmAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val channels: Int = 1
) {
    init {
        require(sampleRateHz > 0)
        require(channels == 1) { "Trudy voice currently expects mono PCM" }
    }
}

data class TrudySpeechDiagnostics(
    val engineId: String,
    val modelId: String,
    val voiceId: String,
    val sampleRateHz: Int,
    val synthesisDurationMs: Long? = null
)

data class TrudySpeechResult(
    val audio: TrudyPcmAudio,
    val diagnostics: TrudySpeechDiagnostics
)

interface TrudySpeechEngine {
    val engineId: String
    suspend fun isAvailable(): Boolean
    suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult
    suspend fun close() {}
}

interface TrudyAudioSink {
    suspend fun play(audio: TrudyPcmAudio)
    fun stop()
    fun close() = stop()
}
