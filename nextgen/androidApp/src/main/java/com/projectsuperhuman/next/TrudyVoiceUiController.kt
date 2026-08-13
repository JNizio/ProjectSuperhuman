package com.projectsuperhuman.next

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.round

/** UI-facing states only. No inference/runtime implementation detail is exposed to Compose. */
enum class TrudyVoiceUiStatus {
    UNAVAILABLE,
    MODEL_NOT_INSTALLED,
    DOWNLOADING,
    READY,
    LOADING,
    SYNTHESIZING,
    SPEAKING,
    STOPPED,
    ERROR
}

enum class TrudyVoiceModelInstallation { UNAVAILABLE, NOT_INSTALLED, INSTALLED }

data class TrudyVoiceOption(val id: String, val label: String)

data class TrudyVoiceModelInfo(
    val installation: TrudyVoiceModelInstallation,
    val modelVersion: String? = null,
    val approximateDownloadBytes: Long? = null,
    val availableVoices: List<TrudyVoiceOption> = emptyList(),
    val canInstall: Boolean = false,
    val canRemove: Boolean = false,
    val reason: String? = null
)

data class TrudyVoiceInstallProgress(
    val fraction: Float? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null
) {
    init {
        require(fraction == null || fraction in 0f..1f)
        require(downloadedBytes == null || downloadedBytes >= 0)
        require(totalBytes == null || totalBytes > 0)
    }
}

data class TrudyVoicePreferences(
    val enabled: Boolean = true,
    val selectedVoiceId: String,
    val speed: Float = 1.0f,
    val autoSpeak: Boolean = false
) {
    init {
        require(selectedVoiceId.isNotBlank())
        require(speed in 0.5f..2.0f)
    }
}

data class TrudyVoiceUiState(
    val status: TrudyVoiceUiStatus,
    val preferences: TrudyVoicePreferences,
    val modelInfo: TrudyVoiceModelInfo,
    val activeMessageId: Long? = null,
    val installProgressPercent: Int? = null,
    val errorMessage: String? = null,
    val promptVisible: Boolean = false
) {
    fun isSpeakingMessage(id: Long): Boolean =
        activeMessageId == id && status in setOf(
            TrudyVoiceUiStatus.LOADING,
            TrudyVoiceUiStatus.SYNTHESIZING,
            TrudyVoiceUiStatus.SPEAKING
        )
}

interface TrudyVoicePreferenceStore {
    fun load(defaults: TrudyVoicePreferences): TrudyVoicePreferences
    fun save(preferences: TrudyVoicePreferences)
}

class InMemoryTrudyVoicePreferenceStore(
    initial: TrudyVoicePreferences? = null
) : TrudyVoicePreferenceStore {
    private var value = initial
    override fun load(defaults: TrudyVoicePreferences): TrudyVoicePreferences = value ?: defaults
    override fun save(preferences: TrudyVoicePreferences) { value = preferences }
    fun current(): TrudyVoicePreferences? = value
}

/**
 * Runtime bridge used by the controller. Implementations may wrap Kokoro, but the UI controller
 * only sees model availability, install progress and the existing provider-neutral voice runtime.
 */
interface TrudyVoiceRuntimeSource {
    suspend fun inspect(config: TrudyVoiceConfig): TrudyVoiceModelInfo
    suspend fun create(config: TrudyVoiceConfig): TrudyVoiceRuntime
    suspend fun install(
        config: TrudyVoiceConfig,
        onProgress: (TrudyVoiceInstallProgress) -> Unit
    ): TrudyVoiceModelInfo = throw UnsupportedOperationException("Voice model installation is unavailable")
    suspend fun remove(config: TrudyVoiceConfig): TrudyVoiceModelInfo =
        throw UnsupportedOperationException("Voice model removal is unavailable")
}

interface TrudyVoiceController {
    val state: StateFlow<TrudyVoiceUiState>
    fun refresh()
    fun speak(messageId: Long, text: String)
    fun maybeAutoSpeak(messageId: Long, text: String)
    fun stop()
    fun onUserPromptSubmitted()
    fun onTrudyHidden()
    fun setEnabled(enabled: Boolean)
    fun setAutoSpeak(enabled: Boolean)
    fun selectVoice(voiceId: String)
    fun setSpeed(speed: Float)
    fun installModel()
    fun removeModel()
    fun reinstallModel()
    fun retryLastVoiceAction()
    fun dismissPrompt()
    fun close()
}

private enum class FailedVoiceAction { SPEAK, INSTALL, REMOVE }

class DefaultTrudyVoiceController(
    private val runtimeSource: TrudyVoiceRuntimeSource,
    private val preferenceStore: TrudyVoicePreferenceStore,
    private val baseConfig: TrudyVoiceConfig = TrudyVoiceConfig(mode = TrudyVoiceMode.KOKORO_LOCAL),
    scope: CoroutineScope? = null
) : TrudyVoiceController {
    private val ownsScope = scope == null
    private val controllerScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialPreferences = preferenceStore.load(
        TrudyVoicePreferences(selectedVoiceId = baseConfig.voiceId, speed = baseConfig.speed)
    )
    private val mutableState = MutableStateFlow(
        TrudyVoiceUiState(
            status = TrudyVoiceUiStatus.UNAVAILABLE,
            preferences = initialPreferences,
            modelInfo = TrudyVoiceModelInfo(TrudyVoiceModelInstallation.UNAVAILABLE)
        )
    )
    override val state: StateFlow<TrudyVoiceUiState> = mutableState.asStateFlow()

    private var runtime: TrudyVoiceRuntime? = null
    private var speechJob: Job? = null
    private var installJob: Job? = null
    private var speechGeneration = 0L
    private var pendingSpeech: Pair<Long, String>? = null
    private var lastFailedAction: FailedVoiceAction? = null
    private val autoSpokenMessageIds = mutableSetOf<Long>()

    init { refresh() }

    override fun refresh() {
        controllerScope.launch {
            val config = currentConfig()
            val info = runCatching { runtimeSource.inspect(config) }.getOrElse {
                TrudyVoiceModelInfo(
                    installation = TrudyVoiceModelInstallation.UNAVAILABLE,
                    reason = it.message ?: "Local voice is unavailable."
                )
            }
            val current = mutableState.value
            mutableState.value = current.copy(
                status = idleStatus(info, current.preferences.enabled),
                modelInfo = info,
                errorMessage = null
            )
        }
    }

    override fun speak(messageId: Long, text: String) {
        val cleaned = TrudySpeechText.prepare(text)
        if (cleaned.isBlank()) return
        if (mutableState.value.isSpeakingMessage(messageId)) {
            stop()
            return
        }
        startSpeech(messageId, cleaned)
    }

    override fun maybeAutoSpeak(messageId: Long, text: String) {
        if (!mutableState.value.preferences.autoSpeak) return
        if (!autoSpokenMessageIds.add(messageId)) return
        speak(messageId, text)
    }

    private fun startSpeech(messageId: Long, text: String) {
        speechGeneration += 1
        val generation = speechGeneration
        speechJob?.cancel()
        runtime?.service?.stop()
        pendingSpeech = messageId to text

        if (!mutableState.value.preferences.enabled) {
            mutableState.value = mutableState.value.copy(
                status = TrudyVoiceUiStatus.STOPPED,
                activeMessageId = null,
                errorMessage = "Voice is turned off in Trudy voice settings.",
                promptVisible = true
            )
            return
        }

        speechJob = controllerScope.launch {
            try {
                val config = currentConfig()
                val info = runtimeSource.inspect(config)
                if (!isCurrent(generation)) return@launch
                mutableState.value = mutableState.value.copy(modelInfo = info, errorMessage = null)
                when (info.installation) {
                    TrudyVoiceModelInstallation.UNAVAILABLE -> {
                        lastFailedAction = FailedVoiceAction.SPEAK
                        mutableState.value = mutableState.value.copy(
                            status = TrudyVoiceUiStatus.UNAVAILABLE,
                            activeMessageId = messageId,
                            errorMessage = info.reason ?: "Local voice is unavailable.",
                            promptVisible = true
                        )
                        return@launch
                    }
                    TrudyVoiceModelInstallation.NOT_INSTALLED -> {
                        mutableState.value = mutableState.value.copy(
                            status = TrudyVoiceUiStatus.MODEL_NOT_INSTALLED,
                            activeMessageId = messageId,
                            errorMessage = null,
                            promptVisible = true
                        )
                        return@launch
                    }
                    TrudyVoiceModelInstallation.INSTALLED -> Unit
                }

                mutableState.value = mutableState.value.copy(
                    status = TrudyVoiceUiStatus.LOADING,
                    activeMessageId = messageId,
                    errorMessage = null,
                    promptVisible = false
                )
                val activeRuntime = runtime ?: runtimeSource.create(config).also { runtime = it }
                val service = activeRuntime.service
                if (service == null) {
                    lastFailedAction = FailedVoiceAction.SPEAK
                    mutableState.value = mutableState.value.copy(
                        status = TrudyVoiceUiStatus.UNAVAILABLE,
                        activeMessageId = messageId,
                        errorMessage = activeRuntime.diagnostics.reason ?: "Local voice could not start.",
                        promptVisible = true
                    )
                    return@launch
                }

                service.prepare()
                if (!isCurrent(generation)) return@launch
                mutableState.value = mutableState.value.copy(status = TrudyVoiceUiStatus.SYNTHESIZING)
                service.speak(text) {
                    if (isCurrent(generation)) {
                        mutableState.value = mutableState.value.copy(status = TrudyVoiceUiStatus.SPEAKING)
                    }
                }
                if (!isCurrent(generation)) return@launch
                pendingSpeech = null
                lastFailedAction = null
                mutableState.value = mutableState.value.copy(
                    status = TrudyVoiceUiStatus.READY,
                    activeMessageId = null,
                    errorMessage = null,
                    promptVisible = false
                )
            } catch (cancelled: CancellationException) {
                if (isCurrent(generation)) {
                    mutableState.value = mutableState.value.copy(
                        status = TrudyVoiceUiStatus.STOPPED,
                        activeMessageId = null,
                        errorMessage = null
                    )
                }
                throw cancelled
            } catch (error: Throwable) {
                if (!isCurrent(generation)) return@launch
                lastFailedAction = FailedVoiceAction.SPEAK
                mutableState.value = mutableState.value.copy(
                    status = TrudyVoiceUiStatus.ERROR,
                    activeMessageId = messageId,
                    errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "Voice unavailable — retry",
                    promptVisible = true
                )
            }
        }
    }

    override fun stop() {
        speechGeneration += 1
        speechJob?.cancel()
        speechJob = null
        runtime?.service?.stop()
        pendingSpeech = null
        mutableState.value = mutableState.value.copy(
            status = TrudyVoiceUiStatus.STOPPED,
            activeMessageId = null,
            errorMessage = null,
            promptVisible = false
        )
    }

    override fun onUserPromptSubmitted() = stop()

    override fun onTrudyHidden() {
        stop()
        releaseRuntime()
    }

    override fun setEnabled(enabled: Boolean) {
        updatePreferences(mutableState.value.preferences.copy(enabled = enabled))
        if (!enabled) {
            stop()
            releaseRuntime()
        } else refresh()
    }

    override fun setAutoSpeak(enabled: Boolean) {
        updatePreferences(mutableState.value.preferences.copy(autoSpeak = enabled))
    }

    override fun selectVoice(voiceId: String) {
        if (voiceId.isBlank()) return
        val voices = mutableState.value.modelInfo.availableVoices
        if (voices.isNotEmpty() && voices.none { it.id == voiceId }) return
        if (voiceId == mutableState.value.preferences.selectedVoiceId) return
        stop()
        releaseRuntime()
        updatePreferences(mutableState.value.preferences.copy(selectedVoiceId = voiceId))
        refresh()
    }

    override fun setSpeed(speed: Float) {
        val bounded = speed.coerceIn(0.5f, 2.0f)
        val snapped = (round(bounded * 20f) / 20f).coerceIn(0.5f, 2.0f)
        if (snapped == mutableState.value.preferences.speed) return
        stop()
        releaseRuntime()
        updatePreferences(mutableState.value.preferences.copy(speed = snapped))
        refresh()
    }

    override fun installModel() = startInstall(removeFirst = false)

    private fun startInstall(removeFirst: Boolean) {
        val current = mutableState.value
        if (!current.modelInfo.canInstall || (removeFirst && !current.modelInfo.canRemove)) {
            mutableState.value = current.copy(
                status = if (current.modelInfo.installation == TrudyVoiceModelInstallation.UNAVAILABLE) {
                    TrudyVoiceUiStatus.UNAVAILABLE
                } else {
                    TrudyVoiceUiStatus.ERROR
                },
                errorMessage = current.modelInfo.reason ?: "Voice model installation is not available in this build.",
                promptVisible = true
            )
            return
        }
        installJob?.cancel()
        speechGeneration += 1
        speechJob?.cancel()
        runtime?.service?.stop()
        if (removeFirst) pendingSpeech = null
        releaseRuntime()
        installJob = controllerScope.launch {
            var lastPercent: Int? = null
            try {
                val config = currentConfig()
                if (removeFirst) {
                    val removed = runtimeSource.remove(config)
                    mutableState.value = mutableState.value.copy(modelInfo = removed)
                }
                mutableState.value = mutableState.value.copy(
                    status = TrudyVoiceUiStatus.DOWNLOADING,
                    installProgressPercent = 0,
                    errorMessage = null,
                    promptVisible = true
                )
                val info = runtimeSource.install(config) { progress ->
                    val percent = progress.fraction?.let { (it * 100f).toInt().coerceIn(0, 100) }
                        ?: if (progress.downloadedBytes != null && progress.totalBytes != null) {
                            ((progress.downloadedBytes * 100L) / progress.totalBytes).toInt().coerceIn(0, 100)
                        } else null
                    if (percent == null || percent != lastPercent) {
                        lastPercent = percent
                        mutableState.value = mutableState.value.copy(installProgressPercent = percent)
                    }
                }
                mutableState.value = mutableState.value.copy(
                    modelInfo = info,
                    status = idleStatus(info, mutableState.value.preferences.enabled),
                    installProgressPercent = null,
                    errorMessage = null,
                    promptVisible = false
                )
                lastFailedAction = null
                val pending = pendingSpeech
                if (pending != null && info.installation == TrudyVoiceModelInstallation.INSTALLED) {
                    startSpeech(pending.first, pending.second)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastFailedAction = FailedVoiceAction.INSTALL
                mutableState.value = mutableState.value.copy(
                    status = TrudyVoiceUiStatus.ERROR,
                    installProgressPercent = null,
                    errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "Voice model installation failed.",
                    promptVisible = true
                )
            }
        }
    }

    override fun removeModel() {
        val current = mutableState.value
        if (!current.modelInfo.canRemove) return
        stop()
        releaseRuntime()
        controllerScope.launch {
            try {
                val info = runtimeSource.remove(currentConfig())
                mutableState.value = mutableState.value.copy(
                    modelInfo = info,
                    status = idleStatus(info, mutableState.value.preferences.enabled),
                    errorMessage = null,
                    promptVisible = false
                )
                lastFailedAction = null
            } catch (error: Throwable) {
                lastFailedAction = FailedVoiceAction.REMOVE
                mutableState.value = mutableState.value.copy(
                    status = TrudyVoiceUiStatus.ERROR,
                    errorMessage = error.message ?: "Could not remove the voice model.",
                    promptVisible = true
                )
            }
        }
    }

    override fun reinstallModel() = startInstall(removeFirst = true)

    override fun retryLastVoiceAction() {
        when (lastFailedAction) {
            FailedVoiceAction.SPEAK -> pendingSpeech?.let { startSpeech(it.first, it.second) }
            FailedVoiceAction.INSTALL -> installModel()
            FailedVoiceAction.REMOVE -> removeModel()
            null -> pendingSpeech?.let { startSpeech(it.first, it.second) }
        }
    }

    override fun dismissPrompt() {
        pendingSpeech = null
        mutableState.value = mutableState.value.copy(promptVisible = false)
    }

    override fun close() {
        speechGeneration += 1
        speechJob?.cancel()
        installJob?.cancel()
        val closing = runtime
        runtime = null
        closing?.service?.stop()
        controllerScope.launch {
            try {
                runCatching { closing?.service?.close() }
            } finally {
                if (ownsScope) controllerScope.cancel()
            }
        }
    }

    private fun updatePreferences(preferences: TrudyVoicePreferences) {
        preferenceStore.save(preferences)
        mutableState.value = mutableState.value.copy(preferences = preferences)
    }

    private fun releaseRuntime() {
        val closing = runtime ?: return
        runtime = null
        closing.service?.stop()
        controllerScope.launch { runCatching { closing.service?.close() } }
    }

    private fun currentConfig(): TrudyVoiceConfig {
        val preferences = mutableState.value.preferences
        return baseConfig.copy(
            mode = if (preferences.enabled) TrudyVoiceMode.KOKORO_LOCAL else TrudyVoiceMode.OFF,
            voiceId = preferences.selectedVoiceId,
            speed = preferences.speed,
            autoSpeak = preferences.autoSpeak
        )
    }

    private fun isCurrent(generation: Long): Boolean = generation == speechGeneration

    private fun idleStatus(info: TrudyVoiceModelInfo, enabled: Boolean): TrudyVoiceUiStatus {
        if (!enabled) return TrudyVoiceUiStatus.STOPPED
        return when (info.installation) {
            TrudyVoiceModelInstallation.UNAVAILABLE -> TrudyVoiceUiStatus.UNAVAILABLE
            TrudyVoiceModelInstallation.NOT_INSTALLED -> TrudyVoiceUiStatus.MODEL_NOT_INSTALLED
            TrudyVoiceModelInstallation.INSTALLED -> TrudyVoiceUiStatus.READY
        }
    }
}

fun TrudyMessage.supportsVoicePlayback(): Boolean =
    role == TrudyMessageRole.TRUDY && status == TrudyMessageStatus.COMPLETE && text.isNotBlank()

/** Deterministic source for previews and controller tests. It never loads a real model. */
enum class FakeTrudyVoiceScenario { UNAVAILABLE, NOT_INSTALLED, READY, FAILURE }

class FakeTrudyVoiceRuntimeSource(
    initialScenario: FakeTrudyVoiceScenario = FakeTrudyVoiceScenario.READY,
    private val voices: List<TrudyVoiceOption> = listOf(
        TrudyVoiceOption("preview-a", "Preview voice A"),
        TrudyVoiceOption("preview-b", "Preview voice B")
    ),
    private val approximateDownloadBytes: Long? = 42_000_000L,
    private val playbackDelayMs: Long = 250L
) : TrudyVoiceRuntimeSource {
    var scenario: FakeTrudyVoiceScenario = initialScenario
    var failInstallation: Boolean = false
    var installCalls: Int = 0
    var removeCalls: Int = 0
    val createdConfigs = mutableListOf<TrudyVoiceConfig>()
    val spokenTexts = mutableListOf<String>()
    var stopCalls: Int = 0

    override suspend fun inspect(config: TrudyVoiceConfig): TrudyVoiceModelInfo = when (scenario) {
        FakeTrudyVoiceScenario.UNAVAILABLE -> TrudyVoiceModelInfo(
            TrudyVoiceModelInstallation.UNAVAILABLE,
            reason = "Preview voice runtime unavailable."
        )
        FakeTrudyVoiceScenario.NOT_INSTALLED -> TrudyVoiceModelInfo(
            TrudyVoiceModelInstallation.NOT_INSTALLED,
            approximateDownloadBytes = approximateDownloadBytes,
            availableVoices = voices,
            canInstall = true,
            reason = "Preview voice model not installed."
        )
        FakeTrudyVoiceScenario.READY -> TrudyVoiceModelInfo(
            TrudyVoiceModelInstallation.INSTALLED,
            modelVersion = "preview-1",
            availableVoices = voices,
            canInstall = true,
            canRemove = true
        )
        FakeTrudyVoiceScenario.FAILURE -> TrudyVoiceModelInfo(
            TrudyVoiceModelInstallation.INSTALLED,
            modelVersion = "preview-1",
            availableVoices = voices,
            canInstall = true,
            canRemove = true
        )
    }

    override suspend fun create(config: TrudyVoiceConfig): TrudyVoiceRuntime {
        createdConfigs += config
        if (scenario == FakeTrudyVoiceScenario.UNAVAILABLE) {
            return TrudyVoiceRuntime(
                service = null,
                diagnostics = TrudyVoiceRuntimeDiagnostics(
                    requestedMode = config.mode,
                    activeMode = TrudyVoiceMode.OFF,
                    engineId = null,
                    modelId = config.modelId,
                    voiceId = config.voiceId,
                    available = false,
                    reason = "Preview runtime unavailable."
                )
            )
        }
        val engine = object : TrudySpeechEngine {
            override val engineId = "preview"
            override suspend fun isAvailable() = true
            override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult {
                if (scenario == FakeTrudyVoiceScenario.FAILURE) error("Preview synthesis failure")
                spokenTexts += request.text
                return TrudySpeechResult(
                    TrudyPcmAudio(floatArrayOf(0f, 0.1f, -0.1f), 24_000),
                    TrudySpeechDiagnostics(engineId, config.modelId, request.voiceId, 24_000)
                )
            }
        }
        val sink = object : TrudyAudioSink {
            override suspend fun play(audio: TrudyPcmAudio) { delay(playbackDelayMs) }
            override fun stop() { stopCalls++ }
        }
        val service = TrudyVoiceService(config, engine, sink)
        return TrudyVoiceRuntime(
            service = service,
            diagnostics = TrudyVoiceRuntimeDiagnostics(
                requestedMode = config.mode,
                activeMode = TrudyVoiceMode.KOKORO_LOCAL,
                engineId = engine.engineId,
                modelId = config.modelId,
                voiceId = config.voiceId,
                available = true
            )
        )
    }

    override suspend fun install(
        config: TrudyVoiceConfig,
        onProgress: (TrudyVoiceInstallProgress) -> Unit
    ): TrudyVoiceModelInfo {
        installCalls++
        onProgress(TrudyVoiceInstallProgress(.1f, 4_200_000, approximateDownloadBytes))
        delay(10)
        onProgress(TrudyVoiceInstallProgress(.5f, 21_000_000, approximateDownloadBytes))
        delay(10)
        if (failInstallation) error("Preview installation failed")
        onProgress(TrudyVoiceInstallProgress(1f, approximateDownloadBytes, approximateDownloadBytes))
        scenario = FakeTrudyVoiceScenario.READY
        return inspect(config)
    }

    override suspend fun remove(config: TrudyVoiceConfig): TrudyVoiceModelInfo {
        removeCalls++
        scenario = FakeTrudyVoiceScenario.NOT_INSTALLED
        return inspect(config)
    }
}
