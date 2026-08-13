package com.projectsuperhuman.next.environment

data class EnvironmentalObservation(
    val retrievedAtEpochMs: Long,
    val locationContextId: String,
    val measurements: List<EnvironmentalMeasurement>,
    val condition: EnvironmentalConditionObservation?,
    val sunCycle: EnvironmentalSunCycle?,
    val freshness: EnvironmentalFreshness
) {
    init { require(locationContextId.isNotBlank()) }
    fun measurement(metricId: String) = measurements.firstOrNull { it.metricId == metricId }
}
