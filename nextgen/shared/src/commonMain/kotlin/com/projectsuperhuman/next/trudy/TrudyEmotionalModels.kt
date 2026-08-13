package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

enum class TrudyEmotionalAxis { VALENCE, CALMNESS, ENERGY, CONFIDENCE, CONNECTEDNESS, FOCUS }

enum class TrudyEmotionalConcept(
    val axis: TrudyEmotionalAxis,
    val targetDirection: Int
) {
    MOOD(TrudyEmotionalAxis.VALENCE, 0),
    HAPPINESS(TrudyEmotionalAxis.VALENCE, 1),
    SADNESS(TrudyEmotionalAxis.VALENCE, -1),
    CALMNESS(TrudyEmotionalAxis.CALMNESS, 1),
    ANXIETY(TrudyEmotionalAxis.CALMNESS, -1),
    ENERGY(TrudyEmotionalAxis.ENERGY, 1),
    DRAIN(TrudyEmotionalAxis.ENERGY, -1),
    CONFIDENCE(TrudyEmotionalAxis.CONFIDENCE, 1),
    INSECURITY(TrudyEmotionalAxis.CONFIDENCE, -1),
    CONNECTEDNESS(TrudyEmotionalAxis.CONNECTEDNESS, 1),
    LONELINESS(TrudyEmotionalAxis.CONNECTEDNESS, -1),
    FOCUS(TrudyEmotionalAxis.FOCUS, 1),
    DISTRACTION(TrudyEmotionalAxis.FOCUS, -1)
}

data class TrudyEmotionalMetricSemantic(
    val axis: TrudyEmotionalAxis,
    val metricId: String,
    val displayName: String,
    val positivePolePhrase: String,
    val negativePolePhrase: String
) {
    fun phraseForDelta(delta: Double): String = when {
        delta > 1e-6 -> positivePolePhrase
        delta < -1e-6 -> negativePolePhrase
        else -> "about the same"
    }

    fun promptHint(): String =
        "self-report $displayName on a -1 to +1 bipolar scale; +1 trends toward $positivePolePhrase, -1 trends toward $negativePolePhrase, and 0 is neutral; wellness signal only, not diagnostic"
}
