package com.projectsuperhuman.next

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
    val priority: String,
    val interpretedSleepMinutes: Int = 0,
    val confidencePct: Int = 0,
    val confidenceLabel: String = "Limited"
) {
    val restorationScore: Int get() = stageBalanceScore
}

/** Compatibility facade for the UI. The actual interpretation now lives in SleepIntelligenceEngine. */
internal object SleepAnalysisEngine {
    fun analyse(snapshot: NativeSleepSnapshot): SleepAnalysis {
        val result = SleepIntelligenceEngine.analyse(snapshot)
        val confidence = result.confidenceFor("interpretation")
        return SleepAnalysis(
            score = result.recoveryScore,
            efficiencyPct = result.efficiencyPct,
            deepPct = result.deepPct,
            remPct = result.remPct,
            awakePct = result.awakePct,
            durationScore = result.durationScore,
            continuityScore = result.continuityScore,
            stageBalanceScore = result.stageBalanceScore,
            headline = result.headline,
            insight = result.insight,
            priority = result.priority,
            interpretedSleepMinutes = result.interpretedSleepMinutes,
            confidencePct = confidence.score,
            confidenceLabel = confidence.label
        )
    }

    fun analyse(
        totalMinutes: Int,
        awakeMinutes: Int,
        deepMinutes: Int,
        remMinutes: Int,
        lightMinutes: Int,
        interruptionCount: Int = 0
    ): SleepAnalysis {
        val snapshot = NativeSleepSnapshot(
            totalMinutes = totalMinutes,
            awakeMinutes = awakeMinutes,
            deepMinutes = deepMinutes,
            remMinutes = remMinutes,
            lightMinutes = lightMinutes,
            stageSegments = List(interruptionCount.coerceAtMost(10)) {
                SleepStageSegment("awake", 0L, 5 * 60_000L)
            }
        )
        return analyse(snapshot)
    }
}
