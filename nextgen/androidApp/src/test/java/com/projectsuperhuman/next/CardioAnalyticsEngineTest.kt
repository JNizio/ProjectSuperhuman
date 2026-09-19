package com.projectsuperhuman.next

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioAnalyticsEngineTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-19T12:00:00Z").toEpochMilli()

    @Test
    fun rangeFiltering() {
        val recent = session("recent", endedAt = Instant.parse("2026-09-18T12:00:00Z").toEpochMilli())
        val old = session("old", endedAt = Instant.parse("2026-08-01T12:00:00Z").toEpochMilli())
        val result = CardioTrendEngine.filterRange(listOf(recent, old), CardioAnalysisRange.DAYS_7, now, utc)
        assertEquals(listOf("recent"), result.map { it.id })
    }

    @Test
    fun dailyAggregation() {
        val a = session("a", durationSeconds = 600, endedAt = Instant.parse("2026-09-19T08:00:00Z").toEpochMilli())
        val b = session("b", durationSeconds = 1200, endedAt = Instant.parse("2026-09-19T10:00:00Z").toEpochMilli())
        val series = CardioTrendEngine.volumeTrend(listOf(a, b), CardioAnalysisRange.DAYS_7, CardioTrendMetric.MINUTES, now, utc)
        assertEquals(CardioBucketGranularity.DAILY, series.granularity)
        assertEquals(7, series.points.size)
        assertEquals(30.0, series.points.last().value)
    }

    @Test
    fun weeklyAggregation() {
        val sessions = listOf(
            session("a", durationSeconds = 600, endedAt = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()),
            session("b", durationSeconds = 1200, endedAt = Instant.parse("2026-09-16T10:00:00Z").toEpochMilli())
        )
        val series = CardioTrendEngine.volumeTrend(sessions, CardioAnalysisRange.WEEKS_4, CardioTrendMetric.MINUTES, now, utc)
        assertEquals(CardioBucketGranularity.WEEKLY, series.granularity)
        assertEquals(30.0, series.points.sumOf { it.value ?: 0.0 })
    }

    @Test
    fun monthlyAggregation() {
        val sessions = listOf(
            session("a", durationSeconds = 600, endedAt = Instant.parse("2026-05-10T10:00:00Z").toEpochMilli()),
            session("b", durationSeconds = 1200, endedAt = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli())
        )
        val series = CardioTrendEngine.volumeTrend(sessions, CardioAnalysisRange.MONTHS_6, CardioTrendMetric.MINUTES, now, utc)
        assertEquals(CardioBucketGranularity.MONTHLY, series.granularity)
        assertEquals(30.0, series.points.sumOf { it.value ?: 0.0 })
    }

    @Test
    fun paceTrend() {
        val run = session(
            "run",
            activity = CardioActivityType.RUNNING,
            avgPaceSecPerKm = 360,
            endedAt = Instant.parse("2026-09-18T10:00:00Z").toEpochMilli()
        )
        val series = CardioTrendEngine.activityTrend(listOf(run), CardioActivityType.RUNNING, CardioAnalysisRange.DAYS_7, CardioTrendMetric.PACE_SEC_PER_KM, now, utc)
        assertNotNull(series)
        assertEquals(360.0, series.points.mapNotNull { it.value }.single())
    }

    @Test
    fun speedTrend() {
        val ride = session(
            "ride",
            activity = CardioActivityType.CYCLING,
            avgSpeedKmh = 24.5,
            endedAt = Instant.parse("2026-09-18T10:00:00Z").toEpochMilli()
        )
        val series = CardioTrendEngine.activityTrend(listOf(ride), CardioActivityType.CYCLING, CardioAnalysisRange.DAYS_7, CardioTrendMetric.SPEED_KMH, now, utc)
        assertEquals(24.5, assertNotNull(series).points.mapNotNull { it.value }.single())
    }

    @Test
    fun heartRateTrend() {
        val run = session("run", avgHeartRate = 145, endedAt = Instant.parse("2026-09-18T10:00:00Z").toEpochMilli())
        val series = CardioTrendEngine.activityTrend(listOf(run), CardioActivityType.RUNNING, CardioAnalysisRange.DAYS_7, CardioTrendMetric.AVG_HEART_RATE, now, utc)
        assertEquals(145.0, assertNotNull(series).points.mapNotNull { it.value }.single())
    }

    @Test
    fun activitySpecificMetricFiltering() {
        val stair = session("stair", activity = CardioActivityType.STAIR_CLIMBER, distanceKm = null)
        val metrics = CardioTrendEngine.availableActivityMetrics(CardioActivityType.STAIR_CLIMBER, listOf(stair))
        assertFalse(CardioTrendMetric.DISTANCE_KM in metrics)
        assertFalse(CardioTrendMetric.SPEED_KMH in metrics)
        assertTrue(CardioTrendMetric.DURATION_MINUTES in metrics)
    }

    @Test
    fun comparableSessionSelection() {
        val previous = session(
            "previous",
            endedAt = now - 2 * DAY,
            distanceKm = 5.0,
            durationSeconds = 1800,
            avgPaceSecPerKm = 360,
            avgHeartRate = 150
        )
        val current = session(
            "current",
            endedAt = now - DAY,
            distanceKm = 5.1,
            durationSeconds = 1790,
            avgPaceSecPerKm = 350,
            avgHeartRate = 149
        )
        assertEquals("previous", CardioComparisonEngine.previousComparableSession(current, listOf(current, previous))?.id)
    }

    @Test
    fun nonComparableSessionRejected() {
        val previous = session(
            "previous",
            endedAt = now - 2 * DAY,
            distanceKm = 20.0,
            durationSeconds = 7200,
            avgPaceSecPerKm = 360
        )
        val current = session(
            "current",
            endedAt = now - DAY,
            distanceKm = 5.0,
            durationSeconds = 1800,
            avgPaceSecPerKm = 350
        )
        assertNull(CardioComparisonEngine.previousComparableSession(current, listOf(previous)))
    }

    @Test
    fun exactDistancePrFromTrueSplit() {
        val run = session("run", distanceKm = 5.0, avgPaceSecPerKm = 360)
        val laps = listOf(
            lap("run", 0, 500.0, 170.0),
            lap("run", 1, 500.0, 165.0)
        )
        val record = CardioRecordsEngine.exactDistanceRecord(
            CardioActivityType.RUNNING,
            listOf(run),
            laps,
            1_000.0
        )
        assertTrue(record.verified)
        assertEquals(335.0, record.value)
    }

    @Test
    fun exactDistancePrIsNotFabricatedFromAveragePace() {
        val run = session("run", distanceKm = 5.0, durationSeconds = 1800, avgPaceSecPerKm = 360)
        val record = CardioRecordsEngine.exactDistanceRecord(
            CardioActivityType.RUNNING,
            listOf(run),
            emptyList(),
            1_000.0
        )
        assertFalse(record.verified)
        assertNull(record.value)
    }

    @Test
    fun longestDurationRecord() {
        val records = CardioRecordsEngine.recordsForActivity(
            CardioActivityType.RUNNING,
            listOf(session("short", durationSeconds = 600), session("long", durationSeconds = 2400))
        )
        assertEquals("long", records.first { it.type == CardioRecordType.LONGEST_DURATION }.sessionId)
    }

    @Test
    fun farthestDistanceRecord() {
        val records = CardioRecordsEngine.recordsForActivity(
            CardioActivityType.RUNNING,
            listOf(session("near", distanceKm = 3.0), session("far", distanceKm = 8.0))
        )
        assertEquals("far", records.first { it.type == CardioRecordType.FARTHEST_DISTANCE }.sessionId)
    }

    @Test
    fun rowingSplitRecord() {
        val records = CardioRecordsEngine.recordsForActivity(
            CardioActivityType.ROWING,
            listOf(
                session("slow", activity = CardioActivityType.ROWING, avgSplit500mSeconds = 130),
                session("fast", activity = CardioActivityType.ROWING, avgSplit500mSeconds = 115)
            )
        )
        assertEquals("fast", records.first { it.type == CardioRecordType.BEST_AVERAGE_SPLIT }.sessionId)
    }

    @Test
    fun loadSourceSelectionPrefersMeasuredZones() {
        val detail = CardioAnalyticsEngine.loadDetail(
            session("load", durationSeconds = 1200, rpe = 9.0, zoneSeconds = mapOf(2 to 600))
        )
        assertEquals(CardioLoadSource.MEASURED_ZONES, detail.source)
        assertEquals(14.0, detail.score)
    }

    @Test
    fun partialHeartRateZoneCoverageIsExposed() {
        val detail = CardioAnalyticsEngine.loadDetail(
            session("partial", durationSeconds = 3600, zoneSeconds = mapOf(2 to 600))
        )
        assertTrue(abs(detail.zoneCoveragePercent - 16.6666667) < 0.01)
        assertEquals(3000, detail.unclassifiedSeconds)
    }

    @Test
    fun insufficientLoadBaseline() {
        val baseline = listOf(
            session("a", endedAt = now - 20 * DAY, rpe = 5.0),
            session("b", endedAt = now - 14 * DAY, rpe = 5.0)
        )
        assertEquals(CardioBaselineQuality.INSUFFICIENT, CardioAnalyticsEngine.baselineQuality(baseline))
    }

    @Test
    fun intensityDistributionKeepsUnclassifiedTime() {
        val distribution = CardioAnalyticsEngine.intensityDistribution(
            listOf(
                session("zoned", durationSeconds = 600, zoneSeconds = mapOf(2 to 300)),
                session("unknown", durationSeconds = 600)
            )
        )
        assertEquals(300, distribution.zoneSeconds[2])
        assertEquals(900, distribution.unclassifiedSeconds)
        assertEquals(1, distribution.sessionsWithMeasuredZones)
        assertEquals(1, distribution.sessionsWithoutMeasuredZones)
    }

    @Test
    fun unitConversionDistance() {
        assertTrue(abs(CardioUnits.kmToMiles(5.0) - 3.10685596) < 0.0001)
        assertTrue(abs(CardioUnits.milesToKm(1.0) - 1.609344) < 0.000001)
    }

    @Test
    fun minPerKmToMinPerMile() {
        val seconds = CardioUnits.secondsPerKmToSecondsPerMile(360.0)
        assertTrue(abs(seconds - 579.36384) < 0.001)
    }

    @Test
    fun kmhToMph() {
        assertTrue(abs(CardioUnits.kmhToMph(16.09344) - 10.0) < 0.0001)
    }

    @Test
    fun emptyDatasets() {
        val series = CardioTrendEngine.volumeTrend(emptyList(), CardioAnalysisRange.DAYS_7, CardioTrendMetric.MINUTES, now, utc)
        assertEquals(7, series.points.size)
        assertTrue(series.points.all { it.value == 0.0 })
        assertNull(
            CardioTrendEngine.activityTrend(
                emptyList(),
                CardioActivityType.RUNNING,
                CardioAnalysisRange.DAYS_7,
                CardioTrendMetric.PACE_SEC_PER_KM,
                now,
                utc
            )
        )
    }

    @Test
    fun singleSessionDataset() {
        val only = session("one", rpe = 5.0, endedAt = now - DAY)
        val load = CardioAnalyticsEngine.loadAnalytics(listOf(only), now)
        assertEquals(1, load.scoredSessions)
        assertEquals(CardioBaselineQuality.INSUFFICIENT, load.baselineQuality)
        assertNull(load.loadRatio)
    }

    @Test
    fun paginationAndFilterBehaviour() {
        val sessions = (0 until 65).map { index ->
            session(
                id = "s" + index,
                activity = if (index % 2 == 0) CardioActivityType.RUNNING else CardioActivityType.CYCLING,
                notes = if (index == 10) "rain tempo" else "",
                endedAt = now - index * 60_000L
            )
        }
        val page = CardioHistoryEngine.filterAndPage(
            sessions,
            CardioHistoryFilter(activities = setOf(CardioActivityType.RUNNING)),
            page = 1,
            pageSize = 10,
            nowEpochMs = now,
            zoneId = utc
        )
        assertEquals(10, page.items.size)
        assertEquals(33, page.totalItems)
        assertTrue(page.hasMore)

        val search = CardioHistoryEngine.filterAndPage(
            sessions,
            CardioHistoryFilter(query = "rain"),
            page = 0,
            pageSize = 30,
            nowEpochMs = now,
            zoneId = utc
        )
        assertEquals(listOf("s10"), search.items.map { it.id })
    }

    private fun session(
        id: String,
        activity: CardioActivityType = CardioActivityType.RUNNING,
        startedAt: Long = now - 3_600_000L,
        endedAt: Long = now,
        durationSeconds: Int = 1800,
        distanceKm: Double? = 5.0,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        avgPaceSecPerKm: Int? = null,
        avgSpeedKmh: Double? = null,
        elevationGainM: Double? = null,
        cadence: Int? = null,
        rpe: Double? = null,
        notes: String = "",
        source: String = "manual",
        workoutType: CardioWorkoutType = CardioWorkoutType.FREE,
        zoneSeconds: Map<Int, Int> = emptyMap(),
        avgSplit500mSeconds: Int? = null,
        avgPace100mSeconds: Int? = null
    ) = CardioSession(
        id = id,
        activity = activity,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        avgPaceSecPerKm = avgPaceSecPerKm,
        avgSpeedKmh = avgSpeedKmh,
        elevationGainM = elevationGainM,
        cadence = cadence,
        rpe = rpe,
        notes = notes,
        source = source,
        workoutType = workoutType,
        zoneSeconds = zoneSeconds,
        avgSplit500mSeconds = avgSplit500mSeconds,
        avgPace100mSeconds = avgPace100mSeconds
    )

    private fun lap(sessionId: String, index: Int, distance: Double, duration: Double) = CardioLap(
        sessionId = sessionId,
        index = index,
        startedAt = now + index * 1000L,
        endedAt = now + (index + 1) * 1000L,
        durationSeconds = duration,
        distanceMeters = distance,
        source = CardioLapProvenance.GPS_ROUTE,
        exactDistance = true
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}
