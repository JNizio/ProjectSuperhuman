package com.projectsuperhuman.next.trudy.conversation

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyTemporalBoundaryProvider
import com.projectsuperhuman.next.trudy.TrudyTimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrudyConversationEvidenceTest {
    private val clock = FixedBoundaries()

    @Test
    fun sleepQuestionThenLastMonthPreservesTopicAndMovesTimeframe() {
        val coordinator = coordinatorWithCompletedSleepTurn()

        val followUp = coordinator.prepare("What about last month?")

        assertEquals("sleep", followUp.resolvedRequest.topic)
        assertEquals(TrudyFollowUpKind.TIMEFRAME_SHIFT, followUp.resolvedRequest.followUpKind)
        assertEquals(clock.startOfMonthEpochMs(1), followUp.resolvedRequest.timeframe.range.fromEpochMs)
        assertEquals(TrudyEvidenceReuseAction.REFRESH, followUp.retrieval.action)
        assertEquals(TrudyEvidenceReuseReason.TIMEFRAME_CHANGED, followUp.retrieval.reason)
    }

    @Test
    fun sleepResultCanBeTheReferentForHeartRateQuestion() {
        val coordinator = coordinatorWithCompletedSleepTurn()

        val followUp = coordinator.prepare("Could that explain my heart rate?")

        assertEquals("heart_rate", followUp.resolvedRequest.topic)
        assertEquals(TrudyFollowUpKind.CROSS_DOMAIN_FOLLOW_UP, followUp.resolvedRequest.followUpKind)
        assertEquals(TrudyReferentKind.LATEST_RESULT, followUp.resolvedRequest.referent.kind)
        assertEquals("sleep-result", followUp.resolvedRequest.referent.resultId)
        assertTrue(HealthDomain.SLEEP in followUp.resolvedRequest.domains)
        assertTrue(HealthDomain.EXERCISE in followUp.resolvedRequest.domains)
    }

    @Test
    fun thatTrendResolvesToLatestStructuredResult() {
        val coordinator = coordinatorWithCompletedSleepTurn()

        val followUp = coordinator.prepare("Is that trend unusual?")

        assertEquals(TrudyReferentKind.LATEST_TREND, followUp.resolvedRequest.referent.kind)
        assertEquals("sleep-result", followUp.resolvedRequest.referent.resultId)
        assertEquals("sleep", followUp.resolvedRequest.referent.topic)
    }

    @Test
    fun missingBloodPressureYesterdayThenDayBeforeKeepsMetricAndShiftsExactly() {
        val coordinator = TrudyConversationEvidenceCoordinator(clock)
        val first = coordinator.prepare("What was my blood pressure yesterday?")
        val missing = TrudyMissingDataFinding(
            domain = HealthDomain.BLOOD_PRESSURE,
            metricIds = first.resolvedRequest.metrics.map { it.metricId },
            timeframe = first.resolvedRequest.timeframe,
            checkedAtEpochMs = clock.nowEpochMs()
        )
        coordinator.complete(first, TrudyAnswerTurnOutcome(missingDataFindings = listOf(missing)))

        val followUp = coordinator.prepare("What about the day before?")

        assertEquals("blood_pressure", followUp.resolvedRequest.topic)
        assertEquals(TrudyFollowUpKind.MISSING_DATA_FOLLOW_UP, followUp.resolvedRequest.followUpKind)
        assertEquals(first.resolvedRequest.metrics, followUp.resolvedRequest.metrics)
        assertEquals(
            first.resolvedRequest.timeframe.range.fromEpochMs - DAY_MS,
            followUp.resolvedRequest.timeframe.range.fromEpochMs
        )
        assertEquals(TrudyEvidenceReuseAction.REFRESH, followUp.retrieval.action)
    }

    @Test
    fun evidenceQuestionReusesOnlyEvidenceUsedByPreviousAnswer() {
        val coordinator = coordinatorWithCompletedSleepTurn()

        val followUp = coordinator.prepare("What evidence are you using?")

        assertEquals(TrudyFollowUpKind.EVIDENCE_EXPLANATION, followUp.resolvedRequest.followUpKind)
        assertEquals(TrudyEvidenceReuseAction.REUSE, followUp.retrieval.action)
        assertEquals(TrudyEvidenceReuseReason.VALID_USED_EVIDENCE, followUp.retrieval.reason)
        assertEquals(listOf("sleep-used"), followUp.retrieval.reusableEvidence.map { it.id })
    }

    @Test
    fun voiceAndTextTurnsUseTheSameConversationState() {
        val coordinator = TrudyConversationEvidenceCoordinator(clock)
        val voice = coordinator.prepare("How's my sleep?", TrudyConversationInputMode.VOICE)
        coordinator.complete(
            voice,
            TrudyAnswerTurnOutcome(
                investigationResult = sleepResult(voice.resolvedRequest),
                retrievedEvidence = sleepBatch(voice),
                usedEvidenceIds = listOf("sleep-used")
            )
        )

        val keyboard = coordinator.prepare("What about last month?", TrudyConversationInputMode.TEXT)

        assertEquals(TrudyConversationInputMode.TEXT, keyboard.resolvedRequest.inputMode)
        assertEquals("sleep", keyboard.resolvedRequest.topic)
        assertEquals(TrudyFollowUpKind.TIMEFRAME_SHIFT, keyboard.resolvedRequest.followUpKind)
    }

    @Test
    fun newestRequestAlwaysRefreshesEvenWhenScopeMatches() {
        val coordinator = coordinatorWithCompletedSleepTurn()

        val followUp = coordinator.prepare("What's my latest sleep data?")

        assertTrue(followUp.resolvedRequest.timeframe.requiresCurrentData)
        assertEquals(TrudyEvidenceReuseAction.REFRESH, followUp.retrieval.action)
        assertEquals(TrudyEvidenceReuseReason.CURRENT_DATA_REQUESTED, followUp.retrieval.reason)
    }

    @Test
    fun staleMatchingEvidenceRefreshes() {
        val coordinator = TrudyConversationEvidenceCoordinator(clock)
        val first = coordinator.prepare("How's my sleep?")
        coordinator.complete(
            first,
            TrudyAnswerTurnOutcome(
                investigationResult = sleepResult(first.resolvedRequest),
                retrievedEvidence = sleepBatch(first, staleAt = clock.nowEpochMs()),
                usedEvidenceIds = listOf("sleep-used")
            )
        )

        val followUp = coordinator.prepare("The same thing")

        assertEquals(TrudyEvidenceReuseAction.REFRESH, followUp.retrieval.action)
        assertEquals(TrudyEvidenceReuseReason.EVIDENCE_STALE, followUp.retrieval.reason)
    }

    @Test
    fun usedEvidenceCountExcludesUnusedDuplicatesAndDataGaps() {
        val selector = TrudyUsedEvidenceSelector()
        val request = TrudyConversationResolver(clock).resolve(
            "How's my sleep?",
            TrudyConversationInputMode.TEXT,
            TrudyConversationEvidenceState()
        )
        val used = evidence("sleep-used", HealthDomain.SLEEP, "sleep_score", TrudyEvidenceRole.SUPPORTING)
        val duplicate = used.copy(id = "duplicate-id")
        val unused = evidence("unused", HealthDomain.SLEEP, "sleep_total_minutes", TrudyEvidenceRole.SUPPORTING)
        val unrelated = evidence("environment", HealthDomain.ENVIRONMENT, "environment_temperature_c", TrudyEvidenceRole.CONTEXT)
        val gap = evidence("gap", HealthDomain.SLEEP, "sleep_score", TrudyEvidenceRole.DATA_GAP)

        val presentation = selector.select(
            retrievedRecordCount = 24,
            candidateRecords = listOf(used, duplicate, unused, unrelated, gap),
            usedEvidenceIds = listOf("sleep-used", "duplicate-id", "environment", "gap"),
            request = request
        )

        assertEquals(1, presentation.usedSupportingRecordCount)
        assertEquals(0, presentation.usedContextRecordCount)
        assertEquals(1, presentation.dataGapCount)
        assertEquals("1 record used", presentation.userFacingLabel)
        assertEquals(listOf("Sleep"), presentation.groups.map { it.label }.distinct())
        assertEquals(23, presentation.excludedRetrievedRecordCount)
    }

    @Test
    fun boundedCacheAndStateNeverBecomeTranscriptMemory() {
        val coordinator = TrudyConversationEvidenceCoordinator(clock)
        repeat(20) { index ->
            val turn = coordinator.prepare("How's my sleep?")
            val records = (0 until 120).map { recordIndex ->
                evidence(
                    id = "$index-$recordIndex",
                    domain = HealthDomain.SLEEP,
                    metricId = "sleep_score",
                    role = TrudyEvidenceRole.SUPPORTING,
                    timestamp = clock.nowEpochMs() - recordIndex
                )
            }
            coordinator.complete(
                turn,
                TrudyAnswerTurnOutcome(
                    retrievedEvidence = sleepBatch(turn).copy(
                        queryKey = "query-$index",
                        investigationId = "investigation-$index",
                        retrievedRecordCount = records.size,
                        records = records
                    ),
                    usedEvidenceIds = records.take(60).map { it.id }
                )
            )
        }

        assertEquals(6, coordinator.cachedBatchCount())
        assertTrue(coordinator.snapshot().latestEvidenceIds.size <= 48)
        assertFalse(coordinator.snapshot().toString().contains("How's my sleep?"))
    }

    private fun coordinatorWithCompletedSleepTurn(): TrudyConversationEvidenceCoordinator {
        val coordinator = TrudyConversationEvidenceCoordinator(clock)
        val first = coordinator.prepare("How's my sleep been this week?")
        coordinator.complete(
            first,
            TrudyAnswerTurnOutcome(
                investigationResult = sleepResult(first.resolvedRequest),
                retrievedEvidence = sleepBatch(first),
                usedEvidenceIds = listOf("sleep-used")
            )
        )
        return coordinator
    }

    private fun sleepResult(request: TrudyResolvedConversationRequest) =
        TrudyConversationInvestigationResult(
            resultId = "sleep-result",
            topic = "sleep",
            domains = listOf(HealthDomain.SLEEP),
            metrics = request.metrics,
            timeframe = request.timeframe,
            findingCode = "TREND_AVAILABLE",
            createdAtEpochMs = clock.nowEpochMs()
        )

    private fun sleepBatch(
        context: TrudyAnswerEngineConversationContext,
        staleAt: Long = clock.nowEpochMs() + DAY_MS
    ) = TrudyConversationEvidenceBatch(
        queryKey = context.retrieval.queryKey,
        investigationId = "sleep-result",
        topics = listOf("sleep"),
        domains = context.resolvedRequest.domains,
        metrics = context.resolvedRequest.metrics,
        timeframe = context.resolvedRequest.timeframe,
        retrievedAtEpochMs = clock.nowEpochMs(),
        staleAtEpochMs = staleAt,
        retrievedRecordCount = 2,
        records = listOf(
            evidence("sleep-used", HealthDomain.SLEEP, "sleep_score", TrudyEvidenceRole.SUPPORTING),
            evidence("sleep-unused", HealthDomain.SLEEP, "sleep_total_minutes", TrudyEvidenceRole.SUPPORTING)
        )
    )

    private fun evidence(
        id: String,
        domain: HealthDomain,
        metricId: String,
        role: TrudyEvidenceRole,
        timestamp: Long = clock.nowEpochMs()
    ) = TrudyConversationEvidenceRecord(
        id = id,
        identity = TrudyConversationEvidenceIdentity(
            domain = domain,
            metricId = metricId,
            evidenceKind = if (role == TrudyEvidenceRole.DATA_GAP) {
                TrudyEvidenceKind.UNCERTAINTY_OR_DATA_GAP
            } else {
                TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION
            },
            timestampEpochMs = timestamp
        ),
        role = role,
        displayValue = "value",
        source = "Data Vault"
    )

    private class FixedBoundaries : TrudyTemporalBoundaryProvider {
        private val now = 1_754_870_400_000L // 2025-08-11T00:00:00Z, a Monday
        override fun nowEpochMs(): Long = now
        override fun startOfTodayEpochMs(): Long = now
        override fun startOfWeekEpochMs(): Long = now
        override fun startOfMonthEpochMs(monthsAgo: Int): Long = when (monthsAgo) {
            0 -> 1_754_006_400_000L // 2025-08-01
            1 -> 1_751_328_000_000L // 2025-07-01
            else -> 1_748_736_000_000L // 2025-06-01
        }
    }

    private companion object { const val DAY_MS = 86_400_000L }
}
