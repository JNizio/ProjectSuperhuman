package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyCardioEvidenceTest {
    @Test
    fun weeklySummaryUsesOnlyCanonicalRowsAndReportsPartialCoverage() {
        val quality = TrudyDataQualityEvidence(
            domain = HealthDomain.EXERCISE,
            score = 80,
            recordCount = 5,
            distinctMetricCount = 3,
            latestTimestampEpochMs = 90L,
            ageHours = 0.0,
            isStale = false,
            notes = emptyList()
        )
        val rows = listOf(
            HealthValue(
                HealthDomain.EXERCISE, "cardio_session", 30.0, "min", 20L, "native-cardio",
                mapOf("sessionId" to "a", "distanceKm" to "5.0", "zone2Seconds" to "1200", "heartRateCoveragePct" to "90")
            ),
            HealthValue(
                HealthDomain.EXERCISE, "cardio_session", 40.0, "min", 40L, "native-cardio",
                mapOf("sessionId" to "b")
            ),
            HealthValue(
                HealthDomain.EXERCISE, "cardio_fitness_efficiency_delta_pct", 3.4, "%", 50L, "cardio-derived",
                mapOf("valueClass" to "DERIVED", "confidence" to "MODERATE", "algorithmVersion" to "v1")
            )
        )

        val summary = assertNotNull(
            TrudyCardioEvidenceAssembler.summarise(
                "week", "Cardio · this week", TrudyTimeRange(0L, 100L), rows, quality
            )
        )

        assertEquals(2, summary.sessions)
        assertEquals(70.0, summary.minutes)
        assertEquals(5.0, summary.distanceKm)
        assertEquals(20.0, summary.zone2Minutes)
        assertEquals(3.4, summary.fitnessEfficiencyDeltaPct)
        assertTrue(summary.dataGaps.any { "distance missing" in it })
        assertTrue(summary.dataGaps.any { "heart-rate coverage missing" in it })
        assertEquals(0.8, summary.toInsightEvidence().confidence)
    }

    @Test
    fun emptyWindowDoesNotManufactureSummary() {
        val result = TrudyCardioEvidenceAssembler.summarise(
            "week", "Cardio · this week", TrudyTimeRange(0L, 100L), emptyList(), null
        )

        assertEquals(null, result)
    }
}
