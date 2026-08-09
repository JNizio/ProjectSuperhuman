package com.projectsuperhuman.next

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.m1x.HealthBridge
import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.roundToInt

private val Navy = Color(0xFF082D66)
private val Blue = Color(0xFF0D6CB4)
private val Bg = Color(0xFFF6F9FC)
private val Ink = Color(0xFF0B1F35)
private val Muted = Color(0xFF64748B)
private val SoftBlue = Color(0xFFEAF4FC)
private val SoftGreen = Color(0xFFECF8F1)
private val SoftPurple = Color(0xFFF3F0FB)
private val SoftOrange = Color(0xFFFFF4E8)

class NextShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeDataHub.initialize(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SuperhumanShell(openLegacy = { startActivity(Intent(this, HealthBridge::class.java)) })
            }
        }
    }
}

private enum class ShellPage { HOME, SETTINGS, CLINICAL, BODY, SLEEP, BLOOD_PRESSURE, NUTRITION, EXERCISE, MINDFULNESS }

@Composable
private fun SuperhumanShell(openLegacy: () -> Unit) {
    var page by remember { mutableStateOf(ShellPage.HOME) }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (page == ShellPage.HOME || page == ShellPage.SETTINGS) {
                NativeTopBar(
                    title = if (page == ShellPage.HOME) "PROJECT SUPERHUMAN" else "SETTINGS",
                    onSettings = { page = if (page == ShellPage.SETTINGS) ShellPage.HOME else ShellPage.SETTINGS }
                )
            }
            when (page) {
                ShellPage.HOME -> NativeHome(
                    openLegacy = openLegacy,
                    openClinical = { page = ShellPage.CLINICAL },
                    openBody = { page = ShellPage.BODY },
                    openSleep = { page = ShellPage.SLEEP },
                    openBloodPressure = { page = ShellPage.BLOOD_PRESSURE },
                    openNutrition = { page = ShellPage.NUTRITION },
                    openExercise = { page = ShellPage.EXERCISE },
                    openMindfulness = { page = ShellPage.MINDFULNESS }
                )
                ShellPage.SETTINGS -> NativeSettings(openLegacy)
                ShellPage.CLINICAL -> NativeClinicalPage({ page = ShellPage.HOME }, openLegacy)
                ShellPage.BODY -> NativeBodyPage({ page = ShellPage.HOME }, openLegacy)
                ShellPage.SLEEP -> NativeSleepPage({ page = ShellPage.HOME }, openLegacy)
                ShellPage.BLOOD_PRESSURE -> NativeBloodPressurePage({ page = ShellPage.HOME }, openLegacy)
                ShellPage.NUTRITION -> NativeNutritionPage({ page = ShellPage.HOME }, openLegacy)
                ShellPage.EXERCISE -> NativeExercisePage({ page = ShellPage.HOME }, openLegacy)
                ShellPage.MINDFULNESS -> NativeMindfulnessPage({ page = ShellPage.HOME }, openLegacy)
            }
        }
    }
}

@Composable
private fun NativeTopBar(title: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(42.dp).height(42.dp).background(Brush.linearGradient(listOf(Navy, Blue)), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) { Text("PS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.25.sp)
                Text(if (title == "PROJECT SUPERHUMAN") "Your health, unified" else "App & data controls", color = Muted, fontSize = 10.sp)
            }
        }
        Box(
            Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) { Text(if (title == "SETTINGS") "×" else "⚙", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NativeHome(
    openLegacy: () -> Unit,
    openClinical: () -> Unit,
    openBody: () -> Unit,
    openSleep: () -> Unit,
    openBloodPressure: () -> Unit,
    openNutrition: () -> Unit,
    openExercise: () -> Unit,
    openMindfulness: () -> Unit
) {
    var sleepScore by remember { mutableStateOf<Double?>(null) }
    var waterLitres by remember { mutableStateOf(0.0) }
    var calories by remember { mutableStateOf<Double?>(null) }
    var trainingSessions by remember { mutableStateOf<Double?>(null) }
    var databaseCount by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        sleepScore = NativeDataHub.latest("sleep_score")?.value
        waterLitres = NativeDataHub.latest("water_total_l")?.value ?: 0.0
        calories = NativeDataHub.latest("calories_today")?.value
        trainingSessions = NativeDataHub.latest("training_sessions_today")?.value
        databaseCount = NativeDataHub.storedValueCount()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HealthSnapshot(openClinical, databaseCount)
        Text("TODAY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Sleep", sleepScore?.roundToInt()?.toString() ?: "—", "Live shared data", SoftPurple, Color(0xFF6547C9), Modifier.weight(1f), openSleep)
            MetricCard(
                "Water",
                String.format("%.2f L", waterLitres),
                "+250 ml direct save",
                SoftBlue,
                Blue,
                Modifier.weight(1f)
            ) {
                val next = waterLitres + 0.25
                waterLitres = next
                // Persisted by a tiny writer composable below.
            }
        }
        WaterPersistence(value = waterLitres)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Nutrition", calories?.roundToInt()?.let { "$it kcal" } ?: "— kcal", "Live shared data", SoftGreen, Color(0xFF168A78), Modifier.weight(1f), openNutrition)
            MetricCard("Training", trainingSessions?.roundToInt()?.let { "$it sessions" } ?: "Ready", "Live shared data", SoftOrange, Color(0xFFD97706), Modifier.weight(1f), openExercise)
        }
        Text("HEALTH HUB", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 2.dp, top = 5.dp))
        HubRow("Clinical", "Labs, markers & body map", "CL", Color(0xFFEAF3FF), Blue, openClinical)
        HubRow("Body & Progress", "Weight, composition & measurements", "BP", Color(0xFFEDF8F5), Color(0xFF168A78), openBody)
        HubRow("Blood Pressure", "Readings, trends & camera import", "HR", Color(0xFFFFF0F0), Color(0xFFCA3A3A), openBloodPressure)
        HubRow("Mindfulness", "Stress, breathing & recovery", "MN", Color(0xFFF4F1FC), Color(0xFF6547C9), openMindfulness)
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(17.dp)) {
            Text("Shared data layer connected", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("Step 8 connects Compose directly to the SQLDelight HealthRepository. Dashboard metrics now read from the shared store and water writes directly to it; legacy tools remain only for features not migrated yet.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text("$databaseCount health values stored", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).clickable(onClick = openLegacy).padding(17.dp)) {
            Text("Legacy bridge", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("Open only for functionality that has not yet been migrated.", color = Muted, fontSize = 9.sp)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun WaterPersistence(value: Double) {
    var initialised by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!initialised) {
            initialised = true
        } else {
            NativeDataHub.saveMetric(HealthDomain.NUTRITION, "water_total_l", value, "L")
        }
    }
}

@Composable
private fun HealthSnapshot(openClinical: () -> Unit, databaseCount: Long) {
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFE7F2FF), Color.White)), RoundedCornerShape(26.dp)).clickable(onClick = openClinical).padding(20.dp)
    ) {
        Text("SUPERHUMAN OVERVIEW", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Health overview", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Shared repository is now the source of truth", color = Muted, fontSize = 11.sp)
            }
            Box(Modifier.width(64.dp).height(64.dp).background(Color.White, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(databaseCount.toString(), color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("VALUES", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SnapshotPill("Clinical", "DB wired", Modifier.weight(1f))
            SnapshotPill("Sleep", "DB wired", Modifier.weight(1f))
            SnapshotPill("Body", "DB wired", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SnapshotPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 11.dp, vertical = 10.dp)) {
        Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, background: Color, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(background, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(15.dp)) {
        Text(title.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        Spacer(Modifier.height(9.dp))
        Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun HubRow(title: String, subtitle: String, initials: String, background: Color, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(42.dp).height(42.dp).background(background, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text(initials, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 9.sp)
        }
        Text("›", color = accent, fontSize = 24.sp)
    }
}

@Composable
private fun NativeSettings(openLegacy: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Native settings", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Platform-level controls now live outside the WebView.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        SettingsRow("Data Vault", "Shared SQLDelight repository active")
        SettingsRow("Health integrations", "Health Connect now · HealthKit later")
        SettingsRow("Permissions", "Camera, Bluetooth and health access")
        SettingsRow("Scientific engine", "Rules, provenance and engine version")
        SettingsRow("Legacy app", "Open unmigrated features", onClick = openLegacy)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Text("›", color = Color(0xFF8AA1B4), fontSize = 23.sp)
    }
}
