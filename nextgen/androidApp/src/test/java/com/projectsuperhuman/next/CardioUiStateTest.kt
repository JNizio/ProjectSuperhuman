package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardioUiStateTest {
    @Test
    fun savingStateDisablesDuplicateSaveIntent() {
        val state = CardioUiState(saveState = CardioSaveState.SAVING)
        assertTrue(state.saveInProgress)
    }

    @Test
    fun liveRecordingStateComesFromPersistedDraftPhase() {
        val draft = CardioLiveDraft(
            sessionId = "state-test",
            activity = CardioActivityType.RUNNING,
            startedAtEpochMs = 1_800_000_000_000L,
            phase = CardioLivePhase.RECORDING,
            phaseStartedEpochMs = 1_800_000_000_000L,
            phaseStartedElapsedRealtimeMs = 10_000L
        )
        assertTrue(CardioUiState(liveDraft = draft).isRecording)
        assertFalse(CardioUiState(liveDraft = draft.copy(phase = CardioLivePhase.PAUSED)).isRecording)
    }
}
