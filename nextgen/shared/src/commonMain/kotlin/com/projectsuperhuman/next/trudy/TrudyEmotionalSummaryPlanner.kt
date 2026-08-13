package com.projectsuperhuman.next.trudy

internal object TrudyEmotionalSummaryPlanner {
    fun plan(domain: TrudyDomainContext): List<TrudyToolOperation> =
        TrudyEmotionalToolPlanner.metricIds(domain)
            .mapNotNull { id -> TrudyEmotionalSemantics.semanticForMetric(id)?.let { semantic -> semantic.axis to id } }
            .distinctBy { it.first }
            .map { (_, id) -> GetPersonalTrend(domain.domain, id, recentDays = 7, baselineDays = 28) }
}
