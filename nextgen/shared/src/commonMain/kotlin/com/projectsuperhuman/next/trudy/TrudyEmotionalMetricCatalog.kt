package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.emotional.EmotionalMetricIds

/** Trudy-specific language semantics keyed by the canonical Emotional metric IDs. */
internal object TrudyEmotionalMetricCatalog {
    private val byAxis = listOf(
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.VALENCE, EmotionalMetricIds.VALENCE.value, "mood", "a happier / more positive mood", "a sadder / lower mood"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.CALMNESS, EmotionalMetricIds.CALMNESS.value, "calmness", "calmer", "more anxious"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.ENERGY, EmotionalMetricIds.ENERGY.value, "energy", "more energetic", "more drained"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.CONFIDENCE, EmotionalMetricIds.CONFIDENCE.value, "confidence", "more confident", "more insecure"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.CONNECTEDNESS, EmotionalMetricIds.CONNECTEDNESS.value, "connectedness", "more connected", "lonelier"),
        TrudyEmotionalMetricSemantic(TrudyEmotionalAxis.FOCUS, EmotionalMetricIds.FOCUS.value, "focus", "more focused", "more distracted")
    ).associateBy { it.axis }

    fun semanticForMetric(metricId: String): TrudyEmotionalMetricSemantic? {
        val id = metricId.trim().lowercase().replace('-', '_').replace(' ', '_')
        return when {
            id == EmotionalMetricIds.VALENCE.value || any(id, "valence", "mood", "happiness") -> byAxis[TrudyEmotionalAxis.VALENCE]
            id == EmotionalMetricIds.CALMNESS.value || any(id, "calm", "anxi") -> byAxis[TrudyEmotionalAxis.CALMNESS]
            id == EmotionalMetricIds.ENERGY.value || any(id, "energy", "drain", "exhaust") -> byAxis[TrudyEmotionalAxis.ENERGY]
            id == EmotionalMetricIds.CONFIDENCE.value || any(id, "confidence", "insecure") -> byAxis[TrudyEmotionalAxis.CONFIDENCE]
            id == EmotionalMetricIds.CONNECTEDNESS.value || any(id, "connected", "lonely") -> byAxis[TrudyEmotionalAxis.CONNECTEDNESS]
            id == EmotionalMetricIds.FOCUS.value || any(id, "focus", "distract") -> byAxis[TrudyEmotionalAxis.FOCUS]
            else -> null
        }
    }

    fun semantic(axis: TrudyEmotionalAxis): TrudyEmotionalMetricSemantic = byAxis.getValue(axis)

    fun selectMetric(axis: TrudyEmotionalAxis, ids: Collection<String>): String? =
        ids.firstOrNull { semanticForMetric(it)?.axis == axis }

    private fun any(value: String, vararg tokens: String) = tokens.any { it in value }
}
