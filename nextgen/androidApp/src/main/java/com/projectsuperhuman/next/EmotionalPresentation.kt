package com.projectsuperhuman.next

/**
 * Presentation-only contract for the Emotional UI.
 *
 * Agent 1 owns the canonical Emotional domain model. Integration code should translate that model
 * to/from these stable axis IDs instead of making Compose depend on persistence or domain classes.
 *
 * Values are normalized to -100..100:
 *  - -100 = fully toward the left-hand label
 *  -    0 = centered
 *  - +100 = fully toward the right-hand label
 */
internal object EmotionalPresentationContract {
    const val MIN_VALUE = -100
    const val MAX_VALUE = 100
    const val STEP = 5

    const val HAPPY_SAD = "happy_sad"
    const val CALM_ANXIOUS = "calm_anxious"
    const val ENERGETIC_DRAINED = "energetic_drained"
    const val CONFIDENT_INSECURE = "confident_insecure"
    const val CONNECTED_LONELY = "connected_lonely"
    const val FOCUSED_DISTRACTED = "focused_distracted"

    val axisIds: List<String> = listOf(
        HAPPY_SAD,
        CALM_ANXIOUS,
        ENERGETIC_DRAINED,
        CONFIDENT_INSECURE,
        CONNECTED_LONELY,
        FOCUSED_DISTRACTED
    )

    fun normalize(values: Map<String, Int>): Map<String, Int> =
        axisIds.associateWith { axisId -> snap(values[axisId] ?: 0) }

    fun snap(value: Int): Int {
        val clamped = value.coerceIn(MIN_VALUE, MAX_VALUE)
        val snapped = kotlin.math.round(clamped / STEP.toDouble()).toInt() * STEP
        return snapped.coerceIn(MIN_VALUE, MAX_VALUE)
    }
}

/**
 * UI-facing current state only. [recordedAtLabel] is already formatted display text.
 * No persistence semantics are implied by this class.
 */
internal data class EmotionalPresentationSnapshot(
    val axisValues: Map<String, Int>,
    val recordedAtLabel: String? = null
) {
    val normalizedValues: Map<String, Int>
        get() = EmotionalPresentationContract.normalize(axisValues)
}
