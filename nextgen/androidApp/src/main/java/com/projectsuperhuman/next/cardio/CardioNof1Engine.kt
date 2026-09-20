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
    private const val DEFAULT_MAX_GAP_MS = 5_000L
    private const val DEFAULT_MIN_SUSTAINED_MS = 5_000L
    private const val ARTIFACT_JUMP_BPM = 45

    fun candidate(
        configuredHrMax: Int?,
        samples: List<CardioHeartRateSample>,
        minConsecutiveSamples: Int = 3
    ): Int? = detectCandidate(
        configuredHrMax = configuredHrMax,
        samples = samples,
        minConsecutiveSamples = minConsecutiveSamples
    )?.candidateBpm

    fun detectCandidate(
        configuredHrMax: Int?,
        samples: List<CardioHeartRateSample>,
        minConsecutiveSamples: Int = 3,
        minSustainedDurationMs: Long = DEFAULT_MIN_SUSTAINED_MS,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
        coveragePct: Double? = null,
        qualityByTimestampEpochMs: Map<Long, CardioObservationQuality> = emptyMap()
    ): CardioHrMaxCandidate? {
        val configured = configuredHrMax?.takeIf { it in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM }
            ?: return null
        if (minConsecutiveSamples < 2 || minSustainedDurationMs < 0L || maxGapMs <= 0L) return null

        val ordered = samples
            .filter { it.isPhysiologicallyStorable }
            .filter { sample ->
                when (qualityByTimestampEpochMs[sample.timestampEpochMs]) {
                    CardioObservationQuality.INVALID,
                    CardioObservationQuality.FILTERED,
                    CardioObservationQuality.SUSPECT_OUTLIER -> false
                    else -> true
                }
            }
            .sortedBy { it.timestampEpochMs }
        if (ordered.size < minConsecutiveSamples) return null

        val artifactIndexes = mutableSetOf<Int>()
        ordered.zipWithNext().forEachIndexed { index, (a, b) ->
            val gap = b.timestampEpochMs - a.timestampEpochMs
            if (gap in 1..2_000L && kotlin.math.abs(b.bpm - a.bpm) >= ARTIFACT_JUMP_BPM) {
                artifactIndexes += index + 1
            }
        }
        val clean = ordered.filterIndexed { index, _ -> index !in artifactIndexes }
        val runs = mutableListOf<MutableList<CardioHeartRateSample>>()
        var currentRun: MutableList<CardioHeartRateSample>? = null
        clean.forEach { sample ->
            if (sample.bpm <= configured) {
                currentRun = null
                return@forEach
            }
            val current = currentRun
            val last = current?.lastOrNull()
            if (last == null || sample.timestampEpochMs - last.timestampEpochMs > maxGapMs) {
                mutableListOf(sample).also {
                    runs += it
                    currentRun = it
                }
            } else {
                current += sample
            }
        }
        if (runs.sumOf { it.size } < minConsecutiveSamples) return null

        val credible = runs
            .filter { run ->
                run.size >= minConsecutiveSamples &&
                    (run.last().timestampEpochMs - run.first().timestampEpochMs) >= minSustainedDurationMs
            }
            .maxWithOrNull(
                compareBy<List<CardioHeartRateSample>> { it.maxOf { sample -> sample.bpm } }
                    .thenBy { it.size }
            ) ?: return null

        val duration = credible.last().timestampEpochMs - credible.first().timestampEpochMs
        val kinds = credible.map { it.source.providerType }.distinct()
        val sourceSummary = credible
            .groupingBy { it.source.deviceName ?: it.source.sourceName }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "Unknown source"

        val directCount = credible.count {
            it.source.transport == CardioSensorTransport.LIVE_BLE ||
                it.source.providerType == CardioSensorProviderType.H19C ||
                it.source.providerType == CardioSensorProviderType.BLE_HEART_RATE
        }
        val qualityLimited = credible.any {
            qualityByTimestampEpochMs[it.timestampEpochMs] in setOf(
                CardioObservationQuality.STALE,
                CardioObservationQuality.GAP_ADJACENT,
                CardioObservationQuality.INTERPOLATED
            )
        }
        val normalizedCoverage = coveragePct?.coerceIn(0.0, 100.0)
        val confidence = when {
            artifactIndexes.isNotEmpty() || qualityLimited -> CardioConfidence.LOW
            normalizedCoverage != null && normalizedCoverage < 70.0 -> CardioConfidence.LOW
            normalizedCoverage == null -> CardioConfidence.MODERATE
            directCount == credible.size && credible.size >= 6 && duration >= 10_000L &&
                normalizedCoverage >= 90.0 -> CardioConfidence.HIGH
            directCount == credible.size -> CardioConfidence.MODERATE
            else -> CardioConfidence.LOW
        }

        val reasons = buildList {
            add(credible.size.toString() + " consecutive samples exceeded configured HRmax")
            add("Sustained for " + duration + " ms with gaps <= " + maxGapMs + " ms")
            add("Source: " + sourceSummary)
            if (kinds.size > 1) add("Candidate contains more than one provider type")
            normalizedCoverage?.let { add("Heart-rate coverage: " + "%.1f".format(java.util.Locale.US, it) + "%") }
            if (qualityLimited) add("Candidate contains samples with reduced observation quality")
            if (artifactIndexes.isNotEmpty()) add("Potential abrupt signal jump detected elsewhere in the stream")
            add("Profile change requires explicit confirmation")
        }

        return CardioHrMaxCandidate(
            candidateBpm = credible.maxOf { it.bpm },
            configuredBpm = configured,
            sustainedDurationMs = duration,
            sampleCount = credible.size,
            confidence = confidence,
            sourceSummary = sourceSummary,
            reasons = reasons,
            coveragePct = normalizedCoverage
        )
    }
}
