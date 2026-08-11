package com.projectsuperhuman.next.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HealthQueryEngineTest {
    private val day = 86_400_000L

    @Test
    fun summaryAndTrendUseBoundedMetricRows() = runTest {
        val values = (0..4).map { i ->
            HealthValue(
                domain = HealthDomain.SLEEP,
                metric = "sleep_total_minutes",
                value = 400.0 + i * 20.0,
                unit = "min",
                timestampEpochMs = 1_000L + i * day,
                source = "test"
            )
        }
        val engine = HealthQueryEngine(FakeInterpretationPort(values))
        val window = MetricWindow(HealthDomain.SLEEP, "sleep_total_minutes", 1_000L, 1_000L + 4 * day)

        val summary = engine.summary(window)
        val trend = engine.trend(window)

        assertEquals(5, summary.count)
        assertEquals(440.0, summary.average)
        assertEquals(400.0, summary.first)
        assertEquals(480.0, summary.last)
        assertEquals(TrendDirection.RISING, trend.direction)
        assertNotNull(trend.slopePerDay)
        assertTrue(trend.slopePerDay!! > 0.0)
    }

    @Test
    fun comparesAdjacentPeriods() = runTest {
        val values = listOf(
            hv(HealthDomain.SLEEP, "sleep_score", 60.0, 1_000L),
            hv(HealthDomain.SLEEP, "sleep_score", 70.0, 2_000L),
            hv(HealthDomain.SLEEP, "sleep_score", 80.0, 3_000L),
            hv(HealthDomain.SLEEP, "sleep_score", 90.0, 4_000L)
        )
        val engine = HealthQueryEngine(FakeInterpretationPort(values))
        val result = engine.comparePeriods(
            MetricWindow(HealthDomain.SLEEP, "sleep_score", 1_000L, 2_000L),
            MetricWindow(HealthDomain.SLEEP, "sleep_score", 3_000L, 4_000L)
        )

        assertEquals(65.0, result.previous.average)
        assertEquals(85.0, result.current.average)
        assertEquals(20.0, result.absoluteChange)
        assertTrue((result.percentChange ?: 0.0) > 30.0)
    }

    @Test
    fun alignsNearbySeriesAndCalculatesCorrelation() = runTest {
        val values = buildList {
            repeat(6) { i ->
                add(hv(HealthDomain.SLEEP, "sleep_score", 50.0 + i * 5, 10_000L + i * day))
                add(hv(HealthDomain.MINDFULNESS, "anxiety_score", 80.0 - i * 6, 10_500L + i * day))
            }
        }
        val engine = HealthQueryEngine(FakeInterpretationPort(values))
        val sleep = MetricWindow(HealthDomain.SLEEP, "sleep_score", 10_000L, 10_000L + 5 * day)
        val anxiety = MetricWindow(HealthDomain.MINDFULNESS, "anxiety_score", 10_000L, 11_000L + 5 * day)

        val aligned = engine.alignedSeries(sleep, anxiety, toleranceMs = 1_000L)
        val correlation = engine.correlation(sleep, anxiety, toleranceMs = 1_000L)

        assertEquals(6, aligned.size)
        assertEquals(6, correlation.sampleCount)
        assertNotNull(correlation.pearsonR)
        assertTrue(correlation.pearsonR!! < -0.99)
        assertEquals(CorrelationStrength.STRONG, correlation.strength)
    }

    @Test
    fun refusesUnboundedRawHistory() = runTest {
        val engine = HealthQueryEngine(FakeInterpretationPort(emptyList()))
        assertFailsWith<IllegalArgumentException> {
            engine.summary(
                MetricWindow(
                    HealthDomain.SLEEP,
                    "sleep_score",
                    0L,
                    121L * day
                )
            )
        }
    }

    private fun hv(domain: HealthDomain, metric: String, value: Double, ts: Long) = HealthValue(
        domain = domain,
        metric = metric,
        value = value,
        unit = if (metric.contains("score")) "score" else "unit",
        timestampEpochMs = ts,
        source = "test"
    )
}

private class FakeInterpretationPort(
    private val values: List<HealthValue>
) : InterpretationDataPort {
    override suspend fun latest(domain: HealthDomain, metric: String): HealthValue? =
        values.filter { it.domain == domain && it.metric == metric }.maxByOrNull { it.timestampEpochMs }

    override suspend fun between(
        domain: HealthDomain,
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = values.filter {
        it.domain == domain && it.metric == metric && it.timestampEpochMs in fromEpochMs..toEpochMs
    }.sortedBy { it.timestampEpochMs }

    override suspend fun domainBetween(
        domain: HealthDomain,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = values.filter {
        it.domain == domain && it.timestampEpochMs in fromEpochMs..toEpochMs
    }.sortedBy { it.timestampEpochMs }
}
