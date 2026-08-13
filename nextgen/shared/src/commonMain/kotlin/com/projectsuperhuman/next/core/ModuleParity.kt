package com.projectsuperhuman.next.core

import kotlin.math.abs

/**
 * Stable, domain-agnostic read model for every Project Superhuman module.
 *
 * This is the contract Trudy and future cross-module tooling should consume instead of
 * reaching into individual screen/view-model classes. All reads still flow through the
 * shared SQL-backed Data Vault through a domain-scoped ModuleDataPort, so no module-specific
 * duplicate database is introduced.
 */
data class ModuleCurrentState(
    val domain: HealthDomain,
    val latestByMetric: List<HealthValue>,
    val latestTimestampEpochMs: Long?
)

data class ModuleHistory(
    val domain: HealthDomain,
    val values: List<HealthValue>,
    val limit: Int,
    val offset: Int
)

data class ModuleMetricFeature(
    val metric: String,
    val unit: String,
    val sampleCount: Int,
    val latest: Double,
    val mean: Double,
    val minimum: Double,
    val maximum: Double,
    val change: Double?,
    val firstTimestampEpochMs: Long,
    val latestTimestampEpochMs: Long
)

data class ModuleDerivedFeatures(
    val domain: HealthDomain,
    val metrics: List<ModuleMetricFeature>
)

enum class ModuleInsightKind { TREND_SIGNAL, DATA_GAP }

data class ModuleParityInsight(
    val id: String,
    val domain: HealthDomain,
    val kind: ModuleInsightKind,
    val title: String,
    val explanation: String,
    val evidenceMetricIds: List<String>,
    val confidence: Double
)

data class ModuleDataQuality(
    val domain: HealthDomain,
    val score: Int,
    val recordCount: Long,
    val distinctMetricCount: Int,
    val latestTimestampEpochMs: Long?,
    val ageHours: Double?,
    val isStale: Boolean,
    val notes: List<String>
)

data class ModuleParitySnapshot(
    val domain: HealthDomain,
    val currentState: ModuleCurrentState,
    val history: ModuleHistory,
    val derivedFeatures: ModuleDerivedFeatures,
    val insights: List<ModuleParityInsight>,
    val dataQuality: ModuleDataQuality
)

/**
 * One implementation for every current HealthDomain.
 *
 * The five surfaces are deliberately conservative:
 *  1. current state
 *  2. history
 *  3. derived features
 *  4. non-diagnostic trend/data-gap insights
 *  5. data quality
 *
 * Domain-specific science remains in the Interpretation/Insights engines; this class provides
 * a uniform, safe baseline so no module needs a bespoke API just to become Trudy-ready.
 *
 * Read-side canonicalisation is intentionally non-destructive. Legacy metric aliases are exposed
 * through their current canonical IDs without rewriting historical SQL rows. This keeps old data
 * readable while all new writes continue through DataIngestionPipeline canonicalisation.
 */
class ModuleParityService(
    private val modulePort: (HealthDomain) -> ModuleDataPort,
    private val nowEpochMs: () -> Long,
    private val registry: MetricRegistry = CoreMetricRegistry
) {
    suspend fun currentState(domain: HealthDomain): ModuleCurrentState {
        val rows = modulePort(domain).page(metric = null, limit = MAX_SCAN_ROWS, offset = 0)
            .map(::canonicalize)
            .sortedByDescending { it.timestampEpochMs }
        val latest = rows.distinctBy { it.metric }
        return ModuleCurrentState(
            domain = domain,
            latestByMetric = latest,
            latestTimestampEpochMs = rows.firstOrNull()?.timestampEpochMs
        )
    }

    suspend fun history(
        domain: HealthDomain,
        limit: Int = DEFAULT_HISTORY_LIMIT,
        offset: Int = 0
    ): ModuleHistory {
        val safeLimit = limit.coerceIn(1, MAX_SCAN_ROWS)
        val safeOffset = offset.coerceAtLeast(0)
        val rows = modulePort(domain).page(
            metric = null,
            limit = safeLimit,
            offset = safeOffset
        ).map(::canonicalize).sortedByDescending { it.timestampEpochMs }
        return ModuleHistory(domain, rows, safeLimit, safeOffset)
    }

    suspend fun derivedFeatures(
        domain: HealthDomain,
        sampleLimit: Int = DEFAULT_FEATURE_ROWS
    ): ModuleDerivedFeatures {
        val rows = modulePort(domain).page(
            metric = null,
            limit = sampleLimit.coerceIn(1, MAX_SCAN_ROWS),
            offset = 0
        ).map(::canonicalize)

        val features = rows.groupBy { it.metric }
            .mapNotNull { (metric, metricRows) ->
                val ordered = metricRows.sortedBy { it.timestampEpochMs }
                val first = ordered.firstOrNull() ?: return@mapNotNull null
                val last = ordered.last()
                val values = ordered.map { it.value }
                ModuleMetricFeature(
                    metric = metric,
                    unit = last.unit,
                    sampleCount = ordered.size,
                    latest = last.value,
                    mean = values.average(),
                    minimum = values.minOrNull() ?: last.value,
                    maximum = values.maxOrNull() ?: last.value,
                    change = if (ordered.size >= 2) last.value - first.value else null,
                    firstTimestampEpochMs = first.timestampEpochMs,
                    latestTimestampEpochMs = last.timestampEpochMs
                )
            }
            .sortedBy { it.metric }

        return ModuleDerivedFeatures(domain, features)
    }

    suspend fun insights(domain: HealthDomain): List<ModuleParityInsight> {
        val features = derivedFeatures(domain)
        if (features.metrics.isEmpty()) {
            return listOf(
                ModuleParityInsight(
                    id = "${domain.name.lowercase()}-no-data",
                    domain = domain,
                    kind = ModuleInsightKind.DATA_GAP,
                    title = "No data yet",
                    explanation = "This module does not yet have enough stored data to describe a personal pattern.",
                    evidenceMetricIds = emptyList(),
                    confidence = 1.0
                )
            )
        }

        return features.metrics.mapNotNull { feature ->
            val delta = feature.change ?: return@mapNotNull null
            if (feature.sampleCount < MIN_TREND_SAMPLES) return@mapNotNull null
            val scale = abs(feature.mean).coerceAtLeast(MIN_SCALE)
            val relative = abs(delta) / scale
            if (relative < MIN_RELATIVE_CHANGE) return@mapNotNull null

            val direction = if (delta > 0.0) "higher" else "lower"
            val confidence = (0.45 + feature.sampleCount.coerceAtMost(25) / 50.0).coerceAtMost(0.9)
            ModuleParityInsight(
                id = "${domain.name.lowercase()}-${feature.metric}-trend",
                domain = domain,
                kind = ModuleInsightKind.TREND_SIGNAL,
                title = "${feature.metric.replace('_', ' ')} is trending $direction",
                explanation = "Across ${feature.sampleCount} recent observations, ${feature.metric.replace('_', ' ')} moved ${formatMagnitude(abs(delta))} ${feature.unit}. This is a descriptive personal trend, not evidence that another factor caused it.",
                evidenceMetricIds = listOf(feature.metric),
                confidence = confidence
            )
        }.sortedByDescending { it.confidence }
    }

    suspend fun dataQuality(domain: HealthDomain): ModuleDataQuality {
        val port = modulePort(domain)
        val recordCount = port.count()
        val recent = port.page(metric = null, limit = MAX_SCAN_ROWS, offset = 0)
            .map(::canonicalize)
            .sortedByDescending { it.timestampEpochMs }
        val latestTimestamp = recent.firstOrNull()?.timestampEpochMs
        val metricCount = recent.asSequence().map { it.metric }.distinct().count()
        val ageHours = latestTimestamp?.let { (nowEpochMs() - it).coerceAtLeast(0L) / HOUR_MS.toDouble() }
        val isStale = ageHours == null || ageHours > STALE_AFTER_HOURS

        val freshnessPoints = when {
            ageHours == null -> 0
            ageHours <= 48.0 -> 40
            ageHours <= 168.0 -> 28
            ageHours <= 720.0 -> 15
            else -> 5
        }
        val volumePoints = when {
            recordCount >= 100L -> 30
            recordCount >= 30L -> 24
            recordCount >= 10L -> 16
            recordCount > 0L -> 8
            else -> 0
        }
        val breadthPoints = when {
            metricCount >= 5 -> 30
            metricCount >= 3 -> 24
            metricCount >= 2 -> 16
            metricCount == 1 -> 8
            else -> 0
        }

        val notes = buildList {
            if (recordCount == 0L) add("No stored observations")
            else {
                if (isStale) add("Latest observation is stale")
                if (metricCount < 2) add("Only one metric is currently represented")
                if (recordCount < 10L) add("More observations would improve trend reliability")
            }
        }

        return ModuleDataQuality(
            domain = domain,
            score = (freshnessPoints + volumePoints + breadthPoints).coerceIn(0, 100),
            recordCount = recordCount,
            distinctMetricCount = metricCount,
            latestTimestampEpochMs = latestTimestamp,
            ageHours = ageHours,
            isStale = isStale,
            notes = notes
        )
    }

    suspend fun snapshot(
        domain: HealthDomain,
        historyLimit: Int = DEFAULT_HISTORY_LIMIT,
        historyOffset: Int = 0
    ): ModuleParitySnapshot = ModuleParitySnapshot(
        domain = domain,
        currentState = currentState(domain),
        history = history(domain, historyLimit, historyOffset),
        derivedFeatures = derivedFeatures(domain),
        insights = insights(domain),
        dataQuality = dataQuality(domain)
    )

    suspend fun allModules(): Map<HealthDomain, ModuleParitySnapshot> =
        HealthDomain.entries.associateWith { domain -> snapshot(domain) }

    private fun canonicalize(value: HealthValue): HealthValue {
        val definition = registry.definition(value.domain, value.metric) ?: return value
        if (definition.id == value.metric) return value
        return value.copy(
            metric = definition.id,
            metadata = value.metadata + ("parityOriginalMetric" to value.metric)
        )
    }

    private fun formatMagnitude(value: Double): String =
        if (value >= 10.0) value.toInt().toString() else ((value * 10.0).toInt() / 10.0).toString()

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 250
        const val DEFAULT_FEATURE_ROWS = 1_000
        const val MAX_SCAN_ROWS = 5_000
        const val MIN_TREND_SAMPLES = 3
        const val MIN_RELATIVE_CHANGE = 0.05
        const val MIN_SCALE = 0.0001
        const val STALE_AFTER_HOURS = 24.0 * 14.0
        const val HOUR_MS = 3_600_000L
    }
}
