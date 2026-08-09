package com.projectsuperhuman.next

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain

private val ModuleNavy = Color(0xFF082D66)
private val ModuleBlue = Color(0xFF0D6CB4)
private val ModuleInk = Color(0xFF0B1F35)
private val ModuleMuted = Color(0xFF64748B)
private val ModuleGood = Color(0xFF168A78)
private val ModuleWarn = Color(0xFFD97706)
private val ModuleBad = Color(0xFFCA3A3A)

@Composable
internal fun NativeClinicalPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    NativeClinicalParityScreen(onBack, openLegacy)
}

@Composable
internal fun NativeBodyPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold("Body & Progress", "Weight, composition and measurements", ModuleGood, onBack) {
        SharedDomainStatus(HealthDomain.BODY)
        OverviewStrip(listOf(
            Triple("WEIGHT", "Live DB", ModuleGood),
            Triple("TREND", "Shared", ModuleBlue),
            Triple("GOAL", "Stored", ModuleWarn)
        ))
        ModuleCard("Weight history", "Native trend surface backed by the shared repository.", ModuleGood)
        ModuleCard("Body composition", "Body fat, muscle, water and smart-scale metrics when available.", ModuleBlue)
        ModuleCard("Measurements", "Waist, chest, arms, hips, thighs and other optional measurements.", Color(0xFF6547C9))
        LegacyAction("Open existing body tools", "Legacy history remains available while Step 12 completes native entry and chart parity.", openLegacy)
    }
}

@Composable
internal fun NativeSleepPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold("Sleep", "Wearable sleep and recovery", Color(0xFF6547C9), onBack) {
        SharedDomainStatus(HealthDomain.SLEEP)
        OverviewStrip(listOf(
            Triple("SCORE", "Live DB", Color(0xFF6547C9)),
            Triple("TOTAL", "Shared", ModuleBlue),
            Triple("DEBT", "Stored", ModuleWarn)
        ))
        ModuleCard("Last sleep", "Duration, score and recovery values now have a direct shared-data path.", Color(0xFF6547C9))
        ModuleCard("Sleep stages", "Awake, light, deep and REM values can be read from the repository.", ModuleBlue)
        ModuleCard("Sleep debt", "Rolling context will be calculated natively during Step 11.", ModuleWarn)
        LegacyAction("Open current sleep sync", "Health Connect capture remains available until its adapter is moved fully behind this screen.", openLegacy)
    }
}

@Composable
internal fun NativeBloodPressurePage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold("Blood Pressure", "Readings, trends and pulse", ModuleBad, onBack) {
        SharedDomainStatus(HealthDomain.BLOOD_PRESSURE)
        OverviewStrip(listOf(
            Triple("SYS", "Live DB", ModuleBad),
            Triple("DIA", "Shared", ModuleBlue),
            Triple("PULSE", "Stored", ModuleGood)
        ))
        ModuleCard("Latest reading", "Systolic, diastolic and pulse values now have a direct shared-data path.", ModuleBad)
        ModuleCard("Trend", "History data can now be read without going through WebView storage.", ModuleBlue)
        ModuleCard("Capture", "Camera/BP-device capture will write into this same repository in Step 12.", ModuleGood)
        LegacyAction("Open existing BP capture", "Use the mature capture path while the native adapter migration is completed.", openLegacy)
    }
}

@Composable
private fun ModuleScaffold(
    title: String,
    subtitle: String,
    accent: Color,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("‹", color = ModuleNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ModuleInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = ModuleMuted, fontSize = 10.sp)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(accent.copy(alpha = .13f), Color.White)),
                RoundedCornerShape(24.dp)
            ).padding(18.dp)
        ) {
            Text("STEP 8 · SHARED DATA", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(6.dp))
            Text("Native + real repository", color = ModuleInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("This screen now reads through the shared HealthRepository instead of depending on WebView/localStorage. Feature-specific capture tools are migrated in the parity steps that follow.", color = ModuleMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }

        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OverviewStrip(items: List<Triple<String, String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            Column(
                modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Text(item.first, color = ModuleMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(item.second, color = item.third, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ModuleCard(title: String, subtitle: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(19.dp)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(5.dp).height(42.dp).background(accent, RoundedCornerShape(99.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ModuleInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = ModuleMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun LegacyAction(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ModuleNavy, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(17.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 9.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(7.dp))
        Text("OPEN  ›", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
    }
}
