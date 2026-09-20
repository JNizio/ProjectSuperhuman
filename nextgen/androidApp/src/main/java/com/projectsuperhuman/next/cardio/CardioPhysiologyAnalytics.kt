package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

internal object CardioComparableSessionEngine {
    fun compare(a: CardioSessionEvidence, b: CardioSessionEvidence): CardioComparabilityResult {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (a.session.activity != b.session.activity) {
            return CardioComparabilityResult(
                CardioComparability.UNSUITABLE, 0.0, listOf("Different activities"), emptyList()
            )
        }

        var score = 1.0
        fun penalize(amount: Double, reason: String) {
            score = (score - amount).coerceAtLeast(0.0)
            reasons += reason
        }

        if (a.session.workoutType != b.session.workoutType) penalize(0.18, "Different workout intent")

        compareRelative(a.session.distanceKm, b.session.distanceKm)?.let { diff ->
            when {
                diff > 0.40 -> penalize(0.45, "Distance differs by more than 40%")
                diff > 0.20 -> penalize(0.18, "Distance differs by more than 20%")
            }
        }

        val durationDiff = relativeDifference(
            a.session.durationSeconds.toDouble(),
            b.session.durationSeconds.toDouble()
        )
        when {
            durationDiff > 0.50 -> penalize(0.35, "Duration differs by more than 50%")
            durationDiff > 0.25 -> penalize(0.15, "Duration differs by more than 25%")
        }

        compareRelative(a.session.elevationGainM, b.session.elevationGainM)?.let { diff ->
            when {
                diff > 0.60 -> penalize(0.30, "Elevation differs materially")
                diff > 0.30 -> penalize(0.12, "Elevation differs")
            }
        }

        val tempA = a.ambientTemperatureC
        val tempB = b.ambientTemperatureC
        if (tempA != null && tempB != null) {
            val delta = abs(tempA - tempB)
            when {
                delta > 10.0 -> penalize(0.25, "Temperature differs by more than 10 C")
                delta > 5.0 -> penalize(0.10, "Temperature differs by more than 5 C")
            }
        }

        val humidityA = a.humidityPercent
        val humidityB = b.humidityPercent
        if (humidityA != null && humidityB != null && abs(humidityA - humidityB) > 25.0) {
            penalize(0.08, "Humidity differs materially")
        }

        val targetA = a.session.extensions["targetZone"]
        val targetB = b.session.extensions["targetZone"]
        if (targetA != null && targetB != null && targetA != targetB) {
            penalize(0.18, "Different target zone")
        }

        val zoneA = a.session.zoneSchemeId
        val zoneB = b.session.zoneSchemeId
        if (zoneA != null && zoneB != null && zoneA != zoneB) {
            penalize(0.12, "Different historical zone profiles")
            warnings += "Zone-derived values should not be silently combined."
        }

        val coverageA = heartRateCoverage(a.samples)
        val coverageB = heartRateCoverage(b.samples)
        if (a.samples.isNotEmpty() && coverageA < 0.70) warnings += "First session has limited HR coverage."
        if (b.samples.isNotEmpty() && coverageB < 0.70) warnings += "Second session has limited HR coverage."

        if (a.confounders.isNotEmpty() || b.confounders.isNotEmpty()) {
            val shared = a.confounders.intersect(b.confounders)
            val unshared = (a.confounders + b.confounders) - shared
            if (unshared.isNotEmpty()) penalize(0.08, "Recorded confounders differ: " + unshared.sorted().joinToString())
        }

        val verdict = when {
            score >= 0.78 -> CardioComparability.COMPARABLE
            score >= 0.48 -> CardioComparability.PARTIALLY_COMPARABLE
            else -> CardioComparability.UNSUITABLE
        }
        if (reasons.isEmpty()) reasons += "Activity, duration, distance and available context are aligned."
        return CardioComparabilityResult(verdict, score, reasons, warnings)
    }

    private fun heartRateCoverage(samples: List<CardioTimeSeriesSample>): Double =
        if (samples.isEmpty()) 0.0 else samples.count { (it.heartRateBpm ?: 0.0) > 0.0 }.toDouble() / samples.size

    private fun compareRelative(a: Double?, b: Double?): Double? =
        if (a != null && b != null && a > 0.0 && b > 0.0) relativeDifference(a, b) else null

    private fun relativeDifference(a: Double, b: Double): Double {
        if (a <= 0.0 || b <= 0.0) return Double.POSITIVE_INFINITY
        return abs(a - b) / maxOf(a, b)
    }
}

internal object CardioBandAnalytics {
    private const val MIN_VALID_SAMPLES = 20
    private const val MIN_BAND_SAMPLES = 12

    fun paceAtHeartRate(
        samples: List<CardioTimeSeriesSample>,
        targetHeartRateBpm: Double,
        toleranceBpm: Double = 3.0
    ): CardioHrBandEstimate {
        val valid = samples.filter {
            (it.heartRateBpm ?: 0.0) > 0.0 && (it.speedMetersPerSecond ?: 0.0) > 0.0
        }
        val band = valid.filter { abs(requireNotNull(it.heartRateBpm) - targetHeartRateBpm) <= toleranceBpm }
        val coverage = percent(valid.size, samples.size)
        if (valid.size < MIN_VALID_SAMPLES || band.size < MIN_BAND_SAMPLES || coverage < 70.0) {
            return CardioHrBandEstimate(
                null, "sec/km", targetHeartRateBpm, toleranceBpm, band.size, samples.size, coverage,
                confidence(valid.size, coverage), CardioAnalyticState.INSUFFICIENT_DATA,
                "median(1000 / speed_mps) while HR is inside target band"
            )
        }
        val pace = median(band.map { 1000.0 / requireNotNull(it.speedMetersPerSecond) })
        return CardioHrBandEstimate(
            pace, "sec/km", targetHeartRateBpm, toleranceBpm, band.size, samples.size, coverage,
            confidence(band.size, coverage), CardioAnalyticState.AVAILABLE,
            "median(1000 / speed_mps) while HR is inside target band"
        )
    }

    fun heartRateAtPace(
        samples: List<CardioTimeSeriesSample>,
        targetPaceSecondsPerKm: Double,
        toleranceFraction: Double = 0.05
    ): CardioHrBandEstimate {
        if (targetPaceSecondsPerKm <= 0.0) {
            return CardioHrBandEstimate(
                null, "bpm", targetPaceSecondsPerKm, toleranceFraction, 0, samples.size, 0.0,
                CardioConfidence.INSUFFICIENT, CardioAnalyticState.REQUIRES_INPUT,
                "median(HR) while pace is within the configured pace band"
            )
        }
        val valid = samples.filter {
            (it.heartRateBpm ?: 0.0) > 0.0 && (it.speedMetersPerSecond ?: 0.0) > 0.0
        }
        val band = valid.filter {
            val pace = 1000.0 / requireNotNull(it.speedMetersPerSecond)
            abs(pace - targetPaceSecondsPerKm) / targetPaceSecondsPerKm <= toleranceFraction
        }
        val coverage = percent(valid.size, samples.size)
        val formula = "median(HR) while pace is within target +/- " + (toleranceFraction * 100).toInt() + "%"
        if (valid.size < MIN_VALID_SAMPLES || band.size < MIN_BAND_SAMPLES || coverage < 70.0) {
            return CardioHrBandEstimate(
                null, "bpm", targetPaceSecondsPerKm, toleranceFraction, band.size, samples.size, coverage,
                confidence(valid.size, coverage), CardioAnalyticState.INSUFFICIENT_DATA, formula
            )
        }
        return CardioHrBandEstimate(
            median(band.map { requireNotNull(it.heartRateBpm) }),
            "bpm", targetPaceSecondsPerKm, toleranceFraction, band.size, samples.size, coverage,
            confidence(band.size, coverage), CardioAnalyticState.AVAILABLE, formula
        )
    }

    fun paceAtHeartRateTrend(
        evidence: List<CardioSessionEvidence>,
        targetHeartRateBpm: Double,
        toleranceBpm: Double = 3.0
    ): List<CardioTimedValue> = evidence.mapNotNull { item ->
        val result = paceAtHeartRate(item.samples, targetHeartRateBpm, toleranceBpm)
        result.value?.takeIf { result.state == CardioAnalyticState.AVAILABLE }
            ?.let { CardioTimedValue(item.session.endedAt, it) }
    }.sortedBy { it.timestampEpochMs }

    fun heartRateAtPaceTrend(
        evidence: List<CardioSessionEvidence>,
        targetPaceSecondsPerKm: Double,
        toleranceFraction: Double = 0.05
    ): List<CardioTimedValue> = evidence.mapNotNull { item ->
        val result = heartRateAtPace(item.samples, targetPaceSecondsPerKm, toleranceFraction)
        result.value?.takeIf { result.state == CardioAnalyticState.AVAILABLE }
            ?.let { CardioTimedValue(item.session.endedAt, it) }
    }.sortedBy { it.timestampEpochMs }

    private fun confidence(samples: Int, coverage: Double): CardioConfidence = when {
        samples >= 120 && coverage >= 90.0 -> CardioConfidence.HIGH
        samples >= 40 && coverage >= 80.0 -> CardioConfidence.MODERATE
        samples >= MIN_BAND_SAMPLES && coverage >= 70.0 -> CardioConfidence.LOW
        else -> CardioConfidence.INSUFFICIENT
    }
}

internal object CardioAerobicEfficiencyEngine {
    fun estimate(activity: CardioActivityType, samples: List<CardioTimeSeriesSample>): CardioEfficiencyEstimate {
        if (samples.isEmpty()) {
            return CardioEfficiencyEstimate(
                null, "", "unavailable", 0, 0.0, CardioConfidence.INSUFFICIENT,
                CardioAnalyticState.REQUIRES_INPUT, "output / heart rate"
            )
        }

        val powerCoverage = samples.count {
            (it.heartRateBpm ?: 0.0) > 0.0 && (it.powerWatts ?: 0.0) > 0.0
        }.toDouble() / samples.size
        val speedCoverage = samples.count {
            (it.heartRateBpm ?: 0.0) > 0.0 && (it.speedMetersPerSecond ?: 0.0) > 0.0
        }.toDouble() / samples.size

        val preferPower = activity == CardioActivityType.CYCLING || activity == CardioActivityType.STATIONARY_BIKE
        val usePower = preferPower && powerCoverage >= 0.70
        val useSpeed = !usePower && speedCoverage >= 0.70

        if (!usePower && !useSpeed) {
            return CardioEfficiencyEstimate(
                null, "", "unavailable", 0, maxOf(powerCoverage, speedCoverage) * 100.0,
                CardioConfidence.INSUFFICIENT, CardioAnalyticState.INSUFFICIENT_DATA,
                "output / heart rate"
            )
        }

        val usable = samples.filter {
            val hr = it.heartRateBpm
            val output = if (usePower) it.powerWatts else it.speedMetersPerSecond
            hr != null && hr > 0.0 && output != null && output > 0.0
        }
        val values = usable.map {
            val output = if (usePower) requireNotNull(it.powerWatts) else requireNotNull(it.speedMetersPerSecond)
            output / requireNotNull(it.heartRateBpm)
        }
        val coverage = percent(usable.size, samples.size)
        return CardioEfficiencyEstimate(
            median(values),
            if (usePower) "W/bpm" else "m/s/bpm",
            if (usePower) "measured power / HR" else "speed / HR",
            usable.size,
            coverage,
            sampleConfidence(usable.size, coverage),
            CardioAnalyticState.AVAILABLE,
            "median(output / heartRate)"
        )
    }
}

internal object CardioDecouplingEngine {
    fun estimate(samples: List<CardioTimeSeriesSample>): CardioDecouplingEstimate {
        if (samples.size < 20) return unavailable("Requires at least 20 samples.")
        val sorted = samples.sortedBy { it.elapsedSeconds }
        val duration = sorted.last().elapsedSeconds - sorted.first().elapsedSeconds
        if (duration < 20.0 * 60.0) return unavailable("Requires at least 20 minutes of sampled exercise.")

        val powerCoverage = sorted.count { (it.heartRateBpm ?: 0.0) > 0.0 && (it.powerWatts ?: 0.0) > 0.0 }.toDouble() / sorted.size
        val speedCoverage = sorted.count { (it.heartRateBpm ?: 0.0) > 0.0 && (it.speedMetersPerSecond ?: 0.0) > 0.0 }.toDouble() / sorted.size
        val usePower = powerCoverage >= 0.80
        val useSpeed = !usePower && speedCoverage >= 0.80
        if (!usePower && !useSpeed) return unavailable("Requires at least 80% HR plus output coverage.")

        val valid = sorted.filter {
            val hr = it.heartRateBpm
            val output = if (usePower) it.powerWatts else it.speedMetersPerSecond
            hr != null && hr > 0.0 && output != null && output > 0.0
        }
        val coverage = percent(valid.size, sorted.size)
        val start = valid.first().elapsedSeconds
        val end = valid.last().elapsedSeconds
        val trimmedStart = start + (end - start) * 0.10
        val trimmedEnd = start + (end - start) * 0.90
        val trimmed = valid.filter { it.elapsedSeconds in trimmedStart..trimmedEnd }
        val midpoint = (trimmedStart + trimmedEnd) / 2.0
        val first = trimmed.filter { it.elapsedSeconds <= midpoint }
        val second = trimmed.filter { it.elapsedSeconds > midpoint }
        if (first.size < 8 || second.size < 8) return unavailable("Stable first and second halves need at least 8 usable samples each.")

        fun ef(part: List<CardioTimeSeriesSample>): Double = median(part.map {
            val output = if (usePower) requireNotNull(it.powerWatts) else requireNotNull(it.speedMetersPerSecond)
            output / requireNotNull(it.heartRateBpm)
        })
        val firstEf = ef(first)
        val secondEf = ef(second)
        if (firstEf <= 0.0) return unavailable("Invalid first-half efficiency.")
        val decoupling = 100.0 * (firstEf - secondEf) / firstEf
        return CardioDecouplingEstimate(
            decoupling,
            if (usePower) "power/HR" else "speed/HR",
            trimmed.size,
            coverage,
            sampleConfidence(trimmed.size, coverage),
            CardioAnalyticState.AVAILABLE,
            "Derived training metric; interpret within comparable steady sessions, not as a diagnosis."
        )
    }

    private fun unavailable(reason: String) = CardioDecouplingEstimate(
        null, null, 0, 0.0, CardioConfidence.INSUFFICIENT,
        CardioAnalyticState.INSUFFICIENT_DATA, reason
    )
}

internal object CardioHeartRateRecoveryEngine {
    fun estimate(samples: List<CardioPostEffortHrSample>): CardioHeartRateRecoveryEstimate {
        val clean = samples.filter {
            it.secondsAfterExerciseEnd in -5.0..180.0 && it.bpm in 20.0..260.0
        }.sortedBy { it.secondsAfterExerciseEnd }
        if (clean.size < 3) return unavailable(clean.size, "Requires HR samples at exercise end and during recovery.")

        val end = nearest(clean, 0.0, 12.0)
            ?: return unavailable(clean.size, "No valid HR sample close enough to exercise termination.")
        val one = nearest(clean, 60.0, 15.0)
            ?: return unavailable(clean.size, "No valid HR sample near 60 seconds post-effort.")
        val two = nearest(clean, 120.0, 20.0)
        val confidence = when {
            two != null && clean.size >= 8 -> CardioConfidence.HIGH
            clean.size >= 5 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioHeartRateRecoveryEstimate(
            end.bpm,
            end.bpm - one.bpm,
            two?.let { end.bpm - it.bpm },
            clean.size,
            confidence,
            CardioAnalyticState.AVAILABLE,
            "Derived from observed HR at workout termination and nearest valid post-effort samples; not diagnostic."
        )
    }

    private fun nearest(samples: List<CardioPostEffortHrSample>, target: Double, tolerance: Double): CardioPostEffortHrSample? =
        samples.minByOrNull { abs(it.secondsAfterExerciseEnd - target) }
            ?.takeIf { abs(it.secondsAfterExerciseEnd - target) <= tolerance }

    private fun unavailable(count: Int, reason: String) = CardioHeartRateRecoveryEstimate(
        null, null, null, count, CardioConfidence.INSUFFICIENT,
        CardioAnalyticState.INSUFFICIENT_DATA, reason
    )
}

internal object CardioHrvEngine {
    fun rmssd(samples: List<CardioRrIntervalSample>): CardioHrvEstimate {
        if (samples.isEmpty()) return unavailable(0, 0, 0, 0.0, "Requires genuine RR intervals.")
        val sorted = samples.sortedBy { it.timestampEpochMs }
        val usable = sorted.filter {
            it.isPhysiologicallyStorable &&
                it.quality != CardioObservationQuality.INVALID &&
                it.quality != CardioObservationQuality.FILTERED
        }
        val rejected = sorted.size - usable.size
        val corrected = usable.count { it.correctionApplied || it.quality == CardioObservationQuality.INTERPOLATED }
        val coverage = percent(usable.size, sorted.size)
        if (usable.size < 20 || coverage < 70.0) {
            return unavailable(usable.size, corrected, rejected, coverage, "Requires at least 20 valid RR intervals and 70% usable coverage.")
        }

        val diffs = usable.zipWithNext().map { (a, b) -> b.rrMs - a.rrMs }
        if (diffs.isEmpty()) return unavailable(usable.size, corrected, rejected, coverage, "Not enough successive RR intervals.")
        val rmssd = sqrt(diffs.sumOf { it * it } / diffs.size)
        val confidence = when {
            usable.size >= 300 && coverage >= 95.0 && corrected.toDouble() / usable.size <= 0.05 -> CardioConfidence.HIGH
            usable.size >= 100 && coverage >= 85.0 && corrected.toDouble() / usable.size <= 0.10 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioHrvEstimate(
            rmssd, usable.size, corrected, rejected, coverage, confidence, CardioAnalyticState.AVAILABLE,
            "Invalid/filtered intervals excluded; explicitly corrected/interpolated intervals retained and counted."
        )
    }

    private fun unavailable(valid: Int, corrected: Int, rejected: Int, coverage: Double, reason: String) =
        CardioHrvEstimate(
            null, valid, corrected, rejected, coverage, CardioConfidence.INSUFFICIENT,
            CardioAnalyticState.INSUFFICIENT_DATA, reason
        )
}

internal object CardioHrvBaselineEngine {
    fun baseline(
        estimates: List<CardioTimedValue>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): CardioBaselineStats = CardioPersonalBaselineStatistics.build(
        values = estimates,
        nowEpochMs = nowEpochMs,
        minimumSamples = 5,
        staleAfterDays = 21
    )

    fun deviationPercent(currentRmssdMs: Double?, baseline: CardioBaselineStats): Double? {
        val current = currentRmssdMs?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val reference = baseline.median?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (baseline.state != CardioAnalyticState.AVAILABLE) return null
        return (current - reference) / reference * 100.0
    }
}

internal object CardioVo2EstimateEngine {
    fun cooper12Minute(effort: CardioPerformanceEffort): CardioVo2Estimate {
        val durationValid = effort.durationSeconds in 690.0..750.0
        if (!effort.verified || !durationValid || effort.distanceMeters < 500.0) {
            return CardioVo2Estimate(
                null, CardioVo2Method.COOPER_12_MINUTE, CardioConfidence.INSUFFICIENT,
                CardioAnalyticState.INSUFFICIENT_DATA, "(distance_m - 504.9) / 44.73",
                listOf("Near-12-minute maximal field effort", "Verified distance"),
                "ESTIMATED, not measured. Requires a verified near-12-minute field-test effort."
            )
        }
        val estimate = (effort.distanceMeters - 504.9) / 44.73
        if (!estimate.isFinite() || estimate <= 0.0) {
            return CardioVo2Estimate(
                null, CardioVo2Method.COOPER_12_MINUTE, CardioConfidence.INSUFFICIENT,
                CardioAnalyticState.INSUFFICIENT_DATA, "(distance_m - 504.9) / 44.73",
                listOf("Near-12-minute maximal field effort", "Verified distance"),
                "Inputs produced an invalid estimate."
            )
        }
        return CardioVo2Estimate(
            estimate, CardioVo2Method.COOPER_12_MINUTE, CardioConfidence.MODERATE,
            CardioAnalyticState.AVAILABLE, "(distance_m - 504.9) / 44.73",
            listOf("Near-12-minute maximal field effort", "Verified distance"),
            "ESTIMATED from field performance, not laboratory gas analysis."
        )
    }
}

internal object CardioCriticalSpeedEngine {
    fun estimate(efforts: List<CardioPerformanceEffort>): CardioCriticalSpeedEstimate {
        val valid = efforts.filter {
            it.verified && it.durationSeconds in 120.0..1_800.0 && it.distanceMeters > 0.0
        }
        if (valid.size < 3 || valid.map { it.durationSeconds.toInt() }.distinct().size < 3) {
            return unavailable(valid.size, "Requires at least 3 verified maximal efforts at meaningfully different durations.")
        }

        val xs = valid.map { it.durationSeconds }
        val ys = valid.map { it.distanceMeters }
        val xMean = xs.average()
        val yMean = ys.average()
        val denominator = xs.sumOf { (it - xMean).pow(2) }
        if (denominator <= 0.0) return unavailable(valid.size, "Effort durations do not provide a solvable performance curve.")
        val slope = xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) } / denominator
        val intercept = yMean - slope * xMean
        if (slope <= 0.0 || intercept < 0.0) return unavailable(valid.size, "Verified efforts do not fit a plausible two-parameter critical-speed model.")

        val fitted = xs.map { slope * it + intercept }
        val ssRes = ys.indices.sumOf { (ys[it] - fitted[it]).pow(2) }
        val ssTot = ys.sumOf { (it - yMean).pow(2) }
        val r2 = if (ssTot > 0.0) 1.0 - ssRes / ssTot else 1.0
        val confidence = when {
            valid.size >= 5 && r2 >= 0.98 -> CardioConfidence.HIGH
            r2 >= 0.95 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioCriticalSpeedEstimate(
            slope, intercept, r2, valid.size, confidence, CardioAnalyticState.AVAILABLE,
            caveat = "Performance model from verified efforts; do not fit random easy sessions."
        )
    }

    private fun unavailable(count: Int, reason: String) = CardioCriticalSpeedEstimate(
        null, null, null, count, CardioConfidence.INSUFFICIENT,
        CardioAnalyticState.INSUFFICIENT_DATA, caveat = reason
    )
}

internal object CardioZoneProfileAnalytics {
    fun distribution(sessions: List<CardioSession>): CardioZoneDistributionByProfile {
        if (sessions.isEmpty()) {
            return CardioZoneDistributionByProfile(
                emptyMap(), 0, true, CardioAnalyticState.INSUFFICIENT_DATA, "No sessions."
            )
        }
        val byProfile = linkedMapOf<String, MutableMap<Int, Int>>()
        var unclassified = 0
        sessions.forEach { session ->
            val profile = session.zoneSchemeId ?: "unknown"
            val zoneMap = byProfile.getOrPut(profile) { mutableMapOf() }
            val measured = session.zoneSeconds.filterKeys { it in 1..5 }
                .mapValues { it.value.coerceAtLeast(0) }
            measured.forEach { (zone, seconds) -> zoneMap[zone] = (zoneMap[zone] ?: 0) + seconds }
            val measuredTotal = measured.values.sum().coerceIn(0, session.durationSeconds.coerceAtLeast(0))
            unclassified += (session.durationSeconds.coerceAtLeast(0) - measuredTotal).coerceAtLeast(0)
        }
        val explicitProfiles = byProfile.keys.filter { it != "unknown" }
        val compatible = explicitProfiles.distinct().size <= 1
        return CardioZoneDistributionByProfile(
            byProfile.mapValues { (_, zones) -> (1..5).associateWith { zones[it] ?: 0 } },
            unclassified,
            compatible,
            if (compatible) CardioAnalyticState.AVAILABLE else CardioAnalyticState.INCOMPATIBLE,
            if (compatible) {
                "Original historical zone classifications retained."
            } else {
                "Multiple zone-profile versions detected; keep original-zone analysis separate unless explicitly recomputed."
            }
        )
    }

    fun threeZone(secondsByFiveZone: Map<Int, Int>): Map<Int, Int> = mapOf(
        1 to ((secondsByFiveZone[1] ?: 0) + (secondsByFiveZone[2] ?: 0)),
        2 to (secondsByFiveZone[3] ?: 0),
        3 to ((secondsByFiveZone[4] ?: 0) + (secondsByFiveZone[5] ?: 0))
    )
}

internal fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}

internal fun percent(numerator: Int, denominator: Int): Double =
    if (denominator > 0) numerator * 100.0 / denominator else 0.0

internal fun sampleConfidence(samples: Int, coveragePercent: Double): CardioConfidence = when {
    samples >= 120 && coveragePercent >= 90.0 -> CardioConfidence.HIGH
    samples >= 40 && coveragePercent >= 80.0 -> CardioConfidence.MODERATE
    samples >= 20 && coveragePercent >= 70.0 -> CardioConfidence.LOW
    else -> CardioConfidence.INSUFFICIENT
}
