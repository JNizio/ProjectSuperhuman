package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class CardioLiveExecutionTest {
    private val source = CardioSensorProvenance(
        providerType = CardioSensorProviderType.BLE_HEART_RATE,
        sourceName = "test-strap",
        transport = CardioSensorTransport.LIVE_BLE,
        deviceName = "Test Strap",
        anonymousSensorId = "sensor-1"
    )

    private fun hr(ms: Long, bpm: Int, receivedAt: Long = ms) =
        CardioHeartRateSample(ms, bpm, source, receivedAtEpochMs = receivedAt)

    private fun rr(ms: Long, value: Double) =
        CardioRrIntervalSample(ms, value, source, receivedAtEpochMs = ms)

    private fun fix(
        ms: Long,
        lat: Double,
        lon: Double,
        accuracy: Float = 5f,
        receivedAt: Long = ms
    ) = CardioGpsFix(
        timestampEpochMs = ms,
        latitude = lat,
        longitude = lon,
        accuracyMeters = accuracy,
        receivedAtEpochMs = receivedAt
    )

    @Test
    fun bluetoothSigPacketPreservesTrueRrIntervals() {
        val parsed = assertNotNull(
            BleHeartRateMeasurementParser.parseMeasurement(
                byteArrayOf(0x10, 60, 0x00, 0x04, 0x80.toByte(), 0x03)
            )
        )

        assertEquals(60, parsed.bpm)
        assertEquals(2, parsed.rrIntervalsMs.size)
        assertEquals(1000.0, parsed.rrIntervalsMs[0], 0.01)
        assertEquals(875.0, parsed.rrIntervalsMs[1], 0.01)
    }

    @Test
    fun bluetoothSigPacketWithoutRrDoesNotInventBeatIntervals() {
        val parsed = assertNotNull(
            BleHeartRateMeasurementParser.parseMeasurement(byteArrayOf(0x00, 72))
        )

        assertEquals(72, parsed.bpm)
        assertTrue(parsed.rrIntervalsMs.isEmpty())
    }

    @Test
    fun hrQualityClassifiesTransientSpikeGapAndStaleWithoutDeletingRawSamples() {
        val samples = listOf(
            hr(0, 80),
            hr(1_000, 155),
            hr(2_000, 82),
            hr(14_500, 84),
            hr(15_500, 86, receivedAt = 30_000)
        )
        val result = CardioHrQualityProcessor.analyse(samples)

        assertEquals(samples.size, result.points.size)
        assertEquals(CardioObservationQuality.SUSPECT_OUTLIER, result.points[1].quality)
        assertEquals(CardioObservationQuality.GAP_ADJACENT, result.points[3].quality)
        assertEquals(CardioObservationQuality.STALE, result.points[4].quality)
        assertEquals(1, result.suspectCount)
        assertEquals(1, result.gapAdjacentCount)
        assertEquals(1, result.staleCount)
    }

    @Test
    fun cadenceLockIsOnlyAQualityFlagNotAReplacementHrValue() {
        val sample = hr(10_000, 161)
        val result = CardioHrQualityProcessor.analyse(
            listOf(sample),
            listOf(CardioCadencePoint(10_000, 160.0))
        )

        assertEquals(CardioObservationQuality.SUSPECT_OUTLIER, result.points.single().quality)
        assertEquals(161, result.points.single().sample.bpm)
        assertEquals(1, result.cadenceLockSuspectCount)
    }

    @Test
    fun rrProcessorCalculatesDerivedRmssdOnlyFromAcceptedTrueRr() {
        val samples = listOf(
            rr(0, 1000.0),
            rr(1_000, 980.0),
            rr(1_980, 1010.0),
            rr(2_990, 990.0),
            rr(3_980, 1005.0),
            rr(4_985, 995.0)
        )
        val result = CardioRrProcessor.analyse(samples, activeDurationMs = 6_000)

        assertEquals(100.0, result.validBeatPercent, 0.01)
        assertNotNull(result.rmssdMs)
        assertEquals(CardioValueClass.DERIVED, assertNotNull(result.rmssdMetric).valueClass)
        assertTrue(result.rmssdMetric!!.caveat!!.contains("not diagnostic"))
    }

    @Test
    fun rrArtifactRemainsRawButIsExcludedFromDerivedQualitySet() {
        val samples = listOf(
            rr(0, 1000.0),
            rr(1_000, 980.0),
            rr(1_980, 300.0),
            rr(2_280, 990.0)
        )
        val result = CardioRrProcessor.analyse(samples, activeDurationMs = 4_000)

        assertEquals(samples.size, result.points.size)
        assertEquals(CardioObservationQuality.SUSPECT_OUTLIER, result.points[2].quality)
        assertTrue(result.validBeatPercent < 100.0)
        assertNull(result.rmssdMs)
    }

    @Test
    fun gpsRejectsWeakFixAndImpossibleJumpWithoutDestroyingRawEvidence() {
        val raw = listOf(
            fix(0, 51.5000, -0.1200),
            fix(1_000, 51.5000, -0.1199, accuracy = 120f),
            fix(2_000, 51.5100, -0.1000)
        )
        val result = CardioGpsProcessor.summarise(raw, CardioActivityType.RUNNING, 2_000)

        assertEquals(3, result.rawFixes.size)
        assertEquals(1, result.cleanedRoute.size)
        assertEquals(CardioObservationQuality.FILTERED, result.qualityPoints[1].quality)
        assertEquals(CardioObservationQuality.SUSPECT_OUTLIER, result.qualityPoints[2].quality)
        assertEquals(0.0, result.distanceMeters, 0.01)
    }

    @Test
    fun routeDistanceAndMovingTimeUseAcceptedTimeAwareSegments() {
        val raw = listOf(
            fix(0, 51.5000, -0.1200),
            fix(10_000, 51.5000, -0.1195),
            fix(20_000, 51.5000, -0.1190)
        )
        val result = CardioGpsProcessor.summarise(raw, CardioActivityType.RUNNING, 20_000)

        assertEquals(3, result.cleanedRoute.size)
        assertTrue(result.distanceMeters in 60.0..80.0)
        assertEquals(20_000L, result.movingTimeMs)
        assertNotNull(result.movingPaceSecondsPerKm)
    }

    @Test
    fun gpsGapDoesNotBridgeLostLocationButTrackingResumesAfterRecovery() {
        val raw = listOf(
            fix(0, 51.5000, -0.1200),
            fix(10_000, 51.5000, -0.1195),
            fix(60_000, 51.5000, -0.1180),
            fix(70_000, 51.5000, -0.1175)
        )
        val result = CardioGpsProcessor.summarise(raw, CardioActivityType.RUNNING, 70_000)

        assertEquals(CardioObservationQuality.GAP_ADJACENT, result.qualityPoints[2].quality)
        assertEquals(4, result.cleanedRoute.size)
        assertTrue(result.distanceMeters in 60.0..80.0)
        assertEquals(20_000L, result.movingTimeMs)
    }

    @Test
    fun autoPauseUsesDebounceAndHysteresisWithoutOscillation() {
        val engine = CardioAutoPauseEngine(
            CardioAutoPauseConfig(
                enabled = true,
                pauseBelowMps = 0.8,
                resumeAboveMps = 1.2,
                pauseDebounceMs = 5_000,
                resumeDebounceMs = 3_000
            )
        )

        assertEquals(CardioAutoPauseDecision.NONE, engine.observe(0.4, 0, manuallyPaused = false))
        assertEquals(CardioAutoPauseDecision.NONE, engine.observe(0.4, 4_000, manuallyPaused = false))
        assertEquals(CardioAutoPauseDecision.PAUSE, engine.observe(0.4, 5_000, manuallyPaused = false))
        assertTrue(engine.isAutoPaused())
        assertEquals(CardioAutoPauseDecision.NONE, engine.observe(0.7, 6_000, manuallyPaused = false))
        assertEquals(CardioAutoPauseDecision.NONE, engine.observe(1.3, 7_000, manuallyPaused = false))
        assertEquals(CardioAutoPauseDecision.RESUME, engine.observe(1.3, 10_000, manuallyPaused = false))
        assertFalse(engine.isAutoPaused())
    }

    @Test
    fun manualAndDistanceLapsRetainExactnessSemantics() {
        val tracker = CardioLiveLapTracker("session")
        tracker.start(0, autoLapMeters = 1_000.0)

        val auto = tracker.onDistance(300_000, 1_005.0)
        assertEquals(1, auto.size)
        assertTrue(auto.single().exactDistance)
        assertEquals(1_000.0, auto.single().distanceMeters, 0.01)

        val manual = assertNotNull(tracker.manualLap(360_000, 1_205.0))
        assertFalse(manual.exactDistance)
        // The auto lap closes at the exact 1,000 m boundary, so the 5 m overshoot
        // remains part of the next lap instead of being discarded.
        assertEquals(205.0, manual.distanceMeters, 0.01)
        assertEquals(2, manual.index)
    }

    @Test
    fun structuredWorkoutExpandsRepeatsAndAdvancesTimeSteps() {
        val workout = CardioStructuredWorkout(
            id = "six-by-two",
            name = "Intervals",
            nodes = listOf(
                CardioStructuredNode.Step(
                    "warm",
                    "10 min easy",
                    CardioStructuredStepKind.WARM_UP,
                    CardioStructuredTarget(CardioStructuredTargetType.TIME, durationSeconds = 10)
                ),
                CardioStructuredNode.Repeat(
                    2,
                    listOf(
                        CardioStructuredNode.Step(
                            "work",
                            "2 min hard",
                            CardioStructuredStepKind.WORK,
                            CardioStructuredTarget(CardioStructuredTargetType.TIME, durationSeconds = 2)
                        ),
                        CardioStructuredNode.Step(
                            "recover",
                            "2 min easy",
                            CardioStructuredStepKind.RECOVERY,
                            CardioStructuredTarget(CardioStructuredTargetType.TIME, durationSeconds = 2)
                        )
                    )
                )
            )
        )

        var state = CardioStructuredWorkoutEngine.start(workout, 0, 0.0)
        assertEquals(5, state.flattenedSteps.size)

        var progress = CardioStructuredWorkoutEngine.update(state, 10_000, 0.0, null, null)
        state = progress.state
        assertEquals("2 min hard", state.currentStep?.label)

        progress = CardioStructuredWorkoutEngine.update(state, 12_000, 0.0, null, null)
        assertEquals("2 min easy", progress.state.currentStep?.label)
    }

    @Test
    fun structuredHrZoneTargetReportsAdherenceWithoutPretendingToComplete() {
        val workout = CardioStructuredWorkout(
            "zone",
            "Zone target",
            listOf(
                CardioStructuredNode.Step(
                    "z3",
                    "Hold zone 3",
                    CardioStructuredStepKind.WORK,
                    CardioStructuredTarget(CardioStructuredTargetType.HR_ZONE, hrZone = 3)
                )
            )
        )
        val state = CardioStructuredWorkoutEngine.start(workout, 0, 0.0)
        val inTarget = CardioStructuredWorkoutEngine.update(state, 5_000, 0.0, 3, null)
        val outOfTarget = CardioStructuredWorkoutEngine.update(state, 6_000, 0.0, 4, null)

        assertEquals(true, inTarget.targetMet)
        assertEquals(false, outOfTarget.targetMet)
        assertFalse(inTarget.state.completed)
    }
}
