package com.projectsuperhuman.next.environment

data class EnvironmentalObservation(
    val retrievedAtEpochMs: Long,
    val sourceScope: String,
    val measurements: List<EnvironmentalMeasurement>,
    val condition: EnvironmentalConditionObservation?,
    val sunCycle: EnvironmentalSunCycle?,
    val freshness: EnvironmentalFreshness
)
