package com.projectsuperhuman.next.trudy

import kotlin.math.abs

internal object TrudyEmotionalTrendResponse {
    fun compose(question: String, comparison: TrudyBaselineComparison): String? {
        val semantic = TrudyEmotionalSemantics.semanticForMetric(comparison.metricId)
            ?: if (TrudyEmotionalSemantics.isEmotionalDomain(comparison.domain)) return unknown(comparison) else return null
        val delta = comparison.absoluteDelta
        if (delta == null || comparison.observationMean == null || comparison.baselineMean == null) {
            return "I don't have enough recent and earlier ${semantic.displayName} check-ins to tell yet."
        }
        if (comparison.confidence == TrudyConfidence.INSUFFICIENT) {
            return "I have some ${semantic.displayName} check-ins, but not enough in both periods to say reliably whether that has changed."
        }

        val concept = TrudyEmotionalSemantics.conceptForQuestion(question)
        val direction = TrudyEmotionalResponseText.sign(delta)
        val lead = directLead(concept, semantic.axis, direction, comparison.confidence)
        val detail = if (direction == 0) {
            "Your ${semantic.displayName} ratings have been about the same as your earlier baseline."
        } else {
            val magnitude = TrudyEmotionalResponseText.format(abs(delta))
            val phrase = TrudyEmotionalSemantics.phraseForQuestion(concept, semantic, direction)
            "Your ${semantic.displayName} ratings have averaged about $magnitude ${if (magnitude == "1") "point" else "points"} ${if (delta > 0) "higher" else "lower"} than your earlier baseline, so you've been reporting ${TrudyEmotionalResponseText.selfReport(phrase)} recently."
        }
        return (lead + detail + TrudyEmotionalResponseText.qualityTail(comparison.confidence, comparison.dataQualityStatus)).trim()
    }

    private fun directLead(
        concept: TrudyEmotionalConcept?,
        measuredAxis: TrudyEmotionalAxis,
        numericDirection: Int,
        confidence: TrudyConfidence
    ): String {
        if (concept == null || concept.axis != measuredAxis || concept.targetDirection == 0 || numericDirection == 0) return ""
        val matches = concept.targetDirection == numericDirection
        return when {
            confidence == TrudyConfidence.LOW && matches -> "It looks like it — "
            confidence == TrudyConfidence.LOW -> "It doesn't look like it — "
            matches -> "Yes — "
            else -> "No — "
        }
    }

    private fun unknown(c: TrudyBaselineComparison): String = when {
        c.absoluteDelta == null -> "I don't have enough recent and earlier ${TrudyEmotionalSemantics.displayMetric(c.metricId)} check-ins to tell yet."
        c.absoluteDelta > 0 -> "Your ${TrudyEmotionalSemantics.displayMetric(c.metricId)} rating has been higher recently than its earlier baseline."
        c.absoluteDelta < 0 -> "Your ${TrudyEmotionalSemantics.displayMetric(c.metricId)} rating has been lower recently than its earlier baseline."
        else -> "Your ${TrudyEmotionalSemantics.displayMetric(c.metricId)} rating has been about the same as its earlier baseline."
    }
}
