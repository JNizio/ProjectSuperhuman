package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyConversationRole as SharedConversationRole
import com.projectsuperhuman.next.trudy.TrudyConversationService as SharedConversationService
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind as SharedEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyEvidenceReference as SharedEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyWarningKind as SharedWarningKind
import com.projectsuperhuman.next.trudy.TrudyConversationTurn as SharedConversationTurn

/** Non-sensitive runtime metadata exposed only as diagnostic UI activity text. */
data class TrudyBackendRuntimeInfo(
    val mode: String,
    val providerId: String,
    val modelId: String,
    val startupFallbackUsed: Boolean = false
)

/**
 * Narrow Android adapter for the shared Trudy orchestration service.
 *
 * Compose and Android conversation state depend only on [TrudyConversationBackend]. Structured
 * health evidence keeps its domain qualification here and no SQL/provider types enter the UI.
 */
class SharedTrudyBackendAdapter(
    private val service: SharedConversationService,
    private val runtimeInfo: TrudyBackendRuntimeInfo? = null
) : TrudyConversationBackend {

    override suspend fun send(request: TrudyConversationRequest): TrudyBackendResult {
        val history = request.history.asSequence()
            .filter { it.text.isNotBlank() }
            .map { turn ->
                SharedConversationTurn(
                    role = when (turn.role) {
                        TrudyMessageRole.USER -> SharedConversationRole.USER
                        TrudyMessageRole.TRUDY -> SharedConversationRole.ASSISTANT
                    },
                    text = turn.text.trim()
                )
            }
            .toList()

        val started = System.currentTimeMillis()
        val result = service.respondTo(
            userText = request.text,
            conversationContext = history
        )
        val durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L)

        return TrudyBackendResult(
            text = result.answerText,
            evidence = result.evidenceReferences.map { it.toBackendEvidence() },
            notices = buildList {
                result.warnings.forEach { warning ->
                    add(
                        TrudyBackendNotice(
                            text = warning.message,
                            caution = warning.kind.isCaution()
                        )
                    )
                }
                if (result.isFallback && result.warnings.isEmpty()) {
                    add(TrudyBackendNotice("Trudy returned a conservative fallback response.", caution = true))
                }
                if (runtimeInfo?.startupFallbackUsed == true) {
                    add(
                        TrudyBackendNotice(
                            "Configured model runtime was unavailable, so Trudy is using deterministic offline mode.",
                            caution = false
                        )
                    )
                }
            }.distinctBy { it.text },
            activity = diagnosticActivity(result.toolCallsMade.size, result.toolCallsMade.count { it.succeeded }, durationMs, result.modelMetadata?.provider, result.modelMetadata?.model),
            retryable = result.isFallback
        )
    }

    private fun diagnosticActivity(
        totalCalls: Int,
        succeededCalls: Int,
        durationMs: Long,
        modelProvider: String?,
        modelId: String?
    ): TrudyBackendActivity? {
        val info = runtimeInfo
        if (totalCalls == 0 && info == null && modelProvider == null && modelId == null) return null
        val mode = info?.mode?.lowercase() ?: "runtime"
        val provider = modelProvider ?: info?.providerId
        val model = modelId ?: info?.modelId
        return TrudyBackendActivity(
            buildString {
                append(mode)
                provider?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                model?.takeIf { it.isNotBlank() }?.let { append("/").append(it) }
                append(" · tools ").append(succeededCalls).append('/').append(totalCalls)
                append(" · ").append(durationMs).append("ms")
            }
        )
    }

    private fun SharedEvidenceReference.toBackendEvidence(): TrudyBackendEvidence {
        val domainLabel = domain.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        val subject = metricId ?: insightId ?: evidenceKind.name.lowercase().replace('_', ' ')
        return TrudyBackendEvidence(
            id = buildString {
                append(domain.name)
                append(':')
                append(metricId ?: insightId ?: evidenceKind.name)
                timestampEpochMs?.let { append(":").append(it) }
                range?.let { append(":").append(it.fromEpochMs).append('-').append(it.toEpochMs) }
            },
            label = "$domainLabel · $subject",
            detail = when {
                timestampEpochMs != null -> "Observed at $timestampEpochMs"
                range != null -> "Range ${range!!.fromEpochMs}–${range!!.toEpochMs}"
                else -> null
            },
            kind = when (evidenceKind) {
                SharedEvidenceKind.DIRECT_PERSONAL_OBSERVATION -> TrudyBackendEvidenceKind.METRIC
                SharedEvidenceKind.DERIVED_PERSONAL_TREND -> TrudyBackendEvidenceKind.DERIVED
                SharedEvidenceKind.UNCERTAINTY_OR_DATA_GAP -> TrudyBackendEvidenceKind.DATA_QUALITY
                SharedEvidenceKind.INTERPRETATION,
                SharedEvidenceKind.EXTERNAL_SCIENTIFIC_EVIDENCE -> TrudyBackendEvidenceKind.GENERAL
            }
        )
    }

    private fun SharedWarningKind.isCaution(): Boolean = when (this) {
        SharedWarningKind.EMPTY_DATA -> false
        SharedWarningKind.STALE_DATA,
        SharedWarningKind.LOW_DATA_QUALITY,
        SharedWarningKind.TOOL_FAILURE,
        SharedWarningKind.UNSUPPORTED_TOOL,
        SharedWarningKind.MALFORMED_TOOL_REQUEST,
        SharedWarningKind.MODEL_FAILURE,
        SharedWarningKind.ITERATION_LIMIT,
        SharedWarningKind.UNBOUND_EVIDENCE_REFERENCE -> true
    }
}
