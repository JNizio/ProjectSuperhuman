package com.projectsuperhuman.next.vitals

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VitalsDomainTest {
    @Test
    fun `BP session preserves every individual reading and shared session metadata`() {
        val readings = listOf(
            BloodPressureReadingInput(122, 78, 1_000L, BloodPressureArm.LEFT, BloodPressurePosition.SITTING),
            BloodPressureReadingInput(118, 76, 61_000L, BloodPressureArm.LEFT, BloodPressurePosition.SITTING)
        )

        val values = VitalsValueFactory.bloodPressureSession("session-1", readings, "After five minutes rest")

        assertEquals(4, values.size)
        assertTrue(values.all { it.domain == HealthDomain.BLOOD_PRESSURE })
        assertTrue(values.all { it.metadata["vitalsSessionId"] == "session-1" })
        assertEquals(setOf("1", "2"), values.mapNotNull { it.metadata["bpReadingIndex"] }.toSet())
        assertEquals(4, values.mapNotNull { it.metadata["sourceRecordId"] }.distinct().size)
        assertFalse(values.any { it.metric.contains("average") })
    }

    @Test
    fun `unusual BP remains confirmable while malformed BP is blocked`() {
        val unusual = VitalsValidationRules.bloodPressure(
            BloodPressureReadingInput(190, 100, 1L, BloodPressureArm.RIGHT, BloodPressurePosition.SITTING)
        )
        val malformed = VitalsValidationRules.bloodPressure(
            BloodPressureReadingInput(400, 100, 1L, BloodPressureArm.RIGHT, BloodPressurePosition.SITTING)
        )

        assertTrue(unusual.canSave)
        assertTrue(unusual.needsConfirmation)
        assertFalse(malformed.canSave)
    }

    @Test
    fun `temperature keeps timestamp method provenance and canonical metric`() {
        val value = VitalsValueFactory.bodyTemperature(
            "temp-1",
            BodyTemperatureInput(36.7, 123_456L, TemperatureSite.EAR)
        )

        assertEquals(HealthDomain.BODY, value.domain)
        assertEquals(VitalsMetrics.BODY_TEMPERATURE_CELSIUS, value.metric)
        assertEquals("°C", value.unit)
        assertEquals("ear", value.metadata["measurementSite"])
        assertEquals("manual", value.metadata["entryMode"])
    }

    @Test
    fun `heart rate reuses canonical wearable metric`() {
        val value = VitalsValueFactory.heartRate("hr-1", HeartRateInput(62, 5_000L))
        assertEquals(HealthDomain.EXERCISE, value.domain)
        assertEquals("heart_rate_bpm", value.metric)
        assertEquals("bpm", value.unit)
    }
}
