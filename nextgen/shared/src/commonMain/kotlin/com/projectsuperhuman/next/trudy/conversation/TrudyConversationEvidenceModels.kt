package com.projectsuperhuman.next.trudy.conversation

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyTimeRange

/** Input channel is diagnostic context only; both channels intentionally share one session. */
enum class TrudyConversationInputMode { TEXT, VOICE }

enum class TrudyFollowUpKind {
    NEW_QUESTION,
    TIMEFRAME_SHIFT,
    CROSS_DOMAIN_FOLLOW_UP,
    EVIDENCE_EXPLANATION,
    SAME_SCOPE,
    MISSING_DATA_FOLLOW_UP,
    AMBIGUOUS_FOLLOW_UP
}

enum class TrudyReferentKind {
    NONE,
    ACTIVE_TOPIC,
    LATEST_RESULT,
    LATEST_TREND,
    LATEST_READINGS,
    ACTIVE_TIMEFRAME,
    SAME_SCOPE,
    ACTIVE_EXPERIMENT
}

enum class TrudyEvidenceRole { SUPPORTING, CONTEXT, DATA_GAP }

enum class TrudyEvidenceReuseAction { FETCH, REFRESH, REUSE, REUSE_MISSING_DATA }

enum class TrudyEvidenceReuseReason {
    NO_CACHED_EVIDENCE,
    VALID_MATCHING_EVIDENCE,
    VALID_USED_EVIDENCE,
    KNOWN_MISSING_DATA,
    TIMEFRAME_CHANGED,
    METRIC_CHANGED,
    TOPIC_CHANGED,
    CURRENT_DATA_REQUESTED,
    EVIDENCE_STALE
}

data class TrudyConversationMetric(
    val domain: HealthDomain,
    val metricId: String
) {
    init { require(metricId.isNotBlank()) }
}

data class TrudyConversationTimeframe(
    val range: TrudyTimeRange,
    val label: String,
    val explicit: Boolean,
    val requiresCurrentData: Boolean = false
) {
    init { require(label.isNotBlank()) }
}

/**
 * Small structured result pointer. It deliberately stores no generated answer or transcript.
 * Agent 2 owns the actual investigation result and may use [resultId] to locate it for one session.
 */
data class TrudyConversationInvestigationResult(
    val resultId: String,
    val topic: String,
    val domains: List<HealthDomain>,
    val metrics: List<TrudyConversationMetric>,
    val timeframe: TrudyConversationTimeframe,
    val findingCode: String,
    val createdAtEpochMs: Long
) {
    init {
        require(resultId.isNotBlank())
        require(topic.isNotBlank())
        require(findingCode.isNotBlank())
        require(createdAtEpochMs >= 0L)
    }
}

data class TrudyMissingDataFinding(
    val domain: HealthDomain,
    val metricIds: List<String>,
    val timeframe: TrudyConversationTimeframe,
    val checkedAtEpochMs: Long
) {
    init {
        require(metricIds.isNotEmpty())
        require(metricIds.none(String::isBlank))
        require(checkedAtEpochMs >= 0L)
    }
}

/** Stable identity used for exact de-duplication and model-to-UI evidence binding. */
data class TrudyConversationEvidenceIdentity(
    val domain: HealthDomain,
    val metricId: String? = null,
    val insightId: String? = null,
    val evidenceKind: TrudyEvidenceKind,
    val timestampEpochMs: Long? = null,
    val range: TrudyTimeRange? = null
) {
    init { require(metricId != null || insightId != null) }

    fun stableKey(): String = buildString {
        append(domain.name).append(':')
        append(metricId ?: insightId).append(':')
        append(evidenceKind.name).append(':')
        append(timestampEpochMs ?: "-").append(':')
        append(range?.fromEpochMs ?: "-").append(':')
        append(range?.toEpochMs ?: "-")
    }
}

/**
 * Bounded evidence value retained only for the current conversation. [displayValue] must already
 * be safe for model/UI use; raw provider payloads and arbitrary metadata are intentionally absent.
 */
data class TrudyConversationEvidenceRecord(
    val id: String,
    val identity: TrudyConversationEvidenceIdentity,
    val role: TrudyEvidenceRole,
    val displayValue: String? = null,
    val source: String? = null
) {
    init { require(id.isNotBlank()) }
}

data class TrudyConversationEvidenceBatch(
    val queryKey: String,
    val investigationId: String,
    val topics: List<String>,
    val domains: List<HealthDomain>,
    val metrics: List<TrudyConversationMetric>,
    val timeframe: TrudyConversationTimeframe,
    val retrievedAtEpochMs: Long,
    val staleAtEpochMs: Long,
    /** Exact retrieval count, which can be larger than the bounded retained record list. */
    val retrievedRecordCount: Int,
    val records: List<TrudyConversationEvidenceRecord>
) {
    init {
        require(queryKey.isNotBlank())
        require(investigationId.isNotBlank())
        require(retrievedAtEpochMs >= 0L)
        require(staleAtEpochMs >= retrievedAtEpochMs)
        require(retrievedRecordCount >= records.size)
    }
}

/** Compact, transcript-free state supplied to Agent 2 before every turn. */
data class TrudyConversationEvidenceState(
    val activeTopic: String? = null,
    val activeDomains: List<HealthDomain> = emptyList(),
    val activeMetrics: List<TrudyConversationMetric> = emptyList(),
    val activeTimeframe: TrudyConversationTimeframe? = null,
    val priorComparisonTimeframe: TrudyConversationTimeframe? = null,
    val latestInvestigationResult: TrudyConversationInvestigationResult? = null,
    val latestEvidenceIds: List<String> = emptyList(),
    val activeExperimentId: String? = null,
    val unresolvedQuestionCode: String? = null,
    val missingDataFindings: List<TrudyMissingDataFinding> = emptyList(),
    val updatedAtEpochMs: Long = 0L
)

data class TrudyResolvedReferent(
    val kind: TrudyReferentKind,
    val topic: String? = null,
    val resultId: String? = null,
    val evidenceIds: List<String> = emptyList()
)

/** Fully resolved request; the answer engine does not need to re-parse the transcript. */
data class TrudyResolvedConversationRequest(
    val rawUserText: String,
    val inputMode: TrudyConversationInputMode,
    val followUpKind: TrudyFollowUpKind,
    val topic: String?,
    val domains: List<HealthDomain>,
    val metrics: List<TrudyConversationMetric>,
    val timeframe: TrudyConversationTimeframe,
    val priorTimeframe: TrudyConversationTimeframe?,
    val referent: TrudyResolvedReferent,
    val evidenceIdsRequested: List<String>,
    val topicChanged: Boolean,
    val metricChanged: Boolean,
    val timeframeChanged: Boolean
)

data class TrudyEvidenceReuseDecision(
    val action: TrudyEvidenceReuseAction,
    val reason: TrudyEvidenceReuseReason,
    val queryKey: String,
    val reusableEvidence: List<TrudyConversationEvidenceRecord> = emptyList(),
    val missingData: List<TrudyMissingDataFinding> = emptyList()
)

/** Contract consumed by Agent 2. It contains context and evidence policy, never final prose. */
data class TrudyAnswerEngineConversationContext(
    val turnId: Long,
    val conversationState: TrudyConversationEvidenceState,
    val resolvedRequest: TrudyResolvedConversationRequest,
    val retrieval: TrudyEvidenceReuseDecision
)

data class TrudyAnswerTurnOutcome(
    val investigationResult: TrudyConversationInvestigationResult? = null,
    val retrievedEvidence: TrudyConversationEvidenceBatch? = null,
    /** IDs genuinely cited or otherwise used to support the answer. */
    val usedEvidenceIds: List<String> = emptyList(),
    val missingDataFindings: List<TrudyMissingDataFinding> = emptyList(),
    val activeExperimentId: String? = null,
    val unresolvedQuestionCode: String? = null
)

data class TrudyEvidenceGroup(
    val label: String,
    val records: List<TrudyConversationEvidenceRecord>
)

/** UI semantics are based on used evidence, while retrieval diagnostics remain separate. */
data class TrudyUsedEvidencePresentation(
    val retrievedRecordCount: Int,
    val usedSupportingRecordCount: Int,
    val usedContextRecordCount: Int,
    val dataGapCount: Int,
    val groups: List<TrudyEvidenceGroup>,
    val excludedRetrievedRecordCount: Int
) {
    val userFacingLabel: String
        get() = when (val count = usedSupportingRecordCount + usedContextRecordCount) {
            0 -> if (dataGapCount > 0) "Data availability" else "No evidence used"
            1 -> "1 record used"
            else -> "$count records used"
        }
}

internal object TrudyConversationBounds {
    const val MAX_DOMAINS = 8
    const val MAX_METRICS = 16
    const val MAX_LATEST_EVIDENCE_IDS = 48
    const val MAX_MISSING_FINDINGS = 12
    const val MAX_CACHE_BATCHES = 6
    const val MAX_RECORDS_PER_BATCH = 96
    const val MAX_REUSABLE_RECORDS = 96
}
