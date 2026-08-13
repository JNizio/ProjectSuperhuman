package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrudyFailureContainmentTest {
    @Test fun intelligenceExecutorFailureIsContainedAsToolFallback() = runBlocking {
        val health = CountingFailureExecutor()
        val intelligence = object : TrudyToolExecutor {
            override val definitions = listOf(TrudyToolDefinition("get_personal_trend", "trend", listOf("domain", "metricId")))
            override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = error("intelligence backend failed")
        }
        var turn = 0
        val model = DeterministicTrudyModelClient {
            turn++
            if (turn == 1) TrudyModelResult(requestedTools = listOf(GetPersonalTrend(HealthDomain.SLEEP, "sleep_score")))
            else TrudyModelResult(responseText = "unsupported fabricated conclusion")
        }

        val result = TrudyConversationService(
            TrudyOrchestrator(model, CompositeTrudyToolExecutor(health, intelligence))
        ).respondTo("Has my sleep improved?")

        assertTrue(result.isFallback)
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.TOOL_FAILURE })
        assertTrue("won't guess" in result.answerText.lowercase())
        assertEquals(0, health.calls)
    }

    @Test fun malformedDomainQualifiedMetricNeverReachesExecutor() = runBlocking {
        val executor = CountingFailureExecutor()
        var turn = 0
        val model = DeterministicTrudyModelClient {
            turn++
            if (turn == 1) TrudyModelResult(
                requestedTools = listOf(TrudyToolOperation.GetMetricHistory(HealthDomain.SLEEP, "", limit = 10))
            ) else TrudyModelResult(responseText = "should not be accepted")
        }

        val result = TrudyConversationService(TrudyOrchestrator(model, executor)).respondTo("ambiguous metric")
        assertTrue(result.isFallback)
        assertTrue(result.warnings.any { it.kind == TrudyWarningKind.MALFORMED_TOOL_REQUEST })
        assertEquals(0, executor.calls)
    }

    @Test fun generalUnsupportedQuestionDoesNotTriggerHealthRead() = runBlocking {
        val executor = CountingFailureExecutor()
        val result = TrudyConversationService(
            TrudyOrchestrator(OfflineDeterministicTrudyModelClient(), executor)
        ).respondTo("Write me a poem about the moon")

        assertTrue(result.answerText.isNotBlank())
        assertEquals(0, executor.calls)
        assertTrue(result.toolCallsMade.isEmpty())
    }
}

private class CountingFailureExecutor : TrudyToolExecutor {
    var calls = 0
    override val definitions: List<TrudyToolDefinition> = emptyList()
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult {
        calls++
        return TrudyToolResult.Failure(operation, TrudyToolFailureCode.EXECUTION_FAILED, "failed")
    }
}
