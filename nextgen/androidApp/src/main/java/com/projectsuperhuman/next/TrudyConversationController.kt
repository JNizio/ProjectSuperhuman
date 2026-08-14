package com.projectsuperhuman.next

import kotlinx.coroutines.delay

/**
 * Narrow Android-facing conversation boundary. Compose depends on this contract only.
 * Implementations may delegate to shared orchestration without exposing health/data classes to UI.
 */
interface TrudyConversationController {
    suspend fun respondTo(request: TrudyConversationRequest): TrudyControllerResult
}

data class TrudyConversationRequest(
    val text: String,
    val history: List<TrudyConversationTurn> = emptyList()
)

data class TrudyConversationTurn(
    val role: TrudyMessageRole,
    val text: String,
    val evidenceKeys: List<String> = emptyList()
)

sealed interface TrudyControllerResult {
    data class Success(val reply: TrudyReply) : TrudyControllerResult
    data class Failure(
        val message: String,
        val retryable: Boolean = true
    ) : TrudyControllerResult
}

/**
 * Presentation-neutral backend contract for Instance A's future orchestration service.
 * It intentionally contains no Compose, repository, Health Connect, or shared health-domain types.
 */
interface TrudyConversationBackend {
    suspend fun send(request: TrudyConversationRequest): TrudyBackendResult
}

data class TrudyBackendEvidence(
    val id: String,
    val label: String,
    val detail: String? = null,
    val kind: TrudyBackendEvidenceKind = TrudyBackendEvidenceKind.GENERAL
)

enum class TrudyBackendEvidenceKind { METRIC, DERIVED, DATA_QUALITY, GENERAL }

data class TrudyBackendNotice(
    val text: String,
    val caution: Boolean = false
)

data class TrudyBackendActivity(
    val label: String
)

data class TrudyBackendResult(
    val text: String? = null,
    val evidence: List<TrudyBackendEvidence> = emptyList(),
    val notices: List<TrudyBackendNotice> = emptyList(),
    val activity: TrudyBackendActivity? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = true
)

/**
 * Drop-in mapper/controller for the future shared orchestration implementation.
 * Only this adapter needs wiring when the concrete backend exists; NativeTrudy remains unchanged.
 */
class SharedTrudyConversationController(
    private val backend: TrudyConversationBackend
) : TrudyConversationController {
    override suspend fun respondTo(request: TrudyConversationRequest): TrudyControllerResult {
        val result = backend.send(request)
        val error = result.errorMessage?.takeIf { it.isNotBlank() }
        if (error != null) return TrudyControllerResult.Failure(error, result.retryable)

        val text = result.text?.trim().orEmpty()
        if (text.isBlank()) {
            return TrudyControllerResult.Failure(
                message = "Trudy returned no response.",
                retryable = true
            )
        }

        return TrudyControllerResult.Success(
            TrudyReply(
                text = text,
                evidence = result.evidence.map { it.toUiEvidence() },
                notices = result.notices.map {
                    TrudyNotice(
                        text = it.text,
                        level = if (it.caution) TrudyNoticeLevel.CAUTION else TrudyNoticeLevel.INFO
                    )
                },
                activity = result.activity?.let { TrudyActivityStatus(it.label) }
            )
        )
    }

    private fun TrudyBackendEvidence.toUiEvidence() = TrudyEvidenceItem(
        id = id,
        label = label,
        detail = detail,
        kind = when (kind) {
            TrudyBackendEvidenceKind.METRIC -> TrudyEvidenceKind.METRIC
            TrudyBackendEvidenceKind.DERIVED -> TrudyEvidenceKind.DERIVED
            TrudyBackendEvidenceKind.DATA_QUALITY -> TrudyEvidenceKind.DATA_QUALITY
            TrudyBackendEvidenceKind.GENERAL -> TrudyEvidenceKind.GENERAL
        }
    )
}

/** Deterministic local fallback. It never reads personal or health data. */
class LocalTrudyConversationController : TrudyConversationController {
    override suspend fun respondTo(request: TrudyConversationRequest): TrudyControllerResult {
        delay(250)
        val normalized = request.text.trim().lowercase()
        val noDataNotice = TrudyNotice(
            text = "No personal data is available in local demo mode.",
            level = TrudyNoticeLevel.INFO
        )
        val localEvidence = listOf(
            TrudyEvidenceItem(
                id = "local_demo",
                label = "Local demo",
                detail = "No health data accessed",
                kind = TrudyEvidenceKind.GENERAL
            )
        )
        val reply = when {
            "sleep" in normalized -> TrudyReply(
                text = "Demo mode: I’m not connected to your health data yet. A real backend can replace this response through the conversation controller without changing the screen.",
                evidence = localEvidence,
                notices = listOf(noDataNotice)
            )
            "changed" in normalized || "today" in normalized -> TrudyReply(
                text = "Demo mode: the conversation layer is ready to display a structured change summary, but this fallback deliberately does not infer personal changes.",
                evidence = localEvidence,
                notices = listOf(noDataNotice)
            )
            "pay attention" in normalized || "attention" in normalized -> TrudyReply(
                text = "Demo mode: a connected backend can return priorities, evidence and uncertainty here. This local fallback has no access to your measurements.",
                evidence = localEvidence,
                notices = listOf(noDataNotice)
            )
            else -> TrudyReply(
                text = "Demo mode: the production-shaped conversation layer is working. This response is local and uses no personal measurements, Data Vault, Health Connect or model provider.",
                evidence = localEvidence,
                notices = listOf(noDataNotice)
            )
        }
        return TrudyControllerResult.Success(reply)
    }
}
