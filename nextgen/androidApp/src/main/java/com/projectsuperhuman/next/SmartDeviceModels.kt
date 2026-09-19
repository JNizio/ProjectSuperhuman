package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.ObservationDeviceIdentity
import com.projectsuperhuman.next.core.ObservationProvenance
import com.projectsuperhuman.next.core.ObservationTimeBasis
import com.projectsuperhuman.next.core.ObservationTiming
import com.projectsuperhuman.next.core.ObservationTransport

internal enum class SmartDeviceCapability {
    HEART_RATE,
    RR_INTERVAL,
    SPO2,
    STEPS,
    SLEEP,
    WEIGHT,
    BODY_COMPOSITION,
    BLOOD_PRESSURE,
    TEMPERATURE,
    CYCLING_POWER,
    CYCLING_CADENCE,
    RUNNING_CADENCE,
    SPEED,
    DISTANCE,
    ACCELEROMETER,
    PPG
}

internal enum class SmartDeviceConnectionStatus {
    SAVED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    STALE,
    RECONNECTING,
    DISCONNECTED,
    ERROR
}

internal data class SmartDeviceDescriptor(
    val deviceId: String,
    val displayName: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val family: SmartDeviceFamily,
    val transport: SmartDeviceTransport,
    val capabilities: Set<SmartDeviceCapability>,
    val saved: Boolean = false
)

internal data class SmartDeviceReading(
    val metric: String,
    val value: Double,
    val unit: String,
    val provenance: ObservationProvenance
) {
    val measuredAtEpochMs: Long get() = provenance.timing.measuredAtEpochMs
    val receivedAtEpochMs: Long? get() = provenance.timing.receivedAtEpochMs
    val importedAtEpochMs: Long? get() = provenance.timing.importedAtEpochMs

    fun ageMs(nowEpochMs: Long = System.currentTimeMillis()): Long =
        (nowEpochMs - measuredAtEpochMs).coerceAtLeast(0L)
}

internal object SmartDeviceObservationMapper {
    fun h19cHeartRate(state: H19cWearableState): SmartDeviceReading? {
        val bpm = state.heartRateBpm ?: return null
        val measuredAt = state.lastHeartRateEpochMs ?: return null
        return SmartDeviceReading(
            metric = "heart_rate_bpm",
            value = bpm.toDouble(),
            unit = "bpm",
            provenance = ObservationProvenance(
                timing = ObservationTiming(
                    measuredAtEpochMs = measuredAt,
                    receivedAtEpochMs = measuredAt,
                    timeBasis = ObservationTimeBasis.PHONE_RECEIVE_TIME
                ),
                device = ObservationDeviceIdentity(
                    deviceId = CardioSensorIds.anonymous(state.deviceAddress),
                    displayName = state.deviceName ?: "H19C",
                    manufacturer = state.manufacturer,
                    model = state.model,
                    firmware = state.firmware,
                    deviceType = "wearable"
                ),
                transport = ObservationTransport.DIRECT_BLE,
                protocol = "FEEA / Bluetooth",
                sourceApplication = "Project Superhuman",
                sourceLabel = H19cWearableRuntime.SOURCE
            )
        )
    }

    fun bleHeartRate(state: CardioSensorState): SmartDeviceReading? {
        if (state.providerType != CardioSensorProviderType.BLE_HEART_RATE) return null
        val bpm = state.currentHeartRateBpm ?: return null
        val measuredAt = state.lastSampleEpochMs ?: return null
        val source = state.provenance
        return SmartDeviceReading(
            metric = "heart_rate_bpm",
            value = bpm.toDouble(),
            unit = "bpm",
            provenance = ObservationProvenance(
                timing = ObservationTiming(
                    measuredAtEpochMs = measuredAt,
                    receivedAtEpochMs = measuredAt,
                    timeBasis = ObservationTimeBasis.PHONE_RECEIVE_TIME
                ),
                device = ObservationDeviceIdentity(
                    deviceId = source?.anonymousSensorId,
                    displayName = source?.deviceName ?: "Bluetooth heart-rate sensor",
                    manufacturer = source?.manufacturer,
                    model = source?.model,
                    deviceType = "heart_rate_sensor"
                ),
                transport = ObservationTransport.STANDARD_BLE,
                protocol = "Bluetooth SIG Heart Rate Service",
                sourceApplication = "Project Superhuman",
                sourceLabel = source?.sourceName ?: "bluetooth-sig-heart-rate"
            )
        )
    }

    fun historicalHeartRate(value: HealthValue): SmartDeviceReading? {
        if (value.metric != "heart_rate_bpm") return null
        val importedAt = value.metadata["importedAtEpochMs"]?.toLongOrNull()
            ?: value.metadata["receivedAtEpochMs"]?.toLongOrNull()
            ?: value.metadata["sourceLastModifiedMs"]?.toLongOrNull()
        val basis = runCatching {
            ObservationTimeBasis.valueOf(value.metadata["timeBasis"].orEmpty())
        }.getOrDefault(ObservationTimeBasis.SOURCE_REPORTED)
        return SmartDeviceReading(
            metric = value.metric,
            value = value.value,
            unit = value.unit,
            provenance = ObservationProvenance(
                timing = ObservationTiming(
                    measuredAtEpochMs = value.metadata["measuredAtEpochMs"]?.toLongOrNull()
                        ?: value.timestampEpochMs,
                    receivedAtEpochMs = value.metadata["receivedAtEpochMs"]?.toLongOrNull(),
                    importedAtEpochMs = importedAt,
                    sourceModifiedAtEpochMs = value.metadata["sourceModifiedAtEpochMs"]?.toLongOrNull()
                        ?: value.metadata["sourceLastModifiedMs"]?.toLongOrNull(),
                    timeBasis = basis
                ),
                device = ObservationDeviceIdentity(
                    deviceId = value.metadata["originDeviceId"] ?: value.metadata["deviceId"],
                    displayName = value.metadata["originDeviceName"]
                        ?: value.metadata["deviceName"]
                        ?: value.metadata["sourceDeviceFamily"],
                    manufacturer = value.metadata["originManufacturer"] ?: value.metadata["manufacturer"],
                    model = value.metadata["originModel"] ?: value.metadata["model"],
                    firmware = value.metadata["originFirmware"] ?: value.metadata["firmware"],
                    deviceType = value.metadata["originDeviceType"]
                ),
                transport = when {
                    value.source == MiniMetricsHealthConnect.SOURCE -> ObservationTransport.HEALTH_CONNECT
                    value.source == H19cWearableRuntime.SOURCE -> ObservationTransport.DIRECT_BLE
                    else -> ObservationTransport.UNKNOWN
                },
                protocol = value.metadata["protocol"],
                sourceApplication = value.metadata["sourceApplication"]
                    ?: if (value.source == MiniMetricsHealthConnect.SOURCE) "Samsung Health" else null,
                sourcePackage = value.metadata["sourcePackage"],
                externalRecordId = value.metadata["externalRecordId"]
                    ?: value.metadata["healthConnectRecordId"],
                sourceLabel = value.source
            )
        )
    }
}
