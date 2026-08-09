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

private val ModuleNavy = Color(0xFF082D66)
private val ModuleBlue = Color(0xFF0D6CB4)
private val ModuleInk = Color(0xFF0B1F35)
private val ModuleMuted = Color(0xFF64748B)
private val ModuleGood = Color(0xFF168A78)
private val ModuleWarn = Color(0xFFD97706)
private val ModuleBad = Color(0xFFCA3A3A)

@Composable
internal fun NativeClinicalPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold(
        title = "Clinical",
        subtitle = "Labs, markers and body map",
        accent = ModuleBlue,
        onBack = onBack
    ) {
        OverviewStrip(
            listOf(
                Triple("STATUS", "Ready", ModuleGood),
                Triple("MARKERS", "—", ModuleBlue),
                Triple("ALERTS", "—", ModuleBad)
            )
        )
        ModuleCard("Clinical overview", "Native marker summaries, reference-range state and trend entry point.", ModuleBlue)
        ModuleCard("Blood results", "FBC, liver, bone, iron and other imported panels.", ModuleGood)
        ModuleCard("Body map", "Segmented body view for linking abnormal or healthy data to regions.", Color(0xFF6547C9))
        LegacyAction("Open existing clinical data", "Use the complete current OCR/import and graphs while native data wiring is migrated.", openLegacy)
    }
}

@Composable
internal fun NativeBodyPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold(
        title = "Body & Progress",
        subtitle = "Weight, composition and measurements",
        accent = ModuleGood,
        onBack = onBack
    ) {
        OverviewStrip(
            listOf(
                Triple("WEIGHT", "—", ModuleGood),
                Triple("TREND", "—", ModuleBlue),
                Triple("GOAL", "—", ModuleWarn)
            )
        )
        ModuleCard("Weight history", "Native trend surface for body-weight measurements and goal direction.", ModuleGood)
        ModuleCard("Body composition", "Body fat, muscle, water and smart-scale metrics when available.", ModuleBlue)
        ModuleCard("Measurements", "Waist, chest, arms, hips, thighs and other optional measurements.", Color(0xFF6547C9))
        LegacyAction("Open existing body data", "Use the complete current history and smart-scale tools during migration.", openLegacy)
    }
}

@Composable
internal fun NativeSleepPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold(
        title = "Sleep",
        subtitle = "Wearable sleep and recovery",
        accent = Color(0xFF6547C9),
        onBack = onBack
    ) {
        OverviewStrip(
            listOf(
                Triple("SCORE", "—", Color(0xFF6547C9)),
                Triple("TOTAL", "—", ModuleBlue),
                Triple("DEBT", "—", ModuleWarn)
            )
        )
        ModuleCard("Last sleep", "Native summary surface for duration, sleep score and recovery status.", Color(0xFF6547C9))
        ModuleCard("Sleep stages", "Awake, light, deep and REM totals from Health Connect.", ModuleBlue)
        ModuleCard("Sleep debt", "Rolling context to show whether recent sleep is catching up or falling behind.", ModuleWarn)
        LegacyAction("Open current sleep sync", "Health Connect import remains available while the native adapter is connected to this screen.", openLegacy)
    }
}

@Composable
internal fun NativeBloodPressurePage(onBack: () -> Unit, openLegacy: () -> Unit) {
    ModuleScaffold(
        title = "Blood Pressure",
        subtitle = "Readings, trends and pulse",
        accent = ModuleBad,
        onBack = onBack
    ) {
        OverviewStrip(
            listOf(
                Triple("SYS", "—", ModuleBad),
                Triple("DIA", "—", ModuleBlue),
                Triple("PULSE", "—", ModuleGood)
            )
        )
        ModuleCard("Latest reading", "Native summary for systolic, diastolic and heart-rate values.", ModuleBad)
        ModuleCard("Trend", "History surface for spotting changes across repeated measurements.", ModuleBlue)
        ModuleCard("Capture", "Camera/BP-device import entry point will move behind this native screen.", ModuleGood)
        LegacyAction("Open existing BP tools", "Use the current camera import and saved readings while native capture is migrated.", openLegacy)
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = ModuleNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ModuleInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = ModuleMuted, fontSize = 10.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = .13f), Color.White)),
                    RoundedCornerShape(24.dp)
                )
                .padding(18.dp)
        ) {
            Text("STEP 5 · NATIVE MODULE", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(6.dp))
            Text("Native experience layer", color = ModuleInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("The module now has a native destination. Existing data tools remain reachable until their storage/import adapters are moved behind it.", color = ModuleMuted, fontSize = 10.sp, lineHeight = 15.sp)
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
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(19.dp))
            .padding(15.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .background(ModuleNavy, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(17.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 9.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(7.dp))
        Text("OPEN  ›", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
    }
}
