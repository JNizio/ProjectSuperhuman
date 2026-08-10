package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

private val DashInk = Color(0xFF0B1F35)
private val DashMuted = Color(0xFF64748B)
private val DashBlue = Color(0xFF0D6CB4)
private val DashGreen = Color(0xFF168A78)
private val DashBorder = Color(0xFFE7ECF2)

private data class BodyProfileUi(
    val heightCm: String = "",
    val age: String = "",
    val male: Boolean? = null,
    val goalKg: String = "",
    val activity: Int = 1
)

private data class TrendMetric(
    val title: String,
    val metric: String,
    val unit: String,
    val decimals: Int = 1
)

private val trendMetrics = listOf(
    TrendMetric("Weight", "body_weight_kg", "kg"),
    TrendMetric("Body fat", "body_fat_pct", "%"),
    TrendMetric("Muscle", "body_muscle_pct", "%"),
    TrendMetric("Water", "body_water_pct", "%"),
    TrendMetric("Skeletal muscle", "body_skeletal_muscle_pct", "%"),
    TrendMetric("Visceral fat", "body_visceral_fat_estimate", "", 0),
    TrendMetric("BMI", "body_bmi", "", 1)
)

private val activityLabels = listOf("Sedentary", "Lightly active", "Moderately active", "Very active", "Athlete")

@Composable
internal fun BodyProfileSetupCard(onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(BodyProfileUi()) }
    var editing by remember { mutableStateOf(false) }
    var activityOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val values = NativeDataHub.latestForDomain(HealthDomain.BODY)
        fun metric(name: String) = values.firstOrNull { it.metric == name }?.value
        profile = BodyProfileUi(
            heightCm = metric("body_height_cm")?.let { "%.0f".format(it) } ?: "",
            age = metric("body_age_years")?.let { "%.0f".format(it) } ?: "",
            male = metric("body_sex_code")?.let { it >= .5 },
            goalKg = metric("body_goal_weight_kg")?.let { "%.1f".format(it) } ?: "",
            activity = metric("body_activity_level")?.toInt()?.coerceIn(0, activityLabels.lastIndex) ?: 1
        )
    }

    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your profile", color = DashInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Used for body-composition estimates and goals", color = DashMuted, fontSize = 9.sp)
            }
            Text(if (editing) "CLOSE" else "EDIT", color = DashBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { editing = !editing })
        }

        if (!editing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ProfileMini("HEIGHT", profile.heightCm.ifBlank { "—" } + if (profile.heightCm.isNotBlank()) " cm" else "", Modifier.weight(1f))
                ProfileMini("AGE", profile.age.ifBlank { "—" }, Modifier.weight(1f))
                ProfileMini("GOAL", profile.goalKg.ifBlank { "—" } + if (profile.goalKg.isNotBlank()) " kg" else "", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ProfileMini("SEX", when(profile.male){true->"Male";false->"Female";null->"—"}, Modifier.weight(1f))
                ProfileMini("ACTIVITY", activityLabels[profile.activity], Modifier.weight(2f))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(profile.heightCm, { v -> profile = profile.copy(heightCm = clean(v, 5)) }, Modifier.weight(1f), label = { Text("Height cm") }, singleLine = true)
                OutlinedTextField(profile.age, { v -> profile = profile.copy(age = clean(v, 3)) }, Modifier.weight(1f), label = { Text("Age") }, singleLine = true)
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFFF4F7FA), RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SexChoice("Male", profile.male == true, Modifier.weight(1f)) { profile = profile.copy(male = true) }
                SexChoice("Female", profile.male == false, Modifier.weight(1f)) { profile = profile.copy(male = false) }
            }
            OutlinedTextField(profile.goalKg, { v -> profile = profile.copy(goalKg = clean(v, 6)) }, Modifier.fillMaxWidth(), label = { Text("Goal weight (kg)") }, singleLine = true)
            Box {
                Box(Modifier.fillMaxWidth().background(Color(0xFFF4F7FA), RoundedCornerShape(14.dp)).clickable { activityOpen = true }.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Activity level", color = DashMuted, fontSize = 10.sp)
                        Text(activityLabels[profile.activity] + "  ▾", color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                DropdownMenu(expanded = activityOpen, onDismissRequest = { activityOpen = false }) {
                    activityLabels.forEachIndexed { index, label ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { profile = profile.copy(activity = index); activityOpen = false })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().background(DashGreen, RoundedCornerShape(15.dp)).clickable {
                scope.launch {
                    profile.heightCm.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_height_cm", it, "cm") }
                    profile.age.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_age_years", it, "years") }
                    profile.male?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_sex_code", if (it) 1.0 else 0.0, "code") }
                    profile.goalKg.toDoubleOrNull()?.let { NativeDataHub.saveMetric(HealthDomain.BODY, "body_goal_weight_kg", it, "kg") }
                    NativeDataHub.saveMetric(HealthDomain.BODY, "body_activity_level", profile.activity.toDouble(), "level")
                    OkokScaleManager.setProfile(profile.heightCm.toDoubleOrNull(), profile.male)
                    status = "Profile saved"
                    editing = false
                    onSaved()
                }
            }.padding(14.dp), contentAlignment = Alignment.Center) {
                Text("SAVE PROFILE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            if (status.isNotBlank()) Text(status, color = DashMuted, fontSize = 8.sp)
        }
    }
}

@Composable
internal fun BodyOverTimeSection() {
    var selected by remember { mutableStateOf(trendMetrics.first()) }
    var history by remember { mutableStateOf<List<HealthValue>>(emptyList()) }

    LaunchedEffect(selected.metric) {
        val now = System.currentTimeMillis()
        history = NativeDataHub.between(selected.metric, now - 365L * 24L * 60L * 60L * 1000L, now)
            .sortedBy { it.timestampEpochMs }
            .takeLast(24)
    }

    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Change over time", color = DashInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Switch metrics to see how your body is changing", color = DashMuted, fontSize = 9.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            trendMetrics.chunked(4).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowMetrics.forEach { metric ->
                        val active = metric.metric == selected.metric
                        Box(Modifier.weight(1f).background(if (active) DashBlue else Color(0xFFF2F6FA), RoundedCornerShape(12.dp)).clickable { selected = metric }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                            Text(metric.title, color = if (active) Color.White else DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    repeat(4 - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (history.size < 2) {
            Box(Modifier.fillMaxWidth().background(Color(0xFFF7F9FC), RoundedCornerShape(16.dp)).padding(18.dp), contentAlignment = Alignment.Center) {
                Text("More readings will build your ${selected.title.lowercase()} trend.", color = DashMuted, fontSize = 10.sp)
            }
        } else {
            val values = history.map { it.value }
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            val span = (max - min).coerceAtLeast(.1)
            val change = values.last() - values.first()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(formatMetric(values.last(), selected), color = DashInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Latest", color = DashMuted, fontSize = 8.sp)
                }
                Text(changeText(change, selected), color = if (abs(change) < .01) DashMuted else DashBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                history.forEach { point ->
                    val fraction = ((point.value - min) / span).coerceIn(0.0, 1.0)
                    Box(Modifier.weight(1f).height((18 + 70 * fraction).dp).background(DashBlue.copy(alpha = .20f), RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

@Composable
private fun ProfileMini(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color(0xFFF6F8FB), RoundedCornerShape(13.dp)).padding(10.dp)) {
        Text(label, color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SexChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(if (selected) DashBlue else Color.Transparent, RoundedCornerShape(11.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else DashMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

private fun clean(v: String, max: Int) = v.filter { it.isDigit() || it == '.' }.take(max)
private fun formatMetric(value: Double, metric: TrendMetric): String = if (metric.decimals == 0) "%.0f%s".format(value, metric.unit) else "%.1f%s".format(value, if (metric.unit.isBlank()) "" else " ${metric.unit}")
private fun changeText(change: Double, metric: TrendMetric): String {
    val sign = if (change > 0) "+" else ""
    return if (metric.decimals == 0) "$sign${"%.0f".format(change)} ${metric.unit}".trim() else "$sign${"%.1f".format(change)} ${metric.unit}".trim()
}
