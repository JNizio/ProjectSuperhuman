package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrudyRecentOverviewAnswerQualityTest {
    @Test
    fun meaningfulCrossDomainChangesBecomeNaturalBriefingAndMissingDataStaysSecondary() {
        val sleep = finding(HealthDomain.SLEEP, "sleep_score", recent = 74.0, baseline = 82.0, priority = .9)
        val heart = finding(HealthDomain.EXERCISE, "resting_heart_rate_bpm", recent = 66.0, baseline = 61.0, priority = .7)
        val plan = plan(
            evidence = listOf(
                trend(HealthDomain.SLEEP, "sleep_score", 74.0, 82.0, unit = ""),
                trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 66.0, 61.0, unit = "bpm")
            ),
            limitations = listOf(missing(HealthDomain.MINDFULNESS, "mindfulness_session_minutes"))
        )

        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan,
                investigation(findings = listOf(sleep, heart), missing = listOf(
                    TrudyMissingEvidence(
                        HealthDomain.MINDFULNESS,
                        "mindfulness_session_minutes",
                        "mindfulness",
                        TrudyEvidenceGapReason.NO_DATA
                    )
                ))
            )
        )

        assertTrue("Your sleep is the clearest recent change" in briefing.answerText)
        assertTrue("74" in briefing.answerText && "82" in briefing.answerText)
        assertTrue("resting heart rate" in briefing.answerText.lowercase())
        assertTrue("mindfulness" in briefing.answerText.lowercase())
        assertFalse(briefing.answerText.lowercase().startsWith("mindfulness"))
        assertEquals(2, briefing.selectedMetrics.size)
    }

    @Test
    fun enoughStableDataProducesStableAnswerRatherThanInsufficientEvidence() {
        val plan = plan(
            evidence = listOf(
                trend(HealthDomain.SLEEP, "sleep_score", 80.0, 80.5),
                trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 61.0, 60.5, "bpm"),
                trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L")
            )
        )

        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(plan, investigation(findings = emptyList()))
        )

        assertTrue(briefing.stableWithUsableData)
        assertTrue(briefing.answerText.startsWith("Nothing major stands out recently."))
        assertFalse("not enough" in briefing.answerText.lowercase())
    }

    @Test
    fun genuinelySparseOverviewRemainsInsufficient() {
        val plan = plan(evidence = emptyList())
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan,
                investigation(
                    findings = emptyList(),
                    missing = listOf(
                        TrudyMissingEvidence(HealthDomain.SLEEP, "sleep_score", "sleep", TrudyEvidenceGapReason.SPARSE_DATA)
                    )
                )
            )
        )

        assertFalse(briefing.stableWithUsableData)
        assertTrue("don't have enough reliable recent data" in briefing.answerText.lowercase())
    }

    @Test
    fun broadOverviewCapsHeadlinesAtThreeDistinctDomainsAndCanSurfaceImprovement() {
        val findings = listOf(
            finding(HealthDomain.HYDRATION, "water_total_l", 3.0, 2.2, .95),
            finding(HealthDomain.SLEEP, "sleep_score", 84.0, 76.0, .9),
            finding(HealthDomain.EMOTIONAL, "emotional_valence", 8.0, 6.0, .8),
            finding(HealthDomain.BODY, "body_weight_kg", 78.0, 77.0, .7)
        )
        val plan = plan(
            evidence = findings.map { trend(it.domain, it.metricId, it.recentMean, it.baselineMean, unitFor(it.metricId)) }
        )

        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(plan, investigation(findings = findings))
        )

        assertEquals(3, briefing.selectedMetrics.size)
        assertTrue("hydration" in briefing.answerText.lowercase())
        assertTrue("up from" in briefing.answerText.lowercase())
        assertFalse("weight" in briefing.answerText.lowercase())
    }

    @Test
    fun internalAnalyticsTerminologyNeverLeaksFromDeterministicBriefing() {
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(evidence = listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0))),
                investigation(findings = listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)))
            )
        )
        val text = briefing.answerText.lowercase()
        listOf(
            "bounded context",
            "structured evidence",
            "retrieved records",
            "investigation target",
            "observation window",
            "baseline window",
            "metric availability"
        ).forEach { forbidden -> assertFalse(forbidden in text) }
    }

    @Test
    fun safetyPlanIsNeverOverriddenByOverviewComposer() {
        val unsafeToOverride = plan(
            evidence = listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0)),
            safety = TrudyAnswerSafetyLevel.URGENT
        )

        assertNull(
            TrudyRecentOverviewAnswerQuality.compose(
                unsafeToOverride,
                investigation(findings = listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)))
            )
        )
    }

    @Test
    fun evidenceFilteringKeepsOnlyMetricsActuallyUsedByChangeBriefing() {
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(evidence = listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0))),
                investigation(findings = listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)))
            )
        )

        assertTrue(
            TrudyRecentOverviewAnswerQuality.shouldKeepReference(
                briefing,
                TrudyEvidenceReference(HealthDomain.SLEEP, "sleep_score", evidenceKind = TrudyEvidenceKind.RAW_OBSERVATION)
            )
        )
        assertFalse(
            TrudyRecentOverviewAnswerQuality.shouldKeepReference(
                briefing,
                TrudyEvidenceReference(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", evidenceKind = TrudyEvidenceKind.RAW_OBSERVATION)
            )
        )
    }

    private fun plan(
        evidence: List<TrudyAnswerEvidence>,
        limitations: List<TrudyAnswerEvidence> = emptyList(),
        safety: TrudyAnswerSafetyLevel = TrudyAnswerSafetyLevel.NONE
    ) = TrudyAnswerPlan(
        intent = TrudyAnswerIntent.PERSONAL_SUMMARY,
        timeframeLabel = "recently",
        allEvidence = evidence + limitations,
        rankedEvidence = evidence,
        limitations = limitations,
        targetSentenceCount = 3,
        safetyLevel = safety
    )

    private fun trend(
        domain: HealthDomain,
        metric: String,
        recent: Double,
        baseline: Double,
        unit: String = ""
    ) = TrudyAnswerEvidence(
        id = "trend:$domain:$metric",
        classification = TrudyAnswerEvidenceClass.USABLE,
        kind = TrudyAnswerEvidenceKind.TREND,
        domain = domain,
        metricId = metric,
        label = metric,
        summary = "comparison",
        sampleCount = 7,
        comparisonSampleCount = 7,
        meanValue = recent,
        baselineMean = baseline,
        delta = recent - baseline,
        unit = unit
    )

    private fun missing(domain: HealthDomain, metric: String) = TrudyAnswerEvidence(
        id = "missing:$domain:$metric",
        classification = TrudyAnswerEvidenceClass.MISSING,
        kind = TrudyAnswerEvidenceKind.AVAILABILITY,
        domain = domain,
        metricId = metric,
        label = metric,
        summary = "missing"
    )

    private fun finding(
        domain: HealthDomain,
        metric: String,
        recent: Double,
        baseline: Double,
        priority: Double
    ) = TrudyInvestigationFinding(
        classification = TrudyFindingClassification.OBSERVED_CHANGE,
        domain = domain,
        metricId = metric,
        recentMean = recent,
        baselineMean = baseline,
        absoluteDelta = recent - baseline,
        percentDelta = if (baseline == 0.0) null else (recent - baseline) / baseline * 100.0,
        standardizedEffect = 1.0,
        favourableDirection = if (recent >= baseline) TrudyEffectDirection.INCREASE else TrudyEffectDirection.DECREASE,
        priorityScore = priority,
        quality = quality(),
        evidence = personalEvidence(domain, metric)
    )

    private fun investigation(
        findings: List<TrudyInvestigationFinding>,
        missing: List<TrudyMissingEvidence> = emptyList()
    ) = TrudyInvestigationResult(
        target = TrudyInvestigationTarget(
            domain = HealthDomain.SLEEP,
            primaryMetricId = "sleep_score",
            supportingMetricIds = emptyList(),
            label = "recent health overview",
            includesSubjectiveClaim = false
        ),
        timeframe = TrudyInvestigationTimeframe(
            recent = RANGE,
            baseline = BASELINE,
            label = "recently",
            explicit = true
        ),
        premiseStatus = TrudyPremiseStatus.NOT_APPLICABLE,
        importantFindings = findings,
        relatedSignals = emptyList(),
        missingEvidence = missing,
        quality = quality(),
        confidence = TrudyConfidence.MODERATE,
        caveats = listOf("Observed changes do not establish causation."),
        execution = TrudyInvestigationExecutionStats(
            requestedMetricCount = 8,
            requestedDomainCount = 8,
            rowsInspected = 56,
            relationshipCount = 0,
            maxRowsPerMetric = 256,
            maxLookbackDays = 14
        )
    )

    private fun quality() = TrudySignalQuality(
        score = .9,
        status = TrudyDataQualityStatus.GOOD,
        sampleCount = 14,
        expectedSampleCount = 14,
        missingFraction = 0.0,
        matchedFraction = null,
        variance = 1.0,
        measurementFrequencyPerDay = 1.0,
        latestTimestampEpochMs = RANGE.toEpochMs,
        stale = false,
        capture = TrudyCaptureDistribution(
            wearableFraction = 1.0,
            manualFraction = 0.0,
            derivedFraction = 0.0,
            deviceFraction = 0.0,
            unknownFraction = 0.0,
            sources = setOf("test")
        )
    )

    private fun personalEvidence(domain: HealthDomain, metric: String) = PersonalEvidenceItem(
        id = "evidence:$domain:$metric",
        domains = listOf(domain),
        metricIds = listOf(metric),
        evidenceType = PersonalEvidenceType.TREND,
        observationWindow = RANGE,
        comparisonWindow = BASELINE,
        effectDirection = TrudyEffectDirection.UNKNOWN,
        sampleCount = 14,
        confidence = TrudyConfidence.MODERATE,
        dataQualityStatus = TrudyDataQualityStatus.GOOD
    )

    private fun unitFor(metric: String) = when (metric) {
        "resting_heart_rate_bpm" -> "bpm"
        "water_total_l" -> "L"
        "body_weight_kg" -> "kg"
        else -> ""
    }

    private companion object {
        val RANGE = TrudyTimeRange(7L * DAY_MS, 14L * DAY_MS - 1L)
        val BASELINE = TrudyTimeRange(0L, 7L * DAY_MS - 1L)
        const val DAY_MS = 86_400_000L
    }
}
