package com.projectsuperhuman.next

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal object CardioTrendEngine {
    fun filterRange(
        sessions: List<CardioSession>,
        range: CardioAnalysisRange,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CardioSession> {
        if (range == CardioAnalysisRange.ALL_TIME) {
            return sessions.filter { it.endedAt <= nowEpochMs }
        }
        val window = range.window(nowEpochMs, zoneId)
        return sessions.filter { window.contains(it.endedAt) }
    }

    fun volumeTrend(
        sessions: List<CardioSession>,
        range: CardioAnalysisRange,
        metric: CardioTrendMetric,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioTrendSeries {
        require(
            metric in setOf(
                CardioTrendMetric.MINUTES,
                CardioTrendMetric.DISTANCE_KM,
                CardioTrendMetric.SESSION_COUNT,
                CardioTrendMetric.LOAD,
                CardioTrendMetric.ZONE_2_MINUTES
            )
        ) { "Unsupported volume metric: $metric" }

        val filtered = filterRange(sessions, range, nowEpochMs, zoneId)
        val granularity = range.defaultGranularity()
        val buckets = bucketDates(filtered, range, granularity, nowEpochMs, zoneId)
        val grouped = filtered.groupBy { bucketStart(dateOf(it.endedAt, zoneId), granularity) }

        val points = buckets.map { start ->
            val bucketSessions = grouped[start].orEmpty()
            aggregateVolumeBucket(start, granularity, bucketSessions, metric)
        }
        return CardioTrendSeries(metric, granularity, points)
    }

    fun activityTrend(
        sessions: List<CardioSession>,
        activity: CardioActivityType,
        range: CardioAnalysisRange,
        metric: CardioTrendMetric,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioTrendSeries? {
        val activitySessions = filterRange(sessions, range, nowEpochMs, zoneId)
            .filter { it.activity == activity }
        if (metric !in availableActivityMetrics(activity, activitySessions)) return null

        val granularity = range.defaultGranularity()
        val buckets = bucketDates(activitySessions, range, granularity, nowEpochMs, zoneId)
        val grouped = activitySessions.groupBy { bucketStart(dateOf(it.endedAt, zoneId), granularity) }
        val points = buckets.map { start ->
            val bucketSessions = grouped[start].orEmpty()
            val values = bucketSessions.mapNotNull { sessionMetricValue(it, metric) }
            CardioTrendPoint(
                bucketStart = start,
                bucketEnd = bucketEnd(start, granularity),
                value = values.takeIf { it.isNotEmpty() }?.average(),
                sessionCount = bucketSessions.size,
                contributingSessionCount = values.size,
                quality = if (values.isEmpty()) CardioMetricQuality.UNAVAILABLE else metricQuality(metric)
            )
        }
        return CardioTrendSeries(metric, granularity, points)
    }

    fun availableActivityMetrics(
        activity: CardioActivityType,
        sessions: List<CardioSession>
    ): Set<CardioTrendMetric> {
        val matching = sessions.filter { it.activity == activity }
        if (matching.isEmpty()) return emptySet()
        val candidates = buildSet {
            add(CardioTrendMetric.DURATION_MINUTES)
            add(CardioTrendMetric.RPE)
            add(CardioTrendMetric.AVG_HEART_RATE)
            add(CardioTrendMetric.MAX_HEART_RATE)
            if (activity.supportsDistance) add(CardioTrendMetric.DISTANCE_KM)
            if (activity.supportsCadence) add(CardioTrendMetric.CADENCE)
            if (activity.supportsElevation) add(CardioTrendMetric.ELEVATION_GAIN_M)
            when (activity.paceMode) {
                CardioPaceMode.PER_KM -> add(CardioTrendMetric.PACE_SEC_PER_KM)
                CardioPaceMode.SPEED -> add(CardioTrendMetric.SPEED_KMH)
                CardioPaceMode.PER_500M -> add(CardioTrendMetric.PACE_SEC_PER_KM)
                CardioPaceMode.PER_100M -> add(CardioTrendMetric.PACE_SEC_PER_KM)
                CardioPaceMode.NONE -> Unit
            }
        }
        return candidates.filterTo(linkedSetOf()) { metric ->
            matching.any { sessionMetricValue(it, metric) != null }
        }
    }

    private fun aggregateVolumeBucket(
        start: LocalDate,
        granularity: CardioBucketGranularity,
        sessions: List<CardioSession>,
        metric: CardioTrendMetric
    ): CardioTrendPoint {
        val end = bucketEnd(start, granularity)
        if (sessions.isEmpty()) {
            return CardioTrendPoint(
                bucketStart = start,
                bucketEnd = end,
                value = 0.0,
                sessionCount = 0,
                contributingSessionCount = 0,
                quality = if (metric == CardioTrendMetric.LOAD || metric == CardioTrendMetric.ZONE_2_MINUTES || metric == CardioTrendMetric.DISTANCE_KM) {
                    CardioMetricQuality.UNAVAILABLE
                } else CardioMetricQuality.MEASURED
            )
        }

        return when (metric) {
            CardioTrendMetric.MINUTES -> CardioTrendPoint(
                start, end,
                sessions.sumOf { it.durationSeconds } / 60.0,
                sessions.size,
                sessions.size,
                CardioMetricQuality.MEASURED
            )
            CardioTrendMetric.SESSION_COUNT -> CardioTrendPoint(
                start, end,
                sessions.size.toDouble(),
                sessions.size,
                sessions.size,
                CardioMetricQuality.MEASURED
            )
            CardioTrendMetric.DISTANCE_KM -> {
                val values = sessions.mapNotNull { it.distanceKm }
                CardioTrendPoint(
                    start, end,
                    values.takeIf { it.isNotEmpty() }?.sum(),
                    sessions.size,
                    values.size,
                    if (values.isEmpty()) CardioMetricQuality.UNAVAILABLE else CardioMetricQuality.MEASURED
                )
            }
            CardioTrendMetric.LOAD -> {
                val values = sessions.mapNotNull { CardioAnalyticsEngine.loadDetail(it).score }
                CardioTrendPoint(
                    start, end,
                    values.takeIf { it.isNotEmpty() }?.sum(),
                    sessions.size,
                    values.size,
                    if (values.isEmpty()) CardioMetricQuality.UNAVAILABLE else CardioMetricQuality.DERIVED
                )
            }
            CardioTrendMetric.ZONE_2_MINUTES -> {
                val withZones = sessions.filter { it.zoneSeconds.isNotEmpty() }
                CardioTrendPoint(
                    start, end,
                    withZones.takeIf { it.isNotEmpty() }?.sumOf { it.zoneSeconds[2] ?: 0 }?.div(60.0),
                    sessions.size,
                    withZones.size,
                    if (withZones.isEmpty()) CardioMetricQuality.UNAVAILABLE else CardioMetricQuality.MEASURED
                )
            }
            else -> error("Unsupported volume metric")
        }
    }

    private fun sessionMetricValue(session: CardioSession, metric: CardioTrendMetric): Double? = when (metric) {
        CardioTrendMetric.DISTANCE_KM -> session.distanceKm
        CardioTrendMetric.PACE_SEC_PER_KM -> when (session.activity.paceMode) {
            CardioPaceMode.PER_KM -> session.avgPaceSecPerKm?.toDouble()
            CardioPaceMode.PER_500M -> session.avgSplit500mSeconds?.toDouble()
            CardioPaceMode.PER_100M -> session.avgPace100mSeconds?.toDouble()
            else -> null
        }
        CardioTrendMetric.SPEED_KMH -> session.avgSpeedKmh
        CardioTrendMetric.AVG_HEART_RATE -> session.avgHeartRate?.toDouble()
        CardioTrendMetric.MAX_HEART_RATE -> session.maxHeartRate?.toDouble()
        CardioTrendMetric.CADENCE -> session.cadence?.toDouble()
        CardioTrendMetric.ELEVATION_GAIN_M -> session.elevationGainM
        CardioTrendMetric.RPE -> session.rpe
        CardioTrendMetric.DURATION_MINUTES -> session.durationSeconds / 60.0
        CardioTrendMetric.MINUTES -> session.durationSeconds / 60.0
        CardioTrendMetric.SESSION_COUNT -> 1.0
        CardioTrendMetric.LOAD -> CardioAnalyticsEngine.loadDetail(session).score
        CardioTrendMetric.ZONE_2_MINUTES -> session.zoneSeconds[2]?.div(60.0)
    }

    private fun metricQuality(metric: CardioTrendMetric): CardioMetricQuality = when (metric) {
        CardioTrendMetric.PACE_SEC_PER_KM,
        CardioTrendMetric.SPEED_KMH,
        CardioTrendMetric.LOAD -> CardioMetricQuality.DERIVED
        else -> CardioMetricQuality.MEASURED
    }

    private fun bucketDates(
        sessions: List<CardioSession>,
        range: CardioAnalysisRange,
        granularity: CardioBucketGranularity,
        nowEpochMs: Long,
        zoneId: ZoneId
    ): List<LocalDate> {
        val nowDate = dateOf(nowEpochMs, zoneId)
        val rawStart = when {
            range != CardioAnalysisRange.ALL_TIME -> dateOf(range.window(nowEpochMs, zoneId).startEpochMs, zoneId)
            sessions.isNotEmpty() -> sessions.minOf { dateOf(it.endedAt, zoneId) }
            else -> return emptyList()
        }
        val first = bucketStart(rawStart, granularity)
        val last = bucketStart(nowDate, granularity)
        return generateSequence(first) { nextBucket(it, granularity) }
            .takeWhile { !it.isAfter(last) }
            .toList()
    }

    private fun dateOf(epochMs: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    private fun bucketStart(date: LocalDate, granularity: CardioBucketGranularity): LocalDate = when (granularity) {
        CardioBucketGranularity.DAILY -> date
        CardioBucketGranularity.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        CardioBucketGranularity.MONTHLY -> date.withDayOfMonth(1)
    }

    private fun bucketEnd(start: LocalDate, granularity: CardioBucketGranularity): LocalDate = when (granularity) {
        CardioBucketGranularity.DAILY -> start
        CardioBucketGranularity.WEEKLY -> start.plusDays(6)
        CardioBucketGranularity.MONTHLY -> start.plusMonths(1).minusDays(1)
    }

    private fun nextBucket(start: LocalDate, granularity: CardioBucketGranularity): LocalDate = when (granularity) {
        CardioBucketGranularity.DAILY -> start.plusDays(1)
        CardioBucketGranularity.WEEKLY -> start.plusWeeks(1)
        CardioBucketGranularity.MONTHLY -> start.plusMonths(1)
    }
}
