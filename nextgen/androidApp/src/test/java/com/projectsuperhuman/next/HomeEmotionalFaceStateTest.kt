package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeEmotionalFaceStateTest {
    @Test
    fun happinessStrengthensSmileWithoutForcingOtherAxes() {
        val neutral = EmotionalFaceMapper.from(emptyMap())
        val happy = EmotionalFaceMapper.from(mapOf(EmotionalPresentationContract.HAPPY_SAD to -80))

        assertTrue(happy.mouthCurve > neutral.mouthCurve)
        assertEquals(neutral.browTension, happy.browTension)
    }

    @Test
    fun anxietyOpensEyesAndTensesBrows() {
        val calm = EmotionalFaceMapper.from(mapOf(EmotionalPresentationContract.CALM_ANXIOUS to -75))
        val anxious = EmotionalFaceMapper.from(mapOf(EmotionalPresentationContract.CALM_ANXIOUS to 75))

        assertTrue(anxious.eyeOpen > calm.eyeOpen)
        assertTrue(anxious.browRaise > calm.browRaise)
        assertTrue(anxious.browTension > calm.browTension)
    }

    @Test
    fun drainedEnergyProducesHeavierEyelids() {
        val energetic = EmotionalFaceMapper.from(mapOf(EmotionalPresentationContract.ENERGETIC_DRAINED to -85))
        val drained = EmotionalFaceMapper.from(mapOf(EmotionalPresentationContract.ENERGETIC_DRAINED to 85))

        assertTrue(drained.eyelidDroop > energetic.eyelidDroop)
        assertTrue(drained.eyeOpen < energetic.eyeOpen)
    }

    @Test
    fun partialCheckInDoesNotDiluteObservedValence() {
        val partial = EmotionalFaceMapper.from(mapOf(EmotionalPresentationContract.HAPPY_SAD to -60))
        val complete = EmotionalFaceMapper.from(
            EmotionalPresentationContract.axisIds.associateWith { axis ->
                if (axis == EmotionalPresentationContract.HAPPY_SAD) -60 else 0
            }
        )

        assertEquals(0.6f, partial.mouthCurve, 0.0001f)
        assertTrue(partial.mouthCurve > complete.mouthCurve)
    }

    @Test
    fun freshnessUsesRelativeCopyForRecentCheckIn() {
        val now = 10_000_000L
        val snapshot = EmotionalPresentationSnapshot(
            axisValues = mapOf(EmotionalPresentationContract.FOCUSED_DISTRACTED to -20),
            recordedAtLabel = "Aug 14 · 12:00",
            recordedAtEpochMs = now - 34L * 60_000L
        )

        assertEquals("Checked in 34m ago", emotionalFreshnessLabel(snapshot, now))
    }
}
