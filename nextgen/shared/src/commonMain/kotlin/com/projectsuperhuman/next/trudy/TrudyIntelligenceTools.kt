package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

data class GetPersonalTrend(val domain: HealthDomain, val metricId: String, val recentDays: Int = 7, val baselineDays: Int = 28) : TrudyToolOperation { override val domains = listOf(domain) }
data class CompareBaseline(val domain: HealthDomain, val metricId: String, val observationWindow: TrudyTimeRange, val baselineWindow: TrudyTimeRange) : TrudyToolOperation { override val domains = listOf(domain) }
data class GetAssociation(val leftDomain: HealthDomain, val leftMetricId: String, val rightDomain: HealthDomain, val rightMetricId: String, val method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, val alignmentWindowMs: Long = 21_600_000L) : TrudyToolOperation { override val domains = listOf(leftDomain,rightDomain).distinct() }
data class GetLaggedAssociation(val leftDomain: HealthDomain, val leftMetricId: String, val rightDomain: HealthDomain, val rightMetricId: String, val lagMs: Long, val method: TrudyAssociationMethod = TrudyAssociationMethod.PEARSON, val alignmentWindowMs: Long = 21_600_000L) : TrudyToolOperation { override val domains = listOf(leftDomain,rightDomain).distinct() }
data class GenerateExperimentHypothesis(val kind: TrudyExperimentKind, val targetDomain: HealthDomain, val targetMetricId: String) : TrudyToolOperation { override val domains = listOf(targetDomain) }
data class EvaluateExperiment(val hypothesis: TrudyExperimentHypothesis, val baselineWindow: TrudyTimeRange, val interventionWindow: TrudyTimeRange, val adherenceFraction: Double) : TrudyToolOperation { override val domains = listOf(hypothesis.targetDomain) }

data class PersonalTrendResult(override val operation: GetPersonalTrend, val comparison: TrudyBaselineComparison) : TrudyToolResult
data class BaselineComparisonResult(override val operation: CompareBaseline, val comparison: TrudyBaselineComparison) : TrudyToolResult
data class AssociationToolResult(override val operation: TrudyToolOperation, val association: TrudyAssociationResult) : TrudyToolResult {
    init { require(operation is GetAssociation || operation is GetLaggedAssociation); require(operation.domains == association.evidence.domains) }
}
data class ExperimentHypothesisResult(override val operation: GenerateExperimentHypothesis, val hypothesis: TrudyExperimentHypothesis) : TrudyToolResult
data class ExperimentEvaluationResult(override val operation: EvaluateExperiment, val result: TrudyExperimentResult) : TrudyToolResult

class TrudyIntelligenceToolService(private val library: TrudyPersonalEvidenceLibrary, private val source: TrudyPersonalEvidenceSource, private val experiments: TrudyExperimentEngine = TrudyExperimentEngine()) : TrudyToolExecutor {
    override val definitions = listOf(
        TrudyToolDefinition("get_personal_trend","Compare one domain-qualified metric's recent window with its prior baseline.",listOf("domain","metricId")),
        TrudyToolDefinition("compare_baseline","Compare two explicit windows for one domain-qualified metric.",listOf("domain","metricId","observationWindow","baselineWindow")),
        TrudyToolDefinition("get_association","Calculate one bounded association between two domain-qualified metrics.",listOf("leftDomain","leftMetricId","rightDomain","rightMetricId")),
        TrudyToolDefinition("get_lagged_association","Calculate one association at one explicit lag; no lag search is performed.",listOf("leftDomain","leftMetricId","rightDomain","rightMetricId","lagMs")),
        TrudyToolDefinition("generate_experiment_hypothesis","Create a safe lifestyle experiment hypothesis from an allowed preset.",listOf("kind","targetDomain","targetMetricId")),
        TrudyToolDefinition("evaluate_experiment","Evaluate explicit baseline/intervention windows for a structured hypothesis.",listOf("hypothesis","baselineWindow","interventionWindow","adherenceFraction"))
    )
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult = try {
        when(operation) {
            is GetPersonalTrend -> PersonalTrendResult(operation,library.recentTrend(operation.domain,operation.metricId,operation.recentDays,operation.baselineDays))
            is CompareBaseline -> BaselineComparisonResult(operation,library.compareBaseline(operation.domain,operation.metricId,operation.observationWindow,operation.baselineWindow))
            is GetAssociation -> AssociationToolResult(operation,library.association(operation.leftDomain,operation.leftMetricId,operation.rightDomain,operation.rightMetricId,operation.method,0,operation.alignmentWindowMs))
            is GetLaggedAssociation -> { require(operation.lagMs in 0..604_800_000L); AssociationToolResult(operation,library.association(operation.leftDomain,operation.leftMetricId,operation.rightDomain,operation.rightMetricId,operation.method,operation.lagMs,operation.alignmentWindowMs)) }
            is GenerateExperimentHypothesis -> ExperimentHypothesisResult(operation,experiments.plan(operation.kind,operation.targetDomain,operation.targetMetricId))
            is EvaluateExperiment -> { require(operation.adherenceFraction in 0.0..1.0); val d=operation.hypothesis.targetDomain; val m=operation.hypothesis.targetMetricId; val all=source.metricHistory(d,m,1000); require(all.all { it.domain==d && it.metricId==m }); ExperimentEvaluationResult(operation,experiments.evaluate(operation.hypothesis,all.filter { it.timestampEpochMs in operation.baselineWindow.fromEpochMs..operation.baselineWindow.toEpochMs },all.filter { it.timestampEpochMs in operation.interventionWindow.fromEpochMs..operation.interventionWindow.toEpochMs },operation.adherenceFraction)) }
            else -> TrudyToolResult.Failure(operation,TrudyToolFailureCode.UNSUPPORTED_OPERATION,"Operation is not an intelligence-layer tool.")
        }
    } catch(e:IllegalArgumentException) { TrudyToolResult.Failure(operation,TrudyToolFailureCode.MALFORMED_REQUEST,e.message ?: "Malformed intelligence request") }
}

class CompositeTrudyToolExecutor(private val delegates: List<TrudyToolExecutor>) : TrudyToolExecutor {
    init { require(delegates.isNotEmpty()) }
    override val definitions = delegates.flatMap { it.definitions }.distinctBy { it.name }
    override suspend fun execute(operation: TrudyToolOperation): TrudyToolResult {
        val intelligence = operation is GetPersonalTrend || operation is CompareBaseline || operation is GetAssociation || operation is GetLaggedAssociation || operation is GenerateExperimentHypothesis || operation is EvaluateExperiment
        val owner = delegates.firstOrNull { d -> d.definitions.any { it.name.startsWith(if(intelligence) "get_personal" else "get_domain") || (intelligence && it.name in setOf("compare_baseline","get_association","get_lagged_association","generate_experiment_hypothesis","evaluate_experiment")) } }
        return owner?.execute(operation) ?: TrudyToolResult.Failure(operation,TrudyToolFailureCode.UNSUPPORTED_OPERATION,"No registered Trudy executor owns this operation.")
    }
}
