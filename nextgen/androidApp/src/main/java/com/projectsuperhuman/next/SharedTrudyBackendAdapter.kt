package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyConversationRole as SharedConversationRole
import com.projectsuperhuman.next.trudy.TrudyConversationService as SharedConversationService
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind as SharedEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyEvidenceReference as SharedEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyWarningKind as SharedWarningKind
import com.projectsuperhuman.next.trudy.TrudyConversationTurn as SharedConversationTurn

/** Runtime metadata is retained for diagnostics but is never rendered into normal conversation UI. */
data class TrudyBackendRuntimeInfo(
    val mode: String,
    val providerId: String,
    val modelId: String,
    val startupFallbackUsed: Boolean = false
)

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

        val result = service.respondTo(
            userText = request.text,
            conversationContext = history
        )

        return TrudyBackendResult(
            text = result.answerText,
            evidence = result.evidenceReferences.map { it.toBackendEvidence() },
            notices = buildList {
                // Keep only actionable runtime failures in the normal chat surface.
                // Staleness/data-quality details remain represented in evidence and should not
                // read like internal backend diagnostics to the user.
                result.warnings.forEach { warning ->
                    if (warning.kind in setOf(
                            SharedWarningKind.TOOL_FAILURE,
                            SharedWarningKind.MODEL_FAILURE,
                            SharedWarningKind.ITERATION_LIMIT
                        )
                    ) {
                        add(
                            TrudyBackendNotice(
                                text = warning.message,
                                caution = true
                            )
                        )
                    }
                }
            }.distinctBy { it.text },
            activity = null,
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
