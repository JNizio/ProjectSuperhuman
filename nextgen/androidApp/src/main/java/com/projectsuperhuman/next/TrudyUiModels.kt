package com.projectsuperhuman.next

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TrudyMessageRole { USER, TRUDY }

enum class TrudyMessageStatus { COMPLETE, SENDING, ERROR }

enum class TrudyEvidenceKind { METRIC, DERIVED, DATA_QUALITY, GENERAL }

enum class TrudyNoticeLevel { INFO, CAUTION }

data class TrudyEvidenceItem(
    /** Exact structured identifier retained for future backend mapping. */
    val id: String,
    /** Human-readable UI label, e.g. "Sleep · sleep score" or "Derived trend". */
    val label: String,
    val detail: String? = null,
    val kind: TrudyEvidenceKind = TrudyEvidenceKind.GENERAL
)

data class TrudyNotice(
    val text: String,
    val level: TrudyNoticeLevel = TrudyNoticeLevel.INFO
)

data class TrudyActivityStatus(
    val label: String
)

data class TrudyMessage(
    val id: Long,
    val role: TrudyMessageRole,
    val text: String,
    val status: TrudyMessageStatus = TrudyMessageStatus.COMPLETE,
    val evidence: List<TrudyEvidenceItem> = emptyList(),
    val notices: List<TrudyNotice> = emptyList(),
    val activity: TrudyActivityStatus? = null,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    /** UI-internal retry payload; never rendered as evidence. */
    val requestText: String? = null,
    val retryable: Boolean = false
)

data class TrudyReply(
    val text: String,
    val evidence: List<TrudyEvidenceItem> = emptyList(),
    val notices: List<TrudyNotice> = emptyList(),
    val activity: TrudyActivityStatus? = null
)

data class TrudyUiState(
    val messages: List<TrudyMessage> = emptyList(),
    val activeRequestId: Long? = null,
    val errorMessage: String? = null,
    val expandedEvidenceMessageIds: Set<Long> = emptySet()
) {
    val isThinking: Boolean get() = activeRequestId != null
    val showWelcome: Boolean get() = messages.isEmpty() && !isThinking
}

data class TrudySendRequest(
    val requestId: Long,
    val assistantMessageId: Long,
    val conversationRequest: TrudyConversationRequest
)

data class TrudyConversationSnapshot(
    val messages: List<TrudyMessage>,
    val draftInput: String,
    val expandedEvidenceMessageIds: Set<Long>
)

/**
 * UI-only state machine. No health repositories, query services or model logic belong here.
 *
 * Lifecycle: idle -> sending placeholder -> success/error -> idle. Request tokens guarantee that
 * stale coroutine completions cannot overwrite a newer or retried request.
 */
class TrudyConversationState(
    snapshot: TrudyConversationSnapshot? = null
) {
    var uiState by mutableStateOf(restoreUiState(snapshot))
        private set

    var inputText by mutableStateOf(snapshot?.draftInput.orEmpty())
        private set

    private var nextMessageId = ((uiState.messages.maxOfOrNull { it.id } ?: 0L) + 1L).coerceAtLeast(1L)
    private var nextRequestId = 1L

    fun updateInput(text: String) {
        inputText = text
    }

    fun beginSend(textOverride: String? = null): TrudySendRequest? {
        if (uiState.activeRequestId != null) return null
        val text = (textOverride ?: inputText).trim()
        if (text.isBlank()) return null

        val history = uiState.messages
            .filter { it.status == TrudyMessageStatus.COMPLETE && it.text.isNotBlank() }
            .map { TrudyConversationTurn(it.role, it.text) }

        val userMessage = TrudyMessage(
            id = nextMessageId++,
            role = TrudyMessageRole.USER,
            text = text
        )
        val assistantMessage = TrudyMessage(
            id = nextMessageId++,
            role = TrudyMessageRole.TRUDY,
            text = "",
            status = TrudyMessageStatus.SENDING,
            requestText = text
        )
        val requestId = nextRequestId++

        uiState = uiState.copy(
            messages = uiState.messages + userMessage + assistantMessage,
            activeRequestId = requestId,
            errorMessage = null
        )
        inputText = ""

        return TrudySendRequest(
            requestId = requestId,
            assistantMessageId = assistantMessage.id,
            conversationRequest = TrudyConversationRequest(text = text, history = history)
        )
    }

    fun complete(request: TrudySendRequest, result: TrudyControllerResult): Boolean {
        if (uiState.activeRequestId != request.requestId) return false
        val index = uiState.messages.indexOfFirst { it.id == request.assistantMessageId }
        if (index < 0) return false

        val existing = uiState.messages[index]
        val replacement = when (result) {
            is TrudyControllerResult.Success -> existing.copy(
                text = result.reply.text,
                status = TrudyMessageStatus.COMPLETE,
                evidence = result.reply.evidence,
                notices = result.reply.notices,
                activity = result.reply.activity,
                retryable = false
            )
            is TrudyControllerResult.Failure -> existing.copy(
                text = result.message.ifBlank { "Trudy couldn't complete that response." },
                status = TrudyMessageStatus.ERROR,
                evidence = emptyList(),
                notices = emptyList(),
                activity = null,
                retryable = result.retryable
            )
        }

        val updated = uiState.messages.toMutableList().also { it[index] = replacement }
        uiState = uiState.copy(
            messages = updated,
            activeRequestId = null,
            errorMessage = (result as? TrudyControllerResult.Failure)?.message
        )
        return true
    }

    fun fail(request: TrudySendRequest, message: String, retryable: Boolean = true): Boolean =
        complete(request, TrudyControllerResult.Failure(message, retryable))

    fun retryFailed(messageId: Long? = null): TrudySendRequest? {
        if (uiState.activeRequestId != null) return null
        val index = if (messageId != null) {
            uiState.messages.indexOfFirst { it.id == messageId }
        } else {
            uiState.messages.indexOfLast { it.role == TrudyMessageRole.TRUDY && it.status == TrudyMessageStatus.ERROR }
        }
        if (index < 0) return null

        val failed = uiState.messages[index]
        val text = failed.requestText?.trim().orEmpty()
        if (!failed.retryable || text.isBlank()) return null

        val requestId = nextRequestId++
        val history = uiState.messages.take(index)
            .filter { it.status == TrudyMessageStatus.COMPLETE && it.text.isNotBlank() }
            .map { TrudyConversationTurn(it.role, it.text) }
        val replacement = failed.copy(
            text = "",
            status = TrudyMessageStatus.SENDING,
            evidence = emptyList(),
            notices = emptyList(),
            activity = null,
            retryable = false
        )
        val updated = uiState.messages.toMutableList().also { it[index] = replacement }
        uiState = uiState.copy(messages = updated, activeRequestId = requestId, errorMessage = null)

        return TrudySendRequest(
            requestId = requestId,
            assistantMessageId = failed.id,
            conversationRequest = TrudyConversationRequest(text = text, history = history)
        )
    }

    fun toggleEvidence(messageId: Long) {
        val current = uiState.expandedEvidenceMessageIds
        uiState = uiState.copy(
            expandedEvidenceMessageIds = if (messageId in current) current - messageId else current + messageId
        )
    }

    fun snapshot(): TrudyConversationSnapshot = TrudyConversationSnapshot(
        messages = uiState.messages,
        draftInput = inputText,
        expandedEvidenceMessageIds = uiState.expandedEvidenceMessageIds
    )

    companion object {
        private fun restoreUiState(snapshot: TrudyConversationSnapshot?): TrudyUiState {
            if (snapshot == null) return TrudyUiState()
            val restored = snapshot.messages.map { message ->
                if (message.status == TrudyMessageStatus.SENDING) {
                    message.copy(
                        text = "Response interrupted. Retry to continue.",
                        status = TrudyMessageStatus.ERROR,
                        retryable = true
                    )
                } else message
            }
            return TrudyUiState(
                messages = restored,
                activeRequestId = null,
                errorMessage = null,
                expandedEvidenceMessageIds = snapshot.expandedEvidenceMessageIds
            )
        }
    }
}
