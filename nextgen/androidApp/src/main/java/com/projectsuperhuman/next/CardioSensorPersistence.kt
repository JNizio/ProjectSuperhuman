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


/**
 * Core-architecture adapter: merge real measured HR evidence into a CardioSession before the
 * repository writes it. Explicit user-entered summary fields win, while quality/provenance and
 * timeline evidence are retained in versioned extension metadata.
 */
internal fun CardioSession.withCardioHeartRateSummary(
    summary: CardioHeartRateSummary
): CardioSession {
    if (summary.sampleCount <= 0) return this

    val sensorExtensions = summary.toMetadata()
        .filterKeys { key ->
            key != "avgHeartRate" &&
                key != "minHeartRate" &&
                key != "maxHeartRate" &&
                !(key.startsWith("zone") && key.endsWith("Seconds"))
        }

    return copy(
        avgHeartRate = avgHeartRate ?: summary.averageBpm,
        minHeartRate = minHeartRate ?: summary.minBpm,
        maxHeartRate = maxHeartRate ?: summary.maxBpm,
        zoneSeconds = if (zoneSeconds.isNotEmpty()) zoneSeconds else summary.zoneSeconds,
        extensions = extensions + sensorExtensions
    )
}
