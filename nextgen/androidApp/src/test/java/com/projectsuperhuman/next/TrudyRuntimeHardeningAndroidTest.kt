package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyConversationService
import com.projectsuperhuman.next.trudy.TrudyModelMetadata
import com.projectsuperhuman.next.trudy.TrudyModelRequest
import com.projectsuperhuman.next.trudy.TrudyModelResult
import com.projectsuperhuman.next.trudy.TrudyModelRuntimeMode
import com.projectsuperhuman.next.trudy.TrudyOrchestrator
import com.projectsuperhuman.next.trudy.TrudyToolDefinition
import com.projectsuperhuman.next.trudy.TrudyToolExecutor
import com.projectsuperhuman.next.trudy.TrudyToolFailureCode
import com.projectsuperhuman.next.trudy.TrudyToolOperation
import com.projectsuperhuman.next.trudy.TrudyToolResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrudyRuntimeHardeningAndroidTest {
    @Test fun deterministicDiagnosticsMatchTheIntegratedIntelligenceModel() {
        val selection = TrudyRuntimeFactory.selectModel(
            TrudyRuntimeConfig(mode = TrudyModelRuntimeMode.DETERMINISTIC),
            localEngine = null,
            hostedTransport = null,
            hostedCredentialProvider = { null }
        )
        assertEquals("trudy-deterministic-v2-intelligence", selection.diagnostics.modelId)
        assertFalse(selection.diagnostics.fallbackUsed)
    }

    @Test fun hostedTransportFailureFallsBackThroughOrchestratorWithoutCrash() = runTest {
        val transport = object : HostedTrudyTransport {
            override suspend fun complete(settings: HostedTrudyModelSettings, credential: String, input: com.projectsuperhuman.next.trudy.TrudyFormattedModelInput): HostedTrudyModelResponse {
                error("network failure")
            }
        }
        val selection = TrudyRuntimeFactory.selectModel(
            TrudyRuntimeConfig(
                mode = TrudyModelRuntimeMode.HOSTED,
                hostedProviderId = "test-provider",
                hostedModelId = "test-model",
                hostedEndpoint = "https://example.invalid/model"
            ),
            localEngine = null,
            hostedTransport = transport,
            hostedCredentialProvider = { "injected-test-credential" }
        )
        val result = TrudyConversationService(TrudyOrchestrator(selection.client, NoAndroidTools)).respondTo("hello")
        assertTrue(result.isFallback)
        assertTrue(result.warnings.any { it.kind.name == "MODEL_FAILURE" })
    }

    @Test fun adapterDiagnosticsContainOperationalMetadataButNotConversationOrEvidencePayload() = runTest {
        val privateQuestion = "private conversation phrase"
        val privateValue = "987654321"
        val model = object : com.projectsuperhuman.next.trudy.TrudyModelClient {
            override suspend fun complete(request: TrudyModelRequest) = TrudyModelResult(
                responseText = "ok",
                metadata = TrudyModelMetadata(provider = "provider-id", model = "model-id")
            )
        }
        val adapter = SharedTrudyBackendAdapter(
            TrudyConversationService(TrudyOrchestrator(model, NoAndroidTools)),
            TrudyBackendRuntimeInfo("DETERMINISTIC", "provider-id", "model-id", false)
        )
        val result = adapter.send(TrudyConversationRequest(privateQuestion))
        val activity = result.activity?.label.orEmpty()
        assertTrue(activity.contains("provider-id"))
        assertTrue(activity.contains("model-id"))
        assertTrue(activity.contains("tools 0/0"))
        assertFalse(activity.contains(privateQuestion))
        assertFalse(activity.contains(privateValue))
        assertFalse(activity.lowercase().contains("credential"))
    }
}

private object NoAndroidTools : TrudyToolExecutor {
    override val definitions: List<TrudyToolDefinition> = emptyList()
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult =
        TrudyToolResult.Failure(operation, TrudyToolFailureCode.UNSUPPORTED_OPERATION, "none")
}
