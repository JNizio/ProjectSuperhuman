package com.projectsuperhuman.next

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp

internal object CardioTrainingLoadEngine {
    private const val CHRONIC_TIME_CONSTANT_DAYS = 42.0
    private const val ACUTE_TIME_CONSTANT_DAYS = 7.0

    fun build(
        sessions: List<CardioSession>,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CardioTrainingLoadPoint> {
        if (sessions.isEmpty()) return emptyList()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val first = sessions.minOf { it.endedAt }
        val firstDay = Instant.ofEpochMilli(first).atZone(zoneId).toLocalDate()
        val daily = sessions.groupBy {
            Instant.ofEpochMilli(it.endedAt).atZone(zoneId).toLocalDate()
        }

        var chronic = 0.0
        var acute = 0.0
        val chronicAlpha = 1.0 - exp(-1.0 / CHRONIC_TIME_CONSTANT_DAYS)
        val acuteAlpha = 1.0 - exp(-1.0 / ACUTE_TIME_CONSTANT_DAYS)
        val output = mutableListOf<CardioTrainingLoadPoint>()

        var day = firstDay
        while (!day.isAfter(today)) {
            val details = daily[day].orEmpty().map(CardioAnalyticsEngine::loadDetail)
            val scores = details.mapNotNull { it.score }
            val dayLoad = scores.sum()
            val priorChronic = chronic
            val priorAcute = acute
            chronic = priorChronic + chronicAlpha * (dayLoad - priorChronic)
            acute = priorAcute + acuteAlpha * (dayLoad - priorAcute)
            output += CardioTrainingLoadPoint(
                epochDay = day.toEpochDay(),
                dailyLoad = dayLoad,
                chronicLoad = chronic,
                acuteLoad = acute,
                trainingStressBalance = priorChronic - priorAcute,
                scoredSessionCount = scores.size
            )
            day = day.plusDays(1)
        }
        return output
    }

    fun latest(
        sessions: List<CardioSession>,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioTrainingLoadPoint? = build(sessions, nowEpochMs, zoneId).lastOrNull()
}

internal object CardioPersonalBaselineEngine {
    fun fitnessSnapshot(
        sessions: List<CardioSession>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): CardioFitnessSnapshot {
        val comparable = sessions.asSequence()
            .filter {
                it.activity == CardioActivityType.RUNNING ||
                    it.activity == CardioActivityType.WALKING ||
                    it.activity == CardioActivityType.TREADMILL
            }
            .filter { it.avgHeartRate != null && it.avgPaceSecPerKm != null }
            .sortedBy { it.endedAt }
            .toList()

        if (comparable.size < 4) {
            return CardioFitnessSnapshot(
                trendLabel = "Building baseline",
                trendDeltaPercent = null,
                confidence = CardioConfidence.INSUFFICIENT,
                basis = "Requires at least 4 sessions with both pace and average heart rate.",
                comparableSessionCount = comparable.size
            )
        }

        fun efficiency(session: CardioSession): Double? {
            val pace = session.avgPaceSecPerKm?.takeIf { it > 0 } ?: return null
            val heartRate = session.avgHeartRate?.takeIf { it > 0 } ?: return null
            val speedMetersPerSecond = 1000.0 / pace
            return speedMetersPerSecond / heartRate
        }

        val recentCut = nowEpochMs - 28L * 86_400_000L
        val baselineCut = nowEpochMs - 84L * 86_400_000L
        val recent = comparable.filter { it.endedAt >= recentCut }.mapNotNull(::efficiency)
        val baseline = comparable.filter { it.endedAt in baselineCut until recentCut }.mapNotNull(::efficiency)

        if (recent.size < 2 || baseline.size < 2) {
            return CardioFitnessSnapshot(
                trendLabel = "Building baseline",
                trendDeltaPercent = null,
                confidence = CardioConfidence.LOW,
                basis = "Pace-to-heart-rate efficiency needs comparable recent and prior sessions.",
                comparableSessionCount = comparable.size
            )
        }

        val recentMean = recent.average()
        val baselineMean = baseline.average()
        val deltaPct = if (baselineMean != 0.0) {
            (recentMean - baselineMean) / abs(baselineMean) * 100.0
        } else {
            null
        }
        val confidence = when {
            recent.size >= 6 && baseline.size >= 6 -> CardioConfidence.HIGH
            recent.size >= 4 && baseline.size >= 4 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        val label = when {
            deltaPct == null -> "Stable"
            deltaPct > 2.0 -> "Improving"
            deltaPct < -2.0 -> "Lower than baseline"
            else -> "Stable"
        }
        return CardioFitnessSnapshot(
            trendLabel = label,
            trendDeltaPercent = deltaPct,
            confidence = confidence,
            basis = "Recent vs prior pace-to-heart-rate efficiency from running, walking and treadmill sessions.",
            comparableSessionCount = comparable.size
        )
    }

    fun readiness(context: CardioRecoveryContext): CardioReadinessSnapshot {
        val components = mutableListOf<Double>()
        val explanations = mutableListOf<String>()

        if (
            context.restingHeartRateBpm != null &&
            context.restingHeartRateBaselineBpm != null &&
            context.restingHeartRateBaselineBpm > 0.0
        ) {
            val deltaPct = (context.restingHeartRateBpm - context.restingHeartRateBaselineBpm) /
                context.restingHeartRateBaselineBpm * 100.0
            components += (50.0 - deltaPct * 5.0).coerceIn(0.0, 100.0)
            explanations += "resting HR vs personal baseline"
        }
        if (
            context.hrvRmssdMs != null &&
            context.hrvBaselineRmssdMs != null &&
            context.hrvBaselineRmssdMs > 0.0
        ) {
            val ratio = context.hrvRmssdMs / context.hrvBaselineRmssdMs
            components += (ratio * 50.0).coerceIn(0.0, 100.0)
            explanations += "RMSSD vs personal baseline"
        }
        context.sleepScore?.let {
            components += it.coerceIn(0.0, 100.0)
            explanations += "sleep score"
        }
        context.trainingStressBalance?.let {
            components += (50.0 + it * 2.0).coerceIn(0.0, 100.0)
            explanations += "fitness/fatigue balance"
        }

        if (components.isEmpty()) {
            return CardioReadinessSnapshot(
                status = "Building baseline",
                score = null,
                confidence = CardioConfidence.INSUFFICIENT,
                availableSignals = 0,
                totalSignals = 4,
                explanation = "Recovery signals are not yet available."
            )
        }

        val score = components.average()
        val confidence = when (components.size) {
            4 -> CardioConfidence.HIGH
            3 -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }
        val status = when {
            score >= 67.0 -> "Signals support normal training"
            score >= 40.0 -> "Mixed recovery signals"
            else -> "Recovery signals are below baseline"
        }
        return CardioReadinessSnapshot(
            status = status,
            score = score,
            confidence = confidence,
            availableSignals = components.size,
            totalSignals = 4,
            explanation = "Based on " + explanations.joinToString(", ") + "."
        )
    }

    fun weekIntent(
        sessions: List<CardioSession>,
        targetMinutes: Int? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioWeekIntentSnapshot {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val monday = now.toLocalDate()
            .minusDays((now.dayOfWeek.value - 1).toLong())
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val week = sessions.filter { it.endedAt in monday..nowEpochMs }
        val minutes = week.sumOf { it.durationSeconds.coerceAtLeast(0) } / 60
        val zone2 = week.sumOf { it.zoneSeconds[2]?.coerceAtLeast(0) ?: 0 } / 60
        val fraction = targetMinutes?.takeIf { it > 0 }?.let { minutes.toDouble() / it }
        val label = when {
            targetMinutes == null -> "$minutes min this week"
            fraction != null && fraction >= 1.0 -> "Weekly intention reached"
            else -> "$minutes / $targetMinutes min"
        }
        return CardioWeekIntentSnapshot(
            minutes = minutes,
            sessions = week.size,
            zone2Minutes = zone2,
            targetMinutes = targetMinutes,
            progressFraction = fraction,
            label = label
        )
    }
}

internal object CardioHrMaxCandidateEngine {
    fun candidate(
        configuredHrMax: Int?,
        samples: List<CardioHeartRateSample>,
        minConsecutiveSamples: Int = 3
    ): Int? {
        if (configuredHrMax == null || minConsecutiveSamples < 2) return null
        val clean = samples.filter { it.isPhysiologicallyStorable }.sortedBy { it.timestampEpochMs }
        if (clean.size < minConsecutiveSamples) return null
        val threshold = configuredHrMax + 1
        for (index in 0..clean.size - minConsecutiveSamples) {
            val window = clean.subList(index, index + minConsecutiveSamples)
            if (
                window.all { it.bpm >= threshold } &&
                window.zipWithNext().all { (a, b) -> b.timestampEpochMs - a.timestampEpochMs <= 10_000L }
            ) {
                return window.maxOf { it.bpm }
            }
        }
        return null
    }
}
