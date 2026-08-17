package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.core.ModuleParityService
import com.projectsuperhuman.next.core.SYNTHETIC_DATA_SOURCE
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrudyHighSignalPipelineTest {
    private val day = 86_400_000L
    private val now = 2_000_000_000_000L

    @Test
    fun temporalDecoratorUsesExactThirtyDayWindowAndConvertsHistoryRead() {
        val boundaries = FixedTemporalBoundaries(now, day)
        val delegate = object : TrudyPreflightPlanner {
            override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> = listOf(
                TrudyToolOperation.GetMetricHistory(HealthDomain.SLEEP, "sleep_score", 250)
            )
        }
        val planner = TrudyTemporalPlanningDecorator(delegate, boundaries)

        val operations = planner.plan(TrudyAskRequest("How has my sleep been over the last 30 days?"))

        val window = assertIs<TrudyToolOperation.GetMetricWindow>(operations.single())
        assertEquals(now - 30L * day, window.range.fromEpochMs)
        assertEquals(now, window.range.toEpochMs)
    }

    @Test
    fun temporalDecoratorResolvesBeforeThatFromPreviousUserTimeframe() {
        val boundaries = FixedTemporalBoundaries(now, day)
        val delegate = object : TrudyPreflightPlanner {
            override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> = listOf(
                CompareBaseline(
                    HealthDomain.SLEEP,
                    "sleep_score",
                    TrudyTimeRange(now - 7L * day, now),
                    TrudyTimeRange(now - 14L * day, now - 7L * day - 1L)
                )
            )
        }
        val planner = TrudyTemporalPlanningDecorator(delegate, boundaries)
        val previous = TrudyConversationTurn(TrudyConversationRole.USER, "What about last month?")

        val operation = assertIs<CompareBaseline>(
            planner.plan(TrudyAskRequest("And before that?", listOf(previous))).single()
        )

        val previousMonthStart = boundaries.startOfMonthEpochMs(1)
        val twoMonthsAgoStart = boundaries.startOfMonthEpochMs(2)
        assertEquals(twoMonthsAgoStart, operation.observationWindow.fromEpochMs)
        assertEquals(previousMonthStart - 1L, operation.observationWindow.toEpochMs)
        assertTrue(operation.baselineWindow.toEpochMs < operation.observationWindow.fromEpochMs)
    }

    @Test
    fun syntheticRowsNeverReplaceRealPersonalEvidence() = runTest {
        val real = HealthValue(
            HealthDomain.SLEEP,
            "sleep_score",
            81.0,
            "score",
            now - day,
            "health-connect"
        )
        val synthetic = HealthValue(
            HealthDomain.SLEEP,
            "sleep_score",
            99.0,
            "score",
            now,
            SYNTHETIC_DATA_SOURCE
        )
        val port = HighSignalFakePort(HealthDomain.SLEEP, listOf(real, synthetic))
        val ports = HealthDomain.entries.associateWith { domain ->
            if (domain == HealthDomain.SLEEP) port else HighSignalFakePort(domain, emptyList())
        }
        val service = TrudyHealthContextService(
            ModuleParityService(modulePort = { ports.getValue(it) }, nowEpochMs = { now })
        )

        val current = service.currentState(HealthDomain.SLEEP)
        val history = service.metricHistory(HealthDomain.SLEEP, "sleep_score")
        val quality = service.dataQuality(HealthDomain.SLEEP)

        assertEquals(81.0, current.single().value)
        assertEquals(listOf(81.0), history.map { it.value })
        assertTrue(current.none { it.source == SYNTHETIC_DATA_SOURCE })
        assertTrue(history.none { it.source == SYNTHETIC_DATA_SOURCE })
        assertTrue(quality.notes.any { "synthetic" in it.lowercase() })
        assertTrue(service.derivedFeatures(HealthDomain.SLEEP).isEmpty())
    }

    @Test
    fun h19cDirectBleIsPreservedAndClassifiedAsWearableEvidence() = runTest {
        val h19c = HealthValue(
            HealthDomain.EXERCISE,
            "heart_rate_bpm",
            63.0,
            "bpm",
            now,
            "h19c-direct-ble",
            metadata = mapOf("deviceName" to "H19C", "transport" to "ble-direct")
        )
        val port = HighSignalFakePort(HealthDomain.EXERCISE, listOf(h19c))
        val ports = HealthDomain.entries.associateWith { domain ->
            if (domain == HealthDomain.EXERCISE) port else HighSignalFakePort(domain, emptyList())
        }
        val service = TrudyHealthContextService(
            ModuleParityService(modulePort = { ports.getValue(it) }, nowEpochMs = { now })
        )

        val evidence = service.currentState(HealthDomain.EXERCISE).single()

        assertEquals("h19c-direct-ble", evidence.source)
        assertEquals("wearable", evidence.metadata["trudyCaptureKind"])
        assertEquals("H19C", evidence.metadata["deviceName"])
    }

    @Test
    fun synthesisPromptContainsSelectedEvidenceInsteadOfRetrievalDump() {
        val sleep = metric(HealthDomain.SLEEP, "sleep_score", 78.0, now)
        val steps = metric(HealthDomain.EXERCISE, "steps", 12_000.0, now)
        val sleepResult = TrudyToolResult.MetricHistory(
            TrudyToolOperation.GetMetricHistory(HealthDomain.SLEEP, "sleep_score"),
            listOf(sleep)
        )
        val stepResult = TrudyToolResult.MetricHistory(
            TrudyToolOperation.GetMetricHistory(HealthDomain.EXERCISE, "steps"),
            listOf(steps)
        )
        val plan = TrudyAnswerPlan(
            intent = TrudyAnswerIntent.TREND,
            timeframeLabel = "recently",
            allEvidence = listOf(answerEvidence(HealthDomain.SLEEP, "sleep_score", now)),
            rankedEvidence = listOf(answerEvidence(HealthDomain.SLEEP, "sleep_score", now)),
            limitations = emptyList(),
            targetSentenceCount = 2
        )
        val formatter = TrudyPromptFormatter()

        val formatted = formatter.format(
            TrudyModelRequest(
                userRequest = "How is my sleep?",
                toolDefinitions = listOf(TrudyToolDefinition("get_metric_history", "history", listOf("domain", "metricId"))),
                toolResults = listOf(sleepResult, stepResult),
                answerPlan = plan
            )
        )

        assertTrue(formatted.tools.isBlank())
        assertTrue("sleep_score" in formatted.toolResults)
        assertFalse("steps=12000" in formatted.toolResults)
        assertTrue("sleep_score" in formatted.evidenceIndex)
        assertFalse("EXERCISE/steps" in formatted.evidenceIndex)
    }

    @Test
    fun crossDomainAssociationKeepsEvidenceFromBothSides() {
        val sleep = metric(HealthDomain.SLEEP, "sleep_score", 78.0, now)
        val exercise = metric(HealthDomain.EXERCISE, "exercise_minutes", 45.0, now)
        val results = listOf<TrudyToolResult>(
            TrudyToolResult.MetricHistory(
                TrudyToolOperation.GetMetricHistory(HealthDomain.SLEEP, "sleep_score"),
                listOf(sleep)
            ),
            TrudyToolResult.MetricHistory(
                TrudyToolOperation.GetMetricHistory(HealthDomain.EXERCISE, "exercise_minutes"),
                listOf(exercise)
            )
        )
        val association = TrudyAnswerEvidence(
            id = "association:exercise:sleep",
            classification = TrudyAnswerEvidenceClass.SUPPORTING,
            kind = TrudyAnswerEvidenceKind.ASSOCIATION,
            domain = HealthDomain.SLEEP,
            metricId = "sleep_score",
            label = "Exercise and sleep",
            summary = "A personal association was detected.",
            sampleCount = 12,
            associationLeftMetricId = "exercise_minutes",
            associationRightMetricId = "sleep_score",
            relevanceScore = 95.0
        )
        val plan = TrudyAnswerPlan(
            intent = TrudyAnswerIntent.ASSOCIATION,
            timeframeLabel = "recently",
            allEvidence = listOf(association),
            rankedEvidence = listOf(association),
            limitations = emptyList(),
            targetSentenceCount = 3
        )

        val refs = selectedEvidenceReferences(results, plan)

        assertEquals(setOf(HealthDomain.SLEEP, HealthDomain.EXERCISE), refs.map { it.domain }.toSet())
        assertEquals(setOf("sleep_score", "exercise_minutes"), refs.mapNotNull { it.metricId }.toSet())
    }

    @Test
    fun hostedHybridDoesNotMarkEveryRetrievedRecordAsUsed() = runTest {
        val sleep = metric(HealthDomain.SLEEP, "sleep_score", 78.0, now)
        val steps = metric(HealthDomain.EXERCISE, "steps", 12_000.0, now)
        val results = listOf<TrudyToolResult>(
            TrudyToolResult.MetricHistory(
                TrudyToolOperation.GetMetricHistory(HealthDomain.SLEEP, "sleep_score"),
                listOf(sleep)
            ),
            TrudyToolResult.MetricHistory(
                TrudyToolOperation.GetMetricHistory(HealthDomain.EXERCISE, "steps"),
                listOf(steps)
            )
        )
        val plan = TrudyAnswerPlan(
            intent = TrudyAnswerIntent.PERSONAL_SUMMARY,
            timeframeLabel = "recently",
            allEvidence = listOf(answerEvidence(HealthDomain.SLEEP, "sleep_score", now)),
            rankedEvidence = listOf(answerEvidence(HealthDomain.SLEEP, "sleep_score", now)),
            limitations = emptyList(),
            targetSentenceCount = 2
        )
        val model = DeterministicTrudyModelClient {
            TrudyModelResult(
                responseText = "Your sleep score is the main signal here.",
                evidenceReferences = results.flatMap(::evidenceReferences)
            )
        }
        val client = HybridTrudyModelClient(model)

        val response = client.complete(
            TrudyModelRequest(
                userRequest = "How am I doing?",
                toolResults = results,
                answerPlan = plan
            )
        )

        assertEquals(1, response.evidenceReferences.size)
        assertEquals(HealthDomain.SLEEP, response.evidenceReferences.single().domain)
        assertEquals("sleep_score", response.evidenceReferences.single().metricId)
    }

    private fun metric(domain: HealthDomain, metric: String, value: Double, timestamp: Long) =
        TrudyMetricEvidence(
            domain = domain,
            metricId = metric,
            value = value,
            unit = when (metric) {
                "steps" -> "count"
                "exercise_minutes" -> "min"
                else -> "score"
            },
            timestampEpochMs = timestamp,
            source = "test-wearable"
        )

    private fun answerEvidence(domain: HealthDomain, metric: String, timestamp: Long) =
        TrudyAnswerEvidence(
            id = "$domain:$metric:$timestamp",
            classification = TrudyAnswerEvidenceClass.USABLE,
            kind = TrudyAnswerEvidenceKind.OBSERVATION,
            domain = domain,
            metricId = metric,
            label = metric,
            summary = "$metric is relevant",
            sampleCount = 1,
            timestampEpochMs = timestamp,
            relevanceScore = 100.0
        )
}

private class FixedTemporalBoundaries(
    private val now: Long,
    private val day: Long
) : TrudyTemporalBoundaryProvider {
    override fun nowEpochMs(): Long = now
    override fun startOfTodayEpochMs(): Long = now.floorDiv(day) * day
    override fun startOfWeekEpochMs(): Long = startOfTodayEpochMs() - 3L * day
    override fun startOfMonthEpochMs(monthsAgo: Int): Long =
        startOfTodayEpochMs() - (10L + 30L * monthsAgo) * day
}

private class HighSignalFakePort(
    override val domain: HealthDomain,
    rows: List<HealthValue>
) : ModuleDataPort {
    private val stored = rows.toMutableList()

    override suspend fun save(values: List<HealthValue>) {
        require(values.all { it.domain == domain })
        stored += values
    }

    override suspend fun latest(metric: String): HealthValue? =
        stored.filter { it.metric == metric }.maxByOrNull { it.timestampEpochMs }

    override suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> =
        stored.filter { it.metric == metric && it.timestampEpochMs in fromEpochMs..toEpochMs }
            .sortedBy { it.timestampEpochMs }

    override suspend fun page(metric: String?, limit: Int, offset: Int): List<HealthValue> =
        stored.asSequence()
            .filter { metric == null || it.metric == metric }
            .sortedByDescending { it.timestampEpochMs }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(0))
            .toList()

    override suspend fun count(): Long = stored.size.toLong()
}
