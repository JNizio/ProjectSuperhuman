package com.projectsuperhuman.next.environment

enum class EnvironmentalFetchSource { NETWORK, CACHE_FRESH, CACHE_STALE }

sealed interface EnvironmentalFetchResult {
    data class Success(
        val observation: EnvironmentalObservation,
        val source: EnvironmentalFetchSource,
        val warnings: Set<EnvironmentalAvailabilityWarning> = emptySet(),
        val refreshError: EnvironmentalProviderError? = null
    ) : EnvironmentalFetchResult
    data class Failure(val error: EnvironmentalProviderError) : EnvironmentalFetchResult
}

interface EnvironmentalRepository {
    suspend fun current(
        coordinates: EnvironmentalCoordinates,
        contextId: String,
        nowEpochMs: Long,
        forceRefresh: Boolean = false
    ): EnvironmentalFetchResult
}
