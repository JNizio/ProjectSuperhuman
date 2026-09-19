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

internal class CardioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DataStoreCardioSessionStore(application)
    private val repository: CardioRepository = DataVaultCardioRepository()
    private val controller = CardioLiveSessionController()
    private val coordinator = CardioSessionCoordinator(store, repository, controller)

    private val _state = MutableStateFlow(CardioUiState())
    val state: StateFlow<CardioUiState> = _state.asStateFlow()
    private var pendingLiveSensorSummary: CardioHeartRateSummary? = null

    init {
        NativeDataHub.initialize(application)
        CardioSensorRuntime.initialize(application)

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
                    pendingLiveSensorSummary = null
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
                    CardioSensorRuntime.pauseSession(System.currentTimeMillis())
                    setFeedback("Cardio timer paused")
                }
                .onFailure { setFailure(it.message ?: "Could not pause cardio session") }
        }
    }

    fun resume() {
        viewModelScope.launch {
            runCatching { coordinator.resume() }
                .onSuccess {
                    CardioSensorRuntime.resumeSession(System.currentTimeMillis())
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
                        CardioSensorRuntime.pauseSession(prepared.session.endedAt)
                        val summary = CardioSensorRuntime.snapshot(prepared.session.endedAt)
                            .takeIf { it.sampleCount > 0 }
                        pendingLiveSensorSummary = summary
                        onReady(
                            summary?.let(prepared.session::withCardioHeartRateSummary)
                                ?: prepared.session
                        )
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
            val result = runCatching {
                coordinator.quickSave { session ->
                    CardioSensorRuntime.pauseSession(session.endedAt)
                    val summary = CardioSensorRuntime.snapshot(session.endedAt)
                    if (summary.sampleCount > 0) session.withCardioHeartRateSummary(summary) else session
                }
            }
                .getOrElse {
                    setFailure(it.message ?: "Cardio save failed")
                    onComplete(false)
                    return@launch
                }

            if (result.success && result.session != null) {
                CardioSensorRuntime.stopSession(result.session.endedAt)
                pendingLiveSensorSummary = null
                CardioSessionForeground.stop(getApplication())
                refreshRecent()
                _state.update {
                    it.copy(
                        saveState = CardioSaveState.SAVED,
                        feedback = "Cardio session saved",
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
            val finalSession = if (finishingLive) {
                val summary = pendingLiveSensorSummary
                    ?: CardioSensorRuntime.snapshot(session.endedAt).takeIf { it.sampleCount > 0 }
                summary?.let(session::withCardioHeartRateSummary) ?: session
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
                if (finishingLive) {
                    CardioSensorRuntime.stopSession(finalSession.endedAt)
                    pendingLiveSensorSummary = null
                    CardioSessionForeground.stop(getApplication())
                }
                refreshRecent()
                _state.update {
                    it.copy(
                        saveState = CardioSaveState.SAVED,
                        feedback = if (editing) "Cardio session updated" else "Cardio session saved"
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
                CardioSensorRuntime.stopSession(System.currentTimeMillis())
                pendingLiveSensorSummary = null
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
                CardioSensorRuntime.pauseSession(System.currentTimeMillis())
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
