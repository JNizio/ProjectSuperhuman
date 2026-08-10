from pathlib import Path

p = Path('nextgen/androidApp/src/main/java/com/projectsuperhuman/next/SleepHistoryPage.kt')
s = p.read_text()

if 'var dashboardView by remember' not in s:
    s = s.replace(
        '    var status by remember { mutableStateOf("Loading sleep history…") }\n',
        '    var status by remember { mutableStateOf("Loading sleep history…") }\n    var dashboardView by remember { mutableStateOf(HistoryDataView.INTERPRETED) }\n',
        1,
    )

s = s.replace(
    '        HistorySleepDashboardHero(nights)\n',
    '        HistorySleepDashboardHero(nights, selected, dashboardView) { dashboardView = it }\n',
    1,
)

s = s.replace(
    '\n        selected?.let { HistoricalSleepDetail(it.snapshot, it.wakeDate) }\n            ?: EmptyHistoryCard(nights.isNotEmpty())\n',
    '\n',
    1,
)

start = s.index('@Composable\nprivate fun HistorySleepDashboardHero')
end = s.index('@Composable\nprivate fun HistoryHeader', start)

hero = r'''@Composable
private fun HistorySleepDashboardHero(
    nights: List<HistoricalSleepNight>,
    selected: HistoricalSleepNight?,
    view: HistoryDataView,
    onViewChange: (HistoryDataView) -> Unit
) {
    val active = selected ?: nights.maxByOrNull { it.endEpochMs }
    val snapshot = active?.snapshot
    val analysis = snapshot?.let { SleepIntelligenceEngine.analyse(it) }
    val previous = active?.let { chosen ->
        nights.filter { it.endEpochMs < chosen.endEpochMs }.sortedByDescending { it.endEpochMs }.take(7)
    } ?: emptyList()
    val recentMinutes = previous.mapNotNull { it.snapshot.totalMinutes }
    val recentScores = previous.map { SleepIntelligenceEngine.analyse(it.snapshot).recoveryScore }
    val avgMinutes = recentMinutes.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val avgScore = recentScores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val totalMinutes = snapshot?.totalMinutes
    val deltaMinutes = if (totalMinutes != null && avgMinutes != null) totalMinutes - avgMinutes else null
    val sleepScore = analysis?.recoveryScore ?: snapshot?.score
    val deepRem = snapshot?.let { (it.deepMinutes ?: 0) + (it.remMinutes ?: 0) }
    val bedTime = snapshot?.startEpochMs?.let(::historyHeroTime) ?: "—"
    val wakeTime = snapshot?.endEpochMs?.let(::historyHeroTime) ?: "—"
    val dateLabel = active?.wakeDate?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) ?: "No sleep selected"
    val comparison = when {
        deltaMinutes == null -> "Build more sleep history to establish your personal baseline."
        deltaMinutes > 20 -> "You slept ${formatHistoryMinutes(deltaMinutes)} longer than your recent baseline."
        deltaMinutes < -20 -> "You slept ${formatHistoryMinutes(-deltaMinutes)} less than your recent baseline."
        else -> "Your sleep duration was close to your recent baseline."
    }
    val focus = analysis?.priority ?: "Keep syncing sleep to unlock personalised trends."
    val scoreLabel = when {
        sleepScore == null -> "Building"
        sleepScore >= 85 -> "Excellent"
        sleepScore >= 75 -> "Strong"
        sleepScore >= 60 -> "Fair"
        else -> "Needs recovery"
    }

    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF08152F), Color(0xFF162D66), Color(0xFF41348F))),
            RoundedCornerShape(28.dp)
        ).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("✦  SLEEP DASHBOARD", color = Color.White.copy(alpha = .64f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text(dateLabel, color = Color.White.copy(alpha = .82f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            DashboardViewToggle(view, onViewChange)
        }

        if (view == HistoryDataView.INTERPRETED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 14.dp)) {
                    Text(formatHistoryMinutes(totalMinutes), color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(5.dp))
                    Text(analysis?.headline ?: "Your night sky is still gathering data", color = Color.White.copy(alpha = .86f), fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SLEEP SCORE", color = Color.White.copy(alpha = .67f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(sleepScore?.toString() ?: "—", color = Color.White, fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Black)
                    Text(scoreLabel, color = if ((sleepScore ?: 0) >= 75) HistoryGood else Color.White.copy(alpha = .64f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryHeroStat("Bed", bedTime, Modifier.weight(1f))
                HistoryHeroStat("Wake", wakeTime, Modifier.weight(1f))
                HistoryHeroStat("7-night avg", formatHistoryMinutes(avgMinutes), Modifier.weight(1f))
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .14f)))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryHeroScoreStat("Duration", analysis?.durationScore, Modifier.weight(1f))
                HistoryHeroScoreStat("Continuity", analysis?.continuityScore, Modifier.weight(1f))
                HistoryHeroScoreStat("Stages", analysis?.stageBalanceScore, Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(comparison, color = Color.White.copy(alpha = .72f), fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatHistoryMinutes(deepRem), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Deep + REM", color = Color.White.copy(alpha = .56f), fontSize = 7.sp)
                }
            }

            Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .09f), RoundedCornerShape(14.dp)).padding(11.dp)) {
                Text("Tonight's focus: $focus", color = Color.White.copy(alpha = .90f), fontSize = 9.sp, lineHeight = 13.sp)
            }

            Text(
                if (avgScore != null) "✦ Recent sleep-score baseline $avgScore   ·   personalised from your history" else "✦ Personal baseline building",
                color = Color.White.copy(alpha = .53f),
                fontSize = 7.sp
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("RAW HEALTH CONNECT DATA", color = Color.White.copy(alpha = .65f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(formatHistoryMinutes(totalMinutes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("recorded sleep", color = Color.White.copy(alpha = .62f), fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SOURCE SCORE", color = Color.White.copy(alpha = .62f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Text(snapshot?.score?.toString() ?: "—", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryHeroStat("Bed", bedTime, Modifier.weight(1f))
                HistoryHeroStat("Wake", wakeTime, Modifier.weight(1f))
                HistoryHeroStat("Awake", formatHistoryMinutes(snapshot?.awakeMinutes), Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryHeroStat("Light", formatHistoryMinutes(snapshot?.lightMinutes), Modifier.weight(1f))
                HistoryHeroStat("Deep", formatHistoryMinutes(snapshot?.deepMinutes), Modifier.weight(1f))
                HistoryHeroStat("REM", formatHistoryMinutes(snapshot?.remMinutes), Modifier.weight(1f))
            }

            Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(11.dp)) {
                Text("Raw view shows the values imported from the sleep source. Interpretation, scoring and historical context are intentionally hidden here.", color = Color.White.copy(alpha = .78f), fontSize = 8.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun DashboardViewToggle(view: HistoryDataView, onChange: (HistoryDataView) -> Unit) {
    Row(
        Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(HistoryDataView.INTERPRETED to "INSIGHT", HistoryDataView.RAW to "RAW").forEach { (item, label) ->
            Box(
                Modifier.background(if (view == item) Color.White.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(9.dp))
                    .superhumanClickable { onChange(item) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White.copy(alpha = if (view == item) .98f else .62f), fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun HistoryHeroScoreStat(label: String, value: Int?, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .075f), RoundedCornerShape(14.dp)).padding(9.dp)) {
        Text(value?.toString() ?: "—", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text("$label /100", color = Color.White.copy(alpha = .55f), fontSize = 7.sp)
    }
}

private fun historyHeroTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

'''

s = s[:start] + hero + s[end:]
p.write_text(s)

gradle = Path('nextgen/androidApp/build.gradle.kts')
g = gradle.read_text()
if 'versionCode = 11011' in g:
    g = g.replace('versionCode = 11011', 'versionCode = 11012')
    g = g.replace('versionName = "11.0.11"', 'versionName = "11.0.12"')
gradle.write_text(g)
