package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.MetricRegistry
import com.projectsuperhuman.next.core.ModuleDataQuality
import com.projectsuperhuman.next.core.ModuleInsightKind
import com.projectsuperhuman.next.core.ModuleParityInsight
import com.projectsuperhuman.next.core.ModuleParityService
import com.projectsuperhuman.next.core.SYNTHETIC_DATA_SOURCE
import com.projectsuperhuman.next.core.ScientificEngine
import com.projectsuperhuman.next.core.ScientificInsight

/**
 * Application-layer read service for Trudy.
 *
 * This deliberately depends only on shared core contracts/services. It has no SQLDelight,
 * repository implementation, Health Connect, OCR, UI, or transient workout-state dependency.
 *
 * Developer-generated synthetic rows are never personal evidence. Every raw read is filtered here,
 * at the single shared Trudy boundary, so local/hosted models, investigations and evidence UI all
 * inherit the same rule rather than relying on prompt instructions to remember it.
 */
class TrudyHealthContextService(
    private val parity: ModuleParityService,
    private val scientificEngine: ScientificEngine? = null,
    private val registry: MetricRegistry = CoreMetricRegistry
) {
    suspend fun currentState(domain: HealthDomain): List<TrudyMetricEvidence> {
        val quality = dataQuality(domain)
        return currentState(domain, quality)
    }

    suspend fun domainHistory(
        domain: HealthDomain,
        limit: Int = DEFAULT_HISTORY_LIMIT,
        offset: Int = 0
    ): List<TrudyMetricEvidence> {
        val safeLimit = limit.coerceIn(1, MAX_DOMAIN_HISTORY)
        val safeOffset = offset.coerceAtLeast(0)
        val quality = dataQuality(domain)
        return realDomainHistory(domain, safeLimit, safeOffset).map { it.toMetricEvidence(quality) }
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
        val quality = dataQuality(domain)
        return realMetricHistory(domain, canonicalMetric, safeLimit, safeOffset)
            .map { it.toMetricEvidence(quality) }
    }

    suspend fun metricWindow(
        domain: HealthDomain,
        metricId: String,
        range: TrudyTimeRange,
        limit: Int = DEFAULT_METRIC_WINDOW_LIMIT
    ): List<TrudyMetricEvidence> {
        val safeLimit = limit.coerceIn(1, MAX_METRIC_HISTORY)
        val canonicalMetric = canonicalMetric(domain, metricId)
        val quality = dataQuality(domain)
        val first = parity.metricWindow(
            domain = domain,
            metric = canonicalMetric,
            fromEpochMs = range.fromEpochMs,
            toEpochMs = range.toEpochMs,
            limit = safeLimit
        ).values
        val filtered = first.filterNot(::isSyntheticForTrudy)
        val rows = if (first.any(::isSyntheticForTrudy) && filtered.size < safeLimit && first.size >= safeLimit) {
            parity.metricWindow(
                domain = domain,
                metric = canonicalMetric,
                fromEpochMs = range.fromEpochMs,
                toEpochMs = range.toEpochMs,
                limit = MAX_METRIC_HISTORY
            ).values.filterNot(::isSyntheticForTrudy).take(safeLimit)
        } else filtered.take(safeLimit)
        return rows.map { it.toMetricEvidence(quality) }
    }

    suspend fun derivedFeatures(domain: HealthDomain): List<TrudyDerivedMetricEvidence> {
        val quality = dataQuality(domain)
        // Module-parity derived values have intentionally lost row-level source identity. If the
        // underlying domain contains developer synthetic history, suppress these aggregates rather
        // than risk presenting a mixed real/synthetic trend as personal evidence. Raw deterministic
        // comparisons remain available through metric-window tools.
        if (containsRecentSynthetic(domain)) return emptyList()
        return parity.derivedFeatures(domain).toTrudyEvidence(domain, quality)
    }

    suspend fun insights(domain: HealthDomain): List<TrudyInsightEvidence> {
        val quality = dataQuality(domain)
        val hasSynthetic = containsRecentSynthetic(domain)
        val parityInsights = if (hasSynthetic) emptyList() else {
            parity.insights(domain).map { it.toTrudyEvidence(quality) }
        }
        val science = scientificEngine ?: return parityInsights
        val boundedValues = realDomainHistory(domain, INTERPRETATION_HISTORY_LIMIT, 0)
        if (boundedValues.isEmpty()) return parityInsights
        val interpreted = science.interpret(boundedValues)
            .filter { it.domain == domain }
            .map { it.toTrudyEvidence(quality) }
        return parityInsights + interpreted
    }

    suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence {
        val parityQuality = parity.dataQuality(domain)
        val probe = parity.history(domain, QUALITY_PROBE_LIMIT, 0).values
        if (probe.none(::isSyntheticForTrudy)) return parityQuality.toTrudyEvidence()

        val real = probe.filterNot(::isSyntheticForTrudy)
        val latest = real.maxOfOrNull { it.timestampEpochMs }
        val ageHours = latest?.let {
            ((System.currentTimeMillis() - it).coerceAtLeast(0L)).toDouble() / HOUR_MS
        }
        val conservativeCap = when {
            real.isEmpty() -> 0
            real.size < 3 -> 35
            real.size < 7 -> 50
            real.size < 14 -> 65
            else -> 90
        }
        return TrudyDataQualityEvidence(
            domain = domain,
            score = minOf(parityQuality.score, conservativeCap),
            recordCount = real.size.toLong(),
            distinctMetricCount = real.map { it.metric }.distinct().size,
            latestTimestampEpochMs = latest,
            ageHours = ageHours,
            isStale = latest == null || (ageHours ?: Double.POSITIVE_INFINITY) > REAL_DATA_STALE_HOURS,
            notes = (parityQuality.notes + "Developer synthetic rows were excluded from Trudy personal evidence.")
                .distinct()
                .take(MAX_QUALITY_NOTES)
        )
    }

    suspend fun context(request: TrudyContextRequest): TrudyHealthContext {
        val domainContexts = request.domains.map { domain ->
            val quality = if (
                request.includeDataQuality || request.includeCurrentState || request.includeHistory ||
                request.includeDerivedFeatures || request.includeInsights
            ) dataQuality(domain) else null

            TrudyDomainContext(
                domain = domain,
                currentState = if (request.includeCurrentState) currentState(domain, quality) else emptyList(),
                history = if (request.includeHistory) {
                    realDomainHistory(
                        domain,
                        request.historyLimitPerDomain.coerceIn(1, MAX_DOMAIN_HISTORY),
                        request.historyOffsetPerDomain.coerceAtLeast(0)
                    ).map { it.toMetricEvidence(quality) }
                } else emptyList(),
                derivedFeatures = if (request.includeDerivedFeatures) {
                    if (containsRecentSynthetic(domain)) emptyList()
                    else parity.derivedFeatures(domain).toTrudyEvidence(domain, quality)
                } else emptyList(),
                insights = if (request.includeInsights) insights(domain) else emptyList(),
                dataQuality = if (request.includeDataQuality) quality else null
            )
        }
        return TrudyHealthContext(request.domains, domainContexts)
    }

    private suspend fun currentState(
        domain: HealthDomain,
        quality: TrudyDataQualityEvidence?
    ): List<TrudyMetricEvidence> {
        val latest = parity.currentState(domain).latestByMetric
        val resolved = latest.mapNotNull { value ->
            if (!isSyntheticForTrudy(value)) value
            else realMetricHistory(domain, value.metric, limit = 1, offset = 0).firstOrNull()
        }
        return resolved.distinctBy { it.metric }.map { it.toMetricEvidence(quality) }
    }

    /** Offset is defined over REAL rows, not over hidden developer rows. */
    private suspend fun realDomainHistory(domain: HealthDomain, limit: Int, offset: Int): List<HealthValue> {
        val targetCount = (limit + offset).coerceAtMost(MAX_SOURCE_FILTER_SCAN)
        val real = ArrayList<HealthValue>(targetCount)
        var rawOffset = 0
        while (real.size < targetCount && rawOffset < MAX_SOURCE_FILTER_SCAN) {
            val pageSize = minOf(SOURCE_FILTER_PAGE, MAX_SOURCE_FILTER_SCAN - rawOffset)
            val page = parity.history(domain, pageSize, rawOffset).values
            if (page.isEmpty()) break
            real += page.filterNot(::isSyntheticForTrudy)
            rawOffset += page.size
            if (page.size < pageSize) break
        }
        return real.drop(offset).take(limit)
    }

    /** Offset is defined over REAL rows, not over hidden developer rows. */
    private suspend fun realMetricHistory(
        domain: HealthDomain,
        metric: String,
        limit: Int,
        offset: Int
    ): List<HealthValue> {
        val targetCount = (limit + offset).coerceAtMost(MAX_SOURCE_FILTER_SCAN)
        val real = ArrayList<HealthValue>(targetCount)
        var rawOffset = 0
        while (real.size < targetCount && rawOffset < MAX_SOURCE_FILTER_SCAN) {
            val pageSize = minOf(SOURCE_FILTER_PAGE, MAX_SOURCE_FILTER_SCAN - rawOffset)
            val page = parity.metricHistory(domain, metric, pageSize, rawOffset).values
            if (page.isEmpty()) break
            real += page.filterNot(::isSyntheticForTrudy)
            rawOffset += page.size
            if (page.size < pageSize) break
        }
        return real.drop(offset).take(limit)
    }

    private suspend fun containsRecentSynthetic(domain: HealthDomain): Boolean =
        parity.history(domain, SYNTHETIC_PROBE_LIMIT, 0).values.any(::isSyntheticForTrudy)

    private fun isSyntheticForTrudy(value: HealthValue): Boolean {
        val source = value.source.trim().lowercase()
        return source == SYNTHETIC_DATA_SOURCE.lowercase() ||
            source.startsWith("project-superhuman-synthetic-") ||
            value.metadata["synthetic"]?.equals("true", ignoreCase = true) == true
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

    private fun com.projectsuperhuman.next.core.ModuleDerivedFeatures.toTrudyEvidence(
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
        const val DEFAULT_METRIC_WINDOW_LIMIT = 250
        const val MAX_METRIC_HISTORY = 5_000
        const val INTERPRETATION_HISTORY_LIMIT = 1_000
        const val SOURCE_FILTER_PAGE = 500
        const val MAX_SOURCE_FILTER_SCAN = 5_000
        const val QUALITY_PROBE_LIMIT = 500
        const val SYNTHETIC_PROBE_LIMIT = 500
        const val MAX_QUALITY_NOTES = 12
        const val HOUR_MS = 3_600_000.0
        const val REAL_DATA_STALE_HOURS = 72.0
        const val MODULE_PARITY_SOURCE = "module-parity"
    }
}
