package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyImportValidatorTest {
    @Test
    fun keepsValidMeasurementsAndFlagsDuplicates() {
        val v = HealthValue(
            domain = HealthDomain.SLEEP,
            metric = "sleep.total_minutes",
            value = 442.0,
            unit = "min",
            timestampEpochMs = 1_720_000_000_000,
            source = "legacy-webview"
        )
        val report = LegacyImportValidator().validate(
            LegacyImportBatch("10.2.1", 1_720_000_100_000, listOf(v, v))
        )
        assertEquals(2, report.accepted)
        assertEquals(0, report.rejected)
        assertEquals(1, report.duplicateCandidates)
    }

    @Test
    fun rejectsMalformedMeasurements() {
        val invalid = HealthValue(
            domain = HealthDomain.CLINICAL,
            metric = "",
            value = Double.NaN,
            unit = "",
            timestampEpochMs = 0,
            source = ""
        )
        val report = LegacyImportValidator().validate(
            LegacyImportBatch("10.2.1", 1, listOf(invalid))
        )
        assertEquals(0, report.accepted)
        assertEquals(1, report.rejected)
    }
}
