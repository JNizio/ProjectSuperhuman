package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TrudySystemIntegrationTest {
    private val boundaries = object : TrudyTemporalBoundaryProvider {
        override fun nowEpochMs() = 50L * DAY_MS + 12L * HOUR_MS
        override fun startOfTodayEpochMs() = 50L * DAY_MS
        override fun startOfWeekEpochMs() = 47L * DAY_MS
        override fun startOfMonthEpochMs(monthsAgo: Int) = (40L - monthsAgo * 30L) * DAY_MS
    }
    private val planner = TrudySystemInvestigationPlanner(TrudyTemporalResolver(boundaries))

    @Test
    fun bloodPressureYesterdayUsesBoundedMetricWindows() {
        val operations = planner.plan(TrudyAskRequest("What was my blood pressure yesterday?"))

        assertTrue(operations.isNotEmpty())
        assertTrue(operations.all { it is TrudyToolOperation.GetMetricWindow })
        val windows = operations.filterIsInstance<TrudyToolOperation.GetMetricWindow>()
        assertTrue(windows.any { it.metricId == "blood_pressure_systolic_mmhg" })
        assertTrue(windows.any { it.metricId == "blood_pressure_diastolic_mmhg" })
        assertTrue(windows.all { it.range == TrudyTimeRange(49L * DAY_MS, 50L * DAY_MS - 1L) })
    }

    @Test
    fun followUpInheritsSleepReferentAndUsesCalendarMonth() {
        val history = listOf(
            TrudyConversationTurn(TrudyConversationRole.USER, "How has my sleep been?"),
            TrudyConversationTurn(TrudyConversationRole.ASSISTANT, "Sleep summary")
        )

        val operations = planner.plan(TrudyAskRequest("What about last month?", history))

        val comparisons = operations.filterIsInstance<CompareBaseline>()
        assertTrue(comparisons.isNotEmpty())
        assertTrue(comparisons.all { it.domain == HealthDomain.SLEEP })
        assertEquals(10L * DAY_MS, comparisons.first().observationWindow.fromEpochMs)
        assertEquals(40L * DAY_MS - 1L, comparisons.first().observationWindow.toEpochMs)
    }

    @Test
    fun whyQuestionCreatesOneBoundedInvestigation() {
        val operations = planner.plan(TrudyAskRequest("Why has my sleep felt worse recently?"))

        val investigation = assertIs<InvestigateChange>(operations.single())
        assertEquals(TrudyChangeClaim.WORSENED, investigation.claim)
        assertTrue(investigation.targets.any { it.domain == HealthDomain.SLEEP && it.metricId == "sleep_score" })
        assertTrue(investigation.related.map { it.domain }.distinct().size > 1)
        assertTrue(investigation.maxAssociations <= 8)
    }

    @Test
    fun evidenceFollowUpUsesPriorStructuredKeys() {
        val history = listOf(
            TrudyConversationTurn(
                TrudyConversationRole.ASSISTANT,
                "Your sleep score changed.",
                evidenceKeys = listOf("SLEEP:sleep_score:123")
            )
        )

        val operations = planner.plan(TrudyAskRequest("What data are you basing that on?", history))

        val window = assertIs<TrudyToolOperation.GetMetricWindow>(operations.single())
        assertEquals("sleep_score", window.metricId)
    }

    @Test
    fun directCrossDomainQuestionCarriesAnExplicitWindow() {
        val operation = planner.plan(
            TrudyAskRequest("Was my stress higher on nights I slept badly last month?")
        ).single()

        val association = assertIs<GetAssociation>(operation)
        assertEquals(HealthDomain.EMOTIONAL, association.leftDomain)
        assertEquals(HealthDomain.SLEEP, association.rightDomain)
        assertTrue(association.window != null)
    }

    @Test
    fun followUpThatUsesThatKeepsPriorDomainAndNewTarget() {
        val history = listOf(
            TrudyConversationTurn(TrudyConversationRole.USER, "How has my sleep been last month?"),
            TrudyConversationTurn(TrudyConversationRole.ASSISTANT, "Sleep summary")
        )

        val operation = planner.plan(
            TrudyAskRequest("Could that affect my heart rate?", history)
        ).single()

        val association = assertIs<GetAssociation>(operation)
        assertEquals(setOf(HealthDomain.SLEEP, HealthDomain.EXERCISE), association.domains.toSet())
        assertTrue(association.window != null)
    }

    @Test
    fun cardioSummaryRequestUsesStructuredExerciseInsights() {
        val operation = planner.plan(TrudyAskRequest("Give me a weekly cardio summary")).single()

        val insights = assertIs<TrudyToolOperation.GetInsights>(operation)
        assertEquals(HealthDomain.EXERCISE, insights.domain)
    }

    @Test
    fun cardioLanguageMapsToCanonicalNof1Metrics() {
        val metrics = TrudySystemCatalog.metricsMentioned("Am I getting fitter and how has my training load changed?")

        assertTrue(metrics.any { it.domain == HealthDomain.EXERCISE && it.metricId == "cardio_fitness_efficiency_delta_pct" })
        assertTrue(metrics.any { it.domain == HealthDomain.EXERCISE && it.metricId == "cardio_chronic_training_load" })
    }

    @Test
    fun sleepAndRunningPerformanceUsesCardioEfficiencyAssociation() {
        val operation = planner.plan(
            TrudyAskRequest("Does sleep appear related to my running performance this month?")
        ).single()

        val association = assertIs<GetAssociation>(operation)
        assertEquals(HealthDomain.SLEEP, association.leftDomain)
        assertEquals("sleep_score", association.leftMetricId)
        assertEquals(HealthDomain.EXERCISE, association.rightDomain)
        assertEquals("cardio_fitness_efficiency_delta_pct", association.rightMetricId)
        assertTrue(association.window != null)
    }

    @Test
    fun runningHeartRateWeatherQuestionUsesEnvironmentalTemperature() {
        val operation = planner.plan(
            TrudyAskRequest("Does temperature affect my heart rate during runs this month?")
        ).single()

        val association = assertIs<GetAssociation>(operation)
        assertEquals(HealthDomain.ENVIRONMENT, association.leftDomain)
        assertEquals("environment_temperature_c", association.leftMetricId)
        assertEquals(HealthDomain.EXERCISE, association.rightDomain)
        assertEquals("heart_rate_avg_bpm", association.rightMetricId)
    }

    @Test
    fun improvedRecordedSleepDoesNotConfirmWorsePremise() = runTest {
        val baseline = TrudyTimeRange(0L, 9L)
        val observation = TrudyTimeRange(10L, 19L)
        val source = FakeEvidenceSource(
            mapOf(
                (HealthDomain.SLEEP to "sleep_score") to rows(HealthDomain.SLEEP, "sleep_score", 60.0, 75.0),
                (HealthDomain.SLEEP to "sleep_deep_minutes") to rows(HealthDomain.SLEEP, "sleep_deep_minutes", 50.0, 65.0),
                (HealthDomain.SLEEP to "sleep_continuity_score") to rows(HealthDomain.SLEEP, "sleep_continuity_score", 62.0, 72.0)
            )
        )
        val investigator = TrudyCrossDomainInvestigator(TrudyPersonalEvidenceLibrary(source) { 20L })
        val result = investigator.investigate(
            InvestigateChange(
                targets = listOf(
                    TrudyInvestigationMetric(HealthDomain.SLEEP, "sleep_score", TrudyInvestigationRole.PRIMARY, TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
                    TrudyInvestigationMetric(HealthDomain.SLEEP, "sleep_deep_minutes", preference = TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
                    TrudyInvestigationMetric(HealthDomain.SLEEP, "sleep_continuity_score", preference = TrudyMetricPreference.HIGHER_IS_FAVOURABLE)
                ),
                related = emptyList(),
                observationWindow = observation,
                baselineWindow = baseline,
                claim = TrudyChangeClaim.WORSENED
            )
        )

        assertEquals(TrudyPremiseAssessment.NOT_SUPPORTED, result.premiseAssessment)
        assertTrue(result.targetComparisons.all { (it.absoluteDelta ?: 0.0) > 0.0 })
    }

    @Test
    fun emptyExperimentBoundaryNeverReturnsPreviewData() = runTest {
        val service = TrudyCanonicalExperimentToolService(
            EmptyTrudyCanonicalExperimentRepository,
            FakeEvidenceSource(emptyMap()),
            TrudyExperimentEngine()
        )

        val result = service.list(GetCanonicalExperiments(TrudyCanonicalExperimentStatus.ACTIVE))

        assertEquals(TrudyExperimentPersistenceState.NOT_CONNECTED, result.persistenceState)
        assertTrue(result.experiments.isEmpty())
    }

    @Test
    fun knowledgeCoordinatorBoundsAndKeepsSourceOwnership() = runTest {
        val source = object : TrudyKnowledgeSource {
            override val sourceId = "nutrition-corpus"
            override val kind = TrudyKnowledgeKind.NUTRITION
            override suspend fun retrieve(query: TrudyKnowledgeQuery) = (1..10).map {
                TrudyKnowledgeItem(
                    stableId = "item-$it",
                    kind = kind,
                    title = "Item $it",
                    summary = "Structured summary $it",
                    sourceId = sourceId,
                    sourceReferences = listOf("source-$it"),
                    version = "1",
                    lastReviewed = "2026-08-14"
                )
            }
        }
        val result = TrudyKnowledgeCoordinator(listOf(source), maxPerSource = 3, maxTotal = 3)
            .retrieve(TrudyKnowledgeQuery("nutrition", listOf(HealthDomain.NUTRITION), listOf("food_kcal")))

        assertEquals(3, result.size)
        assertTrue(result.all { it.sourceId == source.sourceId })
    }

    private fun rows(domain: HealthDomain, metric: String, baseline: Double, observation: Double) =
        (0L..4L).map { evidence(domain, metric, baseline, it) } +
            (10L..14L).map { evidence(domain, metric, observation, it) }

    private fun evidence(domain: HealthDomain, metric: String, value: Double, timestamp: Long) =
        TrudyMetricEvidence(domain, metric, value, "score", timestamp, source = "test")

    private class FakeEvidenceSource(
        private val values: Map<Pair<HealthDomain, String>, List<TrudyMetricEvidence>>
    ) : TrudyPersonalEvidenceSource {
        override suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int) =
            values[domain to metricId].orEmpty().takeLast(limit)

        override suspend fun metricWindow(domain: HealthDomain, metricId: String, range: TrudyTimeRange, limit: Int) =
            values[domain to metricId].orEmpty()
                .filter { it.timestampEpochMs in range.fromEpochMs..range.toEpochMs }
                .takeLast(limit)

        override suspend fun dataQuality(domain: HealthDomain) = TrudyDataQualityEvidence(
            domain = domain,
            score = 100,
            recordCount = values.filterKeys { it.first == domain }.values.sumOf { it.size }.toLong(),
            distinctMetricCount = values.keys.count { it.first == domain },
            latestTimestampEpochMs = values.filterKeys { it.first == domain }.values.flatten().maxOfOrNull { it.timestampEpochMs },
            ageHours = 0.0,
            isStale = false,
            notes = emptyList()
        )
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
