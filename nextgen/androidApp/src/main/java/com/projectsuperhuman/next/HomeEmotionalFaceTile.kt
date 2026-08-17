package com.projectsuperhuman.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

internal data class EmotionalFaceState(
    val mouthCurve: Float = 0f,
    val mouthOpen: Float = 0f,
    val eyeOpen: Float = 0.56f,
    val browRaise: Float = 0f,
    val browTension: Float = 0f,
    val eyelidDroop: Float = 0f,
    val pupilDrift: Float = 0f,
    val cheekLift: Float = 0f,
    val warmth: Float = 0f
)

internal object EmotionalFaceMapper {
    fun from(values: Map<String, Int>): EmotionalFaceState {
        fun positivePole(axis: String): Float? = values[axis]?.let(EmotionalPresentationContract::snap)?.let { -it / 100f }
        val happiness = positivePole(EmotionalPresentationContract.HAPPY_SAD)
        val calm = positivePole(EmotionalPresentationContract.CALM_ANXIOUS)
        val energy = positivePole(EmotionalPresentationContract.ENERGETIC_DRAINED)
        val confidence = positivePole(EmotionalPresentationContract.CONFIDENT_INSECURE)
        val connected = positivePole(EmotionalPresentationContract.CONNECTED_LONELY)
        val focus = positivePole(EmotionalPresentationContract.FOCUSED_DISTRACTED)
        val anxiety = (-valueOrNeutral(calm)).coerceAtLeast(0f)
        val sadness = (-valueOrNeutral(happiness)).coerceAtLeast(0f)
        val drained = (-valueOrNeutral(energy)).coerceAtLeast(0f)
        val insecure = (-valueOrNeutral(confidence)).coerceAtLeast(0f)
        val distracted = (-valueOrNeutral(focus)).coerceAtLeast(0f)
        return EmotionalFaceState(
            mouthCurve = weightedAverage(happiness to 0.74f, confidence to 0.14f, connected to 0.12f).coerceIn(-1f, 1f),
            mouthOpen = (valueOrNeutral(energy).coerceAtLeast(0f) * 0.17f + abs(valueOrNeutral(happiness)) * 0.08f).coerceIn(0f, 0.25f),
            eyeOpen = (0.56f + anxiety * 0.20f + valueOrNeutral(energy) * 0.13f + valueOrNeutral(focus) * 0.05f - drained * 0.13f).coerceIn(0.28f, 0.86f),
            browRaise = (anxiety * 0.62f + valueOrNeutral(energy) * 0.10f - sadness * 0.12f).coerceIn(-0.25f, 0.72f),
            browTension = (anxiety * 0.76f + insecure * 0.24f).coerceIn(0f, 1f),
            eyelidDroop = (drained * 0.68f + sadness * 0.18f - anxiety * 0.18f).coerceIn(0f, 0.82f),
            pupilDrift = (distracted * 0.12f).coerceIn(0f, 0.12f),
            cheekLift = (valueOrNeutral(happiness).coerceAtLeast(0f) * 0.72f + valueOrNeutral(energy).coerceAtLeast(0f) * 0.18f).coerceIn(0f, 0.9f),
            warmth = weightedAverage(happiness to 0.55f, calm to 0.22f, connected to 0.23f).coerceIn(-1f, 1f)
        )
    }

    private fun valueOrNeutral(value: Float?): Float = value ?: 0f
    private fun weightedAverage(vararg inputs: Pair<Float?, Float>): Float {
        val observed = inputs.filter { it.first != null }
        if (observed.isEmpty()) return 0f
        val totalWeight = observed.sumOf { it.second.toDouble() }.toFloat()
        return observed.sumOf { (it.first!! * it.second).toDouble() }.toFloat() / totalWeight
    }
}

internal fun emotionalTileInterpretation(values: Map<String, Int>?): String {
    if (values.isNullOrEmpty()) return "How are you feeling?"
    val observed = values.mapValues { EmotionalPresentationContract.snap(it.value) }
    val energy = observed[EmotionalPresentationContract.ENERGETIC_DRAINED]
    val calmness = observed[EmotionalPresentationContract.CALM_ANXIOUS]
    val strongestMagnitude = observed.values.maxOfOrNull(::abs) ?: 0
    return when {
        strongestMagnitude < 15 -> "Feeling fairly balanced"
        energy != null && energy >= 45 && abs(energy) >= strongestMagnitude - 10 -> "Low energy"
        calmness != null && calmness <= -35 && abs(calmness) >= strongestMagnitude - 10 -> "Leaning calm"
        else -> emotionalHeadline(observed)
    }
}

internal fun emotionalFreshnessLabel(snapshot: EmotionalPresentationSnapshot, nowEpochMs: Long): String {
    val timestamp = snapshot.recordedAtEpochMs
    if (timestamp == null || timestamp <= 0L || timestamp > nowEpochMs) return snapshot.recordedAtLabel?.let { "Checked in $it" } ?: "Latest check-in"
    val elapsedMinutes = (nowEpochMs - timestamp) / 60_000L
    return when {
        elapsedMinutes < 1L -> "Checked in just now"
        elapsedMinutes < 60L -> "Checked in ${elapsedMinutes}m ago"
        elapsedMinutes < 24L * 60L -> "Checked in ${elapsedMinutes / 60L}h ago"
        elapsedMinutes < 48L * 60L -> "Checked in yesterday"
        else -> snapshot.recordedAtLabel?.let { "Checked in $it" } ?: "Latest check-in"
    }
}

@Composable
internal fun HomeEmotionalFaceTile(current: EmotionalPresentationSnapshot?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(25.dp)
    val palette = superhumanPalette
    val dark = SuperhumanAppearance.darkMode
    val observed = current?.axisValues.orEmpty()
    val faceState = EmotionalFaceMapper.from(observed)
    val interpretation = emotionalTileInterpretation(current?.axisValues)
    val supportingText = current?.let { emotionalFreshnessLabel(it, System.currentTimeMillis()) } ?: "Tap for a quick check-in"
    val description = if (current == null) "Emotional check-in. No emotional check-in yet. $supportingText" else "Emotional check-in. $interpretation. $supportingText"

    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.surface,
                        palette.surfaceElevated.copy(alpha = if (dark) .90f else .50f),
                        palette.accentSoft.copy(alpha = if (dark) .60f else .30f)
                    )
                ),
                shape
            )
            .border(1.dp, palette.border, shape)
            .superhumanHomeTileClickable(onClick = onClick)
            .testTag("home_emotional_tile")
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("EMOTIONAL", color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("→", color = palette.textMuted, fontSize = 23.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedEmotionalFace(faceState, current != null, Modifier.size(104.dp))
            Column(Modifier.weight(1f).padding(start = 17.dp)) {
                Text(interpretation, color = palette.brandText, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(supportingText, color = palette.textMuted, fontSize = 9.sp, lineHeight = 13.sp)
                if (current != null && observed.size < EmotionalPresentationContract.axisIds.size) {
                    Spacer(Modifier.height(5.dp))
                    Text("Based on ${observed.size} of 6 signals", color = palette.blue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AnimatedEmotionalFace(target: EmotionalFaceState, hasData: Boolean, modifier: Modifier = Modifier) {
    val animation = tween<Float>(durationMillis = 650)
    val mouthCurve by animateFloatAsState(target.mouthCurve, animation, label = "emotional-mouth")
    val mouthOpen by animateFloatAsState(target.mouthOpen, animation, label = "emotional-mouth-open")
    val eyeOpen by animateFloatAsState(target.eyeOpen, animation, label = "emotional-eye-open")
    val browRaise by animateFloatAsState(target.browRaise, animation, label = "emotional-brow-height")
    val browTension by animateFloatAsState(target.browTension, animation, label = "emotional-brow-tension")
    val eyelidDroop by animateFloatAsState(target.eyelidDroop, animation, label = "emotional-eyelid")
    val pupilDrift by animateFloatAsState(target.pupilDrift, animation, label = "emotional-pupil")
    val cheekLift by animateFloatAsState(target.cheekLift, animation, label = "emotional-cheek")
    val warmth by animateFloatAsState(target.warmth, animation, label = "emotional-warmth")
    val dark = SuperhumanAppearance.darkMode
    val palette = superhumanPalette

    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f
        val warm = if (dark) Color(0xFF5A3F42) else Color(0xFFF4CFC8)
        val cool = if (dark) Color(0xFF29445F) else Color(0xFFDDE7F5)
        val neutral = if (dark) Color(0xFF183040) else Color(0xFFE7F2F3)
        val tint = when {
            !hasData -> if (dark) palette.surfaceElevated else Color(0xFFF0F4F6)
            warmth >= 0f -> lerpColor(neutral, warm, warmth * 0.55f)
            else -> lerpColor(neutral, cool, -warmth * 0.55f)
        }
        drawCircle(Brush.radialGradient(listOf(if (dark) palette.surfaceElevated.copy(alpha = .95f) else Color.White.copy(alpha = .92f), tint), centre, radius), radius, centre)
        drawCircle(palette.border, radius, centre, style = Stroke(width = 1.2.dp.toPx()))

        val ink = if (hasData) palette.textPrimary.copy(alpha = .90f) else palette.textMuted
        val eyeY = size.height * (0.43f - browRaise * 0.012f)
        val eyeDx = size.width * 0.18f
        val eyeWidth = size.width * 0.15f
        val eyeHeight = size.height * (0.035f + eyeOpen * 0.075f) * (1f - eyelidDroop * 0.38f)
        val pupilRadius = size.minDimension * 0.027f

        listOf(-1f, 1f).forEach { side ->
            val eyeCentre = Offset(centre.x + eyeDx * side, eyeY)
            drawOval(if (dark) palette.surface.copy(alpha = .95f) else Color.White.copy(alpha = .92f), eyeCentre - Offset(eyeWidth / 2f, eyeHeight / 2f), Size(eyeWidth, eyeHeight))
            val drift = pupilDrift * size.width * 0.12f * side
            drawCircle(ink, pupilRadius, eyeCentre + Offset(drift, 0f))
            val browY = eyeY - size.height * (0.115f + browRaise * 0.055f)
            val innerLift = browTension * size.height * 0.035f
            val innerX = centre.x + side * size.width * 0.10f
            val outerX = centre.x + side * size.width * 0.26f
            drawLine(ink, Offset(innerX, browY - innerLift), Offset(outerX, browY + innerLift * 0.45f), 3.dp.toPx(), StrokeCap.Round)
        }

        if (cheekLift > 0.05f) {
            val cheek = Color(0xFFE99191).copy(alpha = 0.08f + cheekLift * 0.12f)
            drawOval(cheek, Offset(size.width * 0.18f, size.height * 0.59f), Size(size.width * 0.18f, size.height * 0.08f))
            drawOval(cheek, Offset(size.width * 0.64f, size.height * 0.59f), Size(size.width * 0.18f, size.height * 0.08f))
        }

        val mouthCentreY = size.height * 0.68f
        val mouthHalfWidth = size.width * 0.19f
        val curvePx = mouthCurve * size.height * 0.105f
        val mouth = Path().apply {
            moveTo(centre.x - mouthHalfWidth, mouthCentreY)
            cubicTo(centre.x - mouthHalfWidth * 0.45f, mouthCentreY + curvePx, centre.x + mouthHalfWidth * 0.45f, mouthCentreY + curvePx, centre.x + mouthHalfWidth, mouthCentreY)
        }
        drawPath(mouth, ink, style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round))
        if (mouthOpen > 0.02f) {
            drawOval(ink.copy(alpha = 0.72f), Offset(centre.x - mouthHalfWidth * 0.46f, mouthCentreY + curvePx * 0.55f), Size(mouthHalfWidth * 0.92f, size.height * mouthOpen * 0.12f))
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(start.red + (end.red - start.red) * t, start.green + (end.green - start.green) * t, start.blue + (end.blue - start.blue) * t, start.alpha + (end.alpha - start.alpha) * t)
}

@Preview(name = "Home Emotional · Empty", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun HomeEmotionalFaceEmptyPreview() { MaterialTheme { HomeEmotionalFaceTile(current = null, onClick = {}) } }

@Preview(name = "Home Emotional · Mixed state", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun HomeEmotionalFaceMixedPreview() {
    MaterialTheme {
        HomeEmotionalFaceTile(
            current = EmotionalPresentationSnapshot(
                axisValues = mapOf(
                    EmotionalPresentationContract.HAPPY_SAD to -55,
                    EmotionalPresentationContract.CALM_ANXIOUS to 35,
                    EmotionalPresentationContract.ENERGETIC_DRAINED to 60,
                    EmotionalPresentationContract.FOCUSED_DISTRACTED to 20
                ),
                recordedAtLabel = "Aug 14 · 12:00",
                recordedAtEpochMs = System.currentTimeMillis() - 34L * 60_000L
            ),
            onClick = {}
        )
    }
}
