package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.max

internal object CardioComparisonEngine {
    fun compareWithPrevious(
        current: CardioSession,
        history: List<CardioSession>
    ): CardioSessionComparison? {
        val previous = previousComparableSession(current, history) ?: return null
        val deltas = buildDeltas(current, previous)
        if (deltas.isEmpty()) return null
        return CardioSessionComparison(
            currentSessionId = current.id,
            previousSessionId = previous.id,
            activity = current.activity,
            description = describe(current, previous),
            deltas = deltas
        )
    }

    fun previousComparableSession(
        current: CardioSession,
        history: List<CardioSession>
    ): CardioSession? = history
        .asSequence()
        .filter { it.id != current.id }
        .filter { it.activity == current.activity }
        .filter { it.endedAt < current.endedAt }
        .filter { comparable(current, it) }
        .maxByOrNull { it.endedAt }

    fun comparable(a: CardioSession, b: CardioSession): Boolean {
        if (a.activity != b.activity) return false
        if (
            a.workoutType != CardioWorkoutType.FREE &&
            b.workoutType != CardioWorkoutType.FREE &&
            a.workoutType != b.workoutType
        ) return false

        val durationRatio = ratioDifference(a.durationSeconds.toDouble(), b.durationSeconds.toDouble())
        if (durationRatio > 0.30) return false

        val aDistance = a.distanceKm
        val bDistance = b.distanceKm
        if (aDistance != null && bDistance != null) {
            val allowed = max(0.5, aDistance * 0.20)
            if (abs(aDistance - bDistance) > allowed) return false
        }

        val aElevation = a.elevationGainM
        val bElevation = b.elevationGainM
        if (aElevation != null && bElevation != null) {
            val allowed = max(50.0, aElevation * 0.25)
            if (abs(aElevation - bElevation) > allowed) return false
        }

        return when (a.activity.paceMode) {
            CardioPaceMode.PER_KM -> a.avgPaceSecPerKm != null && b.avgPaceSecPerKm != null
            CardioPaceMode.SPEED -> a.avgSpeedKmh != null && b.avgSpeedKmh != null
            CardioPaceMode.PER_500M -> a.avgSplit500mSeconds != null && b.avgSplit500mSeconds != null
            CardioPaceMode.PER_100M -> a.avgPace100mSeconds != null && b.avgPace100mSeconds != null
            CardioPaceMode.NONE -> a.durationSeconds > 0 && b.durationSeconds > 0
        }
    }

    private fun describe(current: CardioSession, previous: CardioSession): String {
        val currentHr = current.avgHeartRate
        val previousHr = previous.avgHeartRate
        val hrSimilar = currentHr != null && previousHr != null && abs(currentHr - previousHr) <= 5
        val hrLower = currentHr != null && previousHr != null && currentHr <= previousHr - 3

        return when (current.activity.paceMode) {
            CardioPaceMode.PER_KM -> {
                val c = current.avgPaceSecPerKm ?: return "Comparable result"
                val p = previous.avgPaceSecPerKm ?: return "Comparable result"
                when {
                    c <= p - 5 && (hrSimilar || (currentHr != null && previousHr != null && currentHr < previousHr)) ->
                        "Faster at similar or lower average HR"
                    hrLower && abs(c - p) <= 8 -> "Lower average HR at similar pace"
                    else -> "Comparable result"
                }
            }
            CardioPaceMode.SPEED -> {
                val c = current.avgSpeedKmh ?: return "Comparable result"
                val p = previous.avgSpeedKmh ?: return "Comparable result"
                when {
                    c >= p + 0.5 && (hrSimilar || (currentHr != null && previousHr != null && currentHr < previousHr)) ->
                        "Faster at similar or lower average HR"
                    hrLower && abs(c - p) <= 0.5 -> "Lower average HR at similar speed"
                    else -> "Comparable result"
                }
            }
            CardioPaceMode.PER_500M -> {
                val c = current.avgSplit500mSeconds ?: return "Comparable result"
                val p = previous.avgSplit500mSeconds ?: return "Comparable result"
                when {
                    c <= p - 3 && (hrSimilar || (currentHr != null && previousHr != null && currentHr < previousHr)) ->
                        "Faster split at similar or lower average HR"
                    hrLower && abs(c - p) <= 5 -> "Lower average HR at similar split"
                    else -> "Comparable result"
                }
            }
            CardioPaceMode.PER_100M -> {
                val c = current.avgPace100mSeconds ?: return "Comparable result"
                val p = previous.avgPace100mSeconds ?: return "Comparable result"
                when {
                    c <= p - 2 && (hrSimilar || (currentHr != null && previousHr != null && currentHr < previousHr)) ->
                        "Faster pace at similar or lower average HR"
                    hrLower && abs(c - p) <= 3 -> "Lower average HR at similar pace"
                    else -> "Comparable result"
                }
            }
            CardioPaceMode.NONE -> "Comparable result"
        }
    }

    private fun buildDeltas(current: CardioSession, previous: CardioSession): List<CardioMetricDelta> = buildList {
        pair(current.avgPaceSecPerKm, previous.avgPaceSecPerKm)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.PACE, c, p, c - p, "sec/km", true))
        }
        pair(current.avgSpeedKmh, previous.avgSpeedKmh)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.SPEED, c, p, c - p, "km/h"))
        }
        pair(current.avgSplit500mSeconds, previous.avgSplit500mSeconds)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.SPLIT_500M, c, p, c - p, "sec/500m", true))
        }
        pair(current.avgPace100mSeconds, previous.avgPace100mSeconds)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.PACE_100M, c, p, c - p, "sec/100m", true))
        }
        pair(current.avgHeartRate, previous.avgHeartRate)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.AVG_HEART_RATE, c, p, c - p, "bpm"))
        }
        pair(current.distanceKm, previous.distanceKm)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.DISTANCE, c, p, c - p, "km"))
        }
        add(
            CardioMetricDelta(
                CardioComparisonMetric.DURATION,
                current.durationSeconds.toDouble(),
                previous.durationSeconds.toDouble(),
                (current.durationSeconds - previous.durationSeconds).toDouble(),
                "sec"
            )
        )
        pair(current.elevationGainM, previous.elevationGainM)?.let { (c, p) ->
            add(CardioMetricDelta(CardioComparisonMetric.ELEVATION, c, p, c - p, "m"))
        }
    }

    private fun ratioDifference(a: Double, b: Double): Double {
        if (a <= 0.0 || b <= 0.0) return Double.POSITIVE_INFINITY
        return abs(a - b) / max(a, b)
    }

    private fun pair(a: Int?, b: Int?): Pair<Double, Double>? =
        if (a != null && b != null) a.toDouble() to b.toDouble() else null

    private fun pair(a: Double?, b: Double?): Pair<Double, Double>? =
        if (a != null && b != null) a to b else null
}
