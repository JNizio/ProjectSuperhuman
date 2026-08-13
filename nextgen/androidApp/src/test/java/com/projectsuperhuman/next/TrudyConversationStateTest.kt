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
    fun blankSendIsIgnored() {
        val state = TrudyConversationState()
        state.updateInput("   ")

        assertNull(state.beginSend())
        assertTrue(state.uiState.messages.isEmpty())
        assertFalse(state.uiState.isThinking)
    }

    @Test
    fun userMessageAppendsAndInputClears() {
        val state = TrudyConversationState()
        state.updateInput("What changed today?")

        val sent = state.beginSend()

        assertEquals("What changed today?", sent)
        assertEquals("", state.inputText)
        assertEquals(1, state.uiState.messages.size)
        assertEquals(TrudyMessageRole.USER, state.uiState.messages.single().role)
        assertTrue(state.uiState.isThinking)
    }

    @Test
    fun mockResponseAppears() = runBlocking {
        val state = TrudyConversationState()
        val controller = LocalTrudyConversationController()
        val sent = assertNotNull(state.beginSend("How was my sleep?"))

        state.completeReply(controller.respondTo(sent))

        assertEquals(2, state.uiState.messages.size)
        assertEquals(TrudyMessageRole.TRUDY, state.uiState.messages.last().role)
        assertTrue(state.uiState.messages.last().text.startsWith("Demo mode:"))
        assertFalse(state.uiState.isThinking)
    }

    @Test
    fun freshStateShowsWelcome() {
        val state = TrudyConversationState()

        assertTrue(state.uiState.showWelcome)
    }
}
