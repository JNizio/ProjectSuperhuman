package com.projectsuperhuman.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HistoryPurple = Color(0xFF6753D8)
private val HistoryBlue = Color(0xFF4777D9)
private val HistoryInk = Color(0xFF17233A)
private val HistoryMuted = Color(0xFF718096)
private val HistoryGood = Color(0xFF42A58C)
private val HistoryWarn = Color(0xFFE29B55)
private val HistoryBg = Color(0xFFF7F8FC)
private val HistoryBorder = Color(0xFFE6E9F2)

internal data class HistoricalSleepNight(
    val wakeDate: LocalDate,
    val endEpochMs: Long,
    val snapshot: NativeSleepSnapshot
)

private enum class HistoryDataView { INTERPRETED, RAW }

@Composable
internal fun NativeSleepHistoryPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nights by remember { mutableStateOf<List<HistoricalSleepNight>>(emptyList()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var syncing by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading sleep history…") }

    suspend fun refreshHistory() {
        nights = NativeHistoricalSleepStore.loadAll()
        if (selectedDate == null) selectedDate = nights.maxByOrNull { it.endEpochMs }?.wakeDate
        selectedDate?.let { month = YearMonth.from(it) }
        status = if (nights.isEmpty()) "No historical sleep records yet" else "${nights.size} sleep records available"
    }

    suspend fun sync() {
        syncing = true
        val result = SleepHealthConnect.sync(context)
        connected = SleepHealthConnect.hasPermission(context)
        status = result.message
        refreshHistory()
        syncing = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (SleepHealthConnect.permission in granted) scope.launch { sync() }
        else status = "Sleep access wasn’t enabled"
    }

    fun connectOrSync() {
        scope.launch {
            when (SleepHealthConnect.availability(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (SleepHealthConnect.hasPermission(context)) sync()
                    else permissionLauncher.launch(setOf(SleepHealthConnect.permission))
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> status = "Health Connect needs an update"
                else -> status = "Health Connect isn’t available on this device"
            }
        }
    }

    LaunchedEffect(Unit) {
        connected = SleepHealthConnect.hasPermission(context)
        refreshHistory()
        if (connected) sync()
    }

    val selected = nights.firstOrNull { it.wakeDate == selectedDate }

    Column(
        Modifier.fillMaxSize().background(HistoryBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HistoryHeader(onBack)
        SleepCalendarCard(
            month = month,
            nights = nights,
            selectedDate = selectedDate,
            onPrevious = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
            onSelect = { selectedDate = it }
        )

        selected?.let { HistoricalSleepDetail(it.snapshot, it.wakeDate) }
            ?: EmptyHistoryCard(nights.isNotEmpty())

        HistorySyncCard(connected, syncing, status, ::connectOrSync)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HistoryHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(44.dp).height(44.dp)
                .background(Color.White, RoundedCornerShape(15.dp))
                .border(1.dp, HistoryBorder, RoundedCornerShape(15.dp))
                .superhumanClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "←",
                color = HistoryPurple,
                fontSize = 25.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Sleep", color = HistoryInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("History, patterns and previous nights", color = HistoryMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SleepCalendarCard(
    month: YearMonth,
    nights: List<HistoricalSleepNight>,
    selectedDate: LocalDate?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = buildList<LocalDate?> {
        repeat(leading) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        while (size % 7 != 0) add(null)
    }
    val nightByDate = nights.associateBy { it.wakeDate }

    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, HistoryBorder, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CalendarArrow("‹", onPrevious)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = HistoryInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("Tap a night to inspect it", color = HistoryMuted, fontSize = 8.sp)
            }
            CalendarArrow("›", onNext)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.width(28.dp), color = HistoryMuted, fontSize = 8.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(250.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(cells) { date ->
                if (date == null) {
                    Spacer(Modifier.height(34.dp))
                } else {
                    val night = nightByDate[date]
                    val isSelected = date == selectedDate
                    val today = date == LocalDate.now()
                    val score = night?.snapshot?.score
                    val background = when {
                        isSelected -> HistoryPurple
                        night != null -> HistoryPurple.copy(alpha = .10f)
                        else -> Color.Transparent
                    }
                    Column(
                        Modifier.fillMaxWidth().height(34.dp)
                            .background(background, RoundedCornerShape(10.dp))
                            .superhumanClickable(enabled = night != null, onClick = { onSelect(date) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            color = if (isSelected) Color.White else HistoryInk,
                            fontSize = 9.sp,
                            fontWeight = if (today) FontWeight.Black else FontWeight.Bold
                        )
                        if (night != null) {
                            Box(Modifier.width(5.dp).height(5.dp).background(if (isSelected) Color.White else scoreColor(score), CircleShape))
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarLegendDot(HistoryPurple, "Sleep recorded")
            CalendarLegendDot(HistoryGood, "Better recovery")
            CalendarLegendDot(HistoryWarn, "Lower recovery")
        }
    }
}

@Composable
private fun CalendarArrow(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.width(36.dp).height(36.dp).background(HistoryBg, CircleShape).superhumanClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            modifier = Modifier.width(24.dp),
            color = HistoryPurple,
            fontSize = 24.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalendarLegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(7.dp).height(7.dp).background(color, CircleShape))
        Text(label, color = HistoryMuted, fontSize = 7.sp)
    }
}

private fun scoreColor(score: Int?): Color = when {
    score == null -> HistoryPurple
    score >= 75 -> HistoryGood
    score >= 50 -> HistoryPurple
    else -> HistoryWarn
}

@Composable
private fun HistoricalSleepDetail(snapshot: NativeSleepSnapshot, date: LocalDate) {
    val analysis = SleepIntelligenceEngine.analyse(snapshot)
    var view by remember(date) { mutableStateOf(HistoryDataView.INTERPRETED) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SleepViewToggle(view = view, onChange = { view = it })

        when (view) {
            HistoryDataView.INTERPRETED -> InterpretedSleepView(snapshot, date, analysis)
            HistoryDataView.RAW -> RawSleepView(snapshot, date, analysis)
        }
    }
}

@Composable
private fun SleepViewToggle(view: HistoryDataView, onChange: (HistoryDataView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, HistoryBorder, RoundedCornerShape(18.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        SleepViewTab("INTERPRETED", view == HistoryDataView.INTERPRETED, Modifier.weight(1f)) {
            onChange(HistoryDataView.INTERPRETED)
        }
        SleepViewTab("RAW DATA", view == HistoryDataView.RAW, Modifier.weight(1f)) {
            onChange(HistoryDataView.RAW)
        }
    }
}

@Composable
private fun SleepViewTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) HistoryPurple else Color.Transparent, RoundedCornerShape(14.dp))
            .superhumanClickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else HistoryMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .4.sp
        )
    }
}

@Composable
private fun InterpretedSleepView(snapshot: NativeSleepSnapshot, date: LocalDate, analysis: SleepIntelligenceResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(BrushlessPurple(), RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Color.White.copy(alpha = .70f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(analysis.headline, color = Color.White, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(analysis.recoveryScore.toString(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("interpreted score", color = Color.White.copy(alpha = .68f), fontSize = 8.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HistoryHeroStat("Asleep", formatHistoryMinutes(snapshot.totalMinutes), Modifier.weight(1f))
                HistoryHeroStat("Deep", formatHistoryMinutes(snapshot.deepMinutes), Modifier.weight(1f))
                HistoryHeroStat("REM", formatHistoryMinutes(snapshot.remMinutes), Modifier.weight(1f))
            }
        }

        InfoCard("Smart interpretation", analysis.insight) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HistoryMiniStat(analysis.durationScore.toString(), "duration")
                HistoryMiniStat(analysis.continuityScore.toString(), "continuity")
                HistoryMiniStat(analysis.stageBalanceScore.toString(), "stage balance")
            }
            Box(Modifier.fillMaxWidth().background(HistoryPurple.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(11.dp)) {
                Text("Focus: ${analysis.priority}", color = HistoryPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        InfoCard("How this differs from raw data", "The interpreted view keeps the recorded sleep data intact, then layers Project Superhuman's sleep engine on top to estimate recovery quality and highlight the most useful next action.") { }

        ConfidenceCard(analysis)
    }
}

@Composable
private fun RawSleepView(snapshot: NativeSleepSnapshot, date: LocalDate, analysis: SleepIntelligenceResult) {
    val start = snapshot.startEpochMs?.let(::historyTime)
    val end = snapshot.endEpochMs?.let(::historyTime)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Raw sleep record", color = HistoryInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = HistoryMuted, fontSize = 9.sp)
                    if (start != null && end != null) Text("$start – $end", color = HistoryMuted, fontSize = 9.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(snapshot.score?.toString() ?: "—", color = HistoryPurple, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("source score", color = HistoryMuted, fontSize = 8.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HistoryMiniStat(formatHistoryMinutes(snapshot.totalMinutes), "asleep")
                HistoryMiniStat("${snapshot.efficiencyPct ?: analysis.efficiencyPct}%", "efficiency")
                HistoryMiniStat(formatHistoryMinutes(snapshot.awakeMinutes), "awake")
            }
        }

        SleepArchitectureDiagram(snapshot.stageSegments, snapshot.startEpochMs, snapshot.endEpochMs)

        Column(
            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("Recorded stage balance", color = HistoryInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Direct values from the imported sleep record — no interpretation applied.", color = HistoryMuted, fontSize = 9.sp)
            HistoryStageRow("Light", snapshot.lightMinutes, HistoryBlue)
            HistoryStageRow("Deep", snapshot.deepMinutes, HistoryGood)
            HistoryStageRow("REM", snapshot.remMinutes, HistoryPurple)
            HistoryStageRow("Awake", snapshot.awakeMinutes, HistoryWarn)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HistoryMiniStat(snapshot.stageSegments.size.toString(), "stage segments")
                HistoryMiniStat(snapshot.sessionsImported.toString(), "sessions imported")
                HistoryMiniStat(formatHistoryMinutes(snapshot.recentAverageMinutes), "recent avg")
            }
        }

        ConfidenceCard(analysis)
    }
}

@Composable
private fun InfoCard(title: String, body: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = HistoryInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(body, color = HistoryMuted, fontSize = 10.sp, lineHeight = 15.sp)
        content()
    }
}

@Composable
private fun ConfidenceCard(analysis: SleepIntelligenceResult) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("Data confidence", color = HistoryInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text("Confidence is based on the information available from the source records.", color = HistoryMuted, fontSize = 9.sp)
        analysis.confidence.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.metric.replaceFirstChar { it.uppercase() }, color = HistoryInk, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("${item.score}%", color = HistoryInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(item.label, color = if (item.score >= 75) HistoryGood else HistoryWarn, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryHeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(10.dp)) {
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = .62f), fontSize = 8.sp)
    }
}

@Composable
private fun HistoryMiniStat(value: String, label: String) {
    Column {
        Text(value, color = HistoryInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = HistoryMuted, fontSize = 8.sp)
    }
}

@Composable
private fun HistoryStageRow(name: String, minutes: Int?, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(name, color = HistoryInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(formatHistoryMinutes(minutes), color = HistoryMuted, fontSize = 10.sp)
    }
}

@Composable
private fun HistorySyncCard(connected: Boolean, syncing: Boolean, status: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Health Connect", color = HistoryInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(if (connected) "Sleep history is connected and ready to refresh" else "Connect your sleep data to build history", color = HistoryMuted, fontSize = 9.sp, lineHeight = 14.sp)
            }
            Box(Modifier.background(if (connected) HistoryGood.copy(alpha = .12f) else HistoryPurple.copy(alpha = .10f), RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                Text(if (connected) "CONNECTED" else "NOT CONNECTED", color = if (connected) HistoryGood else HistoryPurple, fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }
        Box(
            Modifier.fillMaxWidth().background(if (connected) Color(0xFFF0F3FF) else HistoryPurple, RoundedCornerShape(15.dp))
                .superhumanClickable(enabled = !syncing, onClick = onAction).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (syncing) "SYNCING…" else "SYNC NOW", color = if (connected) HistoryPurple else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Text(status, color = HistoryMuted, fontSize = 8.sp)
    }
}

@Composable
private fun EmptyHistoryCard(hasHistory: Boolean) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Text(if (hasHistory) "No sleep on this date" else "No sleep history yet", color = HistoryInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(if (hasHistory) "Choose a highlighted date in the calendar to inspect that sleep episode." else "Sync Health Connect after a recorded night and previous nights will appear here.", color = HistoryMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

private fun formatHistoryMinutes(minutes: Int?): String {
    if (minutes == null) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun historyTime(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()
    .format(DateTimeFormatter.ofPattern("HH:mm"))

private fun BrushlessPurple(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF4939A9), Color(0xFF6F5BE1)))

private object NativeHistoricalSleepStore {
    suspend fun loadAll(): List<HistoricalSleepNight> {
        val values = NativeDataHub.allValuesAsync()
            .filter { it.domain == com.projectsuperhuman.next.core.HealthDomain.SLEEP }
            .filter { it.metric.startsWith("sleep_") }

        val nights = values
            .filter { it.metric == "sleep_total_minutes" && it.metadata["nightEnd"] != null }
            .groupBy { it.metadata["nightEnd"]!! }
            .mapNotNull { (nightEndRaw, _) ->
                val end = nightEndRaw.toLongOrNull() ?: return@mapNotNull null
                val metrics = values.filter { it.metadata["nightEnd"] == nightEndRaw }
                val metric = { name: String -> metrics.firstOrNull { it.metric == name } }
                val start = metric("sleep_start_epoch_ms")?.value?.toLong()
                    ?: metrics.firstOrNull { it.metric == "sleep_start_epoch_ms" }?.value?.toLong()
                    ?: return@mapNotNull null
                val timeline = metric("sleep_stage_timeline")?.metadata?.get("segments")
                val snapshot = NativeSleepSnapshot(
                    score = metric("sleep_score")?.value?.roundToInt(),
                    totalMinutes = metric("sleep_total_minutes")?.value?.roundToInt(),
                    awakeMinutes = metric("sleep_awake_minutes")?.value?.roundToInt(),
                    lightMinutes = metric("sleep_light_minutes")?.value?.roundToInt(),
                    deepMinutes = metric("sleep_deep_minutes")?.value?.roundToInt(),
                    remMinutes = metric("sleep_rem_minutes")?.value?.roundToInt(),
                    efficiencyPct = metric("sleep_efficiency_pct")?.value?.roundToInt(),
                    startEpochMs = start,
                    endEpochMs = end,
                    stageSegments = parseSleepStageSegments(timeline),
                    sessionsImported = 1,
                    recentAverageMinutes = null,
                    recentAverageScore = null
                )
                HistoricalSleepNight(
                    wakeDate = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate(),
                    endEpochMs = end,
                    snapshot = snapshot
                )
            }
            .sortedByDescending { it.endEpochMs }

        return nights.distinctBy { it.endEpochMs }
    }
}
