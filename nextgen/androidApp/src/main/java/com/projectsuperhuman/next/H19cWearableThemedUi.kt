package com.projectsuperhuman.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ThemedH19cWearableCard() {
    val context = LocalContext.current
    val state by H19cWearableRuntime.state.collectAsState()
    val permissions = remember { H19cWearableRuntime.requiredPermissions() }
    H19cWearableRuntime.initialize(context)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) H19cWearableRuntime.scanAndConnect(context)
    }

    fun connect() {
        if (H19cWearableRuntime.hasPermissions(context)) {
            if (state.deviceAddress != null) H19cWearableRuntime.reconnectSaved(context)
            else H19cWearableRuntime.scanAndConnect(context)
        } else launcher.launch(permissions)
    }

    val accent = superhumanBlue
    val green = superhumanGreen
    val muted = superhumanTextMuted
    val ink = superhumanTextPrimary

    Column(
        Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = if (SuperhumanAppearance.darkMode) .30f else .14f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("H19C wearable", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(state.status, color = muted, fontSize = 9.sp, lineHeight = 13.sp)
            }
            Text(if (state.connected) "CONNECTED" else "DIRECT BLE", color = if (state.connected) green else accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }

        if (state.deviceName != null || state.deviceAddress != null) {
            Spacer(Modifier.height(8.dp))
            Text(listOfNotNull(state.deviceName, state.model, state.firmware?.let { "FW $it" }).joinToString(" · "), color = muted, fontSize = 8.sp)
        }

        if (state.connected) {
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemedH19cStat("HR", state.heartRateBpm?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
                ThemedH19cStat("STEPS", state.steps?.toString() ?: "—", Modifier.weight(1f))
                ThemedH19cStat("SpO₂", state.bloodOxygenPercent?.let { "$it%" } ?: "—", Modifier.weight(1f))
                ThemedH19cStat("BATTERY", state.batteryPercent?.let { "$it%" } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemedH19cAction("SYNC", accent, Modifier.weight(1f)) { H19cWearableRuntime.refreshActivity() }
                ThemedH19cAction("MEASURE HR", if (SuperhumanAppearance.darkMode) Color(0xFFFF8FA3) else Color(0xFFD46072), Modifier.weight(1f)) { H19cWearableRuntime.measureHeartRate() }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemedH19cAction(if (state.liveHeartRate) "STOP LIVE HR" else "LIVE HR", if (state.liveHeartRate) superhumanRed else green, Modifier.weight(1f)) {
                    H19cWearableRuntime.setLiveHeartRate(!state.liveHeartRate)
                }
                ThemedH19cAction("MEASURE SpO₂", if (SuperhumanAppearance.darkMode) Color(0xFF5ACBE1) else Color(0xFF20A7C4), Modifier.weight(1f)) {
                    H19cWearableRuntime.measureBloodOxygen()
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        ThemedH19cAction(
            when (state.phase) {
                H19cConnectionPhase.SCANNING -> "SCANNING…"
                H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING -> "CONNECTING…"
                H19cConnectionPhase.READY -> "DISCONNECT"
                else -> if (state.deviceAddress != null) "RECONNECT H19C" else "FIND H19C"
            },
            if (state.connected) superhumanRed else accent,
            Modifier.fillMaxWidth(),
            enabled = state.phase !in setOf(H19cConnectionPhase.SCANNING, H19cConnectionPhase.CONNECTING, H19cConnectionPhase.DISCOVERING)
        ) { if (state.connected) H19cWearableRuntime.disconnect() else connect() }
        Spacer(Modifier.height(8.dp))
        Text(
            "Direct BLE writes H19C heart rate, steps, activity calories, SpO₂ and supported sleep stages into the Project Superhuman Data Vault. Da Fit is not required for this connection.",
            color = muted, fontSize = 8.sp, lineHeight = 12.sp
        )
        state.manufacturer?.let {
            Spacer(Modifier.height(4.dp))
            Text("Protocol manufacturer: $it", color = muted, fontSize = 7.sp)
        }
    }
}

@Composable
internal fun ThemedH19cMiniMetricCard(metric: HomeMiniMetric) {
    val context = LocalContext.current
    val state by H19cWearableRuntime.state.collectAsState()
    val permissions = remember { H19cWearableRuntime.requiredPermissions() }
    H19cWearableRuntime.initialize(context)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) H19cWearableRuntime.scanAndConnect(context)
    }
    fun connect() {
        if (H19cWearableRuntime.hasPermissions(context)) {
            if (state.deviceAddress != null) H19cWearableRuntime.reconnectSaved(context) else H19cWearableRuntime.scanAndConnect(context)
        } else launcher.launch(permissions)
    }

    val accent = when (metric) {
        HomeMiniMetric.HEART_RATE -> if (SuperhumanAppearance.darkMode) Color(0xFFFF8FA3) else Color(0xFFD46072)
        HomeMiniMetric.STEPS -> superhumanBlue
        HomeMiniMetric.BLOOD_OXYGEN -> if (SuperhumanAppearance.darkMode) Color(0xFF5ACBE1) else Color(0xFF20A7C4)
        HomeMiniMetric.CALORIES -> if (SuperhumanAppearance.darkMode) Color(0xFFFFB15E) else Color(0xFFE08A2E)
    }

    Column(
        Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = if (SuperhumanAppearance.darkMode) .28f else .15f), RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("H19C DIRECT", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text(if (state.connected) "${state.deviceName ?: "H19C"} connected" else "Connect H19C directly", color = superhumanBrandText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(state.status, color = superhumanTextMuted, fontSize = 8.sp, lineHeight = 12.sp)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (!state.connected) {
                ThemedH19cAction(if (state.deviceAddress != null) "RECONNECT" else "FIND H19C", accent, Modifier.weight(1f)) { connect() }
            } else {
                when (metric) {
                    HomeMiniMetric.HEART_RATE -> {
                        ThemedH19cAction("MEASURE", accent, Modifier.weight(1f)) { H19cWearableRuntime.measureHeartRate() }
                        ThemedH19cAction(if (state.liveHeartRate) "STOP LIVE" else "LIVE HR", accent, Modifier.weight(1f)) { H19cWearableRuntime.setLiveHeartRate(!state.liveHeartRate) }
                    }
                    HomeMiniMetric.STEPS, HomeMiniMetric.CALORIES -> ThemedH19cAction("SYNC ACTIVITY", accent, Modifier.weight(1f)) { H19cWearableRuntime.refreshActivity() }
                    HomeMiniMetric.BLOOD_OXYGEN -> ThemedH19cAction("MEASURE SpO₂", accent, Modifier.weight(1f)) { H19cWearableRuntime.measureBloodOxygen() }
                }
            }
        }
    }
}

@Composable
private fun ThemedH19cStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(superhumanSurfaceSoft, RoundedCornerShape(13.dp)).padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(label, color = superhumanTextMuted, fontSize = 6.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        Text(value, color = superhumanBrandText, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun ThemedH19cAction(label: String, accent: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp).background(accent.copy(alpha = if (enabled) if (SuperhumanAppearance.darkMode) .18f else .12f else .05f), RoundedCornerShape(13.dp))
            .superhumanClickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent.copy(alpha = if (enabled) 1f else .45f), fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}
