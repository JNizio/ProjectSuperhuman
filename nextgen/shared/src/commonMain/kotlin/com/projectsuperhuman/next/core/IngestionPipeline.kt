package com.projectsuperhuman.next.core

/** Raw-source adapter. Platform-specific decoders can implement this without exposing storage. */
fun interface InputDecoder<T> {
    fun decode(input: T): List<HealthValue>
}

data class IngestionIssue(
    val metric: String?,
    val message: String,
    val rejected: Boolean
)

data class IngestionResult(
    val accepted: Int,
    val rejected: Int,
    val deduplicated: Int,
    val issues: List<IngestionIssue>
)

/**
 * Shared source -> decode -> validate -> normalise -> deduplicate -> module port pipeline.
 * It never writes directly to SQL.
 */
class DataIngestionPipeline(
    private val gateway: DataVaultGateway,
    private val registry: MetricRegistry = CoreMetricRegistry,
    private val pipelineVersion: String = "ingestion-v1"
) {
    suspend fun <T> ingest(input: T, decoder: InputDecoder<T>): IngestionResult =
        ingestValues(decoder.decode(input))

    suspend fun ingestValues(values: List<HealthValue>): IngestionResult {
        if (values.isEmpty()) return IngestionResult(0, 0, 0, emptyList())

        val issues = mutableListOf<IngestionIssue>()
        val normalised = mutableListOf<HealthValue>()

        values.forEach { raw ->
            val metric = raw.metric.trim()
            if (metric.isEmpty()) {
                issues += IngestionIssue(null, "Metric ID is blank", true)
                return@forEach
            }
            if (!raw.value.isFinite()) {
                issues += IngestionIssue(metric, "Value is not finite", true)
                return@forEach
            }
            if (raw.timestampEpochMs <= 0L) {
                issues += IngestionIssue(metric, "Timestamp is invalid", true)
                return@forEach
            }

            val definition = registry.definition(raw.domain, metric)
            if (definition != null) {
                if (definition.minAccepted != null && raw.value < definition.minAccepted) {
                    issues += IngestionIssue(metric, "Value is below accepted storage bounds", true)
                    return@forEach
                }
                if (definition.maxAccepted != null && raw.value > definition.maxAccepted) {
                    issues += IngestionIssue(metric, "Value is above accepted storage bounds", true)
                    return@forEach
                }
            }

            val canonicalMetric = definition?.id ?: metric
            val canonicalUnit = definition?.canonicalUnit ?: raw.unit.trim()
            val metadata = raw.metadata.toMutableMap().apply {
                put("ingestionPipeline", pipelineVersion)
                if (canonicalMetric != raw.metric) put("originalMetric", raw.metric)
                if (canonicalUnit != raw.unit) put("originalUnit", raw.unit)
                if (definition == null) putIfAbsent("metricRegistryStatus", "unregistered")
            }

            normalised += raw.copy(
                metric = canonicalMetric,
                unit = canonicalUnit,
                metadata = metadata
            )
        }

        // In-batch deduplication. Persistent source-record deduplication is still enforced
        // by the Data Vault's source/sourceRecordId unique index.
        val seen = mutableSetOf<String>()
        val unique = normalised.filter { value ->
            seen.add(dedupeKey(value))
        }
        val deduplicated = normalised.size - unique.size

        unique.groupBy { it.domain }.forEach { (domain, domainValues) ->
            gateway.module(domain).save(domainValues)
        }

        return IngestionResult(
            accepted = unique.size,
            rejected = values.size - normalised.size,
            deduplicated = deduplicated,
            issues = issues
        )
    }

    private fun dedupeKey(value: HealthValue): String {
        val sourceRecordId = value.metadata["sourceRecordId"]
        return if (!sourceRecordId.isNullOrBlank()) {
            "${value.source}|$sourceRecordId"
        } else {
            "${value.source}|${value.domain}|${value.metric}|${value.timestampEpochMs}|${value.value}|${value.unit}"
        }
    }
}
