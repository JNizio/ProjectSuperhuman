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

private val ShellNavy = Color(0xFF082D66)
private val ShellBlue = Color(0xFF0D6CB4)
private val ShellBg = Color(0xFFF6F9FC)
private val ShellInk = Color(0xFF0B1F35)
private val ShellMuted = Color(0xFF64748B)

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

private enum class ShellPage {
    HOME, SETTINGS, CLINICAL, BODY, SLEEP, BLOOD_PRESSURE, NUTRITION, EXERCISE, MINDFULNESS
}

@Composable
private fun SuperhumanShell(openLegacy: () -> Unit) {
    var page by remember { mutableStateOf(ShellPage.HOME) }
    Surface(color = ShellBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (page == ShellPage.HOME || page == ShellPage.SETTINGS) {
                NativeTopBar(
                    title = if (page == ShellPage.HOME) "PROJECT SUPERHUMAN" else "SETTINGS",
                    onSettings = { page = if (page == ShellPage.SETTINGS) ShellPage.HOME else ShellPage.SETTINGS }
                )
            }
            when (page) {
                ShellPage.HOME -> NativeLiveHome(
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
                Modifier.width(42.dp).height(42.dp).background(
                    Brush.linearGradient(listOf(ShellNavy, ShellBlue)), RoundedCornerShape(13.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("PS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = ShellNavy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.25.sp)
                Text(
                    if (title == "PROJECT SUPERHUMAN") "Your health, unified" else "App & data controls",
                    color = ShellMuted,
                    fontSize = 10.sp
                )
            }
        }
        Box(
            Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Text(if (title == "SETTINGS") "×" else "⚙", color = ShellNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NativeSettings(openLegacy: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Native settings", color = ShellInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Platform-level controls now live outside the WebView.", color = ShellMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        SettingsRow("Data Vault", "Shared SQLDelight repository active")
        SettingsRow("Health integrations", "Health Connect now · HealthKit later")
        SettingsRow("Permissions", "Camera, Bluetooth and health access")
        SettingsRow("Scientific engine", "Rules, provenance and engine version")
        SettingsRow("Legacy app", "Open remaining unmigrated features", onClick = openLegacy)
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
            Text(title, color = ShellInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = ShellMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Text("›", color = Color(0xFF8AA1B4), fontSize = 23.sp)
    }
}
