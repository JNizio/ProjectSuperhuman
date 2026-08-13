package com.projectsuperhuman.next.trudy

internal object TrudyEmotionalQuestionParser {
    private val emotionalTokens = listOf(
        "feel", "emotion", "mood", "anx", "happy", "sad", "calm", "drain", "exhaust", "energy",
        "confiden", "insecure", "connect", "lonely", "focus", "distract"
    )
    private val axisTokens = listOf(
        "mood", "anx", "happy", "sad", "calm", "drain", "exhaust", "energy", "confiden", "insecure",
        "connect", "lonely", "focus", "distract"
    )

    fun looksEmotional(message: String): Boolean {
        val text = message.lowercase()
        return emotionalTokens.any { it in text }
    }

    fun concept(message: String): TrudyEmotionalConcept? {
        val text = message.lowercase()
        return when {
            any(text, "anxious", "anxiety") -> TrudyEmotionalConcept.ANXIETY
            any(text, "drained", "drain", "exhausted") -> TrudyEmotionalConcept.DRAIN
            any(text, "distracted", "distraction") -> TrudyEmotionalConcept.DISTRACTION
            any(text, "insecure", "insecurity") -> TrudyEmotionalConcept.INSECURITY
            any(text, "lonely", "lonelier", "loneliness") -> TrudyEmotionalConcept.LONELINESS
            any(text, "sad", "sadder", "sadness", "low mood") -> TrudyEmotionalConcept.SADNESS
            any(text, "calm", "calmer") -> TrudyEmotionalConcept.CALMNESS
            any(text, "happy", "happier", "happiness", "positive mood") -> TrudyEmotionalConcept.HAPPINESS
            any(text, "energy", "energetic") -> TrudyEmotionalConcept.ENERGY
            any(text, "confident", "confidence") -> TrudyEmotionalConcept.CONFIDENCE
            any(text, "connected", "connectedness") -> TrudyEmotionalConcept.CONNECTEDNESS
            any(text, "focus", "focused") -> TrudyEmotionalConcept.FOCUS
            "mood" in text || any(text, "feel", "feeling", "emotion", "emotionally") -> TrudyEmotionalConcept.MOOD
            else -> null
        }
    }

    fun isBroadSummary(message: String): Boolean {
        val text = message.lowercase()
        return looksEmotional(message) && axisTokens.none { it in text } && any(text, "feel", "feeling", "emotion", "emotionally")
    }

    private fun any(value: String, vararg tokens: String) = tokens.any { it in value }
}
