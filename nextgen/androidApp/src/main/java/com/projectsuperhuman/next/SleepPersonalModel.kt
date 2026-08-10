package com.projectsuperhuman.next

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class SleepMetricConfidence(
    val metric: String,
    val score: Int,
    val label: String,
    val reason: String = ""
)

internal data class PersonalSleepBaseline(
    val nights: Int,
    val averageSleepMinutes: Double?,
    val averageBedtimeMinutes: Double?,
    val averageWakeMinutes: Double?,
    val averageDeepPct: Double?,
    val averageRemPct: Double?,
    val averageFragmentationMinutes: Double?,
    val stabilityScore: Int
)

internal data class PersonalSleepAssessment(
    val baseline: PersonalSleepBaseline,
    val currentSleepMinutes: Int?,
    val sleepDeltaMinutes: Int?,
    val deepDeltaPct: Int?,
    val remDeltaPct: Int?,
    val continuityDeltaMinutes: Int?,
    val confidence: List<SleepMetricConfidence>,
    val maturity: Int,
    val headline: String,
    val explanation: String
)

/**
 * Learns from the user's own sleep history without changing recorded data.
 * No causal claims are made here; this layer only detects personal patterns and deviations.
 */
internal object SleepPersonalModel {
    private const val MIN_MATURE_NIGHTS = 30

    fun baseline(episodes: List<CanonicalSleepEpisode>): PersonalSleepBaseline {
        val nights = episodes.filter { it.classification == SleepEpisodeClassification.NIGHT }
        if (nights.isEmpty()) return PersonalSleepBaseline(0, null, null, null, null, null, null, 0)

        val sleepValues = nights.map { it.recordedStageMinutes.toDouble() }
        val bedtimeValues = nights.map { minuteOfDay(it.start) }
        val wakeValues = nights.map { minuteOfDay(it.end) }
        val deepValues = nights.map { stagePct(it, "deep") }
        val remValues = nights.map { stagePct(it, "rem") }
        val fragmentation = nights.map { it.interruptionMinutes.toDouble() }

        val stability = stabilityScore(sleepValues)
        return PersonalSleepBaseline(
            nights.size,
            sleepValues.averageOrNull(),
            circularAverage(bedtimeValues),
            circularAverage(wakeValues),
            deepValues.averageOrNull(),
            remValues.averageOrNull(),
            fragmentation.averageOrNull(),
            stability
        )
    }

    fun assess(history: List<CanonicalSleepEpisode>, current: CanonicalSleepEpisode?): PersonalSleepAssessment {
        val base = baseline(history)
        if (current == null || base.nights == 0) {
            return PersonalSleepAssessment(base, null, null, null, null, null, emptyList(), maturity(base.nights),
                "Building your sleep baseline", "More nights will make your personal pattern clearer.")
        }

        val currentSleep = current.recordedStageMinutes
        val deep = stagePct(current, "deep")
        val rem = stagePct(current, "rem")
        val sleepDelta = base.averageSleepMinutes?.let { (currentSleep - it).roundToInt() }
        val deepDelta = base.averageDeepPct?.let { (deep - it).roundToInt() }
        val remDelta = base.averageRemPct?.let { (rem - it).roundToInt() }
        val continuityDelta = base.averageFragmentationMinutes?.let { (current.interruptionMinutes - it).roundToInt() }

        val confidence = listOf(
            confidence("sleep duration", base.nights, current.blocks.isNotEmpty()),
            confidence("sleep stages", base.nights, current.blocks.any { it.stages.isNotEmpty() }),
            confidence("continuity", base.nights, current.interruptions.isNotEmpty() || current.blocks.size == 1)
        )

        val mature = maturity(base.nights)
        val headline = when {
            mature < 25 -> "Your baseline is taking shape"
            sleepDelta != null && sleepDelta <= -60 -> "Shorter than your usual night"
            sleepDelta != null && sleepDelta >= 60 -> "Longer than your usual night"
            continuityDelta != null && continuityDelta >= 30 -> "More fragmented than usual"
            continuityDelta != null && continuityDelta <= -30 -> "More continuous than usual"
            else -> "Close to your usual pattern"
        }
        val explanation = buildExplanation(sleepDelta, deepDelta, remDelta, continuityDelta, mature)
        return PersonalSleepAssessment(base, currentSleep, sleepDelta, deepDelta, remDelta, continuityDelta, confidence, mature, headline, explanation)
    }

    private fun buildExplanation(sleep: Int?, deep: Int?, rem: Int?, continuity: Int?, maturity: Int): String {
        val parts = mutableListOf<String>()
        if (sleep != null && abs(sleep) >= 30) parts += "sleep duration was ${if (sleep > 0) "above" else "below"} your recent baseline"
        if (continuity != null && abs(continuity) >= 20) parts += "fragmentation was ${if (continuity > 0) "higher" else "lower"} than usual"
        if (deep != null && abs(deep) >= 4) parts += "deep sleep was ${if (deep > 0) "above" else "below"} your baseline"
        if (rem != null && abs(rem) >= 5) parts += "REM was ${if (rem > 0) "above" else "below"} your baseline"
        if (parts.isEmpty()) return if (maturity >= MIN_MATURE_NIGHTS) "Your main sleep measures are currently close to your personal pattern." else "The model is still learning your normal range."
        return "Compared with your personal pattern, ${parts.joinToString(", ")}."
    }

    private fun confidence(metric: String, nights: Int, hasSourceData: Boolean): SleepMetricConfidence {
        val score = when {
            !hasSourceData -> 45
            nights >= 60 -> 95
            nights >= 30 -> 90
            nights >= 14 -> 78
            nights >= 7 -> 65
            else -> 50
        }
        return SleepMetricConfidence(metric, score, when {
            score >= 90 -> "High"
            score >= 75 -> "Good"
            score >= 60 -> "Moderate"
            else -> "Limited"
        })
    }

    private fun maturity(nights: Int): Int = ((nights.toDouble() / MIN_MATURE_NIGHTS) * 100).roundToInt().coerceIn(0, 100)

    private fun stagePct(episode: CanonicalSleepEpisode, type: String): Double {
        val total = episode.recordedStageMinutes.coerceAtLeast(1)
        return episode.blocks.sumOf { block -> block.stages.filter { it.type == type }.sumOf { it.durationMinutes } }.toDouble() / total * 100.0
    }

    private fun minuteOfDay(time: Instant): Double {
        val local = time.atZone(java.time.ZoneId.systemDefault())
        return (local.hour * 60 + local.minute).toDouble()
    }

    private fun circularAverage(values: List<Double>): Double = values.map { if (it < 360) it else it - 1440 }.average()

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun stabilityScore(values: List<Double>): Int {
        if (values.size < 2) return 50
        val mean = values.average()
        val sd = sqrt(values.map { (it - mean).pow(2) }.average())
        return (100.0 - sd / 3.0).roundToInt().coerceIn(0, 100)
    }
}

/** Context contract for future cross-module analysis. It deliberately carries facts, not causal conclusions. */
internal data class SleepContextSignals(
    val sleep: SleepContextSleep? = null,
    val nutrition: Map<String, Double> = emptyMap(),
    val hydration: Map<String, Double> = emptyMap(),
    val training: Map<String, Double> = emptyMap(),
    val recovery: Map<String, Double> = emptyMap()
)

internal data class SleepContextSleep(
    val durationMinutes: Int,
    val deepPct: Double,
    val remPct: Double,
    val fragmentationMinutes: Int,
    val bedtimeMinuteOfDay: Int,
    val wakeMinuteOfDay: Int
)
