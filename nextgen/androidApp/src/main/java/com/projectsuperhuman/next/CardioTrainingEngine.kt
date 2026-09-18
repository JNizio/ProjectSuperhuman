package com.projectsuperhuman.next

import kotlin.math.roundToInt

/**
 * Training concepts inspired by strong cardio trackers, kept deliberately
 * transparent so Project Superhuman never invents sensor data.
 */
internal enum class CardioWorkoutType(val label: String, val description: String) {
    FREE("Free", "Unstructured cardio"),
    RECOVERY("Recovery", "Very easy effort"),
    EASY("Easy", "Comfortable aerobic work"),
    ZONE_2("Zone 2", "Steady low-intensity aerobic work"),
    LONG("Long", "Longer endurance session"),
    TEMPO("Tempo", "Sustained moderately hard effort"),
    THRESHOLD("Threshold", "Hard sustained work near threshold"),
    INTERVALS("Intervals", "Repeated work and recovery"),
    VO2_MAX("VO2 max", "Short hard aerobic intervals"),
    SPRINTS("Sprints", "Very short high-intensity efforts"),
    CUSTOM("Custom", "User-defined session");

    companion object {
        fun fromStored(raw: String?): CardioWorkoutType =
            entries.firstOrNull { it.name == raw } ?: FREE
    }
}

internal data class CardioLoadSnapshot(
    val recent7DayLoad: Double,
    val previous21DayWeeklyAverage: Double?,
    val loadRatio: Double?,
    val recentSessions: Int,
    val recentMinutes: Int,
    val sourceCoverage: Int
)

internal data class CardioEfficiencyComparison(
    val activity: CardioActivityType,
    val currentSessionId: String,
    val previousSessionId: String,
    val currentPaceSecPerKm: Int?,
    val previousPaceSecPerKm: Int?,
    val currentAvgHr: Int?,
    val previousAvgHr: Int?,
    val message: String
)

/**
 * Transparent session-load hierarchy:
 * 1) measured zone time -> weighted zone minutes;
 * 2) otherwise session RPE x minutes;
 * 3) otherwise unknown.
 *
 * This is a training-management score, not a medical measurement.
 */
internal fun cardioSessionLoad(session: CardioSession): Double? {
    if (session.zoneSeconds.isNotEmpty()) {
        val weights = mapOf(1 to 1.0, 2 to 1.4, 3 to 2.0, 4 to 3.0, 5 to 4.0)
        return session.zoneSeconds.entries.sumOf { (zone, seconds) ->
            (seconds / 60.0) * (weights[zone] ?: 1.0)
        }
    }
    val rpe = session.rpe
    return if (rpe != null && rpe > 0.0) {
        (session.durationSeconds / 60.0) * rpe
    } else null
}

internal fun calculateCardioLoadSnapshot(
    sessions: List<CardioSession>,
    now: Long = System.currentTimeMillis()
): CardioLoadSnapshot {
    val day = 86_400_000L
    val sevenStart = now - 7L * day
    val twentyEightStart = now - 28L * day
    val recent = sessions.filter { it.endedAt in sevenStart..now }
    val previous = sessions.filter { it.endedAt in twentyEightStart until sevenStart }

    val recentLoads = recent.mapNotNull(::cardioSessionLoad)
    val previousLoads = previous.mapNotNull(::cardioSessionLoad)
    val recentLoad = recentLoads.sum()
    val previousWeekly = if (previousLoads.isNotEmpty()) previousLoads.sum() / 3.0 else null
    val ratio = if (previousWeekly != null && previousWeekly > 0.0 && recentLoads.isNotEmpty()) {
        recentLoad / previousWeekly
    } else null

    return CardioLoadSnapshot(
        recent7DayLoad = recentLoad,
        previous21DayWeeklyAverage = previousWeekly,
        loadRatio = ratio,
        recentSessions = recent.size,
        recentMinutes = recent.sumOf { it.durationSeconds } / 60,
        sourceCoverage = recentLoads.size
    )
}

internal fun cardioLoadLabel(snapshot: CardioLoadSnapshot): String = when {
    snapshot.sourceCoverage == 0 -> "Needs RPE or zone data"
    snapshot.loadRatio == null -> "Building baseline"
    snapshot.loadRatio < 0.8 -> "Below recent baseline"
    snapshot.loadRatio <= 1.3 -> "Near recent baseline"
    else -> "Above recent baseline"
}

/**
 * Compares only like-for-like activity sessions and only reports an efficiency
 * message when both pace and HR are actually present.
 */
internal fun findCardioEfficiencyComparison(
    current: CardioSession,
    history: List<CardioSession>
): CardioEfficiencyComparison? {
    if (current.avgPaceSecPerKm == null || current.avgHeartRate == null) return null
    val previous = history
        .asSequence()
        .filter { it.id != current.id && it.activity == current.activity }
        .filter { it.avgPaceSecPerKm != null && it.avgHeartRate != null }
        .filter {
            val currentDistance = current.distanceKm
            val priorDistance = it.distanceKm
            currentDistance == null || priorDistance == null ||
                kotlin.math.abs(currentDistance - priorDistance) <= maxOf(0.5, currentDistance * 0.2)
        }
        .sortedByDescending { it.endedAt }
        .firstOrNull() ?: return null

    val paceDelta = previous.avgPaceSecPerKm!! - current.avgPaceSecPerKm
    val hrDelta = previous.avgHeartRate!! - current.avgHeartRate
    val message = when {
        paceDelta > 5 && hrDelta >= 0 ->
            "Faster at the same or lower average heart rate"
        hrDelta > 3 && paceDelta >= -5 ->
            "Lower average heart rate at a similar or faster pace"
        else ->
            "Comparable session available for pace and heart-rate review"
    }

    return CardioEfficiencyComparison(
        activity = current.activity,
        currentSessionId = current.id,
        previousSessionId = previous.id,
        currentPaceSecPerKm = current.avgPaceSecPerKm,
        previousPaceSecPerKm = previous.avgPaceSecPerKm,
        currentAvgHr = current.avgHeartRate,
        previousAvgHr = previous.avgHeartRate,
        message = message
    )
}

internal fun formatCardioLoad(value: Double): String = value.roundToInt().toString()
