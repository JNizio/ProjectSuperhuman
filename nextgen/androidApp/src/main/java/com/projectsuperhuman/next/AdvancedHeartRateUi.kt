package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
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

@Composable
internal fun AdvancedHeartRateSection(syncing: Boolean, refreshSignal: String) {
    var analysis by remember { mutableStateOf<StoredHeartRateAdvancedAnalysis?>(null) }

    LaunchedEffect(syncing, refreshSignal) {
        if (!syncing) analysis = HeartRateAdvancedHealthConnect.loadLatestStored()
    }

    val data = analysis
    if (data == null) {
        AdvancedHeartRateWaitingCard()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativeAnalysisHeader(data)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AdvancedMetricCard(
                label = "BASELINE",
                value = "${data.estimatedBaselineBpm.roundToInt()} bpm",
                caption = "estimated low-state",
                modifier = Modifier.weight(1f)
            )
            AdvancedMetricCard(
                label = "STABILITY",
                value = "${data.stabilityScore.roundToInt()}",
                caption = "sample stability",
                modifier = Modifier.weight(1f)
            )
            AdvancedMetricCard(
                label = "COVERAGE",
                value = "${data.coveragePct.roundToInt()}%",
                caption = "24h signal bins",
                modifier = Modifier.weight(1f)
            )
        }
        HeartRateSmoothedTraceCard(data)
        RelativeIntensityCard(data)
        HeartRateSignalNotesCard(data)
    }
}

@Composable
private fun AdvancedHeartRateWaitingCard() {
    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NATIVE ANALYSIS", color = AdvancedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
            Spacer(Modifier.width(7.dp))
            NativeBadge()
        }
        Spacer(Modifier.height(7.dp))
        Text("Waiting for enough heart-rate samples", color = AdvancedNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(
            "After Heart Rate syncs, Project Superhuman analyses the latest 24 hours locally on-device.",
            color = AdvancedMuted,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun NativeAnalysisHeader(data: StoredHeartRateAdvancedAnalysis) {
    val time = remember(data.analysedAtEpochMs) {
        Instant.ofEpochMilli(data.analysedAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    Row(
        Modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Color.White, AdvancedHeart.copy(alpha = .07f))),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, AdvancedHeart.copy(alpha = .15f), RoundedCornerShape(22.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NATIVE SIGNAL ANALYSIS", color = AdvancedHeart, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                Spacer(Modifier.width(7.dp))
                NativeBadge()
            }
            Spacer(Modifier.height(5.dp))
            Text("${data.sampleCount} Samsung samples", color = AdvancedNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Analysed locally · $time", color = AdvancedMuted, fontSize = 8.sp)
        }
        Text(
            "${data.latestSmoothedBpm.roundToInt()} bpm",
            color = AdvancedNavy,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun NativeBadge() {
    Box(
        Modifier.background(AdvancedNavy.copy(alpha = .08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text("C++", color = AdvancedNavy, fontSize = 7.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AdvancedMetricCard(label: String, value: String, caption: String, modifier: Modifier) {
    Column(
        modifier.background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, AdvancedBorder, RoundedCornerShape(18.dp))
            .padding(11.dp)
    ) {
        Text(label, color = AdvancedMuted, fontSize = 6.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value, color = AdvancedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(caption, color = AdvancedMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun HeartRateSmoothedTraceCard(data: StoredHeartRateAdvancedAnalysis) {
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
            Text(
                "${data.minBpm.roundToInt()}–${data.maxBpm.roundToInt()} bpm",
                color = AdvancedMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        HeartRateSmoothedTrace(data.smoothed, data.estimatedBaselineBpm)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(2.dp).background(AdvancedHeart.copy(alpha = .75f)))
            Spacer(Modifier.width(5.dp))
            Text("smoothed signal", color = AdvancedMuted, fontSize = 7.sp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(18.dp).height(1.dp).background(AdvancedBlue.copy(alpha = .45f)))
            Spacer(Modifier.width(5.dp))
            Text("estimated baseline", color = AdvancedMuted, fontSize = 7.sp)
        }
    }
}

@Composable
private fun HeartRateSmoothedTrace(points: List<HeartRateSmoothedPoint>, baseline: Double) {
    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
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

        fun yFor(value: Double): Float =
            (size.height - (((value - min) / range).toFloat() * size.height * .78f) - size.height * .10f)

        val baselineY = yFor(baseline)
        drawLine(
            AdvancedBlue.copy(alpha = .30f),
            Offset(0f, baselineY),
            Offset(size.width, baselineY),
            strokeWidth = 2f
        )

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = (((point.timestampEpochMs.toDouble() - start) / timeRange).toFloat() * size.width)
            val y = yFor(point.bpm)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AdvancedHeart, style = Stroke(width = 4f))
        val last = points.last()
        drawCircle(AdvancedHeart, radius = 5f, center = Offset(size.width, yFor(last.bpm)))
    }
}

@Composable
private fun RelativeIntensityCard(data: StoredHeartRateAdvancedAnalysis) {
    val bands = listOf(
        Triple("Low", data.lowBandPct, AdvancedBlue),
        Triple("Moderate", data.moderateBandPct, AdvancedCyan),
        Triple("Elevated", data.elevatedBandPct, AdvancedAmber),
        Triple("High", data.highBandPct, AdvancedHeart)
    )
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("RELATIVE INTENSITY", color = AdvancedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(3.dp))
        Text("Where your samples sat today", color = AdvancedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bands.forEach { (_, pct, color) ->
                if (pct > 0.0) {
                    Box(
                        Modifier.weight(pct.toFloat().coerceAtLeast(.5f))
                            .height(12.dp)
                            .background(color.copy(alpha = .82f), RoundedCornerShape(8.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bands.forEach { (label, pct, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${pct.roundToInt()}%", color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(label, color = AdvancedMuted, fontSize = 6.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Adaptive bands are relative to your own recent signal, not medical or exercise-training zones.",
            color = AdvancedMuted,
            fontSize = 7.sp,
            lineHeight = 11.sp
        )
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
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(AdvancedPurple.copy(alpha = .07f), Color.White)), RoundedCornerShape(22.dp))
            .border(1.dp, AdvancedPurple.copy(alpha = .13f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text("SIGNAL NOTES", color = AdvancedPurple, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(7.dp))
        SignalNote("Direction", trendText)
        SignalNote(
            "Unusual samples",
            if (data.unusualSampleCount == 0) "None flagged by the robust signal filter" else "${data.unusualSampleCount} sample${if (data.unusualSampleCount == 1) "" else "s"} stood out from the local pattern"
        )
        if (recovery != null && recovery.isFinite()) {
            SignalNote("Observed post-peak drop", "${recovery.roundToInt()} bpm within the available 2–12 minute window")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Stability describes sampled BPM consistency, not HRV. Unusual-sample flags are signal quality/pattern cues, not arrhythmia detection or diagnosis.",
            color = AdvancedMuted,
            fontSize = 7.sp,
            lineHeight = 11.sp
        )
    }
}

@Composable
private fun SignalNote(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AdvancedMuted, fontSize = 8.sp, modifier = Modifier.weight(.38f))
        Text(value, color = AdvancedNavy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.62f))
    }
}
