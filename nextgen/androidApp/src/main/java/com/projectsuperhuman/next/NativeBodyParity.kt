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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val BodyGreen = Color(0xFF168A78)
private val BodyTeal = Color(0xFF1D9B86)
private val BodyBlue = Color(0xFF0D6CB4)
private val BodyPurple = Color(0xFF6753D8)
private val BodyInk = Color(0xFF0B1F35)
private val BodyMuted = Color(0xFF64748B)
private val BodyBg = Color(0xFFF5F8FC)
private val BodyBorder = Color(0xFFE7ECF2)

private data class BodySnapshot(
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val goalKg: Double? = null
)

private enum class BodyView { PROGRESS, MEASUREMENTS }

@Composable
internal fun NativeBodyParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(BodySnapshot()) }
    var weightHistory by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var bodyFatHistory by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var waistHistory by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var logging by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(BodyView.PROGRESS) }

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
        val from = now - 365L * 24L * 60L * 60L * 1000L
        weightHistory = NativeDataHub.between("body_weight_kg", from, now).sortedBy { it.timestampEpochMs }.takeLast(30)
        bodyFatHistory = NativeDataHub.between("body_fat_pct", from, now).sortedBy { it.timestampEpochMs }.takeLast(30)
        waistHistory = NativeDataHub.between("body_waist_cm", from, now).sortedBy { it.timestampEpochMs }.takeLast(30)
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
            logging = false
            refresh()
        }
    }

    val latestWeight = snapshot.weightKg
    val previousWeight = weightHistory.dropLast(1).lastOrNull()?.value
    val weightChange = if (latestWeight != null && previousWeight != null) latestWeight - previousWeight else null
    val goalDelta = if (latestWeight != null && snapshot.goalKg != null) latestWeight - snapshot.goalKg!! else null

    Column(
        Modifier.fillMaxSize().background(BodyBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BodyHeader(onBack)

        BodyHero(
            snapshot = snapshot,
            weightChange = weightChange,
            goalDelta = goalDelta,
            onLog = { logging = !logging }
        )

        NativeOkokScaleCard(onSaved = { scope.launch { refresh() } })

        BodyViewToggle(view = view, onChange = { view = it })

        if (logging) {
            QuickLogCard(
                weight = weight,
                onWeight = { weight = cleanNumber(it, 6) },
                bodyFat = bodyFat,
                onBodyFat = { bodyFat = cleanNumber(it, 5) },
                waist = waist,
                onWaist = { waist = cleanNumber(it, 6) },
                goal = goal,
                onGoal = { goal = cleanNumber(it, 6) },
                onSave = ::save,
                onCancel = { logging = false },
                status = status
            )
        }

        when (view) {
            BodyView.PROGRESS -> {
                if (weightHistory.size >= 2) {
                    ProgressTrendCard(weightHistory, snapshot.goalKg)
                } else {
                    EmptyProgressCard(onLog = { logging = true })
                }
                RecentChangesCard(
                    weightHistory = weightHistory,
                    bodyFatHistory = bodyFatHistory,
                    waistHistory = waistHistory,
                    snapshot = snapshot
                )
            }
            BodyView.MEASUREMENTS -> {
                MeasurementsCard(snapshot = snapshot, onLog = { logging = true })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BodyHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(44.dp).height(44.dp).background(Color.White, RoundedCornerShape(15.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = BodyBlue, fontSize = 25.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Body & Progress", color = BodyInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Your body trends, measurements & goals", color = BodyMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BodyHero(
    snapshot: BodySnapshot,
    weightChange: Double?,
    goalDelta: Double?,
    onLog: () -> Unit
) {
    val progress = when {
        snapshot.weightKg == null || snapshot.goalKg == null -> null
        abs(snapshot.weightKg - snapshot.goalKg) < 0.05 -> 1.0
        else -> {
            val distance = abs(snapshot.weightKg - snapshot.goalKg)
            (1.0 - (distance / 15.0)).coerceIn(0.0, 1.0)
        }
    }

    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF0A6E8E), Color(0xFF138A8B), Color(0xFF1B9D82))),
            RoundedCornerShape(28.dp)
        ).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("CURRENT WEIGHT", color = Color.White.copy(alpha = .68f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    snapshot.weightKg?.let { "%.1f kg".format(it) } ?: "—",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    when {
                        weightChange == null -> "Add another entry to unlock trend context"
                        abs(weightChange) < 0.05 -> "No meaningful change since last entry"
                        weightChange < 0 -> "↓ %.1f kg since last entry".format(abs(weightChange))
                        else -> "↑ %.1f kg since last entry".format(weightChange)
                    },
                    color = Color.White.copy(alpha = .84f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                Modifier.background(Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp)).clickable(onClick = onLog).padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text("+ LOG", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroMiniStat("BODY FAT", snapshot.bodyFatPct?.let { "%.1f%%".format(it) } ?: "—", Modifier.weight(1f))
            HeroMiniStat("WAIST", snapshot.waistCm?.let { "%.0f cm".format(it) } ?: "—", Modifier.weight(1f))
            HeroMiniStat("GOAL", snapshot.goalKg?.let { "%.1f kg".format(it) } ?: "—", Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Goal progress", color = Color.White.copy(alpha = .72f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        goalDelta == null -> "Set a goal"
                        abs(goalDelta) < 0.05 -> "At goal"
                        goalDelta > 0 -> "%.1f kg to go".format(goalDelta)
                        else -> "%.1f kg below goal".format(abs(goalDelta))
                    },
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(Modifier.fillMaxWidth().height(7.dp).background(Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp))) {
                if (progress != null) {
                    Box(Modifier.fillMaxWidth(progress.toFloat()).height(7.dp).background(Color.White, RoundedCornerShape(10.dp)))
                }
            }
        }
    }
}

@Composable
private fun HeroMiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .11f), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Text(label, color = Color.White.copy(alpha = .58f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BodyViewToggle(view: BodyView, onChange: (BodyView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToggleButton("PROGRESS", view == BodyView.PROGRESS, Modifier.weight(1f)) { onChange(BodyView.PROGRESS) }
        ToggleButton("MEASUREMENTS", view == BodyView.MEASUREMENTS, Modifier.weight(1f)) { onChange(BodyView.MEASUREMENTS) }
    }
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) BodyBlue else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else BodyMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun QuickLogCard(
    weight: String,
    onWeight: (String) -> Unit,
    bodyFat: String,
    onBodyFat: (String) -> Unit,
    waist: String,
    onWaist: (String) -> Unit,
    goal: String,
    onGoal: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    status: String
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Quick log", color = BodyInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Weight first. Everything else is optional.", color = BodyMuted, fontSize = 9.sp)
            }
            Text("Close", color = BodyBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onCancel))
        }
        OutlinedTextField(weight, onWeight, Modifier.fillMaxWidth(), label = { Text("Weight (kg)") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(bodyFat, onBodyFat, Modifier.weight(1f), label = { Text("Body fat %") }, singleLine = true)
            OutlinedTextField(waist, onWaist, Modifier.weight(1f), label = { Text("Waist cm") }, singleLine = true)
        }
        OutlinedTextField(goal, onGoal, Modifier.fillMaxWidth(), label = { Text("Goal weight (kg)") }, singleLine = true)
        Box(
            Modifier.fillMaxWidth().background(BodyGreen, RoundedCornerShape(16.dp)).clickable(onClick = onSave).padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("SAVE ENTRY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        if (status.isNotBlank()) Text(status, color = BodyMuted, fontSize = 9.sp)
    }
}

@Composable
private fun ProgressTrendCard(history: List<HealthValue>, goalKg: Double?) {
    val points = history.takeLast(14)
    val values = points.map { it.value }
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val span = (max - min).coerceAtLeast(0.5)
    val change = values.last() - values.first()

    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weight trend", color = BodyInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Recent ${points.size} entries", color = BodyMuted, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (change > 0) "+" else "") + "%.1f kg".format(change),
                    color = if (abs(change) < 0.1) BodyMuted else if (change < 0) BodyGreen else BodyBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text("net change", color = BodyMuted, fontSize = 7.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(92.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEachIndexed { index, item ->
                val fraction = ((item.value - min) / span).coerceIn(0.0, 1.0)
                val isLatest = index == points.lastIndex
                Box(
                    Modifier.weight(1f).height((18 + 66 * fraction).dp)
                        .background(if (isLatest) BodyBlue else BodyBlue.copy(alpha = .18f), RoundedCornerShape(7.dp))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.1f kg".format(values.first()), color = BodyMuted, fontSize = 8.sp)
            goalKg?.let { Text("Goal %.1f kg".format(it), color = BodyGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            Text("%.1f kg".format(values.last()), color = BodyInk, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyProgressCard(onLog: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Text("Your trend is building", color = BodyInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("Log at least two weight entries to see a useful progress trend.", color = BodyMuted, fontSize = 9.sp)
        Spacer(Modifier.height(12.dp))
        Text("LOG AN ENTRY", color = BodyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onLog))
    }
}

@Composable
private fun RecentChangesCard(
    weightHistory: List<HealthValue>,
    bodyFatHistory: List<HealthValue>,
    waistHistory: List<HealthValue>,
    snapshot: BodySnapshot
) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("Recent changes", color = BodyInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text("Latest change compared with the previous recorded value", color = BodyMuted, fontSize = 8.sp)
        Spacer(Modifier.height(12.dp))
        ChangeRow("Weight", snapshot.weightKg, previousValue(weightHistory), "kg", BodyGreen)
        Spacer(Modifier.height(9.dp))
        ChangeRow("Body fat", snapshot.bodyFatPct, previousValue(bodyFatHistory), "%", BodyBlue)
        Spacer(Modifier.height(9.dp))
        ChangeRow("Waist", snapshot.waistCm, previousValue(waistHistory), "cm", BodyPurple)
    }
}

@Composable
private fun ChangeRow(label: String, latest: Double?, previous: Double?, unit: String, accent: Color) {
    val delta = if (latest != null && previous != null) latest - previous else null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(label, color = BodyInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(latest?.let { "%.1f %s".format(it, unit) } ?: "No data yet", color = BodyMuted, fontSize = 8.sp)
            }
        }
        Text(
            when {
                delta == null -> "—"
                abs(delta) < 0.05 -> "0.0 $unit"
                delta > 0 -> "+%.1f %s".format(delta, unit)
                else -> "−%.1f %s".format(abs(delta), unit)
            },
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun MeasurementsCard(snapshot: BodySnapshot, onLog: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Measurements", color = BodyInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Current saved body measurements", color = BodyMuted, fontSize = 8.sp)
            }
            Text("EDIT", color = BodyBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onLog))
        }
        Spacer(Modifier.height(14.dp))
        MeasurementRow("Weight", snapshot.weightKg?.let { "%.1f kg".format(it) } ?: "—")
        MeasurementRow("Body fat", snapshot.bodyFatPct?.let { "%.1f%%".format(it) } ?: "—")
        MeasurementRow("Waist", snapshot.waistCm?.let { "%.1f cm".format(it) } ?: "—")
        MeasurementRow("Goal weight", snapshot.goalKg?.let { "%.1f kg".format(it) } ?: "—")
        Spacer(Modifier.height(8.dp))
        Text(
            "Smart-scale composition metrics will plug into this view later without changing the core layout.",
            color = BodyMuted,
            fontSize = 8.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun MeasurementRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BodyMuted, fontSize = 10.sp)
        Text(value, color = BodyInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

private fun previousValue(history: List<HealthValue>): Double? = history.dropLast(1).lastOrNull()?.value

private fun cleanNumber(value: String, max: Int): String =
    value.filter { it.isDigit() || it == '.' }.take(max)
