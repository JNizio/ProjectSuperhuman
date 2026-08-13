package com.projectsuperhuman.next.core

/**
 * Ordered composition of metric registries. Earlier registries take precedence for lookup while
 * definition enumeration is de-duplicated by domain + canonical ID.
 */
class CompositeMetricRegistry(
    private vararg val registries: MetricRegistry
) : MetricRegistry {
    init {
        require(registries.isNotEmpty()) { "At least one metric registry is required" }
    }

    override fun definition(domain: HealthDomain, metricOrAlias: String): MetricDefinition? =
        registries.firstNotNullOfOrNull { it.definition(domain, metricOrAlias) }

    override fun definitions(domain: HealthDomain?): List<MetricDefinition> =
        registries.flatMap { it.definitions(domain) }.distinctBy { it.domain to it.id }
}
