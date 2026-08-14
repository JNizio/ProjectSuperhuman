package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyInvestigationV2Test {
    @Test
    fun rollingAlignmentUsesOnlyTheDeclaredPriorWindow() {
        val exposure = (0..5).map { day ->
            evidence(HealthDomain.EXERCISE, "exercise_minutes", day.toDouble(), day * DAY_MS + 8L * HOUR_MS)
        }
        val outcome = listOf(
            evidence(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 60.0, 5L * DAY_MS + 8L * HOUR_MS)
        )

        val pair = TrudyStatistics.alignRolling(exposure, outcome, rollingWindowMs = 3L * DAY_MS).single()

        assertEquals(4.0, pair.left.value)
        assertEquals(outcome.single().timestampEpochMs, pair.left.timestampEpochMs)
    }

    @Test
    fun sleepDeteriorationIsSupportedByAlignedTargetMetrics() = runTest {
        val source = FakeSource(sleepChange(worse = true))

        val result = investigate(source, sleepOperation(TrudyChangeClaim.WORSENED)).structuredResult

        assertEquals(TrudyPremiseStatus.PREMISE_SUPPORTED, result.premiseStatus)
        assertTrue(result.importantFindings.any { it.classification == TrudyFindingClassification.OBSERVED_CHANGE })
        assertTrue(result.importantFindings.any { it.metricId == "sleep_awake_minutes" })
    }

    @Test
    fun improvedRecordedSleepContradictsSubjectiveWorsePremise() = runTest {
        val source = FakeSource(sleepChange(worse = false))

        val result = investigate(
            source,
            sleepOperation(TrudyChangeClaim.WORSENED).copy(includesSubjectiveClaim = true)
        ).structuredResult

        assertEquals(TrudyPremiseStatus.PREMISE_NOT_SUPPORTED, result.premiseStatus)
        assertTrue(result.importantFindings.any { it.classification == TrudyFindingClassification.CONTRADICTORY_EVIDENCE })
        assertTrue(result.caveats.any { "subjective" in it.lowercase() && "wearable" in it.lowercase() })
    }

    @Test
    fun sleepAndStressRelationshipIsRankedAsPossibleNotCausal() = runTest {
        val values = sleepChange(worse = true).toMutableMap()
        values[key(HealthDomain.EMOTIONAL, "emotional_calmness")] = daily(
            HealthDomain.EMOTIONAL,
            "emotional_calmness",
            0..13
        ) { day -> 20.0 + day }
        values[key(HealthDomain.SLEEP, "sleep_score")] = daily(
            HealthDomain.SLEEP,
            "sleep_score",
            0..13
        ) { day -> 40.0 + day * 2.0 }
        val source = FakeSource(values)

        val result = investigate(source, sleepOperation(TrudyChangeClaim.CHANGED)).structuredResult
        val stress = result.relatedSignals.first { it.metricId == "emotional_calmness" }

        assertEquals(TrudyFindingClassification.POSSIBLE_ASSOCIATION, stress.classification)
        assertTrue((stress.coefficient ?: 0.0) > 0.9)
        assertTrue(result.caveats.any { "do not establish causation" in it })
    }

    @Test
    fun sleepEnvironmentRelationshipKeepsEnvironmentRelevantAndBounded() = runTest {
        val values = sleepChange(worse = true).toMutableMap()
        values[key(HealthDomain.ENVIRONMENT, "environment_temperature_c")] = daily(
            HealthDomain.ENVIRONMENT,
            "environment_temperature_c",
            0..13
        ) { day -> 15.0 + day }
        values[key(HealthDomain.SLEEP, "sleep_score")] = daily(
            HealthDomain.SLEEP,
            "sleep_score",
            0..13
        ) { day -> 90.0 - day * 2.0 }
        val source = FakeSource(values)

        val result = investigate(source, sleepOperation(TrudyChangeClaim.CHANGED)).structuredResult
        val environment = result.relatedSignals.first { it.metricId == "environment_temperature_c" }

        assertEquals(TrudyFindingClassification.POSSIBLE_ASSOCIATION, environment.classification)
        assertTrue(environment.relationshipScore > 0.0)
        assertTrue(result.execution.requestedMetricCount <= 13)
        assertTrue(source.limits.all { it <= 256 })
    }

    @Test
    fun exerciseAndHeartRateUsesHeartSpecificCandidateGraph() = runTest {
        val values = mutableMapOf<String, List<TrudyMetricEvidence>>()
        values[key(HealthDomain.EXERCISE, "heart_rate_avg_bpm")] = daily(
            HealthDomain.EXERCISE,
            "heart_rate_avg_bpm",
            0..13
        ) { day -> 65.0 + day * 1.5 }
        values[key(HealthDomain.EXERCISE, "heart_rate_max_bpm")] = daily(
            HealthDomain.EXERCISE,
            "heart_rate_max_bpm",
            0..13
        ) { day -> 120.0 + day }
        values[key(HealthDomain.EXERCISE, "exercise_minutes")] = daily(
            HealthDomain.EXERCISE,
            "exercise_minutes",
            0..13
        ) { day -> 10.0 + day * 3.0 }
        val source = FakeSource(values)
        val plan = TrudyInvestigationRelevanceGraph.plan(
            "What might explain my heart rate?",
            emptyList(),
            TrudyInvestigationIntent.WHAT_MIGHT_EXPLAIN
        )
        val operation = operation(plan, TrudyChangeClaim.CHANGED)

        val result = investigate(source, operation).structuredResult
        val exercise = result.relatedSignals.first { it.metricId == "exercise_minutes" }

        assertEquals("heart rate", result.target.label)
        assertEquals(TrudyFindingClassification.POSSIBLE_ASSOCIATION, exercise.classification)
        assertFalse(result.relatedSignals.any { it.domain == HealthDomain.NUTRITION })
    }

    @Test
    fun todayPriorityRanksFreshChangeAndNeverPromotesStaleBodyMetric() = runTest {
        val plan = TrudyInvestigationRelevanceGraph.plan(
            "What should I pay attention to today?",
            emptyList(),
            TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY
        )
        val values = mutableMapOf<String, List<TrudyMetricEvidence>>()
        values[key(HealthDomain.SLEEP, "sleep_score")] = todayComparisonRows(
            HealthDomain.SLEEP,
            "sleep_score",
            baseline = { day -> 80.0 + day % 2 },
            recent = { 52.0 }
        )
        values[key(HealthDomain.BODY, "body_weight_kg")] = todayComparisonRows(
            HealthDomain.BODY,
            "body_weight_kg",
            baseline = { day -> 75.0 + day / 20.0 },
            recent = { 95.0 }
        )
        val source = FakeSource(values, staleDomains = setOf(HealthDomain.BODY))
        val operation = operation(plan, TrudyChangeClaim.UNSPECIFIED).copy(
            intent = TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY,
            observationWindow = RECENT_ONE_DAY,
            baselineWindow = PRIOR_WEEK
        )

        val result = investigate(source, operation).structuredResult

        assertEquals(TrudyPremiseStatus.NOT_APPLICABLE, result.premiseStatus)
        assertEquals("sleep_score", result.importantFindings.first().metricId)
        assertFalse(result.importantFindings.any { it.metricId == "body_weight_kg" })
        assertTrue(result.missingEvidence.any { it.metricId == "body_weight_kg" && it.reason == TrudyEvidenceGapReason.STALE_DATA })
    }

    @Test
    fun missingSignalsStayInMissingEvidenceRatherThanImportantFindings() = runTest {
        val source = FakeSource(sleepChange(worse = true))

        val result = investigate(source, sleepOperation(TrudyChangeClaim.WORSENED)).structuredResult

        assertTrue(result.missingEvidence.any { it.metricId == "food_caffeine_mg" })
        assertFalse(result.importantFindings.any { finding ->
            result.missingEvidence.any { it.metricId == finding.metricId }
        })
        assertTrue(result.missingEvidence.all { it.excludedFromPriority })
    }

    @Test
    fun eveningCaffeineAlignsToFollowingSleepInsteadOfCalendarDay() = runTest {
        val values = sleepChange(worse = true).toMutableMap()
        values[key(HealthDomain.NUTRITION, "food_caffeine_mg")] = (0..12).map { day ->
            evidence(
                HealthDomain.NUTRITION,
                "food_caffeine_mg",
                20.0 + day * 15.0,
                day * DAY_MS + 20L * HOUR_MS,
                source = "manual food diary"
            )
        }
        values[key(HealthDomain.SLEEP, "sleep_score")] = (0..12).map { day ->
            evidence(
                HealthDomain.SLEEP,
                "sleep_score",
                95.0 - day * 3.0,
                (day + 1L) * DAY_MS + 8L * HOUR_MS,
                source = "Health Connect wearable"
            )
        }
        val source = FakeSource(values)

        val result = investigate(source, sleepOperation(TrudyChangeClaim.CHANGED)).structuredResult
        val caffeine = result.relatedSignals.first { it.metricId == "food_caffeine_mg" }

        assertEquals(TrudyTemporalAlignmentKind.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP, caffeine.temporalAlignment.kind)
        assertEquals(13, caffeine.sampleCount)
        assertTrue((caffeine.coefficient ?: 0.0) < -0.9)
        assertEquals(TrudyFindingClassification.POSSIBLE_ASSOCIATION, caffeine.classification)
    }

    @Test
    fun plannerUsesBoundedSleepDomainsAndAddsActiveExperimentContextForToday() {
        val boundaries = object : TrudyTemporalBoundaryProvider {
            override fun nowEpochMs() = 30L * DAY_MS + 12L * HOUR_MS
            override fun startOfTodayEpochMs() = 30L * DAY_MS
            override fun startOfWeekEpochMs() = 28L * DAY_MS
            override fun startOfMonthEpochMs(monthsAgo: Int) = (30L - monthsAgo * 30L) * DAY_MS
        }
        val planner = TrudySystemInvestigationPlanner(TrudyTemporalResolver(boundaries))

        val sleep = planner.plan(TrudyAskRequest("Why has my sleep suffered in the last few weeks?"))
            .filterIsInstance<InvestigateChange>().single()
        val today = planner.plan(TrudyAskRequest("What should I pay attention to today?"))

        assertTrue(sleep.related.size <= 8)
        assertTrue(sleep.related.any { it.metricId == "food_caffeine_mg" })
        assertFalse(sleep.related.any { it.metricId == "body_weight_kg" })
        assertTrue(today.any { it is InvestigateChange && it.intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY })
        assertTrue(today.any { it is GetCanonicalExperiments })
    }

    private suspend fun investigate(
        source: FakeSource,
        operation: InvestigateChange
    ): TrudyChangeInvestigation = TrudyCrossDomainInvestigator(
        TrudyPersonalEvidenceLibrary(source) { RECENT_END },
        source
    ).investigate(operation)

    private fun sleepOperation(claim: TrudyChangeClaim): InvestigateChange = operation(
        TrudyInvestigationRelevanceGraph.plan(
            "Why has my sleep suffered?",
            emptyList(),
            TrudyInvestigationIntent.WHY
        ),
        claim
    ).copy(includesSubjectiveClaim = true)

    private fun operation(
        plan: TrudyInvestigationRelevancePlan,
        claim: TrudyChangeClaim
    ) = InvestigateChange(
        targets = plan.targets,
        related = plan.related,
        observationWindow = RECENT_WEEK,
        baselineWindow = BASELINE_WEEK,
        claim = claim,
        intent = TrudyInvestigationIntent.WHY,
        targetLabel = plan.targetLabel,
        timeframeLabel = "the last week",
        timeframeExplicit = true
    )

    private fun sleepChange(worse: Boolean): MutableMap<String, List<TrudyMetricEvidence>> {
        val values = mutableMapOf<String, List<TrudyMetricEvidence>>()
        values[key(HealthDomain.SLEEP, "sleep_score")] = comparisonRows(
            HealthDomain.SLEEP,
            "sleep_score",
            baseline = { day -> 79.0 + day % 3 },
            recent = { day -> if (worse) 61.0 + day % 3 else 91.0 + day % 3 }
        )
        values[key(HealthDomain.SLEEP, "sleep_awake_minutes")] = comparisonRows(
            HealthDomain.SLEEP,
            "sleep_awake_minutes",
            baseline = { day -> 27.0 + day % 3 },
            recent = { day -> if (worse) 51.0 + day % 3 else 15.0 + day % 3 }
        )
        values[key(HealthDomain.SLEEP, "sleep_continuity_score")] = comparisonRows(
            HealthDomain.SLEEP,
            "sleep_continuity_score",
            baseline = { day -> 78.0 + day % 3 },
            recent = { day -> if (worse) 58.0 + day % 3 else 92.0 + day % 3 }
        )
        values[key(HealthDomain.SLEEP, "sleep_deep_minutes")] = comparisonRows(
            HealthDomain.SLEEP,
            "sleep_deep_minutes",
            baseline = { day -> 56.0 + day % 3 },
            recent = { day -> if (worse) 39.0 + day % 3 else 72.0 + day % 3 }
        )
        values[key(HealthDomain.SLEEP, "sleep_total_minutes")] = comparisonRows(
            HealthDomain.SLEEP,
            "sleep_total_minutes",
            baseline = { day -> 435.0 + day % 3 },
            recent = { day -> if (worse) 365.0 + day % 3 else 475.0 + day % 3 }
        )
        return values
    }

    private fun comparisonRows(
        domain: HealthDomain,
        metric: String,
        baseline: (Int) -> Double,
        recent: (Int) -> Double
    ) = (0..6).map { day -> evidence(domain, metric, baseline(day), day * DAY_MS + 8L * HOUR_MS) } +
        (7..13).map { day -> evidence(domain, metric, recent(day), day * DAY_MS + 8L * HOUR_MS) }

    private fun daily(
        domain: HealthDomain,
        metric: String,
        days: IntRange,
        value: (Int) -> Double
    ) = days.map { day -> evidence(domain, metric, value(day), day * DAY_MS + 8L * HOUR_MS) }

    private fun todayComparisonRows(
        domain: HealthDomain,
        metric: String,
        baseline: (Int) -> Double,
        recent: (Int) -> Double
    ) = (21..27).map { day -> evidence(domain, metric, baseline(day), day * DAY_MS + 8L * HOUR_MS) } +
        evidence(domain, metric, recent(28), 28L * DAY_MS + 8L * HOUR_MS)

    private fun evidence(
        domain: HealthDomain,
        metric: String,
        value: Double,
        timestamp: Long,
        source: String = "Health Connect wearable"
    ) = TrudyMetricEvidence(domain, metric, value, "unit", timestamp, source = source)

    private class FakeSource(
        private val values: Map<String, List<TrudyMetricEvidence>>,
        private val staleDomains: Set<HealthDomain> = emptySet()
    ) : TrudyPersonalEvidenceSource {
        val limits = mutableListOf<Int>()

        override suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int): List<TrudyMetricEvidence> {
            limits += limit
            return values[key(domain, metricId)].orEmpty().takeLast(limit)
        }

        override suspend fun metricWindow(
            domain: HealthDomain,
            metricId: String,
            range: TrudyTimeRange,
            limit: Int
        ): List<TrudyMetricEvidence> {
            limits += limit
            return values[key(domain, metricId)].orEmpty()
                .filter { it.timestampEpochMs in range.fromEpochMs..range.toEpochMs }
                .takeLast(limit)
        }

        override suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence {
            val rows = values.filterKeys { it.startsWith("${domain.name}/") }.values.flatten()
            return TrudyDataQualityEvidence(
                domain = domain,
                score = if (domain in staleDomains) 35 else 95,
                recordCount = rows.size.toLong(),
                distinctMetricCount = values.keys.count { it.startsWith("${domain.name}/") },
                latestTimestampEpochMs = rows.maxOfOrNull { it.timestampEpochMs },
                ageHours = if (domain in staleDomains) 1_000.0 else 0.0,
                isStale = domain in staleDomains,
                notes = emptyList()
            )
        }
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 86_400_000L
        private const val RECENT_END = 14L * DAY_MS - 1L
        private val BASELINE_WEEK = TrudyTimeRange(0L, 7L * DAY_MS - 1L)
        private val RECENT_WEEK = TrudyTimeRange(7L * DAY_MS, RECENT_END)
        private val PRIOR_WEEK = TrudyTimeRange(21L * DAY_MS, 28L * DAY_MS - 1L)
        private val RECENT_ONE_DAY = TrudyTimeRange(28L * DAY_MS, 29L * DAY_MS - 1L)
        private fun key(domain: HealthDomain, metric: String) = "${domain.name}/$metric"
    }
}
