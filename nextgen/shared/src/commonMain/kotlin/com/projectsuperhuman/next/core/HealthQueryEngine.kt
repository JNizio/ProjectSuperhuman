package com.projectsuperhuman.next.core

import kotlin.math.sqrt

/** A bounded request for one canonical health metric. */
data class MetricWindow(
    val domain: HealthDomain,
    val metric: String,
    val fromEpochMs: Long,
    val toEpochMs: Long
) {
    init {
        require(fromEpochMs <= toEpochMs) { "fromEpochMs must be <= toEpochMs" }
    }
}

data class MetricSummary(
    val domain: HealthDomain,
    val metric: String,
    val fromEpochMs: Long,
    val toEpochMs: Long,
    val count: Int,
    val min: Double?,
    val max: Double?,
    val average: Double?,
    val sum: Double?,
    val first: Double?,
    val last: Double?,
    val unit: String?
)

data class PeriodComparison(
    val previous: MetricSummary,
    val current: MetricSummary,
    val absoluteChange: Double?,
    val percentChange: Double?
)

data class TrendResult(
    val summary: MetricSummary,
    val slopePerDay: Double?,
    val direction: TrendDirection
)

enum class TrendDirection { RISING, FALLING, STABLE, INSUFFICIENT_DATA }

data class AlignedPoint(
    val timestampEpochMs: Long,
    val left: Double,
    val right: Double
)

data class CorrelationResult(
    val sampleCount: Int,
    val pearsonR: Double?,
    val strength: CorrelationStrength
)

enum class CorrelationStrength { STRONG, MODERATE, WEAK, NONE, INSUFFICIENT_DATA }

/**
 * Read-only query/aggregation facade for the Interpretation Engine.
 *
 * Interpretation asks health questions through this class rather than reading SQL or
 * whole-domain archives. Every request is explicitly time-bounded. Cross-metric work is
 * aligned in time before statistics are calculated, which prevents accidental comparison
 * of unrelated observations.
 *
 * Step 4 deliberately keeps this engine storage-agnostic. The backing port may later serve
 * long windows from daily/weekly aggregate tables without changing Interpretation callers.
 */
class HealthQueryEngine(
    private val data: InterpretationDataPort,
    private val registry: MetricRegistry = CoreMetricRegistry,
    private val maxRawWindowMs: Long = DEFAULT_MAX_RAW_WINDOW_MS
) {
    suspend fun summary(window: MetricWindow): MetricSummary {
        val values = read(window)
        if (values.isEmpty()) return emptySummary(window)
        return MetricSummary(
            domain = window.domain,
            metric = canonicalMetric(window),
            fromEpochMs = window.fromEpochMs,
            toEpochMs = window.toEpochMs,
            count = values.size,
            min = values.minOf { it.value },
            max = values.maxOf { it.value },
            average = values.map { it.value }.average(),
            sum = values.sumOf { it.value },
            first = values.first().value,
            last = values.last().value,
            unit = values.last().unit
        )
    }

    suspend fun baseline(window: MetricWindow): MetricSummary = summary(window)

    suspend fun trend(window: MetricWindow): TrendResult {
        val values = read(window)
        val summary = if (values.isEmpty()) emptySummary(window) else summaryFromValues(window, values)
        if (values.size < 2) return TrendResult(summary, null, TrendDirection.INSUFFICIENT_DATA)

        val start = values.first().timestampEpochMs.toDouble()
        val xs = values.map { (it.timestampEpochMs - start) / DAY_MS.toDouble() }
        val ys = values.map { it.value }
        val slope = linearSlope(xs, ys)
        val scale = summary.average?.let { kotlin.math.abs(it) }?.coerceAtLeast(1e-9) ?: 1.0
        val relativeDailySlope = kotlin.math.abs(slope) / scale
        val direction = when {
            relativeDailySlope < STABLE_RELATIVE_CHANGE_PER_DAY -> TrendDirection.STABLE
            slope > 0.0 -> TrendDirection.RISING
            else -> TrendDirection.FALLING
        }
        return TrendResult(summary, slope, direction)
    }

    suspend fun comparePeriods(previous: MetricWindow, current: MetricWindow): PeriodComparison {
        require(previous.domain == current.domain && previous.metric == current.metric) {
            "Period comparison requires the same domain and metric"
        }
        val old = summary(previous)
        val now = summary(current)
        val change = if (old.average != null && now.average != null) now.average - old.average else null
        val percent = if (change != null && old.average != null && old.average != 0.0) {
            change / old.average * 100.0
        } else null
        return PeriodComparison(old, now, change, percent)
    }

    /**
     * Align two series by nearest timestamp within [toleranceMs]. This is the safe input
     * for later correlation/causal-hypothesis work; unaligned rows are never paired.
     */
    suspend fun alignedSeries(
        left: MetricWindow,
        right: MetricWindow,
        toleranceMs: Long = DEFAULT_ALIGNMENT_TOLERANCE_MS
    ): List<AlignedPoint> {
        require(toleranceMs >= 0L)
        val leftValues = read(left)
        val rightValues = read(right)
        if (leftValues.isEmpty() || rightValues.isEmpty()) return emptyList()

        val result = ArrayList<AlignedPoint>(minOf(leftValues.size, rightValues.size))
        var r = 0
        leftValues.forEach { l ->
            while (r + 1 < rightValues.size &&
                kotlin.math.abs(rightValues[r + 1].timestampEpochMs - l.timestampEpochMs) <=
                kotlin.math.abs(rightValues[r].timestampEpochMs - l.timestampEpochMs)
            ) r++

            val candidate = rightValues[r]
            if (kotlin.math.abs(candidate.timestampEpochMs - l.timestampEpochMs) <= toleranceMs) {
                result += AlignedPoint(l.timestampEpochMs, l.value, candidate.value)
            }
        }
        return result
    }

    suspend fun correlation(
        left: MetricWindow,
        right: MetricWindow,
        toleranceMs: Long = DEFAULT_ALIGNMENT_TOLERANCE_MS
    ): CorrelationResult {
        val aligned = alignedSeries(left, right, toleranceMs)
        if (aligned.size < MIN_CORRELATION_SAMPLES) {
            return CorrelationResult(aligned.size, null, CorrelationStrength.INSUFFICIENT_DATA)
        }
        val r = pearson(aligned.map { it.left }, aligned.map { it.right })
        val strength = when {
            r == null -> CorrelationStrength.NONE
            kotlin.math.abs(r) >= 0.7 -> CorrelationStrength.STRONG
            kotlin.math.abs(r) >= 0.4 -> CorrelationStrength.MODERATE
            kotlin.math.abs(r) >= 0.2 -> CorrelationStrength.WEAK
            else -> CorrelationStrength.NONE
        }
        return CorrelationResult(aligned.size, r, strength)
    }

    suspend fun recentChange(
        domain: HealthDomain,
        metric: String,
        nowEpochMs: Long,
        recentDurationMs: Long,
        baselineDurationMs: Long
    ): PeriodComparison {
        require(recentDurationMs > 0 && baselineDurationMs > 0)
        val recentStart = nowEpochMs - recentDurationMs
        val baselineStart = recentStart - baselineDurationMs
        return comparePeriods(
            MetricWindow(domain, metric, baselineStart, recentStart - 1L),
            MetricWindow(domain, metric, recentStart, nowEpochMs)
        )
    }

    private suspend fun read(window: MetricWindow): List<HealthValue> {
        require(window.toEpochMs - window.fromEpochMs <= maxRawWindowMs) {
            "Raw query window is too large; use aggregate-backed query path"
        }
        val metric = canonicalMetric(window)
        return data.between(window.domain, metric, window.fromEpochMs, window.toEpochMs)
            .sortedBy { it.timestampEpochMs }
    }

    private fun canonicalMetric(window: MetricWindow): String =
        registry.definition(window.domain, window.metric)?.id ?: window.metric.trim()

    private fun summaryFromValues(window: MetricWindow, values: List<HealthValue>): MetricSummary =
        MetricSummary(
            domain = window.domain,
            metric = canonicalMetric(window),
            fromEpochMs = window.fromEpochMs,
            toEpochMs = window.toEpochMs,
            count = values.size,
            min = values.minOf { it.value },
            max = values.maxOf { it.value },
            average = values.map { it.value }.average(),
            sum = values.sumOf { it.value },
            first = values.first().value,
            last = values.last().value,
            unit = values.last().unit
        )

    private fun emptySummary(window: MetricWindow) = MetricSummary(
        window.domain, canonicalMetric(window), window.fromEpochMs, window.toEpochMs,
        0, null, null, null, null, null, null, null
    )

    private fun linearSlope(xs: List<Double>, ys: List<Double>): Double {
        val meanX = xs.average()
        val meanY = ys.average()
        var numerator = 0.0
        var denominator = 0.0
        xs.indices.forEach { i ->
            val dx = xs[i] - meanX
            numerator += dx * (ys[i] - meanY)
            denominator += dx * dx
        }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    private fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 2) return null
        val meanX = xs.average()
        val meanY = ys.average()
        var covariance = 0.0
        var varianceX = 0.0
        var varianceY = 0.0
        xs.indices.forEach { i ->
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            covariance += dx * dy
            varianceX += dx * dx
            varianceY += dy * dy
        }
        val denominator = sqrt(varianceX * varianceY)
        return if (denominator == 0.0) null else (covariance / denominator).coerceIn(-1.0, 1.0)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val DEFAULT_MAX_RAW_WINDOW_MS = 120L * DAY_MS
        const val DEFAULT_ALIGNMENT_TOLERANCE_MS = 12L * 60L * 60L * 1000L
        const val MIN_CORRELATION_SAMPLES = 5
        const val STABLE_RELATIVE_CHANGE_PER_DAY = 0.001
    }
}
