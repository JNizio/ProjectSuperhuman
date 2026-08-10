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
private val ModuleBad = Color(0xFFCA3A3A)

@Composable
internal fun NativeClinicalPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    NativeClinicalParityScreen(onBack, openLegacy)
}

@Composable
internal fun NativeBodyPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    NativeBodyParityScreen(onBack, openLegacy)
}

@Composable
internal fun NativeSleepPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    NativeSleepHistoryPage(onBack, openLegacy)
}

@Composable
internal fun NativeBloodPressurePage(onBack: () -> Unit, openLegacy: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.superhumanTopButton(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", color = ModuleNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Blood Pressure", color = ModuleInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Readings, trends & pulse", color = ModuleMuted, fontSize = 10.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFFFFECEC), Color.White)), RoundedCornerShape(24.dp)
            ).padding(18.dp)
        ) {
            Text("COMING LATER", color = ModuleBad, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(6.dp))
            Text("Blood Pressure", color = ModuleInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("This module is intentionally deferred while the rest of Project Superhuman reaches native parity.", color = ModuleMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }

        SharedDomainStatus(HealthDomain.BLOOD_PRESSURE)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BpStat("SYS", "—", ModuleBad, Modifier.weight(1f))
            BpStat("DIA", "—", ModuleBlue, Modifier.weight(1f))
            BpStat("PULSE", "—", ModuleGood, Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Text("Existing capture remains available", color = ModuleInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("Until native BP work resumes, the proven existing capture flow remains accessible without affecting the rest of the native app.", color = ModuleMuted, fontSize = 9.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().background(ModuleNavy, RoundedCornerShape(15.dp)).clickable(onClick = openLegacy).padding(13.dp),
                contentAlignment = Alignment.Center
            ) { Text("OPEN BLOOD PRESSURE TOOLS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BpStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(label, color = ModuleMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
