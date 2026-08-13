package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

object TrudyEmotionalToolPlanner {
    fun initialPlan(message: String): List<TrudyToolOperation>? {
        if (!TrudyEmotionalSemantics.looksEmotionalQuestion(message)) return null
        val emotional = TrudyEmotionalSemantics.routingDomain()
        val text = message.lowercase()
        val domains = when {
            mentionsExercise(text) -> listOf(HealthDomain.EXERCISE, emotional).distinct()
            mentionsSleep(text) -> listOf(HealthDomain.SLEEP, emotional).distinct()
            else -> listOf(emotional)
        }
        return listOf(TrudyToolOperation.GetContext(TrudyContextRequest(domains, 90, includeHistory = false)))
    }

    fun followUp(message: String, results: List<TrudyToolResult>): List<TrudyToolOperation> {
        if (!TrudyEmotionalSemantics.looksEmotionalQuestion(message)) return emptyList()
        if (results.any { it is PersonalTrendResult || it is BaselineComparisonResult || it is AssociationToolResult }) return emptyList()
        val broadSummary = TrudyEmotionalSemantics.isBroadSummaryQuestion(message)

        val contexts = results.filterIsInstance<TrudyToolResult.Context>().flatMap { it.context.domains }
        val emotional = emotionalContext(contexts) ?: return emptyList()
        if (broadSummary) return TrudyEmotionalSummaryPlanner.plan(emotional)
        association(message, contexts, emotional)?.let { return listOf(it) }

        val axis = TrudyEmotionalSemantics.axisForQuestion(message)
        val metric = TrudyEmotionalSemantics.selectMetric(axis, metricIds(emotional))
            ?: TrudyEmotionalSemantics.preferredMetricId(axis)
        val recentDays = when {
            "month" in message.lowercase() -> 30
            "two weeks" in message.lowercase() || "2 weeks" in message.lowercase() -> 14
            else -> 7
        }
        return listOf(GetPersonalTrend(emotional.domain, metric, recentDays, if (recentDays >= 30) 30 else 28))
    }

    internal fun metricIds(context: TrudyDomainContext): List<String> =
        (context.currentState.map { it.metricId } + context.history.map { it.metricId } + context.derivedFeatures.map { it.metricId }).distinct()

    private fun association(message: String, contexts: List<TrudyDomainContext>, emotional: TrudyDomainContext): GetAssociation? {
        val text = message.lowercase()
        val other = when {
            mentionsExercise(text) -> contexts.firstOrNull { it.domain == HealthDomain.EXERCISE }
            mentionsSleep(text) -> contexts.firstOrNull { it.domain == HealthDomain.SLEEP }
            else -> null
        } ?: return null
        val otherMetric = when (other.domain) {
            HealthDomain.EXERCISE -> pick(metricIds(other), "exercise_minutes", "steps", "workout_volume") ?: "exercise_minutes"
            HealthDomain.SLEEP -> pick(metricIds(other), "sleep_score", "sleep_continuity_score", "sleep_total_minutes") ?: "sleep_score"
            else -> return null
        }
        val axis = TrudyEmotionalSemantics.axisForQuestion(message)
        val emotionalMetric = TrudyEmotionalSemantics.selectMetric(axis, metricIds(emotional))
            ?: TrudyEmotionalSemantics.preferredMetricId(axis)
        return GetAssociation(other.domain, otherMetric, emotional.domain, emotionalMetric, TrudyAssociationMethod.SPEARMAN, 86_400_000L)
    }

    private fun emotionalContext(contexts: List<TrudyDomainContext>) = contexts.firstOrNull { context ->
        TrudyEmotionalSemantics.isEmotionalDomain(context.domain) || metricIds(context).any { TrudyEmotionalSemantics.semanticForMetric(it) != null }
    } ?: contexts.firstOrNull { it.domain == TrudyEmotionalSemantics.routingDomain() }

    private fun pick(ids: List<String>, vararg preferred: String): String? {
        preferred.forEach { wanted -> ids.firstOrNull { it.equals(wanted, true) }?.let { return it } }
        return null
    }

    private fun mentionsExercise(text: String) = listOf("exercise", "workout", "active", "activity", "steps", "training").any { it in text }
    private fun mentionsSleep(text: String) = listOf("sleep", "slept", "night").any { it in text }
}
