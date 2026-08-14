package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.emotional.BipolarScaleValue
import com.projectsuperhuman.next.emotional.EmotionalEntry
import com.projectsuperhuman.next.emotional.EmotionalMetricId
import com.projectsuperhuman.next.emotional.EmotionalMetricIds
import com.projectsuperhuman.next.emotional.EmotionalObservation
import com.projectsuperhuman.next.emotional.EmotionalSourceMetadata
import com.projectsuperhuman.next.emotional.EmotionalSourceType
import kotlin.math.roundToInt

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

    val axisIds = listOf(HAPPY_SAD, CALM_ANXIOUS, ENERGETIC_DRAINED, CONFIDENT_INSECURE, CONNECTED_LONELY, FOCUSED_DISTRACTED)

    private val canonicalByAxis: Map<String, EmotionalMetricId> = mapOf(
        HAPPY_SAD to EmotionalMetricIds.VALENCE,
        CALM_ANXIOUS to EmotionalMetricIds.CALMNESS,
        ENERGETIC_DRAINED to EmotionalMetricIds.ENERGY,
        CONFIDENT_INSECURE to EmotionalMetricIds.CONFIDENCE,
        CONNECTED_LONELY to EmotionalMetricIds.CONNECTEDNESS,
        FOCUSED_DISTRACTED to EmotionalMetricIds.FOCUS
    )
    private val axisByCanonical = canonicalByAxis.entries.associate { (axis, metric) -> metric.value to axis }

    fun normalize(values: Map<String, Int>): Map<String, Int> = axisIds.associateWith { axisId -> snap(values[axisId] ?: 0) }

    fun snap(value: Int): Int {
        val clamped = value.coerceIn(MIN_VALUE, MAX_VALUE)
        return (kotlin.math.round(clamped / STEP.toDouble()).toInt() * STEP).coerceIn(MIN_VALUE, MAX_VALUE)
    }

    fun toCanonicalEntry(values: Map<String, Int>, timestampEpochMs: Long): EmotionalEntry {
        val normalizedUi = normalize(values)
        return EmotionalEntry(
            timestampEpochMs = timestampEpochMs,
            source = EmotionalSourceMetadata("project_superhuman_emotional_ui", EmotionalSourceType.SELF_REPORT),
            observations = axisIds.mapNotNull { axisId ->
                val metric = canonicalByAxis[axisId] ?: return@mapNotNull null
                val uiValue = normalizedUi[axisId] ?: return@mapNotNull null
                EmotionalObservation(metric, BipolarScaleValue((-uiValue / 100.0).coerceIn(-1.0, 1.0)))
            },
            metadata = mapOf("sourceRecordId" to "emotional-checkin-$timestampEpochMs")
        )
    }

    fun fromCanonicalValues(values: List<HealthValue>, recordedAtLabel: String? = null): EmotionalPresentationSnapshot? {
        val rows = values.filter { it.metric in axisByCanonical.keys }
        if (rows.isEmpty()) return null
        val latest = rows.groupBy { it.metric }.mapValues { (_, group) -> group.maxByOrNull { it.timestampEpochMs } }
        val uiValues = buildMap {
            latest.forEach { (metric, row) ->
                val axis = axisByCanonical[metric] ?: return@forEach
                val canonical = row?.value?.coerceIn(-1.0, 1.0) ?: return@forEach
                put(axis, snap((-canonical * 100.0).roundToInt()))
            }
        }
        return EmotionalPresentationSnapshot(
            axisValues = uiValues,
            recordedAtLabel = recordedAtLabel,
            recordedAtEpochMs = rows.maxOfOrNull { it.timestampEpochMs }
        )
    }
}

internal data class EmotionalPresentationSnapshot(
    val axisValues: Map<String, Int>,
    val recordedAtLabel: String? = null,
    val recordedAtEpochMs: Long? = null
) {
    val normalizedValues: Map<String, Int>
        get() = EmotionalPresentationContract.normalize(axisValues)
}
