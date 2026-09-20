package com.projectsuperhuman.next.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CardioMetricRegistryTest {
    @Test
    fun nOf1MetricsAndExperimentProtocolAreCanonicalAndUnique() {
        val expected = setOf(
            "cardio_hr_sample_bpm",
            "cardio_rr_interval_ms",
            "heart_rate_variability_rmssd_ms",
            "cardio_fitness_efficiency_delta_pct",
            "cardio_chronic_training_load",
            "cardio_acute_training_load",
            "cardio_training_stress_balance",
            "cardio_training_readiness_score",
            "cardio_experiment_protocol"
        )

        val definitions = CoreMetricRegistry.definitions(HealthDomain.EXERCISE)
        val ids = definitions.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(expected.all { it in ids })
        expected.forEach { assertNotNull(CoreMetricRegistry.definition(HealthDomain.EXERCISE, it)) }
    }
}
