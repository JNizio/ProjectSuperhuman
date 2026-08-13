package com.projectsuperhuman.next.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentalHistoryPrivacyTest {
    @Test
    fun keepsFreshnessAndCoarseContextButDropsPreciseMetadata() = runBlocking {
        val gateway = RecordingEnvironmentalGateway()
        val store = EnvironmentalHistoryStore(gateway)
        val at = HOUR + 15 * MINUTE
        val input = sample(at).copy(
            providerObservationId = "provider-observation-123",
            metadata = mapOf(
                "stationId" to "station-7",
                "latitude" to "sensitive",
                "device_lon" to "sensitive",
                "streetAddress" to "sensitive"
            )
        )

        store.persist(listOf(input))
        val stored = gateway.rows.single()

        assertEquals(at, stored.timestampEpochMs)
        assertEquals(input.fetchedAtEpochMs.toString(), stored.metadata["environment.fetchedAtEpochMs"])
        assertEquals(input.freshUntilEpochMs.toString(), stored.metadata["environment.freshUntilEpochMs"])
        assertEquals("true", stored.metadata["environment.isFreshAtFetch"])
        assertEquals("provider-observation-123", stored.metadata["environment.providerObservationId"])
        assertEquals("city", stored.metadata["environment.locationGranularity"])
        assertEquals("London", stored.metadata["environment.locationContext"])
        assertEquals("station-7", stored.metadata["stationId"])
        assertNull(stored.metadata["latitude"])
        assertNull(stored.metadata["device_lon"])
        assertNull(stored.metadata["streetAddress"])
        assertTrue(stored.metadata.getValue("sourceRecordId").startsWith("env-sampled-v1|"))
    }

    @Test
    fun preciseLocationIsRedactedAndForecastIsRejected() = runBlocking {
        val gateway = RecordingEnvironmentalGateway()
        val store = EnvironmentalHistoryStore(gateway)

        store.persist(listOf(sample(HOUR + 20 * MINUTE).copy(
            locationContext = "precise-location",
            locationGranularity = "gps"
        )))
        val redacted = gateway.rows.single()
        assertEquals("redacted", redacted.metadata["environment.locationGranularity"])
        assertNull(redacted.metadata["environment.locationContext"])

        val forecast = sample(HOUR + 90 * MINUTE).copy(
            fetchedAtEpochMs = HOUR + 60 * MINUTE,
            evidenceKind = "forecast"
        )
        val result = store.persist(listOf(forecast))
        assertEquals(1, result.rejected)
        assertEquals(1, gateway.rows.size)
    }

    private fun sample(at: Long) = EnvironmentalEvidenceInput(
        metric = "environment_temperature",
        value = 20.0,
        unit = "degC",
        observedAtEpochMs = at,
        provider = "provider-a",
        fetchedAtEpochMs = at + MINUTE,
        freshUntilEpochMs = at + 90 * MINUTE,
        locationContext = "London",
        locationGranularity = "city"
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 1_720_000_800_000L
    }
}
