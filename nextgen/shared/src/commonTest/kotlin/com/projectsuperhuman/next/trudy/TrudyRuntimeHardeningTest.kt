package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TrudyRuntimeHardeningTest {
    @Test fun compositeRoutesEachTypedOperationExactlyOnce() = runBlocking {
        val health = CountingExecutor()
        val intelligence = CountingExecutor()
        val composite = CompositeTrudyToolExecutor(health, intelligence)

        composite.execute(TrudyToolOperation.GetDomainState(HealthDomain.SLEEP))
        assertEquals(1, health.calls)
        assertEquals(0, intelligence.calls)

        composite.execute(GetPersonalTrend(HealthDomain.SLEEP, "sleep_score"))
        assertEquals(1, health.calls)
        assertEquals(1, intelligence.calls)

        val unsupported = composite.execute(TrudyToolOperation.Unsupported("run_sql"))
        assertTrue(unsupported is TrudyToolResult.Failure)
        assertEquals(1, health.calls)
        assertEquals(1, intelligence.calls)
    }

    @Test fun deterministicConversationServiceUsesHealthToolForSleep() = runBlocking {
        val fixture = fixture()
        val result = fixture.service.respondTo("How was my sleep?")
        assertTrue(result.answerText.isNotBlank())
        assertTrue(result.toolCallsMade.any { it.operation is TrudyToolOperation.GetContext })
        assertTrue(result.evidenceReferences.all { it.domain == HealthDomain.SLEEP })
        assertFalse(result.isFallback)
    }

    @Test fun deterministicConversationServiceUsesTrendToolForSleepImprovement() = runBlocking {
        val fixture = fixture()
        val result = fixture.service.respondTo("Has my sleep improved recently?")
        assertTrue(result.answerText.isNotBlank())
        assertTrue(result.toolCallsMade.any { it.operation is GetPersonalTrend })
        assertFalse(result.answerText.contains("NaN"))
        assertFalse(result.answerText.contains("Infinity"))
    }

    @Test fun deterministicConversationServiceUsesAssociationToolForExerciseAndSleep() = runBlocking {
        val fixture = fixture()
        val result = fixture.service.respondTo("Is exercise linked to my sleep?")
        assertTrue(result.toolCallsMade.any { it.operation is GetLaggedAssociation })
        assertTrue("caus" in result.answerText.lowercase() || "association" in result.answerText.lowercase())
        assertTrue(result.evidenceReferences.any { it.domain == HealthDomain.EXERCISE })
        assertTrue(result.evidenceReferences.any { it.domain == HealthDomain.SLEEP })
    }

    @Test fun deterministicConversationServiceUsesExperimentHypothesisTool() = runBlocking {
        val fixture = fixture()
        val result = fixture.service.respondTo("What experiment should I try?")
        assertTrue(result.toolCallsMade.any { it.operation is GenerateExperimentHypothesis })
        assertTrue(result.answerText.isNotBlank())
    }

    @Test fun experimentEvaluationRunsThroughConversationService() = runBlocking {
        val source = fixtureSource()
        val hypothesis = TrudyExperimentEngine().plan(TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY, HealthDomain.SLEEP, "sleep_score")
        val operation = EvaluateExperiment(hypothesis, TrudyTimeRange(NOW - 14 * DAY, NOW - 8 * DAY), TrudyTimeRange(NOW - 7 * DAY, NOW), 1.0)
        val model = DeterministicTrudyModelClient { request ->
            if (request.toolResults.isEmpty()) TrudyModelResult(requestedTools = listOf(operation))
            else {
                val evaluation = request.toolResults.filterIsInstance<ExperimentEvaluationResult>().single()
                TrudyModelResult(
                    responseText = evaluation.result.summary,
                    evidenceReferences = evaluation.result.evidence.supportingEvidenceReferences
                )
            }
        }
        val intelligence = TrudyIntelligenceToolService(TrudyPersonalEvidenceLibrary(source) { NOW }, source)
        val service = TrudyConversationService(TrudyOrchestrator(model, CompositeTrudyToolExecutor(CountingExecutor(), intelligence)))
        val result = service.respondTo("Did that experiment work?")
        assertTrue(result.toolCallsMade.any { it.operation is EvaluateExperiment })
        assertTrue(result.answerText.isNotBlank())
        assertFalse(result.isFallback)
    }

    @Test fun missingAndStaleDataBecomeWarningsWithoutFabrication() = runBlocking {
        val missing = fixture(emptySleep = true).service.respondTo("How was my sleep?")
        assertTrue(missing.warnings.any { it.kind == TrudyWarningKind.EMPTY_DATA })
        assertFalse(missing.answerText.contains("82"))

        val stale = fixture(stale = true).service.respondTo("How was my sleep?")
        assertTrue(stale.warnings.any { it.kind == TrudyWarningKind.STALE_DATA })
    }

    @Test fun toolFailureAndUnsupportedOperationAreContained() = runBlocking {
        val throwing = object : TrudyToolExecutor {
            override val definitions = listOf(TrudyToolDefinition("get_context", "x", listOf("domains")))
            override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = error("boom")
        }
        var call = 0
        val model = DeterministicTrudyModelClient { request ->
            call++
            if (call == 1) TrudyModelResult(requestedTools = listOf(TrudyToolOperation.GetDomainState(HealthDomain.SLEEP)))
            else TrudyModelResult(responseText = "fabricated 999")
        }
        val failed = TrudyConversationService(TrudyOrchestrator(model, throwing)).respondTo("sleep")
        assertTrue(failed.isFallback)
        assertFalse(failed.answerText.contains("999"))
        assertTrue(failed.warnings.any { it.kind == TrudyWarningKind.TOOL_FAILURE })

        val unsupportedModel = DeterministicTrudyModelClient { request ->
            if (request.toolResults.isEmpty()) TrudyModelResult(requestedTools = listOf(TrudyToolOperation.Unsupported("unscoped_metric", mapOf("metricId" to "score"))))
            else TrudyModelResult(responseText = "should not be trusted")
        }
        val unsupported = TrudyConversationService(TrudyOrchestrator(unsupportedModel, CountingExecutor())).respondTo("give me score")
        assertTrue(unsupported.isFallback)
        assertTrue(unsupported.warnings.any { it.kind == TrudyWarningKind.UNSUPPORTED_TOOL })
    }

    @Test fun modelFailureAndIterationLimitAreContained() = runBlocking {
        val throwingModel = object : TrudyModelClient { override suspend fun complete(request: TrudyModelRequest): TrudyModelResult = error("provider failed") }
        val modelFailure = TrudyConversationService(TrudyOrchestrator(throwingModel, CountingExecutor())).respondTo("hello")
        assertTrue(modelFailure.isFallback)
        assertTrue(modelFailure.warnings.any { it.kind == TrudyWarningKind.MODEL_FAILURE })

        val looping = DeterministicTrudyModelClient { TrudyModelResult(requestedTools = listOf(TrudyToolOperation.GetDomainState(HealthDomain.SLEEP))) }
        val limited = TrudyConversationService(TrudyOrchestrator(looping, CountingExecutor(), maxModelIterations = 2)).respondTo("loop")
        assertTrue(limited.isFallback)
        assertTrue(limited.warnings.any { it.kind == TrudyWarningKind.ITERATION_LIMIT })
    }

    @Test fun evidenceBindingRejectsWrongStructuredIdentity() = runBlocking {
        val exact = TrudyEvidenceReference(HealthDomain.SLEEP, "sleep_score", evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION, timestampEpochMs = 123L)
        val variants = listOf(
            exact.copy(domain = HealthDomain.BODY),
            exact.copy(metricId = "other"),
            TrudyEvidenceReference(HealthDomain.SLEEP, insightId = "fake-insight", evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION),
            exact.copy(timestampEpochMs = 124L),
            exact.copy(evidenceKind = TrudyEvidenceKind.DERIVED_PERSONAL_TREND)
        )
        for (bad in variants) {
            val result = bindingResult(listOf(bad))
            assertTrue(result.evidenceReferences.isEmpty(), "Bad reference should not bind: $bad")
            assertTrue(result.warnings.any { it.kind == TrudyWarningKind.UNBOUND_EVIDENCE_REFERENCE })
        }

        val mixed = bindingResult(listOf(exact, exact.copy(domain = HealthDomain.BODY)))
        assertEquals(listOf(exact), mixed.evidenceReferences)
        assertTrue(mixed.warnings.any { it.kind == TrudyWarningKind.UNBOUND_EVIDENCE_REFERENCE })
    }

    @Test fun statisticsReturnSafeInsufficiencyForDegenerateInputs() = runBlocking {
        val constant = (1..6).map { n -> TrudyStatistics.AlignedPair(metric(HealthDomain.EXERCISE, "load", 1.0, n.toLong()), metric(HealthDomain.SLEEP, "score", n.toDouble(), n.toLong())) }
        assertNull(TrudyStatistics.pearson(constant))
        assertNull(TrudyStatistics.mean(listOf(1.0, Double.NaN)))
        assertNull(TrudyStatistics.standardDeviation(listOf(1.0, Double.POSITIVE_INFINITY)))
        assertEquals(-2.0, TrudyStatistics.mean(listOf(-1.0, -3.0)))

        val tied = listOf(1.0, 1.0, 2.0, 2.0, 3.0, 3.0).mapIndexed { index, value ->
            TrudyStatistics.AlignedPair(metric(HealthDomain.EXERCISE, "load", value, index.toLong()), metric(HealthDomain.SLEEP, "score", value, index.toLong()))
        }
        assertTrue((TrudyStatistics.spearman(tied) ?: 0.0) > .99)

        val duplicates = listOf(metric(HealthDomain.EXERCISE, "load", 1.0, 10), metric(HealthDomain.EXERCISE, "load", 2.0, 10))
        val right = listOf(metric(HealthDomain.SLEEP, "score", 3.0, 10), metric(HealthDomain.SLEEP, "score", 4.0, 10))
        assertEquals(2, TrudyStatistics.align(duplicates, right, alignmentWindowMs = 0).size)
        assertFailsWith<IllegalArgumentException> { TrudyStatistics.align(duplicates, right, lagMs = TrudyStatistics.MAX_LAG_MS + 1, alignmentWindowMs = 0) }

        val overflow = listOf(metric(HealthDomain.EXERCISE, "load", 1.0, Long.MAX_VALUE))
        assertTrue(TrudyStatistics.align(overflow, right, lagMs = 1, alignmentWindowMs = 0).isEmpty())

        val zeroBaseSource = MapSource(mapOf(key(HealthDomain.SLEEP,"sleep_score") to listOf(
            metric(HealthDomain.SLEEP,"sleep_score",0.0,10), metric(HealthDomain.SLEEP,"sleep_score",0.0,20),
            metric(HealthDomain.SLEEP,"sleep_score",1.0,30), metric(HealthDomain.SLEEP,"sleep_score",2.0,40)
        )))
        val comparison = TrudyPersonalEvidenceLibrary(zeroBaseSource).compareBaseline(HealthDomain.SLEEP,"sleep_score",TrudyTimeRange(30,40),TrudyTimeRange(10,20))
        assertNull(comparison.percentDelta)
    }

    @Test fun unsafeExperimentInterventionsRemainBlocked() {
        val engine = TrudyExperimentEngine()
        listOf(
            "change prescription medication dose",
            "increase insulin dose",
            "quit alcohol cold turkey",
            "stop caffeine abruptly",
            "try a seven day water fast",
            "change treatment without supervision"
        ).forEach { assertNotNull(engine.rejectUnsafeFreeformIntervention(it), it) }
        assertNull(engine.rejectUnsafeFreeformIntervention("keep bedtime within a consistent one-hour window"))
    }

    private suspend fun bindingResult(refs: List<TrudyEvidenceReference>): TrudyConversationResult {
        var calls = 0
        val model = DeterministicTrudyModelClient { request ->
            calls++
            if (calls == 1) TrudyModelResult(requestedTools = listOf(TrudyToolOperation.GetDomainState(HealthDomain.SLEEP)))
            else TrudyModelResult(responseText = "answer", evidenceReferences = refs)
        }
        val executor = object : TrudyToolExecutor {
            override val definitions = listOf(TrudyToolDefinition("get_domain_state","x",listOf("domain")))
            override suspend fun execute(operation: TrudyToolOperation) = TrudyToolResult.DomainState(
                operation as TrudyToolOperation.GetDomainState,
                listOf(metric(HealthDomain.SLEEP,"sleep_score",82.0,123L))
            )
        }
        return TrudyConversationService(TrudyOrchestrator(model,executor)).respondTo("sleep")
    }

    private fun fixture(emptySleep: Boolean = false, stale: Boolean = false): Fixture {
        val source = fixtureSource(stale)
        val health = HealthFixtureExecutor(emptySleep, stale)
        val intelligence = TrudyIntelligenceToolService(TrudyPersonalEvidenceLibrary(source) { NOW }, source)
        return Fixture(TrudyConversationService(TrudyOrchestrator(OfflineDeterministicTrudyModelClient(), CompositeTrudyToolExecutor(health,intelligence))))
    }

    private fun fixtureSource(stale: Boolean = false): MapSource {
        val sleep = (0 until 40).map { i -> metric(HealthDomain.SLEEP,"sleep_score",60.0+i,NOW-(39-i)*DAY) }
        val exercise = (0 until 20).map { i -> metric(HealthDomain.EXERCISE,"exercise_load",10.0+i,NOW-(19-i)*DAY) }
        val sleepAligned = (0 until 20).map { i -> metric(HealthDomain.SLEEP,"sleep_score",65.0+i,NOW-(19-i)*DAY+43_200_000L) }
        return MapSource(
            mapOf(
                key(HealthDomain.SLEEP,"sleep_score") to (sleep + sleepAligned).sortedBy { it.timestampEpochMs },
                key(HealthDomain.EXERCISE,"exercise_load") to exercise
            ),
            stale
        )
    }

    private data class Fixture(val service: TrudyConversationService)

    private class HealthFixtureExecutor(private val emptySleep:Boolean, private val stale:Boolean):TrudyToolExecutor {
        override val definitions=listOf(TrudyToolDefinition("get_context","context",listOf("domains")))
        override suspend fun execute(operation:TrudyToolOperation):TrudyToolResult {
            if(operation !is TrudyToolOperation.GetContext) return TrudyToolResult.Failure(operation,TrudyToolFailureCode.UNSUPPORTED_OPERATION,"health fixture only supports context")
            val domains=operation.request.domains.map { domain ->
                val values=if(domain==HealthDomain.SLEEP && !emptySleep) listOf(metric(domain,"sleep_score",82.0,NOW)) else emptyList()
                TrudyDomainContext(domain,values,emptyList(),emptyList(),emptyList(),quality(domain, stale && domain==HealthDomain.SLEEP, values.size.toLong()))
            }
            return TrudyToolResult.Context(operation,TrudyHealthContext(operation.request.domains,domains))
        }
    }

    private class CountingExecutor:TrudyToolExecutor {
        var calls=0
        override val definitions=emptyList<TrudyToolDefinition>()
        override suspend fun execute(operation:TrudyToolOperation):TrudyToolResult { calls++; return TrudyToolResult.Failure(operation,TrudyToolFailureCode.UNSUPPORTED_OPERATION,"counting") }
    }

    private class MapSource(private val values:Map<String,List<TrudyMetricEvidence>>, private val stale:Boolean=false):TrudyPersonalEvidenceSource {
        override suspend fun metricHistory(domain:HealthDomain,metricId:String,limit:Int)=values[key(domain,metricId)].orEmpty().takeLast(limit)
        override suspend fun dataQuality(domain:HealthDomain)=quality(domain,stale,values.filterKeys { it.startsWith(domain.name+"/") }.values.sumOf { it.size }.toLong())
    }

    companion object {
        private const val DAY=86_400_000L
        private const val NOW=10_000_000_000L
        private fun key(d:HealthDomain,m:String)="${d.name}/$m"
        private fun metric(d:HealthDomain,m:String,v:Double,t:Long)=TrudyMetricEvidence(d,m,v,"unit",t,source="test")
        private fun quality(d:HealthDomain,stale:Boolean=false,count:Long=20)=TrudyDataQualityEvidence(d,if(stale)45 else 90,count,1,NOW,if(stale)1000.0 else 1.0,stale,emptyList())
    }
}
