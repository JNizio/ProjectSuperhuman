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
    val maxItems: Int = 8,
    /** Filled once by the coordinator and reused by all corpus adapters for this retrieval. */
    val routing: TrudyLanguageRouting? = null
) {
    init {
        require(userText.isNotBlank())
        require(domains.distinct().size == domains.size)
        require(maxItems in 1..24)
        require(routing == null || routing.originalText == userText)
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
    val lastReviewed: String,
    /** Bounded lexical/source relevance for ranking only; never diagnostic likelihood. */
    val lexicalRelevance: Int = 0
) {
    init {
        require(stableId.isNotBlank() && title.isNotBlank() && summary.isNotBlank())
        require(sourceId.isNotBlank() && version.isNotBlank() && lastReviewed.isNotBlank())
        require(sourceReferences.isNotEmpty()) { "Knowledge must retain provenance" }
        require(relevantDomains.distinct().size == relevantDomains.size)
        require(relevantMetricIds.none(String::isBlank))
        require(metricHints.distinct().size == metricHints.size)
        require(lexicalRelevance in 0..160)
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
        val routedQuery = if (query.routing == null) {
            query.copy(
                routing = TrudyLanguageRouter.route(
                    query.userText,
                    maxTopics = minOf(TrudyLanguageRouter.MAX_TOPICS, maxOf(query.maxItems, 4))
                )
            )
        } else query
        val routing = routedQuery.routing ?: return emptyList()
        if (routing.productOnlyIntent) return emptyList()

        val ranked = sources.withIndex()
            .filter { (_, source) -> TrudyKnowledgeRelevanceGate.allows(source, routedQuery, routing) }
            .flatMap { (sourceIndex, source) ->
                runCatching {
                    source.retrieve(routedQuery.copy(maxItems = minOf(routedQuery.maxItems, maxPerSource)))
                }.getOrDefault(emptyList())
                    .filter { it.sourceId == source.sourceId && it.kind in source.kinds }
                    .take(maxPerSource)
                    .mapIndexed { itemIndex, item -> RankedKnowledgeItem(item, sourceIndex, itemIndex) }
            }
            .sortedWith(
                compareByDescending<RankedKnowledgeItem> { candidate ->
                    routing.scoreForKind(candidate.item.kind) * ROUTE_SCORE_WEIGHT +
                        candidate.item.lexicalRelevance +
                        explicitContextBonus(candidate.item, routedQuery)
                }.thenByDescending { it.item.lexicalRelevance }
                    .thenBy { it.sourceIndex }
                    .thenBy { it.itemIndex }
                    .thenBy { it.item.stableId }
            )
            .distinctBy { it.item.kind to it.item.stableId }

        val bounded = minOf(routedQuery.maxItems, maxTotal)
        if (bounded <= 0 || ranked.isEmpty()) return emptyList()

        // Preserve one best item for each routed lane before filling remaining top-K positions.
        val selected = mutableListOf<RankedKnowledgeItem>()
        val routedKinds = routing.matches.flatMap { it.kinds }.distinct()
        routedKinds.forEach { kind ->
            if (selected.size >= bounded) return@forEach
            ranked.firstOrNull { it.item.kind == kind && it !in selected }?.let(selected::add)
        }
        ranked.forEach { candidate ->
            if (selected.size < bounded && candidate !in selected) selected += candidate
        }
        return selected.take(bounded).map { it.item }
    }

    private data class RankedKnowledgeItem(
        val item: TrudyKnowledgeItem,
        val sourceIndex: Int,
        val itemIndex: Int
    )

    private companion object { const val ROUTE_SCORE_WEIGHT = 2 }
}

/** Source-level relevance gate: domain fallbacks cannot leak into unrelated product questions. */
private object TrudyKnowledgeRelevanceGate {
    fun allows(
        source: TrudyKnowledgeSource,
        query: TrudyKnowledgeQuery,
        routing: TrudyLanguageRouting
    ): Boolean {
        if (routing.productOnlyIntent) return false
        if (source.kinds.any(routing::hasKind)) return true
        // The mature performance source owns a larger indexed lexicon (naps, shifts, readiness,
        // training modalities, etc.). Let that internal gate run so this shared layer cannot
        // narrow pre-existing coverage merely because a phrase is absent from the cross-corpus map.
        if (TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE in source.kinds) return true
        if (source.kinds.any { kind -> query.domains.any { it in domainsFor(kind) } }) return true
        return source.kinds.any { kind -> query.metricIds.any { metricLooksRelevant(it, kind) } }
    }

    private fun domainsFor(kind: TrudyKnowledgeKind): Set<HealthDomain> = when (kind) {
        TrudyKnowledgeKind.MEDICAL -> setOf(HealthDomain.CLINICAL, HealthDomain.BLOOD_PRESSURE)
        TrudyKnowledgeKind.NUTRITION -> setOf(HealthDomain.NUTRITION, HealthDomain.HYDRATION, HealthDomain.BODY)
        TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE -> setOf(HealthDomain.SLEEP, HealthDomain.EXERCISE)
        TrudyKnowledgeKind.ENVIRONMENT -> setOf(HealthDomain.ENVIRONMENT)
        TrudyKnowledgeKind.EMOTIONAL_WELLBEING -> setOf(HealthDomain.EMOTIONAL, HealthDomain.MINDFULNESS)
        TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY -> emptySet()
    }

    private fun metricLooksRelevant(metricId: String, kind: TrudyKnowledgeKind): Boolean {
        val metric = metricId.lowercase()
        return when (kind) {
            TrudyKnowledgeKind.MEDICAL -> metric.startsWith("clinical_") || metric.startsWith("blood_pressure_")
            TrudyKnowledgeKind.NUTRITION -> metric.startsWith("food_") || metric.startsWith("water_") ||
                metric.startsWith("hydration_") || metric.startsWith("body_") || metric == "weight_kg"
            TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE -> metric.startsWith("sleep_") || metric.startsWith("exercise_") ||
                metric.startsWith("heart_rate_") || metric == "steps"
            TrudyKnowledgeKind.ENVIRONMENT -> metric.startsWith("environment_") || metric.startsWith("weather_")
            TrudyKnowledgeKind.EMOTIONAL_WELLBEING -> metric.startsWith("emotional_") || metric.startsWith("mindfulness_")
            TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY -> false
        }
    }
}

private fun explicitContextBonus(item: TrudyKnowledgeItem, query: TrudyKnowledgeQuery): Int {
    val domainMatch = item.relevantDomains.any { it in query.domains }
    val metricMatch = item.relevantMetricIds.any { it in query.metricIds }
    return when {
        metricMatch -> 24
        domainMatch -> 12
        else -> 0
    }
}
