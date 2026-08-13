package com.projectsuperhuman.next.emotional

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.MetricAggregation
import com.projectsuperhuman.next.core.MetricDefinition
import com.projectsuperhuman.next.core.MetricRegistry

/**
 * Canonical metric registry owned by the Emotional domain. It mirrors Project Superhuman's shared
 * metric-registry contract without requiring this module to mutate persistence or other domains.
 */
object EmotionalMetricRegistry : MetricRegistry {
    private val items: List<MetricDefinition> = CanonicalEmotionalScaleCatalog.definitions().map { scale ->
        MetricDefinition(
            id = scale.metricId.value,
            domain = HealthDomain.EMOTIONAL,
            canonicalUnit = EmotionalNumericSemantics.CANONICAL_UNIT,
            aggregation = MetricAggregation.AVERAGE,
            minAccepted = EmotionalNumericSemantics.MIN,
            maxAccepted = EmotionalNumericSemantics.MAX
        )
    }
    private val byId = items.associateBy { it.id.lowercase() }

    override fun definition(domain: HealthDomain, metricOrAlias: String): MetricDefinition? {
        if (domain != HealthDomain.EMOTIONAL) return null
        return byId[metricOrAlias.trim().lowercase()]
    }

    override fun definitions(domain: HealthDomain?): List<MetricDefinition> = when (domain) {
        null, HealthDomain.EMOTIONAL -> items
        else -> emptyList()
    }
}

/**
 * Small composition adapter for ingestion/integration code that already owns another registry.
 * Emotional definitions take precedence while every other domain delegates untouched.
 */
class EmotionalMetricRegistryOverlay(
    private val fallback: MetricRegistry
) : MetricRegistry {
    override fun definition(domain: HealthDomain, metricOrAlias: String): MetricDefinition? =
        EmotionalMetricRegistry.definition(domain, metricOrAlias) ?: fallback.definition(domain, metricOrAlias)

    override fun definitions(domain: HealthDomain?): List<MetricDefinition> {
        val emotional = EmotionalMetricRegistry.definitions(domain)
        val existing = fallback.definitions(domain)
        return (emotional + existing).distinctBy { it.domain to it.id }
    }
}
