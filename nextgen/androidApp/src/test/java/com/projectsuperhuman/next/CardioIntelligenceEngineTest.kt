package com.projectsuperhuman.next

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioIntelligenceEngineTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-20T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L

    @Test
    fun loadPrefersAdequateMeasuredZones() {
        val session = session(
            "zones",
            durationSeconds = 1200,
            zoneSeconds = mapOf(2 to 900),
            rpe = 9.0
        )
        val load = CardioTrainingLoadIntelligenceEngine.sessionLoad(CardioSessionEvidence(session))
        assertEquals(CardioIntelligenceLoadMethod.HR_ZONE_WEIGHTED, load.method)
        assertTrue(abs(requireNotNull(load.value) - 21.0) < 0.001)
        assertEquals(75.0, load.coveragePercent)
    }

    @Test
    fun lowZoneCoverageFallsBackToRpe() {
        val session = session(
            "fallback",
            durationSeconds = 1200,
            zoneSeconds = mapOf(2 to 120),
            rpe = 5.0
        )
        val load = CardioTrainingLoadIntelligenceEngine.sessionLoad(CardioSessionEvidence(session))
        assertEquals(CardioIntelligenceLoadMethod.SESSION_RPE, load.method)
        assertEquals(100.0, load.value)
    }

    @Test
    fun powerLoadRequiresMeasuredPowerAndThreshold() {
        val session = session("power", activity = CardioActivityType.CYCLING, durationSeconds = 3600)
        val samples = List(60) {
            CardioTimeSeriesSample(it * 60.0, 140.0, powerWatts = 200.0)
        }
        val load = CardioTrainingLoadIntelligenceEngine.sessionLoad(
            CardioSessionEvidence(session, samples = samples, thresholdPowerWatts = 250.0)
        )
        assertEquals(CardioIntelligenceLoadMethod.POWER_STRESS, load.method)
        assertTrue(abs(requireNotNull(load.value) - 64.0) < 0.001)
    }

    @Test
    fun mixedLoadMethodsAreNotCombinedIntoOneScale() {
        val a = CardioSessionEvidence(
            session("a", endedAt = now - day, zoneSeconds = mapOf(2 to 1500), durationSeconds = 1800)
        )
        val b = CardioSessionEvidence(
            session("b", endedAt = now, rpe = 5.0, durationSeconds = 1800)
        )
        val result = CardioTrainingLoadIntelligenceEngine.longitudinal(listOf(a, b), now, utc)
        assertTrue(result.mixedScaleWarning)
        assertEquals(2, result.methodCounts.size)
        assertNotNull(result.modelledMethod)
        assertTrue(result.points.all { it.methods.size <= 1 })
        assertTrue(result.points.flatMap { it.methods }.all { it == result.modelledMethod })
    }

    @Test
    fun comparableSessionsExposeReasonsAndVerdict() {
        val a = CardioSessionEvidence(
            session("a", distanceKm = 5.0, durationSeconds = 1800, workoutType = CardioWorkoutType.ZONE_2),
            ambientTemperatureC = 18.0
        )
        val b = CardioSessionEvidence(
            session("b", distanceKm = 5.2, durationSeconds = 1850, workoutType = CardioWorkoutType.ZONE_2),
            ambientTemperatureC = 20.0
        )
        val result = CardioComparableSessionEngine.compare(a, b)
        assertEquals(CardioComparability.COMPARABLE, result.verdict)
        assertTrue(result.score >= 0.78)
    }

    @Test
    fun differentActivitiesAreUnsuitableForDirectComparison() {
        val run = CardioSessionEvidence(session("run", activity = CardioActivityType.RUNNING))
        val ride = CardioSessionEvidence(session("ride", activity = CardioActivityType.CYCLING))
        assertEquals(
            CardioComparability.UNSUITABLE,
            CardioComparableSessionEngine.compare(run, ride).verdict
        )
    }

    @Test
    fun paceAtHeartRateUsesTimeSeriesBand() {
        val samples = List(60) {
            CardioTimeSeriesSample(it * 10.0, 150.0, speedMetersPerSecond = 3.0)
        }
        val result = CardioBandAnalytics.paceAtHeartRate(samples, 150.0)
        assertEquals(CardioAnalyticState.AVAILABLE, result.state)
        assertTrue(abs(requireNotNull(result.value) - 333.333333) < 0.01)
    }

    @Test
    fun heartRateAtPaceUsesTimeSeriesBand() {
        val samples = List(60) {
            CardioTimeSeriesSample(it * 10.0, 148.0, speedMetersPerSecond = 3.0)
        }
        val result = CardioBandAnalytics.heartRateAtPace(samples, 333.333333)
        assertEquals(CardioAnalyticState.AVAILABLE, result.state)
        assertEquals(148.0, result.value)
    }

    @Test
    fun paceAndHeartRateBandTrendsOnlyIncludeSupportedSessions() {
        val evidence = listOf(
            CardioSessionEvidence(
                session("old", endedAt = now - day),
                samples = samples(speed = 3.0, hr = 150.0)
            ),
            CardioSessionEvidence(
                session("new", endedAt = now),
                samples = samples(speed = 3.2, hr = 150.0)
            ),
            CardioSessionEvidence(session("missing", endedAt = now - 2 * day), samples = emptyList())
        )
        val paceTrend = CardioBandAnalytics.paceAtHeartRateTrend(evidence, 150.0)
        assertEquals(listOf("old", "new").size, paceTrend.size)
        assertTrue(paceTrend.first().value > paceTrend.last().value)

        val targetPace = 1000.0 / 3.0
        val hrTrend = CardioBandAnalytics.heartRateAtPaceTrend(evidence, targetPace)
        assertEquals(1, hrTrend.size)
        assertEquals(150.0, hrTrend.single().value)
    }

    @Test
    fun decouplingRejectsShortSession() {
        val samples = List(60) {
            CardioTimeSeriesSample(it * 10.0, 145.0, speedMetersPerSecond = 3.0)
        }
        assertNull(CardioDecouplingEngine.estimate(samples).percent)
    }

    @Test
    fun decouplingUsesSeparateSessionHalves() {
        val samples = List(121) { index ->
            val speed = if (index < 61) 3.2 else 2.9
            CardioTimeSeriesSample(index * 15.0, 150.0, speedMetersPerSecond = speed)
        }
        val result = CardioDecouplingEngine.estimate(samples)
        assertNotNull(result.percent)
        assertTrue(requireNotNull(result.percent) > 5.0)
        assertEquals("speed/HR", result.input)
    }

    @Test
    fun heartRateRecoveryRequiresTerminationSample() {
        val result = CardioHeartRateRecoveryEngine.estimate(
            listOf(
                CardioPostEffortHrSample(40.0, 160.0),
                CardioPostEffortHrSample(60.0, 150.0),
                CardioPostEffortHrSample(120.0, 130.0)
            )
        )
        assertNull(result.hrr1MinuteBpm)
    }

    @Test
    fun heartRateRecoveryCalculatesOneAndTwoMinuteDrop() {
        val result = CardioHeartRateRecoveryEngine.estimate(
            listOf(
                CardioPostEffortHrSample(0.0, 180.0),
                CardioPostEffortHrSample(30.0, 166.0),
                CardioPostEffortHrSample(60.0, 150.0),
                CardioPostEffortHrSample(90.0, 140.0),
                CardioPostEffortHrSample(120.0, 132.0)
            )
        )
        assertEquals(30.0, result.hrr1MinuteBpm)
        assertEquals(48.0, result.hrr2MinuteBpm)
    }

    @Test
    fun rmssdUsesGenuineRrIntervals() {
        val samples = List(120) { index ->
            rr(index * 1000L, if (index % 2 == 0) 800.0 else 820.0)
        }
        val result = CardioHrvEngine.rmssd(samples)
        assertEquals(CardioAnalyticState.AVAILABLE, result.state)
        assertTrue(abs(requireNotNull(result.rmssdMs) - 20.0) < 0.001)
        assertEquals(0, result.rejectedIntervals)
    }

    @Test
    fun rmssdRejectsSparseRrData() {
        val samples = List(5) { index -> rr(index * 1000L, 800.0) }
        assertNull(CardioHrvEngine.rmssd(samples).rmssdMs)
    }

    @Test
    fun hrvBaselineDeviationRequiresUsableFreshBaseline() {
        val history = (0 until 8).map {
            CardioTimedValue(now - (8L - it) * day, 40.0 + it)
        }
        val baseline = CardioHrvBaselineEngine.baseline(history, now)
        assertEquals(CardioAnalyticState.AVAILABLE, baseline.state)
        val deviation = CardioHrvBaselineEngine.deviationPercent(50.0, baseline)
        assertNotNull(deviation)
        assertTrue(deviation > 0.0)

        val building = CardioHrvBaselineEngine.baseline(history.take(2), now)
        assertNull(CardioHrvBaselineEngine.deviationPercent(50.0, building))
    }

    @Test
    fun cooperVo2IsExplicitlyEstimatedAndRequiresVerifiedEffort() {
        val unavailable = CardioVo2EstimateEngine.cooper12Minute(
            CardioPerformanceEffort(720.0, 2800.0, verified = false)
        )
        assertNull(unavailable.mlKgMin)

        val estimate = CardioVo2EstimateEngine.cooper12Minute(
            CardioPerformanceEffort(720.0, 2800.0, verified = true)
        )
        assertTrue(abs(requireNotNull(estimate.mlKgMin) - ((2800.0 - 504.9) / 44.73)) < 0.001)
        assertTrue(estimate.caveat.contains("ESTIMATED"))
    }

    @Test
    fun criticalSpeedUsesVerifiedPerformanceCurve() {
        val estimate = CardioCriticalSpeedEngine.estimate(
            listOf(
                CardioPerformanceEffort(300.0, 1400.0, true),
                CardioPerformanceEffort(600.0, 2600.0, true),
                CardioPerformanceEffort(900.0, 3800.0, true)
            )
        )
        assertTrue(abs(requireNotNull(estimate.criticalSpeedMetersPerSecond) - 4.0) < 0.0001)
        assertTrue(abs(requireNotNull(estimate.dPrimeMeters) - 200.0) < 0.0001)
        assertTrue(requireNotNull(estimate.rSquared) > 0.999)
    }

    @Test
    fun criticalSpeedRejectsRandomUnverifiedSessions() {
        val estimate = CardioCriticalSpeedEngine.estimate(
            listOf(
                CardioPerformanceEffort(300.0, 1400.0, false),
                CardioPerformanceEffort(600.0, 2600.0, false),
                CardioPerformanceEffort(900.0, 3800.0, false)
            )
        )
        assertNull(estimate.criticalSpeedMetersPerSecond)
    }

    @Test
    fun incompatibleHistoricalZoneProfilesStaySeparated() {
        val a = session("a", zoneSeconds = mapOf(2 to 600), zoneSchemeId = "zones-v1")
        val b = session("b", zoneSeconds = mapOf(2 to 600), zoneSchemeId = "zones-v2")
        val result = CardioZoneProfileAnalytics.distribution(listOf(a, b))
        assertFalse(result.compatibleForCombinedAnalysis)
        assertEquals(2, result.secondsByProfileAndZone.size)
    }

    @Test
    fun baselineReportsBuildingAndStaleStates() {
        val sparse = CardioPersonalBaselineStatistics.build(
            listOf(
                CardioTimedValue(now - 2 * day, 10.0),
                CardioTimedValue(now - day, 11.0)
            ),
            now
        )
        assertEquals(CardioAnalyticState.BUILDING_BASELINE, sparse.state)

        val staleValues = (0 until 8).map {
            CardioTimedValue(now - (40L + it) * day, 10.0 + it)
        }
        val stale = CardioPersonalBaselineStatistics.build(staleValues, now)
        assertEquals(CardioAnalyticState.STALE, stale.state)
        assertTrue(stale.stale)
    }

    @Test
    fun meaningfulChangeDistinguishesSignalFromOrdinaryVariation() {
        val baseline = listOf(10.0, 10.2, 9.8, 10.1, 9.9, 10.0)
        val signal = CardioMeaningfulChangeEngine.evaluate(
            baseline,
            listOf(11.0, 11.2, 11.1, 11.3, 11.0, 11.2)
        )
        assertEquals(CardioMeaningfulChangeState.LIKELY_SIGNAL, signal.state)

        val ordinary = CardioMeaningfulChangeEngine.evaluate(
            baseline,
            listOf(10.0, 10.1, 9.9, 10.0, 10.2, 9.8)
        )
        assertEquals(CardioMeaningfulChangeState.ORDINARY_VARIATION, ordinary.state)
    }

    @Test
    fun changePointRequiresSustainedRepeatedShift() {
        val values = buildList {
            repeat(8) { add(CardioTimedValue(now - (20L - it) * day, 100.0 + (it % 2))) }
            repeat(8) { add(CardioTimedValue(now - (12L - it) * day, 110.0 + (it % 2))) }
        }
        val result = CardioChangePointEngine.detect(values)
        assertEquals(CardioAnalyticState.AVAILABLE, result.state)
        assertNotNull(result.timestampEpochMs)
        assertTrue(abs(requireNotNull(result.standardizedShift)) >= 1.5)
    }

    @Test
    fun correlationSupportsLagAndSpearman() {
        val left = (0 until 12).map { CardioTimedValue(now + it * day, it.toDouble()) }
        val right = (0 until 12).map { CardioTimedValue(now + it * day + day, it.toDouble() * 2.0) }
        val result = CardioCorrelationEngine.correlate(
            left,
            right,
            method = CardioCorrelationMethod.SPEARMAN,
            lagMs = day,
            matchToleranceMs = 1_000L
        )
        assertEquals(12, result.sampleCount)
        assertTrue(requireNotNull(result.coefficient) > 0.99)
        assertTrue(result.caveat.contains("causation"))
    }

    @Test
    fun nOf1ExperimentKeepsConclusionConservative() {
        val spec = CardioNof1ExperimentSpec(
            id = "sleep-zone2",
            hypothesis = "Better sleep improves Zone 2 pace",
            intervention = "Earlier bedtime",
            outcomeMetricId = "cardio_pace_at_hr_sec_per_km",
            baselineStartEpochMs = now - 20 * day,
            baselineEndEpochMs = now - 11 * day,
            interventionStartEpochMs = now - 10 * day,
            interventionEndEpochMs = now,
            expectedDirection = -1
        )
        val observations = buildList {
            repeat(6) {
                add(CardioNof1ExperimentObservation(now - (18L - it) * day, 360.0 + it, "baseline"))
            }
            repeat(6) {
                add(CardioNof1ExperimentObservation(now - (8L - it) * day, 335.0 + it, "intervention"))
            }
        }
        val result = CardioNof1ExperimentEngine.evaluate(spec, observations)
        assertTrue(result.conclusion.contains("consistent", ignoreCase = true))
        assertTrue(result.uncertainty.contains("does not establish causation"))
    }

    @Test
    fun sportFitnessDoesNotMixActivities() {
        val baselineDates = listOf(now - 70 * day, now - 60 * day)
        val recentDates = listOf(now - 10 * day, now - 5 * day)
        val runEvidence = buildList {
            baselineDates.forEachIndexed { index, t ->
                add(
                    CardioSessionEvidence(
                        session("old" + index, endedAt = t),
                        samples = samples(speed = 3.0, hr = 150.0)
                    )
                )
            }
            recentDates.forEachIndexed { index, t ->
                add(
                    CardioSessionEvidence(
                        session("new" + index, endedAt = t),
                        samples = samples(speed = 3.3, hr = 150.0)
                    )
                )
            }
        }
        val cyclingNoise = CardioSessionEvidence(
            session("bike", activity = CardioActivityType.CYCLING, endedAt = now - day),
            samples = samples(speed = 1.0, hr = 180.0)
        )
        val result = CardioFitnessIntelligenceEngine.snapshot(
            CardioActivityType.RUNNING,
            runEvidence + cyclingNoise,
            now
        )
        assertEquals(CardioActivityType.RUNNING, result.activity)
        assertEquals(4, result.comparableSessionCount)
        assertTrue(result.signals.any { it.type == CardioFitnessSignalType.AEROBIC_EFFICIENCY && it.direction > 0 })
    }

    @Test
    fun emptyDataReturnsUnavailableRatherThanFabricatedMetrics() {
        assertNull(CardioBandAnalytics.paceAtHeartRate(emptyList(), 150.0).value)
        assertNull(CardioHrvEngine.rmssd(emptyList()).rmssdMs)
        assertTrue(CardioTrainingLoadIntelligenceEngine.longitudinal(emptyList(), now, utc).points.isEmpty())
        assertEquals(
            CardioAnalyticState.INSUFFICIENT_DATA,
            CardioZoneProfileAnalytics.distribution(emptyList()).state
        )
    }

    private fun session(
        id: String,
        activity: CardioActivityType = CardioActivityType.RUNNING,
        endedAt: Long = now,
        durationSeconds: Int = 1800,
        distanceKm: Double? = 5.0,
        rpe: Double? = null,
        workoutType: CardioWorkoutType = CardioWorkoutType.FREE,
        zoneSeconds: Map<Int, Int> = emptyMap(),
        zoneSchemeId: String? = null
    ) = CardioSession(
        id = id,
        activity = activity,
        startedAt = endedAt - durationSeconds * 1000L,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        rpe = rpe,
        workoutType = workoutType,
        zoneSeconds = zoneSeconds,
        zoneSchemeId = zoneSchemeId
    )

    private fun samples(speed: Double, hr: Double): List<CardioTimeSeriesSample> =
        List(120) { CardioTimeSeriesSample(it * 15.0, hr, speedMetersPerSecond = speed) }

    private fun rr(timestamp: Long, value: Double): CardioRrIntervalSample =
        CardioRrIntervalSample(
            timestampEpochMs = timestamp,
            rrMs = value,
            source = CardioSensorProvenance(
                providerType = CardioSensorProviderType.BLE_HEART_RATE,
                sourceName = "test",
                transport = CardioSensorTransport.LIVE_BLE
            )
        )
}
