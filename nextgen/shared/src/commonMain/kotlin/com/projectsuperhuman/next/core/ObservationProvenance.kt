package com.projectsuperhuman.next.core

/**
 * How the timestamp used for a physiological observation was established.
 *
 * This is deliberately separate from the transport. A BLE packet may not contain an absolute
 * device timestamp, in which case its best measurement time is the phone receive time.
 */
enum class ObservationTimeBasis {
    DEVICE_REPORTED,
    SOURCE_REPORTED,
    PHONE_RECEIVE_TIME,
    SESSION_DERIVED,
    USER_ENTERED,
    UNKNOWN
}

enum class ObservationTransport {
    DIRECT_BLE,
    STANDARD_BLE,
    HEALTH_CONNECT,
    VENDOR_SDK,
    VENDOR_API,
    FILE_IMPORT,
    MANUAL,
    DERIVED,
    UNKNOWN
}

/**
 * Privacy-safe logical identity for a physical device. Hardware addresses should not be exposed
 * here directly; adapters should provide a stable anonymous/hash identifier where necessary.
 */
data class ObservationDeviceIdentity(
    val deviceId: String? = null,
    val displayName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val deviceType: String? = null
)

/**
 * Timing contract for one observation.
 *
 * measuredAtEpochMs is ALWAYS when the event belongs on the physiological timeline.
 * receivedAtEpochMs records when Project Superhuman first received the event.
 * importedAtEpochMs records when the observation was ingested into the Data Vault.
 */
data class ObservationTiming(
    val measuredAtEpochMs: Long,
    val receivedAtEpochMs: Long? = null,
    val importedAtEpochMs: Long? = null,
    val sourceModifiedAtEpochMs: Long? = null,
    val timeBasis: ObservationTimeBasis = ObservationTimeBasis.UNKNOWN
) {
    init {
        require(measuredAtEpochMs > 0L)
        require(receivedAtEpochMs == null || receivedAtEpochMs > 0L)
        require(importedAtEpochMs == null || importedAtEpochMs > 0L)
        require(sourceModifiedAtEpochMs == null || sourceModifiedAtEpochMs > 0L)
    }
}

/**
 * Full source chain for an observation. The physical origin and the transport/application used to
 * reach Project Superhuman are intentionally separate concepts.
 */
data class ObservationProvenance(
    val timing: ObservationTiming,
    val device: ObservationDeviceIdentity = ObservationDeviceIdentity(),
    val transport: ObservationTransport = ObservationTransport.UNKNOWN,
    val protocol: String? = null,
    val sourceApplication: String? = null,
    val sourcePackage: String? = null,
    val externalRecordId: String? = null,
    val sourceLabel: String? = null,
    val confidence: Double? = null
) {
    fun toMetadata(prefix: String = ""): Map<String, String> {
        val p = if (prefix.isBlank()) "" else prefix.trimEnd('.') + "."
        return buildMap {
            put(p + "provenanceVersion", "1")
            put(p + "measuredAtEpochMs", timing.measuredAtEpochMs.toString())
            timing.receivedAtEpochMs?.let { put(p + "receivedAtEpochMs", it.toString()) }
            timing.importedAtEpochMs?.let { put(p + "importedAtEpochMs", it.toString()) }
            timing.sourceModifiedAtEpochMs?.let { put(p + "sourceModifiedAtEpochMs", it.toString()) }
            put(p + "timeBasis", timing.timeBasis.name)
            put(p + "transport", transport.name)
            device.deviceId?.takeIf { it.isNotBlank() }?.let { put(p + "originDeviceId", it) }
            device.displayName?.takeIf { it.isNotBlank() }?.let { put(p + "originDeviceName", it) }
            device.manufacturer?.takeIf { it.isNotBlank() }?.let { put(p + "originManufacturer", it) }
            device.model?.takeIf { it.isNotBlank() }?.let { put(p + "originModel", it) }
            device.firmware?.takeIf { it.isNotBlank() }?.let { put(p + "originFirmware", it) }
            device.deviceType?.takeIf { it.isNotBlank() }?.let { put(p + "originDeviceType", it) }
            protocol?.takeIf { it.isNotBlank() }?.let { put(p + "protocol", it) }
            sourceApplication?.takeIf { it.isNotBlank() }?.let { put(p + "sourceApplication", it) }
            sourcePackage?.takeIf { it.isNotBlank() }?.let { put(p + "sourcePackage", it) }
            externalRecordId?.takeIf { it.isNotBlank() }?.let { put(p + "externalRecordId", it) }
            sourceLabel?.takeIf { it.isNotBlank() }?.let { put(p + "sourceLabel", it) }
            confidence?.let { put(p + "confidence", it.coerceIn(0.0, 1.0).toString()) }
        }
    }
}

/**
 * Compatibility helper while HealthValue still has one canonical timestamp column.
 * The column remains measurement-time semantics; receive/import/source timing lives in metadata.
 */
fun HealthValue.withObservationProvenance(provenance: ObservationProvenance): HealthValue =
    copy(
        timestampEpochMs = provenance.timing.measuredAtEpochMs,
        metadata = metadata + provenance.toMetadata()
    )
