package com.projectsuperhuman.next

internal enum class CardioSaveState {
    IDLE,
    SAVING,
    SAVED,
    FAILED
}

internal data class CardioUndoState(
    val session: CardioSession,
    val expiresAtEpochMs: Long
)

internal data class CardioUiState(
    val sessions: List<CardioSession> = emptyList(),
    val liveDraft: CardioLiveDraft? = null,
    val liveElapsedSeconds: Int = 0,
    val pausedElapsedSeconds: Int = 0,
    val saveState: CardioSaveState = CardioSaveState.IDLE,
    val feedback: String? = null,
    val restoredSession: Boolean = false,
    val incompatibleDraftSchemaVersion: Int? = null,
    val undo: CardioUndoState? = null,
    val canLoadMoreHistory: Boolean = false
) {
    val saveInProgress: Boolean get() = saveState == CardioSaveState.SAVING
    val isRecording: Boolean get() = liveDraft?.phase == CardioLivePhase.RECORDING
}
