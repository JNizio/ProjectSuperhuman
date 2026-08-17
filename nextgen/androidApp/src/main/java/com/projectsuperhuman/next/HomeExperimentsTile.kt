package com.projectsuperhuman.next

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ExperimentTileBlue = Color(0xFF0D6CB4)
private val ExperimentTileViolet = Color(0xFF7257B7)
private val ExperimentTileTeal = Color(0xFF20A7A5)

@Composable
internal fun HomeExperimentsTile(
    experiment: ExperimentPresentation = MockExperimentData.active,
    onClick: () -> Unit
) {
    val palette = superhumanPalette()
    val dark = superhumanDarkMode()
    val primary = experiment.primaryOutcome?.title ?: "No outcome selected"
    val secondary = experiment.outcomes.filter { it.role == ExperimentOutcomeRole.SECONDARY }.joinToString(" · ") { it.title }
    val description = "Experiments. Active experiment ${experiment.title}. Day ${experiment.progress.currentDay} of ${experiment.progress.totalDays}. Primary outcome $primary. Next check-in ${experiment.nextCheckIn ?: "not scheduled"}."

    Box(
        Modifier.fillMaxWidth()
            .height(184.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        if (dark) palette.surfaceAccent.copy(alpha = .78f) else Color(0xFFF1EDFC),
                        palette.surface,
                        if (dark) palette.surfaceRaised else Color(0xFFECF8F7)
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .border(1.dp, palette.border, RoundedCornerShape(28.dp))
            .superhumanHomeTileClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExperimentOrbitGlyph()
                    Spacer(Modifier.width(8.dp))
                    Text("EXPERIMENTS", color = palette.muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                }
                Box(
                    Modifier.size(34.dp)
                        .background(palette.surfaceRaised.copy(alpha = .92f), CircleShape)
                        .border(1.dp, palette.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", color = if (dark) palette.accent else ExperimentTileBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ExperimentProgressRing(
                    progress = experiment.progress.fraction,
                    center = "${experiment.progress.currentDay}/${experiment.progress.totalDays}",
                    modifier = Modifier.size(76.dp),
                    textColor = palette.ink
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("ACTIVE EXPERIMENT", color = if (dark) palette.accentPurple else ExperimentTileViolet, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(experiment.title, color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Text("Primary · $primary", color = palette.ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (secondary.isNotBlank()) Text(secondary, color = palette.muted, fontSize = 8.sp, maxLines = 1)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(ExperimentTileTeal, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(experiment.nextCheckIn ?: "No check-in scheduled", color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperimentOrbitGlyph() {
    Canvas(Modifier.size(18.dp)) {
        drawCircle(ExperimentTileViolet.copy(alpha = .18f), radius = size.minDimension * .46f, style = Stroke(width = 2f))
        drawCircle(ExperimentTileViolet, radius = size.minDimension * .12f, center = center)
        drawCircle(ExperimentTileTeal, radius = size.minDimension * .09f, center = Offset(size.width * .83f, size.height * .34f))
    }
}

@Composable
internal fun ExperimentProgressRing(
    progress: Float,
    center: String,
    modifier: Modifier = Modifier,
    foreground: Color = ExperimentTileViolet,
    textColor: Color? = null
) {
    val resolvedText = textColor ?: superhumanPalette().ink
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * .09f
            drawArc(
                color = foreground.copy(alpha = .13f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = foreground,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = resolvedText, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("DAY", color = resolvedText.copy(alpha = .62f), fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        }
    }
}
