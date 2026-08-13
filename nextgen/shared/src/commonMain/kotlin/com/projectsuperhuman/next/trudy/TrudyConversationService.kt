package com.projectsuperhuman.next.trudy

data class TrudyConversationResult(
    val answerText: String,
    val evidenceReferences: List<TrudyEvidenceReference>,
    val toolCallsMade: List<TrudyToolCallRecord>,
    val warnings: List<TrudyWarning>,
    val modelMetadata: TrudyModelMetadata?,
    val isFallback: Boolean
)

/**
 * Narrow boundary intended for Android presentation/controller code.
 * Conversation state remains owned by the caller; this service performs one response turn.
 */
class TrudyConversationService(
    private val orchestrator: TrudyOrchestrator
) {
    suspend fun respondTo(
        userText: String,
        conversationContext: List<TrudyConversationTurn> = emptyList(),
        preselectedContext: TrudyContextRequest? = null
    ): TrudyConversationResult {
        val result = orchestrator.ask(
            TrudyAskRequest(
                userMessage = userText,
                conversationContext = conversationContext,
                preselectedContext = preselectedContext
            )
        )
        return TrudyConversationResult(
            answerText = result.answerText,
            evidenceReferences = result.evidenceReferences,
            toolCallsMade = result.toolCallsMade,
            warnings = result.warnings,
            modelMetadata = result.modelMetadata,
            isFallback = result.status == TrudyOrchestrationStatus.FALLBACK
        )
    }
}
