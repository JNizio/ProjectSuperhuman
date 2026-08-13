package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs

internal object TrudyEmotionalAssociationResponse {
    fun compose(question: String, association: TrudyAssociationResult): String? {
        val right = TrudyEmotionalSemantics.semanticForMetric(association.rightMetricId)
        val left = TrudyEmotionalSemantics.semanticForMetric(association.leftMetricId)
        val semantic = right ?: left ?: return null
        val emotionalOnRight = right != null
        val otherDomain = if (emotionalOnRight) association.leftDomain else association.rightDomain
        val otherMetric = if (emotionalOnRight) association.leftMetricId else association.rightMetricId
        val coefficient = association.coefficient
        if (coefficient == null || association.confidence == TrudyConfidence.INSUFFICIENT) {
            return "I don't have enough well-aligned ${semantic.displayName} and ${TrudyEmotionalResponseText.driverLabel(otherDomain)} check-ins yet to tell whether they move together reliably."
        }
        if (abs(coefficient) <= 0.05) {
            return "I'm not seeing much of a consistent relationship between your ${semantic.displayName} ratings and ${TrudyEmotionalResponseText.driverLabel(otherDomain)} in the data we can align so far."
        }

        val concept = TrudyEmotionalSemantics.conceptForQuestion(question)
        val phrase = TrudyEmotionalSemantics.phraseForQuestion(concept, semantic, if (coefficient > 0) 1 else -1)
        val samples = if (association.sampleCount > 0) " across ${association.sampleCount} aligned check-ins" else ""
        val tentative = association.confidence == TrudyConfidence.LOW || association.dataQualityStatus != TrudyDataQualityStatus.GOOD
        val cause = when (otherDomain) {
            HealthDomain.EXERCISE -> "exercise"
            HealthDomain.SLEEP -> "sleep"
            else -> TrudyEmotionalResponseText.driverLabel(otherDomain)
        }
        return buildString {
            append("You've tended to report ${TrudyEmotionalResponseText.selfReport(phrase)} ${TrudyEmotionalResponseText.driverPhrase(otherDomain, otherMetric)}$samples.")
            if (tentative) append(" It's still a tentative pattern with the amount and quality of data available.")
            append(" We don't have enough evidence to say $cause caused that difference, but the pattern is worth watching.")
        }
    }
}
