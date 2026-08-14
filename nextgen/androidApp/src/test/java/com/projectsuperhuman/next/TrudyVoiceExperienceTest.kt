package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrudyVoiceExperienceTest {
    @Test
    fun speakingUsesExistingTtsState() {
        assertEquals(
            TrudyVoiceExperienceState.SPEAKING,
            resolveTrudyVoiceExperienceState(
                inputStatus = TrudySpeechInputStatus.IDLE,
                conversationThinking = false,
                conversationError = false,
                outputStatus = TrudyVoiceUiStatus.SPEAKING
            )
        )
    }

    @Test
    fun activeMicrophoneRemainsListeningWhileTtsAvailabilityLoads() {
        assertEquals(
            TrudyVoiceExperienceState.LISTENING,
            resolveTrudyVoiceExperienceState(
                inputStatus = TrudySpeechInputStatus.LISTENING,
                conversationThinking = false,
                conversationError = false,
                outputStatus = TrudyVoiceUiStatus.UNAVAILABLE
            )
        )
    }

    @Test
    fun noInputHasCalmDistinctState() {
        assertEquals(
            TrudyVoiceExperienceState.NO_INPUT,
            resolveTrudyVoiceExperienceState(
                inputStatus = TrudySpeechInputStatus.NO_INPUT,
                conversationThinking = false,
                conversationError = false,
                outputStatus = TrudyVoiceUiStatus.READY
            )
        )
    }

    @Test
    fun contextLabelsComeOnlyFromExplicitRuntimeEvidence() {
        val reply = TrudyReply(
            text = "Your answer",
            evidence = listOf(
                TrudyEvidenceItem(id = "sleep_duration", label = "Sleep duration", detail = "7h"),
                TrudyEvidenceItem(id = "resting_heart_rate", label = "Resting heart rate", detail = "61 bpm")
            )
        )
        assertEquals(
            listOf(TrudyVoiceContextModule.SLEEP, TrudyVoiceContextModule.VITALS),
            TrudyVoiceContextMapper.modulesFor(reply)
        )
        assertTrue(TrudyVoiceContextMapper.modulesFor(TrudyReply(text = "No evidence")).isEmpty())
    }

    @Test
    fun microphoneAmplitudeIsBounded() {
        assertEquals(0f, normaliseTrudyRmsAmplitude(-20f))
        assertEquals(0f, normaliseTrudyRmsAmplitude(Float.NaN))
        assertEquals(1f, normaliseTrudyRmsAmplitude(20f))
        assertTrue(normaliseTrudyRmsAmplitude(4f) in 0f..1f)
    }
}
