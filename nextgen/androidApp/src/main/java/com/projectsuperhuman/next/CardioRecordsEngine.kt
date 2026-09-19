package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.max

internal object CardioRecordsEngine {
    private data class ExactSegmentResult(
        val sessionId: String,
        val durationSeconds: Double,
        val source: CardioLapProvenance
    )

    fun recordsForActivity(
        activity: CardioActivityType,
        sessions: List<CardioSession>,
        laps: List<CardioLap> = emptyList()
    ): List<CardioRecord> {
        val matching = sessions.filter { it.activity == activity }
        if (matching.isEmpty()) return emptyList()

        val records = mutableListOf<CardioRecord>()
        matching.maxByOrNull { it.durationSeconds }?.let { session ->
            records += CardioRecord(
                activity = activity,
                type = CardioRecordType.LONGEST_DURATION,
                label = "Longest duration",
                sessionId = session.id,
                value = session.durationSeconds.toDouble(),
                unit = "sec",
                verified = true
            )
        }
        matching.filter { (it.distanceKm ?: 0.0) > 0.0 }
            .maxByOrNull { it.distanceKm ?: 0.0 }
            ?.let { session ->
                records += CardioRecord(
                    activity = activity,
                    type = CardioRecordType.FARTHEST_DISTANCE,
                    label = if (activity == CardioActivityType.CYCLING) "Longest ride" else "Farthest distance",
                    sessionId = session.id,
                    value = session.distanceKm,
                    unit = "km",
                    verified = true
                )
            }

        when (activity.paceMode) {
            CardioPaceMode.PER_KM -> matching
                .filter { (it.distanceKm ?: 0.0) > 0.0 && (it.avgPaceSecPerKm ?: 0) > 0 }
                .minByOrNull { it.avgPaceSecPerKm ?: Int.MAX_VALUE }
                ?.let { records += averageRecord(activity, it, CardioRecordType.BEST_AVERAGE_PACE, "Best average pace", it.avgPaceSecPerKm!!.toDouble(), "sec/km") }

            CardioPaceMode.SPEED -> matching
                .filter {
                    (it.distanceKm ?: 0.0) >= 1.0 &&
                        it.durationSeconds >= 600 &&
                        (it.avgSpeedKmh ?: 0.0) > 0.0
                }
                .maxByOrNull { it.avgSpeedKmh ?: 0.0 }
                ?.let { records += averageRecord(activity, it, CardioRecordType.BEST_AVERAGE_SPEED, "Best average speed", it.avgSpeedKmh!!, "km/h") }

            CardioPaceMode.PER_500M -> matching
                .filter { (it.avgSplit500mSeconds ?: 0) > 0 }
                .minByOrNull { it.avgSplit500mSeconds ?: Int.MAX_VALUE }
                ?.let { records += averageRecord(activity, it, CardioRecordType.BEST_AVERAGE_SPLIT, "Best average split", it.avgSplit500mSeconds!!.toDouble(), "sec/500m") }

            CardioPaceMode.PER_100M -> matching
                .filter { (it.avgPace100mSeconds ?: 0) > 0 }
                .minByOrNull { it.avgPace100mSeconds ?: Int.MAX_VALUE }
                ?.let { records += averageRecord(activity, it, CardioRecordType.BEST_AVERAGE_PACE, "Best average pace", it.avgPace100mSeconds!!.toDouble(), "sec/100m") }

            CardioPaceMode.NONE -> Unit
        }

        exactTargets(activity).forEach { (label, meters) ->
            val exact = bestExactSegment(activity, matching, laps, meters)
            records += if (exact != null) {
                CardioRecord(
                    activity = activity,
                    type = CardioRecordType.EXACT_DISTANCE,
                    label = label,
                    sessionId = exact.sessionId,
                    value = exact.durationSeconds,
                    unit = "sec",
                    targetDistanceMeters = meters,
                    verified = true,
                    source = exact.source
                )
            } else {
                CardioRecord(
                    activity = activity,
                    type = CardioRecordType.EXACT_DISTANCE,
                    label = label,
                    sessionId = null,
                    value = null,
                    unit = "sec",
                    targetDistanceMeters = meters,
                    verified = false,
                    lockedReason = "Requires real lap, split, route timing or an external exact-distance record"
                )
            }
        }
        return records
    }

    fun allRecords(
        sessions: List<CardioSession>,
        laps: List<CardioLap> = emptyList()
    ): Map<CardioActivityType, List<CardioRecord>> =
        sessions.groupBy { it.activity }
            .mapValues { (activity, values) -> recordsForActivity(activity, values, laps) }

    /**
     * Returns a PR only from actual exact-distance lap/split/route data. Session-average
     * pace is deliberately not accepted here.
     */
    fun exactDistanceRecord(
        activity: CardioActivityType,
        sessions: List<CardioSession>,
        laps: List<CardioLap>,
        targetDistanceMeters: Double
    ): CardioRecord {
        require(targetDistanceMeters > 0.0)
        val matching = sessions.filter { it.activity == activity }
        val exact = bestExactSegment(activity, matching, laps, targetDistanceMeters)
        val label = formatDistanceLabel(targetDistanceMeters)
        return if (exact == null) {
            CardioRecord(
                activity = activity,
                type = CardioRecordType.EXACT_DISTANCE,
                label = label,
                sessionId = null,
                value = null,
                unit = "sec",
                targetDistanceMeters = targetDistanceMeters,
                verified = false,
                lockedReason = "Exact timing data unavailable"
            )
        } else {
            CardioRecord(
                activity = activity,
                type = CardioRecordType.EXACT_DISTANCE,
                label = label,
                sessionId = exact.sessionId,
                value = exact.durationSeconds,
                unit = "sec",
                targetDistanceMeters = targetDistanceMeters,
                verified = true,
                source = exact.source
            )
        }
    }

    private fun bestExactSegment(
        activity: CardioActivityType,
        sessions: List<CardioSession>,
        laps: List<CardioLap>,
        targetMeters: Double
    ): ExactSegmentResult? {
        if (sessions.isEmpty() || laps.isEmpty()) return null
        val allowedSessionIds = sessions.filter { it.activity == activity }.mapTo(hashSetOf()) { it.id }
        var best: ExactSegmentResult? = null

        laps.asSequence()
            .filter { it.sessionId in allowedSessionIds }
            .filter { it.exactDistance && it.distanceMeters > 0.0 && it.durationSeconds > 0.0 }
            .groupBy { it.sessionId }
            .forEach { (sessionId, rawLaps) ->
                val ordered = rawLaps.sortedWith(compareBy<CardioLap> { it.index }.thenBy { it.startedAt })
                for (start in ordered.indices) {
                    var distance = 0.0
                    var duration = 0.0
                    var source = ordered[start].source
                    for (end in start until ordered.size) {
                        val lap = ordered[end]
                        distance += lap.distanceMeters
                        duration += lap.durationSeconds
                        if (source != lap.source) source = CardioLapProvenance.UNKNOWN
                        val tolerance = exactToleranceMeters(targetMeters)
                        if (abs(distance - targetMeters) <= tolerance) {
                            val candidate = ExactSegmentResult(sessionId, duration, source)
                            if (best == null || candidate.durationSeconds < best!!.durationSeconds) best = candidate
                            break
                        }
                        if (distance > targetMeters + tolerance) break
                    }
                }
            }
        return best
    }

    private fun exactTargets(activity: CardioActivityType): List<Pair<String, Double>> = when (activity) {
        CardioActivityType.RUNNING,
        CardioActivityType.WALKING,
        CardioActivityType.TREADMILL,
        CardioActivityType.HIKING -> listOf(
            "Fastest exact 1 km" to 1_000.0,
            "Fastest exact mile" to 1_609.344,
            "Fastest exact 5 km" to 5_000.0,
            "Fastest exact 10 km" to 10_000.0
        )
        CardioActivityType.ROWING -> listOf(
            "Fastest exact 500 m" to 500.0,
            "Fastest exact 2 km" to 2_000.0
        )
        CardioActivityType.SWIMMING -> listOf(
            "Fastest exact 100 m" to 100.0,
            "Fastest exact 200 m" to 200.0,
            "Fastest exact 400 m" to 400.0,
            "Fastest exact 1,500 m" to 1_500.0
        )
        CardioActivityType.CYCLING,
        CardioActivityType.STATIONARY_BIKE -> listOf(
            "Fastest exact 1 km" to 1_000.0,
            "Fastest exact 5 km" to 5_000.0,
            "Fastest exact 10 km" to 10_000.0
        )
        else -> emptyList()
    }

    private fun averageRecord(
        activity: CardioActivityType,
        session: CardioSession,
        type: CardioRecordType,
        label: String,
        value: Double,
        unit: String
    ) = CardioRecord(
        activity = activity,
        type = type,
        label = label,
        sessionId = session.id,
        value = value,
        unit = unit,
        verified = true
    )

    private fun exactToleranceMeters(targetMeters: Double): Double =
        max(1.0, targetMeters * 0.001)

    private fun formatDistanceLabel(meters: Double): String = when {
        abs(meters - 1_609.344) < 1.0 -> "Fastest exact mile"
        meters >= 1_000.0 -> "Fastest exact " + (meters / 1_000.0) + " km"
        else -> "Fastest exact " + meters.toInt() + " m"
    }
}
