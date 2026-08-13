package com.projectsuperhuman.next.trudy

internal object TrudyEmotionalPhraseInterpreter {
    fun phrase(concept: TrudyEmotionalConcept?, metric: TrudyEmotionalMetricSemantic, numericDirection: Int): String {
        if (numericDirection == 0) return "about the same"
        if (concept == TrudyEmotionalConcept.MOOD && concept.axis == metric.axis) {
            return if (numericDirection > 0) "in a more positive mood" else "in a lower mood"
        }
        if (concept != null && concept.axis == metric.axis && concept.targetDirection != 0) {
            val targetSeen = numericDirection == concept.targetDirection
            return when (concept) {
                TrudyEmotionalConcept.HAPPINESS -> if (targetSeen) "happier" else "less happy"
                TrudyEmotionalConcept.SADNESS -> if (targetSeen) "sadder" else "less sad"
                TrudyEmotionalConcept.ANXIETY -> if (targetSeen) "more anxious" else "less anxious"
                TrudyEmotionalConcept.CALMNESS -> if (targetSeen) "calmer" else "less calm"
                TrudyEmotionalConcept.ENERGY -> if (targetSeen) "more energetic" else "less energetic"
                TrudyEmotionalConcept.DRAIN -> if (targetSeen) "more drained" else "less drained"
                TrudyEmotionalConcept.CONFIDENCE -> if (targetSeen) "more confident" else "less confident"
                TrudyEmotionalConcept.INSECURITY -> if (targetSeen) "more insecure" else "less insecure"
                TrudyEmotionalConcept.CONNECTEDNESS -> if (targetSeen) "more connected" else "less connected"
                TrudyEmotionalConcept.LONELINESS -> if (targetSeen) "lonelier" else "less lonely"
                TrudyEmotionalConcept.FOCUS -> if (targetSeen) "more focused" else "less focused"
                TrudyEmotionalConcept.DISTRACTION -> if (targetSeen) "more distracted" else "less distracted"
                TrudyEmotionalConcept.MOOD -> error("Handled above")
            }
        }
        return metric.phraseForDelta(numericDirection.toDouble())
    }
}
