package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthValue

/**
 * Merge sensor evidence into an existing cardio_session row without taking ownership of Cardio's
 * persistence model. Explicit user-entered avg/max/zone fields win; sensor-only quality and
 * provenance fields are added alongside them.
 */
internal fun HealthValue.withCardioHeartRateSummary(summary: CardioHeartRateSummary): HealthValue {
    if (summary.sampleCount <= 0) return this
    val merged = metadata.toMutableMap()
    summary.toMetadata().forEach { (key, value) ->
        when {
            key == "avgHeartRate" && merged.containsKey(key) -> Unit
            key == "maxHeartRate" && merged.containsKey(key) -> Unit
            key.startsWith("zone") && key.endsWith("Seconds") && merged.containsKey(key) -> Unit
            else -> merged[key] = value
        }
    }
    return copy(metadata = merged)
}
