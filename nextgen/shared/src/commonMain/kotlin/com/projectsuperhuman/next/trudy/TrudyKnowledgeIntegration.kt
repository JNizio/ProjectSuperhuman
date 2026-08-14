package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

enum class TrudyKnowledgeKind { MEDICAL, NUTRITION, SLEEP_AND_PERFORMANCE }

data class TrudyKnowledgeQuery(
    val userText: String,
    val domains: List<HealthDomain>,
    val metricIds: List<String>,
    val maxItems: Int = 8
) {
    init {
        require(userText.isNotBlank())
        require(domains.distinct().size == domains.size)
        require(maxItems in 1..24)
    }
}

data class TrudyKnowledgeItem(
    val stableId: String,
    val kind: TrudyKnowledgeKind,
    val title: String,
    val summary: String,
    val sourceId: String,
    val sourceReferences: List<String>,
    val relevantDomains: List<HealthDomain> = emptyList(),
    val relevantMetricIds: List<String> = emptyList(),
    val uncertainty: String? = null,
    val safetyNotes: List<String> = emptyList(),
    val version: String,
    val lastReviewed: String
) {
    init {
        require(stableId.isNotBlank() && title.isNotBlank() && summary.isNotBlank())
        require(sourceId.isNotBlank() && version.isNotBlank() && lastReviewed.isNotBlank())
        require(sourceReferences.isNotEmpty()) { "Knowledge must retain provenance" }
    }
}

/**
 * Integration contract owned by orchestration, not by any corpus. Knowledge agents implement this
 * adapter without changing Trudy prompts, Data Vault retrieval, or cross-domain calculations.
 */
interface TrudyKnowledgeSource {
    val sourceId: String
    val kind: TrudyKnowledgeKind
    suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem>
}

class TrudyKnowledgeCoordinator(
    private val sources: List<TrudyKnowledgeSource> = emptyList(),
    private val maxPerSource: Int = 4,
    private val maxTotal: Int = 8
) {
    init {
        require(sources.distinctBy { it.sourceId }.size == sources.size)
        require(maxPerSource in 1..12 && maxTotal in 1..24)
    }

    suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem> {
        if (sources.isEmpty()) return emptyList()
        return sources.flatMap { source ->
            runCatching { source.retrieve(query.copy(maxItems = minOf(query.maxItems, maxPerSource))) }
                .getOrDefault(emptyList())
                .filter { it.sourceId == source.sourceId && it.kind == source.kind }
                .take(maxPerSource)
        }.distinctBy { it.kind to it.stableId }
            .take(minOf(query.maxItems, maxTotal))
    }
}
