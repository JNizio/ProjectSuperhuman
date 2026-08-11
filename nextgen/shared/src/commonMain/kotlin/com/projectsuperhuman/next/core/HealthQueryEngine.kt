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
    val count: Long,
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
 * Short windows use indexed raw observations. Long windows automatically switch to
 * incrementally maintained daily aggregates. Interpretation never needs to know which
 * storage path answered the question, and never receives unrestricted SQL access.
 */
class HealthQueryEngine(
    private val data: InterpretationDataPort,
    private val registry: MetricRegistry = CoreMetricRegistry,
    private val maxRawWindowMs: Long = DEFAULT_MAX_RAW_WINDOW_MS
) {
    suspend fun summary(window: MetricWindow): MetricSummary =
        if (isRawWindow(window)) {
            val values = readRaw(window)
            if (values.isEmpty()) emptySummary(window) else summaryFromValues(window, values)
        } else {
            summaryFromAggregates(window, readAggregates(window))
        }

    suspend fun baseline(window: MetricWindow): MetricSummary = summary(window)

    suspend fun trend(window: MetricWindow): TrendResult {
        val summary = summary(window)
        val points: List<Pair<Double, Double>> = if (isRawWindow(window)) {
            val values = readRaw(window)
            if (values.isEmpty()) emptyList() else {
                val start = values.first().timestampEpochMs
                values.map { ((it.timestampEpochMs - start) / DAY_MS.toDouble()) to it.value }
            }
        } else {
            val aggregates = readAggregates(window)
            if (aggregates.isEmpty()) emptyList() else {
                val startDay = aggregates.first().dayEpoch
                aggregates.mapNotNull { row ->
                    row.average?.let { ((row.dayEpoch - startDay).toDouble()) to it }
                }
            }
        }

        if (points.size < 2) return TrendResult(summary, null, TrendDirection.INSUFFICIENT_DATA)
        val slope = linearSlope(points.map { it.first }, points.map { it.second })
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
     * Align two raw series by nearest timestamp within [toleranceMs]. Correlation remains a
     * bounded high-resolution operation; callers wanting multi-year relationships should
     * compare aggregate series in a future interpretation stage rather than raw millions.
     */
    suspend fun alignedSeries(
        left: MetricWindow,
        right: MetricWindow,
        toleranceMs: Long = DEFAULT_ALIGNMENT_TOLERANCE_MS
    ): List<AlignedPoint> {
        require(toleranceMs >= 0L)
        require(isRawWindow(left) && isRawWindow(right)) {
            "Aligned raw series are limited to the raw query window"
        }
        val leftValues = readRaw(left)
        val rightValues = readRaw(right)
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

    private fun isRawWindow(window: MetricWindow): Boolean =
        window.toEpochMs - window.fromEpochMs <= maxRawWindowMs

    private suspend fun readRaw(window: MetricWindow): List<HealthValue> {
        require(isRawWindow(window)) { "Raw query window is too large" }
        return data.between(
            window.domain,
            canonicalMetric(window),
            window.fromEpochMs,
            window.toEpochMs
        ).sortedBy { it.timestampEpochMs }
    }

    private suspend fun readAggregates(window: MetricWindow): List<DailyAggregatePoint> =
        data.dailyAggregates(
            window.domain,
            canonicalMetric(window),
            window.fromEpochMs.floorDiv(DAY_MS),
            window.toEpochMs.floorDiv(DAY_MS)
        ).sortedBy { it.dayEpoch }

    private fun canonicalMetric(window: MetricWindow): String =
        registry.definition(window.domain, window.metric)?.id ?: window.metric.trim()

    private fun summaryFromValues(window: MetricWindow, values: List<HealthValue>): MetricSummary =
        MetricSummary(
            domain = window.domain,
            metric = canonicalMetric(window),
            fromEpochMs = window.fromEpochMs,
            toEpochMs = window.toEpochMs,
            count = values.size.toLong(),
            min = values.minOf { it.value },
            max = values.maxOf { it.value },
            average = values.map { it.value }.average(),
            sum = values.sumOf { it.value },
            first = values.first().value,
            last = values.last().value,
            unit = values.last().unit
        )

    private fun summaryFromAggregates(
        window: MetricWindow,
        rows: List<DailyAggregatePoint>
    ): MetricSummary {
        if (rows.isEmpty()) return emptySummary(window)
        val count = rows.sumOf { it.count }
        val sum = rows.mapNotNull { it.sum }.takeIf { it.isNotEmpty() }?.sum()
        val weightedAverage = if (count > 0L && sum != null) sum / count else null
        return MetricSummary(
            domain = window.domain,
            metric = canonicalMetric(window),
            fromEpochMs = window.fromEpochMs,
            toEpochMs = window.toEpochMs,
            count = count,
            min = rows.mapNotNull { it.min }.minOrNull(),
            max = rows.mapNotNull { it.max }.maxOrNull(),
            average = weightedAverage,
            sum = sum,
            first = rows.firstNotNullOfOrNull { it.first },
            last = rows.asReversed().firstNotNullOfOrNull { it.last },
            unit = registry.definition(window.domain, window.metric)?.canonicalUnit
        )
    }

    private fun emptySummary(window: MetricWindow) = MetricSummary(
        window.domain, canonicalMetric(window), window.fromEpochMs, window.toEpochMs,
        0L, null, null, null, null, null, null,
        registry.definition(window.domain, window.metric)?.canonicalUnit
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
