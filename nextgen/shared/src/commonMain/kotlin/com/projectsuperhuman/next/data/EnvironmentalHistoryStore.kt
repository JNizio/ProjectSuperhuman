package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.DataIngestionPipeline
import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.IngestionIssue
import com.projectsuperhuman.next.core.MetricRegistry

/**
 * Narrow persistence DTO used only at the Environmental -> Data Vault boundary.
 * Agent 5's canonical Environmental models should map into this type rather than
 * making this type part of the Environmental domain model.
 */
data class EnvironmentalEvidenceInput(
    val metric: String,
    val value: Double,
    val unit: String,
    val observedAtEpochMs: Long,
    val provider: String,
    val fetchedAtEpochMs: Long,
    val freshUntilEpochMs: Long,
    val providerObservationId: String? = null,
    val evidenceKind: String = "observation",
    val locationContext: String? = null,
    val locationGranularity: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class EnvironmentalPersistenceResult(
    val stored: Int,
    val sampledOut: Int,
    val rejected: Int,
    val issues: List<IngestionIssue>
)

/**
 * Converts refresh-oriented Environmental readings into bounded historical evidence.
 *
 * Storage policy: at most one durable sample per provider + coarse location + metric +
 * sampling bucket. The default bucket is one hour, which is intentionally much coarser
 * than a UI/network refresh cadence. Observation time, not fetch time, selects the bucket.
 */
class EnvironmentalHistoryStore(
    private val gateway: DataVaultGateway,
    registry: MetricRegistry = CoreMetricRegistry,
    private val sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS
) {
    private val module = gateway.module(HealthDomain.ENVIRONMENT)
    private val ingestion = DataIngestionPipeline(
        gateway = gateway,
        registry = registry,
        pipelineVersion = PIPELINE_VERSION
    )

    init {
        require(sampleIntervalMs >= MIN_SAMPLE_INTERVAL_MS) {
            "Environmental history sampling must be at least 15 minutes"
        }
    }

    suspend fun persist(inputs: List<EnvironmentalEvidenceInput>): EnvironmentalPersistenceResult {
        if (inputs.isEmpty()) return EnvironmentalPersistenceResult(0, 0, 0, emptyList())

        val issues = mutableListOf<IngestionIssue>()
        val candidates = mutableListOf<Candidate>()
        var rejected = 0

        inputs.forEach { input ->
            val candidate = candidateFor(input, issues)
            if (candidate == null) rejected++ else candidates += candidate
        }

        // If one fetch contains multiple readings for the same historical bucket, keep the
        // newest provider observation before touching storage. This is deterministic and avoids
        // transient refresh values becoming multiple permanent rows.
        val representatives = candidates
            .groupBy { it.sampleKey }
            .values
            .map { group ->
                group.maxWith(
                    compareBy<Candidate> { it.input.observedAtEpochMs }
                        .thenBy { it.input.fetchedAtEpochMs }
                )
            }

        var sampledOut = candidates.size - representatives.size
        val valuesToStore = mutableListOf<HealthValue>()

        representatives.forEach { candidate ->
            if (alreadyStored(candidate)) {
                sampledOut++
            } else {
                valuesToStore += candidate.toHealthValue()
            }
        }

        val ingestionResult = ingestion.ingestValues(valuesToStore)
        return EnvironmentalPersistenceResult(
            stored = ingestionResult.accepted,
            sampledOut = sampledOut + ingestionResult.deduplicated,
            rejected = rejected + ingestionResult.rejected,
            issues = issues + ingestionResult.issues
        )
    }

    private suspend fun alreadyStored(candidate: Candidate): Boolean {
        val bucketEnd = candidate.bucketStartEpochMs + sampleIntervalMs - 1L
        return module.between(
            metric = candidate.metric,
            fromEpochMs = candidate.bucketStartEpochMs,
            toEpochMs = bucketEnd
        ).any { existing ->
            existing.source.equals(candidate.provider, ignoreCase = true) &&
                (
                    existing.metadata[SAMPLE_KEY_METADATA] == candidate.sourceRecordId ||
                        existing.metadata["sourceRecordId"] == candidate.sourceRecordId ||
                        sameLegacySamplingScope(existing, candidate)
                )
        }
    }

    /** Compatibility fallback for rows written by an early integration without sample-key metadata. */
    private fun sameLegacySamplingScope(existing: HealthValue, candidate: Candidate): Boolean {
        val existingGranularity = existing.metadata[LOCATION_GRANULARITY_METADATA]
        val existingContext = existing.metadata[LOCATION_CONTEXT_METADATA]
        return existing.timestampEpochMs.floorDiv(sampleIntervalMs) * sampleIntervalMs == candidate.bucketStartEpochMs &&
            (existingGranularity ?: "unknown") == candidate.locationGranularity &&
            (existingContext ?: "") == (candidate.locationContext ?: "")
    }

    private fun candidateFor(
        raw: EnvironmentalEvidenceInput,
        issues: MutableList<IngestionIssue>
    ): Candidate? {
        val metric = raw.metric.trim()
        val provider = raw.provider.trim()
        val unit = raw.unit.trim()

        fun reject(message: String): Candidate? {
            issues += IngestionIssue(metric.ifBlank { null }, message, true)
            return null
        }

        if (metric.isBlank()) return reject("Environmental metric ID is blank")
        if (provider.isBlank()) return reject("Environmental provider/source is blank")
        if (unit.isBlank()) return reject("Environmental unit is blank")
        if (!raw.value.isFinite()) return reject("Environmental value is not finite")
        if (raw.observedAtEpochMs <= 0L) return reject("Environmental observation timestamp is invalid")
        if (raw.fetchedAtEpochMs <= 0L) return reject("Environmental fetch timestamp is invalid")
        if (raw.freshUntilEpochMs <= 0L) return reject("Environmental freshness timestamp is invalid")
        if (raw.observedAtEpochMs > raw.fetchedAtEpochMs + MAX_OBSERVATION_FUTURE_SKEW_MS) {
            return reject("Future forecast values cannot be persisted as historical environmental evidence")
        }
        val evidenceKind = raw.evidenceKind.trim().lowercase().replace('-', '_')
        if (evidenceKind !in ALLOWED_EVIDENCE_KINDS) {
            return reject("Environmental evidence kind '${raw.evidenceKind}' is not historical evidence")
        }

        val location = coarseLocation(raw.locationContext, raw.locationGranularity)
        val bucketStart = raw.observedAtEpochMs.floorDiv(sampleIntervalMs) * sampleIntervalMs
        val scopeHash = stableScopeHash("${location.granularity}|${location.context.orEmpty()}")
        val sourceRecordId = "$SAMPLE_KEY_PREFIX|$sampleIntervalMs|$metric|${location.granularity}|$scopeHash|$bucketStart"
        val sampleKey = "${provider.lowercase()}|$sourceRecordId"

        return Candidate(
            input = raw,
            metric = metric,
            unit = unit,
            provider = provider,
            locationContext = location.context,
            locationGranularity = location.granularity,
            bucketStartEpochMs = bucketStart,
            sourceRecordId = sourceRecordId,
            sampleKey = sampleKey
        )
    }

    private fun Candidate.toHealthValue(): HealthValue {
        val safeMetadata = privacySafeMetadata(input.metadata).toMutableMap().apply {
            put("sourceRecordId", sourceRecordId)
            put(SAMPLE_KEY_METADATA, sourceRecordId)
            put("environment.samplingPolicy", SAMPLING_POLICY)
            put("environment.sampleIntervalMs", sampleIntervalMs.toString())
            put("environment.sampleBucketEpochMs", bucketStartEpochMs.toString())
            put("environment.fetchedAtEpochMs", input.fetchedAtEpochMs.toString())
            put("environment.freshUntilEpochMs", input.freshUntilEpochMs.toString())
            put("environment.observationAgeMs", (input.fetchedAtEpochMs - input.observedAtEpochMs).coerceAtLeast(0L).toString())
            put("environment.evidenceKind", input.evidenceKind.trim().lowercase().replace('-', '_'))
            put("environment.isFreshAtFetch", (input.fetchedAtEpochMs <= input.freshUntilEpochMs).toString())
            input.providerObservationId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                put("environment.providerObservationId", it.take(MAX_PROVIDER_OBSERVATION_ID_LENGTH))
            }
            put(LOCATION_GRANULARITY_METADATA, locationGranularity)
            locationContext?.let { put(LOCATION_CONTEXT_METADATA, it) }
        }

        return HealthValue(
            domain = HealthDomain.ENVIRONMENT,
            metric = metric,
            value = input.value,
            unit = unit,
            timestampEpochMs = input.observedAtEpochMs,
            source = provider,
            metadata = safeMetadata
        )
    }

    private fun coarseLocation(context: String?, granularity: String?): CoarseLocation {
        val normalizedGranularity = granularity
            ?.trim()
            ?.lowercase()
            ?.replace('-', '_')
            ?.takeIf { it.isNotEmpty() }
            ?: "unknown"

        if (normalizedGranularity !in ALLOWED_LOCATION_GRANULARITIES) {
            return CoarseLocation(context = null, granularity = "redacted")
        }

        val safeContext = if (normalizedGranularity == "unknown") {
            null
        } else {
            context
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(MAX_LOCATION_CONTEXT_LENGTH)
        }

        return CoarseLocation(context = safeContext, granularity = normalizedGranularity)
    }

    /**
     * Extra provider metadata is allowed for provenance, but precise-location fields are never
     * copied into the vault. Canonical Environmental code can keep coordinates ephemerally for
     * networking; this history boundary intentionally cannot persist them.
     */
    private fun privacySafeMetadata(metadata: Map<String, String>): Map<String, String> =
        metadata.filterKeys { key ->
            val lower = key.trim().lowercase()
            val compact = lower.replace("_", "").replace("-", "").replace(".", "")
            lower !in BLOCKED_LOCATION_METADATA_KEYS &&
                !compact.endsWith("lat") &&
                !compact.endsWith("lon") &&
                !compact.endsWith("lng") &&
                !BLOCKED_LOCATION_METADATA_FRAGMENTS.any { fragment -> compact.contains(fragment) }
        }

    private fun stableScopeHash(value: String): String {
        var hash = FNV_OFFSET_BASIS
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= FNV_PRIME
        }
        return hash.toULong().toString(16)
    }

    private data class CoarseLocation(val context: String?, val granularity: String)

    private data class Candidate(
        val input: EnvironmentalEvidenceInput,
        val metric: String,
        val unit: String,
        val provider: String,
        val locationContext: String?,
        val locationGranularity: String,
        val bucketStartEpochMs: Long,
        val sourceRecordId: String,
        val sampleKey: String
    )

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS = 60L * 60L * 1000L
        const val MIN_SAMPLE_INTERVAL_MS = 15L * 60L * 1000L

        private const val PIPELINE_VERSION = "environment-history-v1"
        private const val SAMPLE_KEY_PREFIX = "env-sampled-v1"
        private const val SAMPLING_POLICY = "one-per-provider-coarse-location-metric-bucket-v1"
        private const val SAMPLE_KEY_METADATA = "environment.sampleKey"
        private const val LOCATION_CONTEXT_METADATA = "environment.locationContext"
        private const val LOCATION_GRANULARITY_METADATA = "environment.locationGranularity"
        private const val MAX_LOCATION_CONTEXT_LENGTH = 120
        private const val MAX_PROVIDER_OBSERVATION_ID_LENGTH = 160
        private const val MAX_OBSERVATION_FUTURE_SKEW_MS = 5L * 60L * 1000L
        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L

        private val ALLOWED_EVIDENCE_KINDS = setOf("observation", "estimate", "derived")

        private val ALLOWED_LOCATION_GRANULARITIES = setOf(
            "unknown",
            "country",
            "region",
            "city",
            "coarse_grid",
            "weather_zone"
        )

        private val BLOCKED_LOCATION_METADATA_KEYS = setOf(
            "lat", "latitude", "lon", "lng", "longitude", "gps", "coordinates",
            "address", "street", "postcode", "postal_code", "zipcode", "zip_code"
        )

        private val BLOCKED_LOCATION_METADATA_FRAGMENTS = setOf(
            "latitude", "longitude", "coordinates", "address", "postcode",
            "postalcode", "zipcode", "gpslocation"
        )
    }
}
