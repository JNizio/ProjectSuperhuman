package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioProductUiTest {
    private val now = 1_800_000_000_000L

    @Test
    fun emptyOverviewStaysExplicitlyInBaselineBuildingState() {
        val model = buildCardioProductOverviewModel(emptyList(), nowEpochMs = now)

        assertEquals("Building baseline", model.fitness.trendLabel)
        assertEquals(CardioConfidence.INSUFFICIENT, model.fitness.confidence)
        assertEquals("Building baseline", model.readiness.status)
        assertEquals(0, model.readiness.availableSignals)
        assertEquals(0, model.week.minutes)
        assertEquals(0, model.week.sessions)
        assertNull(model.week.progressFraction)
    }

    @Test
    fun unscoredSessionsDoNotInventAReadinessSignal() {
        val session = CardioSession(
            id = "unscored",
            activity = CardioActivityType.RUNNING,
            startedAt = now - 1_800_000L,
            endedAt = now - 1_000L,
            durationSeconds = 1_799,
            distanceKm = 4.0,
            avgHeartRate = 145,
            avgPaceSecPerKm = 450
        )

        val model = buildCardioProductOverviewModel(listOf(session), nowEpochMs = now)

        assertEquals(0, model.readiness.availableSignals)
        assertEquals(CardioConfidence.INSUFFICIENT, model.readiness.confidence)
    }

    @Test
    fun connectedSensorStatusIncludesSourceHeartRateAndFreshness() {
        val metrics = CardioLiveSensorMetrics(
            providerType = CardioSensorProviderType.BLE_HEART_RATE,
            connection = CardioSensorConnectionState.CONNECTED,
            sourceLabel = "Polar H10",
            currentHeartRateBpm = 142,
            currentZone = 2,
            lastSampleAgeMs = 1_500L
        )

        val status = cardioProductSensorStatus(metrics)

        assertTrue(status.contains("Polar H10"))
        assertTrue(status.contains("142 bpm"))
        assertTrue(status.contains("updated 1s ago"))
    }

    @Test
    fun noSensorStatusNeverShowsSyntheticHeartRate() {
        val status = cardioProductSensorStatus(CardioLiveSensorMetrics())

        assertEquals("No live sensor", status)
    }
}
