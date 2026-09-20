package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.util.Locale

internal const val CARDIO_SOURCE_POLICY_VERSION = "cardio-source-policy-v1"

internal enum class CardioAnalysisMetric {
    WORKOUT_HEART_RATE,
    LIVE_HEART_RATE,
    DISTANCE,
    CYCLING_POWER,
    HRV_RMSSD,
    PACE_OR_SPEED,
    ELEVATION
}

internal enum class CardioCapability {
    WORKOUT_SUMMARY,
    HEART_RATE_TIME_SERIES,
    ROUTE,
    PACE_SPEED_TIME_SERIES,
    ELEVATION_TIME_SERIES,
    DAILY_RESTING_HEART_RATE,
    DAILY_SLEEP,
    DAILY_HRV,
    RR_INTERVALS,
    POWER,
    RUNNING_DYNAMICS,
    MANUAL_CONTEXT
}

internal data class CardioCapabilities(
    val available: Set<CardioCapability>,
    val unavailableReasons: Map<CardioCapability, String> = emptyMap()
) {
    fun has(capability: CardioCapability): Boolean = capability in available

    fun supportsAerobicDecoupling(): Boolean =
        has(CardioCapability.HEART_RATE_TIME_SERIES) &&
            has(CardioCapability.PACE_SPEED_TIME_SERIES)

    fun supportsRmssd(): Boolean = has(CardioCapability.RR_INTERVALS)

    fun supportsPaceAtHeartRate(): Boolean =
        has(CardioCapability.HEART_RATE_TIME_SERIES) &&
            has(CardioCapability.PACE_SPEED_TIME_SERIES)

    fun supportsPowerAnalysis(): Boolean = has(CardioCapability.POWER)

    fun missing(required: Set<CardioCapability>): Set<CardioCapability> =
        required - available
}

internal object CardioCapabilityDetector {
    fun detect(
        metricIds: Set<String>,
        session: CardioSession? = null,
        hasRoute: Boolean = false,
        hasPaceSpeedTimeSeries: Boolean = false,
        hasElevationTimeSeries: Boolean = false,
        hasPower: Boolean = false,
        hasRunningDynamics: Boolean = false,
        hasManualContext: Boolean = false
    ): CardioCapabilities {
        val normalized = metricIds.map { it.lowercase(Locale.US) }.toSet()
        val available = mutableSetOf<CardioCapability>()

        if (session != null || "cardio_session" in normalized) {
            available += CardioCapability.WORKOUT_SUMMARY
        }
        if (
            "cardio_hr_sample_bpm" in normalized ||
            session?.extensions?.get("heartRateTimeline").isNullOrBlank().not()
        ) {
            available += CardioCapability.HEART_RATE_TIME_SERIES
        }
        if (hasRoute || normalized.any { it.contains("route") || it.contains("gps_point") }) {
            available += CardioCapability.ROUTE
        }
        if (
            hasPaceSpeedTimeSeries ||
            normalized.any { it.contains("speed") || it.contains("pace_sample") }
        ) {
            available += CardioCapability.PACE_SPEED_TIME_SERIES
        }
        if (
            hasElevationTimeSeries ||
            normalized.any { it.contains("elevation") || it.contains("altitude") }
        ) {
            available += CardioCapability.ELEVATION_TIME_SERIES
        }
        if ("resting_heart_rate_bpm" in normalized) {
            available += CardioCapability.DAILY_RESTING_HEART_RATE
        }
        if (normalized.any { it.startsWith("sleep_") }) {
            available += CardioCapability.DAILY_SLEEP
        }
        if ("heart_rate_variability_rmssd_ms" in normalized || "hrv_rmssd_ms" in normalized) {
            available += CardioCapability.DAILY_HRV
        }
        if ("cardio_rr_interval_ms" in normalized) {
            available += CardioCapability.RR_INTERVALS
        }
        if (hasPower || normalized.any { it.contains("power") && it.contains("w") }) {
            available += CardioCapability.POWER
        }
        if (hasRunningDynamics) {
            available += CardioCapability.RUNNING_DYNAMICS
        }
        if (hasManualContext) {
            available += CardioCapability.MANUAL_CONTEXT
        }

        val reasons = CardioCapability.entries
            .filterNot { it in available }
            .associateWith { capability ->
                when (capability) {
                    CardioCapability.HEART_RATE_TIME_SERIES -> "Requires workout HR time-series"
                    CardioCapability.RR_INTERVALS -> "Requires RR intervals"
                    CardioCapability.PACE_SPEED_TIME_SERIES -> "Requires pace/speed time-series"
                    CardioCapability.POWER -> "Requires measured power"
                    CardioCapability.ROUTE -> "Requires route/GPS data"
                    CardioCapability.ELEVATION_TIME_SERIES -> "Requires elevation time-series"
                    CardioCapability.DAILY_RESTING_HEART_RATE -> "Requires daily resting heart rate"
                    CardioCapability.DAILY_SLEEP -> "Requires sleep data"
                    CardioCapability.DAILY_HRV -> "Requires daily HRV"
                    CardioCapability.RUNNING_DYNAMICS -> "Requires running-dynamics sensor data"
                    CardioCapability.MANUAL_CONTEXT -> "Requires manual context"
                    CardioCapability.WORKOUT_SUMMARY -> "Requires a workout summary"
                }
            }

        return CardioCapabilities(available, reasons)
    }
}

internal object CardioProvenanceCodec {
    fun toMetadata(
        provenance: CardioObservationProvenance,
        prefix: String = "provenance"
    ): Map<String, String> = buildMap {
        put(prefix + ".sourceKind", provenance.sourceKind.name)
        put(prefix + ".sourceName", provenance.sourceName)
        provenance.deviceId?.takeIf { it.isNotBlank() }?.let { put(prefix + ".deviceId", it) }
        provenance.deviceName?.takeIf { it.isNotBlank() }?.let { put(prefix + ".deviceName", it) }
        provenance.providerPackage?.takeIf { it.isNotBlank() }?.let { put(prefix + ".providerPackage", it) }
        provenance.externalRecordId?.takeIf { it.isNotBlank() }?.let { put(prefix + ".externalRecordId", it) }
        provenance.sourceProvider?.takeIf { it.isNotBlank() }?.let { put(prefix + ".sourceProvider", it) }
        provenance.sourceTransport?.takeIf { it.isNotBlank() }?.let { put(prefix + ".sourceTransport", it) }
        provenance.stableSourceId?.takeIf { it.isNotBlank() }?.let { put(prefix + ".stableSourceId", it) }
    }

    fun fromMetadata(
        metadata: Map<String, String>,
        prefix: String = "provenance"
    ): CardioObservationProvenance? {
        val kind = metadata[prefix + ".sourceKind"]
            ?.let { raw -> CardioSourceKind.entries.firstOrNull { it.name == raw } }
            ?: return null
        val name = metadata[prefix + ".sourceName"] ?: return null
        return CardioObservationProvenance(
            sourceKind = kind,
            sourceName = name,
            deviceId = metadata[prefix + ".deviceId"],
            deviceName = metadata[prefix + ".deviceName"],
            providerPackage = metadata[prefix + ".providerPackage"],
            externalRecordId = metadata[prefix + ".externalRecordId"],
            sourceProvider = metadata[prefix + ".sourceProvider"],
            sourceTransport = metadata[prefix + ".sourceTransport"],
            stableSourceId = metadata[prefix + ".stableSourceId"]
        )
    }

    fun fromSensor(source: CardioSensorProvenance): CardioObservationProvenance {
        val kind = when (source.providerType) {
            CardioSensorProviderType.H19C -> CardioSourceKind.H19C
            CardioSensorProviderType.BLE_HEART_RATE -> CardioSourceKind.GENERIC_BLE
            CardioSensorProviderType.HEALTH_CONNECT -> CardioSourceKind.HEALTH_CONNECT
            CardioSensorProviderType.NONE -> when (source.transport) {
                CardioSensorTransport.LIVE_BLE -> CardioSourceKind.DIRECT_BLE
                CardioSensorTransport.IMPORTED_HEALTH_CONNECT -> CardioSourceKind.HEALTH_CONNECT
                CardioSensorTransport.NONE -> CardioSourceKind.DERIVED
            }
        }
        return CardioObservationProvenance(
            sourceKind = kind,
            sourceName = source.sourceName,
            deviceId = source.anonymousSensorId,
            deviceName = source.deviceName,
            providerPackage = source.sourcePackage,
            externalRecordId = source.externalRecordId,
            sourceProvider = source.providerType.name,
            sourceTransport = source.transport.name,
            stableSourceId = source.anonymousSensorId
                ?: source.externalRecordId
                ?: source.sourcePackage
        )
    }

    fun displayLabel(provenance: CardioObservationProvenance): String {
        val device = provenance.deviceName?.takeIf { it.isNotBlank() }
            ?: provenance.sourceName
        val transport = provenance.sourceTransport
            ?.replace('_', ' ')
            ?.lowercase(Locale.US)
            ?.replaceFirstChar { it.titlecase(Locale.US) }
        return listOfNotNull(device, transport).joinToString(" · ")
    }
}

internal object CardioTrustIds {
    fun rawObservationId(
        sessionId: String,
        metricId: String,
        timestampEpochMs: Long,
        value: Double,
        provenance: CardioObservationProvenance
    ): String = listOf(
        "cardio-raw",
        sessionId,
        metricId,
        timestampEpochMs.toString(),
        value.toString(),
        provenance.stableSourceId ?: provenance.deviceId ?: provenance.sourceName
    ).joinToString(":")

    fun derivedResultId(
        sessionId: String?,
        metricId: String,
        algorithmVersion: String
    ): String = listOf(
        "cardio-derived",
        metricId,
        sessionId ?: "global",
        algorithmVersion
    ).joinToString(":")
}

internal fun CardioRawObservation.toHealthValue(): HealthValue {
    val metadata = buildMap {
        put(
            "sourceRecordId",
            CardioTrustIds.rawObservationId(
                sessionId,
                metricId,
                originalTimestampEpochMs,
                originalValue,
                provenance
            )
        )
        put("sessionId", sessionId)
        put("valueClass", CardioValueClass.MEASURED.name)
        put("originalTimestampEpochMs", originalTimestampEpochMs.toString())
        put("originalValue", originalValue.toString())
        put("canonicalValue", canonicalValue.toString())
        put("canonicalUnit", unit)
        put("ingestionTimestampEpochMs", ingestionTimestampEpochMs.toString())
        put("quality", quality.name)
        exclusionReason?.let { put("exclusionReason", it) }
        put("interpolated", interpolated.toString())
        put("resampled", resampled.toString())
        put("processingVersion", processingVersion)
        putAll(CardioProvenanceCodec.toMetadata(provenance))
    }
    return HealthValue(
        domain = HealthDomain.EXERCISE,
        metric = metricId,
        value = canonicalValue,
        unit = unit,
        timestampEpochMs = originalTimestampEpochMs,
        source = provenance.sourceName.ifBlank { "cardio-observation" },
        metadata = metadata
    )
}

internal data class CardioSourceCandidate(
    val candidateId: String,
    val metric: CardioAnalysisMetric,
    val provenance: CardioObservationProvenance,
    val valueClass: CardioValueClass,
    val quality: CardioObservationQuality,
    val coveragePct: Double? = null,
    val sampleCount: Int? = null
)

internal data class CardioSourceSelection(
    val selected: CardioSourceCandidate,
    val considered: List<CardioSourceCandidate>,
    val reason: String,
    val policyVersion: String = CARDIO_SOURCE_POLICY_VERSION
)

internal object CardioSourceArbitrator {
    fun select(
        metric: CardioAnalysisMetric,
        candidates: List<CardioSourceCandidate>,
        userOverrideCandidateId: String? = null
    ): CardioSourceSelection? {
        val eligible = candidates
            .filter { it.metric == metric }
            .filter { it.quality != CardioObservationQuality.INVALID }
            .filter { it.valueClass != CardioValueClass.UNAVAILABLE }
        if (eligible.isEmpty()) return null

        val ranked = eligible.sortedWith(
            compareByDescending<CardioSourceCandidate> { score(metric, it) }
                .thenByDescending { it.coveragePct ?: -1.0 }
                .thenByDescending { it.sampleCount ?: -1 }
                .thenBy { it.candidateId }
        )
        val override = userOverrideCandidateId
            ?.let { requested -> ranked.firstOrNull { it.candidateId == requested } }
        val selected = override ?: ranked.first()
        val reason = buildString {
            if (override != null) {
                append("Eligible user override selected. ")
            }
            append("Selected ")
            append(CardioProvenanceCodec.displayLabel(selected.provenance))
            append(" for ")
            append(metric.name.lowercase(Locale.US).replace('_', ' '))
            append(" using metric-specific policy; ")
            append(selected.valueClass.name.lowercase(Locale.US))
            append(", ")
            append(selected.quality.name.lowercase(Locale.US))
            selected.coveragePct?.let {
                append(", ")
                append(String.format(Locale.US, "%.1f%% coverage", it.coerceIn(0.0, 100.0)))
            }
            append(". Overlapping sources remain retained.")
        }
        return CardioSourceSelection(selected, ranked, reason)
    }

    fun selectionMetadata(
        selection: CardioSourceSelection,
        prefix: String = "analysisSource"
    ): Map<String, String> = buildMap {
        put(prefix + "PolicyVersion", selection.policyVersion)
        put(prefix + "CandidateId", selection.selected.candidateId)
        put(prefix + "Reason", selection.reason)
        put(prefix + "ConsideredCount", selection.considered.size.toString())
        putAll(CardioProvenanceCodec.toMetadata(selection.selected.provenance, prefix + ".provenance"))
    }

    private fun score(metric: CardioAnalysisMetric, candidate: CardioSourceCandidate): Int {
        val sourceScore = when (metric) {
            CardioAnalysisMetric.WORKOUT_HEART_RATE -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.H19C -> 95
                CardioSourceKind.GENERIC_BLE, CardioSourceKind.DIRECT_BLE -> 92
                CardioSourceKind.DIRECT_WEARABLE -> 82
                CardioSourceKind.FIT_IMPORT -> 76
                CardioSourceKind.HEALTH_CONNECT -> 68
                CardioSourceKind.TCX_IMPORT, CardioSourceKind.CSV_IMPORT -> 62
                CardioSourceKind.MANUAL -> 45
                CardioSourceKind.DERIVED -> 30
                CardioSourceKind.GPX_IMPORT, CardioSourceKind.PHONE_GPS -> 10
            }
            CardioAnalysisMetric.LIVE_HEART_RATE -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.H19C -> 100
                CardioSourceKind.GENERIC_BLE, CardioSourceKind.DIRECT_BLE -> 98
                CardioSourceKind.DIRECT_WEARABLE -> 88
                CardioSourceKind.HEALTH_CONNECT -> 35
                else -> 15
            }
            CardioAnalysisMetric.DISTANCE -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.PHONE_GPS -> 96
                CardioSourceKind.DIRECT_WEARABLE -> 92
                CardioSourceKind.FIT_IMPORT, CardioSourceKind.GPX_IMPORT -> 90
                CardioSourceKind.HEALTH_CONNECT -> 78
                CardioSourceKind.TCX_IMPORT -> 76
                CardioSourceKind.MANUAL -> 65
                CardioSourceKind.CSV_IMPORT -> 60
                CardioSourceKind.DERIVED -> 45
                CardioSourceKind.H19C, CardioSourceKind.GENERIC_BLE, CardioSourceKind.DIRECT_BLE -> 5
            }
            CardioAnalysisMetric.CYCLING_POWER -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.DIRECT_WEARABLE -> 95
                CardioSourceKind.FIT_IMPORT -> 90
                CardioSourceKind.HEALTH_CONNECT -> 75
                CardioSourceKind.TCX_IMPORT, CardioSourceKind.CSV_IMPORT -> 70
                CardioSourceKind.MANUAL -> 60
                CardioSourceKind.DERIVED -> 40
                else -> 15
            }
            CardioAnalysisMetric.HRV_RMSSD -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.H19C -> 98
                CardioSourceKind.GENERIC_BLE, CardioSourceKind.DIRECT_BLE -> 94
                CardioSourceKind.DIRECT_WEARABLE -> 85
                CardioSourceKind.HEALTH_CONNECT -> 75
                CardioSourceKind.FIT_IMPORT -> 70
                else -> 35
            }
            CardioAnalysisMetric.PACE_OR_SPEED -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.PHONE_GPS -> 94
                CardioSourceKind.DIRECT_WEARABLE -> 92
                CardioSourceKind.FIT_IMPORT, CardioSourceKind.GPX_IMPORT -> 88
                CardioSourceKind.HEALTH_CONNECT -> 76
                CardioSourceKind.TCX_IMPORT -> 72
                CardioSourceKind.MANUAL -> 55
                CardioSourceKind.DERIVED -> 45
                else -> 10
            }
            CardioAnalysisMetric.ELEVATION -> when (candidate.provenance.sourceKind) {
                CardioSourceKind.DIRECT_WEARABLE -> 92
                CardioSourceKind.GPX_IMPORT, CardioSourceKind.FIT_IMPORT -> 88
                CardioSourceKind.PHONE_GPS -> 80
                CardioSourceKind.HEALTH_CONNECT -> 72
                CardioSourceKind.TCX_IMPORT -> 70
                CardioSourceKind.MANUAL -> 55
                CardioSourceKind.DERIVED -> 45
                else -> 10
            }
        }

        val classScore = when (candidate.valueClass) {
            CardioValueClass.MEASURED -> 40
            CardioValueClass.DERIVED -> 15
            CardioValueClass.ESTIMATED -> 0
            CardioValueClass.INFERRED -> -10
            CardioValueClass.UNAVAILABLE -> -200
        }
        val powerPenalty = if (
            metric == CardioAnalysisMetric.CYCLING_POWER &&
            candidate.valueClass != CardioValueClass.MEASURED
        ) -80 else 0
        val qualityScore = when (candidate.quality) {
            CardioObservationQuality.ACCEPTED -> 20
            CardioObservationQuality.GAP_ADJACENT -> 5
            CardioObservationQuality.STALE -> -10
            CardioObservationQuality.SUSPECT_OUTLIER -> -25
            CardioObservationQuality.FILTERED -> -30
            CardioObservationQuality.INTERPOLATED -> -35
            CardioObservationQuality.INVALID -> -1_000
        }
        val coverageScore = ((candidate.coveragePct ?: 0.0).coerceIn(0.0, 100.0) / 10.0).toInt()
        return sourceScore + classScore + powerPenalty + qualityScore + coverageScore
    }
}
