package com.projectsuperhuman.next.trudy

/**
 * High-signal temporal decorator for Trudy's existing authoritative investigation planner.
 *
 * The system planner still decides WHAT to investigate. This class only makes explicit human
 * time expressions deterministic and applies the resolved window to already-selected operations.
 * It is intentionally bounded to windows that fit the investigation engine's 90-day lookback.
 */
class TrudyTemporalPlanningDecorator(
    private val delegate: TrudyPreflightPlanner,
    private val boundaries: TrudyTemporalBoundaryProvider
) : TrudyPreflightPlanner {

    override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> {
        val resolved = resolveExplicit(request.userMessage, request.conversationContext) ?: return delegate.plan(request)
        return delegate.plan(request).map { operation -> operation.withTimeframe(resolved) }.distinct()
    }

    private fun TrudyToolOperation.withTimeframe(timeframe: TrudyResolvedTimeframe): TrudyToolOperation = when (this) {
        is TrudyToolOperation.GetMetricWindow -> copy(range = timeframe.observation)
        is TrudyToolOperation.GetMetricHistory -> if (offset == 0) {
            TrudyToolOperation.GetMetricWindow(domain, metricId, timeframe.observation, limit)
        } else this
        is CompareBaseline -> copy(
            observationWindow = timeframe.observation,
            baselineWindow = timeframe.baseline
        )
        is GetPersonalTrend -> CompareBaseline(
            domain = domain,
            metricId = metricId,
            observationWindow = timeframe.observation,
            baselineWindow = timeframe.baseline
        )
        is GetAssociation -> copy(window = timeframe.observation)
        is GetLaggedAssociation -> copy(window = timeframe.observation)
        is InvestigateChange -> copy(
            observationWindow = timeframe.observation,
            baselineWindow = timeframe.baseline,
            timeframeLabel = timeframe.label,
            timeframeExplicit = true
        )
        else -> this
    }

    private fun resolveExplicit(
        message: String,
        conversation: List<TrudyConversationTurn>
    ): TrudyResolvedTimeframe? {
        val text = normalize(message)
        if (text.isBlank()) return null

        if (BEFORE_REFERENTS.any { it in text }) {
            val previous = conversation.asReversed()
                .asSequence()
                .filter { it.role == TrudyConversationRole.USER }
                .mapNotNull { resolveStandalone(normalize(it.text)) }
                .firstOrNull()
                ?: return null
            return TrudyResolvedTimeframe(
                observation = previous.baseline,
                baseline = prior(previous.baseline),
                label = "the period before ${previous.label}",
                explicit = true
            )
        }

        return resolveStandalone(text)
    }

    private fun resolveStandalone(text: String): TrudyResolvedTimeframe? {
        val now = boundaries.nowEpochMs()
        val today = boundaries.startOfTodayEpochMs()
        val week = boundaries.startOfWeekEpochMs()
        val month = boundaries.startOfMonthEpochMs()

        val observation: TrudyTimeRange
        val label: String
        val baselineOverride: TrudyTimeRange?

        when {
            "last night" in text || "overnight" in text -> {
                // 18:00 yesterday -> now. This captures a sleep episode without pretending we know
                // the user's actual sleep onset before reading their sleep records.
                observation = TrudyTimeRange((today - 6L * HOUR_MS).coerceAtLeast(0L), now)
                label = "last night"
                baselineOverride = null
            }
            "day before yesterday" in text -> {
                observation = TrudyTimeRange(
                    (today - 2L * DAY_MS).coerceAtLeast(0L),
                    (today - DAY_MS - 1L).coerceAtLeast(0L)
                )
                label = "the day before yesterday"
                baselineOverride = null
            }
            "since yesterday" in text -> {
                observation = TrudyTimeRange((today - DAY_MS).coerceAtLeast(0L), now)
                label = "since yesterday"
                baselineOverride = null
            }
            "yesterday" in text -> {
                observation = TrudyTimeRange(
                    (today - DAY_MS).coerceAtLeast(0L),
                    (today - 1L).coerceAtLeast(0L)
                )
                label = "yesterday"
                baselineOverride = null
            }
            "today" in text -> {
                observation = TrudyTimeRange(today.coerceAtLeast(0L), now)
                label = "today"
                baselineOverride = null
            }
            "last 3 days" in text || "past 3 days" in text || "last few days" in text || "past few days" in text -> {
                observation = rolling(now, 3)
                label = "the last few days"
                baselineOverride = null
            }
            "last 7 days" in text || "past 7 days" in text || "past week" in text -> {
                observation = rolling(now, 7)
                label = "the last 7 days"
                baselineOverride = null
            }
            "last 14 days" in text || "past 14 days" in text || "last fortnight" in text || "past fortnight" in text -> {
                observation = rolling(now, 14)
                label = "the last 14 days"
                baselineOverride = null
            }
            "last few weeks" in text || "past few weeks" in text || "few weeks" in text -> {
                observation = rolling(now, 21)
                label = "the last few weeks"
                baselineOverride = null
            }
            "last 30 days" in text || "past 30 days" in text || "past month" in text -> {
                observation = rolling(now, 30)
                label = "the last 30 days"
                baselineOverride = null
            }
            "last week" in text || "previous week" in text -> {
                observation = TrudyTimeRange(
                    (week - 7L * DAY_MS).coerceAtLeast(0L),
                    (week - 1L).coerceAtLeast(0L)
                )
                label = "last week"
                baselineOverride = null
            }
            "this week" in text -> {
                observation = TrudyTimeRange(week.coerceAtLeast(0L), now)
                label = "this week"
                baselineOverride = null
            }
            "last month" in text || "previous month" in text -> {
                val previousMonthStart = boundaries.startOfMonthEpochMs(1)
                observation = TrudyTimeRange(previousMonthStart, (month - 1L).coerceAtLeast(0L))
                label = "last month"
                baselineOverride = TrudyTimeRange(
                    boundaries.startOfMonthEpochMs(2),
                    (previousMonthStart - 1L).coerceAtLeast(0L)
                )
            }
            "this month" in text -> {
                observation = TrudyTimeRange(month.coerceAtLeast(0L), now)
                label = "this month"
                baselineOverride = null
            }
            "recently" in text || "lately" in text || "recent" in text -> {
                observation = rolling(now, 7)
                label = "recently"
                baselineOverride = null
            }
            else -> return null
        }

        return TrudyResolvedTimeframe(
            observation = observation,
            baseline = baselineOverride ?: prior(observation),
            label = label,
            explicit = true
        )
    }

    private fun prior(range: TrudyTimeRange): TrudyTimeRange {
        val duration = (range.toEpochMs - range.fromEpochMs + 1L).coerceAtLeast(1L)
        val end = (range.fromEpochMs - 1L).coerceAtLeast(0L)
        return TrudyTimeRange((end - duration + 1L).coerceAtLeast(0L), end)
    }

    private fun rolling(now: Long, days: Int): TrudyTimeRange =
        TrudyTimeRange((now - days.toLong() * DAY_MS).coerceAtLeast(0L), now)

    private fun normalize(text: String): String = text.lowercase()
        .replace('’', '\'')
        .replace(Regex("[^a-z0-9']+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        val BEFORE_REFERENTS = listOf(
            "before that",
            "before then",
            "the period before",
            "month before",
            "week before"
        )
    }
}
