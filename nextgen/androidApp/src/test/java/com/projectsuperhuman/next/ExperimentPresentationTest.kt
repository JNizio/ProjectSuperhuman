package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentPresentationTest {
    @Test
    fun progressFractionIsBounded() {
        assertEquals(0f, ExperimentProgress(-2, 7, 0, 7).fraction)
        assertEquals(1f, ExperimentProgress(9, 7, 7, 7).fraction)
    }

    @Test
    fun measurementStepRequiresASelectedPrimaryOutcome() {
        val noOutcome = ExperimentDraft()
        val withoutPrimary = ExperimentDraft(outcomes = setOf("Sleep"))
        val complete = withoutPrimary.copy(primaryOutcome = "Sleep")

        assertFalse(noOutcome.canContinue(ExperimentCreationStep.MEASURING))
        assertFalse(withoutPrimary.canContinue(ExperimentCreationStep.MEASURING))
        assertTrue(complete.canContinue(ExperimentCreationStep.MEASURING))
    }

    @Test
    fun scheduleRejectsPhasesLongerThanExperiment() {
        val invalid = ExperimentDraft(durationDays = 7, baselineDays = 3, interventionDays = 6)
        assertFalse(invalid.canContinue(ExperimentCreationStep.SCHEDULE))
    }

    @Test
    fun draftPreviewStaysClearlyIncompleteAndHasNoResult() {
        val draft = ExperimentDraft(
            testingTarget = "Sleep",
            intervention = "Light",
            outcomes = setOf("Sleep", "Mood"),
            primaryOutcome = "Sleep"
        )

        val preview = MockExperimentData.fromDraft(draft)

        assertEquals(ExperimentStatus.DRAFT, preview.status)
        assertEquals("Sleep", preview.primaryOutcome?.title)
        assertNull(preview.result)
        assertTrue(preview.notes.orEmpty().contains("not been saved"))
    }

    @Test
    fun activeMockCommunicatesAnEarlyNonFinalSignal() {
        assertFalse(MockExperimentData.active.result?.isFinal ?: true)
        assertEquals(ExperimentConfidence.TOO_EARLY, MockExperimentData.active.result?.confidence)
    }
}
