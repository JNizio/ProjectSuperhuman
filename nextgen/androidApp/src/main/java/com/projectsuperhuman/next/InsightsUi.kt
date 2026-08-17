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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val InsightsNavy get() = superhumanBrandText
private val InsightsBlue get() = superhumanBlue
private val InsightsCyan get() = superhumanAccent
private val InsightsInk get() = superhumanTextPrimary
private val InsightsMuted get() = superhumanTextMuted
private val InsightsBorder get() = superhumanBorder
private val InsightsCard get() = superhumanSurfaceElevated

@Composable
internal fun NativeInsightsPage(
    onBack: () -> Unit,
    provider: InsightsPresentationProvider = InsightsUiRuntime.provider
) {
    val state = remember(provider) { provider.load() }
    var selected by remember { mutableStateOf<InsightPresentation?>(null) }
    selected?.let { insight -> InsightDetailScreen(insight, onBack = { selected = null }) }
        ?: InsightsScreen(state, onBack, onOpenInsight = { selected = it })
}

@Composable
internal fun InsightsScreen(
    state: InsightsPresentationState,
    onBack: () -> Unit,
    onOpenInsight: (InsightPresentation) -> Unit
) {
    Column(Modifier.fillMaxSize().background(superhumanBackground)) {
        InsightsHeader("Insights", "Connections across your data", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            InsightsSummaryHero(state)
            if (state.insights.isEmpty()) InsightsEmptyState() else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("RECENT CONNECTIONS", color = InsightsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("What seems connected", color = InsightsInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Text("Mock preview", color = InsightsBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                state.insights.forEach { insight -> InsightCard(insight) { onOpenInsight(insight) } }
                InsightGuardrailCard()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InsightsHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            Text("←", color = InsightsNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = InsightsNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = InsightsMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun InsightsSummaryHero(state: InsightsPresentationState) {
    val leading = state.insights.maxByOrNull { it.strength }
    val gradient = if (SuperhumanAppearance.darkMode) {
        listOf(Color(0xFF10292C), Color(0xFF1B1C2D), superhumanSurfaceElevated)
    } else listOf(Color(0xFFE8F5F7), Color(0xFFF1F0FA), Color.White)
    Box(
        Modifier.fillMaxWidth().background(Brush.linearGradient(gradient), RoundedCornerShape(27.dp))
            .border(1.dp, InsightsBorder, RoundedCornerShape(27.dp)).padding(19.dp)
    ) {
        Column {
            Text("YOUR RECENT PICTURE", color = InsightsBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(7.dp))
            Text(if (state.insights.isEmpty()) "Connections need more history" else state.summaryHeadline, color = InsightsInk, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(7.dp))
            Text(if (state.insights.isEmpty()) "Keep using Superhuman and patterns can appear as your history grows." else state.summaryDescription, color = InsightsMuted, fontSize = 10.sp, lineHeight = 15.sp)
            leading?.let {
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SummaryChip(it.confidence.label); SummaryChip(it.timeframe); SummaryChip("${it.sampleCount} observations")
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String) {
    Text(
        label, color = InsightsNavy, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(superhumanSurface.copy(alpha = .90f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun InsightCard(insight: InsightPresentation, onClick: () -> Unit) {
    val animatedStrength by animateFloatAsState(insight.strength, tween(650), label = "insight-card-${insight.id}")
    Column(
        Modifier.fillMaxWidth().background(InsightsCard, RoundedCornerShape(24.dp)).border(1.dp, InsightsBorder, RoundedCornerShape(24.dp))
            .superhumanClickable(onClick = onClick).semantics(mergeDescendants = true) {
                contentDescription = "${insight.headline}. ${insight.confidence.label}. ${insight.timeframe}. ${insight.sampleCount} observations. Open detail."
            }.testTag("insight_card_${insight.id}").padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(insight.category.label.uppercase(), color = InsightsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(insight.freshness, color = InsightsMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp)); InsightConnectionVisual(insight); Spacer(Modifier.height(12.dp))
        Text(insight.headline, color = InsightsInk, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp)); Text(insight.description, color = InsightsMuted, fontSize = 9.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(13.dp)); ConfidenceRow(insight, animatedStrength); Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${insight.timeframe} · ${insight.sampleCount} observations", color = InsightsMuted, fontSize = 8.sp)
            Text("Explore →", color = InsightsBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun InsightConnectionVisual(insight: InsightPresentation) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InsightMetricNode(insight.sourceMetric, Modifier.weight(1f))
        Column(Modifier.weight(.70f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(directionGlyph(insight.direction), color = InsightsBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(insight.direction.label, color = InsightsMuted, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 2)
        }
        InsightMetricNode(insight.targetMetric, Modifier.weight(1f))
    }
}

@Composable
private fun InsightMetricNode(metric: InsightMetricPresentation, modifier: Modifier = Modifier) {
    Column(modifier.background(superhumanSurfaceSoft, RoundedCornerShape(17.dp)).padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(9.dp).background(metric.tone.color, CircleShape)); Spacer(Modifier.height(5.dp))
        Text(metric.label, color = InsightsNavy, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

private fun directionGlyph(direction: InsightDirection): String = when (direction) {
    InsightDirection.MOVE_TOGETHER -> "↗"; InsightDirection.MOVE_OPPOSITE -> "↘"; InsightDirection.MORE_AFTER -> "→"; InsightDirection.MORE_STABLE_WITH -> "↔"
}

@Composable
private fun ConfidenceRow(insight: InsightPresentation, animatedStrength: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PATTERN SIGNAL", color = InsightsMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Text(insight.confidence.label, color = InsightsNavy, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).background(superhumanSurfaceSoft, CircleShape)) {
            Box(Modifier.fillMaxWidth(animatedStrength.coerceIn(.02f, 1f)).height(5.dp).background(Brush.horizontalGradient(listOf(InsightsBlue, InsightsCyan)), CircleShape))
        }
    }
}

@Composable
private fun InsightGuardrailCard() {
    Row(Modifier.fillMaxWidth().background(superhumanSurfaceSoft, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
        Text("i", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(InsightsBlue, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text("Connections, not conclusions", color = InsightsNavy, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp)); Text("These examples show patterns worth watching. They do not establish that one signal caused another.", color = InsightsMuted, fontSize = 8.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun InsightsEmptyState() {
    Column(
        Modifier.fillMaxWidth().background(InsightsCard, RoundedCornerShape(24.dp)).border(1.dp, InsightsBorder, RoundedCornerShape(24.dp)).testTag("insights_empty_state").padding(horizontal = 20.dp, vertical = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(58.dp).background(superhumanAccentSoft, CircleShape), contentAlignment = Alignment.Center) { Text("↗", color = InsightsBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(13.dp)); Text("Your connections are still forming", color = InsightsInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp)); Text("Keep using Superhuman and connections will appear here as your history grows.", color = InsightsMuted, fontSize = 9.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(12.dp)); Text("Sleep · activity · emotional state · environment", color = InsightsBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun InsightDetailScreen(insight: InsightPresentation, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(superhumanBackground)) {
        InsightsHeader("Insight detail", insight.category.label, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            DetailHero(insight); InsightTimelineCard(insight); DetailContextCard(insight); WorthWatchingCard(insight); Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailHero(insight: InsightPresentation) {
    val gradient = if (SuperhumanAppearance.darkMode) listOf(Color(0xFF10292C), Color(0xFF1B1C2D), superhumanSurfaceElevated)
        else listOf(Color(0xFFEAF6F7), Color(0xFFF3F0FB), Color.White)
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(gradient), RoundedCornerShape(27.dp)).border(1.dp, InsightsBorder, RoundedCornerShape(27.dp)).padding(19.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(insight.confidence.label.uppercase(), color = InsightsBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(insight.freshness, color = InsightsMuted, fontSize = 8.sp)
        }
        Spacer(Modifier.height(9.dp)); Text(insight.headline, color = InsightsInk, fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp)); InsightConnectionVisual(insight); Spacer(Modifier.height(13.dp))
        Text(insight.description, color = InsightsMuted, fontSize = 10.sp, lineHeight = 15.sp); Spacer(Modifier.height(11.dp))
        Text("${insight.timeframe} · ${insight.sampleCount} observations", color = InsightsNavy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InsightTimelineCard(insight: InsightPresentation) {
    Column(Modifier.fillMaxWidth().background(InsightsCard, RoundedCornerShape(24.dp)).border(1.dp, InsightsBorder, RoundedCornerShape(24.dp)).testTag("insight_detail_timeline").padding(16.dp)) {
        Text("PAIRED TIMELINE", color = InsightsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp)); Text("Days where both signals changed", color = InsightsInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp)); PairedTimelineChart(insight); Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) { TimelineLegend(insight.sourceMetric); TimelineLegend(insight.targetMetric) }
    }
}

@Composable
private fun PairedTimelineChart(insight: InsightPresentation) {
    var entered by remember(insight.id) { mutableStateOf(false) }
    LaunchedEffect(insight.id) { entered = true }
    val reveal by animateFloatAsState(if (entered) 1f else 0f, tween(750), label = "timeline-${insight.id}")
    val grid = superhumanDivider
    Canvas(Modifier.fillMaxWidth().height(142.dp).semantics { contentDescription = "Paired timeline for ${insight.sourceMetric.label} and ${insight.targetMetric.label} across ${insight.timeframe}" }) {
        val points = insight.timeline
        if (points.size < 2) return@Canvas
        val top = 8.dp.toPx(); val bottom = size.height - 18.dp.toPx(); val height = bottom - top
        repeat(4) { line -> val y = top + height * line / 3f; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        fun linePath(selector: (InsightPairedPoint) -> Float): Path = Path().apply {
            points.forEachIndexed { index, point ->
                val x = size.width * index / points.lastIndex.toFloat(); val y = bottom - height * selector(point).coerceIn(0f, 1f)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        clipRect(right = size.width * reveal) {
            drawPath(linePath { it.sourceLevel }, insight.sourceMetric.tone.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(linePath { it.targetLevel }, insight.targetMetric.tone.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val x = size.width * index / points.lastIndex.toFloat()
                drawCircle(insight.sourceMetric.tone.color, 2.7.dp.toPx(), Offset(x, bottom - height * point.sourceLevel))
                drawCircle(insight.targetMetric.tone.color, 2.7.dp.toPx(), Offset(x, bottom - height * point.targetLevel))
            }
        }
    }
}

@Composable
private fun TimelineLegend(metric: InsightMetricPresentation) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(metric.tone.color, CircleShape)); Text(metric.label, color = InsightsMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailContextCard(insight: InsightPresentation) {
    Column(Modifier.fillMaxWidth().background(InsightsCard, RoundedCornerShape(22.dp)).border(1.dp, InsightsBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("RELATED CONTEXT", color = InsightsMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(9.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { insight.relatedContext.forEach { SummaryChip(it) } }
        Spacer(Modifier.height(11.dp)); Text("Context can help explain a connection, but it can also reveal other variables moving at the same time.", color = InsightsMuted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun WorthWatchingCard(insight: InsightPresentation) {
    Column(Modifier.fillMaxWidth().background(if (SuperhumanAppearance.darkMode) Color(0xFF10292C) else Color(0xFFEBF5F7), RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("WORTH WATCHING", color = InsightsBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp)); Text(insight.worthWatching, color = InsightsNavy, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("This is a pattern to observe—not evidence that one signal caused the other.", color = InsightsMuted, fontSize = 8.sp, lineHeight = 12.sp)
    }
}

@Preview(name = "Insights · Main", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun InsightsScreenPreview() { ProjectSuperhumanTheme { InsightsScreen(MockInsightsPresentationProvider.load(), {}, {}) } }

@Preview(name = "Insights · Detail", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun InsightDetailPreview() { ProjectSuperhumanTheme { InsightDetailScreen(MockInsightsPresentationProvider.load().insights.first(), {}) } }

@Preview(name = "Insights · Empty", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun InsightsEmptyPreview() { ProjectSuperhumanTheme { InsightsScreen(InsightsPresentationState("No patterns yet", "More history is needed.", emptyList()), {}, {}) } }
