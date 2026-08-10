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
    // UI-facing alias retained so recovery cards can describe stage quality as restoration.
    val restorationScore: Int get() = stageBalanceScore
}

/**
 * Deterministic sleep interpretation layer used by the UI and sync pipeline.
 * Keeps product copy separate from raw Health Connect parsing and leaves a clear
 * replacement point for a future shared ScientificEngine implementation.
 */
internal object SleepAnalysisEngine {
    /** Convenience adapter for Compose/UI consumers. */
    fun analyse(snapshot: NativeSleepSnapshot): SleepAnalysis = analyse(
        totalMinutes = snapshot.totalMinutes ?: 0,
        awakeMinutes = snapshot.awakeMinutes ?: 0,
        deepMinutes = snapshot.deepMinutes ?: 0,
        remMinutes = snapshot.remMinutes ?: 0,
        lightMinutes = snapshot.lightMinutes ?: 0
    )

    fun analyse(
        totalMinutes: Int,
        awakeMinutes: Int,
        deepMinutes: Int,
        remMinutes: Int,
        lightMinutes: Int
    ): SleepAnalysis {
        if (totalMinutes <= 0) {
            return SleepAnalysis(
                0, 0, 0, 0, 0, 0, 0, 0,
                "Waiting for a complete night",
                "Once a full sleep session is available, Project Superhuman will break down duration, continuity and stage balance.",
                "Sync a recorded night"
            )
        }

        val asleep = (totalMinutes - awakeMinutes).coerceAtLeast(0)
        val efficiency = ((asleep.toDouble() / totalMinutes) * 100.0).roundToInt().coerceIn(0, 100)
        fun pct(v: Int) = if (asleep > 0) ((v.toDouble() / asleep) * 100.0).roundToInt().coerceIn(0, 100) else 0
        val deepPct = pct(deepMinutes)
        val remPct = pct(remMinutes)
        val awakePct = ((awakeMinutes.toDouble() / totalMinutes) * 100.0).roundToInt().coerceIn(0, 100)

        val durationHours = asleep / 60.0
        val durationScore = (100.0 - abs(durationHours - 8.0) * 18.0).roundToInt().coerceIn(0, 100)
        val continuityScore = efficiency
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

        val insight = when (weakest) {
            "duration" -> "Your stage balance was only part of the picture. Total sleep time was the main limiter, so extending the sleep window is likely to improve recovery most."
            "continuity" -> "You spent a larger share of the night awake or disrupted. A more continuous night would improve recovery even if total time in bed stayed similar."
            else -> "Your total sleep and continuity were stronger than the stage mix. Deep and REM balance are the clearest areas to watch across several nights rather than a single session."
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
