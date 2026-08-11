from pathlib import Path

root = Path('.')
engine = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepIntelligenceEngine.kt'
hero = root / 'nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepNightDashboardHero.kt'
gradle = root / 'nextgen/androidApp/build.gradle.kts'

s = engine.read_text()
s = s.replace(
'''        val stageCoverage = if (recordedSleep > 0) {
            (stageTotal.toDouble() / recordedSleep.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0

        // We only compensate for source uncertainty. We do NOT invent sleep minutes.''',
'''        val stageCoverage = if (recordedSleep > 0) {
            (stageTotal.toDouble() / recordedSleep.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        val gapSegments = snapshot.stageSegments.filter { it.type == "gap" }
        val gapMinutes = gapSegments.sumOf { ((it.endMs - it.startMs) / 60_000L).coerceAtLeast(0L) }.toInt()

        // We only compensate for source uncertainty. We do NOT invent sleep minutes.'''
)
s = s.replace(
'''        val opportunity = (recordedSleep + recordedAwake).coerceAtLeast(recordedSleep)''',
'''        // Distinct Health Connect records can belong to one human night. Time between
        // those blocks is real lost sleep opportunity even when the source does not label
        // it as an Awake stage (Samsung commonly behaves this way).
        val opportunity = (recordedSleep + recordedAwake + gapMinutes).coerceAtLeast(recordedSleep)'''
)
s = s.replace(
'''    private fun continuityScore(efficiency: Int, snapshot: NativeSleepSnapshot): Int {
        val interruptionCount = snapshot.stageSegments.count {
            it.type == "awake" && (it.endMs - it.startMs) >= 5 * 60_000L
        }
        val penalty = min(20, interruptionCount * 4)
        return (efficiency - penalty).coerceIn(0, 100)
    }''',
'''    private fun continuityScore(efficiency: Int, snapshot: NativeSleepSnapshot): Int {
        val awakeInterruptions = snapshot.stageSegments.count {
            it.type == "awake" && (it.endMs - it.startMs) >= 5 * 60_000L
        }
        val gaps = snapshot.stageSegments.filter { it.type == "gap" }
        val gapMinutes = gaps.sumOf { ((it.endMs - it.startMs) / 60_000L).coerceAtLeast(0L) }.toInt()
        val interruptionPenalty = min(20, (awakeInterruptions + gaps.size) * 4)
        val longGapPenalty = min(18, gapMinutes / 6)
        return (efficiency - interruptionPenalty - longGapPenalty).coerceIn(0, 100)
    }'''
)
s = s.replace(
'''        if (snapshot.stageSegments.count { it.type == "awake" } > 8) score -= 4
        return score.coerceIn(40, 96)''',
'''        if (snapshot.stageSegments.count { it.type == "awake" } > 8) score -= 4
        if (snapshot.stageSegments.any { it.type == "gap" }) score -= 3
        return score.coerceIn(40, 96)'''
)
engine.write_text(s)

h = hero.read_text()
h = h.replace(
'''    val durationDelta = if (total != null && recentMinutes != null) total - recentMinutes else null
    val scoreDelta = if (score != null && recentScore != null) score - recentScore else null

    val historicalMessage = when {''',
'''    val durationDelta = if (total != null && recentMinutes != null) total - recentMinutes else null
    val scoreDelta = if (score != null && recentScore != null) score - recentScore else null
    val gapSegments = s?.stageSegments.orEmpty().filter { it.type == "gap" }
    val gapMinutes = gapSegments.sumOf { ((it.endMs - it.startMs) / 60_000L).coerceAtLeast(0L) }.toInt()
    val sleepBlockCount = if (s?.totalMinutes != null) gapSegments.size + 1 else 0

    val historicalMessage = when {'''
)
h = h.replace(
'''        s == null || total == null -> "Connect sleep data to unlock your nightly intelligence."
        durationDelta != null && durationDelta >= 30 ->''',
'''        s == null || total == null -> "Connect sleep data to unlock your nightly intelligence."
        sleepBlockCount > 1 -> "Your sleep was split into $sleepBlockCount blocks with ${minutesCompact(gapMinutes)} between them."
        durationDelta != null && durationDelta >= 30 ->'''
)
h = h.replace(
'''                    detail = when {
                        durationDelta == null -> "sleep"
                        durationDelta > 0 -> "+${minutesCompact(durationDelta)} vs usual"''',
'''                    detail = when {
                        sleepBlockCount > 1 -> "$sleepBlockCount blocks · ${minutesCompact(gapMinutes)} gap"
                        durationDelta == null -> "sleep"
                        durationDelta > 0 -> "+${minutesCompact(durationDelta)} vs usual"'''
)
hero.write_text(h)

g = gradle.read_text().replace('versionCode = 11201', 'versionCode = 11203').replace('versionName = "11.2.1"', 'versionName = "11.2.3"')
gradle.write_text(g)
