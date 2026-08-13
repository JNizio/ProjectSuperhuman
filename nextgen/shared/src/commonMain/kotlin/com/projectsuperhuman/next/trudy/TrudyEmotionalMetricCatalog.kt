package com.projectsuperhuman.next.trudy

internal object TrudyEmotionalMetricCatalog {
    private val byAxis = listOf(
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.VALENCE, "emotional_valence", "mood", "a happier / more positive mood", "a sadder / lower mood"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.CALMNESS, "emotional_calmness", "calmness", "calmer", "more anxious"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.ENERGY, "emotional_energy", "energy", "more energetic", "more drained"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.CONFIDENCE, "emotional_confidence", "confidence", "more confident", "more insecure"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.CONNECTEDNESS, "emotional_connectedness", "connectedness", "more connected", "lonelier"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.FOCUS, "emotional_focus", "focus", "more focused", "more distracted")
    ).associateBy { it.axis }

    fun semanticForMetric(metricId: String): TrudyEmotionalMetricSemantic? {
        val id = metricId.trim().lowercase().replace('-', '_').replace(' ', '_')
        return when {
            id == "emotional_valence" || any(id, "valence", "mood", "happiness") -> byAxis[TrudyEmotionalAxis.VALENCE]
            id == "emotional_calmness" || any(id, "calm", "anxi") -> byAxis[TrudyEmotionalAxis.CALMNESS]
            id == "emotional_energy" || any(id, "energy", "drain", "exhaust") -> byAxis[TrudyEmotionalAxis.ENERGY]
            id == "emotional_confidence" || any(id, "confidence", "insecure") -> byAxis[TrudyEmotionalAxis.CONFIDENCE]
            id == "emotional_connectedness" || any(id, "connected", "lonely") -> byAxis[TrudyEmotionalAxis.CONNECTEDNESS]
            id == "emotional_focus" || any(id, "focus", "distract") -> byAxis[TrudyEmotionalAxis.FOCUS]
            else -> null
        }
    }

    fun semantic(axis: TrudyEmotionalAxis): TrudyEmotionalMetricSemantic = byAxis.getValue(axis)

    fun selectMetric(axis: TrudyEmotionalAxis, ids: Collection<String>): String? =
        ids.firstOrNull { semanticForMetric(it)?.axis == axis }

    private fun any(value: String, vararg tokens: String) = tokens.any { it in value }
}
