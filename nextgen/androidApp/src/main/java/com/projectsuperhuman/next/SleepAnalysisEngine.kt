package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class SleepAnalysis(
    val score: Int,
    val efficiencyPct: Int,
    val deepPct: Int,
    val remPct: Int,
    val awakePct: Int,
    val durationScore: Int,
    val continuityScore: Int,
    val stageBalanceScore: Int,
    val headline: String,
    val insight: String,
    val priority: String
) {
    val restorationScore: Int get() = stageBalanceScore
}

/**
 * Deterministic sleep interpretation layer used by the UI and sync pipeline.
 * totalMinutes represents estimated sleep time. awakeMinutes includes both awake
 * stages and detected gaps between fragmented sleep blocks belonging to one night.
 */
internal object SleepAnalysisEngine {
    fun analyse(snapshot: NativeSleepSnapshot): SleepAnalysis = analyse(
        totalMinutes = snapshot.totalMinutes ?: 0,
        awakeMinutes = snapshot.awakeMinutes ?: 0,
        deepMinutes = snapshot.deepMinutes ?: 0,
        remMinutes = snapshot.remMinutes ?: 0,
        lightMinutes = snapshot.lightMinutes ?: 0,
        interruptionCount = snapshot.stageSegments.count {
            it.type == "awake" && (it.endMs - it.startMs) >= 5 * 60_000L
        }
    )

    fun analyse(
        totalMinutes: Int,
        awakeMinutes: Int,
        deepMinutes: Int,
        remMinutes: Int,
        lightMinutes: Int,
        interruptionCount: Int = 0
    ): SleepAnalysis {
        if (totalMinutes <= 0) {
            return SleepAnalysis(
                0, 0, 0, 0, 0, 0, 0, 0,
                "Waiting for a complete night",
                "Once a full sleep session is available, Project Superhuman will break down duration, continuity and stage balance.",
                "Sync a recorded night"
            )
        }

        val sleepMinutes = totalMinutes.coerceAtLeast(0)
        val opportunityMinutes = (sleepMinutes + awakeMinutes).coerceAtLeast(1)
        val efficiency = ((sleepMinutes.toDouble() / opportunityMinutes) * 100.0).roundToInt().coerceIn(0, 100)
        fun pct(v: Int) = ((v.toDouble() / sleepMinutes) * 100.0).roundToInt().coerceIn(0, 100)
        val deepPct = pct(deepMinutes)
        val remPct = pct(remMinutes)
        val awakePct = ((awakeMinutes.toDouble() / opportunityMinutes) * 100.0).roundToInt().coerceIn(0, 100)

        val durationHours = sleepMinutes / 60.0
        val durationScore = (100.0 - abs(durationHours - 8.0) * 18.0).roundToInt().coerceIn(0, 100)
        val fragmentationPenalty = (interruptionCount * 4).coerceAtMost(18)
        val continuityScore = (efficiency - fragmentationPenalty).coerceIn(0, 100)
        val deepScore = (100.0 - abs(deepPct - 18.0) * 4.0).roundToInt().coerceIn(0, 100)
        val remScore = (100.0 - abs(remPct - 22.0) * 3.5).roundToInt().coerceIn(0, 100)
        val stageBalance = ((deepScore + remScore) / 2.0).roundToInt()
        val score = (durationScore * .40 + continuityScore * .35 + stageBalance * .25).roundToInt().coerceIn(0, 100)

        val headline = when {
            score >= 88 -> "A highly restorative night"
            score >= 78 -> "A strong night of recovery"
            score >= 66 -> "A solid night with room to improve"
            score >= 52 -> "Recovery was mixed"
            else -> "Your sleep was under-recovered"
        }

        val weakest = listOf(
            "duration" to durationScore,
            "continuity" to continuityScore,
            "stages" to stageBalance
        ).minBy { it.second }.first

        val priority = when (weakest) {
            "duration" -> "Protect more sleep opportunity"
            "continuity" -> "Focus on sleep continuity"
            else -> "Support a steadier sleep architecture"
        }

        val interruptionContext = when {
            interruptionCount >= 2 -> " The night was split into several sleep blocks, which lowered continuity."
            interruptionCount == 1 -> " One clear interruption split the night into separate sleep blocks."
            else -> ""
        }
        val insight = when (weakest) {
            "duration" -> "Total sleep time was the main limiter, so extending the sleep window is likely to improve recovery most.$interruptionContext"
            "continuity" -> "Your sleep was more fragmented than ideal. A more continuous night would improve recovery even if total sleep time stayed similar.$interruptionContext"
            else -> "Your duration and continuity were stronger than the stage mix. Deep and REM balance are more useful to watch across several nights than in isolation.$interruptionContext"
        }

        return SleepAnalysis(
            score,
            efficiency,
            deepPct,
            remPct,
            awakePct,
            durationScore,
            continuityScore,
            stageBalance,
            headline,
            insight,
            priority
        )
    }
}
