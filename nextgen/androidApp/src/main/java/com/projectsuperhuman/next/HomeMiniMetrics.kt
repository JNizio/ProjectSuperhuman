package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal enum class HomeMiniMetric(val title: String, val subtitle: String) {
    HEART_RATE("Heart rate", "Pulse & daily range"),
    STEPS("Steps", "Daily movement"),
    BLOOD_OXYGEN("Blood oxygen", "SpO₂ readings"),
    STRESS("Stress", "Recovery & strain")
}

private data class MiniMetricSnapshot(
    val heartRateBpm: Int? = null,
    val steps: Int? = null,
    val bloodOxygenPct: Int? = null,
    val stressScore: Int? = null
)

private val MiniNavy = Color(0xFF123D70)
private val MiniMuted = Color(0xFF748294)
private val MiniBorder = Color(0xFFE3EAF0)
private val MiniHeart = Color(0xFFD46072)
private val MiniSteps = Color(0xFF0D6CB4)
private val MiniOxygen = Color(0xFF20A7C4)
private val MiniStress = Color(0xFF7260BF)

@Composable
internal fun HomeDateStrip() {
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    }
    Box(
        Modifier.fillMaxWidth()
            .height(54.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFF7FBFE), Color(0xFFEEF8FC), Color(0xFFF7FBFE))
                ),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, MiniBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            date,
            color = MiniNavy,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .35.sp
        )
    }
}

@Composable
internal fun HomeMiniMetricsGrid(openMetric: (HomeMiniMetric) -> Unit) {
    var metrics by remember { mutableStateOf(MiniMetricSnapshot()) }
    LaunchedEffect(Unit) { metrics = loadMiniMetricSnapshot() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniMetricCard(
                metric = HomeMiniMetric.HEART_RATE,
                value = metrics.heartRateBpm?.toString() ?: "—",
                unit = if (metrics.heartRateBpm != null) "bpm" else "",
                hasData = metrics.heartRateBpm != null,
                accent = MiniHeart,
                modifier = Modifier.weight(1.25f),
                style = MiniVisualStyle.PULSE,
                onClick = { openMetric(HomeMiniMetric.HEART_RATE) }
            )
            MiniMetricCard(
                metric = HomeMiniMetric.STEPS,
                value = metrics.steps?.let(::compactCount) ?: "—",
                unit = "",
                hasData = metrics.steps != null,
                accent = MiniSteps,
                modifier = Modifier.weight(.75f),
                style = MiniVisualStyle.DOTS,
                onClick = { openMetric(HomeMiniMetric.STEPS) }
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniMetricCard(
                metric = HomeMiniMetric.BLOOD_OXYGEN,
                value = metrics.bloodOxygenPct?.toString() ?: "—",
                unit = if (metrics.bloodOxygenPct != null) "%" else "",
                hasData = metrics.bloodOxygenPct != null,
                accent = MiniOxygen,
                modifier = Modifier.weight(.82f),
                style = MiniVisualStyle.RING,
                onClick = { openMetric(HomeMiniMetric.BLOOD_OXYGEN) }
            )
            MiniMetricCard(
                metric = HomeMiniMetric.STRESS,
                value = metrics.stressScore?.toString() ?: "—",
                unit = if (metrics.stressScore != null) "/100" else "",
                hasData = metrics.stressScore != null,
                accent = MiniStress,
                modifier = Modifier.weight(1.18f),
                style = MiniVisualStyle.WAVES,
                onClick = { openMetric(HomeMiniMetric.STRESS) }
            )
        }
    }
}

private enum class MiniVisualStyle { PULSE, DOTS, RING, WAVES }

@Composable
private fun MiniMetricCard(
    metric: HomeMiniMetric,
    value: String,
    unit: String,
    hasData: Boolean,
    accent: Color,
    modifier: Modifier,
    style: MiniVisualStyle,
    onClick: () -> Unit
) {
    Box(
        modifier.height(106.dp)
            .background(
                Brush.linearGradient(listOf(Color.White, accent.copy(alpha = .065f))),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, accent.copy(alpha = .18f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    metric.title.uppercase(),
                    color = MiniMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .85.sp
                )
                Text("→", color = accent.copy(alpha = .72f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = MiniNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, color = MiniMuted, fontSize = 8.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (hasData) "Latest reading" else "No data yet",
                color = MiniMuted,
                fontSize = 8.sp,
                maxLines = 1
            )
        }

        MiniMetricVisual(
            style = style,
            accent = accent,
            modifier = Modifier.align(Alignment.BottomEnd).width(52.dp).height(28.dp)
        )
    }
}

@Composable
private fun MiniMetricVisual(style: MiniVisualStyle, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        when (style) {
            MiniVisualStyle.PULSE -> {
                val path = Path().apply {
                    moveTo(0f, size.height * .62f)
                    lineTo(size.width * .24f, size.height * .62f)
                    lineTo(size.width * .36f, size.height * .30f)
                    lineTo(size.width * .49f, size.height * .84f)
                    lineTo(size.width * .63f, size.height * .48f)
                    lineTo(size.width, size.height * .48f)
                }
                drawPath(path, accent.copy(alpha = .7f), style = Stroke(width = 3f))
            }
            MiniVisualStyle.DOTS -> {
                listOf(.18f, .48f, .78f).forEachIndexed { index, x ->
                    drawCircle(
                        accent.copy(alpha = .30f + index * .18f),
                        radius = 5f + index * 2f,
                        center = Offset(size.width * x, size.height * (.66f - index * .10f))
                    )
                }
            }
            MiniVisualStyle.RING -> {
                drawCircle(accent.copy(alpha = .16f), radius = size.minDimension * .38f, center = center, style = Stroke(width = 5f))
                drawArc(accent.copy(alpha = .72f), -90f, 250f, false, style = Stroke(width = 5f))
            }
            MiniVisualStyle.WAVES -> {
                repeat(3) { row ->
                    val y = size.height * (.28f + row * .24f)
                    drawLine(
                        accent.copy(alpha = .24f + row * .17f),
                        Offset(size.width * .08f, y),
                        Offset(size.width * (.72f + row * .08f), y),
                        strokeWidth = 3f
                    )
                }
            }
        }
    }
}

@Composable
internal fun NativeMiniMetricPlaceholderPage(metric: HomeMiniMetric, onBack: () -> Unit) {
    val accent = when (metric) {
        HomeMiniMetric.HEART_RATE -> MiniHeart
        HomeMiniMetric.STEPS -> MiniSteps
        HomeMiniMetric.BLOOD_OXYGEN -> MiniOxygen
        HomeMiniMetric.STRESS -> MiniStress
    }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFFF8FBFD))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(46.dp).height(46.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, MiniBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = MiniNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(metric.title, color = MiniNavy, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(metric.subtitle, color = MiniMuted, fontSize = 10.sp)
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = .14f), Color.White)),
                    RoundedCornerShape(26.dp)
                )
                .border(1.dp, accent.copy(alpha = .16f), RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Text("NO DATA YET", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Module ready for data", color = MiniNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "The home shortcut and navigation are wired. Detailed charts, source setup and interpretation will live here when this metric is developed.",
                color = MiniMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlaceholderStat("TODAY", "—", Modifier.weight(1f))
            PlaceholderStat("7 DAY", "—", Modifier.weight(1f))
            PlaceholderStat("SOURCE", "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlaceholderStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text(label, color = MiniMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(value, color = MiniNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

private suspend fun loadMiniMetricSnapshot(): MiniMetricSnapshot {
    suspend fun latestOf(vararg metricNames: String): Double? {
        metricNames.forEach { metric ->
            NativeDataHub.latest(metric)?.value?.let { return it }
        }
        return null
    }

    val rawOxygen = latestOf("blood_oxygen_percent", "spo2", "oxygen_saturation")
    val oxygen = rawOxygen?.let { if (it in 0.0..1.2) it * 100.0 else it }

    return MiniMetricSnapshot(
        heartRateBpm = latestOf("heart_rate_bpm", "heart_rate", "hr_bpm")?.roundToInt(),
        steps = latestOf("steps", "step_count", "daily_steps")?.roundToInt(),
        bloodOxygenPct = oxygen?.roundToInt(),
        stressScore = latestOf("stress_score", "stress_level")?.roundToInt()
    )
}

private fun compactCount(value: Int): String = when {
    value >= 100_000 -> "${value / 1000}k"
    value >= 10_000 -> "%.1fk".format(value / 1000.0)
    else -> "%,d".format(value)
}
