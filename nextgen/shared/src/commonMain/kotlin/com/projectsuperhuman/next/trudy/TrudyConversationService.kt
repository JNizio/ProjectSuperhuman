package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.trudy.conversation.TrudyAnswerTurnOutcome
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationEvidenceBatch
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationEvidenceCoordinator
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationEvidenceRecord
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationInputMode
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationInvestigationResult
import com.projectsuperhuman.next.trudy.conversation.TrudyEvidenceReuseAction
import com.projectsuperhuman.next.trudy.conversation.TrudyEvidenceRole
import com.projectsuperhuman.next.trudy.conversation.TrudyEvidenceGapReason

data class TrudyConversationResult(
    val answerText: String,
    /** References actually used by the final answer, not every retrieved record. */
    val evidenceReferences: List<TrudyEvidenceReference>,
    val toolCallsMade: List<TrudyToolCallRecord>,
    val warnings: List<TrudyWarning>,
    val modelMetadata: TrudyModelMetadata?,
    val isFallback: Boolean,
    /** Diagnostic only. Never render this as the user-facing evidence count. */
    val retrievedEvidenceCount: Int = evidenceReferences.size
)

/**
 * One visible Trudy conversation owns one instance. The coordinator is bounded and transcript-free;
 * text and voice therefore share the same topic/timeframe/evidence state without permanent memory.
 */
class TrudyConversationService(
    private val orchestrator: TrudyOrchestrator,
    private val conversationEvidence: TrudyConversationEvidenceCoordinator? = null,
    private val planningContextHolder: TrudyConversationPlanningContextHolder? = null,
    private val answerTurnRegistry: TrudyAnswerTurnRegistry? = null,
    private val answerEngine: TrudyAnswerEngine = TrudyAnswerEngine(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun respondTo(
        userText: String,
        conversationContext: List<TrudyConversationTurn> = emptyList(),
        preselectedContext: TrudyContextRequest? = null,
        inputMode: TrudyConversationInputMode = TrudyConversationInputMode.TEXT
    ): TrudyConversationResult {
        val coordinator = conversationEvidence
        if (coordinator == null) {
            return orchestrate(userText, conversationContext, preselectedContext)
        }

        val turnContext = coordinator.prepare(userText, inputMode)
        if (turnContext.shouldReuseWithoutRetrieval()) {
            val answer = answerEngine.synthesizeReusedConversation(turnContext)
            val reusable = turnContext.retrieval.reusableEvidence
            val usedIds = if (turnContext.retrieval.action == TrudyEvidenceReuseAction.REUSE) {
                reusable.filter { it.role != TrudyEvidenceRole.DATA_GAP }.map { it.id }
            } else {
                emptyList()
            }
            val presentation = coordinator.complete(
                turnContext,
                TrudyAnswerTurnOutcome(
                    usedEvidenceIds = usedIds,
                    missingDataFindings = turnContext.retrieval.missingData
                )
            )
            return TrudyConversationResult(
                answerText = answer,
                evidenceReferences = presentation.groups.flatMap { group -> group.records.map { it.toEvidenceReference() } },
                toolCallsMade = emptyList(),
                warnings = if (turnContext.retrieval.action == TrudyEvidenceReuseAction.REUSE_MISSING_DATA) {
                    listOf(TrudyWarning(TrudyWarningKind.EMPTY_DATA, "The same requested period is already known to have no matching stored measurement."))
                } else {
                    emptyList()
                },
                modelMetadata = null,
                isFallback = false,
                retrievedEvidenceCount = presentation.retrievedRecordCount
            )
        }

        planningContextHolder?.set(turnContext)
        val result = try {
            orchestrate(userText, conversationContext, preselectedContext)
        } finally {
            planningContextHolder?.clear()
        }
        val trace = answerTurnRegistry?.consume()
        val now = nowEpochMs().coerceAtLeast(0L)
        val selected = trace?.usedEvidence ?: result.evidenceReferences.map { reference ->
            TrudySelectedAnswerEvidence(
                reference = reference,
                label = reference.metricId?.let(::humanMetricLabel) ?: reference.insightId.orEmpty().replace('_', ' '),
                summary = "Used to support the answer."
            )
        }
        val records = selected.distinctBy { it.reference }.map { selectedEvidence ->
            val identity = selectedEvidence.reference.toConversationIdentity()
            TrudyConversationEvidenceRecord(
                id = identity.stableKey(),
                identity = identity,
                role = TrudyEvidenceRole.SUPPORTING,
                displayValue = selectedEvidence.summary
            )
        }
        val retrievedCount = maxOf(trace?.retrievedRecordCount ?: result.retrievedEvidenceCount, records.size)
        val structured = trace?.investigation
        val missing = buildMissingFindings(turnContext, structured, result, now)
        val investigationPointer = structured?.let { investigation ->
            val resolved = turnContext.resolvedRequest
            val metrics = if (resolved.metrics.isNotEmpty()) resolved.metrics else {
                (listOf(investigation.target.primaryMetricId) + investigation.target.supportingMetricIds)
                    .distinct()
                    .map { metricId -> com.projectsuperhuman.next.trudy.conversation.TrudyConversationMetric(investigation.target.domain, metricId) }
            }
            TrudyConversationInvestigationResult(
                resultId = "turn-${turnContext.turnId}-investigation",
                topic = resolved.topic ?: investigation.target.label,
                domains = resolved.domains.ifEmpty { listOf(investigation.target.domain) },
                metrics = metrics,
                timeframe = resolved.timeframe,
                findingCode = investigation.premiseStatus.name,
                createdAtEpochMs = now
            )
        }
        val batch = TrudyConversationEvidenceBatch(
            queryKey = turnContext.retrieval.queryKey,
            investigationId = investigationPointer?.resultId ?: "turn-${turnContext.turnId}",
            topics = listOfNotNull(turnContext.resolvedRequest.topic),
            domains = turnContext.resolvedRequest.domains,
            metrics = turnContext.resolvedRequest.metrics,
            timeframe = turnContext.resolvedRequest.timeframe,
            retrievedAtEpochMs = now,
            staleAtEpochMs = staleAt(turnContext.resolvedRequest.timeframe, now),
            retrievedRecordCount = retrievedCount,
            records = records
        )
        val presentation = coordinator.complete(
            turnContext,
            TrudyAnswerTurnOutcome(
                investigationResult = investigationPointer,
                retrievedEvidence = batch,
                usedEvidenceIds = records.map { it.id },
                missingDataFindings = missing
            )
        )
        return result.copy(
            evidenceReferences = presentation.groups.flatMap { group -> group.records.map { it.toEvidenceReference() } },
            retrievedEvidenceCount = presentation.retrievedRecordCount
        )
    }

    private suspend fun orchestrate(
        userText: String,
        conversationContext: List<TrudyConversationTurn>,
        preselectedContext: TrudyContextRequest?
    ): TrudyConversationResult {
        val result = orchestrator.ask(
            TrudyAskRequest(
                userMessage = userText,
                conversationContext = conversationContext,
                preselectedContext = preselectedContext
            )
        )
        return TrudyConversationResult(
            answerText = result.answerText,
            evidenceReferences = result.evidenceReferences,
            toolCallsMade = result.toolCallsMade,
            warnings = result.warnings,
            modelMetadata = result.modelMetadata,
            isFallback = result.status == TrudyOrchestrationStatus.FALLBACK,
            retrievedEvidenceCount = result.evidenceReferences.size
        )
    }

    private fun buildMissingFindings(
        context: TrudyAnswerEngineConversationContext,
        structured: TrudyInvestigationResult?,
        result: TrudyConversationResult,
        now: Long
    ): List<com.projectsuperhuman.next.trudy.conversation.TrudyMissingDataFinding> {
        val resolved = context.resolvedRequest
        val fromInvestigation = structured?.missingEvidence.orEmpty()
            .filter { it.domain != null && it.metricId != null }
            .filter { gap ->
                gap.reason == TrudyEvidenceGapReason.NO_DATA ||
                    gap.reason == TrudyEvidenceGapReason.NOT_CONNECTED
            }
            .groupBy { requireNotNull(it.domain) }
            .map { (domain, gaps) ->
                missingFinding(
                    domain = domain,
                    metricIds = gaps.mapNotNull { it.metricId },
                    timeframe = resolved.timeframe,
                    checkedAtEpochMs = now
                )
            }
        if (fromInvestigation.isNotEmpty()) return fromInvestigation

        val definiteEmpty = result.evidenceReferences.isEmpty() &&
            result.warnings.any { it.kind == TrudyWarningKind.EMPTY_DATA } &&
            resolved.metrics.isNotEmpty()
        if (!definiteEmpty) return emptyList()
        return resolved.metrics.groupBy { it.domain }.map { (domain, metrics) ->
            missingFinding(domain, metrics.map { it.metricId }, resolved.timeframe, now)
        }
    }

    private fun staleAt(timeframe: com.projectsuperhuman.next.trudy.conversation.TrudyConversationTimeframe, now: Long): Long {
        val ttl = when {
            timeframe.requiresCurrentData -> 5L * MINUTE_MS
            timeframe.range.toEpochMs < now - DAY_MS -> 24L * HOUR_MS
            else -> 60L * MINUTE_MS
        }
        return now + ttl
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
