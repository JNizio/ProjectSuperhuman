package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.DataIngestionPipeline
import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.IngestionResult
import com.projectsuperhuman.next.core.MetricRegistry
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.emotional.DefaultEmotionalHealthValueMapper
import com.projectsuperhuman.next.emotional.EmotionalEntry
import com.projectsuperhuman.next.emotional.EmotionalHealthValueMapper
import com.projectsuperhuman.next.emotional.EmotionalMetricId
import com.projectsuperhuman.next.emotional.EmotionalMetricRegistryOverlay

/**
 * Canonical Emotional -> Data Vault boundary.
 *
 * Emotional domain semantics live in `next.emotional`; this adapter only persists the canonical
 * HealthValue rows produced by that domain and exposes domain-scoped history reads. It intentionally
 * does not accept arbitrary scale definitions, so stored Emotional values cannot drift away from
 * the canonical -1..+1 semantics.
 */
class EmotionalDataVaultAdapter(
    private val port: ModuleDataPort,
    private val ingestValues: suspend (List<HealthValue>) -> IngestionResult,
    private val mapper: EmotionalHealthValueMapper = DefaultEmotionalHealthValueMapper()
) {
    init {
        require(port.domain == HealthDomain.EMOTIONAL) {
            "EmotionalDataVaultAdapter requires an EMOTIONAL ModuleDataPort"
        }
    }

    constructor(
        gateway: DataVaultGateway,
        mapper: EmotionalHealthValueMapper = DefaultEmotionalHealthValueMapper(),
        fallbackRegistry: MetricRegistry = CoreMetricRegistry
    ) : this(
        port = gateway.module(HealthDomain.EMOTIONAL),
        ingestValues = { values ->
            DataIngestionPipeline(
                gateway = gateway,
                registry = EmotionalMetricRegistryOverlay(fallbackRegistry),
                pipelineVersion = "emotional-ingestion-v1"
            ).ingestValues(values)
        },
        mapper = mapper
    )

    /** Validates/maps the canonical domain entry, then persists all observations atomically as one batch. */
    suspend fun save(entry: EmotionalEntry): IngestionResult = ingestValues(mapper.map(entry))

    suspend fun latest(metricId: EmotionalMetricId): HealthValue? = port.latest(metricId.value)

    suspend fun between(
        metricId: EmotionalMetricId,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = port.between(metricId.value, fromEpochMs, toEpochMs)

    suspend fun history(
        metricId: EmotionalMetricId? = null,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = port.page(metricId?.value, limit, offset)

    suspend fun count(): Long = port.count()
}
