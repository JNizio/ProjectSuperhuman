package com.projectsuperhuman.next

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.SleepSessionRecord
import android.os.Build
import android.os.OutcomeReceiver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.roundToInt

private val SleepPurple = Color(0xFF6547C9)
private val SleepBlue = Color(0xFF0D6CB4)
private val SleepInk = Color(0xFF0B1F35)
private val SleepMuted = Color(0xFF64748B)
private val SleepGood = Color(0xFF168A78)
private val SleepWarn = Color(0xFFD97706)
private val SleepBad = Color(0xFFCA3A3A)
private val SleepBg = Color(0xFFF6F9FC)

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
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NativeSleepSnapshot?>(null) }
    var status by remember { mutableStateOf("Ready to sync") }
    var syncing by remember { mutableStateOf(false) }

    suspend fun refresh() {
        snapshot = NativeSleepStore.loadLatest()
    }

    LaunchedEffect(Unit) { refresh() }

    fun syncNow() {
        if (activity == null) {
            status = "Android activity unavailable"
            return
        }
        scope.launch {
            syncing = true
            status = "Reading Health Connect…"
            val result = NativeSleepConnect.readAndStore(activity)
            syncing = false
            status = result.second
            refresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) syncNow() else status = "Sleep permission was not granted"
    }

    fun requestOrSync() {
        if (Build.VERSION.SDK_INT < 34) {
            status = "Health Connect sleep import requires Android 14+ on this build"
            return
        }
        if (context.checkSelfPermission(Manifest.permission.health.READ_SLEEP) == PackageManager.PERMISSION_GRANTED) {
            syncNow()
        } else {
            permissionLauncher.launch(Manifest.permission.health.READ_SLEEP)
        }
    }

    val s = snapshot
    Column(
        Modifier.fillMaxSize().background(SleepBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("‹", color = SleepBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sleep", color = SleepInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Health Connect, stages, score & recovery", color = SleepMuted, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SleepStat("SCORE", s?.score?.toString() ?: "—", SleepPurple, Modifier.weight(1f))
            SleepStat("TOTAL", formatMinutes(s?.totalMinutes), SleepBlue, Modifier.weight(1f))
            SleepStat("AWAKE", formatMinutes(s?.awakeMinutes), SleepWarn, Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(17.dp)) {
            Text("Last sleep", color = SleepInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            StageRow("Light", s?.lightMinutes, SleepBlue)
            StageRow("Deep", s?.deepMinutes, SleepGood)
            StageRow("REM", s?.remMinutes, SleepPurple)
            StageRow("Awake", s?.awakeMinutes, SleepWarn)
            if (s?.totalMinutes != null && s.totalMinutes > 0) {
                Spacer(Modifier.height(8.dp))
                Text(scoreExplanation(s), color = SleepMuted, fontSize = 9.sp, lineHeight = 14.sp)
            }
        }

        Column(Modifier.fillMaxWidth().background(SleepPurple.copy(alpha = .09f), RoundedCornerShape(22.dp)).padding(17.dp)) {
            Text("Health Connect", color = SleepPurple, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Imports up to 30 days of sleep sessions and stages directly into the shared Project Superhuman database.", color = SleepMuted, fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().background(SleepPurple, RoundedCornerShape(16.dp)).clickable(enabled = !syncing, onClick = { requestOrSync() }).padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (syncing) "SYNCING…" else "SYNC SLEEP NOW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = SleepMuted, fontSize = 9.sp)
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Text("Sleep history", color = SleepInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("${s?.sessionsImported ?: 0} session(s) represented by the latest sync. Detailed trend charts will build on these stored metrics rather than WebView data.", color = SleepMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Text("Compatibility bridge", color = SleepBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("The previous sleep screen is still available as a fallback while we validate device-specific Health Connect behaviour.", color = SleepMuted, fontSize = 9.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().background(SleepBlue, RoundedCornerShape(15.dp)).clickable(onClick = openLegacy).padding(13.dp), contentAlignment = Alignment.Center) {
                Text("OPEN EXISTING SLEEP TOOLS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SleepStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(label, color = SleepMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StageRow(name: String, minutes: Int?, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(7.dp).height(7.dp).background(accent, RoundedCornerShape(99.dp)))
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

private fun scoreExplanation(s: NativeSleepSnapshot): String {
    val total = s.totalMinutes ?: return ""
    val score = s.score ?: return ""
    return "Sleep score $score/100 combines duration, awake time/estimated efficiency, and the balance of deep and REM sleep. Latest session: ${formatMinutes(total)}."
}

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

private object NativeSleepConnect {
    suspend fun readAndStore(activity: Activity): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT < 34) return false to "Health Connect requires Android 14+ on this build"
        if (activity.checkSelfPermission(Manifest.permission.health.READ_SLEEP) != PackageManager.PERMISSION_GRANTED) {
            return false to "Sleep permission is not granted"
        }
        val manager = activity.getSystemService(Context.HEALTH_CONNECT_SERVICE) as? HealthConnectManager
            ?: return false to "Health Connect service is unavailable"

        val response = readRecords(manager, activity) ?: return false to "Health Connect sleep read failed"
        val sessions = response.records.sortedByDescending { it.endTime }
        if (sessions.isEmpty()) return false to "No sleep sessions found in the last 30 days"

        val latest = sessions.first()
        val totalMinutes = Duration.between(latest.startTime, latest.endTime).toMinutes().coerceAtLeast(0).toInt()
        var awake = 0L
        var light = 0L
        var deep = 0L
        var rem = 0L
        var genericSleep = 0L

        latest.stages.forEach { stage ->
            val mins = Duration.between(stage.startTime, stage.endTime).toMinutes().coerceAtLeast(0)
            when (stage.type) {
                SleepSessionRecord.StageType.STAGE_TYPE_AWAKE,
                SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED -> awake += mins
                SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT -> light += mins
                SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP -> deep += mins
                SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM -> rem += mins
                SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING -> genericSleep += mins
            }
        }
        if (latest.stages.isEmpty()) genericSleep = totalMinutes.toLong()
        val stagedSleep = light + deep + rem + genericSleep
        val effectiveSleep = if (stagedSleep > 0) stagedSleep.toInt() else (totalMinutes - awake.toInt()).coerceAtLeast(0)
        val score = calculateScore(totalMinutes, awake.toInt(), deep.toInt(), rem.toInt(), effectiveSleep)
        val sourceId = latest.metadata.id
        val common = mapOf("sourceRecordId" to sourceId, "sessionStart" to latest.startTime.toEpochMilli().toString())

        val values = listOf(
            HealthValue(HealthDomain.SLEEP, "sleep_score", score.toDouble(), "score", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_total_minutes", totalMinutes.toDouble(), "min", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_awake_minutes", awake.toDouble(), "min", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_light_minutes", light.toDouble(), "min", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_deep_minutes", deep.toDouble(), "min", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_rem_minutes", rem.toDouble(), "min", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_start_epoch_ms", latest.startTime.toEpochMilli().toDouble(), "ms", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_end_epoch_ms", latest.endTime.toEpochMilli().toDouble(), "ms", latest.endTime.toEpochMilli(), "health-connect", common),
            HealthValue(HealthDomain.SLEEP, "sleep_sessions_imported", sessions.size.toDouble(), "count", System.currentTimeMillis(), "health-connect", emptyMap())
        )
        NativeDataHub.saveValues(values)
        return true to "Synced ${sessions.size} sleep session(s). Latest score: $score/100"
    }

    private suspend fun readRecords(manager: HealthConnectManager, activity: Activity): ReadRecordsResponse<SleepSessionRecord>? = suspendCoroutine { cont ->
        val end = Instant.now()
        val start = end.minusSeconds(30L * 24L * 60L * 60L)
        val range = TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()
        val request = ReadRecordsRequestUsingFilters.Builder(SleepSessionRecord::class.java)
            .setTimeRangeFilter(range)
            .setPageSize(200)
            .build()
        try {
            manager.readRecords(request, activity.mainExecutor, object : OutcomeReceiver<ReadRecordsResponse<SleepSessionRecord>, HealthConnectException> {
                override fun onResult(result: ReadRecordsResponse<SleepSessionRecord>) = cont.resume(result)
                override fun onError(error: HealthConnectException) = cont.resume(null)
            })
        } catch (_: Exception) {
            cont.resume(null)
        }
    }

    private fun calculateScore(total: Int, awake: Int, deep: Int, rem: Int, effectiveSleep: Int): Int {
        if (total <= 0) return 0
        val durationHours = effectiveSleep / 60.0
        val durationScore = (100.0 - abs(durationHours - 8.0) * 18.0).coerceIn(0.0, 100.0)
        val efficiency = if (total > 0) ((total - awake).toDouble() / total * 100.0).coerceIn(0.0, 100.0) else 0.0
        val deepPct = if (effectiveSleep > 0) deep.toDouble() / effectiveSleep * 100.0 else 0.0
        val remPct = if (effectiveSleep > 0) rem.toDouble() / effectiveSleep * 100.0 else 0.0
        val deepScore = (100.0 - abs(deepPct - 18.0) * 5.0).coerceIn(0.0, 100.0)
        val remScore = (100.0 - abs(remPct - 22.0) * 4.0).coerceIn(0.0, 100.0)
        return (durationScore * .55 + efficiency * .20 + deepScore * .15 + remScore * .10).roundToInt().coerceIn(0, 100)
    }
}
