package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs

enum class PersonalEvidenceType { OBSERVATION, TREND, ASSOCIATION, REPEATED_ASSOCIATION, EXPERIMENT_RESULT, INSUFFICIENT_EVIDENCE }
enum class TrudyEffectDirection { INCREASE, DECREASE, MIXED, NONE, UNKNOWN }
enum class TrudyConfidence { INSUFFICIENT, LOW, MODERATE, STRONG }
enum class TrudyDataQualityStatus { GOOD, LIMITED, STALE, SPARSE, INSUFFICIENT }
enum class TrudyAssociationMethod { PEARSON, SPEARMAN }
enum class TrudyExperimentKind { EARLIER_CAFFEINE_CUTOFF, HYDRATION_CONSISTENCY, SLEEP_SCHEDULE_CONSISTENCY, EXERCISE_TIMING, MINDFULNESS_ROUTINE }
enum class TrudyExperimentConclusion { SUPPORTS_HYPOTHESIS, DID_NOT_SUPPORT, INCONCLUSIVE }

data class PersonalEvidenceItem(
    val id: String,
    val domains: List<HealthDomain>,
    val metricIds: List<String>,
    val evidenceType: PersonalEvidenceType,
    val observationWindow: TrudyTimeRange,
    val comparisonWindow: TrudyTimeRange? = null,
    val effectDirection: TrudyEffectDirection = TrudyEffectDirection.UNKNOWN,
    val effectMagnitude: Double? = null,
    val sampleCount: Int,
    val confidence: TrudyConfidence,
    val dataQualityStatus: TrudyDataQualityStatus,
    val caveats: List<String> = emptyList(),
    val supportingEvidenceReferences: List<TrudyEvidenceReference> = emptyList(),
    val attributes: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank())
        require(domains.isNotEmpty() && domains.distinct().size == domains.size)
        require(metricIds.isNotEmpty())
        require(sampleCount >= 0)
        require(effectMagnitude?.isFinite() != false)
        supportingEvidenceReferences.forEach { ref ->
            require(ref.domain in domains) { "Supporting evidence must remain inside declared domains" }
            ref.metricId?.let { require(it in metricIds) { "Supporting metric must remain inside declared metric IDs" } }
        }
    }
}

data class TrudyAssociationResult(
    val leftDomain: HealthDomain,
    val leftMetricId: String,
    val rightDomain: HealthDomain,
    val rightMetricId: String,
    val method: TrudyAssociationMethod,
    val coefficient: Double?,
    val sampleCount: Int,
    val matchedFraction: Double,
    val direction: TrudyEffectDirection,
    val confidence: TrudyConfidence,
    val dataQualityStatus: TrudyDataQualityStatus,
    val lagMs: Long = 0L,
    val caveats: List<String> = emptyList(),
    val evidence: PersonalEvidenceItem
) {
    init {
        require(sampleCount >= 0)
        require(matchedFraction in 0.0..1.0)
        require(coefficient == null || coefficient in -1.0..1.0)
        require(lagMs >= 0L)
        require(evidence.evidenceType == PersonalEvidenceType.ASSOCIATION || evidence.evidenceType == PersonalEvidenceType.INSUFFICIENT_EVIDENCE)
    }
}

data class TrudyBaselineComparison(
    val domain: HealthDomain,
    val metricId: String,
    val observationWindow: TrudyTimeRange,
    val baselineWindow: TrudyTimeRange,
    val observationMean: Double?,
    val baselineMean: Double?,
    val absoluteDelta: Double?,
    val percentDelta: Double?,
    val standardizedEffect: Double?,
    val observationSampleCount: Int,
    val baselineSampleCount: Int,
    val direction: TrudyEffectDirection,
    val confidence: TrudyConfidence,
    val dataQualityStatus: TrudyDataQualityStatus,
    val caveats: List<String> = emptyList(),
    val evidence: PersonalEvidenceItem
)

data class TrudyExpectedGain(
    val direction: TrudyEffectDirection,
    val plausibleAbsoluteRange: ClosedFloatingPointRange<Double>? = null,
    val unit: String? = null,
    val evidenceStrength: TrudyConfidence,
    val uncertainty: String,
    val rationale: String
)

data class TrudyExperimentHypothesis(
    val id: String,
    val hypothesis: String,
    val intervention: String,
    val targetDomain: HealthDomain,
    val targetMetricId: String,
    val secondaryMetrics: List<Pair<HealthDomain, String>>,
    val baselineWindowDays: Int,
    val interventionWindowDays: Int,
    val expectedDirection: TrudyEffectDirection,
    val suggestedDurationDays: Int,
    val confounders: List<String>,
    val safetyNotes: List<String>,
    val evidenceBasis: List<PersonalEvidenceItem>,
    val expectedGain: TrudyExpectedGain
) {
    init {
        require(id.isNotBlank() && hypothesis.isNotBlank() && intervention.isNotBlank())
        require(targetMetricId.isNotBlank())
        require(baselineWindowDays > 0 && interventionWindowDays > 0 && suggestedDurationDays > 0)
    }
}

data class TrudyExperimentResult(
    val hypothesisId: String,
    val targetDomain: HealthDomain,
    val targetMetricId: String,
    val baselineMean: Double?,
    val baselineMedian: Double?,
    val interventionMean: Double?,
    val interventionMedian: Double?,
    val absoluteChange: Double?,
    val relativeChange: Double?,
    val baselineVariability: Double?,
    val interventionVariability: Double?,
    val baselineSampleCount: Int,
    val interventionSampleCount: Int,
    val adherenceFraction: Double,
    val confidence: TrudyConfidence,
    val conclusion: TrudyExperimentConclusion,
    val summary: String,
    val evidence: PersonalEvidenceItem
) {
    init {
        require(adherenceFraction in 0.0..1.0)
        require(!summary.contains("caused", ignoreCase = true))
    }
}

data class TrudyScientificContext(
    val id: String,
    val domains: List<HealthDomain>,
    val summary: String,
    val source: String,
    val confidence: Double?
)

internal fun effectDirection(delta: Double?, epsilon: Double = 1e-9): TrudyEffectDirection = when {
    delta == null || !delta.isFinite() -> TrudyEffectDirection.UNKNOWN
    abs(delta) <= epsilon -> TrudyEffectDirection.NONE
    delta > 0 -> TrudyEffectDirection.INCREASE
    else -> TrudyEffectDirection.DECREASE
}
