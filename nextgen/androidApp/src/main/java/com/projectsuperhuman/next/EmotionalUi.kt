package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private val EmotionalNavy get() = superhumanBrandText
private val EmotionalInk get() = superhumanTextPrimary
private val EmotionalMuted get() = superhumanTextMuted
private val EmotionalBorder get() = superhumanBorder
private val EmotionalCard get() = superhumanSurfaceElevated
private val EmotionalTeal get() = if (SuperhumanAppearance.darkMode) Color(0xFF62D0C5) else Color(0xFF2AA5A4)
private val EmotionalBlue get() = superhumanBlue
private val EmotionalLavender get() = if (SuperhumanAppearance.darkMode) Color(0xFFB3A0EB) else Color(0xFF8B79C8)
private val EmotionalTrackNeutral get() = superhumanBorder

private data class EmotionalAxisUi(
    val id: String,
    val leftLabel: String,
    val rightLabel: String
)

private val EmotionalAxes = listOf(
    EmotionalAxisUi(EmotionalPresentationContract.HAPPY_SAD, "Happy", "Sad"),
    EmotionalAxisUi(EmotionalPresentationContract.CALM_ANXIOUS, "Calm", "Anxious"),
    EmotionalAxisUi(EmotionalPresentationContract.ENERGETIC_DRAINED, "Energetic", "Drained"),
    EmotionalAxisUi(EmotionalPresentationContract.CONFIDENT_INSECURE, "Confident", "Insecure"),
    EmotionalAxisUi(EmotionalPresentationContract.CONNECTED_LONELY, "Connected", "Lonely"),
    EmotionalAxisUi(EmotionalPresentationContract.FOCUSED_DISTRACTED, "Focused", "Distracted")
)

@Composable
internal fun EmotionalModuleScreen(
    current: EmotionalPresentationSnapshot?,
    onBack: () -> Unit,
    onRecord: (Map<String, Int>) -> Unit
) {
    var values by remember(current) {
        mutableStateOf(current?.normalizedValues ?: EmotionalPresentationContract.normalize(emptyMap()))
    }

    Column(Modifier.fillMaxSize().background(superhumanBackground)) {
        EmotionalHeader(onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmotionalHero()
            EmotionalCurrentStateCard(current = current)
            Column(
                Modifier.fillMaxWidth().background(EmotionalCard, RoundedCornerShape(25.dp))
                    .border(1.dp, EmotionalBorder, RoundedCornerShape(25.dp)).padding(horizontal = 17.dp, vertical = 16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("QUICK CHECK-IN", color = EmotionalMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Move what feels obvious", color = EmotionalInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Text("6 signals", color = EmotionalBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                EmotionalAxes.forEachIndexed { index, axis ->
                    BipolarEmotionScale(
                        leftLabel = axis.leftLabel,
                        rightLabel = axis.rightLabel,
                        value = values[axis.id] ?: 0,
                        onValueChange = { next -> values = values.toMutableMap().apply { put(axis.id, next) } },
                        testTag = "emotional_scale_${axis.id}"
                    )
                    if (index != EmotionalAxes.lastIndex) {
                        Spacer(Modifier.height(11.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(superhumanDivider))
                        Spacer(Modifier.height(11.dp))
                    }
                }
            }
            EmotionalRecordButton { onRecord(EmotionalPresentationContract.normalize(values)) }
            Text(
                "Centered is a valid answer. Adjust only the lines that feel useful right now.",
                color = EmotionalMuted, fontSize = 9.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp, bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun EmotionalHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack).semantics { contentDescription = "Back to home" }, contentAlignment = Alignment.Center) {
            Text("←", color = EmotionalNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("Emotional", color = EmotionalNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text("A quick read on right now", color = EmotionalMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun EmotionalHero() {
    val gradient = if (SuperhumanAppearance.darkMode) {
        listOf(Color(0xFF10292B), Color(0xFF211C31), superhumanSurfaceElevated)
    } else listOf(Color(0xFFEAF7F7), Color(0xFFF3F0FB), Color.White)
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(gradient), RoundedCornerShape(26.dp))
            .border(1.dp, EmotionalBorder, RoundedCornerShape(26.dp)).padding(19.dp)
    ) {
        Text("RIGHT NOW", color = EmotionalBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp)); Text("How are you arriving?", color = EmotionalInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("Glide each line toward what feels closer. No scoring, no diagnosis — just a fast snapshot you can use later.", color = EmotionalMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun EmotionalCurrentStateCard(current: EmotionalPresentationSnapshot?) {
    val modifier = Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(20.dp))
        .border(1.dp, EmotionalBorder, RoundedCornerShape(20.dp)).padding(15.dp)
    if (current == null) {
        Column(modifier.testTag("emotional_empty_state")) {
            Text("NO CHECK-IN YET", color = EmotionalMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(5.dp)); Text("Start with what feels most obvious", color = EmotionalInk, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(3.dp)); Text("Anything you leave centered stays neutral.", color = EmotionalMuted, fontSize = 9.sp)
        }
    } else {
        val normalized = current.normalizedValues
        Column(modifier.testTag("emotional_current_state")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CURRENT", color = EmotionalBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                current.recordedAtLabel?.let { Text(it, color = EmotionalMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(5.dp)); Text(emotionalHeadline(normalized), color = EmotionalInk, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                strongestAxes(normalized).take(3).forEach { axis -> EmotionalSignalChip(compactAxisValue(axis, normalized[axis.id] ?: 0), Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EmotionalSignalChip(label: String, modifier: Modifier = Modifier) {
    Box(modifier.background(superhumanSurfaceSoft, RoundedCornerShape(13.dp)).padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = EmotionalNavy, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun BipolarEmotionScale(
    leftLabel: String,
    rightLabel: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val normalized = EmotionalPresentationContract.snap(value)
    val valueLabel = bipolarValueDescription(normalized, leftLabel, rightLabel)
    val sliderModifier = if (testTag == null) Modifier else Modifier.testTag(testTag)

    fun valueFromFraction(fraction: Float): Int {
        val raw = EmotionalPresentationContract.MIN_VALUE + fraction.coerceIn(0f, 1f) *
            (EmotionalPresentationContract.MAX_VALUE - EmotionalPresentationContract.MIN_VALUE)
        return EmotionalPresentationContract.snap(raw.roundToInt())
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(leftLabel, color = EmotionalInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                valueLabel, color = EmotionalBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(superhumanAccentSoft, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text(rightLabel, color = EmotionalInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(7.dp))
        val centreMarker = if (SuperhumanAppearance.darkMode) superhumanTextMuted.copy(alpha = .7f) else Color.White.copy(alpha = .75f)
        val thumbFill = if (SuperhumanAppearance.darkMode) superhumanSurfaceElevated else Color.White
        Canvas(
            sliderModifier.fillMaxWidth().height(48.dp).semantics {
                contentDescription = "$leftLabel to $rightLabel emotional scale"
                stateDescription = valueLabel
                progressBarRangeInfo = ProgressBarRangeInfo(normalized.toFloat(), EmotionalPresentationContract.MIN_VALUE.toFloat()..EmotionalPresentationContract.MAX_VALUE.toFloat(), 39)
                setProgress { target -> onValueChange(EmotionalPresentationContract.snap(target.roundToInt())); true }
            }.pointerInput(leftLabel, rightLabel) {
                detectTapGestures { position -> onValueChange(valueFromFraction(position.x / size.width.toFloat())) }
            }.pointerInput(leftLabel, rightLabel) {
                detectHorizontalDragGestures(
                    onDragStart = { position -> onValueChange(valueFromFraction(position.x / size.width.toFloat())) },
                    onHorizontalDrag = { change, _ -> change.consume(); onValueChange(valueFromFraction(change.position.x / size.width.toFloat())) }
                )
            }
        ) {
            val sideInset = 10.dp.toPx(); val y = size.height / 2f; val startX = sideInset; val endX = size.width - sideInset
            val fraction = (normalized - EmotionalPresentationContract.MIN_VALUE).toFloat() / (EmotionalPresentationContract.MAX_VALUE - EmotionalPresentationContract.MIN_VALUE)
            val thumbX = startX + (endX - startX) * fraction
            drawLine(brush = Brush.horizontalGradient(listOf(EmotionalTeal, EmotionalTrackNeutral, EmotionalLavender), startX = startX, endX = endX), start = Offset(startX, y), end = Offset(endX, y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color = centreMarker, start = Offset(size.width / 2f, y - 7.dp.toPx()), end = Offset(size.width / 2f, y + 7.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = thumbFill, radius = 9.dp.toPx(), center = Offset(thumbX, y))
            drawCircle(color = EmotionalBorder, radius = 9.dp.toPx(), center = Offset(thumbX, y), style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = EmotionalBlue, radius = 4.5.dp.toPx(), center = Offset(thumbX, y))
        }
    }
}

@Composable
private fun EmotionalRecordButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().height(54.dp).background(Brush.horizontalGradient(listOf(Color(0xFF0D6CB4), Color(0xFF20A7C4))), shape)
            .superhumanClickable(onClick = onClick).testTag("emotional_submit").semantics { contentDescription = "Record emotional check-in" },
        contentAlignment = Alignment.Center
    ) { Text("Record check-in", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = .2.sp) }
}

@Composable
internal fun HomeEmotionalTile(current: EmotionalPresentationSnapshot?, onClick: () -> Unit) {
    HomeEmotionalFaceTile(current = current, onClick = onClick)
}

@Composable
private fun EmotionalMiniTrace(values: Map<String, Int>?) {
    val track = superhumanDivider
    val emptyDot = superhumanTextMuted
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        val trackStart = 7.dp.toPx(); val trackEnd = size.width - 7.dp.toPx(); val trackWidth = trackEnd - trackStart; val gap = size.height / EmotionalAxes.size
        EmotionalAxes.forEachIndexed { index, axis ->
            val y = gap * index + gap / 2f; val value = values?.get(axis.id) ?: 0
            val fraction = (value - EmotionalPresentationContract.MIN_VALUE).toFloat() / (EmotionalPresentationContract.MAX_VALUE - EmotionalPresentationContract.MIN_VALUE)
            val x = trackStart + trackWidth * fraction
            drawLine(track, Offset(trackStart, y), Offset(trackEnd, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(if (values == null) emptyDot else EmotionalBlue, radius = 2.7.dp.toPx(), center = Offset(x, y))
        }
    }
}

internal fun bipolarValueDescription(value: Int, leftLabel: String, rightLabel: String): String {
    val snapped = EmotionalPresentationContract.snap(value); val magnitude = abs(snapped)
    if (magnitude < 10) return "Centered"
    val side = if (snapped < 0) leftLabel else rightLabel
    return when { magnitude < 35 -> "A little ${side.lowercase()}"; magnitude < 70 -> side; else -> "Very ${side.lowercase()}" }
}

internal fun emotionalHeadline(values: Map<String, Int>): String {
    val normalized = EmotionalPresentationContract.normalize(values)
    val strongest = strongestAxes(normalized).firstOrNull() ?: return "Ready for a check-in"
    val value = normalized[strongest.id] ?: 0
    return if (abs(value) < 15) "Feeling fairly balanced" else "Leaning ${if (value < 0) strongest.leftLabel.lowercase() else strongest.rightLabel.lowercase()}"
}

private fun strongestAxes(values: Map<String, Int>): List<EmotionalAxisUi> = EmotionalAxes.sortedByDescending { axis -> abs(values[axis.id] ?: 0) }
private fun compactAxisValue(axis: EmotionalAxisUi, value: Int): String = if (abs(value) < 10) "Centered" else if (value < 0) axis.leftLabel else axis.rightLabel

@Preview(name = "Emotional · Empty", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun EmotionalEmptyPreview() { ProjectSuperhumanTheme { EmotionalModuleScreen(null, {}, {}) } }

@Preview(name = "Emotional · Current", showBackground = true, backgroundColor = 0xFFF8FBFD)
@Composable
private fun EmotionalCurrentPreview() {
    ProjectSuperhumanTheme {
        EmotionalModuleScreen(
            current = EmotionalPresentationSnapshot(
                axisValues = mapOf(
                    EmotionalPresentationContract.HAPPY_SAD to -45,
                    EmotionalPresentationContract.CALM_ANXIOUS to -65,
                    EmotionalPresentationContract.ENERGETIC_DRAINED to 25,
                    EmotionalPresentationContract.CONFIDENT_INSECURE to -30,
                    EmotionalPresentationContract.CONNECTED_LONELY to -55,
                    EmotionalPresentationContract.FOCUSED_DISTRACTED to -50
                ), recordedAtLabel = "Just now"
            ), onBack = {}, onRecord = {}
        )
    }
}
