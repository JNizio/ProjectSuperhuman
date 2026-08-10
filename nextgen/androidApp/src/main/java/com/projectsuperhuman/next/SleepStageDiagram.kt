package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class SleepStageSegment(val type: String, val startMs: Long, val endMs: Long)

internal fun parseSleepStageSegments(raw: String?): List<SleepStageSegment> = raw.orEmpty()
    .split(';')
    .mapNotNull { item ->
        val p = item.split(',')
        if (p.size != 3) null else {
            val start = p[1].toLongOrNull()
            val end = p[2].toLongOrNull()
            if (start == null || end == null || end <= start) null else SleepStageSegment(p[0], start, end)
        }
    }
    .sortedBy { it.startMs }

@Composable
internal fun SleepArchitectureDiagram(
    segments: List<SleepStageSegment>,
    startMs: Long?,
    endMs: Long?
) {
    if (segments.isEmpty() || startMs == null || endMs == null || endMs <= startMs) return

    val awake = Color(0xFFE3A05F)
    val rem = Color(0xFF8B69E8)
    val light = Color(0xFF6697E8)
    val deep = Color(0xFF315A9E)
    val grid = Color(0xFFE9ECF4)
    val duration = (endMs - startMs).toFloat()

    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sleep architecture", color = Color(0xFF17233A), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text("How your sleep moved through each stage overnight", color = Color(0xFF718096), fontSize = 9.sp)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot("Awake", awake)
            LegendDot("REM", rem)
            LegendDot("Light", light)
            LegendDot("Deep", deep)
        }

        Canvas(Modifier.fillMaxWidth().height(158.dp)) {
            val left = 44.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val top = 8.dp.toPx()
            val rowH = 30.dp.toPx()
            val labels = listOf("Awake", "REM", "Light", "Deep")
            val colors = listOf(awake, rem, light, deep)

            for (i in 0..3) {
                val y = top + i * rowH
                drawLine(grid, Offset(left, y + rowH / 2), Offset(right, y + rowH / 2), strokeWidth = 1.dp.toPx())
            }

            fun row(type: String) = when (type) { "awake" -> 0; "rem" -> 1; "deep" -> 3; else -> 2 }
            segments.forEach { s ->
                val x1 = left + ((s.startMs - startMs) / duration) * (right - left)
                val x2 = left + ((s.endMs - startMs) / duration) * (right - left)
                val r = row(s.type)
                val y = top + r * rowH + 4.dp.toPx()
                drawRoundRect(
                    color = colors[r],
                    topLeft = Offset(x1, y),
                    size = Size((x2 - x1).coerceAtLeast(2.dp.toPx()), rowH - 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                )
            }

            // Stage labels on left.
            labels.forEachIndexed { i, label ->
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    0f,
                    top + i * rowH + rowH * .62f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(113, 128, 150)
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Spacer(Modifier.width(7.dp).height(7.dp).background(color, RoundedCornerShape(99.dp)))
        Text(label, color = Color(0xFF718096), fontSize = 8.sp)
    }
}
