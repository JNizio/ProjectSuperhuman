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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val SleepPurple = Color(0xFF6753D8)
private val SleepBlue = Color(0xFF4777D9)
private val SleepInk = Color(0xFF17233A)
private val SleepMuted = Color(0xFF718096)
private val SleepGood = Color(0xFF42A58C)
private val SleepWarn = Color(0xFFE29B55)
private val SleepBg = Color(0xFFF7F8FC)
private val SleepBorder = Color(0xFFE6E9F2)

internal data class NativeSleepSnapshot(
    val score: Int? = null,
    val totalMinutes: Int? = null,
    val sleepTimeMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val remMinutes: Int? = null,
    val efficiencyPct: Int? = null,
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
    val stageSegments: List<SleepStageSegment> = emptyList(),
    val sessionsImported: Int = 0,
    val recentAverageMinutes: Int? = null,
    val recentAverageScore: Int? = null
)

private enum class SleepViewMode { RECORDED, INTERPRETED }

@Composable
internal fun NativeSleepParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NativeSleepSnapshot?>(null) }
    var connected by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var connectionChecked by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking Health Connect…") }
    var viewMode by remember { mutableStateOf(SleepViewMode.INTERPRETED) }

    suspend fun refresh() { snapshot = NativeSleepStore.loadLatest() }

    suspend fun sync() {
        syncing = true
        val result = SleepHealthConnect.sync(context)
        syncing = false
        status = result.message
        connected = SleepHealthConnect.hasPermission(context)
        refresh()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (SleepHealthConnect.permission in granted) {
            connected = true
            status = "Connected"
            scope.launch { sync() }
        } else {
            connected = false
            status = "Sleep access wasn’t enabled"
        }
    }

    fun connectOrSync() {
        scope.launch {
            when (SleepHealthConnect.availability(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (SleepHealthConnect.hasPermission(context)) sync()
                    else permissionLauncher.launch(setOf(SleepHealthConnect.permission))
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    status = "Health Connect needs an update before sleep can sync"
                else -> status = "Health Connect isn’t available on this device"
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        val available = SleepHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE
        connected = available && SleepHealthConnect.hasPermission(context)
        connectionChecked = true
        status = when {
            !available -> "Health Connect isn’t available on this device"
            connected -> "Connected"
            else -> "Connect to bring in sleep from your watch or health apps"
        }
        if (connected) sync()
    }

    val s = snapshot
    Column(
        Modifier.fillMaxSize().background(SleepBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SleepHeader(onBack)
        SleepNightDashboardHero(s)
        if (s?.totalMinutes != null) {
            SleepViewToggle(viewMode) { viewMode = it }
            SleepNightOverview(s, viewMode)
            SleepArchitectureDiagram(s.stageSegments, s.startEpochMs, s.endEpochMs)
            SleepStageCard(s, viewMode)
            SleepSmartInsightCard(s, viewMode)
            SleepConfidenceCard(s)
            SleepTrendCard(s)
        } else {
            EmptySleepCard(connected)
        }
        HealthConnectCard(connected, connectionChecked, syncing, status, ::connectOrSync)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SleepHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(44.dp).height(44.dp)
                .background(Color.White, RoundedCornerShape(15.dp))
                .border(1.dp, SleepBorder, RoundedCornerShape(15.dp))
                .superhumanClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "‹",
                modifier = Modifier.width(28.dp),
                color = SleepPurple,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Sleep", color = SleepInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Your nights, recovery and sleep stages", color = SleepMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SleepHero(s: NativeSleepSnapshot?, mode: SleepViewMode) {
    val analysis = s?.let { SleepIntelligenceEngine.analyse(it) }
    val message = analysis?.headline ?: "Your sleep summary will appear here"
    val score = analysis?.recoveryScore ?: s?.score
    val asleep = if (mode == SleepViewMode.RECORDED) formatMinutes(s?.totalMinutes) else formatMinutes(analysis?.interpretedSleepMinutes)
    val deep = if (mode == SleepViewMode.RECORDED) formatMinutes(s?.deepMinutes) else analysis?.let { "${it.deepPct}%" } ?: "—"
    val rem = if (mode == SleepViewMode.RECORDED) formatMinutes(s?.remMinutes) else analysis?.let { "${it.remPct}%" } ?: "—"

    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF4939A9), Color(0xFF6F5BE1))),
            RoundedCornerShape(28.dp)
        ).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    if (mode == SleepViewMode.RECORDED) "RECORDED SLEEP" else "SMART SLEEP",
                    color = Color.White.copy(alpha = .64f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(message, color = Color.White, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
            }
            if (score != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(score.toString(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("sleep score", color = Color.White.copy(alpha = .68f), fontSize = 8.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepHeroStat("Asleep", asleep, Modifier.weight(1f))
            SleepHeroStat("Deep", deep, Modifier.weight(1f))
            SleepHeroStat("REM", rem, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SleepHeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = .62f), fontSize = 8.sp)
    }
}

@Composable
private fun SleepViewToggle(mode: SleepViewMode, onModeChanged: (SleepViewMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SleepModeButton("Recorded", mode == SleepViewMode.RECORDED, Modifier.weight(1f)) {
            onModeChanged(SleepViewMode.RECORDED)
        }
        SleepModeButton("Interpreted", mode == SleepViewMode.INTERPRETED, Modifier.weight(1f)) {
            onModeChanged(SleepViewMode.INTERPRETED)
        }
    }
}

@Composable
private fun SleepModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) SleepPurple else Color.Transparent, RoundedCornerShape(12.dp))
            .superhumanClickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else SleepMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SleepNightOverview(s: NativeSleepSnapshot, mode: SleepViewMode) {
    val start = s.startEpochMs?.let(::formatSleepTime)
    val end = s.endEpochMs?.let(::formatSleepTime)
    val analysis = SleepIntelligenceEngine.analyse(s)
    val efficiency = if (mode == SleepViewMode.RECORDED) s.efficiencyPct else analysis.efficiencyPct
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Last night", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                if (start != null && end != null) Text("$start – $end", color = SleepMuted, fontSize = 9.sp)
            }
            Text(
                if (mode == SleepViewMode.RECORDED) formatMinutes(s.totalMinutes) else formatMinutes(analysis.interpretedSleepMinutes),
                color = SleepPurple, fontSize = 17.sp, fontWeight = FontWeight.Black
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SleepMiniStat("${efficiency ?: 0}%", "efficiency")
            SleepMiniStat(formatMinutes(s.awakeMinutes), "awake")
            SleepMiniStat(s.stageSegments.size.toString(), "stage shifts")
        }
    }
}

@Composable
private fun SleepStageCard(s: NativeSleepSnapshot, mode: SleepViewMode) {
    val analysis = SleepIntelligenceEngine.analyse(s)
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("Stage balance", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(
            if (mode == SleepViewMode.RECORDED) "What the connected source reported" else "How the engine interpreted the recorded stages",
            color = SleepMuted, fontSize = 9.sp
        )
        Spacer(Modifier.height(10.dp))
        if (mode == SleepViewMode.RECORDED) {
            StageRow("Light", s.lightMinutes, SleepBlue)
            StageRow("Deep", s.deepMinutes, SleepGood)
            StageRow("REM", s.remMinutes, SleepPurple)
            StageRow("Awake", s.awakeMinutes, SleepWarn)
        } else {
            StagePercentRow("Light", if ((s.totalMinutes ?: 0) > 0) ((s.lightMinutes ?: 0) * 100 / (s.totalMinutes ?: 1)) else null, SleepBlue)
            StagePercentRow("Deep", analysis.deepPct, SleepGood)
            StagePercentRow("REM", analysis.remPct, SleepPurple)
            StagePercentRow("Awake", analysis.awakePct, SleepWarn)
        }
    }
}

@Composable
private fun StagePercentRow(name: String, percent: Int?, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(9.dp))
        Text(name, color = SleepInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(if (percent == null) "—" else "$percent%", color = SleepMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SleepSmartInsightCard(s: NativeSleepSnapshot, mode: SleepViewMode) {
    val analysis = SleepIntelligenceEngine.analyse(s)
    val confidence = analysis.confidenceFor("interpretation")
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Smart sleep insight", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("A calculated view layered over the recorded data", color = SleepMuted, fontSize = 9.sp)
            }
            Box(
                Modifier.background(SleepPurple.copy(alpha = .09f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) { Text("${confidence.label} confidence", color = SleepPurple, fontSize = 7.sp, fontWeight = FontWeight.Black) }
        }
        Text(analysis.insight, color = SleepMuted, fontSize = 10.sp, lineHeight = 15.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SleepMiniStat(analysis.durationScore.toString(), "duration")
            SleepMiniStat(analysis.continuityScore.toString(), "continuity")
            SleepMiniStat(analysis.stageBalanceScore.toString(), "stage balance")
        }
        Box(
            Modifier.fillMaxWidth().background(SleepPurple.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(11.dp)
        ) {
            Text("Focus: ${analysis.priority}", color = SleepPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            if (mode == SleepViewMode.INTERPRETED) "The interpreted view uses the same source records, with confidence-aware calculations layered on top." else "Switch to Interpreted to see the engine's confidence-aware view.",
            color = SleepMuted.copy(alpha = .9f), fontSize = 8.sp, lineHeight = 12.sp
        )
    }
}

@Composable
private fun SleepConfidenceCard(s: NativeSleepSnapshot) {
    val analysis = SleepIntelligenceEngine.analyse(s)
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Data confidence", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text("How much detail the source gives the engine to work with", color = SleepMuted, fontSize = 9.sp)
        analysis.confidence.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.metric.replaceFirstChar { it.uppercase() }, color = SleepInk, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("${item.score}%", color = SleepInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(item.label, color = if (item.score >= 75) SleepGood else SleepWarn, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SleepTrendCard(s: NativeSleepSnapshot) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Personal pattern", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(
            if (s.sessionsImported >= 7) "Your recent history is starting to establish a personal sleep baseline."
            else "More nights will make your personal baseline more useful.",
            color = SleepMuted, fontSize = 9.sp, lineHeight = 14.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SleepMiniStat(formatMinutes(s.recentAverageMinutes), "avg sleep")
            SleepMiniStat(s.recentAverageScore?.toString() ?: "—", "avg score")
            SleepMiniStat(s.sessionsImported.toString(), "nights")
        }
    }
}

@Composable
private fun HealthConnectCard(connected: Boolean, checked: Boolean, syncing: Boolean, status: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Health Connect", color = SleepInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(
                    if (connected) "Sleep data is connected and ready to sync" else "Connect your sleep data securely",
                    color = SleepMuted, fontSize = 9.sp, lineHeight = 14.sp
                )
            }
            Box(
                Modifier.background(if (connected) SleepGood.copy(alpha = .12f) else SleepPurple.copy(alpha = .10f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Text(
                    when { !checked -> "CHECKING"; connected -> "CONNECTED"; else -> "NOT CONNECTED" },
                    color = if (connected) SleepGood else SleepPurple,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().background(if (connected) Color(0xFFF0F3FF) else SleepPurple, RoundedCornerShape(15.dp))
                .superhumanClickable(enabled = !syncing, onClick = onAction).padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when { syncing -> "SYNCING…"; connected -> "SYNC NOW"; else -> "CONNECT HEALTH CONNECT" },
                color = if (connected) SleepPurple else Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(status, color = SleepMuted, fontSize = 8.sp)
    }
}

@Composable
private fun EmptySleepCard(connected: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(18.dp)
    ) {
        Text("No sleep data yet", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(
            if (connected) "Sync after your next recorded night, or check that your sleep app is sharing data with Health Connect."
            else "Connect Health Connect to import sleep recorded by compatible apps and devices.",
            color = SleepMuted, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun SleepMiniStat(value: String, label: String) {
    Column {
        Text(value, color = SleepInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = SleepMuted, fontSize = 8.sp)
    }
}

@Composable
private fun StageRow(name: String, minutes: Int?, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(9.dp))
        Text(name, color = SleepInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(formatMinutes(minutes), color = SleepMuted, fontSize = 10.sp)
    }
}

private fun formatMinutes(minutes: Int?): String {
    if (minutes == null) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatSleepTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private object NativeSleepStore {
    suspend fun loadLatest(): NativeSleepSnapshot {
        val values = NativeDataHub.latestForDomain(HealthDomain.SLEEP)
        fun metric(name: String): HealthValue? = values.firstOrNull { it.metric == name }
        val all = NativeDataHub.domainBetween(HealthDomain.SLEEP, 0L, Long.MAX_VALUE)
        val latestEnd = metric("sleep_end_epoch_ms")?.value?.toLong()
        val latestStageRaw = metric("sleep_stage_timeline")?.metadata?.get("segments")
        val totalSeries = all.filter { it.metric == "sleep_total_minutes" }
        val scoreSeries = all.filter { it.metric == "sleep_score" }
        return NativeSleepSnapshot(
            score = metric("sleep_score")?.value?.roundToInt(),
            totalMinutes = metric("sleep_total_minutes")?.value?.roundToInt(),
            sleepTimeMinutes = metric("sleep_time_minutes")?.value?.roundToInt(),
            awakeMinutes = metric("sleep_awake_minutes")?.value?.roundToInt(),
            lightMinutes = metric("sleep_light_minutes")?.value?.roundToInt(),
            deepMinutes = metric("sleep_deep_minutes")?.value?.roundToInt(),
            remMinutes = metric("sleep_rem_minutes")?.value?.roundToInt(),
            efficiencyPct = metric("sleep_efficiency_pct")?.value?.roundToInt(),
            startEpochMs = metric("sleep_start_epoch_ms")?.value?.toLong(),
            endEpochMs = latestEnd,
            stageSegments = parseSleepStageSegments(latestStageRaw),
            sessionsImported = metric("sleep_sessions_imported")?.value?.roundToInt() ?: 0,
            recentAverageMinutes = totalSeries.takeLast(7).map { it.value }.average().takeIf { !it.isNaN() }?.roundToInt(),
            recentAverageScore = scoreSeries.takeLast(7).map { it.value }.average().takeIf { !it.isNaN() }?.roundToInt()
        )
    }
}
