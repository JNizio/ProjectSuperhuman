package com.projectsuperhuman.next

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TrudyMessageRole {
    USER,
    TRUDY
}

data class TrudyMessage(
    val id: Long,
    val role: TrudyMessageRole,
    val text: String,
    val evidence: List<String> = emptyList()
)

data class TrudyUiState(
    val messages: List<TrudyMessage> = emptyList(),
    val isThinking: Boolean = false,
    val errorMessage: String? = null
) {
    val showWelcome: Boolean get() = messages.isEmpty() && !isThinking
}

data class TrudyReply(
    val text: String,
    val evidence: List<String> = emptyList()
)

/**
 * UI-only conversation state. It intentionally owns no health models or data dependencies.
 */
class TrudyConversationState {
    var uiState by mutableStateOf(TrudyUiState())
        private set

    var inputText by mutableStateOf("")
        private set

    private var nextMessageId = 1L

    fun updateInput(text: String) {
        inputText = text
    }

    fun beginSend(textOverride: String? = null): String? {
        if (uiState.isThinking) return null
        val text = (textOverride ?: inputText).trim()
        if (text.isBlank()) return null

        uiState = uiState.copy(
            messages = uiState.messages + TrudyMessage(
                id = nextMessageId++,
                role = TrudyMessageRole.USER,
                text = text
            ),
            isThinking = true,
            errorMessage = null
        )
        inputText = ""
        return text
    }

    fun completeReply(reply: TrudyReply) {
        uiState = uiState.copy(
            messages = uiState.messages + TrudyMessage(
                id = nextMessageId++,
                role = TrudyMessageRole.TRUDY,
                text = reply.text,
                evidence = reply.evidence
            ),
            isThinking = false,
            errorMessage = null
        )
    }

    fun fail(message: String) {
        uiState = uiState.copy(
            isThinking = false,
            errorMessage = message.ifBlank { "Trudy couldn't complete that demo response." }
        )
    }
}
