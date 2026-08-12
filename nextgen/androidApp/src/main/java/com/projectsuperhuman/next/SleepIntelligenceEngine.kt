package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Reference bands are deliberately kept separate from source data and can be versioned later. */
internal data class SleepReferenceProfile(
    val targetSleepMinMinutes: Int = 7 * 60,
    val targetSleepMaxMinutes: Int = 9 * 60,
    val deepMinPct: Double = 13.0,
    val deepMaxPct: Double = 23.0,
    val remMinPct: Double = 18.0,
    val remMaxPct: Double = 27.0,
    val strongEfficiencyPct: Double = 85.0
)

internal data class SleepIntelligenceResult(
    val interpretedSleepMinutes: Int,
    val sleepOpportunityMinutes: Int,
    val efficiencyPct: Int,
    val deepPct: Int,
    val remPct: Int,
    val awakePct: Int,
    val durationScore: Int,
    val continuityScore: Int,
    val stageBalanceScore: Int,
    val recoveryScore: Int,
    val confidence: List<SleepMetricConfidence>,
    val headline: String,
    val insight: String,
    val priority: String
) {
    fun confidenceFor(metric: String): SleepMetricConfidence =
        confidence.firstOrNull { it.metric == metric }
            ?: SleepMetricConfidence(metric, 50, "Limited", "Not enough source detail")
}

/**
 * Stage 2: turns reconstructed/source metrics into transparent interpretation.
 * It never rewrites the recorded values. Reference bands are used as context, not truth.
 */
internal object SleepIntelligenceEngine {
    private val reference = SleepReferenceProfile()

    fun analyse(snapshot: NativeSleepSnapshot): SleepIntelligenceResult {
        val recordedSleep = snapshot.totalMinutes ?: 0
        val recordedAwake = snapshot.awakeMinutes ?: 0
        val deep = snapshot.deepMinutes ?: 0
        val rem = snapshot.remMinutes ?: 0
        val light = snapshot.lightMinutes ?: 0
        val stageTotal = deep + rem + light
        val stageCoverage = if (recordedSleep > 0) {
            (stageTotal.toDouble() / recordedSleep.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        val gapSegments = snapshot.stageSegments.filter { it.type == "gap" }
        val gapMinutes = gapSegments.sumOf { ((it.endMs - it.startMs) / 60_000L).coerceAtLeast(0L) }.toInt()

        // We only compensate for source uncertainty. We do NOT invent sleep minutes.
        // Unclassified time remains unknown and lowers confidence instead.
        val interpretedSleep = recordedSleep
        // Distinct Health Connect records can belong to one human night. Time between
        // those blocks is real lost sleep opportunity even when the source does not label
        // it as an Awake stage (Samsung commonly behaves this way).
        val opportunity = (recordedSleep + recordedAwake + gapMinutes).coerceAtLeast(recordedSleep)
        val efficiency = if (opportunity > 0) {
            (recordedSleep * 100.0 / opportunity).roundToInt().coerceIn(0, 100)
        } else 0

        fun pct(value: Int): Int = if (recordedSleep > 0) {
            (value * 100.0 / recordedSleep).roundToInt().coerceIn(0, 100)
        } else 0

        val deepPct = pct(deep)
        val remPct = pct(rem)
        val awakePct = if (opportunity > 0) {
            (recordedAwake * 100.0 / opportunity).roundToInt().coerceIn(0, 100)
        } else 0

        val durationScore = durationScore(recordedSleep)
        val continuityScore = continuityScore(efficiency, snapshot)
        val stageBalanceScore = stageBalance(deepPct, remPct, stageCoverage)
        val recoveryScore = (
            durationScore * 0.40 + continuityScore * 0.35 + stageBalanceScore * 0.25
        ).roundToInt().coerceIn(0, 100)

        val baseConfidence = sourceConfidence(snapshot, stageCoverage)
        val confidence = listOf(
            SleepMetricConfidence("total", baseConfidence, confidenceLabel(baseConfidence), "Based directly on recorded sleep-session duration"),
            SleepMetricConfidence("stages", (baseConfidence * stageCoverage).roundToInt().coerceIn(0, 100), confidenceLabel((baseConfidence * stageCoverage).roundToInt()), "Based on the proportion of the recorded night covered by explicit stage intervals"),
            SleepMetricConfidence("efficiency", (baseConfidence - if (snapshot.stageSegments.any { it.type == "awake" }) 0 else 8).coerceIn(40, 100), confidenceLabel((baseConfidence - if (snapshot.stageSegments.any { it.type == "awake" }) 0 else 8).coerceIn(40, 100)), "Calculated from recorded sleep and explicitly reported awake time"),
            SleepMetricConfidence("interpretation", (baseConfidence * 0.9).roundToInt().coerceIn(40, 95), confidenceLabel((baseConfidence * 0.9).roundToInt()), "Interpretation combines source data with reference ranges; it is not a direct measurement")
        )

        val weakest = listOf(
            "duration" to durationScore,
            "continuity" to continuityScore,
            "stages" to stageBalanceScore
        ).minBy { it.second }.first

        val headline = when {
            recoveryScore >= 88 -> "A highly restorative night"
            recoveryScore >= 78 -> "A strong night of recovery"
            recoveryScore >= 66 -> "A solid night with room to improve"
            recoveryScore >= 52 -> "Recovery was mixed"
            else -> "Sleep was under-recovered"
        }

        val priority = when (weakest) {
            "duration" -> "Protect more sleep opportunity"
            "continuity" -> "Focus on sleep continuity"
            else -> "Support a steadier sleep architecture"
        }

        val stageNote = when {
            stageCoverage < 0.75 -> "Stage coverage is incomplete, so stage-based conclusions are lower confidence."
            deepPct < reference.deepMinPct -> "Deep sleep was below the reference band; watch the pattern across several nights rather than one night alone."
            deepPct > reference.deepMaxPct -> "Deep sleep was above the reference band; one night is not enough to treat that as a problem or benefit."
            remPct < reference.remMinPct -> "REM was below the reference band; the multi-night trend matters more than a single estimate."
            remPct > reference.remMaxPct -> "REM was above the reference band; treat this as context, not a diagnosis."
            else -> "Deep and REM proportions sit within the broad reference bands used by the engine."
        }

        val insight = when (weakest) {
            "duration" -> "Duration was the main limiter. ${stageNote}"
            "continuity" -> "Continuity was the main limiter. ${stageNote}"
            else -> "The stage mix is the main area to watch. ${stageNote}"
        }

        return SleepIntelligenceResult(
            interpretedSleepMinutes = interpretedSleep,
            sleepOpportunityMinutes = opportunity,
            efficiencyPct = efficiency,
            deepPct = deepPct,
            remPct = remPct,
            awakePct = awakePct,
            durationScore = durationScore,
            continuityScore = continuityScore,
            stageBalanceScore = stageBalanceScore,
            recoveryScore = recoveryScore,
            confidence = confidence,
            headline = headline,
            insight = insight,
            priority = priority
        )
    }

    private fun durationScore(minutes: Int): Int {
        if (minutes <= 0) return 0
        if (minutes in reference.targetSleepMinMinutes..reference.targetSleepMaxMinutes) return 100
        val distance = if (minutes < reference.targetSleepMinMinutes) {
            reference.targetSleepMinMinutes - minutes
        } else {
            minutes - reference.targetSleepMaxMinutes
        }
        return (100 - distance * 0.35).roundToInt().coerceIn(0, 100)
    }

    private fun continuityScore(efficiency: Int, snapshot: NativeSleepSnapshot): Int {
        val awakeInterruptions = snapshot.stageSegments.count {
            it.type == "awake" && (it.endMs - it.startMs) >= 5 * 60_000L
        }
        val gaps = snapshot.stageSegments.filter { it.type == "gap" }
        val gapMinutes = gaps.sumOf { ((it.endMs - it.startMs) / 60_000L).coerceAtLeast(0L) }.toInt()
        val interruptionPenalty = min(20, (awakeInterruptions + gaps.size) * 4)
        val longGapPenalty = min(18, gapMinutes / 6)
        return (efficiency - interruptionPenalty - longGapPenalty).coerceIn(0, 100)
    }

    private fun stageBalance(deepPct: Int, remPct: Int, coverage: Double): Int {
        if (coverage <= 0.0) return 0
        fun bandScore(value: Int, minPct: Double, maxPct: Double): Double {
            return when {
                value in minPct.roundToInt()..maxPct.roundToInt() -> 100.0
                value < minPct -> max(0.0, 100.0 - (minPct - value) * 6.0)
                else -> max(0.0, 100.0 - (value - maxPct) * 6.0)
            }
        }
        val raw = ((bandScore(deepPct, reference.deepMinPct, reference.deepMaxPct) +
            bandScore(remPct, reference.remMinPct, reference.remMaxPct)) / 2.0)
        return (raw * (0.55 + coverage * 0.45)).roundToInt().coerceIn(0, 100)
    }

    private fun sourceConfidence(snapshot: NativeSleepSnapshot, stageCoverage: Double): Int {
        var score = 96
        if (snapshot.totalMinutes == null) score -= 30
        if (snapshot.startEpochMs == null || snapshot.endEpochMs == null) score -= 12
        if (snapshot.stageSegments.isEmpty()) score -= 10
        if (stageCoverage < 0.75) score -= 12
        if (snapshot.stageSegments.count { it.type == "awake" } > 8) score -= 4
        if (snapshot.stageSegments.any { it.type == "gap" }) score -= 3
        return score.coerceIn(40, 96)
    }




    private fun confidenceLabel(score: Int): String = when {
        score >= 90 -> "High"
        score >= 75 -> "Good"
        score >= 60 -> "Moderate"
        else -> "Limited"
    }
}
