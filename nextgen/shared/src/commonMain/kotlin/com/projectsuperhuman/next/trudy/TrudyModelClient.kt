package com.projectsuperhuman.next.trudy

/**
 * Provider-neutral model boundary. Implementations may later target local or hosted models,
 * but this shared contract contains no networking, credentials, or provider-specific types.
 */
interface TrudyModelClient {
    suspend fun complete(request: TrudyModelRequest): TrudyModelResult
}

data class TrudyModelRequest(
    val userRequest: String,
    val context: TrudyHealthContext? = null,
    val toolDefinitions: List<TrudyToolDefinition> = emptyList(),
    val toolResults: List<TrudyToolResult> = emptyList()
)

data class TrudyEvidenceReference(
    val domain: com.projectsuperhuman.next.core.HealthDomain,
    val metricId: String? = null,
    val insightId: String? = null,
    val evidenceKind: TrudyEvidenceKind
)

data class TrudyModelResult(
    val responseText: String? = null,
    val requestedTools: List<TrudyToolOperation> = emptyList(),
    val evidenceReferences: List<TrudyEvidenceReference> = emptyList()
)
