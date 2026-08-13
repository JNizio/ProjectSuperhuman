package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyConversationRole as SharedConversationRole
import com.projectsuperhuman.next.trudy.TrudyConversationService as SharedConversationService
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind as SharedEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyEvidenceReference as SharedEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyWarningKind as SharedWarningKind
import com.projectsuperhuman.next.trudy.TrudyConversationTurn as SharedConversationTurn

/**
 * Narrow Android adapter for the shared Trudy orchestration service.
 *
 * Compose and the Android conversation state continue to depend only on
 * [TrudyConversationBackend]. Shared health/model types are translated here and never leak into
 * the UI contract. Construct this only when a real shared [SharedConversationService] is available;
 * the shell may keep using [LocalTrudyConversationController] until model/provider wiring exists.
 */
class SharedTrudyBackendAdapter(
    private val service: SharedConversationService
) : TrudyConversationBackend {

    override suspend fun send(request: TrudyConversationRequest): TrudyBackendResult {
        val result = service.respondTo(
            userText = request.text,
            conversationContext = request.history.map { turn ->
                SharedConversationTurn(
                    role = when (turn.role) {
                        TrudyMessageRole.USER -> SharedConversationRole.USER
                        TrudyMessageRole.TRUDY -> SharedConversationRole.ASSISTANT
                    },
                    text = turn.text
                )
            }
        )

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
            },
            activity = result.toolCallsMade.takeIf { it.isNotEmpty() }?.let { calls ->
                val succeeded = calls.count { it.succeeded }
                TrudyBackendActivity("Used $succeeded/${calls.size} health data tool calls")
            },
            retryable = result.isFallback
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
