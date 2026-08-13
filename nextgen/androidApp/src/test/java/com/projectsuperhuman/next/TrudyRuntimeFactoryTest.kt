package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.LocalTrudyModelEngine
import com.projectsuperhuman.next.trudy.LocalTrudyModelResponse
import com.projectsuperhuman.next.trudy.TrudyConversationRole
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyFormattedModelInput
import com.projectsuperhuman.next.trudy.TrudyModelClient
import com.projectsuperhuman.next.trudy.TrudyModelRequest
import com.projectsuperhuman.next.trudy.TrudyModelResult
import com.projectsuperhuman.next.trudy.TrudyModelRuntimeMode
import com.projectsuperhuman.next.trudy.TrudyOrchestrator
import com.projectsuperhuman.next.trudy.TrudyToolDefinition
import com.projectsuperhuman.next.trudy.TrudyToolExecutor
import com.projectsuperhuman.next.trudy.TrudyToolFailureCode
import com.projectsuperhuman.next.trudy.TrudyToolOperation
import com.projectsuperhuman.next.trudy.TrudyToolResult
import com.projectsuperhuman.next.trudy.TrudyMetricEvidence
import com.projectsuperhuman.next.trudy.TrudyConversationService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrudyRuntimeFactoryTest {
    @Test
    fun deterministicModeIsSelectedWithoutProviderDependencies() {
        val selection = TrudyRuntimeFactory.selectModel(
            config = TrudyRuntimeConfig(mode = TrudyModelRuntimeMode.DETERMINISTIC),
            localEngine = null,
            hostedTransport = null,
            hostedCredentialProvider = { null }
        )

        assertEquals(TrudyModelRuntimeMode.DETERMINISTIC, selection.diagnostics.activeMode)
        assertFalse(selection.diagnostics.fallbackUsed)
        assertEquals("offline", selection.diagnostics.providerId)
    }

    @Test
    fun localModeUsesSwappableEngine() = runTest {
        val engine = object : LocalTrudyModelEngine {
            override val engineId = "fake-local-engine"
            override suspend fun generate(input: TrudyFormattedModelInput) =
                LocalTrudyModelResponse(responseText = "local")
        }
        val selection = TrudyRuntimeFactory.selectModel(
            config = TrudyRuntimeConfig(mode = TrudyModelRuntimeMode.LOCAL),
            localEngine = engine,
            hostedTransport = null,
            hostedCredentialProvider = { null }
        )

        assertEquals(TrudyModelRuntimeMode.LOCAL, selection.diagnostics.activeMode)
        assertEquals("fake-local-engine", selection.diagnostics.modelId)
        assertEquals("local", selection.client.complete(TrudyModelRequest("hello")).responseText)
    }

    @Test
    fun hostedModeWithoutCredentialFallsBackBeforeAnyNetworkCall() {
        var transportCalled = false
        val transport = object : HostedTrudyTransport {
            override suspend fun complete(
                settings: HostedTrudyModelSettings,
                credential: String,
                input: TrudyFormattedModelInput
            ): HostedTrudyModelResponse {
                transportCalled = true
                return HostedTrudyModelResponse(responseText = "unexpected")
            }
        }
        val selection = TrudyRuntimeFactory.selectModel(
            config = TrudyRuntimeConfig(
                mode = TrudyModelRuntimeMode.HOSTED,
                hostedProviderId = "test",
                hostedModelId = "model",
                hostedEndpoint = "https://example.invalid/model"
            ),
            localEngine = null,
            hostedTransport = transport,
            hostedCredentialProvider = { null }
        )

        assertEquals(TrudyModelRuntimeMode.DETERMINISTIC, selection.diagnostics.activeMode)
        assertTrue(selection.diagnostics.fallbackUsed)
        assertFalse(transportCalled)
    }

    @Test
    fun hostedClientRejectsMissingCredential() = runTest {
        val transport = object : HostedTrudyTransport {
            override suspend fun complete(
                settings: HostedTrudyModelSettings,
                credential: String,
                input: TrudyFormattedModelInput
            ) = HostedTrudyModelResponse(responseText = "unused")
        }
        val client = HostedTrudyModelClient(
            HostedTrudyModelSettings("test", "model", "https://example.invalid/model"),
            credentialProvider = { null },
            transport = transport
        )

        assertFailsWith<HostedTrudyConfigurationException> {
            client.complete(TrudyModelRequest("hello"))
        }
    }

    @Test
    fun runtimeFallsBackWhenConfiguredModelInitializationFails() {
        val brokenEngine = object : LocalTrudyModelEngine {
            override val engineId: String
                get() = error("engine init failed")

            override suspend fun generate(input: TrudyFormattedModelInput) =
                LocalTrudyModelResponse(responseText = "unreachable")
        }

        val runtime = TrudyRuntimeFactory.create(
            config = TrudyRuntimeConfig(mode = TrudyModelRuntimeMode.LOCAL),
            localEngine = brokenEngine
        )

        assertTrue(runtime.diagnostics.fallbackUsed)
        assertEquals(TrudyModelRuntimeMode.DETERMINISTIC, runtime.diagnostics.activeMode)
        assertIs<SharedTrudyConversationController>(runtime.controller)
    }

    @Test
    fun adapterMapsOnlyNonBlankHistoryIntoSharedRoles() = runTest {
        var captured: TrudyModelRequest? = null
        val model = object : TrudyModelClient {
            override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
                captured = request
                return TrudyModelResult(responseText = "ok")
            }
        }
        val service = TrudyConversationService(TrudyOrchestrator(model, EmptyTools))
        val adapter = SharedTrudyBackendAdapter(service)

        adapter.send(
            TrudyConversationRequest(
                text = "question",
                history = listOf(
                    TrudyConversationTurn(TrudyMessageRole.USER, "first"),
                    TrudyConversationTurn(TrudyMessageRole.TRUDY, "answer"),
                    TrudyConversationTurn(TrudyMessageRole.TRUDY, "   ")
                )
            )
        )

        assertEquals(2, captured!!.conversationContext.size)
        assertEquals(TrudyConversationRole.USER, captured!!.conversationContext[0].role)
        assertEquals(TrudyConversationRole.ASSISTANT, captured!!.conversationContext[1].role)
    }

    @Test
    fun adapterPreservesDomainQualifiedEvidenceMapping() = runTest {
        var call = 0
        val ref = TrudyEvidenceReference(
            domain = HealthDomain.SLEEP,
            metricId = "sleep_score",
            evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION,
            timestampEpochMs = 123L
        )
        val model = object : TrudyModelClient {
            override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
                call++
                return if (call == 1) {
                    TrudyModelResult(requestedTools = listOf(TrudyToolOperation.GetDomainState(HealthDomain.SLEEP)))
                } else {
                    TrudyModelResult(responseText = "sleep", evidenceReferences = listOf(ref))
                }
            }
        }
        val tool = object : TrudyToolExecutor {
            override val definitions = listOf(TrudyToolDefinition("get_domain_state", "state", listOf("domain")))
            override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult =
                TrudyToolResult.DomainState(
                    operation as TrudyToolOperation.GetDomainState,
                    listOf(
                        TrudyMetricEvidence(
                            domain = HealthDomain.SLEEP,
                            metricId = "sleep_score",
                            value = 80.0,
                            unit = "score",
                            timestampEpochMs = 123L,
                            source = "test"
                        )
                    )
                )
        }
        val adapter = SharedTrudyBackendAdapter(
            TrudyConversationService(TrudyOrchestrator(model, tool))
        )

        val result = adapter.send(TrudyConversationRequest("sleep"))

        assertEquals(1, result.evidence.size)
        assertTrue(result.evidence.single().id.startsWith("SLEEP:sleep_score"))
        assertEquals(TrudyBackendEvidenceKind.METRIC, result.evidence.single().kind)
    }

    @Test
    fun controllerBoundaryDoesNotExposeSharedHealthOrRepositoryTypes() {
        val signatures = TrudyConversationController::class.java.methods.joinToString { it.toGenericString() }
        assertFalse(signatures.contains("com.projectsuperhuman.next.core"))
        assertFalse(signatures.contains("SqlHealthRepository"))
        assertFalse(signatures.contains("ModuleDataPort"))
    }
}

private object EmptyTools : TrudyToolExecutor {
    override val definitions: List<TrudyToolDefinition> = emptyList()
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult =
        TrudyToolResult.Failure(operation, TrudyToolFailureCode.UNSUPPORTED_OPERATION, "none")
}
