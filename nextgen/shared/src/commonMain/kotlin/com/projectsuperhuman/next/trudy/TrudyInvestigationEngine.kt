package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.math.min

/** The question shape determines what can become an important finding. */
enum class TrudyInvestigationIntent {
    WHY,
    WHAT_CHANGED,
    WHAT_MIGHT_EXPLAIN,
    WHAT_TO_WATCH_TODAY,
    IS_ANYTHING_CONNECTED,
    WHAT_WAS_DIFFERENT
}

/** Explicit premise status for the Answer Engine. */
enum class TrudyPremiseStatus {
    PREMISE_SUPPORTED,
    PREMISE_NOT_SUPPORTED,
    PREMISE_MIXED,
    PREMISE_UNVERIFIABLE,
    NOT_APPLICABLE
}

/** Deliberately contains no causal classification. */
enum class TrudyFindingClassification {
    OBSERVED_CHANGE,
    POSSIBLE_ASSOCIATION,
    NO_CLEAR_ASSOCIATION,
    INSUFFICIENT_EVIDENCE,
    CONTRADICTORY_EVIDENCE
}

enum class TrudyTemporalAlignmentKind {
    SAME_DAY,
    PREVIOUS_EVENING_TO_FOLLOWING_SLEEP,
    PREVIOUS_NIGHT_TO_NEXT_MORNING,
    ROLLING_AVERAGE,
    BASELINE_VS_RECENT
}

data class TrudyTemporalAlignment(
    val kind: TrudyTemporalAlignmentKind = TrudyTemporalAlignmentKind.SAME_DAY,
    val lagMs: Long = 0L,
    val toleranceMs: Long = 12L * HOUR_MS,
    val rollingDays: Int = 1
) {
    init {
        require(lagMs in 0L..TrudyStatistics.MAX_LAG_MS)
        require(toleranceMs in 0L..TrudyStatistics.MAX_ALIGNMENT_WINDOW_MS)
        require(rollingDays in 1..14)
        if (kind == TrudyTemporalAlignmentKind.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP) {
            require(lagMs > 0L) { "Following-sleep alignment requires an explicit positive lag" }
        }
    }

    companion object {
        const val HOUR_MS = 3_600_000L

        val SAME_DAY = TrudyTemporalAlignment()
        val PREVIOUS_EVENING_TO_FOLLOWING_SLEEP = TrudyTemporalAlignment(
            kind = TrudyTemporalAlignmentKind.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP,
            lagMs = 12L * HOUR_MS,
            toleranceMs = 8L * HOUR_MS
        )
        val PREVIOUS_NIGHT_TO_NEXT_MORNING = TrudyTemporalAlignment(
            kind = TrudyTemporalAlignmentKind.PREVIOUS_NIGHT_TO_NEXT_MORNING,
            lagMs = 8L * HOUR_MS,
            toleranceMs = 6L * HOUR_MS
        )
        fun rolling(days: Int) = TrudyTemporalAlignment(
            kind = TrudyTemporalAlignmentKind.ROLLING_AVERAGE,
            toleranceMs = 12L * HOUR_MS,
            rollingDays = days
        )
    }
}

enum class TrudyEvidenceGapReason {
    NO_DATA,
    SPARSE_DATA,
    STALE_DATA,
    LOW_ALIGNMENT,
    LOW_VARIANCE,
    UNMEASURED_CONFOUNDER,
    NOT_CONNECTED
}

enum class TrudyCaptureKind { WEARABLE, MANUAL, DERIVED, DEVICE, UNKNOWN }

data class TrudyCaptureDistribution(
    val wearableFraction: Double,
    val manualFraction: Double,
    val derivedFraction: Double,
    val deviceFraction: Double,
    val unknownFraction: Double,
    val sources: Set<String>
)

data class TrudySignalQuality(
    val score: Double,
    val status: TrudyDataQualityStatus,
    val sampleCount: Int,
    val expectedSampleCount: Int,
    val missingFraction: Double,
    val matchedFraction: Double? = null,
    val variance: Double? = null,
    val measurementFrequencyPerDay: Double,
    val latestTimestampEpochMs: Long?,
    val stale: Boolean,
    val capture: TrudyCaptureDistribution,
    val notes: List<String> = emptyList()
) {
    init {
        require(score in 0.0..1.0)
        require(sampleCount >= 0 && expectedSampleCount >= 0)
        require(missingFraction in 0.0..1.0)
        require(matchedFraction == null || matchedFraction in 0.0..1.0)
    }
}

data class TrudyInvestigationTarget(
    val domain: HealthDomain,
    val primaryMetricId: String,
    val supportingMetricIds: List<String>,
    val label: String,
    val includesSubjectiveClaim: Boolean
)

data class TrudyInvestigationTimeframe(
    val recent: TrudyTimeRange,
    val baseline: TrudyTimeRange,
    val label: String,
    val explicit: Boolean
)

data class TrudyInvestigationFinding(
    val classification: TrudyFindingClassification,
    val domain: HealthDomain,
    val metricId: String,
    val recentMean: Double,
    val baselineMean: Double,
    val absoluteDelta: Double,
    val percentDelta: Double?,
    val standardizedEffect: Double?,
    val favourableDirection: TrudyEffectDirection,
    val priorityScore: Double,
    val quality: TrudySignalQuality,
    val evidence: PersonalEvidenceItem
)

data class TrudyRelatedSignal(
    val classification: TrudyFindingClassification,
    val domain: HealthDomain,
    val metricId: String,
    val targetDomain: HealthDomain,
    val targetMetricId: String,
    val temporalAlignment: TrudyTemporalAlignment,
    val coefficient: Double?,
    val relationshipScore: Double,
    val relevanceWeight: Double,
    val sampleCount: Int,
    val quality: TrudySignalQuality,
    val evidence: PersonalEvidenceItem
)

data class TrudyMissingEvidence(
    val domain: HealthDomain?,
    val metricId: String?,
    val label: String,
    val reason: TrudyEvidenceGapReason,
    val excludedFromPriority: Boolean = true
)

data class TrudyInvestigationExecutionStats(
    val requestedMetricCount: Int,
    val requestedDomainCount: Int,
    val rowsInspected: Int,
    val relationshipCount: Int,
    val maxRowsPerMetric: Int,
    val maxLookbackDays: Int
)

data class TrudyInvestigationResult(
    val target: TrudyInvestigationTarget,
    val timeframe: TrudyInvestigationTimeframe,
    val premiseStatus: TrudyPremiseStatus,
    val importantFindings: List<TrudyInvestigationFinding>,
    val relatedSignals: List<TrudyRelatedSignal>,
    val missingEvidence: List<TrudyMissingEvidence>,
    val quality: TrudySignalQuality,
    val confidence: TrudyConfidence,
    val caveats: List<String>,
    val execution: TrudyInvestigationExecutionStats
) {
    init {
        require(caveats.any { "caus" in it.lowercase() }) {
            "Investigation results must retain the observational causal boundary"
        }
        require(missingEvidence.all { it.excludedFromPriority })
    }
}

data class TrudyInvestigationBudget(
    val maxTargets: Int = 5,
    val maxRelatedSignals: Int = 8,
    val maxRowsPerMetric: Int = 256,
    val maxLookbackDays: Int = 56,
    val maxImportantFindings: Int = 5
) {
    init {
        require(maxTargets in 1..8)
        require(maxRelatedSignals in 0..8)
        require(maxRowsPerMetric in 5..365)
        require(maxLookbackDays in 7..90)
        require(maxImportantFindings in 1..8)
    }
}

data class TrudyInvestigationRelevancePlan(
    val targetLabel: String,
    val targets: List<TrudyInvestigationMetric>,
    val related: List<TrudyInvestigationMetric>
)

/** Small, auditable metric graph. Plans are bounded before any Data Vault read occurs. */
object TrudyInvestigationRelevanceGraph {
    fun plan(
        text: String,
        selected: List<TrudySystemMetric>,
        intent: TrudyInvestigationIntent
    ): TrudyInvestigationRelevancePlan {
        val normalized = text.lowercase()
        return when {
            intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY -> today()
            listOf("sleep", "slept", "night", "bedtime", "tired").any { it in normalized } -> sleep()
            listOf("heart rate", "resting hr", "pulse", "bpm").any { it in normalized } -> heartRate(normalized)
            else -> generic(selected)
        }
    }

    private fun sleep() = TrudyInvestigationRelevancePlan(
        targetLabel = "sleep",
        targets = listOf(
            target(HealthDomain.SLEEP, "sleep_score", TrudyMetricPreference.HIGHER_IS_FAVOURABLE, primary = true),
            target(HealthDomain.SLEEP, "sleep_awake_minutes", TrudyMetricPreference.LOWER_IS_FAVOURABLE),
            target(HealthDomain.SLEEP, "sleep_continuity_score", TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            target(HealthDomain.SLEEP, "sleep_deep_minutes", TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            target(HealthDomain.SLEEP, "sleep_total_minutes", TrudyMetricPreference.HIGHER_IS_FAVOURABLE)
        ),
        related = listOf(
            related(HealthDomain.EMOTIONAL, "emotional_calmness", 1.0),
            related(HealthDomain.NUTRITION, "food_caffeine_mg", 1.0, TrudyTemporalAlignment.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP),
            related(HealthDomain.ENVIRONMENT, "environment_temperature_c", 0.85),
            related(HealthDomain.ENVIRONMENT, "environment_relative_humidity_pct", 0.75),
            related(HealthDomain.EXERCISE, "exercise_minutes", 0.8),
            related(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 0.75, TrudyTemporalAlignment.PREVIOUS_NIGHT_TO_NEXT_MORNING),
            related(HealthDomain.HYDRATION, "water_total_l", 0.55),
            related(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", 0.5)
        )
    )

    private fun heartRate(text: String): TrudyInvestigationRelevancePlan {
        val targetMetric = if ("resting" in text) "resting_heart_rate_bpm" else "heart_rate_avg_bpm"
        return TrudyInvestigationRelevancePlan(
            targetLabel = if (targetMetric.startsWith("resting")) "resting heart rate" else "heart rate",
            targets = listOf(
                target(HealthDomain.EXERCISE, targetMetric, TrudyMetricPreference.CONTEXT_DEPENDENT, primary = true),
                target(HealthDomain.EXERCISE, "heart_rate_max_bpm", TrudyMetricPreference.CONTEXT_DEPENDENT)
            ),
            related = listOf(
                related(HealthDomain.EXERCISE, "exercise_minutes", 1.0),
                related(HealthDomain.EXERCISE, "workout_volume", 0.9),
                related(HealthDomain.SLEEP, "sleep_score", 0.85, TrudyTemporalAlignment.PREVIOUS_NIGHT_TO_NEXT_MORNING),
                related(HealthDomain.EMOTIONAL, "emotional_calmness", 0.75),
                related(HealthDomain.HYDRATION, "water_total_l", 0.65),
                related(HealthDomain.ENVIRONMENT, "environment_temperature_c", 0.6),
                related(HealthDomain.BODY, "body_temperature_celsius", 0.55)
            )
        )
    }

    private fun today() = TrudyInvestigationRelevancePlan(
        targetLabel = "today priorities",
        targets = listOf(
            target(HealthDomain.SLEEP, "sleep_score", TrudyMetricPreference.HIGHER_IS_FAVOURABLE, primary = true),
            target(HealthDomain.EXERCISE, "resting_heart_rate_bpm", TrudyMetricPreference.CONTEXT_DEPENDENT),
            target(HealthDomain.EXERCISE, "steps", TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            target(HealthDomain.HYDRATION, "water_total_l", TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            target(HealthDomain.EMOTIONAL, "emotional_energy", TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            target(HealthDomain.EMOTIONAL, "emotional_calmness", TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            target(HealthDomain.BODY, "body_weight_kg", TrudyMetricPreference.CONTEXT_DEPENDENT),
            target(HealthDomain.ENVIRONMENT, "environment_european_aqi", TrudyMetricPreference.LOWER_IS_FAVOURABLE)
        ),
        related = emptyList()
    )

    private fun generic(selected: List<TrudySystemMetric>): TrudyInvestigationRelevancePlan {
        val chosen = selected.take(MAX_GENERIC_TARGETS)
        val primary = chosen.firstOrNull()
            ?: TrudySystemMetric(HealthDomain.SLEEP, "sleep_score", emptySet(), TrudyMetricPreference.HIGHER_IS_FAVOURABLE)
        val targets = listOf(
            TrudyInvestigationMetric(
                primary.domain,
                primary.metricId,
                TrudyInvestigationRole.PRIMARY,
                primary.preference,
                relevanceWeight = 1.0
            )
        ) + chosen.drop(1).map {
            TrudyInvestigationMetric(it.domain, it.metricId, preference = it.preference, relevanceWeight = 0.8)
        }
        return TrudyInvestigationRelevancePlan(primary.metricId, targets, emptyList())
    }

    private fun target(
        domain: HealthDomain,
        metricId: String,
        preference: TrudyMetricPreference,
        primary: Boolean = false
    ) = TrudyInvestigationMetric(
        domain = domain,
        metricId = metricId,
        role = if (primary) TrudyInvestigationRole.PRIMARY else TrudyInvestigationRole.SUPPORTING,
        preference = preference,
        temporalAlignment = TrudyTemporalAlignment(
            kind = TrudyTemporalAlignmentKind.BASELINE_VS_RECENT,
            toleranceMs = 0L
        ),
        relevanceWeight = if (primary) 1.0 else 0.85
    )

    private fun related(
        domain: HealthDomain,
        metricId: String,
        relevance: Double,
        alignment: TrudyTemporalAlignment = TrudyTemporalAlignment.SAME_DAY
    ) = TrudyInvestigationMetric(
        domain = domain,
        metricId = metricId,
        preference = TrudyMetricPreference.CONTEXT_DEPENDENT,
        temporalAlignment = alignment,
        relevanceWeight = relevance
    )

    private const val MAX_GENERIC_TARGETS = 5
}

/**
 * Converts bounded comparisons and candidate relationships into the stable Answer Engine contract.
 * It never queries an unplanned domain or metric and never searches across arbitrary lags.
 */
class TrudyInvestigationResultAssembler(
    private val source: TrudyPersonalEvidenceSource? = null
) {
    suspend fun assemble(
        operation: InvestigateChange,
        premise: TrudyPremiseAssessment,
        comparisons: List<TrudyBaselineComparison>,
        associations: List<Pair<TrudyInvestigationMetric, TrudyAssociationResult>>
    ): TrudyInvestigationResult {
        val primary = operation.targets.firstOrNull { it.role == TrudyInvestigationRole.PRIMARY }
            ?: operation.targets.first()
        val metrics = (operation.targets + operation.related)
            .distinctBy { it.domain to it.metricId }
            .take(operation.budget.maxTargets + operation.budget.maxRelatedSignals)
        val combinedRange = TrudyTimeRange(
            operation.baselineWindow.fromEpochMs,
            operation.observationWindow.toEpochMs
        )
        val rowsByMetric = linkedMapOf<Pair<HealthDomain, String>, List<TrudyMetricEvidence>>()
        val domainQuality = linkedMapOf<HealthDomain, TrudyDataQualityEvidence>()
        if (source != null) {
            metrics.forEach { metric ->
                val key = metric.domain to metric.metricId
                rowsByMetric[key] = source.metricWindow(
                    metric.domain,
                    metric.metricId,
                    combinedRange,
                    operation.budget.maxRowsPerMetric
                ).filter(TrudyStatistics::usableEvidence)
            }
            metrics.map { it.domain }.distinct().forEach { domain ->
                domainQuality[domain] = source.dataQuality(domain)
            }
        }

        val qualityByMetric = metrics.associate { metric ->
            val key = metric.domain to metric.metricId
            val rows = rowsByMetric[key].orEmpty()
            key to qualityFor(
                rows = rows,
                range = combinedRange,
                observationStart = operation.observationWindow.fromEpochMs,
                domainQuality = domainQuality[metric.domain],
                fallbackStatus = comparisons.firstOrNull {
                    it.domain == metric.domain && it.metricId == metric.metricId
                }?.dataQualityStatus
            )
        }

        val premiseStatus = when {
            operation.intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY -> TrudyPremiseStatus.NOT_APPLICABLE
            premise == TrudyPremiseAssessment.SUPPORTED -> TrudyPremiseStatus.PREMISE_SUPPORTED
            premise == TrudyPremiseAssessment.NOT_SUPPORTED -> TrudyPremiseStatus.PREMISE_NOT_SUPPORTED
            premise == TrudyPremiseAssessment.MIXED -> TrudyPremiseStatus.PREMISE_MIXED
            else -> TrudyPremiseStatus.PREMISE_UNVERIFIABLE
        }

        val findings = comparisons.mapNotNull { comparison ->
            val recent = comparison.observationMean ?: return@mapNotNull null
            val baseline = comparison.baselineMean ?: return@mapNotNull null
            val delta = comparison.absoluteDelta ?: return@mapNotNull null
            val metric = operation.targets.firstOrNull {
                it.domain == comparison.domain && it.metricId == comparison.metricId
            } ?: return@mapNotNull null
            val quality = qualityByMetric[comparison.domain to comparison.metricId]
                ?: fallbackQuality(comparison)
            val meaningful = meaningfulChange(comparison)
            if (operation.intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY &&
                (!meaningful || quality.stale || quality.status == TrudyDataQualityStatus.INSUFFICIENT)
            ) return@mapNotNull null
            val classification = when {
                premiseStatus == TrudyPremiseStatus.PREMISE_NOT_SUPPORTED && metric.role == TrudyInvestigationRole.PRIMARY ->
                    TrudyFindingClassification.CONTRADICTORY_EVIDENCE
                meaningful -> TrudyFindingClassification.OBSERVED_CHANGE
                operation.claim != TrudyChangeClaim.UNSPECIFIED -> TrudyFindingClassification.CONTRADICTORY_EVIDENCE
                else -> return@mapNotNull null
            }
            TrudyInvestigationFinding(
                classification = classification,
                domain = comparison.domain,
                metricId = comparison.metricId,
                recentMean = recent,
                baselineMean = baseline,
                absoluteDelta = delta,
                percentDelta = comparison.percentDelta,
                standardizedEffect = comparison.standardizedEffect,
                favourableDirection = favourableDirection(metric.preference, delta),
                priorityScore = changePriority(comparison, quality, metric.relevanceWeight),
                quality = quality,
                evidence = comparison.evidence
            )
        }.sortedByDescending { it.priorityScore }
            .take(operation.budget.maxImportantFindings)

        val related = associations.map { (candidate, association) ->
            val metricQuality = qualityByMetric[candidate.domain to candidate.metricId]
                ?: fallbackQuality(association)
            val pairQuality = metricQuality.copy(
                matchedFraction = association.matchedFraction,
                score = (metricQuality.score * (0.55 + 0.45 * association.matchedFraction)).coerceIn(0.0, 1.0),
                status = worse(metricQuality.status, association.dataQualityStatus),
                notes = (metricQuality.notes + association.caveats).distinct()
            )
            val coefficient = association.coefficient
            val classification = when {
                coefficient == null || association.sampleCount < MIN_ASSOCIATION_SAMPLES || pairQuality.status == TrudyDataQualityStatus.INSUFFICIENT ->
                    TrudyFindingClassification.INSUFFICIENT_EVIDENCE
                abs(coefficient) >= MIN_REPORTABLE_ASSOCIATION && pairQuality.score >= MIN_REPORTABLE_QUALITY ->
                    TrudyFindingClassification.POSSIBLE_ASSOCIATION
                else -> TrudyFindingClassification.NO_CLEAR_ASSOCIATION
            }
            val score = if (classification == TrudyFindingClassification.POSSIBLE_ASSOCIATION) {
                (abs(coefficient ?: 0.0) * pairQuality.score * candidate.relevanceWeight).coerceIn(0.0, 1.0)
            } else 0.0
            TrudyRelatedSignal(
                classification = classification,
                domain = candidate.domain,
                metricId = candidate.metricId,
                targetDomain = primary.domain,
                targetMetricId = primary.metricId,
                temporalAlignment = candidate.temporalAlignment,
                coefficient = coefficient,
                relationshipScore = score,
                relevanceWeight = candidate.relevanceWeight,
                sampleCount = association.sampleCount,
                quality = pairQuality,
                evidence = association.evidence
            )
        }.sortedWith(compareByDescending<TrudyRelatedSignal> { it.relationshipScore }
            .thenBy { it.metricId })
            .take(operation.budget.maxRelatedSignals)

        val gaps = buildList {
            metrics.forEach { metric ->
                val quality = qualityByMetric[metric.domain to metric.metricId] ?: return@forEach
                val reason = when {
                    quality.sampleCount == 0 -> TrudyEvidenceGapReason.NO_DATA
                    quality.stale -> TrudyEvidenceGapReason.STALE_DATA
                    quality.variance != null && quality.variance <= MIN_VARIANCE -> TrudyEvidenceGapReason.LOW_VARIANCE
                    quality.sampleCount < MIN_ASSOCIATION_SAMPLES -> TrudyEvidenceGapReason.SPARSE_DATA
                    else -> null
                }
                if (reason != null) add(TrudyMissingEvidence(metric.domain, metric.metricId, metric.metricId, reason))
            }
            related.filter { it.quality.matchedFraction != null && it.quality.matchedFraction < MIN_MATCHED_FRACTION }
                .forEach { add(TrudyMissingEvidence(it.domain, it.metricId, it.metricId, TrudyEvidenceGapReason.LOW_ALIGNMENT)) }
            addAll(expectedConfounders(operation, rowsByMetric.keys))
        }.distinctBy { listOf(it.domain?.name, it.metricId, it.reason.name).joinToString(":") }

        val consideredQualities = (
            findings.map { it.quality } + related.filter {
                it.classification != TrudyFindingClassification.INSUFFICIENT_EVIDENCE
            }.map { it.quality }
        )
            .ifEmpty { qualityByMetric.values.toList() }
        val overall = combineQuality(consideredQualities)
        val confidence = confidenceFor(overall, findings, related)
        val caveats = buildList {
            add("Observed personal-data relationships are associations and do not establish causation.")
            if (operation.includesSubjectiveClaim && premiseStatus == TrudyPremiseStatus.PREMISE_NOT_SUPPORTED) {
                add("Recorded metrics do not support the claimed deterioration; subjective experience may still differ from wearable signals.")
            }
            if (related.any { it.quality.status != TrudyDataQualityStatus.GOOD }) {
                add("Lower-quality, sparse, stale, or poorly aligned relationships are labelled weak or insufficient.")
            }
        }
        return TrudyInvestigationResult(
            target = TrudyInvestigationTarget(
                domain = primary.domain,
                primaryMetricId = primary.metricId,
                supportingMetricIds = operation.targets.filterNot { it == primary }.map { it.metricId },
                label = operation.targetLabel ?: primary.metricId,
                includesSubjectiveClaim = operation.includesSubjectiveClaim
            ),
            timeframe = TrudyInvestigationTimeframe(
                recent = operation.observationWindow,
                baseline = operation.baselineWindow,
                label = operation.timeframeLabel,
                explicit = operation.timeframeExplicit
            ),
            premiseStatus = premiseStatus,
            importantFindings = findings,
            relatedSignals = related,
            missingEvidence = gaps,
            quality = overall,
            confidence = confidence,
            caveats = caveats,
            execution = TrudyInvestigationExecutionStats(
                requestedMetricCount = metrics.size,
                requestedDomainCount = metrics.map { it.domain }.distinct().size,
                rowsInspected = rowsByMetric.values.sumOf { it.size },
                relationshipCount = associations.size,
                maxRowsPerMetric = operation.budget.maxRowsPerMetric,
                maxLookbackDays = operation.budget.maxLookbackDays
            )
        )
    }

    private fun qualityFor(
        rows: List<TrudyMetricEvidence>,
        range: TrudyTimeRange,
        observationStart: Long,
        domainQuality: TrudyDataQualityEvidence?,
        fallbackStatus: TrudyDataQualityStatus?
    ): TrudySignalQuality {
        val days = (((range.toEpochMs - range.fromEpochMs).coerceAtLeast(0L) / DAY_MS) + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val expected = min(days, MAX_EXPECTED_SAMPLES)
        val distinctDays = rows.map { it.timestampEpochMs.floorDiv(DAY_MS) }.distinct().size
        val missing = if (expected == 0) 1.0 else (1.0 - distinctDays.toDouble() / expected).coerceIn(0.0, 1.0)
        val variance = TrudyStatistics.standardDeviation(rows.map { it.value })?.let { it * it }
        val latest = rows.maxOfOrNull { it.timestampEpochMs }
        val stale = domainQuality?.isStale == true || latest == null || latest < observationStart
        val capture = captureDistribution(rows)
        val sampleScore = (rows.size.toDouble() / 14.0).coerceIn(0.0, 1.0)
        val moduleScore = (domainQuality?.score?.toDouble()?.div(100.0) ?: 0.75).coerceIn(0.0, 1.0)
        val provenanceScore = when {
            capture.wearableFraction + capture.deviceFraction >= 0.75 -> 1.0
            capture.manualFraction >= 0.75 -> 0.85
            capture.unknownFraction >= 0.75 -> 0.7
            else -> 0.9
        }
        val score = (0.30 * sampleScore + 0.25 * (1.0 - missing) + 0.20 * moduleScore +
            0.15 * if (stale) 0.0 else 1.0 + 0.10 * provenanceScore).coerceIn(0.0, 1.0)
        val status = when {
            rows.isEmpty() -> TrudyDataQualityStatus.INSUFFICIENT
            stale -> TrudyDataQualityStatus.STALE
            rows.size < MIN_ASSOCIATION_SAMPLES -> TrudyDataQualityStatus.SPARSE
            moduleScore < 0.5 || missing > 0.55 -> TrudyDataQualityStatus.LIMITED
            else -> fallbackStatus ?: TrudyDataQualityStatus.GOOD
        }
        return TrudySignalQuality(
            score = score,
            status = status,
            sampleCount = rows.size,
            expectedSampleCount = expected,
            missingFraction = missing,
            variance = variance,
            measurementFrequencyPerDay = if (days == 0) 0.0 else rows.size.toDouble() / days,
            latestTimestampEpochMs = latest,
            stale = stale,
            capture = capture,
            notes = buildList {
                if (stale) add("No observation from the recent window.")
                if (missing > 0.55) add("More than half of expected daily coverage is missing.")
                if (variance != null && variance <= MIN_VARIANCE) add("The signal has too little variance for relationship scoring.")
                if (capture.manualFraction > 0.5) add("Most observations were manually entered.")
            }
        )
    }

    private fun captureDistribution(rows: List<TrudyMetricEvidence>): TrudyCaptureDistribution {
        if (rows.isEmpty()) return TrudyCaptureDistribution(0.0, 0.0, 0.0, 0.0, 1.0, emptySet())
        val kinds = rows.map(::captureKind)
        fun fraction(kind: TrudyCaptureKind) = kinds.count { it == kind }.toDouble() / kinds.size
        return TrudyCaptureDistribution(
            wearableFraction = fraction(TrudyCaptureKind.WEARABLE),
            manualFraction = fraction(TrudyCaptureKind.MANUAL),
            derivedFraction = fraction(TrudyCaptureKind.DERIVED),
            deviceFraction = fraction(TrudyCaptureKind.DEVICE),
            unknownFraction = fraction(TrudyCaptureKind.UNKNOWN),
            sources = rows.map { it.source }.filter { it.isNotBlank() }.toSet().take(MAX_SOURCES).toSet()
        )
    }

    private fun captureKind(row: TrudyMetricEvidence): TrudyCaptureKind {
        val text = (row.source + " " + row.metadata.values.joinToString(" ")).lowercase()
        return when {
            row.evidenceKind == TrudyEvidenceKind.DERIVED_PERSONAL_TREND || "derived" in text -> TrudyCaptureKind.DERIVED
            listOf("health connect", "healthconnect", "wearable", "samsung", "fitbit", "garmin", "oura").any { it in text } -> TrudyCaptureKind.WEARABLE
            listOf("manual", "user entry", "self report", "self-report").any { it in text } -> TrudyCaptureKind.MANUAL
            listOf("sensor", "device", "scale", "monitor").any { it in text } -> TrudyCaptureKind.DEVICE
            else -> TrudyCaptureKind.UNKNOWN
        }
    }

    private fun fallbackQuality(comparison: TrudyBaselineComparison): TrudySignalQuality {
        val samples = comparison.observationSampleCount + comparison.baselineSampleCount
        return fallbackQuality(samples, comparison.dataQualityStatus, comparison.confidence)
    }

    private fun fallbackQuality(association: TrudyAssociationResult): TrudySignalQuality =
        fallbackQuality(association.sampleCount, association.dataQualityStatus, association.confidence)
            .copy(matchedFraction = association.matchedFraction)

    private fun fallbackQuality(
        samples: Int,
        status: TrudyDataQualityStatus,
        confidence: TrudyConfidence
    ): TrudySignalQuality {
        val score = when (confidence) {
            TrudyConfidence.STRONG -> 0.9
            TrudyConfidence.MODERATE -> 0.75
            TrudyConfidence.LOW -> 0.55
            TrudyConfidence.INSUFFICIENT -> 0.25
        }
        return TrudySignalQuality(
            score = score,
            status = status,
            sampleCount = samples,
            expectedSampleCount = samples,
            missingFraction = 0.0,
            measurementFrequencyPerDay = 0.0,
            latestTimestampEpochMs = null,
            stale = status == TrudyDataQualityStatus.STALE,
            capture = TrudyCaptureDistribution(0.0, 0.0, 0.0, 0.0, 1.0, emptySet())
        )
    }

    private fun meaningfulChange(comparison: TrudyBaselineComparison): Boolean =
        abs(comparison.standardizedEffect ?: 0.0) >= MIN_STANDARDIZED_CHANGE ||
            abs(comparison.percentDelta ?: 0.0) >= MIN_PERCENT_CHANGE

    private fun changePriority(
        comparison: TrudyBaselineComparison,
        quality: TrudySignalQuality,
        relevance: Double
    ): Double {
        val effect = maxOf(
            (abs(comparison.standardizedEffect ?: 0.0) / 2.0).coerceIn(0.0, 1.0),
            (abs(comparison.percentDelta ?: 0.0) / 30.0).coerceIn(0.0, 1.0)
        )
        return (effect * quality.score * relevance).coerceIn(0.0, 1.0)
    }

    private fun favourableDirection(preference: TrudyMetricPreference, delta: Double) = when (preference) {
        TrudyMetricPreference.HIGHER_IS_FAVOURABLE -> if (delta > 0) TrudyEffectDirection.INCREASE else TrudyEffectDirection.DECREASE
        TrudyMetricPreference.LOWER_IS_FAVOURABLE -> if (delta < 0) TrudyEffectDirection.INCREASE else TrudyEffectDirection.DECREASE
        TrudyMetricPreference.CONTEXT_DEPENDENT -> TrudyEffectDirection.UNKNOWN
    }

    private fun worse(a: TrudyDataQualityStatus, b: TrudyDataQualityStatus): TrudyDataQualityStatus {
        val severity = mapOf(
            TrudyDataQualityStatus.GOOD to 0,
            TrudyDataQualityStatus.LIMITED to 1,
            TrudyDataQualityStatus.SPARSE to 2,
            TrudyDataQualityStatus.STALE to 3,
            TrudyDataQualityStatus.INSUFFICIENT to 4
        )
        return if (severity.getValue(a) >= severity.getValue(b)) a else b
    }

    private fun expectedConfounders(
        operation: InvestigateChange,
        available: Set<Pair<HealthDomain, String>>
    ): List<TrudyMissingEvidence> {
        val target = operation.targets.first()
        val confounders = when {
            target.domain == HealthDomain.SLEEP -> listOf(
                "subjective_sleep_quality" to "Subjective sleep quality",
                "medication_change" to "Medication changes",
                "illness_symptoms" to "Illness or symptoms",
                "alcohol_timing" to "Alcohol amount and timing"
            )
            target.metricId.contains("heart_rate") -> listOf(
                "illness_symptoms" to "Illness or symptoms",
                "medication_change" to "Medication changes",
                "measurement_context" to "Posture and measurement context"
            )
            else -> emptyList()
        }
        val availableIds = available.map { it.second }.toSet()
        return confounders.filterNot { it.first in availableIds }.map {
            TrudyMissingEvidence(null, it.first, it.second, TrudyEvidenceGapReason.UNMEASURED_CONFOUNDER)
        }
    }

    private fun combineQuality(items: List<TrudySignalQuality>): TrudySignalQuality {
        if (items.isEmpty()) return fallbackQuality(0, TrudyDataQualityStatus.INSUFFICIENT, TrudyConfidence.INSUFFICIENT)
        val samples = items.sumOf { it.sampleCount }
        val expected = items.sumOf { it.expectedSampleCount }
        val status = items.map { it.status }.reduce(::worse)
        val sources = items.flatMap { it.capture.sources }.take(MAX_SOURCES).toSet()
        return TrudySignalQuality(
            score = items.map { it.score }.average().coerceIn(0.0, 1.0),
            status = status,
            sampleCount = samples,
            expectedSampleCount = expected,
            missingFraction = items.map { it.missingFraction }.average().coerceIn(0.0, 1.0),
            matchedFraction = items.mapNotNull { it.matchedFraction }.takeIf { it.isNotEmpty() }?.average(),
            variance = null,
            measurementFrequencyPerDay = items.sumOf { it.measurementFrequencyPerDay },
            latestTimestampEpochMs = items.mapNotNull { it.latestTimestampEpochMs }.maxOrNull(),
            stale = items.all { it.stale },
            capture = TrudyCaptureDistribution(
                wearableFraction = items.map { it.capture.wearableFraction }.average(),
                manualFraction = items.map { it.capture.manualFraction }.average(),
                derivedFraction = items.map { it.capture.derivedFraction }.average(),
                deviceFraction = items.map { it.capture.deviceFraction }.average(),
                unknownFraction = items.map { it.capture.unknownFraction }.average(),
                sources = sources
            ),
            notes = items.flatMap { it.notes }.distinct().take(MAX_NOTES)
        )
    }

    private fun confidenceFor(
        quality: TrudySignalQuality,
        findings: List<TrudyInvestigationFinding>,
        related: List<TrudyRelatedSignal>
    ): TrudyConfidence = when {
        quality.status == TrudyDataQualityStatus.INSUFFICIENT || (findings.isEmpty() && related.none { it.classification == TrudyFindingClassification.POSSIBLE_ASSOCIATION }) -> TrudyConfidence.INSUFFICIENT
        quality.score >= 0.82 && quality.sampleCount >= 30 -> TrudyConfidence.STRONG
        quality.score >= 0.66 && quality.sampleCount >= 14 -> TrudyConfidence.MODERATE
        else -> TrudyConfidence.LOW
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_EXPECTED_SAMPLES = 90
        const val MAX_SOURCES = 8
        const val MAX_NOTES = 12
        const val MIN_ASSOCIATION_SAMPLES = 5
        const val MIN_MATCHED_FRACTION = 0.45
        const val MIN_REPORTABLE_ASSOCIATION = 0.30
        const val MIN_REPORTABLE_QUALITY = 0.45
        const val MIN_STANDARDIZED_CHANGE = 0.50
        const val MIN_PERCENT_CHANGE = 8.0
        const val MIN_VARIANCE = 1e-9
    }
}
