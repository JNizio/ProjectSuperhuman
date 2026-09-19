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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

@Composable
internal fun CardioTrendChart(
    series: CardioTrendSeries,
    title: String,
    valueFormatter: (Double) -> String = { String.format("%.1f", it) },
    modifier: Modifier = Modifier
) {
    val values = remember(series) { series.points.mapNotNull { it.value } }
    val qualityText = remember(series) {
        val qualities = series.points
            .filter { it.value != null && it.quality != CardioMetricQuality.UNAVAILABLE }
            .map { it.quality }
            .distinct()
        when {
            qualities.isEmpty() -> "Unavailable"
            qualities.size == 1 -> when (qualities.single()) {
                CardioMetricQuality.MEASURED -> "Measured"
                CardioMetricQuality.DERIVED -> "Derived"
                CardioMetricQuality.ESTIMATED -> "Estimated"
                CardioMetricQuality.UNAVAILABLE -> "Unavailable"
            }
            else -> "Mixed data quality"
        }
    }
    val description = remember(series, title, qualityText) {
        val available = series.points.filter { it.value != null }
        if (available.isEmpty()) {
            title + ". No recorded values in this range."
        } else {
            title + ". " + available.size + " plotted periods. First " +
                valueFormatter(available.first().value!!) + ", last " +
                valueFormatter(available.last().value!!) + "."
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(superhumanSurfaceSoft, RoundedCornerShape(18.dp))
            .semantics { contentDescription = description }
            .padding(14.dp)
    ) {
        Text(
            title,
            color = superhumanTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            qualityText,
            color = superhumanTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        if (values.isEmpty()) {
            Text(
                "No recorded data for this metric in the selected range.",
                color = superhumanTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            return@Column
        }

        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: min
        val span = (max - min).takeIf { it > 0.000001 } ?: 1.0

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(valueFormatter(max), color = superhumanTextMuted, fontSize = 12.sp)
            Text(valueFormatter(min), color = superhumanTextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(156.dp)
        ) {
            val count = series.points.size
            if (count == 0) return@Canvas
            val xStep = if (count <= 1) 0f else size.width / (count - 1)
            val grid = superhumanBorder.copy(alpha = .55f)
            val accent = superhumanGreen

            repeat(4) { index ->
                val y = size.height * index / 3f
                drawLine(
                    color = grid,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            var activePath: Path? = null
            var previousHadValue = false
            series.points.forEachIndexed { index, point ->
                val value = point.value
                if (value == null) {
                    activePath?.let { drawPath(it, accent, style = Stroke(width = 3f, cap = StrokeCap.Round)) }
                    activePath = null
                    previousHadValue = false
                } else {
                    val x = if (count <= 1) size.width / 2f else xStep * index
                    val normalized = ((value - min) / span).toFloat().coerceIn(0f, 1f)
                    val y = size.height - normalized * size.height
                    if (!previousHadValue || activePath == null) {
                        activePath = Path().apply { moveTo(x, y) }
                    } else {
                        activePath?.lineTo(x, y)
                    }
                    previousHadValue = true
                    drawCircle(color = accent, radius = 4.5f, center = Offset(x, y))
                }
            }
            activePath?.let { drawPath(it, accent, style = Stroke(width = 3f, cap = StrokeCap.Round)) }
        }

        if (series.points.isNotEmpty()) {
            val dateFormatter = DateTimeFormatter.ofPattern("d MMM")
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    series.points.first().bucketStart.format(dateFormatter),
                    color = superhumanTextMuted,
                    fontSize = 12.sp
                )
                if (series.points.size > 1) {
                    Text(
                        series.points.last().bucketEnd.format(dateFormatter),
                        color = superhumanTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
