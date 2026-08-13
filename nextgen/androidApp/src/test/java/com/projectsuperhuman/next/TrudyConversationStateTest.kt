package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TrudyConversationStateTest {
    @Test
    fun blankInputIsIgnored() {
        val state = TrudyConversationState()
        state.updateInput("   ")

        assertNull(state.beginSend())
        assertTrue(state.uiState.messages.isEmpty())
        assertFalse(state.uiState.isThinking)
    }

    @Test
    fun sendingAppendsUserAndPlaceholderAndClearsDraft() {
        val state = TrudyConversationState()
        state.updateInput("What changed today?")

        val request = assertNotNull(state.beginSend())

        assertEquals("", state.inputText)
        assertEquals(2, state.uiState.messages.size)
        assertEquals(TrudyMessageRole.USER, state.uiState.messages[0].role)
        assertEquals(TrudyMessageStatus.SENDING, state.uiState.messages[1].status)
        assertEquals(request.requestId, state.uiState.activeRequestId)
        assertTrue(state.uiState.isThinking)
    }

    @Test
    fun duplicateSendIsPreventedWhileRequestIsActive() {
        val state = TrudyConversationState()
        assertNotNull(state.beginSend("First"))

        assertNull(state.beginSend("First"))
        assertNull(state.beginSend("Second"))
        assertEquals(2, state.uiState.messages.size)
    }

    @Test
    fun successfulResponseReplacesPlaceholderAndPreservesStructuredMetadata() {
        val state = TrudyConversationState()
        val request = assertNotNull(state.beginSend("How was my sleep?"))
        val evidence = TrudyEvidenceItem("sleep_score", "Sleep · sleep score", "Data quality: stale", TrudyEvidenceKind.METRIC)
        val notice = TrudyNotice("Based on limited data", TrudyNoticeLevel.CAUTION)

        assertTrue(state.complete(request, TrudyControllerResult.Success(
            TrudyReply("A structured answer", evidence = listOf(evidence), notices = listOf(notice))
        )))

        val assistant = state.uiState.messages.last()
        assertEquals(TrudyMessageStatus.COMPLETE, assistant.status)
        assertEquals("A structured answer", assistant.text)
        assertEquals(listOf(evidence), assistant.evidence)
        assertEquals(listOf(notice), assistant.notices)
        assertFalse(state.uiState.isThinking)
    }

    @Test
    fun failureReplacesPlaceholderAndCanRetryWithoutDuplicatingUserMessage() {
        val state = TrudyConversationState()
        val first = assertNotNull(state.beginSend("What changed?"))
        state.fail(first, "Backend unavailable", retryable = true)

        assertEquals(TrudyMessageStatus.ERROR, state.uiState.messages.last().status)
        assertEquals("Backend unavailable", state.uiState.errorMessage)
        assertFalse(state.uiState.isThinking)

        val retry = assertNotNull(state.retryFailed())
        assertEquals(2, state.uiState.messages.size)
        assertEquals(TrudyMessageStatus.SENDING, state.uiState.messages.last().status)
        assertEquals("What changed?", retry.conversationRequest.text)
        assertTrue(state.uiState.isThinking)
    }

    @Test
    fun staleCompletionCannotOverwriteRetriedRequest() {
        val state = TrudyConversationState()
        val first = assertNotNull(state.beginSend("Question"))
        state.fail(first, "Temporary failure")
        val retry = assertNotNull(state.retryFailed())

        assertFalse(state.complete(first, TrudyControllerResult.Success(TrudyReply("stale"))))
        assertTrue(state.complete(retry, TrudyControllerResult.Success(TrudyReply("fresh"))))
        assertEquals("fresh", state.uiState.messages.last().text)
    }

    @Test
    fun evidenceExpansionStateTogglesAndSurvivesSnapshotRecreation() {
        val state = TrudyConversationState()
        val request = assertNotNull(state.beginSend("Question"))
        state.complete(request, TrudyControllerResult.Success(
            TrudyReply("Answer", evidence = listOf(TrudyEvidenceItem("derived_trend", "Derived trend")))
        ))
        val assistantId = state.uiState.messages.last().id
        state.toggleEvidence(assistantId)
        state.updateInput("draft")

        val restored = TrudyConversationState(state.snapshot())

        assertTrue(assistantId in restored.uiState.expandedEvidenceMessageIds)
        assertEquals("draft", restored.inputText)
        assertEquals("Answer", restored.uiState.messages.last().text)
    }

    @Test
    fun inFlightSnapshotRestoresAsRetryableInterruptedError() {
        val state = TrudyConversationState()
        state.beginSend("Question")

        val restored = TrudyConversationState(state.snapshot())

        assertFalse(restored.uiState.isThinking)
        assertEquals(TrudyMessageStatus.ERROR, restored.uiState.messages.last().status)
        assertTrue(restored.uiState.messages.last().retryable)
        assertNotNull(restored.retryFailed())
    }

    @Test
    fun localControllerReturnsStructuredFallback() = runBlocking {
        val result = LocalTrudyConversationController().respondTo(TrudyConversationRequest("How was my sleep?"))
        val success = result as TrudyControllerResult.Success

        assertTrue(success.reply.text.startsWith("Demo mode:"))
        assertTrue(success.reply.evidence.isNotEmpty())
        assertTrue(success.reply.notices.isNotEmpty())
    }

    @Test
    fun sharedControllerMapsBackendDtosWithoutHealthOrUiTypesAtBoundary() = runBlocking {
        val controller = SharedTrudyConversationController(object : TrudyConversationBackend {
            override suspend fun send(request: TrudyConversationRequest) = TrudyBackendResult(
                text = "Backend answer",
                evidence = listOf(
                    TrudyBackendEvidence(
                        id = "body_weight_kg",
                        label = "Body · body weight",
                        kind = TrudyBackendEvidenceKind.METRIC
                    )
                ),
                notices = listOf(TrudyBackendNotice("Data is stale"))
            )
        })

        val result = controller.respondTo(TrudyConversationRequest("Body?")) as TrudyControllerResult.Success
        assertEquals("Backend answer", result.reply.text)
        assertEquals("body_weight_kg", result.reply.evidence.single().id)
        assertEquals(TrudyEvidenceKind.METRIC, result.reply.evidence.single().kind)
        assertEquals("Data is stale", result.reply.notices.single().text)
    }
}
