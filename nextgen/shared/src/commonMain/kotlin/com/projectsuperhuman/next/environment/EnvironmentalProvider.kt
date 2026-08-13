package com.projectsuperhuman.next.environment

interface EnvironmentalProvider {
    val providerId: String
    suspend fun current(
        coordinates: EnvironmentalCoordinates,
        contextId: String,
        retrievedAtEpochMs: Long
    ): EnvironmentalProviderResult
}

sealed interface EnvironmentalProviderResult {
    data class Success(
        val observation: EnvironmentalObservation,
        val warnings: Set<EnvironmentalAvailabilityWarning> = emptySet()
    ) : EnvironmentalProviderResult
    data class Failure(val error: EnvironmentalProviderError) : EnvironmentalProviderResult
}

enum class EnvironmentalAvailabilityWarning { AIR_QUALITY_UNAVAILABLE, AIR_QUALITY_INCOMPLETE }
