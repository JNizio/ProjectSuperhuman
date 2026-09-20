package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyCanonicalExperiment
import com.projectsuperhuman.next.trudy.TrudyCanonicalExperimentStatus
import com.projectsuperhuman.next.trudy.TrudyConfidence
import com.projectsuperhuman.next.trudy.TrudyEvidenceKind
import com.projectsuperhuman.next.trudy.TrudyEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyTimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CardioExperimentRepositoryTest {
    @Test
    fun canonicalExperimentRoundTripsThroughDataVaultShape() {
        val experiment = TrudyCanonicalExperiment(
            id = "zone2-caffeine",
            title = "Caffeine and Zone 2 efficiency",
            hypothesis = "Caffeine timing may be associated with pace at a comparable heart rate.",
            intervention = "Use the recorded caffeine protocol.",
            status = TrudyCanonicalExperimentStatus.ACTIVE,
            targetDomain = HealthDomain.EXERCISE,
            targetMetricId = "cardio_fitness_efficiency_delta_pct",
            secondaryMetrics = listOf(HealthDomain.SLEEP to "sleep_score"),
            baselineWindow = TrudyTimeRange(10L, 20L),
            interventionWindow = TrudyTimeRange(21L, 30L),
            adherenceFraction = 0.8,
            source = "cardio-experiment",
            updatedEpochMs = 31L,
            comparator = "usual pre-run routine",
            inclusionRules = listOf("comparable running sessions"),
            confounders = listOf("sleep", "temperature", "route", "training load"),
            observations = listOf("session-linked observations"),
            analysisMethod = "before_after_personal_comparison",
            confidence = TrudyConfidence.LOW,
            caveats = listOf("Association does not establish causation."),
            evidenceReferences = listOf(
                TrudyEvidenceReference(
                    domain = HealthDomain.EXERCISE,
                    metricId = "cardio_fitness_efficiency_delta_pct",
                    evidenceKind = TrudyEvidenceKind.DERIVED_PERSONAL_TREND,
                    timestampEpochMs = 30L
                )
            )
        )

        val restored = assertNotNull(cardioExperimentFromValue(experiment.toCardioExperimentValue()))

        assertEquals(experiment.id, restored.id)
        assertEquals(experiment.targetMetricId, restored.targetMetricId)
        assertEquals(experiment.confounders, restored.confounders)
        assertEquals(experiment.inclusionRules, restored.inclusionRules)
        assertEquals(experiment.evidenceReferences, restored.evidenceReferences)
        assertEquals("cardio_experiment_protocol", experiment.toCardioExperimentValue().metric)
        assertEquals("cardio-experiment:zone2-caffeine", experiment.toCardioExperimentValue().metadata["sourceRecordId"])
    }
}
