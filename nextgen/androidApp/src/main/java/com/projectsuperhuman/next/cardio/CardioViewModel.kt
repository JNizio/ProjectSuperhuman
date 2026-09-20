package com.projectsuperhuman.next

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class CardioPendingLiveEvidence(
    val session: CardioSession,
    val heartRateSamples: List<CardioHeartRateSample>,
    val rrIntervals: List<CardioRrIntervalSample>,
    val telemetry: CardioLiveTelemetrySnapshot?
)

internal class CardioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DataStoreCardioSessionStore(application)
    private val repository: CardioRepository = DataVaultCardioRepository()
    private val controller = CardioLiveSessionController()
    private val coordinator = CardioSessionCoordinator(store, repository, controller)
    private val nof1Repository = CardioNof1Repository()

    private val _state = MutableStateFlow(CardioUiState())
    val state: StateFlow<CardioUiState> = _state.asStateFlow()
    private var pendingLiveSensorSummary: CardioHeartRateSummary? = null
    private var pendingLiveEvidence: CardioPendingLiveEvidence? = null

    init {
        NativeDataHub.initialize(application)
        CardioSensorRuntime.initialize(application)
        CardioGpsRuntime.initialize(application)

        viewModelScope.launch {
            val restore = store.migrateLegacyIfNeeded()
            if (restore.draft != null) {
                // Re-establish both the foreground owner and sensor collection around the same
                // durable session identity whenever a recoverable draft is discovered.
                CardioSessionForeground.start(getApplication())
                CardioSensorRuntime.startSession(
                    restore.draft.sessionId,
                    restore.draft.startedAtEpochMs
                )
                CardioGpsRuntime.restoreSession(
                    restore.draft.sessionId,
                    restore.draft.activity,
                    restore.draft.startedAtEpochMs,
                    paused = restore.draft.phase != CardioLivePhase.RECORDING
                )
                if (restore.draft.phase != CardioLivePhase.RECORDING) {
                    CardioSensorRuntime.pauseSession(System.currentTimeMillis())
                }
            }
            runCatching { CardioSensorRuntime.reconnectPreferred() }
            _state.update {
                it.copy(
                    restoredSession = restore.draft != null,
                    incompatibleDraftSchemaVersion = restore.incompatibleSchemaVersion,
                    feedback = when {
                        restore.incompatibleSchemaVersion != null ->
                            "A newer Cardio draft format was found. It was left untouched."
                        restore.draft != null && restore.restoredFromLegacy ->
                            "Previous cardio session restored and migrated"
                        restore.draft != null ->
                            "Previous cardio session restored"
                        else -> it.feedback
                    }
                )
            }

            store.observe().collect { draft ->
                val timing = draft?.let(controller::timing)
                _state.update {
                    it.copy(
                        liveDraft = draft,
                        liveElapsedSeconds = timing?.activeSeconds ?: 0,
                        pausedElapsedSeconds = timing?.pausedSeconds ?: 0
                    )
                }
            }
        }

        viewModelScope.launch { refreshRecent() }

        viewModelScope.launch {
            while (isActive) {
                val draft = _state.value.liveDraft
                if (draft != null) {
                    CardioGpsRuntime.tick()
                    val timing = controller.timing(draft)
                    _state.update {
                        it.copy(
                            liveElapsedSeconds = timing.activeSeconds,
                            pausedElapsedSeconds = timing.pausedSeconds,
                            undo = it.undo?.takeIf { token ->
                                token.expiresAtEpochMs > System.currentTimeMillis()
                            }
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            undo = it.undo?.takeIf { token ->
                                token.expiresAtEpochMs > System.currentTimeMillis()
                            }
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    fun start(
        activity: CardioActivityType,
        workoutType: CardioWorkoutType,
        onStarted: () -> Unit = {}
    ) {
        if (_state.value.liveDraft != null) {
            onStarted()
            return
        }
        viewModelScope.launch {
            runCatching { coordinator.start(activity, workoutType) }
                .onSuccess { draft ->
                    CardioSensorRuntime.startSession(draft.sessionId, draft.startedAtEpochMs)
                    CardioGpsRuntime.startSession(draft.sessionId, draft.activity, draft.startedAtEpochMs)
                    pendingLiveSensorSummary = null
                    pendingLiveEvidence = null
                    CardioSessionForeground.start(getApplication())
                    setFeedback("Recording started")
                    onStarted()
                }
                .onFailure { setFailure(it.message ?: "Could not start cardio session") }
        }
    }

    fun pause() {
        viewModelScope.launch {
            runCatching { coordinator.pause() }
                .onSuccess {
                    val now = System.currentTimeMillis()
                    CardioSensorRuntime.pauseSession(now)
                    CardioGpsRuntime.pause(now, manual = true)
                    setFeedback("Cardio timer paused")
                }
                .onFailure { setFailure(it.message ?: "Could not pause cardio session") }
        }
    }

    fun resume() {
        viewModelScope.launch {
            runCatching { coordinator.resume() }
                .onSuccess {
                    val now = System.currentTimeMillis()
                    CardioSensorRuntime.resumeSession(now)
                    CardioGpsRuntime.resume(now, manual = true)
                    CardioSessionForeground.start(getApplication())
                    setFeedback("Cardio timer resumed")
                }
                .onFailure { setFailure(it.message ?: "Could not resume cardio session") }
        }
    }

    fun prepareDetailedFinish(onReady: (CardioSession) -> Unit) {
        if (_state.value.saveInProgress) return
        viewModelScope.launch {
            runCatching { coordinator.prepareDetailedFinish() }
                .onSuccess { prepared ->
                    if (prepared == null) {
                        setFailure("No active cardio session")
                    } else {
                        val evidence = captureLiveEvidence(prepared.session)
                        pendingLiveSensorSummary = CardioSensorRuntime.snapshot(prepared.session.endedAt)
                            .takeIf { it.sampleCount > 0 }
                        pendingLiveEvidence = evidence
                        onReady(evidence.session)
                    }
                }
                .onFailure { setFailure(it.message ?: "Could not prepare cardio finish") }
        }
    }

    fun quickSave(onComplete: (Boolean) -> Unit = {}) {
        if (_state.value.saveInProgress) return
        _state.update {
            it.copy(saveState = CardioSaveState.SAVING, feedback = "Saving cardio session…")
        }

        viewModelScope.launch {
            var evidence: CardioPendingLiveEvidence? = null
            val result = runCatching {
                coordinator.quickSave { session ->
                    captureLiveEvidence(session).also { evidence = it }.session
                }
            }
                .getOrElse {
                    setFailure(it.message ?: "Cardio save failed")
                    onComplete(false)
                    return@launch
                }

            if (result.success && result.session != null) {
                val telemetryResult = evidence?.let {
                    nof1Repository.persistLiveTelemetry(
                        result.session,
                        it.heartRateSamples,
                        it.rrIntervals,
                        it.telemetry
                    )
                }
                CardioSensorRuntime.stopSession(result.session.endedAt)
                CardioGpsRuntime.stop(result.session.endedAt)
                pendingLiveSensorSummary = null
                pendingLiveEvidence = null
                CardioSessionForeground.stop(getApplication())
                refreshRecent()
                _state.update {
                    it.copy(
                        saveState = CardioSaveState.SAVED,
                        feedback = if (telemetryResult?.success == false) {
                            "Workout saved; " + telemetryResult.failedCount + " telemetry write(s) need recovery"
                        } else {
                            "Cardio session saved"
                        },
                        undo = CardioUndoState(
                            session = result.session,
                            expiresAtEpochMs = System.currentTimeMillis() + UNDO_WINDOW_MS
                        )
                    )
                }
                onComplete(true)
            } else {
                setFailure(result.message + ". Session draft was preserved.")
                onComplete(false)
            }
        }
    }

    fun saveSession(
        session: CardioSession,
        editing: Boolean,
        finishingLive: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (_state.value.saveInProgress) return
        _state.update {
            it.copy(saveState = CardioSaveState.SAVING, feedback = "Saving cardio session…")
        }

        viewModelScope.launch {
            val evidence = if (finishingLive) {
                pendingLiveEvidence ?: captureLiveEvidence(session)
            } else null
            val finalSession = if (finishingLive) {
                val enriched = evidence?.session ?: session
                session.copy(
                    distanceKm = session.distanceKm ?: enriched.distanceKm,
                    avgHeartRate = session.avgHeartRate ?: enriched.avgHeartRate,
                    minHeartRate = session.minHeartRate ?: enriched.minHeartRate,
                    maxHeartRate = session.maxHeartRate ?: enriched.maxHeartRate,
                    avgPaceSecPerKm = session.avgPaceSecPerKm ?: enriched.avgPaceSecPerKm,
                    avgSpeedKmh = session.avgSpeedKmh ?: enriched.avgSpeedKmh,
                    elevationGainM = session.elevationGainM ?: enriched.elevationGainM,
                    zoneSeconds = if (session.zoneSeconds.isNotEmpty()) session.zoneSeconds else enriched.zoneSeconds,
                    zoneSchemeId = session.zoneSchemeId ?: enriched.zoneSchemeId,
                    extensions = enriched.extensions + session.extensions
                )
            } else {
                session
            }
            val result = runCatching {
                when {
                    finishingLive -> coordinator.saveDetailedLive(finalSession)
                    editing -> coordinator.updateCompleted(finalSession)
                    else -> coordinator.saveManual(finalSession)
                }
            }.getOrElse {
                setFailure(it.message ?: "Cardio save failed")
                onComplete(false)
                return@launch
            }

            if (result.success) {
                val telemetryResult = if (finishingLive && evidence != null) {
                    nof1Repository.persistLiveTelemetry(
                        finalSession,
                        evidence.heartRateSamples,
                        evidence.rrIntervals,
                        evidence.telemetry
                    )
                } else null
                if (finishingLive) {
                    CardioSensorRuntime.stopSession(finalSession.endedAt)
                    CardioGpsRuntime.stop(finalSession.endedAt)
                    pendingLiveSensorSummary = null
                    pendingLiveEvidence = null
                    CardioSessionForeground.stop(getApplication())
                }
                refreshRecent()
                _state.update {
                    it.copy(
                        saveState = CardioSaveState.SAVED,
                        feedback = when {
                            telemetryResult?.success == false ->
                                "Workout saved; " + telemetryResult.failedCount + " telemetry write(s) need recovery"
                            editing -> "Cardio session updated"
                            else -> "Cardio session saved"
                        }
                    )
                }
                onComplete(true)
            } else {
                setFailure(
                    if (finishingLive) result.message + ". Live draft was preserved." else result.message
                )
                onComplete(false)
            }
        }
    }

    fun discardLive(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { coordinator.discard() }.getOrDefault(false)
            if (ok) {
                val now = System.currentTimeMillis()
                CardioSensorRuntime.stopSession(now)
                CardioGpsRuntime.stop(now)
                pendingLiveSensorSummary = null
                pendingLiveEvidence = null
                CardioSessionForeground.stop(getApplication())
                _state.update {
                    it.copy(
                        feedback = "Workout discarded",
                        undo = null,
                        saveState = CardioSaveState.IDLE
                    )
                }
                onComplete()
            } else {
                setFailure("Could not discard the workout")
            }
        }
    }

    fun undoQuickSave() {
        val token = _state.value.undo ?: return
        if (token.expiresAtEpochMs <= System.currentTimeMillis()) {
            _state.update { it.copy(undo = null) }
            return
        }

        viewModelScope.launch {
            val result = runCatching { coordinator.undoQuickSave(token.session) }
                .getOrElse {
                    setFailure(it.message ?: "Could not restore saved workout")
                    return@launch
                }

            if (result.restored) {
                CardioSensorRuntime.startSession(token.session.id, token.session.startedAt)
                CardioGpsRuntime.startSession(token.session.id, token.session.activity, token.session.startedAt)
                val now = System.currentTimeMillis()
                CardioSensorRuntime.pauseSession(now)
                CardioGpsRuntime.pause(now, manual = true)
                CardioSessionForeground.start(getApplication())
                refreshRecent()
                _state.update {
                    it.copy(
                        undo = null,
                        saveState = CardioSaveState.IDLE,
                        feedback = if (result.completedCopyDeleted) {
                            "Saved workout restored as a paused live session"
                        } else {
                            "Workout restored. Saved copy will be replaced on the next save."
                        }
                    )
                }
            }
        }
    }

    fun manualLap(): CardioLap? = CardioGpsRuntime.manualLap()

    fun setAutoPauseEnabled(enabled: Boolean) {
        CardioGpsRuntime.setAutoPauseEnabled(enabled)
        setFeedback(if (enabled) "Auto-pause enabled" else "Auto-pause disabled")
    }

    fun setStructuredWorkout(workout: CardioStructuredWorkout?) {
        CardioGpsRuntime.setStructuredWorkout(workout)
    }

    fun deleteSession(sessionId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = repository.delete(sessionId)
            if (deleted) {
                refreshRecent()
                setFeedback("Cardio session deleted")
            } else {
                setFailure("Could not delete cardio session")
            }
            onComplete(deleted)
        }
    }

    fun loadMoreHistory() {
        if (!_state.value.canLoadMoreHistory) return
        viewModelScope.launch {
            val current = _state.value.sessions
            val page = repository.pageHistory(HISTORY_PAGE_SIZE, current.size)
            _state.update {
                it.copy(
                    sessions = (current + page).distinctBy(CardioSession::id),
                    canLoadMoreHistory = page.size == HISTORY_PAGE_SIZE
                )
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(feedback = null, restoredSession = false) }
    }

    private fun captureLiveEvidence(session: CardioSession): CardioPendingLiveEvidence {
        CardioSensorRuntime.pauseSession(session.endedAt)
        CardioGpsRuntime.pause(session.endedAt, manual = true)
        val summary = CardioSensorRuntime.snapshot(session.endedAt).takeIf { it.sampleCount > 0 }
        val telemetry = CardioGpsRuntime.snapshot(session.endedAt)
        var enriched = summary?.let(session::withCardioHeartRateSummary) ?: session
        enriched = CardioGpsRuntime.enrichSession(enriched, telemetry)
        return CardioPendingLiveEvidence(
            session = enriched,
            heartRateSamples = CardioSensorRuntime.rawHeartRateSamples(),
            rrIntervals = CardioSensorRuntime.rawRrIntervals(),
            telemetry = telemetry
        )
    }

    private suspend fun refreshRecent() {
        val sessions = repository.recentSessions(HISTORY_PAGE_SIZE)
        _state.update {
            it.copy(
                sessions = sessions,
                canLoadMoreHistory = sessions.size == HISTORY_PAGE_SIZE
            )
        }
    }

    private fun setFeedback(message: String) {
        _state.update {
            it.copy(
                feedback = message,
                saveState = if (it.saveState == CardioSaveState.SAVING) {
                    CardioSaveState.IDLE
                } else {
                    it.saveState
                }
            )
        }
    }

    private fun setFailure(message: String) {
        _state.update {
            it.copy(saveState = CardioSaveState.FAILED, feedback = message)
        }
    }

    private companion object {
        const val HISTORY_PAGE_SIZE = 250
        const val UNDO_WINDOW_MS = 10_000L
    }
}
