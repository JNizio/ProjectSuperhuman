package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.ModuleParityService
import com.projectsuperhuman.next.trudy.CompositeTrudyToolExecutor
import com.projectsuperhuman.next.trudy.EmptyTrudyCanonicalExperimentRepository
import com.projectsuperhuman.next.trudy.HealthContextPersonalEvidenceSource
import com.projectsuperhuman.next.trudy.HybridTrudyModelClient
import com.projectsuperhuman.next.trudy.LocalTrudyModelClient
import com.projectsuperhuman.next.trudy.LocalTrudyModelEngine
import com.projectsuperhuman.next.trudy.OfflineDeterministicTrudyModelClient
import com.projectsuperhuman.next.trudy.TrudyAnswerEngineModelClient
import com.projectsuperhuman.next.trudy.TrudyAnswerTurnRegistry
import com.projectsuperhuman.next.trudy.TrudyCachedPersonalEvidenceSource
import com.projectsuperhuman.next.trudy.TrudyCanonicalExperimentRepository
import com.projectsuperhuman.next.trudy.TrudyConversationAwarePreflightPlanner
import com.projectsuperhuman.next.trudy.TrudyConversationPlanningContextHolder
import com.projectsuperhuman.next.trudy.TrudyConversationService
import com.projectsuperhuman.next.trudy.TrudyHealthContextService
import com.projectsuperhuman.next.trudy.TrudyHealthToolService
import com.projectsuperhuman.next.trudy.TrudyIntelligenceToolService
import com.projectsuperhuman.next.trudy.TrudyKnowledgeCoordinator
import com.projectsuperhuman.next.trudy.TrudyKnowledgeSource
import com.projectsuperhuman.next.trudy.TrudyLanguageAwarePreflightPlanner
import com.projectsuperhuman.next.trudy.TrudyModelClient
import com.projectsuperhuman.next.trudy.TrudyModelRuntimeMode
import com.projectsuperhuman.next.trudy.TrudyOrchestrator
import com.projectsuperhuman.next.trudy.TrudyPersonalEvidenceLibrary
import com.projectsuperhuman.next.trudy.TrudySystemInvestigationPlanner
import com.projectsuperhuman.next.trudy.TrudyTemporalBoundaryProvider
import com.projectsuperhuman.next.trudy.TrudyTemporalPlanningDecorator
import com.projectsuperhuman.next.trudy.TrudyTemporalResolver
import com.projectsuperhuman.next.trudy.defaultTrudyKnowledgeSources
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationEvidenceCoordinator
import com.projectsuperhuman.next.trudy.medical.CuratedMedicalManagementRepository
import com.projectsuperhuman.next.trudy.medical.EmptyMedicalConditionCandidateProvider
import com.projectsuperhuman.next.trudy.medical.MedicalContextAwareTrudyModelClient
import com.projectsuperhuman.next.trudy.medical.TrudyMedicalContextPlanner
import com.projectsuperhuman.next.trudy.withEmotionalReasoning
import com.projectsuperhuman.next.trudy.withEnvironmentalReasoning

/** Android configuration is read once at composition time; business logic never reads BuildConfig. */
data class TrudyRuntimeConfig(
    val mode: TrudyModelRuntimeMode = TrudyModelRuntimeMode.DETERMINISTIC,
    val hostedProviderId: String = "gemini",
    val hostedModelId: String = "gemini-3.5-flash",
    val hostedEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models",
    val localModelId: String = "local"
) {
    companion object {
        fun fromBuildConfig(): TrudyRuntimeConfig = TrudyRuntimeConfig(
            mode = runCatching { TrudyModelRuntimeMode.valueOf(BuildConfig.TRUDY_RUNTIME_MODE.trim().uppercase()) }
                .getOrDefault(TrudyModelRuntimeMode.DETERMINISTIC),
            hostedProviderId = BuildConfig.TRUDY_HOSTED_PROVIDER_ID,
            hostedModelId = BuildConfig.TRUDY_HOSTED_MODEL_ID,
            hostedEndpoint = BuildConfig.TRUDY_HOSTED_ENDPOINT,
            localModelId = BuildConfig.TRUDY_LOCAL_MODEL_ID
        )
    }
}

data class TrudyRuntimeDiagnostics(
    val requestedMode: TrudyModelRuntimeMode,
    val activeMode: TrudyModelRuntimeMode,
    val providerId: String,
    val modelId: String,
    val fallbackUsed: Boolean,
    val fallbackReason: String? = null
)

data class TrudyRuntime(
    val controller: TrudyConversationController,
    val diagnostics: TrudyRuntimeDiagnostics
)

internal data class TrudyModelSelection(
    val client: TrudyModelClient,
    val diagnostics: TrudyRuntimeDiagnostics
)

internal object TrudyRuntimeFactory {
    fun create(
        config: TrudyRuntimeConfig = TrudyRuntimeConfig.fromBuildConfig(),
        localEngine: LocalTrudyModelEngine? = null,
        hostedTransport: HostedTrudyTransport? = null,
        hostedCredentialProvider: () -> String? = {
            BuildConfig.TRUDY_GEMINI_API_KEY.takeIf { it.isNotBlank() }
        },
        appContext: Context? = null,
        experimentRepository: TrudyCanonicalExperimentRepository = EmptyTrudyCanonicalExperimentRepository,
        knowledgeSources: List<TrudyKnowledgeSource> = defaultTrudyKnowledgeSources(),
        temporalBoundaries: TrudyTemporalBoundaryProvider = AndroidTrudyTemporalBoundaryProvider()
    ): TrudyRuntime {
        val effectiveHostedTransport = hostedTransport ?: when {
            config.hostedProviderId.equals("gemini", ignoreCase = true) -> GeminiHostedTrudyTransport()
            else -> null
        }
        val selection = runCatching {
            selectModel(config, localEngine, effectiveHostedTransport, hostedCredentialProvider)
        }.getOrElse {
            deterministicSelection(config.mode, "Configured model runtime could not be initialized.")
        }

        return try {
            val parity = ModuleParityService(
                modulePort = { domain -> NativeDataHub.module(domain) },
                nowEpochMs = { System.currentTimeMillis() }
            )
            val context = TrudyHealthContextService(parity)
            val healthTools = TrudyHealthToolService(context)
            val evidenceSource = TrudyCachedPersonalEvidenceSource(
                HealthContextPersonalEvidenceSource(context)
            )
            val intelligenceTools = TrudyIntelligenceToolService(
                library = TrudyPersonalEvidenceLibrary(evidenceSource),
                source = evidenceSource,
                experimentRepository = experimentRepository
            )
            val tools = CompositeTrudyToolExecutor(
                healthExecutor = healthTools,
                intelligenceExecutor = intelligenceTools
            )

            val answerTurnRegistry = TrudyAnswerTurnRegistry()
            val planningContextHolder = TrudyConversationPlanningContextHolder()

            // Ordering is intentional: the Answer Engine is innermost, so Emotional, Environmental
            // and Medical decorators enrich the request before final synthesis/safety filtering.
            val answerClient = TrudyAnswerEngineModelClient(
                delegate = selection.client,
                registry = answerTurnRegistry
            )
            val domainReasoningClient = answerClient.withEmotionalReasoning().withEnvironmentalReasoning()
            val candidateProvider = appContext?.let { AndroidMedicalCorpusCandidateProvider(it) }
                ?: EmptyMedicalConditionCandidateProvider
            val medicalPlanner = TrudyMedicalContextPlanner(
                knowledge = CuratedMedicalManagementRepository(),
                conditionCandidates = candidateProvider
            )
            val reasoningClient = MedicalContextAwareTrudyModelClient(
                delegate = domainReasoningClient,
                planner = medicalPlanner
            )

            val investigationPlanner = TrudySystemInvestigationPlanner(TrudyTemporalResolver(temporalBoundaries))
            val languagePlanner = TrudyLanguageAwarePreflightPlanner(investigationPlanner)
            val conversationPlanner = TrudyConversationAwarePreflightPlanner(languagePlanner, planningContextHolder)
            // Temporal retargeting is outermost so only time language the user actually typed is
            // considered explicit. Conversation planning may add helpful labels internally, but
            // those labels must not be mistaken for a user-specified window.
            val temporalPlanner = TrudyTemporalPlanningDecorator(conversationPlanner, temporalBoundaries)
            val orchestrator = TrudyOrchestrator(
                modelClient = reasoningClient,
                tools = tools,
                preflightPlanner = temporalPlanner,
                knowledgeCoordinator = TrudyKnowledgeCoordinator(knowledgeSources)
            )
            val service = TrudyConversationService(
                orchestrator = orchestrator,
                conversationEvidence = TrudyConversationEvidenceCoordinator(temporalBoundaries),
                planningContextHolder = planningContextHolder,
                answerTurnRegistry = answerTurnRegistry
            )
            val backend = SharedTrudyBackendAdapter(
                service = service,
                runtimeInfo = TrudyBackendRuntimeInfo(
                    mode = selection.diagnostics.activeMode.name,
                    providerId = selection.diagnostics.providerId,
                    modelId = selection.diagnostics.modelId,
                    startupFallbackUsed = selection.diagnostics.fallbackUsed
                )
            )
            TrudyRuntime(
                controller = SharedTrudyConversationController(backend),
                diagnostics = selection.diagnostics
            )
        } catch (_: Throwable) {
            deterministicControllerRuntime(
                requestedMode = config.mode,
                reason = "Trudy application runtime initialization failed."
            )
        }
    }

    internal fun selectModel(
        config: TrudyRuntimeConfig,
        localEngine: LocalTrudyModelEngine?,
        hostedTransport: HostedTrudyTransport?,
        hostedCredentialProvider: () -> String?
    ): TrudyModelSelection = when (config.mode) {
        TrudyModelRuntimeMode.DETERMINISTIC -> deterministicSelection(config.mode)

        TrudyModelRuntimeMode.LOCAL -> {
            if (localEngine == null) {
                deterministicSelection(config.mode, "No local inference engine is installed.")
            } else {
                val engineId = localEngine.engineId.ifBlank { config.localModelId }
                TrudyModelSelection(
                    client = LocalTrudyModelClient(localEngine),
                    diagnostics = TrudyRuntimeDiagnostics(
                        requestedMode = config.mode,
                        activeMode = TrudyModelRuntimeMode.LOCAL,
                        providerId = "local",
                        modelId = engineId,
                        fallbackUsed = false
                    )
                )
            }
        }

        TrudyModelRuntimeMode.HOSTED -> {
            val credential = runCatching { hostedCredentialProvider() }.getOrNull()
            val invalid = when {
                hostedTransport == null -> "No hosted transport is installed."
                config.hostedModelId.isBlank() -> "Hosted model ID is not configured."
                !config.hostedEndpoint.startsWith("https://") -> "Hosted endpoint is missing or not HTTPS."
                credential.isNullOrBlank() -> "Hosted credential is unavailable."
                else -> null
            }
            if (invalid != null) {
                deterministicSelection(config.mode, invalid)
            } else {
                val settings = HostedTrudyModelSettings(
                    providerId = config.hostedProviderId.ifBlank { "hosted" },
                    modelId = config.hostedModelId,
                    endpoint = config.hostedEndpoint
                )
                val hostedClient = HostedTrudyModelClient(
                    settings = settings,
                    credentialProvider = { credential },
                    transport = hostedTransport!!
                )
                TrudyModelSelection(
                    client = HybridTrudyModelClient(hostedClient),
                    diagnostics = TrudyRuntimeDiagnostics(
                        requestedMode = config.mode,
                        activeMode = TrudyModelRuntimeMode.HOSTED,
                        providerId = settings.providerId,
                        modelId = settings.modelId,
                        fallbackUsed = false
                    )
                )
            }
        }
    }

    private fun deterministicSelection(
        requestedMode: TrudyModelRuntimeMode,
        reason: String? = null
    ) = TrudyModelSelection(
        client = OfflineDeterministicTrudyModelClient(),
        diagnostics = TrudyRuntimeDiagnostics(
            requestedMode = requestedMode,
            activeMode = TrudyModelRuntimeMode.DETERMINISTIC,
            providerId = "offline",
            modelId = DETERMINISTIC_MODEL_ID,
            fallbackUsed = requestedMode != TrudyModelRuntimeMode.DETERMINISTIC || reason != null,
            fallbackReason = reason
        )
    )

    private fun deterministicControllerRuntime(
        requestedMode: TrudyModelRuntimeMode,
        reason: String
    ): TrudyRuntime = TrudyRuntime(
        controller = LocalTrudyConversationController(),
        diagnostics = TrudyRuntimeDiagnostics(
            requestedMode = requestedMode,
            activeMode = TrudyModelRuntimeMode.DETERMINISTIC,
            providerId = "local-controller",
            modelId = "local-safe-fallback",
            fallbackUsed = true,
            fallbackReason = reason
        )
    )

    private const val DETERMINISTIC_MODEL_ID = "trudy-deterministic-v3-quality"
}
