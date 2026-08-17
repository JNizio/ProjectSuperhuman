package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyLongitudinalReasoningTest {
    private val day = 86_400_000L
    private val now = 2_000_000_000_000L

    @Test
    fun undatedLifeEventDoesNotSilentlyBecomeRecentWindow() = runTest {
        val planner = TrudyLongitudinalPreflightPlanner(object : TrudyPreflightPlanner {
            override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> = listOf(
                CompareBaseline(
                    HealthDomain.EXERCISE,
                    "resting_heart_rate_bpm",
                    TrudyTimeRange(now - 7 * day, now),
                    TrudyTimeRange(now - 14 * day, now - 7 * day - 1)
                )
            )
        })

        val planned = planner.plan(TrudyAskRequest("My resting heart rate seems higher since I started running."))
        assertIs<GetCanonicalExperiments>(planned.single())

        val model = TrudyLongitudinalModelClient(
            DeterministicTrudyModelClient { TrudyModelResult(responseText = "delegate should not answer") }
        )
        val result = model.complete(
            TrudyModelRequest(
                userRequest = "My resting heart rate seems higher since I started running.",
                toolResults = listOf(
                    CanonicalExperimentsResult(
                        GetCanonicalExperiments(),
                        TrudyExperimentPersistenceState.AVAILABLE,
                        emptyList()
                    )
                )
            )
        )

        val answer = result.responseText.orEmpty().lowercase()
        assertTrue("doesn't have a stored date" in answer)
        assertTrue("won't substitute" in answer)
        assertFalse("last 7 days" in answer)
        assertTrue(result.requestedTools.isEmpty())
    }

    @Test
    fun canonicalExperimentProvidesExactBeforeAfterWindows() = runTest {
        val baseline = TrudyTimeRange(now - 28 * day, now - 15 * day)
        val intervention = TrudyTimeRange(now - 14 * day, now - day)
        val experiment = experiment(
            title = "Daily running",
            interventionText = "Run 15 minutes every day",
            baseline = baseline,
            intervention = intervention
        )
        val model = TrudyLongitudinalModelClient(
            DeterministicTrudyModelClient { TrudyModelResult(responseText = "delegate") }
        )

        val response = model.complete(
            TrudyModelRequest(
                userRequest = "What changed in my resting heart rate after I started running every day?",
                toolResults = listOf(
                    CanonicalExperimentsResult(
                        GetCanonicalExperiments(),
                        TrudyExperimentPersistenceState.AVAILABLE,
                        listOf(experiment)
                    )
                )
            )
        )

        val operation = assertIs<InvestigateChange>(response.requestedTools.single())
        assertEquals(intervention, operation.observationWindow)
        assertEquals(baseline, operation.baselineWindow)
        assertTrue(operation.timeframeExplicit)
        assertTrue(operation.targets.any { it.metricId == "resting_heart_rate_bpm" })
    }

    @Test
    fun genericExperimentReferenceUsesActiveCanonicalExperiment() = runTest {
        val experiment = experiment(
            title = "Earlier caffeine cutoff",
            interventionText = "No caffeine after 14:00",
            baseline = TrudyTimeRange(now - 20 * day, now - 11 * day),
            intervention = TrudyTimeRange(now - 10 * day, now - day)
        )
        val model = TrudyLongitudinalModelClient(
            DeterministicTrudyModelClient { TrudyModelResult(responseText = "delegate") }
        )

        val response = model.complete(
            TrudyModelRequest(
                userRequest = "What changed after I started the experiment?",
                toolResults = listOf(
                    CanonicalExperimentsResult(
                        GetCanonicalExperiments(),
                        TrudyExperimentPersistenceState.AVAILABLE,
                        listOf(experiment)
                    )
                )
            )
        )

        assertTrue(response.requestedTools.single() is InvestigateChange)
    }

    @Test
    fun phaseShiftDetectorFindsLargeStableSleepShift() {
        val rows = (0 until 14).map { index ->
            val value = if (index < 7) 82.0 + (index % 2) else 67.0 + (index % 2)
            metric(
                HealthDomain.SLEEP,
                "sleep_score",
                value,
                now - (13 - index) * day,
                "health-connect"
            )
        }

        val shift = assertNotNull(TrudyPhaseShiftDetector.detect(rows))
        assertEquals(HealthDomain.SLEEP, shift.domain)
        assertEquals("sleep_score", shift.metricId)
        assertTrue(shift.afterMean < shift.beforeMean)
        assertTrue(kotlin.math.abs(shift.relativeChange) > 0.10)
    }

    @Test
    fun smallNoisyVariationDoesNotInventChangePoint() {
        val rows = (0 until 14).map { index ->
            metric(
                HealthDomain.SLEEP,
                "sleep_score",
                80.0 + (index % 3) * 0.4,
                now - (13 - index) * day,
                "health-connect"
            )
        }
        assertEquals(null, TrudyPhaseShiftDetector.detect(rows))
    }

    @Test
    fun multipleWearablesRemainDistinctWhenTheyDisagree() {
        val h19 = metric(HealthDomain.EXERCISE, "heart_rate_bpm", 62.0, now, "h19c-direct-ble")
        val hc = metric(HealthDomain.EXERCISE, "heart_rate_bpm", 74.0, now - 30_000L, "health-connect")
        val disagreement = TrudyLongitudinalContextHints.sourceDisagreement(
            listOf(
                TrudyToolResult.MetricWindow(
                    TrudyToolOperation.GetMetricWindow(
                        HealthDomain.EXERCISE,
                        "heart_rate_bpm",
                        TrudyTimeRange(now - day, now)
                    ),
                    listOf(h19, hc)
                )
            )
        )

        assertNotNull(disagreement)
        assertEquals("heart_rate_bpm", disagreement.metricId)
        assertTrue("H19C" in disagreement.sources)
        assertTrue("Health Connect" in disagreement.sources)
        assertTrue("do not silently average" in disagreement.modelHint)
    }

    @Test
    fun bareWhyCarriesPreviousUserQuestionIntoPlanning() {
        var seen = ""
        val planner = TrudyLongitudinalPreflightPlanner(object : TrudyPreflightPlanner {
            override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> {
                seen = request.userMessage
                return listOf(TrudyToolOperation.GetDomainState(HealthDomain.SLEEP))
            }
        })
        planner.plan(
            TrudyAskRequest(
                userMessage = "Why?",
                conversationContext = listOf(
                    TrudyConversationTurn(TrudyConversationRole.USER, "Why has my sleep been worse this week?"),
                    TrudyConversationTurn(TrudyConversationRole.ASSISTANT, "Your sleep continuity fell.")
                )
            )
        )

        assertTrue("previous finding" in seen.lowercase())
        assertTrue("sleep" in seen.lowercase())
    }

    @Test
    fun responseGuardKeepsClinicalConditionAsRecordedContextNotConfirmation() = runTest {
        val condition = TrudyMetricEvidence(
            domain = HealthDomain.CLINICAL,
            metricId = "clinical.condition.example",
            value = 1.0,
            unit = "present",
            timestampEpochMs = now,
            source = "native-condition-profile-v1",
            metadata = mapOf("displayName" to "Example condition", "profileType" to "chronic-condition")
        )
        val guard = TrudyLongitudinalResponseGuard(
            delegate = DeterministicTrudyModelClient { TrudyModelResult(responseText = "Your condition is recorded.") },
            nowEpochMs = { now }
        )
        val result = guard.complete(
            TrudyModelRequest(
                userRequest = "What is my condition reading?",
                toolResults = listOf(
                    TrudyToolResult.DomainState(
                        TrudyToolOperation.GetDomainState(HealthDomain.CLINICAL),
                        listOf(condition)
                    )
                )
            )
        )

        val text = result.responseText.orEmpty().lowercase()
        assertTrue("recorded in project superhuman" in text)
        assertTrue("not as proof of clinician confirmation" in text)
    }

    private fun experiment(
        title: String,
        interventionText: String,
        baseline: TrudyTimeRange,
        intervention: TrudyTimeRange
    ) = TrudyCanonicalExperiment(
        id = "exp-${title.lowercase().replace(' ', '-')}",
        title = title,
        hypothesis = "$interventionText may change the target",
        intervention = interventionText,
        status = TrudyCanonicalExperimentStatus.ACTIVE,
        targetDomain = HealthDomain.SLEEP,
        targetMetricId = "sleep_score",
        baselineWindow = baseline,
        interventionWindow = intervention,
        source = "test-repository",
        updatedEpochMs = now
    )

    private fun metric(
        domain: HealthDomain,
        metricId: String,
        value: Double,
        timestamp: Long,
        source: String
    ) = TrudyMetricEvidence(
        domain = domain,
        metricId = metricId,
        value = value,
        unit = when {
            metricId.endsWith("_bpm") -> "bpm"
            metricId == "sleep_score" -> "score"
            else -> "unit"
        },
        timestampEpochMs = timestamp,
        source = source,
        metadata = if ("h19c" in source || "health" in source) mapOf("trudyCaptureKind" to "wearable") else emptyMap()
    )
}
