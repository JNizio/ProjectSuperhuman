package com.projectsuperhuman.next.vitals

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue

/** Stable identifiers shared by capture, history and Trudy/Data Vault queries. */
object VitalsMetrics {
    const val HEART_RATE_BPM = "heart_rate_bpm"
    const val BLOOD_PRESSURE_SYSTOLIC = "blood_pressure_systolic_mmhg"
    const val BLOOD_PRESSURE_DIASTOLIC = "blood_pressure_diastolic_mmhg"
    const val BODY_TEMPERATURE_CELSIUS = "body_temperature_celsius"
}

const val MANUAL_VITALS_SOURCE = "manual-vitals"

enum class BloodPressureArm(val storedValue: String) {
    LEFT("left"), RIGHT("right")
}

enum class BloodPressurePosition(val storedValue: String) {
    SITTING("sitting"), STANDING("standing")
}

enum class TemperatureSite(val storedValue: String) {
    ORAL("oral"), EAR("ear"), FOREHEAD("forehead"), AXILLARY("axillary"), OTHER("other")
}

data class BloodPressureReadingInput(
    val systolic: Int,
    val diastolic: Int,
    val measuredAtEpochMs: Long,
    val arm: BloodPressureArm,
    val position: BloodPressurePosition
)

data class BodyTemperatureInput(
    val celsius: Double,
    val measuredAtEpochMs: Long,
    val site: TemperatureSite? = null
)

data class HeartRateInput(
    val beatsPerMinute: Int,
    val measuredAtEpochMs: Long
)

data class VitalsValidation(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val canSave: Boolean get() = errors.isEmpty()
    val needsConfirmation: Boolean get() = canSave && warnings.isNotEmpty()
}

/**
 * Entry validation deliberately separates malformed values from unusual-but-possible values.
 * Warnings can be confirmed by the user; values outside canonical storage bounds cannot be saved.
 */
object VitalsValidationRules {
    fun bloodPressure(input: BloodPressureReadingInput): VitalsValidation {
        val errors = buildList {
            if (input.systolic !in 40..300) add("Systolic must be between 40 and 300 mmHg.")
            if (input.diastolic !in 20..200) add("Diastolic must be between 20 and 200 mmHg.")
            if (input.measuredAtEpochMs <= 0L) add("Choose a valid measurement time.")
        }
        val warnings = buildList {
            if (input.systolic <= input.diastolic) add("Systolic is usually higher than diastolic. Check the values before saving.")
            if (input.systolic < 70 || input.systolic > 180 || input.diastolic < 40 || input.diastolic > 120) {
                add("This reading is unusual. Check the monitor and save only if it is accurate.")
            }
        }
        return VitalsValidation(errors.distinct(), warnings.distinct())
    }

    fun temperature(input: BodyTemperatureInput): VitalsValidation {
        val errors = buildList {
            if (!input.celsius.isFinite() || input.celsius !in 20.0..50.0) {
                add("Temperature must be between 20.0 and 50.0 °C.")
            }
            if (input.measuredAtEpochMs <= 0L) add("Choose a valid measurement time.")
        }
        val warnings = if (input.celsius.isFinite() && input.celsius !in 34.0..40.5 && errors.isEmpty()) {
            listOf("This temperature is unusual. Check the value and measurement method before saving.")
        } else emptyList()
        return VitalsValidation(errors, warnings)
    }

    fun heartRate(input: HeartRateInput): VitalsValidation {
        val errors = buildList {
            if (input.beatsPerMinute !in 20..260) add("Heart rate must be between 20 and 260 bpm.")
            if (input.measuredAtEpochMs <= 0L) add("Choose a valid measurement time.")
        }
        val warnings = if (input.beatsPerMinute !in 40..140 && errors.isEmpty()) {
            listOf("This heart rate is unusual. Check the value and save only if it is accurate.")
        } else emptyList()
        return VitalsValidation(errors, warnings)
    }
}

object VitalsValueFactory {
    fun bloodPressureSession(
        sessionId: String,
        readings: List<BloodPressureReadingInput>,
        notes: String? = null
    ): List<HealthValue> {
        require(sessionId.isNotBlank()) { "A BP session ID is required" }
        require(readings.isNotEmpty()) { "At least one BP reading is required" }
        readings.forEach { require(VitalsValidationRules.bloodPressure(it).canSave) }

        return readings.flatMapIndexed { index, reading ->
            val readingNumber = index + 1
            val common = buildMap {
                put("entryMode", "manual")
                put("vitalsSessionId", sessionId)
                put("bpReadingIndex", readingNumber.toString())
                put("arm", reading.arm.storedValue)
                put("bodyPosition", reading.position.storedValue)
                notes?.trim()?.takeIf { it.isNotEmpty() }?.let { put("notes", it) }
            }
            listOf(
                HealthValue(
                    domain = HealthDomain.BLOOD_PRESSURE,
                    metric = VitalsMetrics.BLOOD_PRESSURE_SYSTOLIC,
                    value = reading.systolic.toDouble(),
                    unit = "mmHg",
                    timestampEpochMs = reading.measuredAtEpochMs,
                    source = MANUAL_VITALS_SOURCE,
                    metadata = common + ("sourceRecordId" to "bp:$sessionId:$readingNumber:systolic")
                ),
                HealthValue(
                    domain = HealthDomain.BLOOD_PRESSURE,
                    metric = VitalsMetrics.BLOOD_PRESSURE_DIASTOLIC,
                    value = reading.diastolic.toDouble(),
                    unit = "mmHg",
                    timestampEpochMs = reading.measuredAtEpochMs,
                    source = MANUAL_VITALS_SOURCE,
                    metadata = common + ("sourceRecordId" to "bp:$sessionId:$readingNumber:diastolic")
                )
            )
        }
    }

    fun bodyTemperature(recordId: String, input: BodyTemperatureInput): HealthValue {
        require(recordId.isNotBlank())
        require(VitalsValidationRules.temperature(input).canSave)
        return HealthValue(
            domain = HealthDomain.BODY,
            metric = VitalsMetrics.BODY_TEMPERATURE_CELSIUS,
            value = input.celsius,
            unit = "°C",
            timestampEpochMs = input.measuredAtEpochMs,
            source = MANUAL_VITALS_SOURCE,
            metadata = buildMap {
                put("entryMode", "manual")
                put("sourceRecordId", "temperature:$recordId")
                input.site?.let { put("measurementSite", it.storedValue) }
            }
        )
    }

    fun heartRate(recordId: String, input: HeartRateInput): HealthValue {
        require(recordId.isNotBlank())
        require(VitalsValidationRules.heartRate(input).canSave)
        return HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = VitalsMetrics.HEART_RATE_BPM,
            value = input.beatsPerMinute.toDouble(),
            unit = "bpm",
            timestampEpochMs = input.measuredAtEpochMs,
            source = MANUAL_VITALS_SOURCE,
            metadata = mapOf(
                "entryMode" to "manual",
                "sourceRecordId" to "heart-rate:$recordId"
            )
        )
    }
}

