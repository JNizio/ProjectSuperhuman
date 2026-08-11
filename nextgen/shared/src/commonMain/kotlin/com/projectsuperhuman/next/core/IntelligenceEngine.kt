package com.projectsuperhuman.next.core

import kotlin.math.abs

/**
 * Step 5 intelligence contracts.
 *
 * These objects are derived hypotheses, not raw health observations and not diagnoses.
 * Every finding carries the exact evidence windows used to produce it so UI and future
 * engines can explain where a claim came from and how strong the supporting data is.
 */
data class EvidenceWindow(
    val domain: HealthDomain,
    val metric: String,
    val fromEpochMs: Long,
    val toEpochMs: Long,
    val sampleCount: Long,
    val role: String
)

enum class FindingKind { TREND, RELATIONSHIP, INTERVENTION_EFFECT }
enum class FindingDirection { POSITIVE, NEGATIVE, RISING, FALLING, STABLE, MIXED, UNKNOWN }

data class InterpretationFinding(
    val id: String,
    val kind: FindingKind,
    val title: String,
    val explanation: String,
    val direction: FindingDirection,
    val confidence: Double,
    val evidence: List<EvidenceWindow>,
    val caveats: List<String>,
    val generatedEpochMs: Long,
    val engineVersion: String = ENGINE_VERSION
) {
    init {
        require(confidence in 0.0..1.0)
        require(evidence.isNotEmpty())
    }
}

/** Defines a user-observed intervention without assuming it is a treatment or cure. */
data class InterventionDefinition(
    val id: String,
    val name: String,
    val startedEpochMs: Long,
    val outcomeDomain: HealthDomain,
    val outcomeMetric: String,
    val baselineDurationMs: Long,
    val observationDurationMs: Long,
    val expectedDirection: OutcomeDirection = OutcomeDirection.UNKNOWN,
    val notes: String? = null
) {
    init {
        require(baselineDurationMs > 0L)
        require(observationDurationMs > 0L)
    }
}

enum class OutcomeDirection { HIGHER_IS_BETTER, LOWER_IS_BETTER, UNKNOWN }
enum class InterventionStatus { IMPROVED, WORSENED, NO_CLEAR_CHANGE, INSUFFICIENT_DATA }

data class InterventionEvaluation(
    val intervention: InterventionDefinition,
    val comparison: PeriodComparison,
    val status: InterventionStatus,
    val confidence: Double,
    val finding: InterpretationFinding
)

data class InsightCard(
    val finding: InterpretationFinding,
    val priority: Double,
    val shouldSurface: Boolean,
    val reason: String
)

/**
 * Conservative interpretation layer over Step 4. It never talks to SQL directly.
 * Relationship findings intentionally say "associated" rather than causal language.
 */
class InterpretationEngine(
    private val queries: HealthQueryEngine,
    private val nowEpochMs: () -> Long
) {
    suspend fun trend(window: MetricWindow): InterpretationFinding {
        val result = queries.trend(window)
        val confidence = trendConfidence(result)
        val direction = when (result.direction) {
            TrendDirection.RISING -> FindingDirection.RISING
            TrendDirection.FALLING -> FindingDirection.FALLING
            TrendDirection.STABLE -> FindingDirection.STABLE
            TrendDirection.INSUFFICIENT_DATA -> FindingDirection.UNKNOWN
        }
        return InterpretationFinding(
            id = stableFindingId("trend", listOf(window), nowEpochMs()),
            kind = FindingKind.TREND,
            title = "${window.metric} trend",
            explanation = when (result.direction) {
                TrendDirection.RISING -> "This metric has been trending upward across the selected window."
                TrendDirection.FALLING -> "This metric has been trending downward across the selected window."
                TrendDirection.STABLE -> "This metric has been relatively stable across the selected window."
                TrendDirection.INSUFFICIENT_DATA -> "There is not enough data yet to estimate a reliable trend."
            },
            direction = direction,
            confidence = confidence,
            evidence = listOf(window.evidence(result.summary.count, "trend-series")),
            caveats = buildList {
                if (result.summary.count < 5L) add("Limited number of observations")
                add("A trend describes recorded data and does not by itself explain why it changed")
            },
            generatedEpochMs = nowEpochMs()
        )
    }

    suspend fun relationship(
        left: MetricWindow,
        right: MetricWindow,
        toleranceMs: Long = 12L * 60L * 60L * 1000L
    ): InterpretationFinding {
        val correlation = queries.correlation(left, right, toleranceMs)
        val r = correlation.pearsonR
        val direction = when {
            r == null -> FindingDirection.UNKNOWN
            r > 0.0 -> FindingDirection.POSITIVE
            r < 0.0 -> FindingDirection.NEGATIVE
            else -> FindingDirection.STABLE
        }
        val confidence = relationshipConfidence(correlation)
        val associationText = when {
            r == null -> "There is not enough aligned data to estimate a relationship."
            r > 0.0 -> "Higher values of ${left.metric} have been associated with higher values of ${right.metric} in the aligned observations."
            r < 0.0 -> "Higher values of ${left.metric} have been associated with lower values of ${right.metric} in the aligned observations."
            else -> "No meaningful linear association was detected in the aligned observations."
        }
        return InterpretationFinding(
            id = stableFindingId("relationship", listOf(left, right), nowEpochMs()),
            kind = FindingKind.RELATIONSHIP,
            title = "${left.metric} and ${right.metric}",
            explanation = associationText,
            direction = direction,
            confidence = confidence,
            evidence = listOf(
                left.evidence(correlation.sampleCount.toLong(), "aligned-left"),
                right.evidence(correlation.sampleCount.toLong(), "aligned-right")
            ),
            caveats = listOf(
                "Association does not establish causation",
                "Other recorded or unrecorded factors may explain part or all of this relationship",
                "Wearable and self-reported measurements can contain error"
            ),
            generatedEpochMs = nowEpochMs()
        )
    }

    private fun trendConfidence(result: TrendResult): Double {
        val n = result.summary.count
        if (n < 2L || result.direction == TrendDirection.INSUFFICIENT_DATA) return 0.1
        val sampleFactor = (n.coerceAtMost(30L).toDouble() / 30.0)
        return (0.35 + sampleFactor * 0.5).coerceAtMost(0.85)
    }

    private fun relationshipConfidence(result: CorrelationResult): Double {
        val r = result.pearsonR ?: return 0.1
        val sampleFactor = (result.sampleCount.coerceAtMost(30).toDouble() / 30.0)
        // Deliberately capped below 0.9: observational correlation is never treated as certainty.
        return (abs(r) * 0.55 + sampleFactor * 0.3).coerceIn(0.1, 0.85)
    }
}

/** Evaluates a user-defined intervention using explicit before/after windows. */
class InterventionEngine(
    private val queries: HealthQueryEngine,
    private val nowEpochMs: () -> Long
) {
    suspend fun evaluate(definition: InterventionDefinition): InterventionEvaluation {
        val baseline = MetricWindow(
            definition.outcomeDomain,
            definition.outcomeMetric,
            definition.startedEpochMs - definition.baselineDurationMs,
            definition.startedEpochMs - 1L
        )
        val observationEnd = minOf(
            nowEpochMs(),
            definition.startedEpochMs + definition.observationDurationMs
        )
        val observation = MetricWindow(
            definition.outcomeDomain,
            definition.outcomeMetric,
            definition.startedEpochMs,
            observationEnd
        )
        val comparison = queries.comparePeriods(baseline, observation)
        val sufficient = comparison.previous.count >= MIN_PERIOD_SAMPLES &&
            comparison.current.count >= MIN_PERIOD_SAMPLES &&
            comparison.absoluteChange != null
        val status = if (!sufficient) {
            InterventionStatus.INSUFFICIENT_DATA
        } else {
            classify(definition.expectedDirection, comparison.percentChange ?: 0.0)
        }
        val confidence = if (!sufficient) 0.1 else {
            val n = minOf(comparison.previous.count, comparison.current.count)
            (0.35 + n.coerceAtMost(20L).toDouble() / 20.0 * 0.4).coerceAtMost(0.75)
        }
        val finding = InterpretationFinding(
            id = "intervention:${definition.id}:${observationEnd}",
            kind = FindingKind.INTERVENTION_EFFECT,
            title = "${definition.name}: ${definition.outcomeMetric}",
            explanation = interventionExplanation(status, comparison),
            direction = when (status) {
                InterventionStatus.IMPROVED -> FindingDirection.POSITIVE
                InterventionStatus.WORSENED -> FindingDirection.NEGATIVE
                InterventionStatus.NO_CLEAR_CHANGE -> FindingDirection.STABLE
                InterventionStatus.INSUFFICIENT_DATA -> FindingDirection.UNKNOWN
            },
            confidence = confidence,
            evidence = listOf(
                baseline.evidence(comparison.previous.count, "pre-intervention"),
                observation.evidence(comparison.current.count, "post-intervention")
            ),
            caveats = listOf(
                "Before/after changes cannot prove that the intervention caused the outcome",
                "Concurrent behaviour, illness, medication, environment and measurement changes may confound the result"
            ),
            generatedEpochMs = nowEpochMs()
        )
        return InterventionEvaluation(definition, comparison, status, confidence, finding)
    }

    private fun classify(direction: OutcomeDirection, percentChange: Double): InterventionStatus {
        if (abs(percentChange) < MIN_MEANINGFUL_PERCENT_CHANGE) return InterventionStatus.NO_CLEAR_CHANGE
        return when (direction) {
            OutcomeDirection.HIGHER_IS_BETTER -> if (percentChange > 0) InterventionStatus.IMPROVED else InterventionStatus.WORSENED
            OutcomeDirection.LOWER_IS_BETTER -> if (percentChange < 0) InterventionStatus.IMPROVED else InterventionStatus.WORSENED
            OutcomeDirection.UNKNOWN -> InterventionStatus.NO_CLEAR_CHANGE
        }
    }

    private fun interventionExplanation(status: InterventionStatus, comparison: PeriodComparison): String {
        val change = comparison.percentChange?.let { "%.1f".formatPortable(it) }
        return when (status) {
            InterventionStatus.IMPROVED -> "The recorded outcome moved in the intended direction${change?.let { " by about $it%" } ?: ""} after this intervention began."
            InterventionStatus.WORSENED -> "The recorded outcome moved away from the intended direction${change?.let { " by about $it%" } ?: ""} after this intervention began."
            InterventionStatus.NO_CLEAR_CHANGE -> "No clear change in the recorded outcome was detected across the before/after windows."
            InterventionStatus.INSUFFICIENT_DATA -> "There is not enough before/after data yet to evaluate this intervention."
        }
    }

    private companion object {
        const val MIN_PERIOD_SAMPLES = 3L
        const val MIN_MEANINGFUL_PERCENT_CHANGE = 3.0
    }
}

/** Prioritises derived findings for UI without changing the underlying evidence. */
class InsightsEngine(
    private val minimumSurfaceConfidence: Double = 0.45
) {
    fun rank(findings: List<InterpretationFinding>): List<InsightCard> = findings
        .map { finding ->
            val evidenceSamples = finding.evidence.sumOf { it.sampleCount }
            val evidenceFactor = (evidenceSamples.coerceAtMost(30L).toDouble() / 30.0)
            val kindWeight = when (finding.kind) {
                FindingKind.INTERVENTION_EFFECT -> 1.0
                FindingKind.RELATIONSHIP -> 0.9
                FindingKind.TREND -> 0.75
            }
            val priority = (finding.confidence * 0.75 + evidenceFactor * 0.25) * kindWeight
            val shouldSurface = finding.confidence >= minimumSurfaceConfidence &&
                finding.direction != FindingDirection.UNKNOWN
            InsightCard(
                finding = finding,
                priority = priority.coerceIn(0.0, 1.0),
                shouldSurface = shouldSurface,
                reason = if (shouldSurface) "Enough evidence to surface as a cautious insight" else "Held back until evidence is stronger"
            )
        }
        .sortedByDescending { it.priority }
}

private fun MetricWindow.evidence(sampleCount: Long, role: String) = EvidenceWindow(
    domain, metric, fromEpochMs, toEpochMs, sampleCount, role
)

private fun stableFindingId(prefix: String, windows: List<MetricWindow>, generatedEpochMs: Long): String =
    buildString {
        append(prefix)
        windows.forEach {
            append(':'); append(it.domain.name); append(':'); append(it.metric)
            append(':'); append(it.fromEpochMs); append(':'); append(it.toEpochMs)
        }
        append(':'); append(generatedEpochMs)
    }

/** Avoid java.lang.String formatting in commonMain. */
private fun String.formatPortable(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return rounded.toString()
}

private const val ENGINE_VERSION = "superhuman-intelligence-0.1"
