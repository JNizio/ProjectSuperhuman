package com.projectsuperhuman.next

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class CardioAnalysisRange(val label: String, val days: Long?) {
    DAYS_7("7 days", 7),
    WEEKS_4("4 weeks", 28),
    MONTHS_3("3 months", 92),
    MONTHS_6("6 months", 183),
    YEAR_1("1 year", 366),
    ALL_TIME("All time", null);

    fun window(nowEpochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): CardioDateWindow {
        if (this == ALL_TIME) return CardioDateWindow(Long.MIN_VALUE, nowEpochMs)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val start = today.minusDays(requireNotNull(days) - 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return CardioDateWindow(start, nowEpochMs)
    }
}

internal data class CardioDateWindow(
    val startEpochMs: Long,
    val endEpochMs: Long
) {
    fun contains(epochMs: Long): Boolean = epochMs in startEpochMs..endEpochMs
}

internal enum class CardioBucketGranularity { DAILY, WEEKLY, MONTHLY }

internal fun CardioAnalysisRange.defaultGranularity(): CardioBucketGranularity = when (this) {
    CardioAnalysisRange.DAYS_7 -> CardioBucketGranularity.DAILY
    CardioAnalysisRange.WEEKS_4, CardioAnalysisRange.MONTHS_3 -> CardioBucketGranularity.WEEKLY
    CardioAnalysisRange.MONTHS_6, CardioAnalysisRange.YEAR_1, CardioAnalysisRange.ALL_TIME -> CardioBucketGranularity.MONTHLY
}

internal enum class CardioMetricQuality {
    MEASURED,
    DERIVED,
    ESTIMATED,
    UNAVAILABLE
}

internal enum class CardioTrendMetric(val label: String) {
    MINUTES("Minutes"),
    DISTANCE_KM("Distance"),
    SESSION_COUNT("Sessions"),
    LOAD("Cardio load"),
    ZONE_2_MINUTES("Zone 2"),
    PACE_SEC_PER_KM("Pace"),
    SPEED_KMH("Speed"),
    AVG_HEART_RATE("Average HR"),
    MAX_HEART_RATE("Max HR"),
    CADENCE("Cadence"),
    ELEVATION_GAIN_M("Elevation"),
    RPE("RPE"),
    DURATION_MINUTES("Duration")
}

internal data class CardioTrendPoint(
    val bucketStart: LocalDate,
    val bucketEnd: LocalDate,
    val value: Double?,
    val sessionCount: Int,
    val contributingSessionCount: Int,
    val quality: CardioMetricQuality
)

internal data class CardioTrendSeries(
    val metric: CardioTrendMetric,
    val granularity: CardioBucketGranularity,
    val points: List<CardioTrendPoint>
)

internal enum class CardioLapProvenance {
    LIVE_SENSOR,
    GPS_ROUTE,
    HEALTH_CONNECT,
    MANUAL_EXACT,
    EXTERNAL_RECORD,
    UNKNOWN
}

/**
 * Analytics-ready lap/split model. Distance and duration must come from a real split,
 * lap, route segment or external record; analytics must never synthesize laps from a
 * session-average pace.
 */
internal data class CardioLap(
    val sessionId: String,
    val index: Int,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Double,
    val distanceMeters: Double,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val source: CardioLapProvenance = CardioLapProvenance.UNKNOWN,
    val exactDistance: Boolean = true
) {
    val paceSecondsPerKm: Double?
        get() = distanceMeters.takeIf { it > 0.0 }?.let { durationSeconds / (it / 1000.0) }

    val speedKmh: Double?
        get() = durationSeconds.takeIf { it > 0.0 }?.let { (distanceMeters / 1000.0) / (it / 3600.0) }
}

internal enum class CardioRecordType {
    LONGEST_DURATION,
    FARTHEST_DISTANCE,
    EXACT_DISTANCE,
    BEST_AVERAGE_PACE,
    BEST_AVERAGE_SPEED,
    BEST_AVERAGE_SPLIT
}

internal data class CardioRecord(
    val activity: CardioActivityType,
    val type: CardioRecordType,
    val label: String,
    val sessionId: String?,
    val value: Double?,
    val unit: String,
    val targetDistanceMeters: Double? = null,
    val verified: Boolean,
    val lockedReason: String? = null,
    val source: CardioLapProvenance? = null
)

internal enum class CardioLoadSource(val label: String) {
    MEASURED_ZONES("Measured HR zones"),
    RPE_MINUTES("RPE × minutes"),
    UNAVAILABLE("Unavailable")
}

internal data class CardioLoadDetail(
    val score: Double?,
    val source: CardioLoadSource,
    val measuredZoneSeconds: Int,
    val sessionDurationSeconds: Int,
    val zoneCoveragePercent: Double,
    val unclassifiedSeconds: Int
)

internal enum class CardioBaselineQuality(val label: String) {
    INSUFFICIENT("Insufficient baseline"),
    BUILDING("Building baseline"),
    USABLE("Usable baseline")
}

internal data class CardioLoadAnalytics(
    val recent7DayLoad: Double,
    val previous21DayWeeklyAverage: Double?,
    val loadRatio: Double?,
    val baselineQuality: CardioBaselineQuality,
    val baselineScoredSessions: Int,
    val baselineActiveWeeks: Int,
    val baselineSpanDays: Int,
    val scoredSessions: Int,
    val totalSessions: Int,
    val measuredZoneCoveragePercent: Double,
    val unclassifiedSeconds: Int
)

internal data class CardioIntensityDistribution(
    val zoneSeconds: Map<Int, Int>,
    val unclassifiedSeconds: Int,
    val totalSeconds: Int,
    val sessionsWithMeasuredZones: Int,
    val sessionsWithoutMeasuredZones: Int
)

internal data class CardioConsistency(
    val sessionsPerWeek: Double,
    val activeWeeks: Int,
    val totalWeeks: Int,
    val averageWeeklyMinutes: Double,
    val averageWeeklyDistanceKm: Double?,
    val rolling28DaySessionsPerWeek: Double
)

internal data class CardioGoal(
    val id: String,
    val type: CardioGoalType,
    val target: Double,
    val enabled: Boolean = true
)

internal enum class CardioGoalType(val label: String) {
    WEEKLY_MINUTES("Weekly minutes"),
    WEEKLY_DISTANCE_KM("Weekly distance"),
    WEEKLY_SESSIONS("Weekly sessions"),
    WEEKLY_ZONE_2_MINUTES("Weekly Zone 2")
}

internal data class CardioGoalProgress(
    val goal: CardioGoal,
    val current: Double,
    val progressFraction: Double
)

internal data class CardioHistoryFilter(
    val range: CardioAnalysisRange = CardioAnalysisRange.ALL_TIME,
    val activities: Set<CardioActivityType> = emptySet(),
    val workoutTypes: Set<CardioWorkoutType> = emptySet(),
    val sources: Set<String> = emptySet(),
    val query: String = ""
)

internal data class CardioHistoryPage(
    val items: List<CardioSession>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val hasMore: Boolean
)

internal enum class CardioComparisonMetric {
    PACE,
    SPEED,
    SPLIT_500M,
    PACE_100M,
    AVG_HEART_RATE,
    DISTANCE,
    DURATION,
    ELEVATION
}

internal data class CardioMetricDelta(
    val metric: CardioComparisonMetric,
    val current: Double,
    val previous: Double,
    val delta: Double,
    val unit: String,
    val lowerIsBetterForPerformance: Boolean = false
)

internal data class CardioSessionComparison(
    val currentSessionId: String,
    val previousSessionId: String,
    val activity: CardioActivityType,
    val description: String,
    val deltas: List<CardioMetricDelta>
)

internal data class CardioPostWorkoutSummary(
    val sessionId: String,
    val durationSeconds: Int,
    val distanceKm: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val paceSecondsPerKm: Int?,
    val speedKmh: Double?,
    val zoneSeconds: Map<Int, Int>,
    val load: CardioLoadDetail,
    val rpe: Double?,
    val newRecords: List<CardioRecord>,
    val comparison: CardioSessionComparison?
)

internal enum class CardioUnitSystem { METRIC, IMPERIAL }

internal data class CardioTimeSeriesSample(
    val elapsedSeconds: Double,
    val heartRateBpm: Double?,
    val speedMetersPerSecond: Double? = null,
    val powerWatts: Double? = null
)

internal data class CardioAerobicDecoupling(
    val percent: Double,
    val input: String,
    val coveragePercent: Double,
    val quality: CardioMetricQuality = CardioMetricQuality.ESTIMATED
)
