package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrudyAnswerEngineTest {
    private val now = 100L * DAY_MS
    private val engine = TrudyAnswerEngine { now }
    private val boundaries = object : TrudyTemporalBoundaryProvider {
        override fun nowEpochMs() = now
        override fun startOfTodayEpochMs() = now
        override fun startOfWeekEpochMs() = 96L * DAY_MS
        override fun startOfMonthEpochMs(monthsAgo: Int) = (90L - monthsAgo * 30L) * DAY_MS
    }

    @Test
    fun sleepLastWeekWithTwoNightsExplainsTheActualLimitation() {
        val result = BaselineComparisonResult(
            CompareBaseline(HealthDomain.SLEEP, "sleep_score", recent, baseline),
            comparison("sleep_score", 74.0, 73.0, recentCount = 2, baselineCount = 2)
        )

        val answer = answer("How's my sleep doing the last week or so?", result)

        assertTrue(answer.startsWith("I only have two recorded nights"))
        assertTrue("too little to judge the trend" in answer)
        assertNoEngineeringLanguage(answer)
    }

    @Test
    fun todayPriorityNeverRanksNoDataOrStaleVisceralFatAsAHealthFinding() {
        val noData = insight(
            id = "empty",
            domain = HealthDomain.BODY,
            title = "No data yet",
            explanation = "No observations are available.",
            quality = quality(HealthDomain.BODY, records = 0, ageHours = null, stale = false)
        )
        val stale = insight(
            id = "stale-visceral",
            domain = HealthDomain.BODY,
            title = "Visceral fat changed",
            explanation = "Visceral fat is higher.",
            quality = quality(HealthDomain.BODY, records = 8, ageHours = 24.0 * 21.0, stale = true)
        )
        val fresh = insight(
            id = "fresh-heart-rate",
            domain = HealthDomain.EXERCISE,
            title = "Resting heart rate is higher",
            explanation = "Your resting heart rate is above its recent comparison level.",
            quality = quality(HealthDomain.EXERCISE, records = 10, ageHours = 1.0, stale = false)
        )

        val answer = answer(
            "What should I pay attention to in my health data today?",
            TrudyToolResult.Insights(TrudyToolOperation.GetInsights(HealthDomain.BODY), listOf(noData, stale)),
            TrudyToolResult.Insights(TrudyToolOperation.GetInsights(HealthDomain.EXERCISE), listOf(fresh))
        )

        assertTrue(answer.startsWith("The main thing worth paying attention to today"))
        assertTrue("resting heart rate" in answer.lowercase())
        assertFalse("No data yet" in answer)
        assertFalse("visceral fat" in answer.lowercase())
    }

    @Test
    fun missingBloodPressureYesterdayNeverInventsAReading() {
        val result = TrudyToolResult.MetricWindow(
            TrudyToolOperation.GetMetricWindow(
                HealthDomain.BLOOD_PRESSURE,
                "blood_pressure_systolic_mmhg",
                recent
            ),
            emptyList()
        )
        val diastolic = TrudyToolResult.MetricWindow(
            TrudyToolOperation.GetMetricWindow(
                HealthDomain.BLOOD_PRESSURE,
                "blood_pressure_diastolic_mmhg",
                recent
            ),
            emptyList()
        )

        val answer = answer("What was my blood pressure yesterday?", result, diastolic)

        assertEquals("I don't have a blood-pressure reading from yesterday.", answer)
        assertFalse(Regex("""\d+/\d+""").containsMatchIn(answer))
    }

    @Test
    fun whySleepSufferedAnswersThePremiseThenOneRelatedPattern() {
        val investigation = investigation(
            assessment = TrudyPremiseAssessment.SUPPORTED,
            claim = TrudyChangeClaim.WORSENED,
            comparisons = listOf(comparison("sleep_score", 62.0, 74.0)),
            associations = listOf(association(-0.52))
        )

        val answer = answer("Why has my sleep suffered?", investigation)

        assertTrue(answer.startsWith("Your recorded sleep did look worse"))
        assertTrue("calmness" in answer.lowercase())
        assertTrue(causalCaveatCount(answer) == 1)
        assertNoEngineeringLanguage(answer)
    }

    @Test
    fun objectivelyImprovedSleepDoesNotConfirmTerriblePremise() {
        val investigation = investigation(
            assessment = TrudyPremiseAssessment.NOT_SUPPORTED,
            claim = TrudyChangeClaim.WORSENED,
            comparisons = listOf(
                comparison("sleep_score", 78.0, 68.0),
                comparison("sleep_deep_minutes", 70.0, 54.0),
                comparison("sleep_continuity_score", 82.0, 72.0)
            )
        )

        val answer = answer("My sleep has been terrible lately", investigation)

        assertTrue(answer.startsWith("Your recorded sleep data doesn't support that"))
        assertTrue("improved" in answer)
        assertTrue("experience and the recorded metrics don't currently match" in answer)
    }

    @Test
    fun evidenceFollowUpNamesTheRecordsAndInheritedPeriod() {
        val rows = (1..7).map {
            metric(
                HealthDomain.SLEEP,
                "sleep_score",
                70.0 + it,
                timestamp = now - it * DAY_MS
            )
        }
        val result = TrudyToolResult.MetricWindow(
            TrudyToolOperation.GetMetricWindow(HealthDomain.SLEEP, "sleep_score", recent),
            rows
        )
        val conversation = listOf(
            TrudyConversationTurn(TrudyConversationRole.USER, "How's my sleep doing the last week or so?"),
            TrudyConversationTurn(TrudyConversationRole.ASSISTANT, "Your sleep looks stable.")
        )

        val plan = engine.plan("What data are you basing that on?", listOf(result), conversation)
        val answer = engine.synthesize(plan)

        assertTrue(answer.startsWith("I'm basing that on 7"))
        assertTrue("roughly the past week" in answer)
        assertTrue("sleep score" in answer)
    }

    @Test
    fun badProviderTemplateIsReplacedByNaturalSynthesis() {
        val result = BaselineComparisonResult(
            CompareBaseline(HealthDomain.SLEEP, "sleep_score", recent, baseline),
            comparison("sleep_score", 76.0, 75.0)
        )
        val plan = engine.plan("How's my sleep doing the last week or so?", listOf(result))

        val answer = engine.finalize(
            plan,
            "I retrieved structured evidence, but there isn't enough bounded context here to produce a reliable personal summary."
        )

        assertTrue(answer.startsWith("Your sleep looks fairly stable"))
        assertNoEngineeringLanguage(answer)
    }

    @Test
    fun schemaUnitsAreNeverSpoken() {
        val result = TrudyToolResult.MetricWindow(
            TrudyToolOperation.GetMetricWindow(HealthDomain.SLEEP, "sleep_score", recent),
            listOf(metric(HealthDomain.SLEEP, "sleep_score", 12.0, unit = "score"))
        )

        val answer = answer("What was my sleep score yesterday?", result)

        assertTrue("12" in answer)
        assertFalse("12 score" in answer.lowercase())
        assertFalse("index" in answer.lowercase())
    }

    @Test
    fun terribleSleepStatementTriggersPremiseInvestigation() {
        val planner = TrudySystemInvestigationPlanner(TrudyTemporalResolver(boundaries))

        val operation = assertIs<InvestigateChange>(
            planner.plan(TrudyAskRequest("My sleep has been terrible lately")).single()
        )

        assertEquals(TrudyChangeClaim.WORSENED, operation.claim)
        assertTrue(operation.targets.any { it.metricId == "sleep_score" })
    }

    @Test
    fun lastWeekOrSoUsesARollingWeekRatherThanPreviousCalendarWeek() {
        val timeframe = TrudyTemporalResolver(boundaries).resolve("How's my sleep doing the last week or so?")

        assertEquals(now - 7L * DAY_MS, timeframe.observation.fromEpochMs)
        assertEquals(now, timeframe.observation.toEpochMs)
        assertEquals("roughly the past week", timeframe.label)
    }

    @Test
    fun unrelatedDomainEvidenceIsClassifiedAndExcludedFromSleepRanking() {
        val sleep = TrudyToolResult.MetricWindow(
            TrudyToolOperation.GetMetricWindow(HealthDomain.SLEEP, "sleep_score", recent),
            listOf(metric(HealthDomain.SLEEP, "sleep_score", 78.0))
        )
        val body = TrudyToolResult.MetricWindow(
            TrudyToolOperation.GetMetricWindow(HealthDomain.BODY, "body_fat_pct", recent),
            listOf(metric(HealthDomain.BODY, "body_fat_pct", 16.0, unit = "%"))
        )

        val plan = engine.plan("How is my sleep doing?", listOf(sleep, body))

        assertTrue(plan.allEvidence.any {
            it.metricId == "body_fat_pct" && it.classification == TrudyAnswerEvidenceClass.IRRELEVANT
        })
        assertTrue(plan.rankedEvidence.all { it.metricId != "body_fat_pct" })
    }

    @Test
    fun cleaningABadTemplateNeverRemovesEmergencyEscalation() {
        val plan = engine.plan("I have severe chest pain", emptyList())

        val answer = engine.finalize(
            plan,
            "I retrieved structured evidence. Please call your emergency number immediately."
        )

        assertTrue(answer.startsWith("This could need emergency assessment now"))
        assertTrue("emergency number" in answer)
        assertNoEngineeringLanguage(answer)
    }

    private fun answer(question: String, vararg results: TrudyToolResult): String =
        engine.synthesize(engine.plan(question, results.toList()))

    private fun investigation(
        assessment: TrudyPremiseAssessment,
        claim: TrudyChangeClaim,
        comparisons: List<TrudyBaselineComparison>,
        associations: List<TrudyAssociationResult> = emptyList()
    ): ChangeInvestigationResult {
        val operation = InvestigateChange(
            targets = comparisons.mapIndexed { index, comparison ->
                TrudyInvestigationMetric(
                    comparison.domain,
                    comparison.metricId,
                    if (index == 0) TrudyInvestigationRole.PRIMARY else TrudyInvestigationRole.SUPPORTING,
                    TrudyMetricPreference.HIGHER_IS_FAVOURABLE
                )
            },
            related = if (associations.isEmpty()) emptyList() else listOf(
                TrudyInvestigationMetric(HealthDomain.EMOTIONAL, "emotional_calmness")
            ),
            observationWindow = recent,
            baselineWindow = baseline,
            claim = claim
        )
        return ChangeInvestigationResult(
            operation,
            TrudyChangeInvestigation(
                premiseAssessment = assessment,
                observationWindow = recent,
                baselineWindow = baseline,
                targetComparisons = comparisons,
                relatedAssociations = associations,
                missingMetrics = emptyList(),
                caveats = listOf("Observational relationship.")
            )
        )
    }

    private fun comparison(
        metricId: String,
        observation: Double,
        comparison: Double,
        recentCount: Int = 7,
        baselineCount: Int = 7
    ): TrudyBaselineComparison {
        val delta = observation - comparison
        val status = if (minOf(recentCount, baselineCount) < 5) {
            TrudyDataQualityStatus.SPARSE
        } else {
            TrudyDataQualityStatus.GOOD
        }
        val confidence = if (status == TrudyDataQualityStatus.GOOD) TrudyConfidence.MODERATE else TrudyConfidence.INSUFFICIENT
        val item = PersonalEvidenceItem(
            id = "comparison:$metricId",
            domains = listOf(HealthDomain.SLEEP),
            metricIds = listOf(metricId),
            evidenceType = PersonalEvidenceType.TREND,
            observationWindow = recent,
            comparisonWindow = baseline,
            effectDirection = effectDirection(delta),
            effectMagnitude = delta,
            sampleCount = recentCount + baselineCount,
            confidence = confidence,
            dataQualityStatus = status
        )
        return TrudyBaselineComparison(
            domain = HealthDomain.SLEEP,
            metricId = metricId,
            observationWindow = recent,
            baselineWindow = baseline,
            observationMean = observation,
            baselineMean = comparison,
            absoluteDelta = delta,
            percentDelta = delta / comparison * 100.0,
            standardizedEffect = null,
            observationSampleCount = recentCount,
            baselineSampleCount = baselineCount,
            direction = effectDirection(delta),
            confidence = confidence,
            dataQualityStatus = status,
            evidence = item
        )
    }

    private fun association(coefficient: Double): TrudyAssociationResult {
        val evidence = PersonalEvidenceItem(
            id = "association:calmness:sleep",
            domains = listOf(HealthDomain.EMOTIONAL, HealthDomain.SLEEP),
            metricIds = listOf("emotional_calmness", "sleep_score"),
            evidenceType = PersonalEvidenceType.ASSOCIATION,
            observationWindow = recent,
            effectDirection = effectDirection(coefficient),
            effectMagnitude = coefficient,
            sampleCount = 10,
            confidence = TrudyConfidence.MODERATE,
            dataQualityStatus = TrudyDataQualityStatus.GOOD
        )
        return TrudyAssociationResult(
            leftDomain = HealthDomain.EMOTIONAL,
            leftMetricId = "emotional_calmness",
            rightDomain = HealthDomain.SLEEP,
            rightMetricId = "sleep_score",
            method = TrudyAssociationMethod.PEARSON,
            coefficient = coefficient,
            sampleCount = 10,
            matchedFraction = .8,
            direction = effectDirection(coefficient),
            confidence = TrudyConfidence.MODERATE,
            dataQualityStatus = TrudyDataQualityStatus.GOOD,
            evidence = evidence
        )
    }

    private fun insight(
        id: String,
        domain: HealthDomain,
        title: String,
        explanation: String,
        quality: TrudyDataQualityEvidence
    ) = TrudyInsightEvidence(
        id = id,
        domain = domain,
        evidenceKind = TrudyEvidenceKind.INTERPRETATION,
        title = title,
        explanation = explanation,
        evidenceMetricIds = emptyList(),
        confidence = .8,
        source = "test",
        dataQuality = quality
    )

    private fun quality(
        domain: HealthDomain,
        records: Long,
        ageHours: Double?,
        stale: Boolean
    ) = TrudyDataQualityEvidence(
        domain = domain,
        score = 90,
        recordCount = records,
        distinctMetricCount = if (records == 0L) 0 else 1,
        latestTimestampEpochMs = ageHours?.let { now - (it * HOUR_MS).toLong() },
        ageHours = ageHours,
        isStale = stale,
        notes = emptyList()
    )

    private fun metric(
        domain: HealthDomain,
        metricId: String,
        value: Double,
        timestamp: Long = now - HOUR_MS,
        unit: String = "score"
    ) = TrudyMetricEvidence(
        domain = domain,
        metricId = metricId,
        value = value,
        unit = unit,
        timestampEpochMs = timestamp,
        source = "test"
    )

    private fun assertNoEngineeringLanguage(answer: String) {
        val lower = answer.lowercase()
        listOf("bounded context", "structured evidence", "tool execution", "preflight", "context bundle")
            .forEach { assertFalse(it in lower, "Answer leaked '$it': $answer") }
    }

    private fun causalCaveatCount(answer: String): Int {
        val lower = answer.lowercase()
        return listOf("doesn't show that", "does not show that", "association is not causation")
            .sumOf { phrase -> lower.windowed(phrase.length).count { it == phrase } }
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        val recent = TrudyTimeRange(90L * DAY_MS, 100L * DAY_MS)
        val baseline = TrudyTimeRange(80L * DAY_MS, 90L * DAY_MS - 1L)
    }
}
