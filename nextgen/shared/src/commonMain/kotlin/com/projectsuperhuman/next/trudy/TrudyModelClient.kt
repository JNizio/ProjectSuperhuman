package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/**
 * Provider-neutral model boundary. Implementations may target local or hosted models,
 * but this shared contract contains no networking, credentials, or provider-specific types.
 */
interface TrudyModelClient {
    suspend fun complete(request: TrudyModelRequest): TrudyModelResult
}

enum class TrudyConversationRole { USER, ASSISTANT }

data class TrudyConversationTurn(
    val role: TrudyConversationRole,
    val text: String,
    /** Opaque structured keys from the assistant turn, retained for evidence follow-ups. */
    val evidenceKeys: List<String> = emptyList()
)

data class TrudyModelRequest(
    val userRequest: String,
    val systemInstruction: String = TrudyModelPolicy.SYSTEM_INSTRUCTION,
    val conversationContext: List<TrudyConversationTurn> = emptyList(),
    val context: TrudyHealthContext? = null,
    val toolDefinitions: List<TrudyToolDefinition> = emptyList(),
    val toolResults: List<TrudyToolResult> = emptyList(),
    val iteration: Int = 0,
    val knowledgeContext: List<TrudyKnowledgeItem> = emptyList()
)

data class TrudyEvidenceReference(
    val domain: HealthDomain,
    val metricId: String? = null,
    val insightId: String? = null,
    val evidenceKind: TrudyEvidenceKind,
    val timestampEpochMs: Long? = null,
    val range: TrudyTimeRange? = null
)

data class TrudyModelMetadata(
    val provider: String? = null,
    val model: String? = null,
    val requestId: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

data class TrudyModelResult(
    val responseText: String? = null,
    val requestedTools: List<TrudyToolOperation> = emptyList(),
    val evidenceReferences: List<TrudyEvidenceReference> = emptyList(),
    val metadata: TrudyModelMetadata? = null
)

/** Small deterministic adapter useful for tests, previews and offline wiring. */
class DeterministicTrudyModelClient(
    private val responder: suspend (TrudyModelRequest) -> TrudyModelResult
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult = responder(request)
}
