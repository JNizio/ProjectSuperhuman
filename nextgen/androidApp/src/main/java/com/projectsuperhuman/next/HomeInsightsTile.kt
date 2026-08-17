package com.projectsuperhuman.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeInsightsTile(state: InsightsPresentationState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(25.dp)
    val leading = state.insights.firstOrNull()
    val palette = superhumanPalette
    val dark = SuperhumanAppearance.darkMode
    val animatedStrength by animateFloatAsState(
        targetValue = leading?.strength ?: 0f,
        animationSpec = tween(700),
        label = "insights-home-strength"
    )
    val description = if (leading == null) {
        "Insights. No connections yet. Keep using Superhuman to build history."
    } else {
        "Insights. ${state.summaryHeadline}. Leading pattern: ${leading.headline}. ${leading.confidence.label}."
    }

    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.surface,
                        palette.accentSoft.copy(alpha = if (dark) .62f else .30f),
                        palette.surfaceElevated.copy(alpha = if (dark) .90f else .46f)
                    )
                ),
                shape
            )
            .border(1.dp, palette.border, shape)
            .superhumanHomeTileClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("home_insights_tile")
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("INSIGHTS", color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("→", color = palette.textMuted, fontSize = 23.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (leading == null) "Connections will appear here" else state.summaryHeadline,
            color = palette.brandText,
            fontSize = 20.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(7.dp))
        if (leading == null) {
            Text("Keep logging a little longer.", color = palette.textMuted, fontSize = 9.sp)
        } else {
            Text(leading.headline, color = palette.textMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2)
            Spacer(Modifier.height(11.dp))
            InsightConnectionMiniVisual(leading, animatedStrength)
        }
    }
}

@Composable
private fun InsightConnectionMiniVisual(insight: InsightPresentation, animatedStrength: Float) {
    val palette = superhumanPalette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetricPill(insight.sourceMetric.label, insight.sourceMetric.tone.color, Modifier.weight(1f))
        Box(Modifier.weight(.55f).height(24.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val y = size.height / 2f
                drawLine(palette.border, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(palette.blue, 3.5.dp.toPx(), Offset(size.width * animatedStrength.coerceIn(0.08f, .92f), y))
            }
        }
        MetricPill(insight.targetMetric.label, insight.targetMetric.tone.color, Modifier.weight(1f))
    }
}

@Composable
private fun MetricPill(label: String, tone: Color, modifier: Modifier = Modifier) {
    val palette = superhumanPalette
    Row(
        modifier.background(palette.surfaceElevated.copy(alpha = .90f), RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).background(tone, CircleShape))
        Text(label, color = palette.textPrimary, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun HomeInsightsTilePreview() {
    HomeInsightsTile(MockInsightsPresentationProvider.load(), onClick = {})
}
