package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioFoundationTrustTest {
    private val ble = CardioSensorProvenance(
        providerType = CardioSensorProviderType.BLE_HEART_RATE,
        sourceName = "bluetooth-sig-heart-rate",
        transport = CardioSensorTransport.LIVE_BLE,
        deviceName = "Test Strap",
        anonymousSensorId = "strap-1"
    )

    @Test
    fun provenanceRoundTripPreservesStableSourceIdentity() {
        val original = CardioObservationProvenance(
            sourceKind = CardioSourceKind.GENERIC_BLE,
            sourceName = "bluetooth-sig-heart-rate",
            deviceId = "anon-1",
            deviceName = "Chest Strap",
            providerPackage = "com.example.provider",
            externalRecordId = "record-7",
            sourceProvider = "BLE_HEART_RATE",
            sourceTransport = "LIVE_BLE",
            stableSourceId = "anon-1"
        )
        val decoded = CardioProvenanceCodec.fromMetadata(CardioProvenanceCodec.toMetadata(original))
        assertEquals(original, decoded)
    }

    @Test
    fun rawObservationPreservesOriginalAndQualityWithoutDestructiveCleaning() {
        val raw = CardioRawObservation(
            sessionId = "s1",
            metricId = "cardio_hr_sample_bpm",
            originalTimestampEpochMs = 1234L,
            originalValue = 301.0,
            canonicalValue = 301.0,
            unit = "bpm",
            ingestionTimestampEpochMs = 1400L,
            provenance = CardioProvenanceCodec.fromSensor(ble),
            quality = CardioObservationQuality.INVALID,
            exclusionReason = "Outside range"
        )
        val row = raw.toHealthValue()
        assertEquals(301.0, row.value)
        assertEquals("301.0", row.metadata["originalValue"])
        assertEquals(CardioObservationQuality.INVALID.name, row.metadata["quality"])
        assertEquals("Outside range", row.metadata["exclusionReason"])
        assertEquals("false", row.metadata["interpolated"])
        assertEquals("false", row.metadata["resampled"])
    }

    @Test
    fun liveHeartRateArbitrationPrefersDirectBleButRetainsAlternatives() {
        val delayed = candidate(
            "health-connect",
            CardioAnalysisMetric.LIVE_HEART_RATE,
            CardioSourceKind.HEALTH_CONNECT,
            CardioValueClass.MEASURED,
            coverage = 100.0
        )
        val direct = candidate(
            "direct-ble",
            CardioAnalysisMetric.LIVE_HEART_RATE,
            CardioSourceKind.GENERIC_BLE,
            CardioValueClass.MEASURED,
            coverage = 92.0
        )
        val result = assertNotNull(CardioSourceArbitrator.select(
            CardioAnalysisMetric.LIVE_HEART_RATE,
            listOf(delayed, direct)
        ))
        assertEquals("direct-ble", result.selected.candidateId)
        assertEquals(2, result.considered.size)
        assertTrue(result.reason.contains("Overlapping sources remain retained"))
    }

    @Test
    fun measuredCyclingPowerOutranksEstimatedPower() {
        val measured = candidate(
            "measured-manual-import",
            CardioAnalysisMetric.CYCLING_POWER,
            CardioSourceKind.MANUAL,
            CardioValueClass.MEASURED
        )
        val estimated = candidate(
            "estimated-wearable",
            CardioAnalysisMetric.CYCLING_POWER,
            CardioSourceKind.DIRECT_WEARABLE,
            CardioValueClass.ESTIMATED
        )
        assertEquals(
            "measured-manual-import",
            CardioSourceArbitrator.select(
                CardioAnalysisMetric.CYCLING_POWER,
                listOf(estimated, measured)
            )?.selected?.candidateId
        )
    }

    @Test
    fun eligibleUserSourceOverrideIsExplicitAndSafe() {
        val direct = candidate(
            "direct",
            CardioAnalysisMetric.WORKOUT_HEART_RATE,
            CardioSourceKind.GENERIC_BLE,
            CardioValueClass.MEASURED
        )
        val imported = candidate(
            "imported",
            CardioAnalysisMetric.WORKOUT_HEART_RATE,
            CardioSourceKind.HEALTH_CONNECT,
            CardioValueClass.MEASURED
        )
        val selection = assertNotNull(
            CardioSourceArbitrator.select(
                CardioAnalysisMetric.WORKOUT_HEART_RATE,
                listOf(direct, imported),
                userOverrideCandidateId = "imported"
            )
        )
        assertEquals("imported", selection.selected.candidateId)
        assertTrue(selection.reason.startsWith("Eligible user override selected."))
        assertEquals(
            CARDIO_SOURCE_POLICY_VERSION,
            CardioSourceArbitrator.selectionMetadata(selection)["analysisSourcePolicyVersion"]
        )
    }

    @Test
    fun unavailableSourceIsNeverSelected() {
        val unavailable = candidate(
            "missing",
            CardioAnalysisMetric.WORKOUT_HEART_RATE,
            CardioSourceKind.H19C,
            CardioValueClass.UNAVAILABLE
        )
        assertNull(CardioSourceArbitrator.select(CardioAnalysisMetric.WORKOUT_HEART_RATE, listOf(unavailable)))
    }

    @Test
    fun invalidSourceIsNeverSelected() {
        val invalid = candidate(
            "bad",
            CardioAnalysisMetric.WORKOUT_HEART_RATE,
            CardioSourceKind.H19C,
            CardioValueClass.MEASURED,
            quality = CardioObservationQuality.INVALID
        )
        assertNull(CardioSourceArbitrator.select(CardioAnalysisMetric.WORKOUT_HEART_RATE, listOf(invalid)))
    }

    @Test
    fun capabilitiesExposeReusableMissingDataChecks() {
        val caps = CardioCapabilityDetector.detect(
            metricIds = setOf("cardio_session", "cardio_hr_sample_bpm", "cardio_rr_interval_ms"),
            hasPaceSpeedTimeSeries = true
        )
        assertTrue(caps.supportsAerobicDecoupling())
        assertTrue(caps.supportsRmssd())
        assertTrue(caps.supportsPaceAtHeartRate())
        assertFalse(caps.supportsPowerAnalysis())
        assertEquals("Requires measured power", caps.unavailableReasons[CardioCapability.POWER])
    }

    @Test
    fun hrrProfileRetainsFormulaHrmaxAsEstimateAndRoundTrips() {
        val profile = CardioZoneEngine.createProfile(
            revisionId = "phys-1",
            effectiveFromEpochMs = 1_800_000_000_000L,
            hrMaxBpm = 190,
            hrMaxSource = CardioHrMaxSource.FORMULA_ESTIMATE,
            restingHrBpm = 55,
            lactateThresholdHrBpm = null,
            zoneModel = CardioZoneModel.HRR,
            sport = CardioActivityType.RUNNING,
            sportSpecificSettings = mapOf("note" to "running")
        )
        assertEquals(5, profile.zones.size)
        assertEquals("%HRR", profile.zones.first().calculationMethod)
        assertEquals(CardioHrMaxSource.FORMULA_ESTIMATE, profile.hrMaxSource)
        val restored = assertNotNull(CardioPhysiologyCodec.fromHealthValue(CardioPhysiologyCodec.toHealthValue(profile)))
        assertEquals(profile, restored)
    }

    @Test
    fun hrmaxSourceHierarchyPrefersValidatedThenManualThenFormula() {
        val selected = CardioHrMaxPolicy.select(
            listOf(
                CardioHrMaxEvidence(188, CardioHrMaxSource.FORMULA_ESTIMATE, 300L),
                CardioHrMaxEvidence(191, CardioHrMaxSource.MANUAL_CONFIRMED, 200L),
                CardioHrMaxEvidence(193, CardioHrMaxSource.VALIDATED_OBSERVED, 100L)
            )
        )
        assertEquals(193, selected?.bpm)
        assertEquals(CardioHrMaxSource.VALIDATED_OBSERVED, selected?.source)
    }

    @Test
    fun manualAndThreeZoneModelsAreSupported() {
        val manual = CardioZoneEngine.buildZones(
            CardioZoneModel.MANUAL,
            hrMaxBpm = null,
            restingHrBpm = null,
            lthrBpm = null,
            customZones = listOf(
                CardioZoneBoundary(1, "Easy", 60, 120, "Easy"),
                CardioZoneBoundary(2, "Hard", 121, 190, "Hard")
            )
        )
        val three = CardioZoneEngine.buildZones(
            CardioZoneModel.THREE_ZONE,
            hrMaxBpm = 190,
            restingHrBpm = null,
            lthrBpm = null
        )
        assertEquals(2, manual.size)
        assertEquals(3, three.size)
    }

    @Test
    fun originalZoneRevisionIsNotSilentlyReplacedByCurrentRevision() {
        val original = profile("old", 1_700_000_000_000L)
        val current = profile("new", 1_800_000_000_000L)
        val session = session().copy(physiologyRevisionId = "old", zoneSchemeId = "old-scheme")
        assertEquals(
            "old",
            CardioZoneAnalysisResolver.resolve(session, original, current, CardioZoneAnalysisMode.ORIGINAL)?.revisionId
        )
        assertEquals(
            "new",
            CardioZoneAnalysisResolver.resolve(session, original, current, CardioZoneAnalysisMode.CURRENT)?.revisionId
        )
        assertEquals("old", session.physiologyRevisionId)
    }

    @Test
    fun sustainedCredibleHrmaxCandidateRequiresConfirmation() {
        val samples = listOf(191, 192, 193, 194).mapIndexed { index, bpm ->
            CardioHeartRateSample(
                timestampEpochMs = index * 2_000L,
                bpm = bpm,
                source = ble,
                receivedAtEpochMs = index * 2_000L
            )
        }
        val candidate = assertNotNull(
            CardioHrMaxCandidateEngine.detectCandidate(
                configuredHrMax = 190,
                samples = samples,
                minSustainedDurationMs = 5_000L,
                coveragePct = 96.0
            )
        )
        assertEquals(194, candidate.candidateBpm)
        assertTrue(candidate.requiresConfirmation)
        assertEquals(96.0, candidate.coveragePct)
        assertTrue(candidate.sustainedDurationMs >= 5_000L)
    }

    @Test
    fun belowThresholdSampleBreaksHrmaxCandidateRun() {
        val samples = listOf(
            CardioHeartRateSample(0L, 191, ble),
            CardioHeartRateSample(1_000L, 192, ble),
            CardioHeartRateSample(2_000L, 180, ble),
            CardioHeartRateSample(3_000L, 193, ble),
            CardioHeartRateSample(4_000L, 194, ble)
        )
        assertNull(
            CardioHrMaxCandidateEngine.detectCandidate(
                configuredHrMax = 190,
                samples = samples,
                minConsecutiveSamples = 3,
                minSustainedDurationMs = 0L
            )
        )
    }

    @Test
    fun filteredSamplesDoNotSupportHrmaxCandidate() {
        val samples = listOf(191, 192, 193, 194).mapIndexed { index, bpm ->
            CardioHeartRateSample(index * 2_000L, bpm, ble)
        }
        val quality = samples.associate { it.timestampEpochMs to CardioObservationQuality.FILTERED }
        assertNull(
            CardioHrMaxCandidateEngine.detectCandidate(
                190,
                samples,
                minSustainedDurationMs = 0L,
                qualityByTimestampEpochMs = quality
            )
        )
    }

    @Test
    fun singleSpikeDoesNotCreateHrmaxCandidate() {
        val samples = listOf(
            CardioHeartRateSample(0L, 140, ble),
            CardioHeartRateSample(1_000L, 205, ble),
            CardioHeartRateSample(2_000L, 141, ble),
            CardioHeartRateSample(3_000L, 142, ble)
        )
        assertNull(CardioHrMaxCandidateEngine.detectCandidate(190, samples, minSustainedDurationMs = 0L))
    }

    @Test
    fun oldSessionSchemaMigratesWithoutLosingStorageIdentity() {
        val old = HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = "cardio_session",
            value = 30.0,
            unit = "min",
            timestampEpochMs = 1_800_000_000_000L,
            source = "health-connect-cardio",
            metadata = mapOf(
                "sessionId" to "legacy-1",
                "sourceRecordId" to "external-1",
                "cardioSchemaVersion" to "1",
                "activityType" to "RUNNING",
                "durationSeconds" to "1800",
                "physiologyRevisionId" to "phys-old"
            )
        )
        val migrated = cardioSessionFromValue(old)
        assertEquals(CARDIO_SESSION_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals("1", migrated.extensions["originalCardioSchemaVersion"])
        assertEquals("external-1", migrated.extensions["_storageSourceRecordId"])
        assertEquals("phys-old", migrated.physiologyRevisionId)
    }

    @Test
    fun futureSessionSchemaIsPreservedInsteadOfDowngraded() {
        val future = session().copy(schemaVersion = CARDIO_SESSION_SCHEMA_VERSION + 2)
        val result = CardioSessionSchemaMigration.migrate(future)
        assertTrue(result.futureSchema)
        assertEquals(CARDIO_SESSION_SCHEMA_VERSION + 2, result.session.schemaVersion)
    }

    @Test
    fun recomputationIsIdempotentAndAlgorithmVersionAware() {
        val v1 = fakeRecomputer("metric-x", "algo-v1")
        val context = CardioRecomputeContext(
            session = session(),
            capabilities = CardioCapabilities(setOf(CardioCapability.WORKOUT_SUMMARY)),
            sourceIds = listOf("sensor:1"),
            generatedAtEpochMs = 1_800_000_123_456L
        )
        val first = CardioRecomputationEngine(listOf(v1)).plan(context)
        val output = first.outputs.single()
        assertEquals(1_800_000_123_456L, output.metric.generatedAtEpochMs)
        val second = CardioRecomputationEngine(listOf(v1)).plan(context, setOf(output.stableKey))
        assertTrue(second.outputs.isEmpty())
        assertEquals(setOf(output.stableKey), second.alreadyPresentKeys)

        val changed = CardioRecomputationEngine(listOf(fakeRecomputer("metric-x", "algo-v2"))).plan(
            context,
            setOf(output.stableKey)
        )
        assertEquals(1, changed.outputs.size)
        assertNotEquals(output.stableKey, changed.outputs.single().stableKey)
    }

    @Test
    fun recomputationRefusesToInventMissingInputs() {
        val recomputer = object : CardioMetricRecomputer {
            override val metricId = "needs-rr"
            override val algorithmVersion = "v1"
            override val requiredCapabilities = setOf(CardioCapability.RR_INTERVALS)
            override fun compute(context: CardioRecomputeContext) = CardioDerivedMetric(
                metricId,
                42.0,
                "ms",
                CardioValueClass.DERIVED,
                CardioConfidence.MODERATE,
                algorithmVersion,
                listOf("RR intervals")
            )
        }
        val report = CardioRecomputationEngine(listOf(recomputer)).plan(
            CardioRecomputeContext(session(), CardioCapabilities(emptySet()))
        )
        assertTrue(report.outputs.isEmpty())
        assertEquals(setOf(CardioCapability.RR_INTERVALS), report.unavailableMetrics["needs-rr"])
    }

    @Test
    fun derivedEvidenceCarriesTrudyAndAlgorithmProvenance() {
        val metric = CardioDerivedMetric(
            metricId = "metric-x",
            value = 12.5,
            unit = "score",
            valueClass = CardioValueClass.DERIVED,
            confidence = CardioConfidence.HIGH,
            algorithmVersion = "algo-v7",
            requiredInputs = listOf("a", "b"),
            caveat = "test caveat",
            sourceIds = listOf("source-a"),
            generatedAtEpochMs = 1_800_000_000_100L
        )
        val row = assertNotNull(CardioDerivedEvidenceCodec.toHealthValue("s1", metric, 1_800_000_000_000L))
        assertEquals("true", row.metadata["trudyEvidence"])
        assertEquals("DERIVED", row.metadata["valueClass"])
        assertEquals("algo-v7", row.metadata["algorithmVersion"])
        assertEquals("source-a", row.metadata["sourceIds"])
        assertEquals("derived", row.metadata["provenance"])
        assertTrue(row.metadata["sourceRecordId"].orEmpty().contains("algo-v7"))
    }

    private fun candidate(
        id: String,
        metric: CardioAnalysisMetric,
        kind: CardioSourceKind,
        valueClass: CardioValueClass,
        quality: CardioObservationQuality = CardioObservationQuality.ACCEPTED,
        coverage: Double? = null
    ) = CardioSourceCandidate(
        candidateId = id,
        metric = metric,
        provenance = CardioObservationProvenance(kind, id),
        valueClass = valueClass,
        quality = quality,
        coveragePct = coverage
    )

    private fun profile(id: String, effective: Long) = CardioZoneEngine.createProfile(
        revisionId = id,
        effectiveFromEpochMs = effective,
        hrMaxBpm = 190,
        hrMaxSource = CardioHrMaxSource.MANUAL_CONFIRMED,
        restingHrBpm = 55,
        lactateThresholdHrBpm = null,
        zoneModel = CardioZoneModel.HR_MAX,
        createdAtEpochMs = effective
    )

    private fun session() = CardioSession(
        id = "session-1",
        activity = CardioActivityType.RUNNING,
        startedAt = 1_799_999_000_000L,
        endedAt = 1_800_000_000_000L,
        durationSeconds = 1_000,
        source = "test"
    )

    private fun fakeRecomputer(metric: String, version: String) = object : CardioMetricRecomputer {
        override val metricId = metric
        override val algorithmVersion = version
        override val requiredCapabilities = setOf(CardioCapability.WORKOUT_SUMMARY)
        override fun compute(context: CardioRecomputeContext) = CardioDerivedMetric(
            metricId = metricId,
            value = 5.0,
            unit = "score",
            valueClass = CardioValueClass.DERIVED,
            confidence = CardioConfidence.MODERATE,
            algorithmVersion = algorithmVersion,
            requiredInputs = listOf("cardio_session")
        )
    }
}
