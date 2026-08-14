package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TrudyEvidenceContinuityTest {
    @Test
    fun completedAssistantEvidenceIsPassedIntoTheNextTurn() {
        val state = TrudyConversationState()
        val first = assertNotNull(state.beginSend("How has my sleep been?"))
        state.complete(
            first,
            TrudyControllerResult.Success(
                TrudyReply(
                    "Sleep answer",
                    evidence = listOf(TrudyEvidenceItem("SLEEP:sleep_score:123", "Sleep · sleep score"))
                )
            )
        )

        val followUp = assertNotNull(state.beginSend("What data are you basing that on?"))

        val assistantTurn = followUp.conversationRequest.history.last { it.role == TrudyMessageRole.TRUDY }
        assertEquals(listOf("SLEEP:sleep_score:123"), assistantTurn.evidenceKeys)
    }
}
