package com.projectsuperhuman.next.trudy.performance

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.emotional.EmotionalMetricIds
import com.projectsuperhuman.next.environment.EnvironmentalMetricCatalog
import com.projectsuperhuman.next.trudy.TrudyExperimentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrudyPerformanceKnowledgeTest {
    private val knowledge = TrudyPerformanceKnowledge()

    @Test
    fun catalogCoversEveryRequestedPerformanceDomainWithValidEvidence() {
        val covered = PerformanceKnowledgeCatalog.topics
            .flatMap { listOf(it.primaryDomain) + it.relatedDomains }
            .toSet()
        assertEquals(PerformanceKnowledgeDomain.entries.toSet(), covered)

        val sourceIds = PerformanceEvidenceCatalog.sources.map { it.id }.toSet()
        PerformanceKnowledgeCatalog.claims.forEach { claim ->
            assertTrue(claim.evidenceSourceIds.isNotEmpty())
            assertTrue(claim.evidenceSourceIds.all { it in sourceIds }, claim.id)
        }
    }

    @Test
    fun everyMetricBindingPointsAtAnExistingCanonicalOwner() {
        val emotionalIds = EmotionalMetricIds.canonical.map { it.value }.toSet()
        val bindings = PerformanceKnowledgeCatalog.topics.flatMap { it.metricBindings } +
            PerformanceExperimentMethodology.blueprints.flatMap { listOf(it.primaryOutcome) + it.secondaryOutcomes }

        bindings.forEach { binding ->
            when (binding.origin) {
                PerformanceMetricOrigin.CORE_METRIC_REGISTRY ->
                    assertNotNull(CoreMetricRegistry.definition(binding.domain, binding.metricId), binding.metricId)
                PerformanceMetricOrigin.ENVIRONMENTAL_DOMAIN ->
                    assertNotNull(EnvironmentalMetricCatalog.definition(binding.metricId), binding.metricId)
                PerformanceMetricOrigin.EMOTIONAL_DOMAIN ->
                    assertTrue(binding.metricId in emotionalIds, binding.metricId)
            }
        }
    }

    @Test
    fun britishSleepSlangMapsToContinuityAndCanonicalSleepEvidence() {
        val result = knowledge.resolve("I slept like crap and kept waking up — I'm shattered")
        val topics = result.matches.map { it.topic.id }.toSet()

        assertTrue("sleep_continuity" in topics)
        assertTrue("fatigue" in topics)
        assertTrue(result.metricBindings.any { it.metricId == "sleep_awake_minutes" })
        assertTrue(result.metricBindings.any { it.metricId == "emotional_energy" })
    }

    @Test
    fun hardWorkoutSlangRoutesToLoadRecoveryAndDoesNotInventReadinessProof() {
        val result = knowledge.resolve("I smashed the gym and the workout destroyed me")
        val topics = result.matches.map { it.topic.id }.toSet()

        assertTrue("training_load" in topics)
        assertTrue("training_recovery" in topics)
        assertTrue(result.metricBindings.any { it.metricId == "workout_volume" })
        assertTrue(PerformanceSafetyBoundary.AVOID_SINGLE_SCORE_READINESS_CLAIM in result.globalSafetyBoundaries)
    }

    @Test
    fun racingPulseRetrievesHeartRateContextWithSafetyBoundary() {
        val result = knowledge.resolve("My pulse is racing and my heart is pounding")

        assertTrue(result.matches.any { it.topic.id == "heart_rate_context" })
        assertTrue(result.metricBindings.any { it.metricId == "heart_rate_bpm" })
        assertTrue(PerformanceSafetyBoundary.STOP_FOR_CONCERNING_SYMPTOMS in result.globalSafetyBoundaries)
        assertTrue(PerformanceSafetyBoundary.NON_DIAGNOSTIC in result.globalSafetyBoundaries)
    }

    @Test
    fun crossDomainHotBedroomQuestionReturnsEnvironmentAndSleepMappings() {
        val result = knowledge.resolve("Could my hot bedroom be why I kept waking up?")

        assertTrue(result.matches.any { it.topic.id == "sleep_environment" })
        assertTrue(result.metricBindings.any { it.metricId == "environment_temperature_c" })
        assertTrue(result.metricBindings.any { it.metricId == "sleep_awake_minutes" })
        assertTrue(PerformanceSafetyBoundary.PERSONAL_ASSOCIATION_NOT_CAUSATION in result.globalSafetyBoundaries)
    }

    @Test
    fun stressAndBrainFogUseEmotionalSleepAndRecoveryContext() {
        val result = knowledge.resolve("I'm stressed out and my head feels foggy")
        val topics = result.matches.map { it.topic.id }.toSet()

        assertTrue("emotional_stress" in topics)
        assertTrue("mood_energy_focus" in topics)
        assertTrue(result.metricBindings.any { it.metricId == "emotional_calmness" })
        assertTrue(result.metricBindings.any { it.metricId == "emotional_focus" })
        assertTrue(PerformanceSafetyBoundary.MENTAL_WELLBEING_NOT_DIAGNOSIS in result.globalSafetyBoundaries)
    }

    @Test
    fun spellingAndAbbreviationNormalisationRemainDataDriven() {
        val misspeltExercise = knowledge.resolve("Have I done enough excersize and HR zone 2 this week?")
        val air = knowledge.resolve("Was PM2.5 or AQI bad for my run?")

        assertTrue(misspeltExercise.matches.any { it.topic.id == "cardiovascular_training" })
        assertTrue(misspeltExercise.matches.any { it.topic.id == "heart_rate_context" })
        assertTrue(air.matches.any { it.topic.id == "air_quality" })
        assertTrue(air.metricBindings.any { it.metricId == "environment_pm2_5_ug_m3" })
    }

    @Test
    fun caffeineQuestionReturnsStructuredExperimentMethodology() {
        val result = knowledge.resolve("How could I test whether caffeine affects my sleep?")
        val blueprint = result.experimentBlueprints.firstOrNull { it.id == "caffeine_sleep_timing" }

        assertNotNull(blueprint)
        assertEquals(TrudyExperimentKind.EARLIER_CAFFEINE_CUTOFF, blueprint.existingExperimentKind)
        assertEquals("sleep_total_minutes", blueprint.primaryOutcome.metricId)
        assertTrue(blueprint.confounders.isNotEmpty())
        assertTrue(blueprint.interpretationLimitations.any { "uncontrolled" in it.lowercase() })
    }

    @Test
    fun experimentKnowledgeContainsCoreMethodConceptsAndCausalBoundary() {
        val ids = PerformanceExperimentMethodology.principles.map { it.id }.toSet()
        assertTrue(setOf(
            "hypothesis", "baseline", "single_intervention", "primary_outcome", "confounders",
            "duration", "consistency", "measurement_noise", "repeated_measures", "interpretation"
        ).all { it in ids })

        val result = knowledge.resolve("Help me design a personal experiment with a baseline and repeated measures")
        assertTrue(result.matches.any { it.topic.id == "personal_experiments" })
        assertTrue(PerformanceSafetyBoundary.EXPERIMENT_NOT_UNIVERSAL_PROOF in result.globalSafetyBoundaries)
    }

    @Test
    fun unknownTextDoesNotCauseBroadDataVaultRetrieval() {
        val result = knowledge.resolve("Please rename my dashboard tile")
        assertTrue(result.matches.isEmpty())
        assertTrue(result.metricBindings.isEmpty())
        assertTrue(result.claims.isEmpty())
    }
}
