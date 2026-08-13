package com.projectsuperhuman.next.emotional

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.MetricAggregation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmotionalDomainTest {
    @Test
    fun canonicalScaleIdsAndNumericOrientationAreStable() {
        val definitions = CanonicalEmotionalScaleCatalog.definitions()

        assertEquals(6, definitions.size)
        assertEquals(EmotionalMetricIds.canonical, definitions.map { it.metricId }.toSet())
        assertEquals("Happy", CanonicalEmotionalScaleCatalog.definition(EmotionalMetricIds.VALENCE)?.positivePole?.defaultLabel)
        assertEquals("Sad", CanonicalEmotionalScaleCatalog.definition(EmotionalMetricIds.VALENCE)?.negativePole?.defaultLabel)
        assertEquals(-1.0, EmotionalNumericSemantics.MIN)
        assertEquals(0.0, EmotionalNumericSemantics.NEUTRAL)
        assertEquals(1.0, EmotionalNumericSemantics.MAX)
    }

    @Test
    fun displayLabelsCanChangeWithoutChangingStoredMeaning() {
        val original = CanonicalEmotionalScaleCatalog.definition(EmotionalMetricIds.VALENCE)!!
        val relabelled = original.copy(
            positivePole = original.positivePole.copy(defaultLabel = "Upbeat"),
            negativePole = original.negativePole.copy(defaultLabel = "Low")
        )

        assertEquals(original.metricId, relabelled.metricId)
        assertEquals(original.positivePole.semanticId, relabelled.positivePole.semanticId)
        assertEquals(original.negativePole.semanticId, relabelled.negativePole.semanticId)
        assertEquals(original.semanticsVersion, relabelled.semanticsVersion)
    }

    @Test
    fun normalizedValueRejectsNonFiniteAndOutOfRangeData() {
        assertFailsWith<IllegalArgumentException> { BipolarScaleValue(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { BipolarScaleValue(Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { BipolarScaleValue(-1.01) }
        assertFailsWith<IllegalArgumentException> { BipolarScaleValue(1.01) }

        assertEquals(BipolarDirection.TOWARD_NEGATIVE_POLE, BipolarScaleValue(-0.25).direction)
        assertEquals(BipolarDirection.NEUTRAL, BipolarScaleValue(0.0).direction)
        assertEquals(BipolarDirection.TOWARD_POSITIVE_POLE, BipolarScaleValue(0.25).direction)
    }

    @Test
    fun neutralIsAnObservationAndMissingIsAbsence() {
        val entry = entryOf(
            EmotionalObservation(EmotionalMetricIds.CALMNESS, BipolarScaleValue(0.0))
        )
        val mapped = DefaultEmotionalHealthValueMapper().map(entry)

        assertEquals(1, mapped.size)
        assertEquals(0.0, mapped.single().value)
        assertTrue(BipolarScaleValue(0.0).isNeutral)

        val emptyResult = EmotionalEntryValidator.validate(entry.copy(observations = emptyList()))
        assertFalse(emptyResult.isValid)
        assertTrue(emptyResult.issues.any { it.code == EmotionalValidationCode.EMPTY_OBSERVATIONS })
    }

    @Test
    fun entryValidationRejectsBadTimestampDuplicatesAndUnknownAxes() {
        val unknown = EmotionalMetricId("emotional_future_axis")
        val entry = entryOf(
            EmotionalObservation(EmotionalMetricIds.FOCUS, BipolarScaleValue(0.5)),
            EmotionalObservation(EmotionalMetricIds.FOCUS, BipolarScaleValue(0.25)),
            EmotionalObservation(unknown, BipolarScaleValue(-0.1))
        ).copy(timestampEpochMs = 0L)

        val result = EmotionalEntryValidator.validate(entry)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == EmotionalValidationCode.INVALID_TIMESTAMP })
        assertTrue(result.issues.any { it.code == EmotionalValidationCode.DUPLICATE_METRIC && it.metricId == EmotionalMetricIds.FOCUS })
        assertTrue(result.issues.any { it.code == EmotionalValidationCode.UNKNOWN_METRIC && it.metricId == unknown })
    }

    @Test
    fun mapperPreservesCanonicalNumbersTimestampAndSourceMetadata() {
        val entry = EmotionalEntry(
            timestampEpochMs = 1_723_000_000_000L,
            source = EmotionalSourceMetadata(
                sourceId = "manual-emotional-checkin",
                type = EmotionalSourceType.SELF_REPORT,
                metadata = mapOf("device" to "phone")
            ),
            observations = listOf(
                EmotionalObservation(EmotionalMetricIds.ENERGY, BipolarScaleValue(-0.75)),
                EmotionalObservation(EmotionalMetricIds.CONNECTEDNESS, BipolarScaleValue(0.6))
            ),
            metadata = mapOf("context" to "afternoon")
        )

        val mapped = DefaultEmotionalHealthValueMapper().map(entry)

        assertEquals(2, mapped.size)
        assertTrue(mapped.all { it.domain == HealthDomain.EMOTIONAL })
        assertTrue(mapped.all { it.timestampEpochMs == entry.timestampEpochMs })
        assertTrue(mapped.all { it.source == "manual-emotional-checkin" })
        assertTrue(mapped.all { it.unit == EmotionalNumericSemantics.CANONICAL_UNIT })
        assertTrue(mapped.all { it.metadata["emotionalSourceType"] == "self_report" })
        assertTrue(mapped.all { it.metadata["emotionalSemanticsVersion"] == "1" })
        assertEquals(-0.75, mapped.single { it.metric == EmotionalMetricIds.ENERGY.value }.value)
        assertEquals(0.6, mapped.single { it.metric == EmotionalMetricIds.CONNECTEDNESS.value }.value)
    }

    @Test
    fun sourceRecordIdsAreMetricQualifiedSoMultiAxisEntriesDoNotDeduplicateTogether() {
        val entry = entryOf(
            EmotionalObservation(EmotionalMetricIds.VALENCE, BipolarScaleValue(0.3)),
            EmotionalObservation(EmotionalMetricIds.CALMNESS, BipolarScaleValue(-0.4))
        ).copy(metadata = mapOf("sourceRecordId" to "checkin-42"))

        val ids = DefaultEmotionalHealthValueMapper().map(entry).map { it.metadata.getValue("sourceRecordId") }

        assertEquals(2, ids.toSet().size)
        assertTrue(ids.contains("checkin-42:emotional_valence"))
        assertTrue(ids.contains("checkin-42:emotional_calmness"))
    }

    @Test
    fun emotionalMetricRegistryUsesAverageAndNormalizedBoundsForEveryAxis() {
        val registered = EmotionalMetricRegistry.definitions(HealthDomain.EMOTIONAL)

        assertEquals(EmotionalMetricIds.canonical.map { it.value }.toSet(), registered.map { it.id }.toSet())
        registered.forEach { definition ->
            assertEquals("score", definition.canonicalUnit)
            assertEquals(MetricAggregation.AVERAGE, definition.aggregation)
            assertEquals(-1.0, definition.minAccepted)
            assertEquals(1.0, definition.maxAccepted)
            assertFalse(definition.derived)
        }
    }

    private fun entryOf(vararg observations: EmotionalObservation): EmotionalEntry = EmotionalEntry(
        timestampEpochMs = 1_723_000_000_000L,
        source = EmotionalSourceMetadata("test-self-report", EmotionalSourceType.SELF_REPORT),
        observations = observations.toList()
    )
}
