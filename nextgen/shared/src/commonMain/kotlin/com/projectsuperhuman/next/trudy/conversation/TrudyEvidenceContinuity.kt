package com.projectsuperhuman.next.trudy.conversation

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyTimeRange

/** In-memory, per-conversation evidence cache. There is no external logging or persistence. */
class BoundedTrudyConversationEvidenceCache {
    private val batches = mutableListOf<TrudyConversationEvidenceBatch>()

    fun put(batch: TrudyConversationEvidenceBatch) {
        val bounded = batch.copy(
            topics = batch.topics.filter(String::isNotBlank).distinct().take(4),
            domains = batch.domains.distinct().take(TrudyConversationBounds.MAX_DOMAINS),
            metrics = batch.metrics.distinct().take(TrudyConversationBounds.MAX_METRICS),
            records = batch.records
                .distinctBy { it.identity.stableKey() }
                .take(TrudyConversationBounds.MAX_RECORDS_PER_BATCH)
        )
        batches.removeAll { it.queryKey == bounded.queryKey }
        batches.add(0, bounded)
        while (batches.size > TrudyConversationBounds.MAX_CACHE_BATCHES) {
            batches.removeAt(batches.lastIndex)
        }
    }

    fun snapshot(): List<TrudyConversationEvidenceBatch> = batches.toList()

    fun recordsByIds(ids: Collection<String>): List<TrudyConversationEvidenceRecord> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toSet()
        return batches.asSequence()
            .flatMap { it.records.asSequence() }
            .filter { it.id in wanted }
            .distinctBy { it.identity.stableKey() }
            .take(TrudyConversationBounds.MAX_REUSABLE_RECORDS)
            .toList()
    }

    fun clear() = batches.clear()
}

class TrudyEvidenceReusePolicy {
    fun decide(
        request: TrudyResolvedConversationRequest,
        state: TrudyConversationEvidenceState,
        batches: List<TrudyConversationEvidenceBatch>,
        nowEpochMs: Long
    ): TrudyEvidenceReuseDecision {
        val queryKey = queryKey(request)
        if (request.timeframe.requiresCurrentData) {
            return decision(TrudyEvidenceReuseAction.REFRESH, TrudyEvidenceReuseReason.CURRENT_DATA_REQUESTED, queryKey)
        }

        val knownMissing = state.missingDataFindings.filter { finding ->
            finding.domain in request.domains &&
                finding.timeframe.range == request.timeframe.range &&
                finding.metricIds.any { metricId -> request.metrics.any { it.domain == finding.domain && it.metricId == metricId } }
        }
        if (knownMissing.isNotEmpty()) {
            return decision(
                TrudyEvidenceReuseAction.REUSE_MISSING_DATA,
                TrudyEvidenceReuseReason.KNOWN_MISSING_DATA,
                queryKey,
                missingData = knownMissing
            )
        }
        val priorMissingScope = state.missingDataFindings.any { finding ->
            finding.domain in request.domains &&
                finding.metricIds.any { metricId -> request.metrics.any { it.domain == finding.domain && it.metricId == metricId } }
        }
        if (priorMissingScope && request.timeframeChanged) {
            return decision(
                TrudyEvidenceReuseAction.REFRESH,
                TrudyEvidenceReuseReason.TIMEFRAME_CHANGED,
                queryKey
            )
        }

        if (request.followUpKind == TrudyFollowUpKind.EVIDENCE_EXPLANATION && request.evidenceIdsRequested.isNotEmpty()) {
            val records = batches.asSequence()
                .flatMap { it.records.asSequence() }
                .filter { it.id in request.evidenceIdsRequested }
                .distinctBy { it.identity.stableKey() }
                .take(TrudyConversationBounds.MAX_REUSABLE_RECORDS)
                .toList()
            val sourceBatches = batches.filter { batch -> batch.records.any { it.id in request.evidenceIdsRequested } }
            if (records.isNotEmpty() && sourceBatches.all { nowEpochMs < it.staleAtEpochMs }) {
                return decision(
                    TrudyEvidenceReuseAction.REUSE,
                    TrudyEvidenceReuseReason.VALID_USED_EVIDENCE,
                    queryKey,
                    records
                )
            }
        }

        val exact = batches.firstOrNull { batch -> scopesMatch(batch, request) }
        if (exact != null) {
            return if (nowEpochMs >= exact.staleAtEpochMs) {
                decision(TrudyEvidenceReuseAction.REFRESH, TrudyEvidenceReuseReason.EVIDENCE_STALE, queryKey)
            } else {
                decision(
                    TrudyEvidenceReuseAction.REUSE,
                    TrudyEvidenceReuseReason.VALID_MATCHING_EVIDENCE,
                    queryKey,
                    exact.records
                )
            }
        }

        val sameTopic = batches.firstOrNull { batch ->
            request.topic != null && request.topic in batch.topics
        }
        if (sameTopic != null) {
            val reason = when {
                sameTopic.timeframe.range != request.timeframe.range -> TrudyEvidenceReuseReason.TIMEFRAME_CHANGED
                sameTopic.metrics.toSet() != request.metrics.toSet() -> TrudyEvidenceReuseReason.METRIC_CHANGED
                else -> TrudyEvidenceReuseReason.TOPIC_CHANGED
            }
            return decision(TrudyEvidenceReuseAction.REFRESH, reason, queryKey)
        }
        if (request.topicChanged && batches.isNotEmpty()) {
            return decision(TrudyEvidenceReuseAction.REFRESH, TrudyEvidenceReuseReason.TOPIC_CHANGED, queryKey)
        }
        return decision(TrudyEvidenceReuseAction.FETCH, TrudyEvidenceReuseReason.NO_CACHED_EVIDENCE, queryKey)
    }

    fun queryKey(request: TrudyResolvedConversationRequest): String = buildString {
        append(request.topic ?: "unspecified").append('|')
        append(request.domains.map { it.name }.sorted().joinToString(",")).append('|')
        append(request.metrics.map { "${it.domain.name}:${it.metricId}" }.sorted().joinToString(",")).append('|')
        append(request.timeframe.range.fromEpochMs).append('-').append(request.timeframe.range.toEpochMs)
    }

    private fun scopesMatch(
        batch: TrudyConversationEvidenceBatch,
        request: TrudyResolvedConversationRequest
    ): Boolean {
        if (batch.timeframe.range != request.timeframe.range) return false
        if (request.topic != null && request.topic !in batch.topics) return false
        if (!batch.domains.containsAll(request.domains)) return false
        return request.metrics.isEmpty() || batch.metrics.containsAll(request.metrics)
    }

    private fun decision(
        action: TrudyEvidenceReuseAction,
        reason: TrudyEvidenceReuseReason,
        queryKey: String,
        records: List<TrudyConversationEvidenceRecord> = emptyList(),
        missingData: List<TrudyMissingDataFinding> = emptyList()
    ) = TrudyEvidenceReuseDecision(
        action = action,
        reason = reason,
        queryKey = queryKey,
        reusableEvidence = records.take(TrudyConversationBounds.MAX_REUSABLE_RECORDS),
        missingData = missingData.take(TrudyConversationBounds.MAX_MISSING_FINDINGS)
    )
}

/** Selects only model-declared used evidence, then removes duplicates and unrelated records. */
class TrudyUsedEvidenceSelector {
    fun select(
        retrievedRecordCount: Int,
        candidateRecords: List<TrudyConversationEvidenceRecord>,
        usedEvidenceIds: Collection<String>,
        request: TrudyResolvedConversationRequest
    ): TrudyUsedEvidencePresentation {
        val usedIds = usedEvidenceIds.toSet()
        val used = candidateRecords.asSequence()
            .filter { it.id in usedIds }
            .filter { it.isRelevantTo(request) }
            .distinctBy { it.identity.stableKey() }
            .take(TrudyConversationBounds.MAX_REUSABLE_RECORDS)
            .toList()
        val groups = used.groupBy { groupLabel(it.identity.domain) }
            .toList()
            .sortedBy { (label, _) -> GROUP_ORDER.indexOf(label).let { if (it < 0) Int.MAX_VALUE else it } }
            .map { (label, records) -> TrudyEvidenceGroup(label, records) }
        val visibleCount = used.count { it.role != TrudyEvidenceRole.DATA_GAP }
        return TrudyUsedEvidencePresentation(
            retrievedRecordCount = retrievedRecordCount.coerceAtLeast(candidateRecords.size),
            usedSupportingRecordCount = used.count { it.role == TrudyEvidenceRole.SUPPORTING },
            usedContextRecordCount = used.count { it.role == TrudyEvidenceRole.CONTEXT },
            dataGapCount = used.count { it.role == TrudyEvidenceRole.DATA_GAP },
            groups = groups,
            excludedRetrievedRecordCount = (retrievedRecordCount - visibleCount).coerceAtLeast(0)
        )
    }

    private fun TrudyConversationEvidenceRecord.isRelevantTo(
        request: TrudyResolvedConversationRequest
    ): Boolean {
        if (identity.domain !in request.domains) return false
        if (identity.metricId != null && request.metrics.isNotEmpty() &&
            request.metrics.none { it.domain == identity.domain && it.metricId == identity.metricId }
        ) return false
        val evidenceRange = identity.range ?: identity.timestampEpochMs?.let { TrudyTimeRange(it, it) }
        return evidenceRange == null || evidenceRange.overlaps(request.timeframe.range)
    }

    private fun TrudyTimeRange.overlaps(other: TrudyTimeRange): Boolean =
        fromEpochMs <= other.toEpochMs && other.fromEpochMs <= toEpochMs

    private fun groupLabel(domain: HealthDomain): String = when (domain) {
        HealthDomain.SLEEP -> "Sleep"
        HealthDomain.BLOOD_PRESSURE, HealthDomain.BODY -> "Vitals"
        HealthDomain.EXERCISE -> "Exercise & heart rate"
        HealthDomain.ENVIRONMENT -> "Environment"
        HealthDomain.EMOTIONAL -> "Emotional"
        HealthDomain.NUTRITION -> "Nutrition"
        HealthDomain.HYDRATION -> "Hydration"
        HealthDomain.MINDFULNESS -> "Mindfulness"
        HealthDomain.CLINICAL -> "Clinical"
    }

    private companion object {
        val GROUP_ORDER = listOf(
            "Sleep", "Vitals", "Exercise & heart rate", "Environment", "Emotional",
            "Nutrition", "Hydration", "Mindfulness", "Clinical"
        )
    }
}
