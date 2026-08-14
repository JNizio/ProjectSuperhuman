package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeVitalsTileTest {
    @Test
    fun mapsLatestCanonicalHeartRateAndBloodPressure() {
        val heartRows = listOf(
            value(HealthDomain.EXERCISE, "heart_rate_bpm", 65.0, 100L),
            value(HealthDomain.EXERCISE, "heart_rate_bpm", 72.0, 200L)
        )
        val bloodPressureRows = listOf(
            value(HealthDomain.BLOOD_PRESSURE, "blood_pressure_systolic_mmhg", 118.0, 300L),
            value(HealthDomain.BLOOD_PRESSURE, "blood_pressure_diastolic_mmhg", 74.0, 300L)
        )

        val snapshot = selectHomeVitalsSnapshot(heartRows, bloodPressureRows, emptyList())

        assertEquals(72, snapshot.heartRateBpm)
        assertEquals(118, snapshot.systolicMmhg)
        assertEquals(74, snapshot.diastolicMmhg)
        assertNull(snapshot.bodyTemperatureCelsius)
    }

    @Test
    fun missingMeasurementsRemainMissing() {
        assertEquals(HomeVitalsSnapshot(), selectHomeVitalsSnapshot(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun doesNotCombineBloodPressureValuesFromDifferentReadings() {
        val rows = listOf(
            value(HealthDomain.BLOOD_PRESSURE, "blood_pressure_systolic_mmhg", 118.0, 300L),
            value(HealthDomain.BLOOD_PRESSURE, "blood_pressure_diastolic_mmhg", 74.0, 600_301L)
        )

        val snapshot = selectHomeVitalsSnapshot(emptyList(), rows, emptyList())

        assertNull(snapshot.systolicMmhg)
        assertNull(snapshot.diastolicMmhg)
    }

    @Test
    fun freshestHeartRateWinsAcrossCanonicalRawAndAverageMetrics() {
        val rows = listOf(
            value(HealthDomain.EXERCISE, "heart_rate_bpm", 65.0, 100L),
            value(HealthDomain.EXERCISE, "heart_rate_avg_bpm", 70.0, 200L)
        )

        assertEquals(70, selectHomeVitalsSnapshot(rows, emptyList(), emptyList()).heartRateBpm)
    }

    @Test
    fun freshnessUsesRelativeMinutesForRecentReadings() {
        assertEquals("Heart rate · 2m ago", vitalsFreshness("Heart rate", timestampMs = 1_000L, nowMs = 121_000L))
    }

    private fun value(domain: HealthDomain, metric: String, value: Double, timestamp: Long) = HealthValue(
        domain = domain,
        metric = metric,
        value = value,
        unit = "",
        timestampEpochMs = timestamp,
        source = "test"
    )
}
