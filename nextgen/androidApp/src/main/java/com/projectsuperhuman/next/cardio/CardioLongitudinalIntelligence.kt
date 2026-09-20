package com.projectsuperhuman.next

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

internal object CardioTrainingLoadIntelligenceEngine {
    private val zoneWeights = mapOf(1 to 1.0, 2 to 1.4, 3 to 2.0, 4 to 3.0, 5 to 4.0)
    private const val MIN_COVERAGE_PERCENT = 70.0
    private const val CHRONIC_DAYS = 42.0
    private const val ACUTE_DAYS = 7.0

    fun sessionLoad(evidence: CardioSessionEvidence): CardioSessionLoadEstimate {
        val session = evidence.session
        val durationSeconds = session.durationSeconds.coerceAtLeast(0)
        if (durationSeconds <= 0) return unavailable(session.id, "Session duration is unavailable.")

        val zoneSeconds = session.zoneSeconds.filterKeys { it in 1..5 }
            .mapValues { it.value.coerceAtLeast(0) }
        val classifiedSeconds = zoneSeconds.values.sum().coerceIn(0, durationSeconds)
        val zoneCoverage = 100.0 * classifiedSeconds / durationSeconds

        if (classifiedSeconds > 0 && zoneCoverage >= MIN_COVERAGE_PERCENT) {
            val score = zoneSeconds.entries.sumOf { (zone, seconds) ->
                (seconds / 60.0) * (zoneWeights[zone] ?: 0.0)
            }
            return CardioSessionLoadEstimate(
                session.id,
                score,
                CardioIntelligenceLoadMethod.HR_ZONE_WEIGHTED,
                coverageConfidence(zoneCoverage),
                zoneCoverage,
                "sum(zone_minutes * zone_weight), weights Z1..Z5 = 1.0,1.4,2.0,3.0,4.0",
                caveat = "This scale is method-specific and must not be treated as identical to power stress or session-RPE."
            )
        }

        val threshold = evidence.thresholdPowerWatts?.takeIf { it > 0.0 }
        if (threshold != null && evidence.samples.isNotEmpty()) {
            val powerSamples = evidence.samples.mapNotNull { it.powerWatts?.takeIf { watts -> watts > 0.0 } }
            val powerCoverage = percent(powerSamples.size, evidence.samples.size)
            if (powerCoverage >= MIN_COVERAGE_PERCENT && powerSamples.size >= 20) {
                val meanPower = powerSamples.average()
                val intensityFactor = meanPower / threshold
                val hours = durationSeconds / 3600.0
                val score = hours * intensityFactor.pow(2) * 100.0
                return CardioSessionLoadEstimate(
                    session.id,
                    score,
                    CardioIntelligenceLoadMethod.POWER_STRESS,
                    coverageConfidence(powerCoverage),
                    powerCoverage,
                    "duration_hours * (mean_power / threshold_power)^2 * 100",
                    caveat = "Uses measured power and configured threshold power. It is a transparent power-stress estimate, not interchangeable with HR-zone or RPE load."
                )
            }
        }

        val rpe = session.rpe?.takeIf { it > 0.0 }
        if (rpe != null) {
            return CardioSessionLoadEstimate(
                session.id,
                (durationSeconds / 60.0) * rpe,
                CardioIntelligenceLoadMethod.SESSION_RPE,
                CardioConfidence.LOW,
                100.0,
                "session_RPE * duration_minutes",
                caveat = "Self-reported load scale; keep separate from sensor-derived load methods."
            )
        }

        return unavailable(
            session.id,
            when {
                classifiedSeconds > 0 -> "HR-zone coverage is below 70%; no valid power-threshold or session-RPE fallback exists."
                else -> "Requires adequate measured HR-zone coverage, measured power with threshold power, or session RPE."
            }
        )
    }

    fun longitudinal(
        evidence: List<CardioSessionEvidence>,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioTrainingLoadSeries {
        if (evidence.isEmpty()) {
            return CardioTrainingLoadSeries(
                points = emptyList(),
                methodCounts = emptyMap(),
                scoredSessionFraction = 0.0,
                mixedScaleWarning = false,
                modelledMethod = null
            )
        }

        val estimates = evidence.associate { it.session.id to sessionLoad(it) }
        val scored = estimates.values.filter { it.value != null && it.method != CardioIntelligenceLoadMethod.UNAVAILABLE }
        val methodCounts = scored.groupingBy { it.method }.eachCount()
        if (scored.isEmpty()) {
            return CardioTrainingLoadSeries(
                points = emptyList(),
                methodCounts = methodCounts,
                scoredSessionFraction = 0.0,
                mixedScaleWarning = false,
                modelledMethod = null
            )
        }

        val modelledMethod = methodCounts.entries
            .sortedWith(compareByDescending<Map.Entry<CardioIntelligenceLoadMethod, Int>> { it.value }
                .thenBy { methodPriority(it.key) })
            .first().key
        val mixed = methodCounts.keys.size > 1

        val eligible = evidence.filter {
            val estimate = estimates[it.session.id]
            estimate?.method == modelledMethod && estimate.value != null
        }
        if (eligible.isEmpty()) {
            return CardioTrainingLoadSeries(
                points = emptyList(),
                methodCounts = methodCounts,
                scoredSessionFraction = scored.size.toDouble() / evidence.size,
                mixedScaleWarning = mixed,
                modelledMethod = modelledMethod
            )
        }

        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        var day = eligible.minOf {
            Instant.ofEpochMilli(it.session.endedAt).atZone(zoneId).toLocalDate()
        }
        val byDay = eligible.groupBy {
            Instant.ofEpochMilli(it.session.endedAt).atZone(zoneId).toLocalDate()
        }

        var chronic = 0.0
        var acute = 0.0
        val chronicAlpha = 1.0 - exp(-1.0 / CHRONIC_DAYS)
        val acuteAlpha = 1.0 - exp(-1.0 / ACUTE_DAYS)
        val points = mutableListOf<CardioLongitudinalLoadPoint>()

        while (!day.isAfter(today)) {
            val dayEvidence = byDay[day].orEmpty()
            val dayScores = dayEvidence.mapNotNull { estimates[it.session.id]?.value }
            val dayLoad = dayScores.sum()
            val priorChronic = chronic
            val priorAcute = acute
            chronic = priorChronic + chronicAlpha * (dayLoad - priorChronic)
            acute = priorAcute + acuteAlpha * (dayLoad - priorAcute)
            val confidence = if (mixed) {
                CardioConfidence.LOW
            } else {
                when {
                    dayEvidence.isNotEmpty() && dayEvidence.all {
                        estimates[it.session.id]?.confidence == CardioConfidence.HIGH
                    } -> CardioConfidence.HIGH
                    dayEvidence.isNotEmpty() -> CardioConfidence.MODERATE
                    else -> CardioConfidence.LOW
                }
            }
            points += CardioLongitudinalLoadPoint(
                day.toEpochDay(),
                dayLoad,
                chronic,
                acute,
                priorChronic - priorAcute,
                dayScores.size,
                evidence.count {
                    Instant.ofEpochMilli(it.session.endedAt).atZone(zoneId).toLocalDate() == day
                },
                if (dayEvidence.isEmpty()) emptySet() else setOf(modelledMethod),
                confidence
            )
            day = day.plusDays(1)
        }

        return CardioTrainingLoadSeries(
            points = points,
            methodCounts = methodCounts,
            scoredSessionFraction = scored.size.toDouble() / evidence.size,
            mixedScaleWarning = mixed,
            modelledMethod = modelledMethod
        )
    }

    private fun coverageConfidence(coverage: Double): CardioConfidence = when {
        coverage >= 95.0 -> CardioConfidence.HIGH
        coverage >= 85.0 -> CardioConfidence.MODERATE
        coverage >= MIN_COVERAGE_PERCENT -> CardioConfidence.LOW
        else -> CardioConfidence.INSUFFICIENT
    }

    private fun unavailable(sessionId: String, caveat: String) = CardioSessionLoadEstimate(
        sessionId,
        null,
        CardioIntelligenceLoadMethod.UNAVAILABLE,
        CardioConfidence.INSUFFICIENT,
        0.0,
        "unavailable",
        caveat = caveat
    )

    private fun methodPriority(method: CardioIntelligenceLoadMethod): Int = when (method) {
        CardioIntelligenceLoadMethod.HR_ZONE_WEIGHTED -> 0
        CardioIntelligenceLoadMethod.POWER_STRESS -> 1
        CardioIntelligenceLoadMethod.SESSION_RPE -> 2
        CardioIntelligenceLoadMethod.UNAVAILABLE -> 3
    }
}

internal object CardioPersonalBaselineStatistics {
    fun build(
        values: List<CardioTimedValue>,
        nowEpochMs: Long = System.currentTimeMillis(),
        minimumSamples: Int = 5,
        staleAfterDays: Int = 21
    ): CardioBaselineStats {
        val clean = values.filter { it.value.isFinite() }.sortedBy { it.timestampEpochMs }
        if (clean.size < minimumSamples) {
            return CardioBaselineStats(
                clean.takeIf { it.isNotEmpty() }?.let { median(it.map(CardioTimedValue::value)) },
                clean.takeIf { it.isNotEmpty() }?.map(CardioTimedValue::value)?.average(),
                null,
                null,
                clean.size,
                clean.firstOrNull()?.timestampEpochMs,
                clean.lastOrNull()?.timestampEpochMs,
                CardioConfidence.INSUFFICIENT,
                CardioAnalyticState.BUILDING_BASELINE,
                false
            )
        }
        val raw = clean.map(CardioTimedValue::value)
        val med = median(raw)
        val mad = median(raw.map { abs(it - med) })
        val robustSd = 1.4826 * mad
        val last = requireNotNull(clean.lastOrNull()).timestampEpochMs
        val stale = nowEpochMs - last > staleAfterDays * 86_400_000L
        val confidence = when {
            stale -> CardioConfidence.LOW
            clean.size >= 20 -> CardioConfidence.HIGH
            clean.size >= 10 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioBaselineStats(
            med,
            raw.average(),
            mad,
            robustSd,
            clean.size,
            clean.first().timestampEpochMs,
            last,
            confidence,
            if (stale) CardioAnalyticState.STALE else CardioAnalyticState.AVAILABLE,
            stale
        )
    }
}

internal object CardioMeaningfulChangeEngine {
    fun evaluate(
        baseline: List<Double>,
        recent: List<Double>,
        confounders: Set<String> = emptySet()
    ): CardioMeaningfulChange {
        val base = baseline.filter(Double::isFinite)
        val obs = recent.filter(Double::isFinite)
        if (base.size < 4 || obs.size < 4) {
            return CardioMeaningfulChange(
                CardioMeaningfulChangeState.INSUFFICIENT_EVIDENCE,
                null, null, null, base.size, obs.size, null,
                CardioConfidence.INSUFFICIENT,
                listOf("Requires at least 4 baseline and 4 recent observations.")
            )
        }

        val baseMedian = median(base)
        val recentMedian = median(obs)
        val change = recentMedian - baseMedian
        val percent = if (abs(baseMedian) > 1e-9) change / abs(baseMedian) * 100.0 else null
        val mad = median(base.map { abs(it - baseMedian) })
        val robustSd = 1.4826 * mad
        val effect = if (robustSd > 1e-9) change / robustSd else null
        val fraction = when {
            change > 0.0 -> obs.count { it > baseMedian }.toDouble() / obs.size
            change < 0.0 -> obs.count { it < baseMedian }.toDouble() / obs.size
            else -> 0.5
        }

        val signal = effect != null && abs(effect) >= 0.8 && fraction >= 0.70
        val ordinary = effect != null && abs(effect) < 0.5
        val state = when {
            signal -> CardioMeaningfulChangeState.LIKELY_SIGNAL
            ordinary -> CardioMeaningfulChangeState.ORDINARY_VARIATION
            else -> CardioMeaningfulChangeState.INSUFFICIENT_EVIDENCE
        }
        var confidence = when {
            state == CardioMeaningfulChangeState.LIKELY_SIGNAL && base.size >= 10 && obs.size >= 6 -> CardioConfidence.HIGH
            state == CardioMeaningfulChangeState.LIKELY_SIGNAL -> CardioConfidence.MODERATE
            state == CardioMeaningfulChangeState.ORDINARY_VARIATION -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        val caveats = mutableListOf<String>()
        if (robustSd <= 1e-9) caveats += "Baseline variability is too small to standardize reliably."
        if (confounders.isNotEmpty()) {
            caveats += "Recorded confounders: " + confounders.sorted().joinToString()
            if (confidence == CardioConfidence.HIGH) confidence = CardioConfidence.MODERATE
        }
        caveats += "This is a conservative personal change signal, not a claim of statistical or medical certainty."
        return CardioMeaningfulChange(
            state, change, percent, effect, base.size, obs.size, fraction, confidence, caveats
        )
    }
}

internal object CardioChangePointEngine {
    fun detect(values: List<CardioTimedValue>, minimumSideSamples: Int = 5): CardioChangePoint {
        val clean = values.filter { it.value.isFinite() }.sortedBy { it.timestampEpochMs }
        if (clean.size < minimumSideSamples * 2 + 2) return unavailable(clean.size, minimumSideSamples)

        var bestIndex = -1
        var bestEffect = 0.0
        var bestBefore = 0.0
        var bestAfter = 0.0

        for (index in minimumSideSamples..clean.size - minimumSideSamples) {
            val before = clean.subList(0, index).map(CardioTimedValue::value)
            val after = clean.subList(index, clean.size).map(CardioTimedValue::value)
            val beforeMedian = median(before)
            val afterMedian = median(after)
            val beforeMad = median(before.map { abs(it - beforeMedian) })
            val afterMad = median(after.map { abs(it - afterMedian) })
            val withinScale = maxOf(1.4826 * beforeMad, 1.4826 * afterMad, 1e-9)
            val effect = (afterMedian - beforeMedian) / withinScale
            if (abs(effect) > abs(bestEffect)) {
                bestEffect = effect
                bestIndex = index
                bestBefore = beforeMedian
                bestAfter = afterMedian
            }
        }

        if (bestIndex < 0 || abs(bestEffect) < 1.5) {
            return CardioChangePoint(
                null, null, null, bestEffect.takeIf { bestIndex >= 0 },
                if (bestIndex >= 0) bestIndex else 0,
                if (bestIndex >= 0) clean.size - bestIndex else 0,
                CardioConfidence.LOW,
                CardioAnalyticState.INSUFFICIENT_DATA,
                "No conservative sustained shift met the 1.5 robust-SD threshold."
            )
        }

        val after = clean.subList(bestIndex, clean.size)
        val direction = if (bestEffect > 0.0) 1 else -1
        val persistence = after.takeLast(minOf(5, after.size)).count {
            if (direction > 0) it.value > bestBefore else it.value < bestBefore
        }.toDouble() / minOf(5, after.size)
        if (persistence < 0.80) {
            return CardioChangePoint(
                null, bestBefore, bestAfter, bestEffect, bestIndex, clean.size - bestIndex,
                CardioConfidence.LOW, CardioAnalyticState.INSUFFICIENT_DATA,
                "Candidate shift was not persistent enough in the most recent observations."
            )
        }
        val confidence = when {
            abs(bestEffect) >= 2.5 && clean.size >= 20 -> CardioConfidence.HIGH
            abs(bestEffect) >= 2.0 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioChangePoint(
            clean[bestIndex].timestampEpochMs,
            bestBefore,
            bestAfter,
            bestEffect,
            bestIndex,
            clean.size - bestIndex,
            confidence,
            CardioAnalyticState.AVAILABLE,
            "Analytical cue for a sustained shift; not a medical diagnosis."
        )
    }

    private fun unavailable(size: Int, minimumSideSamples: Int) = CardioChangePoint(
        null, null, null, null, minOf(size, minimumSideSamples), 0,
        CardioConfidence.INSUFFICIENT, CardioAnalyticState.INSUFFICIENT_DATA,
        "Requires at least " + (minimumSideSamples * 2 + 2) + " repeated observations."
    )
}

internal object CardioCorrelationEngine {
    fun correlate(
        left: List<CardioTimedValue>,
        right: List<CardioTimedValue>,
        method: CardioCorrelationMethod = CardioCorrelationMethod.SPEARMAN,
        lagMs: Long = 0L,
        matchToleranceMs: Long = 12L * 60L * 60L * 1000L
    ): CardioCorrelationResult {
        require(lagMs >= 0L)
        require(matchToleranceMs >= 0L)
        val cleanLeft = left.filter { it.value.isFinite() }.sortedBy { it.timestampEpochMs }
        val cleanRight = right.filter { it.value.isFinite() }.sortedBy { it.timestampEpochMs }
        if (cleanLeft.isEmpty() || cleanRight.isEmpty()) return unavailable(method, lagMs)

        val matched = cleanLeft.mapNotNull { source ->
            val targetTime = source.timestampEpochMs + lagMs
            val candidate = cleanRight.minByOrNull { abs(it.timestampEpochMs - targetTime) } ?: return@mapNotNull null
            if (abs(candidate.timestampEpochMs - targetTime) <= matchToleranceMs) source.value to candidate.value else null
        }
        val matchedFraction = matched.size.toDouble() / cleanLeft.size
        if (matched.size < 6) {
            return CardioCorrelationResult(
                null, method, matched.size, matchedFraction, lagMs,
                CardioConfidence.INSUFFICIENT, CardioAnalyticState.INSUFFICIENT_DATA
            )
        }

        val x = matched.map { it.first }
        val y = matched.map { it.second }
        val coefficient = when (method) {
            CardioCorrelationMethod.PEARSON -> pearson(x, y)
            CardioCorrelationMethod.SPEARMAN -> pearson(ranks(x), ranks(y))
        }
        val confidence = when {
            matched.size >= 30 && matchedFraction >= 0.80 -> CardioConfidence.HIGH
            matched.size >= 15 && matchedFraction >= 0.60 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioCorrelationResult(
            coefficient, method, matched.size, matchedFraction, lagMs, confidence,
            CardioAnalyticState.AVAILABLE
        )
    }

    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 2) return null
        val mx = x.average()
        val my = y.average()
        val numerator = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
        val dx = sqrt(x.sumOf { (it - mx).pow(2) })
        val dy = sqrt(y.sumOf { (it - my).pow(2) })
        if (dx <= 0.0 || dy <= 0.0) return null
        return (numerator / (dx * dy)).coerceIn(-1.0, 1.0)
    }

    private fun ranks(values: List<Double>): List<Double> {
        val indexed = values.withIndex().sortedBy { it.value }
        val ranks = MutableList(values.size) { 0.0 }
        var start = 0
        while (start < indexed.size) {
            var end = start
            while (end + 1 < indexed.size && indexed[end + 1].value == indexed[start].value) end++
            val averageRank = (start + end + 2) / 2.0
            for (i in start..end) ranks[indexed[i].index] = averageRank
            start = end + 1
        }
        return ranks
    }

    private fun unavailable(method: CardioCorrelationMethod, lagMs: Long) = CardioCorrelationResult(
        null, method, 0, 0.0, lagMs, CardioConfidence.INSUFFICIENT,
        CardioAnalyticState.INSUFFICIENT_DATA
    )
}

internal object CardioNof1ExperimentEngine {
    fun evaluate(
        spec: CardioNof1ExperimentSpec,
        observations: List<CardioNof1ExperimentObservation>
    ): CardioNof1ExperimentResult {
        val relevant = observations.filter {
            it.value.isFinite() &&
                it.timestampEpochMs in spec.baselineStartEpochMs..spec.interventionEndEpochMs
        }
        val adherent = relevant.filter { it.adherent }
        val baseline = adherent.filter {
            it.timestampEpochMs in spec.baselineStartEpochMs..spec.baselineEndEpochMs &&
                it.phase.equals("baseline", ignoreCase = true)
        }
        val intervention = adherent.filter {
            it.timestampEpochMs in spec.interventionStartEpochMs..spec.interventionEndEpochMs &&
                it.phase.equals("intervention", ignoreCase = true)
        }
        val adherence = if (relevant.isEmpty()) 0.0 else adherent.size.toDouble() / relevant.size
        val refs = relevant.mapNotNull { it.evidenceReference }.distinct()

        if (baseline.size < 3 || intervention.size < 3) {
            return CardioNof1ExperimentResult(
                spec.id, null, null, null, null, null, baseline.size, intervention.size,
                adherence, CardioConfidence.INSUFFICIENT, "Inconclusive",
                "Requires at least 3 adherent observations in each phase.", refs
            )
        }

        val baseValues = baseline.map { it.value }
        val interventionValues = intervention.map { it.value }
        val baseMedian = median(baseValues)
        val interventionMedian = median(interventionValues)
        val change = interventionMedian - baseMedian
        val percent = if (abs(baseMedian) > 1e-9) change / abs(baseMedian) * 100.0 else null
        val mad = median(baseValues.map { abs(it - baseMedian) })
        val scale = 1.4826 * mad
        val effect = if (scale > 1e-9) change / scale else null
        val confounders = (baseline + intervention).flatMap { it.confounders }.toSet() + spec.confounders

        val directionMatches = when {
            spec.expectedDirection > 0 -> change > 0.0
            spec.expectedDirection < 0 -> change < 0.0
            else -> true
        }
        val strongEnough = effect != null && abs(effect) >= 0.8
        val conclusion = when {
            strongEnough && directionMatches && adherence >= 0.80 -> "Signal consistent with hypothesis"
            strongEnough && !directionMatches && adherence >= 0.80 -> "Observed signal did not align with hypothesis"
            else -> "Inconclusive"
        }
        var confidence = when {
            baseline.size >= 8 && intervention.size >= 8 && adherence >= 0.90 -> CardioConfidence.HIGH
            baseline.size >= 5 && intervention.size >= 5 && adherence >= 0.80 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        if (confounders.isNotEmpty() && confidence == CardioConfidence.HIGH) confidence = CardioConfidence.MODERATE

        val uncertainty = buildString {
            append("Personal association only; phase comparison does not establish causation.")
            if (confounders.isNotEmpty()) append(" Confounders: " + confounders.sorted().joinToString() + ".")
            if (effect == null) append(" Baseline variability was too small to standardize robustly.")
        }
        return CardioNof1ExperimentResult(
            spec.id,
            baseMedian,
            interventionMedian,
            change,
            percent,
            effect,
            baseline.size,
            intervention.size,
            adherence,
            confidence,
            conclusion,
            uncertainty,
            refs
        )
    }
}

internal object CardioFitnessIntelligenceEngine {
    fun snapshot(
        activity: CardioActivityType,
        evidence: List<CardioSessionEvidence>,
        nowEpochMs: Long = System.currentTimeMillis(),
        targetHeartRateBpm: Double? = null,
        targetPaceSecondsPerKm: Double? = null
    ): CardioSportFitnessSnapshot {
        val sport = evidence.filter { it.session.activity == activity }.sortedBy { it.session.endedAt }
        if (sport.size < 4) {
            return CardioSportFitnessSnapshot(
                activity, "Building baseline", emptyList(), sport.size, CardioConfidence.INSUFFICIENT,
                CardioAnalyticState.BUILDING_BASELINE,
                "Fitness is sport-aware and requires repeated comparable sessions."
            )
        }
        val recentCut = nowEpochMs - 28L * 86_400_000L
        val baselineCut = nowEpochMs - 84L * 86_400_000L
        val signals = mutableListOf<CardioFitnessSignal>()

        val efficiency = sport.mapNotNull { item ->
            val value = CardioAerobicEfficiencyEngine.estimate(activity, item.samples).value ?: return@mapNotNull null
            item.session.endedAt to value
        }
        buildSignal(
            CardioFitnessSignalType.AEROBIC_EFFICIENCY,
            efficiency,
            baselineCut,
            recentCut,
            nowEpochMs,
            higherIsBetter = true,
            explanation = "Output per heartbeat under sport-specific inputs"
        )?.let(signals::add)

        if (targetHeartRateBpm != null &&
            activity in setOf(CardioActivityType.RUNNING, CardioActivityType.WALKING, CardioActivityType.TREADMILL, CardioActivityType.HIKING)
        ) {
            val paceAtHr = sport.mapNotNull { item ->
                val value = CardioBandAnalytics.paceAtHeartRate(item.samples, targetHeartRateBpm).value ?: return@mapNotNull null
                item.session.endedAt to value
            }
            buildSignal(
                CardioFitnessSignalType.PACE_AT_HEART_RATE,
                paceAtHr,
                baselineCut,
                recentCut,
                nowEpochMs,
                higherIsBetter = false,
                explanation = "Pace at a comparable heart-rate band"
            )?.let(signals::add)
        }

        if (targetPaceSecondsPerKm != null &&
            activity in setOf(CardioActivityType.RUNNING, CardioActivityType.WALKING, CardioActivityType.TREADMILL, CardioActivityType.HIKING)
        ) {
            val hrAtPace = sport.mapNotNull { item ->
                val value = CardioBandAnalytics.heartRateAtPace(item.samples, targetPaceSecondsPerKm).value ?: return@mapNotNull null
                item.session.endedAt to value
            }
            buildSignal(
                CardioFitnessSignalType.HEART_RATE_AT_PACE,
                hrAtPace,
                baselineCut,
                recentCut,
                nowEpochMs,
                higherIsBetter = false,
                explanation = "Heart rate at a comparable pace band"
            )?.let(signals::add)
        }

        val decoupling = sport.mapNotNull { item ->
            val value = CardioDecouplingEngine.estimate(item.samples).percent ?: return@mapNotNull null
            item.session.endedAt to abs(value)
        }
        buildSignal(
            CardioFitnessSignalType.DECOUPLING,
            decoupling,
            baselineCut,
            recentCut,
            nowEpochMs,
            higherIsBetter = false,
            explanation = "Absolute aerobic decoupling in sufficiently long sampled sessions"
        )?.let(signals::add)

        val hrr = sport.mapNotNull { item ->
            val value = CardioHeartRateRecoveryEngine.estimate(item.postEffortHeartRate).hrr1MinuteBpm ?: return@mapNotNull null
            item.session.endedAt to value
        }
        buildSignal(
            CardioFitnessSignalType.HEART_RATE_RECOVERY,
            hrr,
            baselineCut,
            recentCut,
            nowEpochMs,
            higherIsBetter = true,
            explanation = "One-minute post-effort HR recovery from observed recovery samples"
        )?.let(signals::add)

        if (signals.isEmpty()) {
            return CardioSportFitnessSnapshot(
                activity, "Building baseline", emptyList(), sport.size, CardioConfidence.LOW,
                CardioAnalyticState.BUILDING_BASELINE,
                "Sessions exist, but recent and prior windows do not yet contain enough compatible high-quality signals."
            )
        }

        val improved = signals.count { it.direction > 0 }
        val lower = signals.count { it.direction < 0 }
        val label = when {
            improved >= 2 && improved > lower -> "Multiple personal fitness signals improved"
            lower >= 2 && lower > improved -> "Multiple signals are below prior baseline"
            else -> "Mixed or stable personal fitness signals"
        }
        val confidence = when {
            signals.count { it.confidence == CardioConfidence.HIGH } >= 2 -> CardioConfidence.HIGH
            signals.count { it.confidence >= CardioConfidence.MODERATE } >= 2 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioSportFitnessSnapshot(
            activity, label, signals, sport.size, confidence, CardioAnalyticState.AVAILABLE,
            "No cross-sport score is produced; each signal retains its own direction, sample count and definition."
        )
    }

    private fun buildSignal(
        type: CardioFitnessSignalType,
        timed: List<Pair<Long, Double>>,
        baselineCut: Long,
        recentCut: Long,
        nowEpochMs: Long,
        higherIsBetter: Boolean,
        explanation: String
    ): CardioFitnessSignal? {
        val baseline = timed.filter { it.first in baselineCut until recentCut }.map { it.second }
        val recent = timed.filter { it.first in recentCut..nowEpochMs }.map { it.second }
        if (baseline.size < 2 || recent.size < 2) return null
        val old = median(baseline)
        val new = median(recent)
        val rawPercent = if (abs(old) > 1e-9) (new - old) / abs(old) * 100.0 else return null
        val performanceDelta = if (higherIsBetter) rawPercent else -rawPercent
        val direction = when {
            performanceDelta > 1.0 -> 1
            performanceDelta < -1.0 -> -1
            else -> 0
        }
        val count = baseline.size + recent.size
        val confidence = when {
            baseline.size >= 6 && recent.size >= 6 -> CardioConfidence.HIGH
            baseline.size >= 4 && recent.size >= 4 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        return CardioFitnessSignal(type, performanceDelta, direction, count, confidence, explanation)
    }
}

internal object CardioInsightEngine {
    fun fromFitness(snapshot: CardioSportFitnessSnapshot): List<CardioInsight> =
        snapshot.signals.filter { it.deltaPercent != null && it.direction != 0 }.map { signal ->
            val delta = requireNotNull(signal.deltaPercent)
            val directionWord = if (signal.direction > 0) "improved" else "was lower"
            CardioInsight(
                id = "fitness-" + snapshot.activity.name.lowercase() + "-" + signal.type.name.lowercase(),
                title = signal.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                body = "Recent " + signal.type.name.replace('_', ' ').lowercase() + " " + directionWord +
                    " by " + String.format(java.util.Locale.US, "%.1f", abs(delta)) +
                    "% versus the prior personal baseline.",
                metricId = signal.type.name.lowercase(),
                sampleCount = signal.sampleCount,
                confidence = signal.confidence,
                comparisonWindow = "recent 28 days vs prior 56 days",
                confounders = emptyList(),
                definition = signal.explanation
            )
        }
}

internal object CardioPeriodSummaryEngine {
    fun summarize(
        sessions: List<CardioSession>,
        evidence: List<CardioSessionEvidence>,
        period: CardioSummaryPeriod,
        records: List<CardioRecord> = emptyList(),
        endEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioPeriodSummary {
        val endDate = Instant.ofEpochMilli(endEpochMs).atZone(zoneId).toLocalDate()
        val startDate = when (period) {
            CardioSummaryPeriod.WEEKLY -> endDate.minusDays(6)
            CardioSummaryPeriod.MONTHLY -> endDate.minusDays(29)
        }
        val startEpochMs = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val selected = sessions.filter { it.endedAt in startEpochMs..endEpochMs }
        val selectedEvidence = evidence.filter { it.session.id in selected.map(CardioSession::id).toSet() }
        val loadEstimates = selectedEvidence.map(CardioTrainingLoadIntelligenceEngine::sessionLoad)
        val loadMethods = loadEstimates.filter { it.value != null }.map { it.method }.distinct()
        val load = if (loadMethods.size == 1) loadEstimates.mapNotNull { it.value }.sum() else null
        val zone = (1..5).associateWith { z -> selected.sumOf { it.zoneSeconds[z] ?: 0 } }
        val zoneProfile = CardioZoneProfileAnalytics.distribution(selected)

        val fitnessSignals = selected.map { it.activity }.distinct().flatMap { activity ->
            CardioFitnessIntelligenceEngine.snapshot(activity, evidence, endEpochMs).signals
        }

        val qualityNotes = buildList {
            if (loadMethods.size > 1) add("Multiple load methods present; period load is not summed across incompatible scales.")
            if (!zoneProfile.compatibleForCombinedAnalysis) add(zoneProfile.caveat)
            if (selected.any { it.avgHeartRate == null }) add("Some sessions are missing average heart rate.")
        }
        val missing = buildList {
            if (selected.isEmpty()) add("No cardio sessions in this period")
            if (selected.none { it.zoneSeconds.isNotEmpty() }) add("Measured zone time")
            if (selectedEvidence.none { it.samples.isNotEmpty() }) add("Time-series HR/output samples")
            if (selectedEvidence.none { it.rrIntervals.isNotEmpty() }) add("RR intervals for HRV")
        }
        return CardioPeriodSummary(
            period,
            startEpochMs,
            endEpochMs,
            selected.size,
            selected.sumOf { it.durationSeconds.coerceAtLeast(0) } / 60,
            selected.mapNotNull { it.distanceKm }.takeIf { it.isNotEmpty() }?.sum(),
            load,
            zone,
            fitnessSignals,
            records.filter { it.verified && it.sessionId in selected.map(CardioSession::id).toSet() }
                .map { it.label },
            qualityNotes,
            if (sessions.size >= 8) "Personal baseline available for supported metrics" else "Building baseline",
            missing
        )
    }
}
