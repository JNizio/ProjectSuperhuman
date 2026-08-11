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
            val converted = normaliseKnownUnit(raw.value, raw.unit, definition)
            if (definition != null && converted == null) {
                issues += IngestionIssue(
                    metric,
                    "Unit '${raw.unit}' is incompatible with canonical unit '${definition.canonicalUnit}'",
                    true
                )
                return@forEach
            }

            val canonicalValue = converted?.first ?: raw.value
            val canonicalUnit = converted?.second ?: raw.unit.trim()
            if (definition != null) {
                if (definition.minAccepted != null && canonicalValue < definition.minAccepted) {
                    issues += IngestionIssue(metric, "Value is below accepted storage bounds", true)
                    return@forEach
                }
                if (definition.maxAccepted != null && canonicalValue > definition.maxAccepted) {
                    issues += IngestionIssue(metric, "Value is above accepted storage bounds", true)
                    return@forEach
                }
            }

            val canonicalMetric = definition?.id ?: metric
            val metadata = raw.metadata.toMutableMap().apply {
                put("ingestionPipeline", pipelineVersion)
                if (canonicalMetric != raw.metric) put("originalMetric", raw.metric)
                if (canonicalUnit != raw.unit || canonicalValue != raw.value) put("originalUnit", raw.unit)
                if (canonicalValue != raw.value) put("originalValue", raw.value.toString())
                if (definition == null && !containsKey("metricRegistryStatus")) {
                    put("metricRegistryStatus", "unregistered")
                }
            }

            normalised += raw.copy(
                metric = canonicalMetric,
                value = canonicalValue,
                unit = canonicalUnit,
                metadata = metadata
            )
        }

        val seen = mutableSetOf<String>()
        val unique = normalised.filter { value -> seen.add(dedupeKey(value)) }
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

    /**
     * Known metrics may be converted into their canonical unit. We never merely relabel
     * a numeric value. Unknown metrics (especially new Clinical markers) are preserved
     * exactly as supplied and can be registered later.
     */
    private fun normaliseKnownUnit(
        value: Double,
        suppliedUnit: String,
        definition: MetricDefinition?
    ): Pair<Double, String>? {
        if (definition == null) return value to suppliedUnit.trim()

        val canonical = definition.canonicalUnit
        val unit = suppliedUnit.trim()
        if (unit.equals(canonical, ignoreCase = true)) return value to canonical

        val lower = unit.lowercase()
        return when (canonical) {
            "min" -> when (lower) {
                "minute", "minutes", "mins" -> value to canonical
                "h", "hr", "hrs", "hour", "hours" -> value * 60.0 to canonical
                "s", "sec", "secs", "second", "seconds" -> value / 60.0 to canonical
                else -> null
            }
            "%" -> when (lower) {
                "percent", "percentage", "pct" -> value to canonical
                else -> null
            }
            "g" -> when (lower) {
                "gram", "grams" -> value to canonical
                "kg", "kilogram", "kilograms" -> value * 1000.0 to canonical
                else -> null
            }
            "count" -> when (lower) {
                "counts", "times", "events" -> value to canonical
                else -> null
            }
            "score" -> when (lower) {
                "points", "point" -> value to canonical
                else -> null
            }
            else -> null
        }
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
