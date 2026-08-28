package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

private val DashInk get() = superhumanTextPrimary
private val DashMuted get() = superhumanTextMuted
private val DashBlue get() = superhumanBlue
private val DashGreen get() = superhumanGreen
private val DashBorder get() = superhumanBorder
private val DashSurface get() = superhumanSurface
private val DashSoft get() = superhumanSurfaceSoft
private val BodyDashboardData = NativeDomainData.forDomain(HealthDomain.BODY)

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

private data class TrendRange(
    val title: String,
    val days: Long
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

private val trendRanges = listOf(
    TrendRange("30D", 30L),
    TrendRange("90D", 90L),
    TrendRange("1Y", 365L)
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
        Modifier.fillMaxWidth().background(DashSurface, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your profile", color = DashInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Used for body-composition estimates and goals", color = DashMuted, fontSize = 9.sp)
            }
            Text(if (editing) "CLOSE" else "EDIT", color = DashBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { editing = !editing })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (editing) {
                ProfileEditMini("HEIGHT", profile.heightCm, "cm", Modifier.weight(1f)) { profile = profile.copy(heightCm = clean(it, 5)) }
                ProfileEditMini("AGE", profile.age, "", Modifier.weight(1f)) { profile = profile.copy(age = clean(it, 3)) }
                ProfileEditMini("GOAL", profile.goalKg, "kg", Modifier.weight(1f)) { profile = profile.copy(goalKg = clean(it, 6)) }
            } else {
                ProfileMini("HEIGHT", profile.heightCm.ifBlank { "—" } + if (profile.heightCm.isNotBlank()) " cm" else "", Modifier.weight(1f))
                ProfileMini("AGE", profile.age.ifBlank { "—" }, Modifier.weight(1f))
                ProfileMini("GOAL", profile.goalKg.ifBlank { "—" } + if (profile.goalKg.isNotBlank()) " kg" else "", Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (editing) {
                Box(Modifier.weight(1f).height(80.dp).background(DashSoft, RoundedCornerShape(13.dp)).padding(6.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SEX", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SexChoice("Male", profile.male == true, Modifier.weight(1f)) { profile = profile.copy(male = true) }
                            SexChoice("Female", profile.male == false, Modifier.weight(1f)) { profile = profile.copy(male = false) }
                        }
                    }
                }
                Box(Modifier.weight(2f)) {
                    Column(
                        Modifier.fillMaxWidth().height(80.dp).background(DashSoft, RoundedCornerShape(13.dp)).superhumanClickable { activityOpen = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("ACTIVITY", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Text(activityLabels[profile.activity] + "  ▾", color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    DropdownMenu(expanded = activityOpen, onDismissRequest = { activityOpen = false }) {
                        activityLabels.forEachIndexed { index, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { profile = profile.copy(activity = index); activityOpen = false })
                        }
                    }
                }
            } else {
                ProfileMini("SEX", when(profile.male){true->"Male";false->"Female";null->"—"}, Modifier.weight(1f))
                ProfileMini("ACTIVITY", activityLabels[profile.activity], Modifier.weight(2f))
            }
        }
        if (editing) {
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
    var range by remember { mutableStateOf(trendRanges[1]) }
    var history by remember { mutableStateOf<List<HealthValue>>(emptyList()) }

    LaunchedEffect(selected.metric, range.days) {
        val now = System.currentTimeMillis()
        val raw = BodyDashboardData.between(
            selected.metric,
            now - range.days * 24L * 60L * 60L * 1000L,
            now
        ).sortedBy { it.timestampEpochMs }

        // A trend point represents the latest reading on that local day. This prevents
        // multiple scale readings on one day from visually crowding out the date axis.
        history = raw
            .groupBy {
                Instant.ofEpochMilli(it.timestampEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
            .values
            .mapNotNull { day -> day.maxByOrNull { it.timestampEpochMs } }
            .sortedBy { it.timestampEpochMs }
    }

    Column(Modifier.fillMaxWidth().background(DashSurface, RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Change over time", color = DashInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("Switch metrics and tap any date to inspect that reading", color = DashMuted, fontSize = 9.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            trendMetrics.chunked(4).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowMetrics.forEach { metric ->
                        val active = metric.metric == selected.metric
                        Box(Modifier.weight(1f).background(if (active) DashBlue else DashSoft, RoundedCornerShape(12.dp)).clickable { selected = metric }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                            Text(metric.title, color = if (active) Color.White else DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    repeat(4 - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            trendRanges.forEach { item ->
                val active = item.days == range.days
                Box(
                    Modifier.weight(1f)
                        .background(if (active) DashBlue.copy(alpha = .11f) else DashSoft, RoundedCornerShape(11.dp))
                        .clickable { range = item }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.title, color = if (active) DashBlue else DashMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        if (history.size < 2) {
            Box(Modifier.fillMaxWidth().background(DashSoft, RoundedCornerShape(16.dp)).padding(18.dp), contentAlignment = Alignment.Center) {
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(changeText(change, selected), color = if (abs(change) < .01) DashMuted else DashBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("${range.title} change", color = DashMuted, fontSize = 7.sp)
                }
            }

            var selectedIndex by remember(
                selected.metric,
                range.days,
                history.size,
                history.firstOrNull()?.timestampEpochMs,
                history.lastOrNull()?.timestampEpochMs
            ) { mutableStateOf(history.lastIndex) }

            val selectedDateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
            val axisDateFormat = remember(range.days) {
                SimpleDateFormat(if (range.days >= 365L) "MMM yy" else "d MMM", Locale.getDefault())
            }
            val selectedPoint = history.getOrNull(selectedIndex) ?: history.last()

            Row(
                Modifier.fillMaxWidth().background(DashSoft, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Selected day", color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(selectedDateFormat.format(Date(selectedPoint.timestampEpochMs)), color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Text(formatMetric(selectedPoint.value, selected), color = DashBlue, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }

            val firstTimestamp = history.first().timestampEpochMs
            val lastTimestamp = history.last().timestampEpochMs

            Canvas(
                Modifier.fillMaxWidth().height(158.dp).pointerInput(history, selected.metric, range.days) {
                    detectTapGestures { tap ->
                        if (history.size > 1 && size.width > 12f) {
                            val fraction = ((tap.x - 6f) / (size.width - 12f)).coerceIn(0f, 1f)
                            val target = firstTimestamp + ((lastTimestamp - firstTimestamp) * fraction).toLong()
                            selectedIndex = history.indices.minByOrNull { index ->
                                abs(history[index].timestampEpochMs - target)
                            } ?: history.lastIndex
                        }
                    }
                }
            ) {
                if (history.size > 1) {
                    val left = 6f
                    val right = size.width - 6f
                    val top = 12f
                    val bottom = size.height - 12f
                    val timeSpan = (lastTimestamp - firstTimestamp).coerceAtLeast(1L)
                    fun xFor(point: HealthValue) = left + (right - left) *
                        ((point.timestampEpochMs - firstTimestamp).toDouble() / timeSpan.toDouble()).toFloat()
                    fun yFor(v: Double) = bottom - ((v - min) / span).toFloat() * (bottom - top)

                    repeat(3) { row ->
                        val y = top + (bottom - top) * row / 2f
                        drawLine(DashBorder, Offset(left, y), Offset(right, y), strokeWidth = 1.5f)
                    }

                    val path = Path()
                    history.forEachIndexed { index, point ->
                        val x = xFor(point)
                        val y = yFor(point.value)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, DashBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))

                    val dotStep = (history.size / 90).coerceAtLeast(1)
                    history.forEachIndexed { index, point ->
                        if (index % dotStep == 0 || index == selectedIndex || index == history.lastIndex) {
                            val x = xFor(point)
                            val y = yFor(point.value)
                            drawCircle(
                                if (index == selectedIndex) DashBlue else DashBlue.copy(alpha = .50f),
                                radius = if (index == selectedIndex) 8f else 3.5f,
                                center = Offset(x, y)
                            )
                            if (index == selectedIndex) drawCircle(DashSurface, radius = 3f, center = Offset(x, y))
                        }
                    }

                    history.getOrNull(selectedIndex)?.let { point ->
                        val x = xFor(point)
                        drawLine(DashBlue.copy(alpha = .18f), Offset(x, top), Offset(x, bottom), strokeWidth = 2f)
                    }
                }
            }

            val tickIndices = listOf(0, history.lastIndex / 3, (history.lastIndex * 2) / 3, history.lastIndex).distinct()
            Row(Modifier.fillMaxWidth()) {
                tickIndices.forEachIndexed { tickPosition, index ->
                    Text(
                        axisDateFormat.format(Date(history[index].timestampEpochMs)),
                        modifier = Modifier.weight(1f),
                        color = DashMuted,
                        fontSize = 7.sp,
                        textAlign = when (tickPosition) {
                            0 -> TextAlign.Start
                            tickIndices.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        }
                    )
                }
            }
            Text("Tap anywhere on the chart — the nearest recorded day will be selected", color = DashMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ProfileEditMini(label: String, value: String, unit: String, modifier: Modifier, onValue: (String) -> Unit) {
    Column(modifier.background(DashSoft, RoundedCornerShape(13.dp)).padding(10.dp)) {
        Text(label, color = DashMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = DashInk, fontSize = 10.sp, fontWeight = FontWeight.Black),
                decorationBox = { inner -> if (value.isBlank()) Text("—", color = DashMuted, fontSize = 10.sp) else inner() }
            )
            if (unit.isNotBlank()) Text(" $unit", color = DashMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileMini(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(DashSoft, RoundedCornerShape(13.dp)).padding(10.dp)) {
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
