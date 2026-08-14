package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.medical.CuratedMedicalSafetySignalProvider
import com.projectsuperhuman.next.trudy.medical.MedicalEscalationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TrudySwarmKnowledgeIntegrationTest {
    private val knowledge = TrudyKnowledgeCoordinator()

    @Test
    fun sleepSlangResolvesThroughUnifiedProviderWithCanonicalHints() = runTest {
        val result = knowledge.retrieve(
            TrudyKnowledgeQuery("I've been sleeping like crap lately", emptyList(), emptyList())
        )

        assertTrue(result.any { it.kind == TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE })
        assertTrue(result.flatMap { it.metricHints }.any { it.domain == HealthDomain.SLEEP })
        assertTrue(result.all { it.sourceReferences.isNotEmpty() })
    }

    @Test
    fun gordAndAcidRefluxResolveToTheSameMedicalReferenceBoundary() = runTest {
        val gord = knowledge.retrieve(TrudyKnowledgeQuery("Could this be GORD?", emptyList(), emptyList()))
        val reflux = knowledge.retrieve(TrudyKnowledgeQuery("Could this be acid reflux?", emptyList(), emptyList()))

        val gordItem = assertNotNull(gord.firstOrNull { it.kind == TrudyKnowledgeKind.MEDICAL })
        val refluxItem = assertNotNull(reflux.firstOrNull { it.kind == TrudyKnowledgeKind.MEDICAL })
        assertEquals("medical:gastro_oesophageal_reflux_disease", gordItem.stableId)
        assertEquals(gordItem.stableId, refluxItem.stableId)
        assertTrue(gordItem.uncertainty?.contains("not diagnostic", ignoreCase = true) == true)
    }

    @Test
    fun overnightWeightJumpGetsBodyOutcomeAndContextWithoutFatGainAssumption() = runTest {
        val result = knowledge.retrieve(
            TrudyKnowledgeQuery("Why did my weight jump overnight?", emptyList(), emptyList())
        )
        val item = assertNotNull(result.firstOrNull { it.kind == TrudyKnowledgeKind.NUTRITION })

        assertTrue(item.metricHints.any {
            it.domain == HealthDomain.BODY && it.metricId == "body_weight_kg" &&
                it.role == TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
        })
        assertTrue(item.uncertainty?.contains("fluid", ignoreCase = true) == true)
        assertTrue(item.uncertainty?.contains("fat", ignoreCase = true) == true)
    }

    @Test
    fun caffeineQuestionKeepsExperimentMethodologySeparateFromCanonicalExperimentState() = runTest {
        val result = knowledge.retrieve(
            TrudyKnowledgeQuery("How could I test whether caffeine affects my sleep?", emptyList(), emptyList())
        )
        val methodology = assertNotNull(result.firstOrNull { it.kind == TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY })

        assertTrue(methodology.summary.contains("Baseline", ignoreCase = true))
        assertTrue(methodology.summary.contains("intervention", ignoreCase = true))
        assertTrue(methodology.uncertainty?.isNotBlank() == true)
        assertTrue(methodology.metricHints.any { it.domain == HealthDomain.SLEEP })
    }

    @Test
    fun environmentalAndEmotionalKnowledgeRemainDistinctLanes() = runTest {
        val environment = knowledge.retrieve(
            TrudyKnowledgeQuery("Could my hot bedroom be why I keep waking up?", emptyList(), emptyList())
        )
        val emotional = knowledge.retrieve(
            TrudyKnowledgeQuery("I'm stressed out and feel wired", emptyList(), emptyList())
        )

        assertTrue(environment.any { it.kind == TrudyKnowledgeKind.ENVIRONMENT || HealthDomain.ENVIRONMENT in it.relevantDomains })
        assertTrue(emotional.any { it.kind == TrudyKnowledgeKind.EMOTIONAL_WELLBEING || HealthDomain.EMOTIONAL in it.relevantDomains })
    }

    @Test
    fun knowledgeAliasCanDriveOnlyItsBoundedPersonalMetric() = runTest {
        val source = object : TrudyKnowledgeSource {
            override val sourceId = "test-performance"
            override val kind = TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE
            override suspend fun retrieve(query: TrudyKnowledgeQuery) = listOf(
                TrudyKnowledgeItem(
                    stableId = "test:heart_racing",
                    kind = kind,
                    title = "Heart-rate context",
                    summary = "A racing-heart phrase can refer to heart-rate context without implying a diagnosis.",
                    sourceId = sourceId,
                    sourceReferences = listOf("test-source"),
                    relevantDomains = listOf(HealthDomain.EXERCISE),
                    relevantMetricIds = listOf("heart_rate_avg_bpm"),
                    metricHints = listOf(
                        TrudyKnowledgeMetricHint(
                            HealthDomain.EXERCISE,
                            "heart_rate_avg_bpm",
                            TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
                        )
                    ),
                    version = "test",
                    lastReviewed = "2026-08-14"
                )
            )
        }
        val executor = object : TrudyToolExecutor {
            override val definitions: List<TrudyToolDefinition> = emptyList()
            override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = when (operation) {
                is TrudyToolOperation.GetMetricHistory -> TrudyToolResult.MetricHistory(operation, emptyList())
                else -> TrudyToolResult.Failure(operation, TrudyToolFailureCode.UNSUPPORTED_OPERATION, "unexpected")
            }
        }
        val orchestrator = TrudyOrchestrator(
            modelClient = DeterministicTrudyModelClient { TrudyModelResult(responseText = "done") },
            tools = executor,
            preflightPlanner = NoOpTrudyPreflightPlanner,
            knowledgeCoordinator = TrudyKnowledgeCoordinator(listOf(source))
        )

        val result = orchestrator.ask(TrudyAskRequest("My heart is racing"))

        val operation = assertIs<TrudyToolOperation.GetMetricHistory>(result.toolCallsMade.single().operation)
        assertEquals(HealthDomain.EXERCISE, operation.domain)
        assertEquals("heart_rate_avg_bpm", operation.metricId)
        assertEquals(60, operation.limit)
    }

    @Test
    fun missingBloodPressureWindowProducesNoInventedReading() = runTest {
        val operation = TrudyToolOperation.GetMetricWindow(
            domain = HealthDomain.BLOOD_PRESSURE,
            metricId = "blood_pressure_systolic_mmhg",
            range = TrudyTimeRange(1L, 2L)
        )
        val model = OfflineDeterministicTrudyModelClient()
        val result = model.complete(
            TrudyModelRequest(
                userRequest = "What was my blood pressure yesterday?",
                toolResults = listOf(TrudyToolResult.MetricWindow(operation, emptyList()))
            )
        )

        assertEquals("There is no stored measurement for that metric in the requested time window.", result.responseText)
    }

    @Test
    fun severeChestPainWithDifficultyBreathingEscalatesWithoutBroadAlarmism() {
        val safety = CuratedMedicalSafetySignalProvider()
        val emergency = safety.assess("I have severe chest pain and difficulty breathing")
        val ordinaryReflux = safety.assess("I have acid reflux after dinner")

        assertEquals(MedicalEscalationLevel.EMERGENCY, emergency.level)
        assertTrue(emergency.signals.any { it.id == "possible_acute_coronary_pattern" })
        assertTrue(ordinaryReflux.level < MedicalEscalationLevel.URGENT)
    }

    @Test
    fun irrelevantUiTextDoesNotTriggerBroadKnowledgeRetrieval() = runTest {
        val result = knowledge.retrieve(
            TrudyKnowledgeQuery("Please rename my dashboard tile", emptyList(), emptyList())
        )
        assertTrue(result.isEmpty())
    }
}
