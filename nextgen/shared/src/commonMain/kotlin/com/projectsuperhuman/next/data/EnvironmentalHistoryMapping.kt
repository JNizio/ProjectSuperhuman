package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.environment.EnvironmentalObservation

/** Maps the canonical provider-independent Environmental observation into bounded Data Vault history. */
suspend fun EnvironmentalHistoryStore.persistCanonical(
    observation: EnvironmentalObservation
): EnvironmentalPersistenceResult {
    val freshUntil = observation.retrievedAtEpochMs + observation.freshness.freshForMs
    val inputs = observation.measurements.map { measurement ->
        EnvironmentalEvidenceInput(
            metric = measurement.metricId,
            value = measurement.value,
            unit = measurement.unit.symbol,
            observedAtEpochMs = measurement.measurementTimeEpochMs,
            provider = measurement.provenance.providerId,
            fetchedAtEpochMs = observation.retrievedAtEpochMs,
            freshUntilEpochMs = freshUntil,
            providerObservationId = measurement.provenance.sourceId,
            evidenceKind = "observation",
            locationContext = observation.locationContextId,
            locationGranularity = "coarse_grid"
        )
    }
    return persist(inputs)
}
