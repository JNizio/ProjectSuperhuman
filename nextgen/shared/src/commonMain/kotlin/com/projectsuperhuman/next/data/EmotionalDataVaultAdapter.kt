package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.DataIngestionPipeline
import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.IngestionResult
import com.projectsuperhuman.next.core.MetricRegistry

/**
 * Persistence-only bridge for Emotional data while the canonical Emotional models are owned
 * by Agent 1. This deliberately stays on top of the generic Data Vault primitives instead of
 * introducing an Emotional database or a competing domain model.
 *
 * Callers persist one numeric metric observation at a time. [sourceEventId] identifies a
 * higher-level check-in/event; the adapter metric-scopes it before writing `sourceRecordId`
 * so several metrics from the same check-in can coexist without violating Data Vault dedupe.
 */
class EmotionalDataVaultAdapter(
    private val gateway: DataVaultGateway,
    private val ingestion: DataIngestionPipeline = DataIngestionPipeline(gateway),
    private val registry: MetricRegistry = CoreMetricRegistry
) {
    private val port = gateway.module(HealthDomain.EMOTIONAL)

    suspend fun saveMetric(
        metricId: String,
        value: Double,
        unit: String,
        timestampEpochMs: Long,
        source: String,
        sourceEventId: String? = null,
        scaleMin: Double? = null,
        scaleMax: Double? = null,
        scaleLowMeaning: String? = null,
        scaleHighMeaning: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): IngestionResult {
        val metric = canonicalMetric(metricId)
        require(metric.isNotBlank()) { "metricId must not be blank" }
        require(unit.isNotBlank()) { "unit must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(sourceEventId == null || sourceEventId.isNotBlank()) {
            "sourceEventId must be null or non-blank"
        }
        validateScale(value, scaleMin, scaleMax, scaleLowMeaning, scaleHighMeaning)

        val persistenceMetadata = metadata.toMutableMap().apply {
            sourceEventId?.let { eventId ->
                put(EmotionalPersistenceMetadata.SOURCE_EVENT_ID, eventId)
                put(EmotionalPersistenceMetadata.SOURCE_RECORD_ID, "$eventId:$metric")
            }
            if (scaleMin != null && scaleMax != null) {
                put(EmotionalPersistenceMetadata.SCALE_MIN, scaleMin.toString())
                put(EmotionalPersistenceMetadata.SCALE_MAX, scaleMax.toString())
            }
            scaleLowMeaning?.let { put(EmotionalPersistenceMetadata.SCALE_LOW_MEANING, it) }
            scaleHighMeaning?.let { put(EmotionalPersistenceMetadata.SCALE_HIGH_MEANING, it) }
        }

        return ingestion.ingestValues(
            listOf(
                HealthValue(
                    domain = HealthDomain.EMOTIONAL,
                    metric = metric,
                    value = value,
                    unit = unit,
                    timestampEpochMs = timestampEpochMs,
                    source = source,
                    metadata = persistenceMetadata
                )
            )
        )
    }

    suspend fun latest(metricId: String): HealthValue? =
        port.latest(canonicalMetric(metricId))

    suspend fun between(
        metricId: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = port.between(canonicalMetric(metricId), fromEpochMs, toEpochMs)

    suspend fun history(
        metricId: String? = null,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = port.page(metricId?.let(::canonicalMetric), limit, offset)

    suspend fun count(): Long = port.count()

    private fun canonicalMetric(metricId: String): String =
        registry.definition(HealthDomain.EMOTIONAL, metricId)?.id ?: metricId.trim()

    private fun validateScale(
        value: Double,
        scaleMin: Double?,
        scaleMax: Double?,
        scaleLowMeaning: String?,
        scaleHighMeaning: String?
    ) {
        require((scaleMin == null) == (scaleMax == null)) {
            "scaleMin and scaleMax must either both be present or both be absent"
        }
        require((scaleLowMeaning == null && scaleHighMeaning == null) || scaleMin != null) {
            "Scale meaning labels require numeric scale bounds"
        }
        if (scaleMin != null && scaleMax != null) {
            require(scaleMin.isFinite() && scaleMax.isFinite() && scaleMin < scaleMax) {
                "Scale bounds must be finite and scaleMin must be < scaleMax"
            }
            require(value in scaleMin..scaleMax) {
                "Emotional value must fall inside its declared scale"
            }
        }
    }
}

/** Stable metadata keys persisted inside [HealthValue.metadata]. */
object EmotionalPersistenceMetadata {
    const val SOURCE_EVENT_ID = "sourceEventId"
    const val SOURCE_RECORD_ID = "sourceRecordId"
    const val SCALE_MIN = "scaleMin"
    const val SCALE_MAX = "scaleMax"
    const val SCALE_LOW_MEANING = "scaleLowMeaning"
    const val SCALE_HIGH_MEANING = "scaleHighMeaning"
}
