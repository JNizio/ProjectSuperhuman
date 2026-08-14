package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs

/**
 * Compatibility factory for older callers/tests that construct the pre-v2 investigation shape.
 * The production investigator always supplies [TrudyChangeInvestigation.structuredResult] directly;
 * this overload keeps Agent 2-era callers source-compatible without making the new field nullable.
 */
@Suppress("FunctionName")
fun TrudyChangeInvestigation(
    premiseAssessment: TrudyPremiseAssessment,
    observationWindow: TrudyTimeRange,
    baselineWindow: TrudyTimeRange,
    targetComparisons: List<TrudyBaselineComparison>,
    relatedAssociations: List<TrudyAssociationResult>,
    missingMetrics: List<Pair<HealthDomain, String>>,
    caveats: List<String>
): TrudyChangeInvestigation {
    val primary = targetComparisons.firstOrNull()
    val fallbackMissing = missingMetrics.firstOrNull()
    val targetDomain = primary?.domain ?: fallbackMissing?.first ?: HealthDomain.SLEEP
    val targetMetric = primary?.metricId ?: fallbackMissing?.second ?: "unknown_metric"
    val targetQuality = primary?.toLegacySignalQuality() ?: emptyLegacyQuality()
    val findings = targetComparisons.mapNotNull { comparison ->
        val recent = comparison.observationMean ?: return@mapNotNull null
        val base = comparison.baselineMean ?: return@mapNotNull null
        val delta = comparison.absoluteDelta ?: return@mapNotNull null
        TrudyInvestigationFinding(
            classification = if (comparison.confidence == TrudyConfidence.INSUFFICIENT) {
                TrudyFindingClassification.INSUFFICIENT_EVIDENCE
            } else {
                TrudyFindingClassification.OBSERVED_CHANGE
            },
            domain = comparison.domain,
            metricId = comparison.metricId,
            recentMean = recent,
            baselineMean = base,
            absoluteDelta = delta,
            percentDelta = comparison.percentDelta,
            standardizedEffect = comparison.standardizedEffect,
            favourableDirection = comparison.direction,
            priorityScore = if (comparison == primary) 1.0 else .75,
            quality = comparison.toLegacySignalQuality(),
            evidence = comparison.evidence
        )
    }
    val related = relatedAssociations.map { association ->
        TrudyRelatedSignal(
            classification = if (association.coefficient == null || association.confidence == TrudyConfidence.INSUFFICIENT) {
                TrudyFindingClassification.INSUFFICIENT_EVIDENCE
            } else {
                TrudyFindingClassification.POSSIBLE_ASSOCIATION
            },
            domain = association.leftDomain,
            metricId = association.leftMetricId,
            targetDomain = association.rightDomain,
            targetMetricId = association.rightMetricId,
            temporalAlignment = TrudyTemporalAlignment.SAME_DAY,
            coefficient = association.coefficient,
            relationshipScore = association.coefficient?.let(::abs)?.coerceIn(0.0, 1.0) ?: 0.0,
            relevanceWeight = .75,
            sampleCount = association.sampleCount,
            quality = association.toLegacySignalQuality(),
            evidence = association.evidence
        )
    }
    val missing = missingMetrics.map { (domain, metricId) ->
        TrudyMissingEvidence(
            domain = domain,
            metricId = metricId,
            label = humanMetricLabel(metricId),
            reason = TrudyEvidenceGapReason.NO_DATA
        )
    }
    val premise = when (premiseAssessment) {
        TrudyPremiseAssessment.SUPPORTED -> TrudyPremiseStatus.PREMISE_SUPPORTED
        TrudyPremiseAssessment.NOT_SUPPORTED -> TrudyPremiseStatus.PREMISE_NOT_SUPPORTED
        TrudyPremiseAssessment.MIXED -> TrudyPremiseStatus.PREMISE_MIXED
        TrudyPremiseAssessment.INSUFFICIENT -> TrudyPremiseStatus.PREMISE_UNVERIFIABLE
    }
    val causalCaveats = (caveats + "Observed personal relationships do not establish causation.").distinct()
    val structured = TrudyInvestigationResult(
        target = TrudyInvestigationTarget(
            domain = targetDomain,
            primaryMetricId = targetMetric,
            supportingMetricIds = targetComparisons.drop(1).map { it.metricId }.distinct(),
            label = humanMetricLabel(targetMetric),
            includesSubjectiveClaim = false
        ),
        timeframe = TrudyInvestigationTimeframe(
            recent = observationWindow,
            baseline = baselineWindow,
            label = "requested period",
            explicit = false
        ),
        premiseStatus = premise,
        importantFindings = findings,
        relatedSignals = related,
        missingEvidence = missing,
        quality = targetQuality,
        confidence = primary?.confidence ?: TrudyConfidence.INSUFFICIENT,
        caveats = causalCaveats,
        execution = TrudyInvestigationExecutionStats(
            requestedMetricCount = (targetComparisons.size + relatedAssociations.size).coerceAtLeast(1),
            requestedDomainCount = (targetComparisons.map { it.domain } + relatedAssociations.flatMap { listOf(it.leftDomain, it.rightDomain) }).distinct().size.coerceAtLeast(1),
            rowsInspected = targetComparisons.sumOf { it.observationSampleCount + it.baselineSampleCount } + relatedAssociations.sumOf { it.sampleCount },
            relationshipCount = relatedAssociations.size,
            maxRowsPerMetric = 256,
            maxLookbackDays = 56
        )
    )
    return TrudyChangeInvestigation(
        premiseAssessment = premiseAssessment,
        observationWindow = observationWindow,
        baselineWindow = baselineWindow,
        targetComparisons = targetComparisons,
        relatedAssociations = relatedAssociations,
        missingMetrics = missingMetrics,
        caveats = causalCaveats,
        structuredResult = structured
    )
}

private fun TrudyBaselineComparison.toLegacySignalQuality(): TrudySignalQuality = TrudySignalQuality(
    score = legacyQualityScore(dataQualityStatus, confidence),
    status = dataQualityStatus,
    sampleCount = observationSampleCount,
    expectedSampleCount = maxOf(observationSampleCount, baselineSampleCount, 1),
    missingFraction = if (observationSampleCount == 0) 1.0 else 0.0,
    variance = standardizedEffect?.let(::abs),
    measurementFrequencyPerDay = 0.0,
    latestTimestampEpochMs = evidence.supportingEvidenceReferences.mapNotNull { it.timestampEpochMs }.maxOrNull(),
    stale = dataQualityStatus == TrudyDataQualityStatus.STALE,
    capture = unknownCapture(evidence.supportingEvidenceReferences.mapNotNull { it.domain.name }.toSet())
)

private fun TrudyAssociationResult.toLegacySignalQuality(): TrudySignalQuality = TrudySignalQuality(
    score = legacyQualityScore(dataQualityStatus, confidence),
    status = dataQualityStatus,
    sampleCount = sampleCount,
    expectedSampleCount = sampleCount.coerceAtLeast(1),
    missingFraction = (1.0 - matchedFraction).coerceIn(0.0, 1.0),
    matchedFraction = matchedFraction,
    variance = coefficient?.let(::abs),
    measurementFrequencyPerDay = 0.0,
    latestTimestampEpochMs = evidence.supportingEvidenceReferences.mapNotNull { it.timestampEpochMs }.maxOrNull(),
    stale = dataQualityStatus == TrudyDataQualityStatus.STALE,
    capture = unknownCapture(evidence.supportingEvidenceReferences.mapNotNull { it.domain.name }.toSet())
)

private fun emptyLegacyQuality() = TrudySignalQuality(
    score = 0.0,
    status = TrudyDataQualityStatus.INSUFFICIENT,
    sampleCount = 0,
    expectedSampleCount = 1,
    missingFraction = 1.0,
    measurementFrequencyPerDay = 0.0,
    latestTimestampEpochMs = null,
    stale = false,
    capture = unknownCapture(emptySet())
)

private fun legacyQualityScore(status: TrudyDataQualityStatus, confidence: TrudyConfidence): Double {
    val statusScore = when (status) {
        TrudyDataQualityStatus.GOOD -> .9
        TrudyDataQualityStatus.LIMITED -> .6
        TrudyDataQualityStatus.SPARSE -> .45
        TrudyDataQualityStatus.STALE -> .35
        TrudyDataQualityStatus.INSUFFICIENT -> .2
    }
    val confidenceFactor = when (confidence) {
        TrudyConfidence.STRONG -> 1.0
        TrudyConfidence.MODERATE -> .9
        TrudyConfidence.LOW -> .75
        TrudyConfidence.INSUFFICIENT -> .5
    }
    return (statusScore * confidenceFactor).coerceIn(0.0, 1.0)
}

private fun unknownCapture(sources: Set<String>) = TrudyCaptureDistribution(
    wearableFraction = 0.0,
    manualFraction = 0.0,
    derivedFraction = 0.0,
    deviceFraction = 0.0,
    unknownFraction = 1.0,
    sources = sources
)
