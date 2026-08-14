package com.projectsuperhuman.next.trudy.conversation

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudySystemCatalog
import com.projectsuperhuman.next.trudy.TrudyTemporalBoundaryProvider
import com.projectsuperhuman.next.trudy.TrudyTimeRange

/** Structured follow-up/reference resolver. Raw history is neither required nor accepted. */
class TrudyConversationResolver(
    private val boundaries: TrudyTemporalBoundaryProvider
) {
    fun resolve(
        userText: String,
        inputMode: TrudyConversationInputMode,
        state: TrudyConversationEvidenceState
    ): TrudyResolvedConversationRequest {
        val normalized = normalize(userText)
        val currentModules = TrudySystemCatalog.modulesMentioned(normalized)
        val explicitTopic = currentModules.firstOrNull()?.id
        val topic = explicitTopic ?: state.activeTopic
        val referent = resolveReferent(normalized, state)
        val timeframe = resolveTimeframe(normalized, state.activeTimeframe)
        val currentMetrics = TrudySystemCatalog.metricsMentioned(normalized, currentModules)
            .map { TrudyConversationMetric(it.domain, it.metricId) }

        val inheritedMetrics = when {
            asksForEvidence(normalized) -> state.activeMetrics
            currentMetrics.isEmpty() -> state.activeMetrics
            referent.kind in RESULT_REFERENTS -> state.activeMetrics
            else -> emptyList()
        }
        val metrics = (currentMetrics + inheritedMetrics)
            .distinctBy { it.domain to it.metricId }
            .take(TrudyConversationBounds.MAX_METRICS)
        val inheritedDomains = when {
            currentModules.isEmpty() || referent.kind in RESULT_REFERENTS || asksForEvidence(normalized) -> state.activeDomains
            else -> emptyList()
        }
        val domains = (currentMetrics.map { it.domain } + inheritedDomains)
            .distinct()
            .take(TrudyConversationBounds.MAX_DOMAINS)

        val topicChanged = explicitTopic != null && state.activeTopic != null && explicitTopic != state.activeTopic
        val metricChanged = currentMetrics.isNotEmpty() && state.activeMetrics.isNotEmpty() &&
            currentMetrics.toSet() != state.activeMetrics.toSet()
        val timeframeChanged = state.activeTimeframe?.range?.let { it != timeframe.range } ?: timeframe.explicit
        val followUp = followUpKind(
            normalized = normalized,
            hasState = state.activeTopic != null,
            hasExplicitTopic = explicitTopic != null,
            topicChanged = topicChanged,
            timeframeChanged = timeframeChanged,
            referent = referent,
            state = state
        )

        return TrudyResolvedConversationRequest(
            rawUserText = userText.trim(),
            inputMode = inputMode,
            followUpKind = followUp,
            topic = topic,
            domains = domains,
            metrics = metrics,
            timeframe = timeframe,
            priorTimeframe = state.activeTimeframe,
            referent = referent,
            evidenceIdsRequested = if (asksForEvidence(normalized)) state.latestEvidenceIds else emptyList(),
            topicChanged = topicChanged,
            metricChanged = metricChanged,
            timeframeChanged = timeframeChanged
        )
    }

    private fun resolveReferent(
        text: String,
        state: TrudyConversationEvidenceState
    ): TrudyResolvedReferent {
        val kind = when {
            "that trend" in text -> TrudyReferentKind.LATEST_TREND
            "those readings" in text -> TrudyReferentKind.LATEST_READINGS
            "those nights" in text -> TrudyReferentKind.ACTIVE_TIMEFRAME
            "before that" in text || wordPresent(text, "then") -> TrudyReferentKind.ACTIVE_TIMEFRAME
            "the same thing" in text || "same thing" in text -> TrudyReferentKind.SAME_SCOPE
            asksForEvidence(text) -> TrudyReferentKind.LATEST_RESULT
            wordPresent(text, "that") || wordPresent(text, "it") -> TrudyReferentKind.LATEST_RESULT
            "this experiment" in text || "that experiment" in text -> TrudyReferentKind.ACTIVE_EXPERIMENT
            looksLikeFollowUp(text) -> TrudyReferentKind.ACTIVE_TOPIC
            else -> TrudyReferentKind.NONE
        }
        return TrudyResolvedReferent(
            kind = kind,
            topic = state.activeTopic,
            resultId = state.latestInvestigationResult?.resultId,
            evidenceIds = if (kind in RESULT_REFERENTS) state.latestEvidenceIds else emptyList()
        )
    }

    private fun resolveTimeframe(
        text: String,
        active: TrudyConversationTimeframe?
    ): TrudyConversationTimeframe {
        val now = boundaries.nowEpochMs().coerceAtLeast(0L)
        val today = boundaries.startOfTodayEpochMs().coerceAtLeast(0L)
        val week = boundaries.startOfWeekEpochMs().coerceAtLeast(0L)
        val month = boundaries.startOfMonthEpochMs().coerceAtLeast(0L)
        val currentRequested = CURRENT_TERMS.any { wordPresent(text, it) }

        fun frame(from: Long, to: Long, label: String) = TrudyConversationTimeframe(
            range = TrudyTimeRange(from.coerceAtLeast(0L), to.coerceAtLeast(from.coerceAtLeast(0L))),
            label = label,
            explicit = true,
            requiresCurrentData = currentRequested
        )
        fun shiftPrior(value: TrudyConversationTimeframe, label: String): TrudyConversationTimeframe {
            val duration = (value.range.toEpochMs - value.range.fromEpochMs + 1L).coerceAtLeast(1L)
            val end = (value.range.fromEpochMs - 1L).coerceAtLeast(0L)
            return frame((end - duration + 1L).coerceAtLeast(0L), end, label)
        }

        return when {
            "day before" in text || "before that" in text -> active?.let { shiftPrior(it, "the period before ${it.label}") }
                ?: frame((today - 2L * DAY_MS), (today - DAY_MS - 1L), "the day before yesterday")
            "last month" in text -> frame(boundaries.startOfMonthEpochMs(1), month - 1L, "last month")
            "this month" in text -> frame(month, now, "this month")
            "last week" in text -> frame(week - 7L * DAY_MS, week - 1L, "last week")
            "this week" in text -> frame(week, now, "this week")
            "yesterday" in text -> frame(today - DAY_MS, today - 1L, "yesterday")
            "today" in text -> frame(today, now, "today")
            "last 24 hours" in text -> frame(now - DAY_MS, now, "the last 24 hours")
            "recently" in text || wordPresent(text, "recent") -> frame(now - 7L * DAY_MS, now, "the last 7 days")
            active != null -> active.copy(explicit = false, requiresCurrentData = currentRequested)
            else -> TrudyConversationTimeframe(
                range = TrudyTimeRange((now - 7L * DAY_MS).coerceAtLeast(0L), now),
                label = "the last 7 days",
                explicit = false,
                requiresCurrentData = currentRequested
            )
        }
    }

    private fun followUpKind(
        normalized: String,
        hasState: Boolean,
        hasExplicitTopic: Boolean,
        topicChanged: Boolean,
        timeframeChanged: Boolean,
        referent: TrudyResolvedReferent,
        state: TrudyConversationEvidenceState
    ): TrudyFollowUpKind = when {
        asksForEvidence(normalized) && hasState -> TrudyFollowUpKind.EVIDENCE_EXPLANATION
        !hasState && looksLikeFollowUp(normalized) -> TrudyFollowUpKind.AMBIGUOUS_FOLLOW_UP
        state.missingDataFindings.isNotEmpty() && timeframeChanged && !hasExplicitTopic ->
            TrudyFollowUpKind.MISSING_DATA_FOLLOW_UP
        timeframeChanged && !topicChanged -> TrudyFollowUpKind.TIMEFRAME_SHIFT
        topicChanged && referent.kind in RESULT_REFERENTS -> TrudyFollowUpKind.CROSS_DOMAIN_FOLLOW_UP
        referent.kind == TrudyReferentKind.SAME_SCOPE -> TrudyFollowUpKind.SAME_SCOPE
        looksLikeFollowUp(normalized) -> TrudyFollowUpKind.SAME_SCOPE
        else -> TrudyFollowUpKind.NEW_QUESTION
    }

    private fun asksForEvidence(text: String): Boolean = EVIDENCE_PHRASES.any { it in text }

    private fun looksLikeFollowUp(text: String): Boolean = FOLLOW_UP_PHRASES.any { it in text } ||
        listOf("that", "it", "then").any { wordPresent(text, it) }

    private fun wordPresent(text: String, word: String): Boolean =
        text.split(' ').any { it == word }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9']+"), " ")
        .trim()

    private companion object {
        const val DAY_MS = 86_400_000L
        val EVIDENCE_PHRASES = listOf(
            "what evidence", "which evidence", "what data", "which data", "basing that on",
            "based on", "how do you know", "show your evidence", "show your sources"
        )
        val FOLLOW_UP_PHRASES = listOf(
            "what about", "how about", "could that", "could it", "those nights", "those readings",
            "that trend", "before that", "the same thing", "same thing"
        )
        val CURRENT_TERMS = listOf("current", "newest", "latest", "now")
        val RESULT_REFERENTS = setOf(
            TrudyReferentKind.LATEST_RESULT,
            TrudyReferentKind.LATEST_TREND,
            TrudyReferentKind.LATEST_READINGS,
            TrudyReferentKind.ACTIVE_TIMEFRAME
        )
    }
}
