package com.projectsuperhuman.next.trudy.conversation

import com.projectsuperhuman.next.trudy.TrudyTemporalBoundaryProvider

/**
 * Per-visible-conversation coordinator. The Android controller should own one instance and send
 * both voice and keyboard turns through it. Nothing is written outside that local session.
 */
class TrudyConversationEvidenceCoordinator(
    private val boundaries: TrudyTemporalBoundaryProvider,
    private val reusePolicy: TrudyEvidenceReusePolicy = TrudyEvidenceReusePolicy(),
    private val selector: TrudyUsedEvidenceSelector = TrudyUsedEvidenceSelector()
) {
    private val resolver = TrudyConversationResolver(boundaries)
    private val cache = BoundedTrudyConversationEvidenceCache()
    private var state = TrudyConversationEvidenceState()
    private var nextTurnId = 1L

    fun prepare(
        userText: String,
        inputMode: TrudyConversationInputMode = TrudyConversationInputMode.TEXT
    ): TrudyAnswerEngineConversationContext {
        val resolved = resolver.resolve(userText, inputMode, state)
        return TrudyAnswerEngineConversationContext(
            turnId = nextTurnId++,
            conversationState = state,
            resolvedRequest = resolved,
            retrieval = reusePolicy.decide(
                request = resolved,
                state = state,
                batches = cache.snapshot(),
                nowEpochMs = boundaries.nowEpochMs()
            )
        )
    }

    fun complete(
        context: TrudyAnswerEngineConversationContext,
        outcome: TrudyAnswerTurnOutcome
    ): TrudyUsedEvidencePresentation {
        outcome.retrievedEvidence?.let(cache::put)
        val candidates = if (outcome.retrievedEvidence != null) {
            outcome.retrievedEvidence.records
        } else {
            cache.recordsByIds(
                (context.retrieval.reusableEvidence.map { it.id } + outcome.usedEvidenceIds).distinct()
            )
        }
        val retrievedCount = outcome.retrievedEvidence?.retrievedRecordCount
            ?: context.retrieval.reusableEvidence.size
        val presentation = selector.select(
            retrievedRecordCount = retrievedCount,
            candidateRecords = candidates,
            usedEvidenceIds = outcome.usedEvidenceIds,
            request = context.resolvedRequest
        )
        state = reduce(context, outcome, presentation)
        return presentation
    }

    fun snapshot(): TrudyConversationEvidenceState = state

    fun cachedBatchCount(): Int = cache.snapshot().size

    fun reset() {
        cache.clear()
        state = TrudyConversationEvidenceState()
        nextTurnId = 1L
    }

    private fun reduce(
        context: TrudyAnswerEngineConversationContext,
        outcome: TrudyAnswerTurnOutcome,
        presentation: TrudyUsedEvidencePresentation
    ): TrudyConversationEvidenceState {
        val request = context.resolvedRequest
        val prior = state.activeTimeframe?.takeIf { it.range != request.timeframe.range }
            ?: state.priorComparisonTimeframe
        val usedIds = presentation.groups.flatMap { it.records }.map { it.id }
            .distinct().take(TrudyConversationBounds.MAX_LATEST_EVIDENCE_IDS)
        val missing = (outcome.missingDataFindings + state.missingDataFindings)
            .distinctBy { Triple(it.domain, it.metricIds.sorted(), it.timeframe.range) }
            .take(TrudyConversationBounds.MAX_MISSING_FINDINGS)
        val now = outcome.retrievedEvidence?.retrievedAtEpochMs
            ?: outcome.investigationResult?.createdAtEpochMs
            ?: boundaries.nowEpochMs()

        return TrudyConversationEvidenceState(
            activeTopic = request.topic ?: state.activeTopic,
            activeDomains = request.domains.distinct().take(TrudyConversationBounds.MAX_DOMAINS),
            activeMetrics = request.metrics.distinct().take(TrudyConversationBounds.MAX_METRICS),
            activeTimeframe = request.timeframe,
            priorComparisonTimeframe = prior,
            latestInvestigationResult = outcome.investigationResult ?: state.latestInvestigationResult,
            latestEvidenceIds = if (usedIds.isNotEmpty()) usedIds else state.latestEvidenceIds,
            activeExperimentId = outcome.activeExperimentId ?: state.activeExperimentId,
            unresolvedQuestionCode = outcome.unresolvedQuestionCode,
            missingDataFindings = missing,
            updatedAtEpochMs = now.coerceAtLeast(0L)
        )
    }
}
