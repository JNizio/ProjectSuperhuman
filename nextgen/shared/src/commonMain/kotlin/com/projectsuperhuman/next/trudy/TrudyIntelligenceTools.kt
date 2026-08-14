package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

data class GetPersonalTrend(val domain: HealthDomain, val metricId: String, val recentDays: Int = 7, val baselineDays: Int = 28) : TrudyToolOperation { override val domains = listOf(domain) }
data class CompareBaseline(val domain: HealthDomain, val metricId: String, val observationWindow: TrudyTimeRange, val baselineWindow: TrudyTimeRange) : TrudyToolOperation { override val domains = listOf(domain) }
data class GetAssociation(val leftDomain: HealthDomain, val leftMetricId: String, val rightDomain: HealthDomain, val rightMetricId: String, val method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, val alignmentWindowMs: Long = 21_600_000L, val window: TrudyTimeRange? = null) : TrudyToolOperation { override val domains = listOf(leftDomain,rightDomain).distinct() }
data class GetLaggedAssociation(val leftDomain: HealthDomain, val leftMetricId: String, val rightDomain: HealthDomain, val rightMetricId: String, val lagMs: Long, val method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, val alignmentWindowMs: Long = 21_600_000L, val window: TrudyTimeRange? = null) : TrudyToolOperation { override val domains = listOf(leftDomain,rightDomain).distinct() }
data class GenerateExperimentHypothesis(val kind: TrudyExperimentKind, val targetDomain: HealthDomain, val targetMetricId: String) : TrudyToolOperation { override val domains = listOf(targetDomain) }
data class EvaluateExperiment(val hypothesis: TrudyExperimentHypothesis, val baselineWindow: TrudyTimeRange, val interventionWindow: TrudyTimeRange, val adherenceFraction: Double) : TrudyToolOperation { override val domains = listOf(hypothesis.targetDomain) }

data class PersonalTrendResult(override val operation: GetPersonalTrend, val comparison: TrudyBaselineComparison) : TrudyToolResult
data class BaselineComparisonResult(override val operation: CompareBaseline, val comparison: TrudyBaselineComparison) : TrudyToolResult
data class AssociationToolResult(override val operation: TrudyToolOperation, val association: TrudyAssociationResult) : TrudyToolResult {
    init { require(operation is GetAssociation || operation is GetLaggedAssociation); require(operation.domains == association.evidence.domains) }
}
data class ExperimentHypothesisResult(override val operation: GenerateExperimentHypothesis, val hypothesis: TrudyExperimentHypothesis) : TrudyToolResult
data class ExperimentEvaluationResult(override val operation: EvaluateExperiment, val result: TrudyExperimentResult) : TrudyToolResult

class TrudyIntelligenceToolService(
    private val library: TrudyPersonalEvidenceLibrary,
    private val source: TrudyPersonalEvidenceSource,
    private val experiments: TrudyExperimentEngine = TrudyExperimentEngine(),
    experimentRepository: TrudyCanonicalExperimentRepository = EmptyTrudyCanonicalExperimentRepository
) : TrudyToolExecutor {
    private val investigator = TrudyCrossDomainInvestigator(library, source)
    private val canonicalExperiments = TrudyCanonicalExperimentToolService(experimentRepository, source, experiments)
    override val definitions = listOf(
        TrudyToolDefinition("get_personal_trend","Compare one domain-qualified metric's recent window with its prior baseline.",listOf("domain","metricId")),
        TrudyToolDefinition("compare_baseline","Compare two explicit windows for one domain-qualified metric.",listOf("domain","metricId","observationWindow","baselineWindow")),
        TrudyToolDefinition("get_association","Calculate one bounded association between two domain-qualified metrics.",listOf("leftDomain","leftMetricId","rightDomain","rightMetricId")),
        TrudyToolDefinition("get_lagged_association","Calculate one association at one explicit lag; no lag search is performed.",listOf("leftDomain","leftMetricId","rightDomain","rightMetricId","lagMs")),
        TrudyToolDefinition("investigate_change","Verify a claimed change across explicit windows, then inspect a bounded set of related metrics.",listOf("targets","related","observationWindow","baselineWindow")),
        TrudyToolDefinition("generate_experiment_hypothesis","Create a safe lifestyle experiment hypothesis from an allowed preset.",listOf("kind","targetDomain","targetMetricId")),
        TrudyToolDefinition("evaluate_experiment","Evaluate explicit baseline/intervention windows for a structured hypothesis.",listOf("hypothesis","baselineWindow","interventionWindow","adherenceFraction")),
        TrudyToolDefinition("get_canonical_experiments","Read saved experiments only from the canonical persistence boundary.",listOf("status")),
        TrudyToolDefinition("evaluate_canonical_experiment","Compare saved baseline/intervention windows for a canonical experiment.",emptyList()),
        TrudyToolDefinition("get_system_availability","Report modules that do not yet have canonical persisted history.",listOf("moduleIds"))
    )
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = try {
        when(operation) {
            is GetPersonalTrend -> PersonalTrendResult(operation,library.recentTrend(operation.domain,operation.metricId,operation.recentDays,operation.baselineDays))
            is CompareBaseline -> BaselineComparisonResult(operation,library.compareBaseline(operation.domain,operation.metricId,operation.observationWindow,operation.baselineWindow))
            is GetAssociation -> AssociationToolResult(operation,library.association(operation.leftDomain,operation.leftMetricId,operation.rightDomain,operation.rightMetricId,operation.method,0,operation.alignmentWindowMs,window=operation.window))
            is GetLaggedAssociation -> { require(operation.lagMs in 0..MAX_LAG_MS); AssociationToolResult(operation,library.association(operation.leftDomain,operation.leftMetricId,operation.rightDomain,operation.rightMetricId,operation.method,operation.lagMs,operation.alignmentWindowMs,window=operation.window)) }
            is InvestigateChange -> ChangeInvestigationResult(operation, investigator.investigate(operation))
            is GenerateExperimentHypothesis -> ExperimentHypothesisResult(operation,experiments.plan(operation.kind,operation.targetDomain,operation.targetMetricId))
            is EvaluateExperiment -> { require(operation.adherenceFraction in 0.0..1.0); val d=operation.hypothesis.targetDomain; val m=operation.hypothesis.targetMetricId; val baseline=source.metricWindow(d,m,operation.baselineWindow,1000); val intervention=source.metricWindow(d,m,operation.interventionWindow,1000); require((baseline+intervention).all { it.domain==d && it.metricId==m }); ExperimentEvaluationResult(operation,experiments.evaluate(operation.hypothesis,baseline,intervention,operation.adherenceFraction)) }
            is GetCanonicalExperiments -> canonicalExperiments.list(operation)
            is EvaluateCanonicalExperiment -> canonicalExperiments.evaluate(operation)
            is GetSystemAvailability -> SystemAvailabilityResult(
                operation,
                TrudySystemCatalog.modules.filter { it.id in operation.moduleIds && !it.canonicalDataAvailable }
                    .associate { it.id to (it.unavailableReason ?: "Canonical history is not connected.") }
            )
            else -> TrudyToolResult.Failure(operation,TrudyToolFailureCode.UNSUPPORTED_OPERATION,"Operation is not an intelligence-layer tool.")
        }
    } catch(e:IllegalArgumentException) { TrudyToolResult.Failure(operation,TrudyToolFailureCode.MALFORMED_REQUEST,e.message ?: "Malformed intelligence request") }

    private companion object { const val MAX_LAG_MS = 604_800_000L }
}

/**
 * Explicit two-owner router for Trudy tools.
 *
 * Ownership is determined only from the sealed operation type, never from definition names or
 * string prefixes. One operation is dispatched to at most one executor.
 */
class CompositeTrudyToolExecutor(
    private val healthExecutor: TrudyToolExecutor,
    private val intelligenceExecutor: TrudyToolExecutor
) : TrudyToolExecutor {
    override val definitions: List<TrudyToolDefinition> =
        (healthExecutor.definitions + intelligenceExecutor.definitions).distinctBy { it.name }

    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = when (operation) {
        is TrudyToolOperation.GetDomainState,
        is TrudyToolOperation.GetMetricHistory,
        is TrudyToolOperation.GetMetricWindow,
        is TrudyToolOperation.GetDomainHistory,
        is TrudyToolOperation.GetDerivedFeatures,
        is TrudyToolOperation.GetInsights,
        is TrudyToolOperation.GetDataQuality,
        is TrudyToolOperation.GetContext -> healthExecutor.execute(operation)

        is GetPersonalTrend,
        is CompareBaseline,
        is GetAssociation,
        is GetLaggedAssociation,
        is InvestigateChange,
        is GenerateExperimentHypothesis,
        is EvaluateExperiment,
        is GetCanonicalExperiments,
        is EvaluateCanonicalExperiment,
        is GetSystemAvailability -> intelligenceExecutor.execute(operation)

        is TrudyToolOperation.Unsupported -> TrudyToolResult.Failure(
            operation,
            TrudyToolFailureCode.UNSUPPORTED_OPERATION,
            "No registered Trudy executor owns unsupported operation: ${operation.name}"
        )
    }
}
