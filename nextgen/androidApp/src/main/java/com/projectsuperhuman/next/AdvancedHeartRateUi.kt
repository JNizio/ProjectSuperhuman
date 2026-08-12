package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val AdvancedHeart = Color(0xFFD46072)
private val AdvancedNavy = Color(0xFF123D70)
private val AdvancedMuted = Color(0xFF748294)
private val AdvancedBorder = Color(0xFFE3EAF0)
private val AdvancedBlue = Color(0xFF0D6CB4)
private val AdvancedCyan = Color(0xFF20A7C4)
private val AdvancedAmber = Color(0xFFD99A45)
private val AdvancedPurple = Color(0xFF7260BF)

private data class HeartAverageSummary(val today: Double?, val sevenDay: Double?)

@Composable
internal fun AdvancedHeartRateSection(syncing: Boolean, refreshSignal: String) {
    var analysis by remember { mutableStateOf<StoredHeartRateAdvancedAnalysis?>(null) }
    var averages by remember { mutableStateOf(HeartAverageSummary(null, null)) }

    LaunchedEffect(syncing, refreshSignal) {
        if (!syncing) {
            analysis = HeartRateAdvancedHealthConnect.loadLatestStored()
            averages = loadHeartAverages()
        }
    }

    val data = analysis
    if (data == null) {
        AdvancedHeartRateWaitingCard()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativeAnalysisHeader(data)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AdvancedMetricCard("BASELINE", "${data.estimatedBaselineBpm.roundToInt()} bpm", "estimated low-state", Modifier.weight(1f))
            AdvancedMetricCard("STABILITY", "${data.stabilityScore.roundToInt()}", "sample stability", Modifier.weight(1f))
            AdvancedMetricCard("COVERAGE", "${data.coveragePct.roundToInt()}%", "24h signal bins", Modifier.weight(1f))
        }
        HeartRateSmoothedTraceCard(data, averages)
        RelativeIntensityCard(data)
        HeartRateSignalNotesCard(data)
    }
}

private suspend fun loadHeartAverages(): HeartAverageSummary {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = System.currentTimeMillis()

    suspend fun dailyAverage(date: LocalDate): Double? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L)
        return NativeDataHub.between(HealthDomain.EXERCISE, "heart_rate_avg_bpm", start, end)
            .filter { it.source == MiniMetricsHealthConnect.SOURCE && it.metadata["summaryDate"] == date.toString() }
            .maxByOrNull { it.timestampEpochMs }?.value
    }

    val daily = (0L..6L).mapNotNull { dailyAverage(today.minusDays(it)) }
    return HeartAverageSummary(dailyAverage(today), daily.takeIf { it.isNotEmpty() }?.average())
}

@Composable
private fun AdvancedHeartRateWaitingCard() {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NATIVE ANALYSIS", color = AdvancedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
            Spacer(Modifier.width(7.dp)); NativeBadge()
        }
        Spacer(Modifier.height(7.dp))
        Text("Waiting for enough heart-rate samples", color = AdvancedNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text("After Heart Rate syncs, Project Superhuman analyses the latest 24 hours locally on-device.", color = AdvancedMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun NativeAnalysisHeader(data: StoredHeartRateAdvancedAnalysis) {
    val time = remember(data.analysedAtEpochMs) {
        Instant.ofEpochMilli(data.analysedAtEpochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    Row(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color.White, AdvancedHeart.copy(alpha = .07f))), RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedHeart.copy(alpha = .15f), RoundedCornerShape(22.dp)).padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NATIVE SIGNAL ANALYSIS", color = AdvancedHeart, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                Spacer(Modifier.width(7.dp)); NativeBadge()
            }
            Spacer(Modifier.height(5.dp))
            Text("${data.sampleCount} Samsung samples", color = AdvancedNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Analysed locally · $time", color = AdvancedMuted, fontSize = 8.sp)
        }
        Text("${data.latestSmoothedBpm.roundToInt()} bpm", color = AdvancedNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NativeBadge() {
    Box(Modifier.background(AdvancedNavy.copy(alpha = .08f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text("C++", color = AdvancedNavy, fontSize = 7.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AdvancedMetricCard(label: String, value: String, caption: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).border(1.dp, AdvancedBorder, RoundedCornerShape(18.dp)).padding(11.dp)) {
        Text(label, color = AdvancedMuted, fontSize = 6.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value, color = AdvancedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(caption, color = AdvancedMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun HeartRateSmoothedTraceCard(data: StoredHeartRateAdvancedAnalysis, averages: HeartAverageSummary) {
    var selectedIndex by remember(data.analysedAtEpochMs) { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let { data.smoothed.getOrNull(it) }

    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("24H SIGNAL", color = AdvancedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                Spacer(Modifier.height(3.dp))
                Text("Smoothed heart rate", color = AdvancedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Text("${data.minBpm.roundToInt()}–${data.maxBpm.roundToInt()} bpm", color = AdvancedMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            selected?.let { "${formatSignalTime(it.timestampEpochMs)}  ·  ${it.bpm.roundToInt()} bpm" } ?: "Press or drag across the signal to inspect a time",
            color = if (selected != null) AdvancedHeart else AdvancedMuted,
            fontSize = 9.sp,
            fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(8.dp))
        HeartRateSmoothedTrace(data.smoothed, data.estimatedBaselineBpm, selectedIndex) { selectedIndex = it }
        Spacer(Modifier.height(4.dp))
        SignalTimeAxis(data.smoothed)
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(2.dp).background(AdvancedHeart.copy(alpha = .75f)))
            Spacer(Modifier.width(5.dp)); Text("smoothed signal", color = AdvancedMuted, fontSize = 7.sp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(18.dp).height(1.dp).background(AdvancedBlue.copy(alpha = .45f)))
            Spacer(Modifier.width(5.dp)); Text("estimated baseline", color = AdvancedMuted, fontSize = 7.sp)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            CompactAverage("TODAY AVG", averages.today, Modifier.weight(1f))
            CompactAverage("7 DAY AVG", averages.sevenDay, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactAverage(label: String, value: Double?, modifier: Modifier) {
    Row(
        modifier.background(Color(0xFFF7FAFC), RoundedCornerShape(14.dp)).border(1.dp, AdvancedBorder, RoundedCornerShape(14.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AdvancedMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Text(value?.roundToInt()?.let { "$it bpm" } ?: "—", color = AdvancedNavy, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SignalTimeAxis(points: List<HeartRateSmoothedPoint>) {
    if (points.isEmpty()) return
    val start = points.first().timestampEpochMs
    val end = points.last().timestampEpochMs
    val span = (end - start).coerceAtLeast(1L)
    val labels = (0..4).map { i -> formatSignalTime(start + span * i / 4L) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, color = AdvancedMuted, fontSize = 7.sp, textAlign = TextAlign.Center) }
    }
}

private fun formatSignalTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun HeartRateSmoothedTrace(
    points: List<HeartRateSmoothedPoint>,
    baseline: Double,
    selectedIndex: Int?,
    onSelected: (Int?) -> Unit
) {
    fun indexForFraction(fraction: Float): Int? {
        if (points.isEmpty()) return null
        val start = points.first().timestampEpochMs.toDouble()
        val end = points.last().timestampEpochMs.toDouble()
        val target = start + (end - start).coerceAtLeast(1.0) * fraction.coerceIn(0f, 1f)
        return points.indices.minByOrNull { kotlin.math.abs(points[it].timestampEpochMs - target) }
    }

    Canvas(
        Modifier.fillMaxWidth().height(126.dp)
            .pointerInput(points) {
                detectTapGestures { offset -> onSelected(indexForFraction(offset.x / size.width.coerceAtLeast(1).toFloat())) }
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset -> onSelected(indexForFraction(offset.x / size.width.coerceAtLeast(1).toFloat())) },
                    onDrag = { change, _ ->
                        onSelected(indexForFraction(change.position.x / size.width.coerceAtLeast(1).toFloat()))
                        change.consume()
                    }
                )
            }
    ) {
        if (points.size < 2) {
            drawLine(AdvancedBorder, Offset(0f, size.height * .65f), Offset(size.width, size.height * .65f), strokeWidth = 2f)
            return@Canvas
        }
        val values = points.map { it.bpm }
        val min = minOf(values.minOrNull() ?: baseline, baseline) - 5.0
        val max = maxOf(values.maxOrNull() ?: baseline, baseline) + 5.0
        val range = (max - min).coerceAtLeast(10.0)
        val start = points.first().timestampEpochMs.toDouble()
        val end = points.last().timestampEpochMs.toDouble()
        val timeRange = (end - start).coerceAtLeast(1.0)

        fun yFor(value: Double): Float = size.height - (((value - min) / range).toFloat() * size.height * .78f) - size.height * .10f
        fun xFor(timestamp: Long): Float = (((timestamp.toDouble() - start) / timeRange).toFloat() * size.width)

        val baselineY = yFor(baseline)
        drawLine(AdvancedBlue.copy(alpha = .30f), Offset(0f, baselineY), Offset(size.width, baselineY), strokeWidth = 2f)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(point.timestampEpochMs)
            val y = yFor(point.bpm)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AdvancedHeart, style = Stroke(width = 4f))

        selectedIndex?.let { index ->
            points.getOrNull(index)?.let { point ->
                val x = xFor(point.timestampEpochMs)
                val y = yFor(point.bpm)
                drawLine(AdvancedHeart.copy(alpha = .22f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                drawCircle(Color.White, radius = 9f, center = Offset(x, y))
                drawCircle(AdvancedHeart, radius = 7f, center = Offset(x, y), style = Stroke(width = 4f))
            }
        }
        val last = points.last()
        drawCircle(AdvancedHeart, radius = 5f, center = Offset(xFor(last.timestampEpochMs), yFor(last.bpm)))
    }
}

@Composable
private fun RelativeIntensityCard(data: StoredHeartRateAdvancedAnalysis) {
    val bands = listOf(
        Triple("Low", data.lowBandPct, AdvancedBlue), Triple("Moderate", data.moderateBandPct, AdvancedCyan),
        Triple("Elevated", data.elevatedBandPct, AdvancedAmber), Triple("High", data.highBandPct, AdvancedHeart)
    )
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, AdvancedBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("RELATIVE INTENSITY", color = AdvancedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(3.dp)); Text("Where your samples sat today", color = AdvancedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bands.forEach { (_, pct, color) -> if (pct > 0.0) Box(Modifier.weight(pct.toFloat().coerceAtLeast(.5f)).height(12.dp).background(color.copy(alpha = .82f), RoundedCornerShape(8.dp))) }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bands.forEach { (label, pct, color) -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${pct.roundToInt()}%", color = color, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(label, color = AdvancedMuted, fontSize = 6.sp)
            } }
        }
        Spacer(Modifier.height(8.dp))
        Text("Adaptive bands are relative to your own recent signal, not medical or exercise-training zones.", color = AdvancedMuted, fontSize = 7.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun HeartRateSignalNotesCard(data: StoredHeartRateAdvancedAnalysis) {
    val trend = data.recentTrendBpmPerHour
    val trendText = when {
        trend == null -> "Not enough recent samples for a direction"
        abs(trend) < 1.0 -> "Recent signal is broadly steady"
        trend > 0 -> "Recent direction +${"%.1f".format(trend)} bpm/hour"
        else -> "Recent direction ${"%.1f".format(trend)} bpm/hour"
    }
    val recovery = data.observedRecoveryDropBpm
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AdvancedPurple.copy(alpha = .07f), Color.White)), RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedPurple.copy(alpha = .13f), RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("SIGNAL NOTES", color = AdvancedPurple, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(7.dp))
        SignalNote("Direction", trendText)
        SignalNote("Unusual samples", if (data.unusualSampleCount == 0) "None flagged by the robust signal filter" else "${data.unusualSampleCount} sample${if (data.unusualSampleCount == 1) "" else "s"} stood out from the local pattern")
        if (recovery != null && recovery.isFinite()) SignalNote("Observed post-peak drop", "${recovery.roundToInt()} bpm within the available 2–12 minute window")
        Spacer(Modifier.height(6.dp))
        Text("Stability describes sampled BPM consistency, not HRV. Unusual-sample flags are signal quality/pattern cues, not arrhythmia detection or diagnosis.", color = AdvancedMuted, fontSize = 7.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun SignalNote(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AdvancedMuted, fontSize = 8.sp, modifier = Modifier.weight(.38f))
        Text(value, color = AdvancedNavy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.62f))
    }
}
