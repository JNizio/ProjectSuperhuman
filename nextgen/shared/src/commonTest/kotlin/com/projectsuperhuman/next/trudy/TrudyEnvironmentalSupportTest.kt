package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrudyEnvironmentalSupportTest {
    private val environmentDomain = HealthDomain.BODY
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val config = TrudyEnvironmentalConfig(
        environmentDomain = environmentDomain,
        metrics = listOf(
            TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE, "environment_temperature_c"),
            TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.RELATIVE_HUMIDITY, "environment_humidity_pct"),
            TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.PRECIPITATION, "environment_precipitation_mm")
        ),
        targets = listOf(
            TrudyEnvironmentalTargetBinding(
                TrudyEnvironmentalTargetSemantic.SLEEP,
                HealthDomain.SLEEP,
                "sleep_score",
                "your sleep",
                "Your sleep has tended to be better",
                "Your sleep has tended to be worse"
            ),
            TrudyEnvironmentalTargetBinding(
                TrudyEnvironmentalTargetSemantic.EXERCISE,
                HealthDomain.EXERCISE,
                "exercise_minutes",
                "your exercise",
                "You tended to exercise more",
                "You tended to exercise less"
            )
        )
    )

    @Test fun environmentalAssociationsUseExistingDeterministicTool() {
        val temperature = config.metric(TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE)!!
        val sleep = config.target(TrudyEnvironmentalTargetSemantic.SLEEP)!!
        val operations = TrudyEnvironmentalTools(config).association(temperature, sleep)
        val association = operations.filterIsInstance<GetAssociation>().single()
        assertEquals(environmentDomain, association.leftDomain)
        assertEquals("environment_temperature_c", association.leftMetricId)
        assertEquals(HealthDomain.SLEEP, association.rightDomain)
        assertEquals("sleep_score", association.rightMetricId)
        assertEquals(TrudyAssociationMethod.SPEARMAN, association.method)
    }

    @Test fun broadAssociationRequestIsBounded() {
        val sleep = config.target(TrudyEnvironmentalTargetSemantic.SLEEP)!!
        val operations = TrudyEnvironmentalTools(config).associations(config.metrics, sleep)
        assertEquals(3, operations.filterIsInstance<GetAssociation>().size)
        assertEquals(1, operations.filterIsInstance<TrudyToolOperation.GetDataQuality>().size)
    }

    @Test fun currentSnapshotPreservesSourceMeasurementTimeAndQuality() {
        val q = quality(now - 2 * hour, stale = false)
        val operations = TrudyEnvironmentalTools(config).currentState()
        val stateOp = operations.filterIsInstance<TrudyToolOperation.GetDomainState>().single()
        val qualityOp = operations.filterIsInstance<TrudyToolOperation.GetDataQuality>().single()
        val evidence = listOf(
            metric("environment_temperature_c", 22.5, "°C", now - 2 * hour, "station:test", q),
            metric("environment_humidity_pct", 61.0, "%", now - 2 * hour, "station:test", q)
        )
        val synthesis = TrudyEnvironmentalSynthesizer(config, { now }).current(
            config.metrics,
            listOf(TrudyToolResult.DomainState(stateOp, evidence), TrudyToolResult.DataQuality(qualityOp, q))
        )
        assertEquals(TrudyEnvironmentalFreshness.CURRENT, synthesis.grounding.freshness)
        assertEquals(now - 2 * hour, synthesis.grounding.latestMeasurementEpochMs)
        assertEquals(listOf("station:test"), synthesis.grounding.sources)
        assertEquals(q, synthesis.grounding.dataQuality)
        assertTrue("temperature 22.5°C" in synthesis.text)
        assertTrue("humidity 61%" in synthesis.text)
    }

    @Test fun oldSnapshotIsNeverPresentedAsCurrentConditions() {
        val staleConfig = config.copy(currentFreshnessHours = 3.0, agingFreshnessHours = 8.0)
        val q = quality(now - 12 * hour, stale = false)
        val stateOp = TrudyToolOperation.GetDomainState(environmentDomain)
        val qualityOp = TrudyToolOperation.GetDataQuality(environmentDomain)
        val synthesis = TrudyEnvironmentalSynthesizer(staleConfig, { now }).current(
            staleConfig.metrics,
            listOf(
                TrudyToolResult.DomainState(stateOp, listOf(metric("environment_temperature_c", 19.0, "°C", now - 12 * hour, "stored-weather", q))),
                TrudyToolResult.DataQuality(qualityOp, q)
            )
        )
        assertEquals(TrudyEnvironmentalFreshness.STALE, synthesis.grounding.freshness)
        assertTrue(synthesis.text.startsWith("I don't have fresh enough readings"))
    }

    @Test fun naturalAssociationLanguageAvoidsRoboticDisclaimer() {
        val temperature = config.metric(TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE)!!
        val sleep = config.target(TrudyEnvironmentalTargetSemantic.SLEEP)!!
        val result = associationResult(temperature.metricId, sleep, -0.58)
        val text = TrudyEnvironmentalSynthesizer(config, { now }).association(temperature, sleep, result).text
        assertTrue(text.startsWith("Your sleep has tended to be worse on hotter nights."))
        assertTrue("doesn't show temperature is responsible" in text)
        assertFalse("association is not causation" in text.lowercase())
        assertFalse("descriptive trend" in text.lowercase())
    }

    @Test fun policyRequiresFreshGroundedEnvironmentalEvidence() {
        val policy = TrudyEnvironmentalPolicy.MODEL_INSTRUCTION.lowercase()
        assertTrue("never invent current weather" in policy)
        assertTrue("source" in policy)
        assertTrue("measurement timestamp" in policy)
        assertTrue("data-quality" in policy)
        assertTrue("deterministic trudy" in policy)
        assertTrue("do not diagnose" in policy)
    }

    private fun quality(timestamp: Long, stale: Boolean) = TrudyDataQualityEvidence(
        environmentDomain, 88, 30, 3, timestamp, 2.0, stale, emptyList()
    )

    private fun metric(
        id: String,
        value: Double,
        unit: String,
        timestamp: Long,
        source: String,
        quality: TrudyDataQualityEvidence
    ) = TrudyMetricEvidence(environmentDomain, id, value, unit, timestamp, source = source, dataQuality = quality)

    private fun associationResult(
        environmentalMetricId: String,
        target: TrudyEnvironmentalTargetBinding,
        coefficient: Double
    ): TrudyAssociationResult {
        val references = listOf(
            TrudyEvidenceReference(environmentDomain, metricId = environmentalMetricId, evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION, timestampEpochMs = now - hour),
            TrudyEvidenceReference(target.domain, metricId = target.metricId, evidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION, timestampEpochMs = now - hour)
        )
        val evidence = PersonalEvidenceItem(
            id = "environment-test",
            domains = listOf(environmentDomain, target.domain),
            metricIds = listOf(environmentalMetricId, target.metricId),
            evidenceType = PersonalEvidenceType.ASSOCIATION,
            observationWindow = TrudyTimeRange(now - 7 * 24 * hour, now),
            effectDirection = TrudyEffectDirection.DECREASE,
            effectMagnitude = coefficient,
            sampleCount = 18,
            confidence = TrudyConfidence.MODERATE,
            dataQualityStatus = TrudyDataQualityStatus.GOOD,
            supportingEvidenceReferences = references
        )
        return TrudyAssociationResult(
            environmentDomain, environmentalMetricId, target.domain, target.metricId,
            TrudyAssociationMethod.SPEARMAN, coefficient, 18, 0.8, TrudyEffectDirection.DECREASE,
            TrudyConfidence.MODERATE, TrudyDataQualityStatus.GOOD, evidence = evidence
        )
    }
}
