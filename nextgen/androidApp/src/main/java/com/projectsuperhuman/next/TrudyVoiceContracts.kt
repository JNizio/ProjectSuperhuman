package com.projectsuperhuman.next

/** Local-first voice contract. Trudy core never depends on a TTS vendor or inference runtime. */
enum class TrudyVoiceMode { OFF, KOKORO_LOCAL }

enum class TrudyVoiceRuntimeState { NOT_INSTALLED, INSTALLING, READY, LOADING, SPEAKING, ERROR }

data class TrudyVoiceInstallProgress(
    val downloadedBytes: Long,
    val totalBytes: Long? = null
) {
    init {
        require(downloadedBytes >= 0L)
        require(totalBytes == null || totalBytes >= 0L)
    }
    val fraction: Float? get() = totalBytes?.takeIf { it > 0L }
        ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

data class TrudyVoiceOption(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val installed: Boolean
)

data class TrudyVoiceStatus(
    val state: TrudyVoiceRuntimeState,
    val modelId: String,
    val installedVersion: String? = null,
    val progress: TrudyVoiceInstallProgress? = null,
    val message: String? = null
)

interface TrudyVoiceModelManager {
    suspend fun status(): TrudyVoiceStatus
    suspend fun install(onProgress: suspend (TrudyVoiceInstallProgress) -> Unit = {}): TrudyVoiceStatus
    suspend fun availableVoices(): List<TrudyVoiceOption>
    suspend fun cleanupIncomplete()
}

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
    val synthesisDurationMs: Long? = null,
    val modelLoadDurationMs: Long? = null,
    val generatedAudioDurationMs: Long? = null,
    val realTimeFactor: Double? = null,
    val modelBytesOnDisk: Long? = null
)

data class TrudySpeechResult(
    val audio: TrudyPcmAudio,
    val diagnostics: TrudySpeechDiagnostics
)

interface TrudySpeechEngine {
    val engineId: String
    suspend fun isAvailable(): Boolean
    suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult

    /** Default preserves existing engines; Kokoro overrides this to synthesize bounded chunks. */
    suspend fun synthesizeStreaming(
        request: TrudySpeechRequest,
        onChunk: suspend (TrudySpeechResult) -> Unit
    ): TrudySpeechDiagnostics {
        val result = synthesize(request)
        onChunk(result)
        return result.diagnostics
    }

    fun cancelCurrent() {}
    suspend fun close() {}
}

interface TrudyAudioSink {
    suspend fun play(audio: TrudyPcmAudio)
    fun stop()
    fun close() = stop()
}
