package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrudyOrchestratorTest {
    private val now = 2_000_000_000_000L

    @Test
    fun noToolResponseReturnsDirectModelAnswer() = runTest {
        val model = ScriptedModel(TrudyModelResult(responseText = "Hello."))
        val tools = RecordingTools()

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("Say hello"))

        assertEquals(TrudyOrchestrationStatus.SUCCESS, result.status)
        assertEquals("Hello.", result.answerText)
        assertTrue(result.toolCallsMade.isEmpty())
        assertTrue(tools.operations.isEmpty())
    }

    @Test
    fun oneToolResponseExecutesAndBindsEvidence() = runTest {
        val operation = TrudyToolOperation.GetDomainState(HealthDomain.SLEEP)
        val evidence = metric(HealthDomain.SLEEP, "sleep_score", 82.0)
        val tools = RecordingTools { TrudyToolResult.DomainState(operation, listOf(evidence)) }
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(operation)),
            TrudyModelResult(
                responseText = "Your latest sleep score is available.",
                evidenceReferences = listOf(
                    TrudyEvidenceReference(
                        domain = HealthDomain.SLEEP,
                        metricId = "sleep_score",
                        evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION
                    )
                )
            )
        )

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("How was my sleep?"))

        assertEquals(1, result.toolCallsMade.size)
        assertTrue(result.toolCallsMade.single().succeeded)
        assertEquals(now, result.evidenceReferences.single().timestampEpochMs)
        assertEquals("sleep_score", result.evidenceReferences.single().metricId)
    }

    @Test
    fun multiToolResponseExecutesEveryQualifiedOperation() = runTest {
        val state = TrudyToolOperation.GetDomainState(HealthDomain.SLEEP)
        val insights = TrudyToolOperation.GetInsights(HealthDomain.SLEEP)
        val tools = RecordingTools { operation ->
            when (operation) {
                state -> TrudyToolResult.DomainState(state, listOf(metric(HealthDomain.SLEEP, "sleep_score", 80.0)))
                insights -> TrudyToolResult.Insights(insights, emptyList())
                else -> error("unexpected")
            }
        }
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(state, insights)),
            TrudyModelResult(responseText = "Done.")
        )

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("What changed?"))

        assertEquals(listOf(state, insights), tools.operations)
        assertEquals(2, result.toolCallsMade.size)
    }

    @Test
    fun explicitCrossDomainContextUsesOnlyListedDomains() = runTest {
        val request = TrudyContextRequest(
            domains = listOf(HealthDomain.SLEEP, HealthDomain.NUTRITION),
            includeHistory = false
        )
        val operation = TrudyToolOperation.GetContext(request)
        val context = TrudyHealthContext(
            requestedDomains = request.domains,
            domains = request.domains.map { emptyDomain(it) }
        )
        val tools = RecordingTools { TrudyToolResult.Context(operation, context) }
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(operation)),
            TrudyModelResult(responseText = "Compared.")
        )

        TrudyOrchestrator(model, tools).ask(TrudyAskRequest("Compare sleep and nutrition"))

        val executed = tools.operations.single() as TrudyToolOperation.GetContext
        assertEquals(listOf(HealthDomain.SLEEP, HealthDomain.NUTRITION), executed.domains)
        assertFalse(executed.domains.contains(HealthDomain.BODY))
    }

    @Test
    fun malformedToolRequestIsRejectedBeforeExecutor() = runTest {
        val malformed = TrudyToolOperation.GetMetricHistory(
            domain = HealthDomain.SLEEP,
            metricId = "",
            limit = 20
        )
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(malformed)),
            TrudyModelResult(responseText = "I cannot read that metric.")
        )
        val tools = RecordingTools()

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("Read metric"))

        assertTrue(tools.operations.isEmpty())
        assertEquals(TrudyOrchestrationStatus.FALLBACK, result.status)
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.MALFORMED_TOOL_REQUEST })
    }

    @Test
    fun unsupportedToolNeverBecomesHealthRead() = runTest {
        val unsupported = TrudyToolOperation.Unsupported("run_sql", mapOf("sql" to "SELECT *"))
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(unsupported)),
            TrudyModelResult(responseText = "Cannot do that.")
        )
        val tools = RecordingTools()

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("Run arbitrary query"))

        assertTrue(tools.operations.isEmpty())
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.UNSUPPORTED_TOOL })
        assertEquals(TrudyOrchestrationStatus.FALLBACK, result.status)
    }

    @Test
    fun repeatedToolRequestsHitIterationLimit() = runTest {
        val operation = TrudyToolOperation.GetDomainState(HealthDomain.BODY)
        val model = RepeatingToolModel(operation)
        val tools = RecordingTools { TrudyToolResult.DomainState(operation, emptyList()) }

        val result = TrudyOrchestrator(model, tools, maxModelIterations = 2)
            .ask(TrudyAskRequest("Keep looking"))

        assertEquals(TrudyOrchestrationStatus.FALLBACK, result.status)
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.ITERATION_LIMIT })
        assertEquals(1, tools.operations.size)
        assertEquals(2, model.calls)
    }

    @Test
    fun emptyDataPropagatesAsStructuredWarning() = runTest {
        val operation = TrudyToolOperation.GetDomainState(HealthDomain.SLEEP)
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(operation)),
            TrudyModelResult(responseText = "There is no sleep data yet.")
        )
        val tools = RecordingTools { TrudyToolResult.DomainState(operation, emptyList()) }

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("How was sleep?"))

        assertEquals(TrudyOrchestrationStatus.SUCCESS, result.status)
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.EMPTY_DATA })
    }

    @Test
    fun staleAndLowQualityDataPropagate() = runTest {
        val operation = TrudyToolOperation.GetDataQuality(HealthDomain.SLEEP)
        val quality = TrudyDataQualityEvidence(
            domain = HealthDomain.SLEEP,
            score = 25,
            recordCount = 3,
            distinctMetricCount = 1,
            latestTimestampEpochMs = now - 40L * 24L * 60L * 60L * 1000L,
            ageHours = 40.0 * 24.0,
            isStale = true,
            notes = listOf("stale")
        )
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(operation)),
            TrudyModelResult(responseText = "The data is limited.")
        )
        val tools = RecordingTools { TrudyToolResult.DataQuality(operation, quality) }

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("Can you trust my sleep data?"))

        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.STALE_DATA })
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.LOW_DATA_QUALITY })
    }

    @Test
    fun toolFailureProducesHonestFallback() = runTest {
        val operation = TrudyToolOperation.GetDomainState(HealthDomain.CLINICAL)
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(operation)),
            TrudyModelResult(responseText = "Everything looks normal.")
        )
        val tools = ThrowingTools()

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("Are my labs okay?"))

        assertEquals(TrudyOrchestrationStatus.FALLBACK, result.status)
        assertEquals("I couldn't access the requested health data reliably, so I won't guess.", result.answerText)
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.TOOL_FAILURE })
        assertTrue(result.evidenceReferences.isEmpty())
    }

    @Test
    fun inventedEvidenceReferenceIsDroppedButRealReferenceIsPreserved() = runTest {
        val operation = TrudyToolOperation.GetDomainState(HealthDomain.BODY)
        val evidence = metric(HealthDomain.BODY, "body_weight_kg", 78.0)
        val model = ScriptedModel(
            TrudyModelResult(requestedTools = listOf(operation)),
            TrudyModelResult(
                responseText = "Weight is available.",
                evidenceReferences = listOf(
                    TrudyEvidenceReference(
                        HealthDomain.BODY,
                        metricId = "body_weight_kg",
                        evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION
                    ),
                    TrudyEvidenceReference(
                        HealthDomain.BODY,
                        metricId = "invented_metric",
                        evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION
                    )
                )
            )
        )
        val tools = RecordingTools { TrudyToolResult.DomainState(operation, listOf(evidence)) }

        val result = TrudyOrchestrator(model, tools).ask(TrudyAskRequest("What is my weight?"))

        assertEquals(listOf("body_weight_kg"), result.evidenceReferences.mapNotNull { it.metricId })
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.UNBOUND_EVIDENCE_REFERENCE })
    }

    private fun metric(domain: HealthDomain, metric: String, value: Double) = TrudyMetricEvidence(
        domain = domain,
        metricId = metric,
        value = value,
        unit = "unit",
        timestampEpochMs = now,
        source = "test"
    )

    private fun emptyDomain(domain: HealthDomain) = TrudyDomainContext(
        domain = domain,
        currentState = emptyList(),
        history = emptyList(),
        derivedFeatures = emptyList(),
        insights = emptyList(),
        dataQuality = null
    )
}

private class ScriptedModel(
    vararg results: TrudyModelResult
) : TrudyModelClient {
    private val queue = results.toMutableList()
    val requests = mutableListOf<TrudyModelRequest>()

    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        requests += request
        return queue.removeFirst()
    }
}

private class RepeatingToolModel(
    private val operation: TrudyToolOperation
) : TrudyModelClient {
    var calls: Int = 0
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        calls++
        return TrudyModelResult(requestedTools = listOf(operation))
    }
}

private class RecordingTools(
    private val responder: (TrudyToolOperation) -> TrudyToolResult = {
        error("Tool executor should not have been called")
    }
) : TrudyToolExecutor {
    override val definitions: List<TrudyToolDefinition> = emptyList()
    val operations = mutableListOf<TrudyToolOperation>()

    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult {
        operations += operation
        return responder(operation)
    }
}

private class ThrowingTools : TrudyToolExecutor {
    override val definitions: List<TrudyToolDefinition> = emptyList()
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult {
        error("boom")
    }
}
