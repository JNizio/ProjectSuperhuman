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
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
    val sessionsImported: Int = 0
)

@Composable
internal fun NativeSleepParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NativeSleepSnapshot?>(null) }
    var connected by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var connectionChecked by remember { mutableStateOf(false) }
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
        SleepHero(s)
        HealthConnectCard(connected, connectionChecked, syncing, status, ::connectOrSync)
        if (s?.totalMinutes != null) {
            LastSleepCard(s)
            SleepStageCard(s)
        } else {
            EmptySleepCard(connected)
        }
        SleepHistorySummary(s)
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
            Text("Your nights, recovery and sleep stages", color = SleepMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SleepHero(s: NativeSleepSnapshot?) {
    val score = s?.score
    val message = when {
        score == null -> "Your sleep summary will appear here"
        score >= 85 -> "A strong night"
        score >= 70 -> "A solid night"
        score >= 55 -> "Room to recover"
        else -> "Recovery was limited"
    }
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF4939A9), Color(0xFF6F5BE1))),
            RoundedCornerShape(28.dp)
        ).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("LATEST SLEEP", color = Color.White.copy(alpha = .64f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(5.dp))
                Text(message, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            if (score != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(score.toString(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("sleep score", color = Color.White.copy(alpha = .68f), fontSize = 8.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SleepHeroStat("Asleep", formatMinutes(s?.totalMinutes), Modifier.weight(1f))
            SleepHeroStat("Deep", formatMinutes(s?.deepMinutes), Modifier.weight(1f))
            SleepHeroStat("REM", formatMinutes(s?.remMinutes), Modifier.weight(1f))
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
private fun HealthConnectCard(connected: Boolean, checked: Boolean, syncing: Boolean, status: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Health Connect", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    if (connected) "Sleep can sync from supported watches and health apps" else "Bring your sleep data together in one place",
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
private fun LastSleepCard(s: NativeSleepSnapshot) {
    val start = s.startEpochMs?.let(::formatSleepTime)
    val end = s.endEpochMs?.let(::formatSleepTime)
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Last sleep", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                if (start != null && end != null) Text("$start – $end", color = SleepMuted, fontSize = 9.sp)
            }
            Text(formatMinutes(s.totalMinutes), color = SleepPurple, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        val awake = s.awakeMinutes ?: 0
        val total = s.totalMinutes ?: 0
        val efficiency = if (total > 0) (((total - awake).toDouble() / total) * 100).roundToInt().coerceIn(0, 100) else 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SleepMiniStat("$efficiency%", "sleep efficiency")
            SleepMiniStat(formatMinutes(s.awakeMinutes), "awake")
            SleepMiniStat((s.score ?: 0).toString(), "score")
        }
    }
}

@Composable
private fun SleepStageCard(s: NativeSleepSnapshot) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("Sleep stages", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("How your night was distributed", color = SleepMuted, fontSize = 9.sp)
        Spacer(Modifier.height(10.dp))
        StageRow("Light", s.lightMinutes, SleepBlue)
        StageRow("Deep", s.deepMinutes, SleepGood)
        StageRow("REM", s.remMinutes, SleepPurple)
        StageRow("Awake", s.awakeMinutes, SleepWarn)
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
private fun SleepHistorySummary(s: NativeSleepSnapshot?) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, SleepBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("Sleep history", color = SleepInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(
            if ((s?.sessionsImported ?: 0) > 0) "${s?.sessionsImported} recent nights are available for trends and future recovery insights."
            else "Your recent nights will build into trends here as sleep data is synced.",
            color = SleepMuted, fontSize = 9.sp, lineHeight = 14.sp
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
        return NativeSleepSnapshot(
            score = metric("sleep_score")?.value?.roundToInt(),
            totalMinutes = metric("sleep_total_minutes")?.value?.roundToInt(),
            awakeMinutes = metric("sleep_awake_minutes")?.value?.roundToInt(),
            lightMinutes = metric("sleep_light_minutes")?.value?.roundToInt(),
            deepMinutes = metric("sleep_deep_minutes")?.value?.roundToInt(),
            remMinutes = metric("sleep_rem_minutes")?.value?.roundToInt(),
            startEpochMs = metric("sleep_start_epoch_ms")?.value?.toLong(),
            endEpochMs = metric("sleep_end_epoch_ms")?.value?.toLong(),
            sessionsImported = metric("sleep_sessions_imported")?.value?.roundToInt() ?: 0
        )
    }
}
