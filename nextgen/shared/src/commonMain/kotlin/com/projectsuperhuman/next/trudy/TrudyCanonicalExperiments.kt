package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

enum class TrudyCanonicalExperimentStatus { DRAFT, ACTIVE, COMPLETED, ANY }
enum class TrudyExperimentPersistenceState { AVAILABLE, NOT_CONNECTED }

data class TrudyCanonicalExperiment(
    val id: String,
    val title: String,
    val hypothesis: String,
    val intervention: String,
    val status: TrudyCanonicalExperimentStatus,
    val targetDomain: HealthDomain,
    val targetMetricId: String,
    val secondaryMetrics: List<Pair<HealthDomain, String>> = emptyList(),
    val baselineWindow: TrudyTimeRange,
    val interventionWindow: TrudyTimeRange,
    val adherenceFraction: Double? = null,
    val source: String,
    val updatedEpochMs: Long
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && source.isNotBlank())
        require(targetMetricId.isNotBlank())
        require(adherenceFraction == null || adherenceFraction.isFinite() && adherenceFraction in 0.0..1.0)
    }
}

/**
 * Future persistence engine boundary. The current preview-only Experiments UI must not implement
 * this contract with MockExperimentData.
 */
interface TrudyCanonicalExperimentRepository {
    val persistenceState: TrudyExperimentPersistenceState
    suspend fun list(status: TrudyCanonicalExperimentStatus = TrudyCanonicalExperimentStatus.ANY): List<TrudyCanonicalExperiment>
    suspend fun get(id: String): TrudyCanonicalExperiment?
}

object EmptyTrudyCanonicalExperimentRepository : TrudyCanonicalExperimentRepository {
    override val persistenceState = TrudyExperimentPersistenceState.NOT_CONNECTED
    override suspend fun list(status: TrudyCanonicalExperimentStatus): List<TrudyCanonicalExperiment> = emptyList()
    override suspend fun get(id: String): TrudyCanonicalExperiment? = null
}

data class GetCanonicalExperiments(
    val status: TrudyCanonicalExperimentStatus = TrudyCanonicalExperimentStatus.ANY,
    val limit: Int = 10
) : TrudyToolOperation {
    override val domains: List<HealthDomain> = emptyList()
}

data class EvaluateCanonicalExperiment(
    val experimentId: String? = null
) : TrudyToolOperation {
    override val domains: List<HealthDomain> = emptyList()
}

data class CanonicalExperimentsResult(
    override val operation: GetCanonicalExperiments,
    val persistenceState: TrudyExperimentPersistenceState,
    val experiments: List<TrudyCanonicalExperiment>
) : TrudyToolResult

data class CanonicalExperimentEvaluationResult(
    override val operation: EvaluateCanonicalExperiment,
    val persistenceState: TrudyExperimentPersistenceState,
    val experiment: TrudyCanonicalExperiment?,
    val evaluation: TrudyExperimentResult?
) : TrudyToolResult

class TrudyCanonicalExperimentToolService(
    private val repository: TrudyCanonicalExperimentRepository,
    private val source: TrudyPersonalEvidenceSource,
    private val engine: TrudyExperimentEngine
) {
    suspend fun list(operation: GetCanonicalExperiments): CanonicalExperimentsResult {
        val experiments = repository.list(operation.status)
            .sortedByDescending { it.updatedEpochMs }
            .take(operation.limit.coerceIn(1, MAX_EXPERIMENTS))
        return CanonicalExperimentsResult(operation, repository.persistenceState, experiments)
    }

    suspend fun evaluate(operation: EvaluateCanonicalExperiment): CanonicalExperimentEvaluationResult {
        val record = operation.experimentId?.let { repository.get(it) }
            ?: repository.list(TrudyCanonicalExperimentStatus.ACTIVE).maxByOrNull { it.updatedEpochMs }
            ?: repository.list(TrudyCanonicalExperimentStatus.COMPLETED).maxByOrNull { it.updatedEpochMs }
        if (record == null) {
            return CanonicalExperimentEvaluationResult(operation, repository.persistenceState, null, null)
        }
        val hypothesis = TrudyExperimentHypothesis(
            id = record.id,
            hypothesis = record.hypothesis,
            intervention = record.intervention,
            targetDomain = record.targetDomain,
            targetMetricId = record.targetMetricId,
            secondaryMetrics = record.secondaryMetrics,
            baselineWindowDays = daysIn(record.baselineWindow),
            interventionWindowDays = daysIn(record.interventionWindow),
            expectedDirection = TrudyEffectDirection.UNKNOWN,
            suggestedDurationDays = daysIn(record.interventionWindow),
            confounders = emptyList(),
            safetyNotes = emptyList(),
            evidenceBasis = emptyList(),
            expectedGain = TrudyExpectedGain(
                direction = TrudyEffectDirection.UNKNOWN,
                evidenceStrength = TrudyConfidence.INSUFFICIENT,
                uncertainty = "No expected gain is inferred from a stored protocol.",
                rationale = "Evaluation uses only the recorded baseline and intervention windows."
            )
        )
        val baseline = source.metricWindow(record.targetDomain, record.targetMetricId, record.baselineWindow, MAX_METRIC_ROWS)
        val intervention = source.metricWindow(record.targetDomain, record.targetMetricId, record.interventionWindow, MAX_METRIC_ROWS)
        val evaluation = engine.evaluate(
            hypothesis = hypothesis,
            baseline = baseline,
            intervention = intervention,
            adherenceFraction = record.adherenceFraction ?: 0.0
        )
        return CanonicalExperimentEvaluationResult(operation, repository.persistenceState, record, evaluation)
    }

    private fun daysIn(range: TrudyTimeRange): Int =
        (((range.toEpochMs - range.fromEpochMs).coerceAtLeast(0L) / DAY_MS) + 1L)
            .coerceIn(1L, 3_650L).toInt()

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_EXPERIMENTS = 50
        const val MAX_METRIC_ROWS = 1_000
    }
}
