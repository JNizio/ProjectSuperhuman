package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/**
 * Coarse knowledge lanes used by orchestration. Domain repositories keep their richer schemas
 * behind adapters; this enum is only a retrieval/synthesis boundary.
 */
enum class TrudyKnowledgeKind {
    MEDICAL,
    NUTRITION,
    SLEEP_AND_PERFORMANCE,
    ENVIRONMENT,
    EMOTIONAL_WELLBEING,
    EXPERIMENT_METHODOLOGY
}

enum class TrudyKnowledgeMetricRole {
    PRIMARY_OUTCOME,
    SECONDARY_OUTCOME,
    EXPOSURE,
    CONFOUNDER,
    CONTEXT,
    DATA_QUALITY
}

data class TrudyKnowledgeMetricHint(
    val domain: HealthDomain,
    val metricId: String,
    val role: TrudyKnowledgeMetricRole = TrudyKnowledgeMetricRole.CONTEXT
) {
    init { require(metricId.isNotBlank()) }
}

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
    val metricHints: List<TrudyKnowledgeMetricHint> = emptyList(),
    val uncertainty: String? = null,
    val safetyNotes: List<String> = emptyList(),
    val version: String,
    val lastReviewed: String
) {
    init {
        require(stableId.isNotBlank() && title.isNotBlank() && summary.isNotBlank())
        require(sourceId.isNotBlank() && version.isNotBlank() && lastReviewed.isNotBlank())
        require(sourceReferences.isNotEmpty()) { "Knowledge must retain provenance" }
        require(relevantDomains.distinct().size == relevantDomains.size)
        require(relevantMetricIds.none(String::isBlank))
        require(metricHints.distinct().size == metricHints.size)
    }
}

/**
 * Stable provider boundary consumed by Trudy. Retrieval is query-scoped and bounded; personal
 * observations never live here and remain owned by the Data Vault/tool layer.
 */
interface TrudyKnowledgeProvider {
    suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem>
}

/**
 * Adapter implemented by each curated corpus. A source may expose several coarse lanes while
 * still retaining its own domain-specific models, terminology index and provenance structures.
 */
interface TrudyKnowledgeSource {
    val sourceId: String
    val kind: TrudyKnowledgeKind
    val kinds: Set<TrudyKnowledgeKind> get() = setOf(kind)
    suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem>
}

class TrudyKnowledgeCoordinator(
    sources: List<TrudyKnowledgeSource> = emptyList(),
    private val maxPerSource: Int = 4,
    private val maxTotal: Int = 8
) : TrudyKnowledgeProvider {
    private val sources: List<TrudyKnowledgeSource> = sources
        .ifEmpty(::defaultTrudyKnowledgeSources)
        .distinctBy { it.sourceId }

    init {
        require(this.sources.distinctBy { it.sourceId }.size == this.sources.size)
        require(this.sources.all { it.kinds.isNotEmpty() && it.kind in it.kinds })
        require(maxPerSource in 1..12 && maxTotal in 1..24)
    }

    override suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem> {
        if (sources.isEmpty()) return emptyList()
        return sources.filter { TrudyKnowledgeRelevanceGate.allows(it, query) }
            .flatMap { source ->
                runCatching { source.retrieve(query.copy(maxItems = minOf(query.maxItems, maxPerSource))) }
                    .getOrDefault(emptyList())
                    .filter { it.sourceId == source.sourceId && it.kind in source.kinds }
                    .take(maxPerSource)
            }
            .distinctBy { it.kind to it.stableId }
            .take(minOf(query.maxItems, maxTotal))
    }
}

/**
 * Source-level gate prevents a domain's internal fallback intent from becoming broad cross-domain
 * retrieval. Rich terminology matching remains inside each domain-specific indexed repository.
 */
private object TrudyKnowledgeRelevanceGate {
    private val nutritionDomains = setOf(HealthDomain.NUTRITION, HealthDomain.HYDRATION, HealthDomain.BODY)
    private val nutritionTerms = setOf(
        "nutrition", "food", "foods", "diet", "dietary", "meal", "meals", "calorie", "calories",
        "protein", "carb", "carbs", "carbohydrate", "fat", "fibre", "fiber", "vitamin", "mineral",
        "nutrient", "nutrients", "hydration", "hydrated", "water", "electrolyte", "electrolytes",
        "weight", "body composition", "body fat", "metabolism", "glycaemic", "glycemic"
    )

    fun allows(source: TrudyKnowledgeSource, query: TrudyKnowledgeQuery): Boolean {
        if (source.kind != TrudyKnowledgeKind.NUTRITION) return true
        if (query.domains.any { it in nutritionDomains }) return true
        val normalized = query.userText.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        if (normalized.isBlank()) return false
        val tokens = normalized.split(' ').toSet()
        return nutritionTerms.any { term ->
            if (' ' in term) normalized.contains(term) else term in tokens
        }
    }
}
