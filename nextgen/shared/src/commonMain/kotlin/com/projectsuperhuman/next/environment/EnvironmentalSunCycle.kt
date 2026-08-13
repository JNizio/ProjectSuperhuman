package com.projectsuperhuman.next.environment

data class EnvironmentalSunCycle(
    val sunriseEpochMs: Long,
    val sunsetEpochMs: Long,
    val daylightDurationSeconds: Double,
    val provenance: EnvironmentalProvenance
)
