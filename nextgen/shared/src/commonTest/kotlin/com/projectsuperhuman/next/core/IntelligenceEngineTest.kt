package com.projectsuperhuman.next.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntelligenceEngineTest {
    private val day = 86_400_000L

    @Test
    fun strongRelationshipRemainsAssociationNotCausation() = runBlocking {
        val start = 10_000L
        val values = buildList {
            repeat(8) { i ->
                add(hv(HealthDomain.SLEEP, "sleep_score", 50.0 + i * 5.0, start + i * day))
                add(hv(HealthDomain.MINDFULNESS, "anxiety_score", 90.0 - i * 7.0, start + 500L + i * day))
            }
        }
        val engine = InterpretationEngine(
            HealthQueryEngine(FakeStep5Port(values)),
            nowEpochMs = { start + 9 * day }
        )
        val finding = engine.relationship(
            MetricWindow(HealthDomain.SLEEP, "sleep_score", start, start + 7 * day),
            MetricWindow(HealthDomain.MINDFULNESS, "anxiety_score", start, start + 7 * day + 1_000L),
            toleranceMs = 1_000L
        )

        assertEquals(FindingKind.RELATIONSHIP, finding.kind)
        assertEquals(FindingDirection.NEGATIVE, finding.direction)
        assertTrue(finding.confidence < 0.9)
        assertTrue(finding.explanation.contains("associated"))
        assertTrue(finding.caveats.any { it.contains("does not establish causation") })
    }

    @Test
    fun interventionWithTooLittleDataDoesNotClaimEffect() = runBlocking {
        val start = 20 * day
        val values = listOf(
            hv(HealthDomain.SLEEP, "sleep_score", 60.0, start - day),
            hv(HealthDomain.SLEEP, "sleep_score", 80.0, start + day)
        )
        val evaluation = InterventionEngine(
            HealthQueryEngine(FakeStep5Port(values)),
            nowEpochMs = { start + 3 * day }
        ).evaluate(
            InterventionDefinition(
                id = "earlier-bedtime",
                name = "Earlier bedtime",
                startedEpochMs = start,
                outcomeDomain = HealthDomain.SLEEP,
                outcomeMetric = "sleep_score",
                baselineDurationMs = 7 * day,
                observationDurationMs = 7 * day,
                expectedDirection = OutcomeDirection.HIGHER_IS_BETTER
            )
        )

        assertEquals(InterventionStatus.INSUFFICIENT_DATA, evaluation.status)
        assertEquals(FindingDirection.UNKNOWN, evaluation.finding.direction)
        assertTrue(evaluation.confidence <= 0.1)
    }

    @Test
    fun interventionCanDescribeObservedImprovementWithoutClaimingCause() = runBlocking {
        val start = 30 * day
        val values = buildList {
            repeat(5) { i ->
                add(hv(HealthDomain.SLEEP, "sleep_score", 60.0 + i, start - (5 - i) * day))
                add(hv(HealthDomain.SLEEP, "sleep_score", 78.0 + i, start + i * day))
            }
        }
        val evaluation = InterventionEngine(
            HealthQueryEngine(FakeStep5Port(values)),
            nowEpochMs = { start + 5 * day }
        ).evaluate(
            InterventionDefinition(
                id = "routine",
                name = "Evening routine",
                startedEpochMs = start,
                outcomeDomain = HealthDomain.SLEEP,
                outcomeMetric = "sleep_score",
                baselineDurationMs = 6 * day,
                observationDurationMs = 6 * day,
                expectedDirection = OutcomeDirection.HIGHER_IS_BETTER
            )
        )

        assertEquals(InterventionStatus.IMPROVED, evaluation.status)
        assertTrue(evaluation.finding.explanation.contains("recorded outcome"))
        assertTrue(evaluation.finding.caveats.any { it.contains("cannot prove") })
    }

    @Test
    fun insightsEngineSuppressesWeakUnknownFinding() {
        val weak = InterpretationFinding(
            id = "weak",
            kind = FindingKind.RELATIONSHIP,
            title = "Weak",
            explanation = "Not enough evidence",
            direction = FindingDirection.UNKNOWN,
            confidence = 0.1,
            evidence = listOf(EvidenceWindow(HealthDomain.SLEEP, "sleep_score", 0, day, 2, "test")),
            caveats = emptyList(),
            generatedEpochMs = day
        )
        val strong = weak.copy(
            id = "strong",
            kind = FindingKind.TREND,
            direction = FindingDirection.RISING,
            confidence = 0.8,
            evidence = listOf(EvidenceWindow(HealthDomain.SLEEP, "sleep_score", 0, 20 * day, 20, "test"))
        )

        val ranked = InsightsEngine().rank(listOf(weak, strong))
        val weakCard = ranked.first { it.finding.id == "weak" }
        val strongCard = ranked.first { it.finding.id == "strong" }

        assertFalse(weakCard.shouldSurface)
        assertTrue(strongCard.shouldSurface)
        assertTrue(strongCard.priority > weakCard.priority)
    }

    private fun hv(domain: HealthDomain, metric: String, value: Double, ts: Long) = HealthValue(
        domain = domain,
        metric = metric,
        value = value,
        unit = if (metric.contains("score")) "score" else "unit",
        timestampEpochMs = ts,
        source = "step5-test"
    )
}

private class FakeStep5Port(
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

    override suspend fun dailyAggregates(
        domain: HealthDomain,
        metric: String,
        fromDayEpoch: Long,
        toDayEpoch: Long
    ): List<DailyAggregatePoint> = values
        .filter { it.domain == domain && it.metric == metric }
        .groupBy { it.timestampEpochMs.floorDiv(86_400_000L) }
        .filterKeys { it in fromDayEpoch..toDayEpoch }
        .map { (dayEpoch, rows) ->
            val sorted = rows.sortedBy { it.timestampEpochMs }
            DailyAggregatePoint(
                dayEpoch = dayEpoch,
                domain = domain,
                metric = metric,
                count = rows.size.toLong(),
                min = rows.minOf { it.value },
                max = rows.maxOf { it.value },
                average = rows.map { it.value }.average(),
                sum = rows.sumOf { it.value },
                first = sorted.first().value,
                last = sorted.last().value
            )
        }.sortedBy { it.dayEpoch }
}
