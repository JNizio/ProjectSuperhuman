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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import kotlin.math.abs

private val BodyGreen = Color(0xFF168A78)
private val BodyBlue = Color(0xFF0D6CB4)
private val BodyInk = Color(0xFF0B1F35)
private val BodyMuted = Color(0xFF64748B)
private val BodyBg = Color(0xFFF6F9FC)

private data class BodySnapshot(
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val goalKg: Double? = null
)

@Composable
internal fun NativeBodyParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(BodySnapshot()) }
    var weightHistory by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    suspend fun refresh() {
        val values = NativeDataHub.latestForDomain(HealthDomain.BODY)
        fun metric(name: String) = values.firstOrNull { it.metric == name }?.value
        snapshot = BodySnapshot(
            weightKg = metric("body_weight_kg"),
            bodyFatPct = metric("body_fat_pct"),
            waistCm = metric("body_waist_cm"),
            goalKg = metric("body_goal_weight_kg")
        )
        val now = System.currentTimeMillis()
        weightHistory = NativeDataHub.between("body_weight_kg", now - 365L * 24L * 60L * 60L * 1000L, now)
            .sortedBy { it.timestampEpochMs }
            .takeLast(10)
    }

    LaunchedEffect(Unit) { refresh() }

    fun save() {
        scope.launch {
            weight.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_weight_kg", it, "kg") }
            bodyFat.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_fat_pct", it, "%") }
            waist.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_waist_cm", it, "cm") }
            goal.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_goal_weight_kg", it, "kg") }
            status = "Progress saved"
            weight = ""; bodyFat = ""; waist = ""; goal = ""
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().background(BodyBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("←", color = BodyBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Body & Progress", color = BodyInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Weight, composition, measurements & goals", color = BodyMuted, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BodyStat("WEIGHT", snapshot.weightKg?.let { "%.1f kg".format(it) } ?: "—", BodyGreen, Modifier.weight(1f))
            BodyStat("BODY FAT", snapshot.bodyFatPct?.let { "%.1f%%".format(it) } ?: "—", BodyBlue, Modifier.weight(1f))
            BodyStat("WAIST", snapshot.waistCm?.let { "%.0f cm".format(it) } ?: "—", Color(0xFF6547C9), Modifier.weight(1f))
        }

        if (weightHistory.size >= 2) {
            WeightTrendCard(weightHistory, snapshot.goalKg)
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Log progress", color = BodyInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Enter only the measurements you want to update.", color = BodyMuted, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("Weight (kg)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(bodyFat, { bodyFat = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.fillMaxWidth(), label = { Text("Body fat (%)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(waist, { waist = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("Waist (cm)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(goal, { goal = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("Goal weight (kg)") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().background(BodyGreen, RoundedCornerShape(16.dp)).clickable(onClick = { save() }).padding(14.dp), contentAlignment = Alignment.Center) {
                Text("SAVE PROGRESS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(status, color = BodyMuted, fontSize = 9.sp)
            }
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp)) {
            Text("Goal", color = BodyInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            val currentWeight = snapshot.weightKg
            val targetWeight = snapshot.goalKg
            val delta = if (currentWeight != null && targetWeight != null) currentWeight - targetWeight else null
            Text(
                when {
                    delta == null -> "Set a goal weight to see distance-to-goal context."
                    abs(delta) < 0.05 -> "At goal weight."
                    delta > 0 -> "${"%.1f".format(delta)} kg above goal."
                    else -> "${"%.1f".format(-delta)} kg below goal."
                },
                color = BodyMuted,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun WeightTrendCard(history: List<HealthValue>, goalKg: Double?) {
    val values = history.map { it.value }
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val span = (max - min).coerceAtLeast(0.5)
    val change = values.last() - values.first()

    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weight trend", color = BodyInk, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Last ${history.size} entries", color = BodyMuted, fontSize = 9.sp)
            }
            Text(
                (if (change > 0) "+" else "") + "%.1f kg".format(change),
                color = if (abs(change) < 0.1) BodyMuted else if (change < 0) BodyGreen else BodyBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(74.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEach { item ->
                val fraction = ((item.value - min) / span).coerceIn(0.0, 1.0)
                Box(
                    Modifier.weight(1f).height((18 + 52 * fraction).dp).background(BodyBlue.copy(alpha = .22f), RoundedCornerShape(6.dp))
                )
            }
        }
        if (goalKg != null) {
            Spacer(Modifier.height(8.dp))
            Text("Goal %.1f kg · latest %.1f kg".format(goalKg, values.last()), color = BodyMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun BodyStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(label, color = BodyMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}
