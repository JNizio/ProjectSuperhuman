package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.core.ModuleParityService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrudyModelRuntimeTest {
    private val now = 2_000_000_000_000L

    @Test
    fun deterministicRuntimeUsesRealOrchestratorAndHealthToolFlow() = runTest {
        val sleepPort = RuntimeFakePort(
            HealthDomain.SLEEP,
            listOf(
                HealthValue(
                    domain = HealthDomain.SLEEP,
                    metric = "sleep_score",
                    value = 82.0,
                    unit = "score",
                    timestampEpochMs = now,
                    source = "test"
                )
            )
        )
        val ports = HealthDomain.entries.associateWith { domain ->
            if (domain == HealthDomain.SLEEP) sleepPort else RuntimeFakePort(domain, emptyList())
        }
        val parity = ModuleParityService(
            modulePort = { domain -> ports.getValue(domain) },
            nowEpochMs = { now }
        )
        val tools = TrudyHealthToolService(TrudyHealthContextService(parity))
        val service = TrudyConversationService(
            TrudyOrchestrator(OfflineDeterministicTrudyModelClient(), tools)
        )

        val result = service.respondTo("How was my sleep?")

        assertTrue(result.toolCallsMade.isNotEmpty())
        assertTrue(result.toolCallsMade.all { it.operation.domains == listOf(HealthDomain.SLEEP) })
        assertTrue(result.evidenceReferences.any { it.domain == HealthDomain.SLEEP && it.metricId == "sleep_score" })
        assertTrue(result.answerText.contains("sleep", ignoreCase = true))
    }

    @Test
    fun localAdapterSeamDelegatesFormattedInputToSwappableEngine() = runTest {
        var seen: TrudyFormattedModelInput? = null
        val engine = object : LocalTrudyModelEngine {
            override val engineId: String = "fake-open-model"
            override suspend fun generate(input: TrudyFormattedModelInput): LocalTrudyModelResponse {
                seen = input
                return LocalTrudyModelResponse(responseText = "local answer")
            }
        }
        val client = LocalTrudyModelClient(engine)

        val result = client.complete(
            TrudyModelRequest(
                userRequest = "hello",
                conversationContext = listOf(TrudyConversationTurn(TrudyConversationRole.USER, "previous")),
                toolDefinitions = listOf(TrudyToolDefinition("get_domain_state", "state", listOf("domain")))
            )
        )

        assertEquals("local answer", result.responseText)
        assertEquals("local", result.metadata?.provider)
        assertEquals("fake-open-model", result.metadata?.model)
        assertTrue(seen?.conversation?.contains("USER: previous") == true)
        assertTrue(seen?.tools?.contains("get_domain_state") == true)
    }

    @Test
    fun promptFormatterBoundsConversationHistory() {
        val formatter = TrudyPromptFormatter(maxConversationTurns = 2, maxEvidenceRowsPerResult = 2)
        val request = TrudyModelRequest(
            userRequest = "question",
            conversationContext = listOf(
                TrudyConversationTurn(TrudyConversationRole.USER, "one"),
                TrudyConversationTurn(TrudyConversationRole.ASSISTANT, "two"),
                TrudyConversationTurn(TrudyConversationRole.USER, "three")
            )
        )

        val formatted = formatter.format(request)

        assertTrue("one" !in formatted.conversation)
        assertTrue("two" in formatted.conversation)
        assertTrue("three" in formatted.conversation)
    }
}

private class RuntimeFakePort(
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
