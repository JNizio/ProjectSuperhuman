package com.projectsuperhuman.next.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentalHistorySamplingTest {
    @Test
    fun repeatedRefreshInSameHourIsSampledOut() = runBlocking {
        val gateway = RecordingEnvironmentalGateway()
        val store = EnvironmentalHistoryStore(gateway)
        val first = sample(HOUR + 5 * MINUTE, 20.0)

        assertEquals(1, store.persist(listOf(first)).stored)
        val result = store.persist(listOf(sample(HOUR + 40 * MINUTE, 21.0)))

        assertEquals(0, result.stored)
        assertEquals(1, result.sampledOut)
        assertEquals(1, gateway.rows.size)
        assertEquals(20.0, gateway.rows.single().value)
        assertEquals(first.observedAtEpochMs, gateway.rows.single().timestampEpochMs)
    }

    @Test
    fun batchKeepsNewestPerBucketButKeepsOtherHoursAndProviders() = runBlocking {
        val gateway = RecordingEnvironmentalGateway()
        val store = EnvironmentalHistoryStore(gateway)

        val result = store.persist(listOf(
            sample(HOUR + 4 * MINUTE, 18.0),
            sample(HOUR + 52 * MINUTE, 22.0),
            sample(HOUR + 70 * MINUTE, 19.0),
            sample(HOUR + 10 * MINUTE, 17.0, "provider-b")
        ))

        assertEquals(3, result.stored)
        assertEquals(1, result.sampledOut)
        assertTrue(gateway.rows.any { it.value == 22.0 && it.timestampEpochMs == HOUR + 52 * MINUTE })
        assertEquals(2, gateway.rows.map { it.source }.distinct().size)
    }

    @Test
    fun staleAndUnregisteredEvidenceRemainHonest() = runBlocking {
        val gateway = RecordingEnvironmentalGateway()
        val store = EnvironmentalHistoryStore(gateway)
        val input = sample(HOUR + 5 * MINUTE, 123.4).copy(
            metric = "environment_future_metric",
            unit = "provider-unit",
            fetchedAtEpochMs = HOUR + 90 * MINUTE,
            freshUntilEpochMs = HOUR + 60 * MINUTE
        )

        val result = store.persist(listOf(input))
        val stored = gateway.rows.single()

        assertEquals(1, result.stored)
        assertEquals("provider-unit", stored.unit)
        assertEquals("unregistered", stored.metadata["metricRegistryStatus"])
        assertEquals("false", stored.metadata["environment.isFreshAtFetch"])
    }

    private fun sample(at: Long, value: Double, provider: String = "provider-a") = EnvironmentalEvidenceInput(
        metric = "environment_temperature",
        value = value,
        unit = "degC",
        observedAtEpochMs = at,
        provider = provider,
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
