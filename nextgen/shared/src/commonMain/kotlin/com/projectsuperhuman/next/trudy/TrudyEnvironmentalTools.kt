package com.projectsuperhuman.next.trudy

/** Builds only existing typed Trudy operations for Environmental evidence. */
class TrudyEnvironmentalTools(private val config: TrudyEnvironmentalConfig) {
    fun currentState(): List<TrudyToolOperation> = listOf(
        TrudyToolOperation.GetDomainState(config.environmentDomain),
        TrudyToolOperation.GetDataQuality(config.environmentDomain)
    )

    fun association(
        environmentalMetric: TrudyEnvironmentalMetricBinding,
        target: TrudyEnvironmentalTargetBinding
    ): List<TrudyToolOperation> = listOf(
        TrudyToolOperation.GetMetricHistory(config.environmentDomain, environmentalMetric.metricId, 64),
        TrudyToolOperation.GetDataQuality(config.environmentDomain),
        GetAssociation(
            leftDomain = config.environmentDomain,
            leftMetricId = environmentalMetric.metricId,
            rightDomain = target.domain,
            rightMetricId = target.metricId,
            method = TrudyAssociationMethod.SPEARMAN,
            alignmentWindowMs = config.associationAlignmentWindowMs
        )
    )

    fun associations(
        environmentalMetrics: List<TrudyEnvironmentalMetricBinding>,
        target: TrudyEnvironmentalTargetBinding
    ): List<TrudyToolOperation> = buildList {
        environmentalMetrics.distinctBy { it.metricId }
            .take(config.maxBroadAssociationMetrics)
            .forEach { metric ->
                add(TrudyToolOperation.GetMetricHistory(config.environmentDomain, metric.metricId, 64))
                add(
                    GetAssociation(
                        leftDomain = config.environmentDomain,
                        leftMetricId = metric.metricId,
                        rightDomain = target.domain,
                        rightMetricId = target.metricId,
                        method = TrudyAssociationMethod.SPEARMAN,
                        alignmentWindowMs = config.associationAlignmentWindowMs
                    )
                )
            }
        add(TrudyToolOperation.GetDataQuality(config.environmentDomain))
    }
}
