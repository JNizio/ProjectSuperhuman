package com.projectsuperhuman.next

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

internal object CardioAnalyticsEngine {
    private val zoneWeights = mapOf(1 to 1.0, 2 to 1.4, 3 to 2.0, 4 to 3.0, 5 to 4.0)

    fun loadDetail(session: CardioSession): CardioLoadDetail {
        val duration = session.durationSeconds.coerceAtLeast(0)
        val measuredSeconds = session.zoneSeconds
            .filterKeys { it in 1..5 }
            .values
            .sum()
            .coerceIn(0, duration)
        val coverage = if (duration > 0) measuredSeconds * 100.0 / duration else 0.0

        if (measuredSeconds > 0) {
            val score = session.zoneSeconds.entries.sumOf { (zone, seconds) ->
                (seconds.coerceAtLeast(0) / 60.0) * (zoneWeights[zone] ?: 0.0)
            }
            return CardioLoadDetail(
                score = score,
                source = CardioLoadSource.MEASURED_ZONES,
                measuredZoneSeconds = measuredSeconds,
                sessionDurationSeconds = duration,
                zoneCoveragePercent = coverage,
                unclassifiedSeconds = (duration - measuredSeconds).coerceAtLeast(0)
            )
        }

        val rpe = session.rpe
        if (rpe != null && rpe > 0.0 && duration > 0) {
            return CardioLoadDetail(
                score = (duration / 60.0) * rpe,
                source = CardioLoadSource.RPE_MINUTES,
                measuredZoneSeconds = 0,
                sessionDurationSeconds = duration,
                zoneCoveragePercent = 0.0,
                unclassifiedSeconds = duration
            )
        }

        return CardioLoadDetail(
            score = null,
            source = CardioLoadSource.UNAVAILABLE,
            measuredZoneSeconds = 0,
            sessionDurationSeconds = duration,
            zoneCoveragePercent = 0.0,
            unclassifiedSeconds = duration
        )
    }

    fun loadAnalytics(
        sessions: List<CardioSession>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): CardioLoadAnalytics {
        val day = 86_400_000L
        val recentStart = nowEpochMs - 7L * day
        val baselineStart = nowEpochMs - 28L * day
        val recent = sessions.filter { it.endedAt in recentStart..nowEpochMs }
        val baseline = sessions.filter { it.endedAt in baselineStart until recentStart }

        val recentDetails = recent.map(::loadDetail)
        val baselineDetails = baseline.map(::loadDetail)
        val recentScores = recentDetails.mapNotNull { it.score }
        val baselineScores = baselineDetails.mapNotNull { it.score }
        val baselineQuality = baselineQuality(baseline.filter { loadDetail(it).score != null })

        val recentLoad = recentScores.sum()
        val previousWeekly = baselineScores.takeIf { it.isNotEmpty() }?.sum()?.div(3.0)
        val ratio = if (
            baselineQuality == CardioBaselineQuality.USABLE &&
            previousWeekly != null &&
            previousWeekly > 0.0 &&
            recentScores.isNotEmpty()
        ) recentLoad / previousWeekly else null

        val totalSeconds = recent.sumOf { it.durationSeconds.coerceAtLeast(0) }
        val measuredSeconds = recentDetails.sumOf { it.measuredZoneSeconds }
        return CardioLoadAnalytics(
            recent7DayLoad = recentLoad,
            previous21DayWeeklyAverage = previousWeekly,
            loadRatio = ratio,
            baselineQuality = baselineQuality,
            scoredSessions = recentScores.size,
            totalSessions = recent.size,
            measuredZoneCoveragePercent = if (totalSeconds > 0) measuredSeconds * 100.0 / totalSeconds else 0.0,
            unclassifiedSeconds = (totalSeconds - measuredSeconds).coerceAtLeast(0)
        )
    }

    fun baselineQuality(scoredBaselineSessions: List<CardioSession>): CardioBaselineQuality {
        if (scoredBaselineSessions.size < 3) return CardioBaselineQuality.INSUFFICIENT
        val sorted = scoredBaselineSessions.sortedBy { it.endedAt }
        val spanDays = ((sorted.last().endedAt - sorted.first().endedAt).coerceAtLeast(0L) / 86_400_000.0)
        if (spanDays < 7.0) return CardioBaselineQuality.INSUFFICIENT

        val activeWeeks = sorted.map { it.endedAt / (7L * 86_400_000L) }.distinct().size
        return if (sorted.size >= 8 && activeWeeks >= 3 && spanDays >= 14.0) {
            CardioBaselineQuality.USABLE
        } else {
            CardioBaselineQuality.BUILDING
        }
    }

    fun intensityDistribution(sessions: List<CardioSession>): CardioIntensityDistribution {
        val zones = (1..5).associateWith { zone ->
            sessions.sumOf { it.zoneSeconds[zone]?.coerceAtLeast(0) ?: 0 }
        }
        val measuredSessions = sessions.count { it.zoneSeconds.values.any { seconds -> seconds > 0 } }
        val unclassified = sessions.sumOf { session ->
            val measured = session.zoneSeconds
                .filterKeys { it in 1..5 }
                .values
                .sum()
                .coerceIn(0, session.durationSeconds.coerceAtLeast(0))
            (session.durationSeconds.coerceAtLeast(0) - measured).coerceAtLeast(0)
        }
        return CardioIntensityDistribution(
            zoneSeconds = zones,
            unclassifiedSeconds = unclassified,
            totalSeconds = sessions.sumOf { it.durationSeconds.coerceAtLeast(0) },
            sessionsWithMeasuredZones = measuredSessions,
            sessionsWithoutMeasuredZones = sessions.size - measuredSessions
        )
    }

    fun consistency(
        sessions: List<CardioSession>,
        range: CardioAnalysisRange,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioConsistency {
        val filtered = CardioTrendEngine.filterRange(sessions, range, nowEpochMs, zoneId)
        if (filtered.isEmpty()) {
            return CardioConsistency(0.0, 0, 0, 0.0, null, 0.0)
        }

        val firstDay = Instant.ofEpochMilli(filtered.minOf { it.endedAt }).atZone(zoneId).toLocalDate()
        val lastDay = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val requestedDays = range.days?.toDouble()
            ?: (java.time.temporal.ChronoUnit.DAYS.between(firstDay, lastDay).coerceAtLeast(0) + 1).toDouble()
        val weeks = ceil(requestedDays / 7.0).toInt().coerceAtLeast(1)
        val activeWeeks = filtered.map {
            val date = Instant.ofEpochMilli(it.endedAt).atZone(zoneId).toLocalDate()
            val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
            weekStart
        }.distinct().size

        val distanceValues = filtered.mapNotNull { it.distanceKm }
        val rollingStart = nowEpochMs - 28L * 86_400_000L
        val rollingCount = sessions.count { it.endedAt in rollingStart..nowEpochMs }

        return CardioConsistency(
            sessionsPerWeek = filtered.size.toDouble() / weeks,
            activeWeeks = activeWeeks,
            totalWeeks = weeks,
            averageWeeklyMinutes = (filtered.sumOf { it.durationSeconds } / 60.0) / weeks,
            averageWeeklyDistanceKm = distanceValues.takeIf { it.isNotEmpty() }?.sum()?.div(weeks),
            rolling28DaySessionsPerWeek = rollingCount / 4.0
        )
    }

    fun goalProgress(
        goals: List<CardioGoal>,
        sessions: List<CardioSession>,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CardioGoalProgress> {
        val week = CardioTrendEngine.filterRange(
            sessions,
            CardioAnalysisRange.DAYS_7,
            nowEpochMs,
            zoneId
        )
        return goals.filter { it.enabled && it.target > 0.0 }.map { goal ->
            val current = when (goal.type) {
                CardioGoalType.WEEKLY_MINUTES -> week.sumOf { it.durationSeconds } / 60.0
                CardioGoalType.WEEKLY_DISTANCE_KM -> week.mapNotNull { it.distanceKm }.sum()
                CardioGoalType.WEEKLY_SESSIONS -> week.size.toDouble()
                CardioGoalType.WEEKLY_ZONE_2_MINUTES -> week.sumOf { it.zoneSeconds[2] ?: 0 } / 60.0
            }
            CardioGoalProgress(goal, current, (current / goal.target).coerceAtLeast(0.0))
        }
    }

    fun postWorkoutSummary(
        session: CardioSession,
        history: List<CardioSession>,
        laps: List<CardioLap> = emptyList()
    ): CardioPostWorkoutSummary {
        val records = CardioRecordsEngine.recordsForActivity(
            activity = session.activity,
            sessions = history,
            laps = laps
        ).filter { it.sessionId == session.id && it.verified }

        return CardioPostWorkoutSummary(
            sessionId = session.id,
            durationSeconds = session.durationSeconds,
            distanceKm = session.distanceKm,
            avgHeartRate = session.avgHeartRate,
            maxHeartRate = session.maxHeartRate,
            paceSecondsPerKm = session.avgPaceSecPerKm,
            speedKmh = session.avgSpeedKmh,
            zoneSeconds = session.zoneSeconds,
            load = loadDetail(session),
            rpe = session.rpe,
            newRecords = records,
            comparison = CardioComparisonEngine.compareWithPrevious(session, history)
        )
    }

    /**
     * Analytical estimate only. Requires continuous HR plus speed or power, at least 20 minutes,
     * at least 20 usable samples and >=80% HR/output coverage across the sampled timeline.
     */
    fun aerobicDecoupling(samples: List<CardioTimeSeriesSample>): CardioAerobicDecoupling? {
        if (samples.size < 20) return null
        val sorted = samples.sortedBy { it.elapsedSeconds }
        val duration = sorted.last().elapsedSeconds - sorted.first().elapsedSeconds
        if (duration < 20.0 * 60.0) return null

        val usePower = sorted.count { (it.powerWatts ?: 0.0) > 0.0 } >= sorted.size * 0.8
        val useSpeed = sorted.count { (it.speedMetersPerSecond ?: 0.0) > 0.0 } >= sorted.size * 0.8
        if (!usePower && !useSpeed) return null

        val usable = sorted.filter {
            val hr = it.heartRateBpm
            val output = if (usePower) it.powerWatts else it.speedMetersPerSecond
            hr != null && hr > 0.0 && output != null && output > 0.0
        }
        val coverage = usable.size * 100.0 / sorted.size
        if (coverage < 80.0 || usable.size < 20) return null

        val start = usable.first().elapsedSeconds
        val end = usable.last().elapsedSeconds
        val trimStart = start + (end - start) * 0.10
        val trimEnd = start + (end - start) * 0.90
        val trimmed = usable.filter { it.elapsedSeconds in trimStart..trimEnd }
        if (trimmed.size < 16) return null
        val midpoint = (trimStart + trimEnd) / 2.0
        val first = trimmed.filter { it.elapsedSeconds <= midpoint }
        val second = trimmed.filter { it.elapsedSeconds > midpoint }
        if (first.size < 8 || second.size < 8) return null

        fun efficiency(part: List<CardioTimeSeriesSample>): Double = part.map {
            val output = if (usePower) requireNotNull(it.powerWatts) else requireNotNull(it.speedMetersPerSecond)
            output / requireNotNull(it.heartRateBpm)
        }.average()

        val firstEf = efficiency(first)
        val secondEf = efficiency(second)
        if (firstEf <= 0.0) return null
        val percent = ((firstEf - secondEf) / firstEf) * 100.0
        return CardioAerobicDecoupling(
            percent = percent,
            input = if (usePower) "power/HR" else "speed/HR",
            coveragePercent = coverage
        )
    }
}
