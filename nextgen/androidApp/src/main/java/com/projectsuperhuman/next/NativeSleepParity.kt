package com.projectsuperhuman.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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

data class NativeSleepSnapshot(
    val score: Int? = null,
    val totalMinutes: Int? = null,
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

@Composable
internal fun NativeSleepParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NativeSleepSnapshot?>(null) }
    var connected by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking Health Connect…") }

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
        } else status = "Sleep access wasn’t enabled"
    }

    fun connectOrSync() {
        scope.launch {
            when (SleepHealthConnect.availability(context)) {
                HealthConnectClient.SDK_AVAILABLE -> if (SleepHealthConnect.hasPermission(context)) sync()
                else permissionLauncher.launch(setOf(SleepHealthConnect.permission))
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> status = "Health Connect needs an update"
                else -> status = "Health Connect isn’t available on this device"
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        val available = SleepHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE
        connected = available && SleepHealthConnect.hasPermission(context)
        checked = true
        status = when {
            !available -> "Health Connect isn’t available on this device"
            connected -> "Connected"
            else -> "Connect to bring in sleep from your watch or health apps"
        }
        if (connected) sync()
    }

    val s = snapshot
    val analysis = if (s?.totalMinutes != null) SleepAnalysisEngine.analyse(
        s.totalMinutes, s.awakeMinutes ?: 0, s.deepMinutes ?: 0, s.remMinutes ?: 0, s.lightMinutes ?: 0
    ) else null

    Column(
        Modifier.fillMaxSize().background(SleepBg).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SleepHeader(onBack)
        SleepHero(s, analysis)
        if (s?.totalMinutes != null && analysis != null) {
            SleepOverviewCard(s, analysis)
            SleepArchitectureDiagram(s.stageSegments, s.startEpochMs, s.endEpochMs)
            SleepStageBreakdown(s, analysis)
            SleepInsightCard(analysis)
            SleepTrendCard(s)
        } else EmptySleepCard(connected)
        HealthConnectCard(connected, checked, syncing, status, ::connectOrSync)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SleepHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, SleepBorder, RoundedCornerShape(14.dp)).superhumanClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("‹", color = SleepPurple, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Sleep", color = SleepInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Recovery, architecture and nightly trends", color = SleepMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SleepHero(s: NativeSleepSnapshot?, analysis: SleepAnalysis?) {
    val start = s?.startEpochMs?.let(::formatSleepTime)
    val end = s?.endEpochMs?.let(::formatSleepTime)
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF44329E), Color(0xFF735FE8))), RoundedCornerShape(28.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("LATEST SLEEP", color = Color.White.copy(alpha = .65f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text(analysis?.headline ?: "Your sleep summary will appear here", color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                if (start != null && end != null) {
                    Spacer(Modifier.height(6.dp)); Text("$start – $end", color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
                }
            }
            if (analysis != null) Column(horizontalAlignment = Alignment.End) {
                Text(analysis.score.toString(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text("sleep score", color = Color.White.copy(alpha = .66f), fontSize = 8.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepHeroStat("Asleep", formatMinutes(s?.totalMinutes?.minus(s.awakeMinutes ?: 0)), Modifier.weight(1f))
            SleepHeroStat("Efficiency", s?.efficiencyPct?.let { "$it%" } ?: "—", Modifier.weight(1f))
            SleepHeroStat("Deep + REM", if (s?.totalMinutes != null) formatMinutes((s.deepMinutes ?: 0) + (s.remMinutes ?: 0)) else "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SleepHeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = .64f), fontSize = 8.sp)
    }
}

@Composable
private fun SleepOverviewCard(s: NativeSleepSnapshot, a: SleepAnalysis) = CardShell {
    Text("Night overview", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SleepMiniStat(formatMinutes(s.totalMinutes), "time in bed")
        SleepMiniStat(formatMinutes((s.totalMinutes ?: 0) - (s.awakeMinutes ?: 0)), "asleep")
        SleepMiniStat("${a.efficiencyPct}%", "efficiency")
    }
    Spacer(Modifier.height(14.dp))
    ScoreBar("Duration", a.durationScore, SleepBlue)
    ScoreBar("Continuity", a.continuityScore, SleepGood)
    ScoreBar("Stage balance", a.stageBalanceScore, SleepPurple)
}

@Composable
private fun SleepStageBreakdown(s: NativeSleepSnapshot, a: SleepAnalysis) = CardShell {
    Text("Sleep stages", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
    Text("Distribution across the latest night", color = SleepMuted, fontSize = 9.sp)
    Spacer(Modifier.height(10.dp))
    StageRow("Light", s.lightMinutes, SleepBlue, if ((s.lightMinutes ?: 0) > 0) (100 - a.deepPct - a.remPct).coerceAtLeast(0) else 0)
    StageRow("Deep", s.deepMinutes, Color(0xFF315A9E), a.deepPct)
    StageRow("REM", s.remMinutes, SleepPurple, a.remPct)
    StageRow("Awake", s.awakeMinutes, SleepWarn, a.awakePct)
}

@Composable
private fun SleepInsightCard(a: SleepAnalysis) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF0EEFF), RoundedCornerShape(22.dp)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("RECOVERY INSIGHT", color = SleepPurple, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(a.priority, color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(a.insight, color = SleepMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SleepTrendCard(s: NativeSleepSnapshot) = CardShell {
    Text("Recent trend", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
    Text("Averages from sleep stored on this device", color = SleepMuted, fontSize = 9.sp)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SleepMiniStat(s.recentAverageMinutes?.let(::formatMinutes) ?: "—", "avg sleep")
        SleepMiniStat(s.recentAverageScore?.toString() ?: "—", "avg score")
        SleepMiniStat(s.sessionsImported.toString(), "nights synced")
    }
}

@Composable
private fun HealthConnectCard(connected: Boolean, checked: Boolean, syncing: Boolean, status: String, onAction: () -> Unit) = CardShell {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Health Connect", color = SleepInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(if (connected) "Sleep data source connected" else "Connect a supported sleep source", color = SleepMuted, fontSize = 9.sp)
        }
        Box(Modifier.background(if (connected) SleepGood.copy(alpha = .12f) else SleepPurple.copy(alpha = .10f), RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
            Text(when { !checked -> "CHECKING"; connected -> "CONNECTED"; else -> "NOT CONNECTED" }, color = if (connected) SleepGood else SleepPurple, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
    }
    Spacer(Modifier.height(11.dp))
    Box(
        Modifier.fillMaxWidth().background(if (connected) Color(0xFFF0F3FF) else SleepPurple, RoundedCornerShape(15.dp))
            .superhumanClickable(enabled = !syncing, onClick = onAction).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(when { syncing -> "SYNCING…"; connected -> "SYNC NOW"; else -> "CONNECT HEALTH CONNECT" }, color = if (connected) SleepPurple else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(8.dp)); Text(status, color = SleepMuted, fontSize = 8.sp)
}

@Composable
private fun EmptySleepCard(connected: Boolean) = CardShell {
    Text("No sleep data yet", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
    Text(if (connected) "Sync a recorded night to see sleep architecture, recovery scoring and stage trends." else "Connect Health Connect to import sleep from compatible apps and devices.", color = SleepMuted, fontSize = 10.sp, lineHeight = 15.sp)
}

@Composable
private fun CardShell(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        content = content
    )
}

@Composable
private fun SleepMiniStat(value: String, label: String) { Column { Text(value, color = SleepInk, fontSize = 13.sp, fontWeight = FontWeight.Black); Text(label, color = SleepMuted, fontSize = 8.sp) } }

@Composable
private fun ScoreBar(label: String, score: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SleepMuted, fontSize = 9.sp, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f).height(7.dp).background(Color(0xFFEEF0F6), RoundedCornerShape(99.dp))) {
            Box(Modifier.fillMaxWidth(score.coerceIn(0, 100) / 100f).height(7.dp).background(color, RoundedCornerShape(99.dp)))
        }
        Spacer(Modifier.width(8.dp)); Text(score.toString(), color = SleepInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StageRow(name: String, minutes: Int?, accent: Color, pct: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(9.dp)); Text(name, color = SleepInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${formatMinutes(minutes)}  ·  $pct%", color = SleepMuted, fontSize = 10.sp)
    }
}

private fun formatMinutes(minutes: Int?): String { if (minutes == null) return "—"; val h = minutes / 60; val m = minutes % 60; return if (h > 0) "${h}h ${m}m" else "${m}m" }
private fun formatSleepTime(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

private object NativeSleepStore {
    suspend fun loadLatest(): NativeSleepSnapshot {
        val values = NativeDataHub.latestForDomain(HealthDomain.SLEEP)
        fun metric(name: String): HealthValue? = values.firstOrNull { it.metric == name }
        val end = metric("sleep_end_epoch_ms")?.value?.toLong()
        val from = (end ?: System.currentTimeMillis()) - 30L * 24 * 60 * 60 * 1000
        val durations = NativeDataHub.between("sleep_total_minutes", from, System.currentTimeMillis())
        val scores = NativeDataHub.between("sleep_score", from, System.currentTimeMillis())
        return NativeSleepSnapshot(
            score = metric("sleep_score")?.value?.roundToInt(), totalMinutes = metric("sleep_total_minutes")?.value?.roundToInt(),
            awakeMinutes = metric("sleep_awake_minutes")?.value?.roundToInt(), lightMinutes = metric("sleep_light_minutes")?.value?.roundToInt(),
            deepMinutes = metric("sleep_deep_minutes")?.value?.roundToInt(), remMinutes = metric("sleep_rem_minutes")?.value?.roundToInt(),
            efficiencyPct = metric("sleep_efficiency_pct")?.value?.roundToInt(), startEpochMs = metric("sleep_start_epoch_ms")?.value?.toLong(), endEpochMs = end,
            stageSegments = parseSleepStageSegments(metric("sleep_stage_timeline")?.metadata?.get("segments")),
            sessionsImported = metric("sleep_sessions_imported")?.value?.roundToInt() ?: 0,
            recentAverageMinutes = durations.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt(),
            recentAverageScore = scores.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()
        )
    }
}
