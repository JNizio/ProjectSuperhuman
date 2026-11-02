package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sparse, factual home hypnogram. No synthetic stage cycles or invented timestamps. */
@Composable
internal fun HomeSleepCyclePlot(
    segments: List<SleepStageSegment>,
    startMs: Long,
    endMs: Long,
    modifier: Modifier = Modifier
) {
    val palette = remember { listOf(Color(0xFFE8A9A0), Color(0xFFC6A9EB), Color(0xFF9574D1), Color(0xFF7151AE)) }
    val plottedSegments = remember(segments) { segments.filter { it.type != "gap" } }
    val timeLabels = remember(startMs, endMs) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        formatter.format(Instant.ofEpochMilli(startMs)) to formatter.format(Instant.ofEpochMilli(endMs))
    }
    fun stage(s: String): Int = when (s.lowercase()) {
        "awake" -> 0; "rem" -> 1; "deep" -> 3; else -> 2
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(101.dp)) {
            val duration = (endMs - startMs).coerceAtLeast(1).toFloat()
            val left = 3.dp.toPx()
            val right = size.width - 3.dp.toPx()
            val top = 6.dp.toPx()
            val step = (size.height - 12.dp.toPx()) / 3f
            for (i in 0..3) {
                val y = top + i * step
                drawLine(superhumanDivider.copy(alpha = .45f), Offset(left, y), Offset(right, y),
                    strokeWidth = .6.dp.toPx())
            }
            plottedSegments.forEach { segment ->
                val x1 = left + ((segment.startMs - startMs).toFloat() / duration).coerceIn(0f, 1f) * (right - left)
                val x2 = left + ((segment.endMs - startMs).toFloat() / duration).coerceIn(0f, 1f) * (right - left)
                val y = top + stage(segment.type) * step
                drawLine(palette[stage(segment.type)], Offset(x1, y), Offset(x2.coerceAtLeast(x1), y),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(timeLabels.first, color = superhumanTextMuted, fontSize = 9.sp)
            Text(timeLabels.second, color = superhumanTextMuted, fontSize = 9.sp)
        }
    }
}
