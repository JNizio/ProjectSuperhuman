package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.MetricRegistry
import com.projectsuperhuman.next.core.ModuleDataQuality
import com.projectsuperhuman.next.core.ModuleDerivedFeatures
import com.projectsuperhuman.next.core.ModuleInsightKind
import com.projectsuperhuman.next.core.ModuleParityInsight
import com.projectsuperhuman.next.core.ModuleParityService
import com.projectsuperhuman.next.core.ScientificEngine
import com.projectsuperhuman.next.core.ScientificInsight

/**
 * Application-layer read service for Trudy.
 *
 * This deliberately depends only on shared core contracts/services. It has no SQLDelight,
 * repository implementation, Health Connect, OCR, UI, or transient workout-state dependency.
 */
class TrudyHealthContextService(
    private val parity: ModuleParityService,
    private val scientificEngine: ScientificEngine? = null,
    private val registry: MetricRegistry = CoreMetricRegistry
) {
    suspend fun currentState(domain: HealthDomain): List<TrudyMetricEvidence> {
        val quality = dataQuality(domain)
        return parity.currentState(domain).latestByMetric.map { it.toMetricEvidence(quality) }
    }

    suspend fun domainHistory(
        domain: HealthDomain,
        limit: Int = DEFAULT_HISTORY_LIMIT,
        offset: Int = 0
    ): List<TrudyMetricEvidence> {
        val history = parity.history(domain, limit.coerceIn(1, MAX_DOMAIN_HISTORY), offset.coerceAtLeast(0))
        val quality = dataQuality(domain)
        return history.values.map { it.toMetricEvidence(quality) }
    }

    suspend fun metricHistory(
        domain: HealthDomain,
        metricId: String,
        limit: Int = DEFAULT_METRIC_HISTORY_LIMIT,
        offset: Int = 0
    ): List<TrudyMetricEvidence> {
        val safeLimit = limit.coerceIn(1, MAX_METRIC_HISTORY)
        val safeOffset = offset.coerceAtLeast(0)
        val canonicalMetric = canonicalMetric(domain, metricId)
        val scanLimit = ((safeLimit + safeOffset) * METRIC_SCAN_MULTIPLIER)
            .coerceAtLeast(safeLimit)
            .coerceAtMost(MAX_METRIC_SCAN)
        val history = parity.history(domain, scanLimit, 0)
        val quality = dataQuality(domain)
        return history.values.asSequence()
            .filter { it.metric == canonicalMetric }
            .drop(safeOffset)
            .take(safeLimit)
            .map { it.toMetricEvidence(quality) }
            .toList()
    }

    suspend fun derivedFeatures(domain: HealthDomain): List<TrudyDerivedMetricEvidence> {
        val quality = dataQuality(domain)
        return parity.derivedFeatures(domain).toTrudyEvidence(domain, quality)
    }

    suspend fun insights(domain: HealthDomain): List<TrudyInsightEvidence> {
        val quality = dataQuality(domain)
        val parityInsights = parity.insights(domain).map { it.toTrudyEvidence(quality) }
        val science = scientificEngine ?: return parityInsights
        val boundedValues = parity.history(domain, INTERPRETATION_HISTORY_LIMIT, 0).values
        if (boundedValues.isEmpty()) return parityInsights
        val interpreted = science.interpret(boundedValues)
            .filter { it.domain == domain }
            .map { it.toTrudyEvidence(quality) }
        return parityInsights + interpreted
    }

    suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence =
        parity.dataQuality(domain).toTrudyEvidence()

    suspend fun context(request: TrudyContextRequest): TrudyHealthContext {
        val domainContexts = request.domains.map { domain ->
            val quality = if (
                request.includeDataQuality || request.includeCurrentState || request.includeHistory ||
                request.includeDerivedFeatures || request.includeInsights
            ) dataQuality(domain) else null

            TrudyDomainContext(
                domain = domain,
                currentState = if (request.includeCurrentState) {
                    parity.currentState(domain).latestByMetric.map { it.toMetricEvidence(quality) }
                } else emptyList(),
                history = if (request.includeHistory) {
                    parity.history(
                        domain,
                        request.historyLimitPerDomain.coerceIn(1, MAX_DOMAIN_HISTORY),
                        request.historyOffsetPerDomain.coerceAtLeast(0)
                    ).values.map { it.toMetricEvidence(quality) }
                } else emptyList(),
                derivedFeatures = if (request.includeDerivedFeatures) {
                    parity.derivedFeatures(domain).toTrudyEvidence(domain, quality)
                } else emptyList(),
                insights = if (request.includeInsights) insights(domain) else emptyList(),
                dataQuality = if (request.includeDataQuality) quality else null
            )
        }
        return TrudyHealthContext(request.domains, domainContexts)
    }

    private fun canonicalMetric(domain: HealthDomain, metricId: String): String =
        registry.definition(domain, metricId)?.id ?: metricId.trim()

    private fun HealthValue.toMetricEvidence(quality: TrudyDataQualityEvidence?) = TrudyMetricEvidence(
        domain = domain,
        metricId = metric,
        value = value,
        unit = unit,
        timestampEpochMs = timestampEpochMs,
        source = source,
        dataQuality = quality,
        metadata = metadata
    )

    private fun ModuleDerivedFeatures.toTrudyEvidence(
        expectedDomain: HealthDomain,
        quality: TrudyDataQualityEvidence?
    ): List<TrudyDerivedMetricEvidence> {
        require(domain == expectedDomain) { "Module parity returned the wrong domain" }
        return metrics.map { feature ->
            TrudyDerivedMetricEvidence(
                domain = domain,
                metricId = feature.metric,
                unit = feature.unit,
                sampleCount = feature.sampleCount,
                latest = feature.latest,
                mean = feature.mean,
                minimum = feature.minimum,
                maximum = feature.maximum,
                change = feature.change,
                range = TrudyTimeRange(feature.firstTimestampEpochMs, feature.latestTimestampEpochMs),
                source = MODULE_PARITY_SOURCE,
                dataQuality = quality
            )
        }
    }

    private fun ModuleParityInsight.toTrudyEvidence(quality: TrudyDataQualityEvidence?) =
        TrudyInsightEvidence(
            id = id,
            domain = domain,
            evidenceKind = when (kind) {
                ModuleInsightKind.TREND_SIGNAL -> TrudyEvidenceKind.DERIVED_PERSONAL_TREND
                ModuleInsightKind.DATA_GAP -> TrudyEvidenceKind.UNCERTAINTY_OR_DATA_GAP
            },
            title = title,
            explanation = explanation,
            evidenceMetricIds = evidenceMetricIds,
            confidence = confidence,
            source = MODULE_PARITY_SOURCE,
            dataQuality = quality
        )

    private fun ScientificInsight.toTrudyEvidence(quality: TrudyDataQualityEvidence?) =
        TrudyInsightEvidence(
            id = id,
            domain = domain,
            evidenceKind = TrudyEvidenceKind.INTERPRETATION,
            title = title,
            explanation = explanation,
            evidenceMetricIds = evidenceMetricIds,
            confidence = confidence,
            source = "scientific-engine:$engineVersion",
            dataQuality = quality
        )

    private fun ModuleDataQuality.toTrudyEvidence() = TrudyDataQualityEvidence(
        domain = domain,
        score = score,
        recordCount = recordCount,
        distinctMetricCount = distinctMetricCount,
        latestTimestampEpochMs = latestTimestampEpochMs,
        ageHours = ageHours,
        isStale = isStale,
        notes = notes
    )

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 250
        const val MAX_DOMAIN_HISTORY = 1_000
        const val DEFAULT_METRIC_HISTORY_LIMIT = 250
        const val MAX_METRIC_HISTORY = 1_000
        const val METRIC_SCAN_MULTIPLIER = 4
        const val MAX_METRIC_SCAN = 5_000
        const val INTERPRETATION_HISTORY_LIMIT = 1_000
        const val MODULE_PARITY_SOURCE = "module-parity"
    }
}
