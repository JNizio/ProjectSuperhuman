package com.projectsuperhuman.next.environment

data class EnvironmentalProvenance(
    val providerId: String,
    val sourceId: String
)

data class EnvironmentalMeasurement(
    val metricId: String,
    val value: Double,
    val unit: EnvironmentalUnit,
    val measurementTimeEpochMs: Long,
    val provenance: EnvironmentalProvenance
) {
    init {
        require(metricId.isNotBlank())
        require(value.isFinite())
        val definition = EnvironmentalMetricCatalog.definition(metricId)
        require(definition == null || definition.unit == unit)
    }
}
