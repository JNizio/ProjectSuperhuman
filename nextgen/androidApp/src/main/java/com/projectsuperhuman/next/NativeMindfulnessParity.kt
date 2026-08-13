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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MindPurple = Color(0xFF6547C9)
private val MindBlue = Color(0xFF0D6CB4)
private val MindGreen = Color(0xFF168A78)
private val MindOrange = Color(0xFFD97706)
private val MindInk = Color(0xFF0B1F35)
private val MindMuted = Color(0xFF64748B)
private val MindBg = Color(0xFFF6F9FC)

private enum class MindMode(val label: String) {
    BREATHING("Breathing"), MEDITATION("Meditation"), BODY_SCAN("Body scan")
}

@Composable
internal fun NativeMindfulnessParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    var guidedRoutineOpen by remember { mutableStateOf(false) }
    if (guidedRoutineOpen) {
        GuidedDeepBreathRoutine(onBack = { guidedRoutineOpen = false })
        return
    }

    val scope = rememberCoroutineScope()
    val domainData = remember { NativeDomainData.forDomain(HealthDomain.MINDFULNESS) }
    var mode by remember { mutableStateOf(MindMode.BREATHING) }
    var minutesText by remember { mutableStateOf("5") }
    var stressBefore by remember { mutableStateOf("5") }
    var stressAfter by remember { mutableStateOf("3") }
    var running by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }
    var todayMinutes by remember { mutableStateOf(0) }
    var sevenDayMinutes by remember { mutableStateOf(0) }
    var sessions7d by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Ready") }

    suspend fun refresh() {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        val values = domainData.between("mindfulness_session_minutes", now - 7L * day, now)
        sevenDayMinutes = values.sumOf { it.value }.roundToInt()
        sessions7d = values.size
        todayMinutes = values.filter { it.timestampEpochMs >= now - day }.sumOf { it.value }.roundToInt()
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(running, secondsLeft) {
        if (running && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        } else if (running && secondsLeft <= 0) {
            running = false
            status = "Session complete — save it below"
        }
    }

    fun startTimer() {
        val mins = minutesText.toIntOrNull()?.coerceIn(1, 120) ?: 5
        secondsLeft = mins * 60
        running = true
        status = "${mode.label} in progress"
    }

    fun saveSession() {
        val mins = minutesText.toDoubleOrNull()?.coerceIn(1.0, 120.0) ?: 5.0
        val before = stressBefore.toDoubleOrNull()?.coerceIn(0.0, 10.0)
        val after = stressAfter.toDoubleOrNull()?.coerceIn(0.0, 10.0)
        val now = System.currentTimeMillis()
        val meta = mapOf(
            "mode" to mode.name,
            "stressBefore" to (before?.toString() ?: ""),
            "stressAfter" to (after?.toString() ?: "")
        )
        val values = mutableListOf(
            HealthValue(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", mins, "min", now, "native-mindfulness", meta)
        )
        if (before != null) values += HealthValue(HealthDomain.MINDFULNESS, "stress_before", before, "0-10", now, "native-mindfulness", meta)
        if (after != null) values += HealthValue(HealthDomain.MINDFULNESS, "stress_after", after, "0-10", now, "native-mindfulness", meta)
        scope.launch {
            NativeDataHub.saveValues(values)
            refresh()
            status = "Saved ${mins.roundToInt()} min ${mode.label.lowercase()} session"
        }
    }

    Column(
        Modifier.fillMaxSize().background(MindBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("←", color = MindBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mindfulness", color = MindInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Breathing, meditation & stress regulation", color = MindMuted, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MindStat("TODAY", "$todayMinutes min", MindPurple, Modifier.weight(1f))
            MindStat("7 DAYS", "$sevenDayMinutes min", MindBlue, Modifier.weight(1f))
            MindStat("SESSIONS", sessions7d.toString(), MindGreen, Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF082D66), Color(0xFF0D6CB4), Color(0xFF24A6B7))),
                RoundedCornerShape(24.dp)
            ).clickable { guidedRoutineOpen = true }.padding(17.dp)
        ) {
            Text("GUIDED BREATHWORK", color = Color.White.copy(alpha = .68f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(5.dp))
            Text("Deep-breath bubble routine", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("30 breaths · 1:30 hold · recovery · 30 breaths · 2:00 hold", color = Color.White.copy(alpha = .76f), fontSize = 9.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.background(Color.White.copy(alpha = .13f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("OPEN GUIDED SESSION  →", color = Color(0xFF7CE3D2), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Choose practice", color = MindInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MindMode.values().forEach { item ->
                    val active = mode == item
                    Box(
                        Modifier.weight(1f).background(if (active) MindPurple else MindPurple.copy(alpha = .08f), RoundedCornerShape(14.dp)).clickable { mode = item }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.label, color = if (active) Color.White else MindPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Minutes") }
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().background(if (running) MindOrange else MindPurple, RoundedCornerShape(16.dp)).clickable { if (running) running = false else startTimer() }.padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                val timer = if (running) String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60) else "START ${mode.label.uppercase()}"
                Text(timer, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            if (running) {
                Spacer(Modifier.height(8.dp))
                Text("Tap the timer to pause. Slow, comfortable breathing; never force breath holds or continue if dizzy.", color = MindMuted, fontSize = 9.sp, lineHeight = 14.sp)
            }
        }

        Column(Modifier.fillMaxWidth().background(MindPurple.copy(alpha = .08f), RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Stress check-in", color = MindPurple, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Optional 0–10 ratings help show whether a session changes perceived stress.", color = MindMuted, fontSize = 9.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = stressBefore, onValueChange = { stressBefore = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Before") })
                OutlinedTextField(value = stressAfter, onValueChange = { stressAfter = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("After") })
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().background(MindGreen, RoundedCornerShape(16.dp)).clickable { saveSession() }.padding(14.dp), contentAlignment = Alignment.Center) {
                Text("SAVE SESSION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(7.dp))
            Text(status, color = MindMuted, fontSize = 9.sp)
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Text("Practice approach", color = MindInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("Use relaxed breathing and non-reactive attention. The aim is practice and nervous-system regulation, not forcing symptoms or sensations to disappear.", color = MindMuted, fontSize = 9.sp, lineHeight = 14.sp)
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Text("Existing guided tools", color = MindBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("The previous mindfulness tools remain available as a compatibility fallback during parity testing.", color = MindMuted, fontSize = 9.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().background(MindBlue, RoundedCornerShape(15.dp)).clickable(onClick = openLegacy).padding(13.dp), contentAlignment = Alignment.Center) {
                Text("OPEN EXISTING TOOLS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MindStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(label, color = MindMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}