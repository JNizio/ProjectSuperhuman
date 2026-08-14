package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrudyQualityV3IntegrationTest {
    private val boundaries = object : TrudyTemporalBoundaryProvider {
        override fun nowEpochMs() = 100L * DAY_MS
        override fun startOfTodayEpochMs() = 100L * DAY_MS
        override fun startOfWeekEpochMs() = 96L * DAY_MS
        override fun startOfMonthEpochMs(monthsAgo: Int) = (90L - monthsAgo * 30L) * DAY_MS
    }
    private val planner = TrudyLanguageAwarePreflightPlanner(
        TrudySystemInvestigationPlanner(TrudyTemporalResolver(boundaries))
    )

    @Test
    fun sleepSlangFlowsThroughLanguageRouterIntoSingleInvestigationPath() {
        val operation = assertIs<InvestigateChange>(
            planner.plan(TrudyAskRequest("I slept like crap.")).single()
        )
        assertEquals(TrudyChangeClaim.WORSENED, operation.claim)
        assertTrue(operation.targets.any { it.domain == HealthDomain.SLEEP && it.metricId == "sleep_score" })
    }

    @Test
    fun fatigueAndRacingHeartSlangRouteToCanonicalDataWithoutBroadDomainSweep() {
        val fatigue = planner.plan(TrudyAskRequest("I'm knackered."))
        val racing = planner.plan(TrudyAskRequest("My heart is racing."))

        assertTrue(fatigue.isNotEmpty())
        assertTrue(fatigue.flatMap { it.domains }.all { it == HealthDomain.EMOTIONAL })
        assertTrue(racing.isNotEmpty())
        assertTrue(racing.flatMap { it.domains }.all { it == HealthDomain.EXERCISE })
        assertTrue(racing.flatMap(::metricIds).any { it == "heart_rate_avg_bpm" || it == "resting_heart_rate_bpm" })
    }

    @Test
    fun multiIntentLanguageStaysBoundedAndKeepsDistinctLanes() {
        val routing = TrudyLanguageRouter.route("I've been tired, bloated and sleeping badly.")
        val domains = routing.matches.flatMap { it.domains }.toSet()

        assertTrue(routing.matches.size in 2..TrudyLanguageRouter.DEFAULT_MAX_TOPICS)
        assertTrue(HealthDomain.SLEEP in domains)
        assertTrue(HealthDomain.EMOTIONAL in domains)
        assertFalse(routing.productOnlyIntent)
    }

    @Test
    fun gordAndOvernightWeightKeepTheirSpecificSemanticRoutes() {
        val reflux = TrudyLanguageRouter.route("Could this be GORD?")
        val weight = TrudyLanguageRouter.route("My weight shot up overnight.")

        assertTrue(reflux.matches.any { it.semanticId == "gastro_oesophageal_reflux" })
        assertFalse(reflux.matches.any { it.semanticId == "abdominal_pain" })
        assertTrue(weight.matches.any { it.phraseClass == TrudyPhraseClass.BODY_WEIGHT })
        assertTrue(weight.matches.flatMap { it.domains }.contains(HealthDomain.BODY))
    }

    @Test
    fun structuredPremiseStatusControlsAnswerClassification() {
        val premise = TrudyAnswerEvidence(
            id = "premise",
            classification = TrudyAnswerEvidenceClass.USABLE,
            kind = TrudyAnswerEvidenceKind.PREMISE,
            domain = HealthDomain.SLEEP,
            label = "Sleep premise",
            summary = "Legacy premise",
            relevanceScore = 100.0
        )
        val base = TrudyAnswerPlan(
            intent = TrudyAnswerIntent.CAUSE,
            timeframeLabel = "the last 7 days",
            allEvidence = listOf(premise),
            rankedEvidence = listOf(premise),
            limitations = emptyList(),
            targetSentenceCount = 3
        )
        val structured = minimalInvestigation(
            premise = TrudyPremiseStatus.PREMISE_NOT_SUPPORTED,
            findings = listOf(minimalFinding(HealthDomain.SLEEP, "sleep_score", priority = 1.0))
        )
        val toolResult = ChangeInvestigationResult(
            operation = minimalOperation(),
            investigation = minimalLegacyInvestigation(structured)
        )

        val refined = TrudyStructuredInvestigationAnswerBridge.refine(base, listOf(toolResult))

        val refinedPremise = refined.allEvidence.first { it.kind == TrudyAnswerEvidenceKind.PREMISE }
        assertEquals(TrudyAnswerEvidenceClass.CONTRADICTORY, refinedPremise.classification)
        assertTrue("do not support" in refinedPremise.summary)
    }

    @Test
    fun structuredMissingAndStaleEvidenceCannotBecomeTopFinding() {
        val metric = TrudyAnswerEvidence(
            id = "sleep-score",
            classification = TrudyAnswerEvidenceClass.USABLE,
            kind = TrudyAnswerEvidenceKind.TREND,
            domain = HealthDomain.SLEEP,
            metricId = "sleep_score",
            label = "Sleep score",
            summary = "Changed",
            relevanceScore = 90.0
        )
        val base = TrudyAnswerPlan(
            intent = TrudyAnswerIntent.PRIORITY,
            timeframeLabel = "today",
            allEvidence = listOf(metric),
            rankedEvidence = listOf(metric),
            limitations = emptyList(),
            targetSentenceCount = 2
        )
        val structured = minimalInvestigation(
            premise = TrudyPremiseStatus.NOT_APPLICABLE,
            gaps = listOf(
                TrudyMissingEvidence(
                    domain = HealthDomain.SLEEP,
                    metricId = "sleep_score",
                    label = "Sleep score",
                    reason = TrudyEvidenceGapReason.STALE_DATA
                )
            )
        )
        val refined = TrudyStructuredInvestigationAnswerBridge.refine(
            base,
            listOf(ChangeInvestigationResult(minimalOperation(), minimalLegacyInvestigation(structured)))
        )

        assertTrue(refined.rankedEvidence.none { it.metricId == "sleep_score" })
        assertTrue(refined.limitations.any {
            it.metricId == "sleep_score" && it.classification == TrudyAnswerEvidenceClass.STALE
        })
    }

    @Test
    fun answerSelectedEvidenceIsSmallerThanRetrievedEvidence() {
        val finding = TrudyAnswerEvidence(
            id = "sleep-score",
            classification = TrudyAnswerEvidenceClass.USABLE,
            kind = TrudyAnswerEvidenceKind.OBSERVATION,
            domain = HealthDomain.SLEEP,
            metricId = "sleep_score",
            label = "Sleep score",
            summary = "Sleep score supported the answer.",
            relevanceScore = 100.0
        )
        val plan = TrudyAnswerPlan(
            intent = TrudyAnswerIntent.PERSONAL_SUMMARY,
            timeframeLabel = "the last 7 days",
            allEvidence = listOf(finding),
            rankedEvidence = listOf(finding),
            limitations = emptyList(),
            targetSentenceCount = 2
        )
        val available = (1L..24L).map { timestamp ->
            TrudyEvidenceReference(
                domain = HealthDomain.SLEEP,
                metricId = "sleep_score",
                evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION,
                timestampEpochMs = timestamp
            )
        }

        val used = TrudyUsedAnswerEvidenceSelector.select(plan, available, available)

        assertEquals(24, available.size)
        assertEquals(5, used.size)
        assertTrue(used.all { it.reference.metricId == "sleep_score" })
    }

    private fun metricIds(operation: TrudyToolOperation): List<String> = when (operation) {
        is TrudyToolOperation.GetMetricHistory -> listOf(operation.metricId)
        is TrudyToolOperation.GetMetricWindow -> listOf(operation.metricId)
        is CompareBaseline -> listOf(operation.metricId)
        is GetPersonalTrend -> listOf(operation.metricId)
        is InvestigateChange -> (operation.targets + operation.related).map { it.metricId }
        is GetAssociation -> listOf(operation.leftMetricId, operation.rightMetricId)
        is GetLaggedAssociation -> listOf(operation.leftMetricId, operation.rightMetricId)
        else -> emptyList()
    }

    private fun minimalOperation() = InvestigateChange(
        targets = listOf(
            TrudyInvestigationMetric(
                HealthDomain.SLEEP,
                "sleep_score",
                TrudyInvestigationRole.PRIMARY,
                TrudyMetricPreference.HIGHER_IS_FAVOURABLE
            )
        ),
        related = emptyList(),
        observationWindow = recent,
        baselineWindow = baseline,
        claim = TrudyChangeClaim.WORSENED
    )

    private fun minimalLegacyInvestigation(structured: TrudyInvestigationResult) = TrudyChangeInvestigation(
        premiseAssessment = when (structured.premiseStatus) {
            TrudyPremiseStatus.PREMISE_SUPPORTED -> TrudyPremiseAssessment.SUPPORTED
            TrudyPremiseStatus.PREMISE_NOT_SUPPORTED -> TrudyPremiseAssessment.NOT_SUPPORTED
            TrudyPremiseStatus.PREMISE_MIXED -> TrudyPremiseAssessment.MIXED
            TrudyPremiseStatus.PREMISE_UNVERIFIABLE,
            TrudyPremiseStatus.NOT_APPLICABLE -> TrudyPremiseAssessment.INSUFFICIENT
        },
        observationWindow = recent,
        baselineWindow = baseline,
        targetComparisons = emptyList(),
        relatedAssociations = emptyList(),
        missingMetrics = emptyList(),
        caveats = listOf("Observational evidence does not establish causation."),
        structuredResult = structured
    )

    private fun minimalInvestigation(
        premise: TrudyPremiseStatus,
        findings: List<TrudyInvestigationFinding> = emptyList(),
        gaps: List<TrudyMissingEvidence> = emptyList()
    ) = TrudyInvestigationResult(
        target = TrudyInvestigationTarget(
            domain = HealthDomain.SLEEP,
            primaryMetricId = "sleep_score",
            supportingMetricIds = emptyList(),
            label = "sleep",
            includesSubjectiveClaim = premise != TrudyPremiseStatus.NOT_APPLICABLE
        ),
        timeframe = TrudyInvestigationTimeframe(recent, baseline, "the last 7 days", explicit = true),
        premiseStatus = premise,
        importantFindings = findings,
        relatedSignals = emptyList(),
        missingEvidence = gaps,
        quality = quality(),
        confidence = TrudyConfidence.MODERATE,
        caveats = listOf("Observed personal relationships do not establish causation."),
        execution = TrudyInvestigationExecutionStats(1, 1, 14, 0, 256, 56)
    )

    private fun minimalFinding(domain: HealthDomain, metricId: String, priority: Double) =
        TrudyInvestigationFinding(
            classification = TrudyFindingClassification.OBSERVED_CHANGE,
            domain = domain,
            metricId = metricId,
            recentMean = 78.0,
            baselineMean = 68.0,
            absoluteDelta = 10.0,
            percentDelta = 14.7,
            standardizedEffect = .6,
            favourableDirection = TrudyEffectDirection.INCREASE,
            priorityScore = priority,
            quality = quality(),
            evidence = PersonalEvidenceItem(
                id = "finding:$metricId",
                domains = listOf(domain),
                metricIds = listOf(metricId),
                evidenceType = PersonalEvidenceType.TREND,
                observationWindow = recent,
                comparisonWindow = baseline,
                effectDirection = TrudyEffectDirection.INCREASE,
                effectMagnitude = 10.0,
                sampleCount = 14,
                confidence = TrudyConfidence.MODERATE,
                dataQualityStatus = TrudyDataQualityStatus.GOOD
            )
        )

    private fun quality() = TrudySignalQuality(
        score = .9,
        status = TrudyDataQualityStatus.GOOD,
        sampleCount = 7,
        expectedSampleCount = 7,
        missingFraction = 0.0,
        variance = 1.0,
        measurementFrequencyPerDay = 1.0,
        latestTimestampEpochMs = 100L * DAY_MS,
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

    private companion object {
        const val DAY_MS = 86_400_000L
        val recent = TrudyTimeRange(93L * DAY_MS, 100L * DAY_MS)
        val baseline = TrudyTimeRange(86L * DAY_MS, 93L * DAY_MS - 1L)
    }
}
