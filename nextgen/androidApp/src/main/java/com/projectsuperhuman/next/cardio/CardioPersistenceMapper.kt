package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.math.roundToInt

internal fun cardioSessionFromValue(row: HealthValue): CardioSession {
    val meta = row.metadata
    fun d(key: String) = meta[key]?.toDoubleOrNull()
    fun i(key: String) = meta[key]?.toIntOrNull()

    val ended = meta["endedAt"]?.toLongOrNull() ?: row.timestampEpochMs
    val duration = i("durationSeconds")
        ?: (row.value * 60.0).roundToInt().coerceAtLeast(0)
    val started = meta["startedAt"]?.toLongOrNull()
        ?: (ended - duration * 1000L)
    val zones = (1..5).mapNotNull { zone ->
        i("zone" + zone + "Seconds")?.takeIf { it > 0 }?.let { zone to it }
    }.toMap()
    val extensions = meta.filterKeys { it.startsWith("ext.") }
        .mapKeys { it.key.removePrefix("ext.") }

    return CardioSession(
        id = meta["sessionId"].orEmpty().ifBlank { "legacy-cardio-" + row.timestampEpochMs },
        activity = CardioActivityType.fromStored(meta["activityType"]),
        startedAt = started,
        endedAt = ended,
        durationSeconds = duration,
        pausedDurationSeconds = i("pausedDurationSeconds") ?: 0,
        distanceKm = d("distanceKm"),
        avgHeartRate = i("avgHeartRate"),
        maxHeartRate = i("maxHeartRate"),
        minHeartRate = i("minHeartRate"),
        caloriesKcal = d("caloriesKcal"),
        avgPaceSecPerKm = i("avgPaceSecPerKm"),
        bestPaceSecPerKm = i("bestPaceSecPerKm"),
        avgSpeedKmh = d("avgSpeedKmh"),
        maxSpeedKmh = d("maxSpeedKmh"),
        elevationGainM = d("elevationGainM"),
        cadence = i("cadence"),
        rpe = d("rpe"),
        notes = meta["notes"].orEmpty(),
        source = meta["cardioSource"].orEmpty().ifBlank { row.source },
        workoutType = CardioWorkoutType.fromStored(meta["workoutType"]),
        zoneSeconds = zones,
        avgSplit500mSeconds = i("avgSplit500mSeconds"),
        avgPace100mSeconds = i("avgPace100mSeconds"),
        schemaVersion = i("cardioSchemaVersion") ?: 1,
        extensions = extensions
    )
}

internal fun CardioSession.toHealthValue(): HealthValue {
    val meta = mutableMapOf(
        "sessionId" to id,
        "sourceRecordId" to ("cardio:" + id),
        "cardioSchemaVersion" to schemaVersion.toString(),
        "activityType" to activity.name,
        "activityName" to activity.displayName,
        "startedAt" to startedAt.toString(),
        "endedAt" to endedAt.toString(),
        "durationSeconds" to durationSeconds.toString(),
        "pausedDurationSeconds" to pausedDurationSeconds.toString(),
        "notes" to notes,
        "cardioSource" to source,
        "workoutType" to workoutType.name
    )
    distanceKm?.let { meta["distanceKm"] = it.toString() }
    avgHeartRate?.let { meta["avgHeartRate"] = it.toString() }
    maxHeartRate?.let { meta["maxHeartRate"] = it.toString() }
    minHeartRate?.let { meta["minHeartRate"] = it.toString() }
    caloriesKcal?.let { meta["caloriesKcal"] = it.toString() }
    avgPaceSecPerKm?.let { meta["avgPaceSecPerKm"] = it.toString() }
    bestPaceSecPerKm?.let { meta["bestPaceSecPerKm"] = it.toString() }
    avgSpeedKmh?.let { meta["avgSpeedKmh"] = it.toString() }
    maxSpeedKmh?.let { meta["maxSpeedKmh"] = it.toString() }
    elevationGainM?.let { meta["elevationGainM"] = it.toString() }
    cadence?.let { meta["cadence"] = it.toString() }
    rpe?.let { meta["rpe"] = it.toString() }
    avgSplit500mSeconds?.let { meta["avgSplit500mSeconds"] = it.toString() }
    avgPace100mSeconds?.let { meta["avgPace100mSeconds"] = it.toString() }
    zoneSeconds.forEach { (zone, seconds) -> meta["zone" + zone + "Seconds"] = seconds.toString() }
    extensions.forEach { (key, value) ->
        if (key.isNotBlank()) meta["ext." + key] = value
    }

    return HealthValue(
        domain = HealthDomain.EXERCISE,
        metric = "cardio_session",
        value = durationSeconds / 60.0,
        unit = "min",
        timestampEpochMs = endedAt,
        source = "native-cardio",
        metadata = meta
    )
}
