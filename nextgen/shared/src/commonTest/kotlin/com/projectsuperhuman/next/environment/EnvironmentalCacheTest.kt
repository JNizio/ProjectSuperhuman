package com.projectsuperhuman.next.environment

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EnvironmentalCacheTest {
    @Test
    fun stale_cache_is_used_only_within_fallback_window() = runTest {
        val provider = FakeProvider()
        val repository = CachingEnvironmentalRepository(provider, policy = EnvironmentalFreshnessPolicy(100L, 500L))
        val point = EnvironmentalCoordinates(10.0, 20.0)
        assertEquals(EnvironmentalFetchSource.NETWORK, assertIs<EnvironmentalFetchResult.Success>(repository.current(point, "test-area", 1_000L)).source)
        assertEquals(EnvironmentalFetchSource.CACHE_FRESH, assertIs<EnvironmentalFetchResult.Success>(repository.current(point, "test-area", 1_050L)).source)
        provider.fail = true
        val stale = assertIs<EnvironmentalFetchResult.Success>(repository.current(point, "test-area", 1_200L))
        assertEquals(EnvironmentalFetchSource.CACHE_STALE, stale.source)
        assertEquals(EnvironmentalFreshnessState.STALE, stale.observation.freshness.state)
        assertIs<EnvironmentalFetchResult.Failure>(repository.current(point, "test-area", 1_600L))
    }

    private class FakeProvider : EnvironmentalProvider {
        override val providerId = "fake"
        var fail = false
        override suspend fun current(coordinates: EnvironmentalCoordinates, contextId: String, retrievedAtEpochMs: Long): EnvironmentalProviderResult {
            if (fail) return EnvironmentalProviderResult.Failure(EnvironmentalProviderError(EnvironmentalProviderErrorKind.NETWORK, true))
            return EnvironmentalProviderResult.Success(EnvironmentalObservation(
                retrievedAtEpochMs, contextId,
                listOf(EnvironmentalMeasurement(EnvironmentalMetricIds.TEMPERATURE_C, 20.0, EnvironmentalUnit.CELSIUS, retrievedAtEpochMs, EnvironmentalProvenance("fake", "weather"))),
                null, null,
                EnvironmentalFreshness(EnvironmentalFreshnessState.FRESH, retrievedAtEpochMs, 0L, 100L)
            ))
        }
    }
}
