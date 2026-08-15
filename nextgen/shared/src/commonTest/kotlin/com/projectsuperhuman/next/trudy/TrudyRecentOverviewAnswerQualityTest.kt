package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrudyRecentOverviewAnswerQualityTest {
    @Test
    fun meaningfulChangesLeadAndMissingPlannedMetricStaysSecondary() {
        val sleep = finding(HealthDomain.SLEEP, "sleep_score", 74.0, 82.0, .9)
        val heart = finding(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 66.0, 61.0, .7)
        val mindfulnessMissing = missingTrend(HealthDomain.MINDFULNESS, "mindfulness_session_minutes")
        val plan = plan(
            evidence = listOf(
                trend(HealthDomain.SLEEP, "sleep_score", 74.0, 82.0),
                trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 66.0, 61.0, "bpm"),
                trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L"),
                trend(HealthDomain.EMOTIONAL, "emotional_valence", 7.0, 7.1),
                mindfulnessMissing
            ),
            limitations = listOf(mindfulnessMissing)
        )
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan,
                investigation(
                    findings = listOf(sleep, heart),
                    missing = listOf(
                        TrudyMissingEvidence(
                            HealthDomain.MINDFULNESS,
                            "mindfulness_session_minutes",
                            "mindfulness",
                            TrudyEvidenceGapReason.NO_DATA
                        )
                    )
                )
            )
        )

        assertTrue("Sleep is the clearest recent change" in briefing.answerText)
        assertTrue("fell from 82 to 74" in briefing.answerText)
        assertTrue("resting heart rate" in briefing.answerText.lowercase())
        assertTrue("mindfulness" in briefing.answerText.lowercase())
        assertFalse(briefing.answerText.lowercase().startsWith("mindfulness"))
        assertEquals(2, briefing.selectedMetrics.size)
    }

    @Test
    fun enoughStableCoverageIsStableButPartialCoverageIsNot() {
        val fourSignals = listOf(
            trend(HealthDomain.SLEEP, "sleep_score", 80.0, 80.5),
            trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 61.0, 60.5, "bpm"),
            trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L"),
            trend(HealthDomain.EMOTIONAL, "emotional_valence", 7.0, 7.1)
        )
        val stable = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(plan(fourSignals), investigation(emptyList()))
        )
        val partial = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(plan(fourSignals.take(3)), investigation(emptyList()))
        )

        assertTrue(stable.stableWithUsableData)
        assertEquals(4, stable.assessedMetricCount)
        assertTrue(stable.answerText.startsWith("Nothing major stands out recently."))
        assertEquals(3, stable.selectedMetrics.size)
        assertFalse(partial.stableWithUsableData)
        assertTrue("too much of the rest" in partial.answerText.lowercase())
        assertTrue(partial.selectedMetrics.isEmpty())
    }

    @Test
    fun genuinelySparseOverviewRemainsInsufficient() {
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(emptyList()),
                investigation(
                    emptyList(),
                    missing = listOf(
                        TrudyMissingEvidence(HealthDomain.SLEEP, "sleep_score", "sleep", TrudyEvidenceGapReason.SPARSE_DATA)
                    )
                )
            )
        )

        assertFalse(briefing.stableWithUsableData)
        assertTrue("don't have enough reliable recent data" in briefing.answerText.lowercase())
        assertTrue(briefing.selectedMetrics.isEmpty())
    }

    @Test
    fun broadOverviewCapsHeadlinesAtThreeDistinctDomainsAndIncludesImprovements() {
        val findings = listOf(
            finding(HealthDomain.HYDRATION, "water_total_l", 3.0, 2.2, .95),
            finding(HealthDomain.SLEEP, "sleep_score", 84.0, 76.0, .9),
            finding(HealthDomain.EMOTIONAL, "emotional_valence", 8.0, 6.0, .8),
            finding(HealthDomain.BODY, "body_weight_kg", 78.0, 77.0, .7)
        )
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(findings.map { trend(it.domain, it.metricId, it.recentMean, it.baselineMean, unitFor(it.metricId)) }),
                investigation(findings)
            )
        )

        assertEquals(3, briefing.selectedMetrics.size)
        assertTrue("hydration" in briefing.answerText.lowercase())
        assertTrue("rose from" in briefing.answerText.lowercase())
        assertFalse("weight" in briefing.answerText.lowercase())
    }

    @Test
    fun lowVarianceFindingCannotBecomeHeadline() {
        val sleep = finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .95)
        val lowSleep = lowQuality(HealthDomain.SLEEP, "sleep_score")
        val plan = plan(
            evidence = listOf(
                trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0),
                trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 61.0, 60.5, "bpm"),
                trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L"),
                trend(HealthDomain.EMOTIONAL, "emotional_valence", 7.0, 7.1),
                trend(HealthDomain.BODY, "body_weight_kg", 77.0, 77.1, "kg")
            ),
            limitations = listOf(lowSleep)
        )
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan,
                investigation(
                    listOf(sleep),
                    missing = listOf(
                        TrudyMissingEvidence(
                            HealthDomain.SLEEP,
                            "sleep_score",
                            "sleep",
                            TrudyEvidenceGapReason.LOW_VARIANCE
                        )
                    )
                )
            )
        )

        assertFalse("sleep is the clearest" in briefing.answerText.lowercase())
        assertTrue(briefing.stableWithUsableData)
    }

    @Test
    fun smallCelsiusShiftCannotBecomeHeadlineFromPercentageMathAlone() {
        val temperature = finding(
            HealthDomain.ENVIRONMENT,
            "environment_temperature_c",
            recent = 10.5,
            baseline = 10.0,
            priority = .95,
            standardizedEffect = 1.0
        )
        val evidence = listOf(
            trend(HealthDomain.ENVIRONMENT, "environment_temperature_c", 10.5, 10.0, "°C"),
            trend(HealthDomain.SLEEP, "sleep_score", 80.0, 80.5),
            trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 61.0, 60.5, "bpm"),
            trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L"),
            trend(HealthDomain.EMOTIONAL, "emotional_valence", 7.0, 7.1)
        )
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(plan(evidence), investigation(listOf(temperature)))
        )

        assertFalse("outdoor temperature is the clearest" in briefing.answerText.lowercase())
        assertTrue(briefing.stableWithUsableData)
    }

    @Test
    fun unmeasuredSleepConfoundersNeverLeakIntoBroadOverviewLimitations() {
        val evidence = listOf(
            trend(HealthDomain.SLEEP, "sleep_score", 80.0, 80.5),
            trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 61.0, 60.5, "bpm"),
            trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L"),
            trend(HealthDomain.EMOTIONAL, "emotional_valence", 7.0, 7.1)
        )
        val confounder = TrudyAnswerEvidence(
            id = "missing-medication",
            classification = TrudyAnswerEvidenceClass.LOW_QUALITY,
            kind = TrudyAnswerEvidenceKind.AVAILABILITY,
            metricId = "medication_change",
            label = "Medication changes",
            summary = "not measured"
        )
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(evidence, limitations = listOf(confounder)),
                investigation(emptyList())
            )
        )

        assertFalse("medication" in briefing.answerText.lowercase())
        assertTrue(briefing.stableWithUsableData)
    }

    @Test
    fun naturalAliasesReachOverviewWhileNamedDomainsRemainFocused() {
        val broad = TrudyRecentOverviewAnswerQuality.planningText("Give me a health overview this week")
        val focused = TrudyRecentOverviewAnswerQuality.planningText("Give me an overview of my sleep this week")

        assertTrue("What should I know?" in broad)
        assertTrue("this week" in broad)
        assertEquals("Give me an overview of my sleep this week", focused)
        assertEquals("What should I know recently?", TrudyRecentOverviewAnswerQuality.planningText("What should I know recently?"))
    }

    @Test
    fun focusedInvestigationAndSafetyPlansAreNeverOverridden() {
        val focused = TrudyRecentOverviewAnswerQuality.compose(
            plan(listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0))),
            investigation(listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)), targetLabel = "sleep")
        )
        val urgent = TrudyRecentOverviewAnswerQuality.compose(
            plan(
                listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0)),
                safety = TrudyAnswerSafetyLevel.URGENT
            ),
            investigation(listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)))
        )

        assertNull(focused)
        assertNull(urgent)
    }

    @Test
    fun deterministicBriefingNeverLeaksInternalAnalyticsLanguage() {
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0))),
                investigation(listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)))
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
        ).forEach { assertFalse(it in text) }
    }

    @Test
    fun evidenceFilteringIsAnswerAlignedAndStableEvidenceIsRepresentative() {
        val changed = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0))),
                investigation(listOf(finding(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0, .9)))
            )
        )
        assertTrue(changed.keeps(HealthDomain.SLEEP, "sleep_score"))
        assertFalse(changed.keeps(HealthDomain.MINDFULNESS, "mindfulness_session_minutes"))

        val stableEvidence = listOf(
            trend(HealthDomain.SLEEP, "sleep_score", 80.0, 80.5),
            trend(HealthDomain.EXERCISE, "resting_heart_rate_bpm", 61.0, 60.5, "bpm"),
            trend(HealthDomain.HYDRATION, "water_total_l", 2.5, 2.6, "L"),
            trend(HealthDomain.EMOTIONAL, "emotional_valence", 7.0, 7.1),
            trend(HealthDomain.BODY, "body_weight_kg", 77.0, 77.1, "kg")
        )
        val stable = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(plan(stableEvidence), investigation(emptyList()))
        )
        assertEquals(3, stable.selectedMetrics.size)
        assertFalse(stable.keeps(HealthDomain.BODY, "body_weight_kg"))
    }

    @Test
    fun structuredFindingProvenanceBacksChangedBriefingEvidence() {
        val reference = TrudyEvidenceReference(
            HealthDomain.SLEEP,
            "sleep_score",
            evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION,
            timestampEpochMs = RANGE.toEpochMs
        )
        val sleep = finding(
            HealthDomain.SLEEP,
            "sleep_score",
            70.0,
            82.0,
            .9,
            supportingReferences = listOf(reference)
        )
        val investigation = investigation(listOf(sleep))
        val briefing = requireNotNull(
            TrudyRecentOverviewAnswerQuality.compose(
                plan(listOf(trend(HealthDomain.SLEEP, "sleep_score", 70.0, 82.0))),
                investigation
            )
        )

        assertEquals(listOf(reference), briefing.structuredSupportingReferences(investigation))
    }

    private fun TrudyRecentOverviewBriefing.keeps(domain: HealthDomain, metric: String) =
        TrudyRecentOverviewAnswerQuality.shouldKeepReference(
            this,
            TrudyEvidenceReference(domain, metric, evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION)
        )

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

    private fun missingTrend(domain: HealthDomain, metric: String) = TrudyAnswerEvidence(
        id = "trend-missing:$domain:$metric",
        classification = TrudyAnswerEvidenceClass.MISSING,
        kind = TrudyAnswerEvidenceKind.TREND,
        domain = domain,
        metricId = metric,
        label = metric,
        summary = "missing comparison"
    )

    private fun lowQuality(domain: HealthDomain, metric: String) = TrudyAnswerEvidence(
        id = "low-quality:$domain:$metric",
        classification = TrudyAnswerEvidenceClass.LOW_QUALITY,
        kind = TrudyAnswerEvidenceKind.AVAILABILITY,
        domain = domain,
        metricId = metric,
        label = metric,
        summary = "low variance"
    )

    private fun finding(
        domain: HealthDomain,
        metric: String,
        recent: Double,
        baseline: Double,
        priority: Double,
        standardizedEffect: Double = 1.0,
        supportingReferences: List<TrudyEvidenceReference> = emptyList()
    ) = TrudyInvestigationFinding(
        classification = TrudyFindingClassification.OBSERVED_CHANGE,
        domain = domain,
        metricId = metric,
        recentMean = recent,
        baselineMean = baseline,
        absoluteDelta = recent - baseline,
        percentDelta = if (baseline == 0.0) null else (recent - baseline) / baseline * 100.0,
        standardizedEffect = standardizedEffect,
        favourableDirection = if (recent >= baseline) TrudyEffectDirection.INCREASE else TrudyEffectDirection.DECREASE,
        priorityScore = priority,
        quality = quality(),
        evidence = personalEvidence(domain, metric, supportingReferences)
    )

    private fun investigation(
        findings: List<TrudyInvestigationFinding>,
        missing: List<TrudyMissingEvidence> = emptyList(),
        targetLabel: String = "recent health overview",
        requestedMetricCount: Int = 8
    ) = TrudyInvestigationResult(
        target = TrudyInvestigationTarget(
            domain = HealthDomain.SLEEP,
            primaryMetricId = "sleep_score",
            supportingMetricIds = emptyList(),
            label = targetLabel,
            includesSubjectiveClaim = false
        ),
        timeframe = TrudyInvestigationTimeframe(RANGE, BASELINE, "recently", explicit = true),
        premiseStatus = TrudyPremiseStatus.NOT_APPLICABLE,
        importantFindings = findings,
        relatedSignals = emptyList(),
        missingEvidence = missing,
        quality = quality(),
        confidence = TrudyConfidence.MODERATE,
        caveats = listOf("Observed changes do not establish causation."),
        execution = TrudyInvestigationExecutionStats(
            requestedMetricCount = requestedMetricCount,
            requestedDomainCount = requestedMetricCount,
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
        measurementFrequencyPerDay = 1.0,
        latestTimestampEpochMs = RANGE.toEpochMs,
        stale = false,
        capture = TrudyCaptureDistribution(1.0, 0.0, 0.0, 0.0, 0.0, setOf("test"))
    )

    private fun personalEvidence(
        domain: HealthDomain,
        metric: String,
        supportingReferences: List<TrudyEvidenceReference> = emptyList()
    ) = PersonalEvidenceItem(
        id = "evidence:$domain:$metric",
        domains = listOf(domain),
        metricIds = listOf(metric),
        evidenceType = PersonalEvidenceType.TREND,
        observationWindow = RANGE,
        comparisonWindow = BASELINE,
        effectDirection = TrudyEffectDirection.UNKNOWN,
        sampleCount = 14,
        confidence = TrudyConfidence.MODERATE,
        dataQualityStatus = TrudyDataQualityStatus.GOOD,
        supportingEvidenceReferences = supportingReferences
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
