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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Midnight UI is powered by the real NativeHomeSnapshot, not illustrative metrics.
private val midnightGreen get() = if (SuperhumanAppearance.darkMode) Color(0xFF51D1A2) else Color(0xFF168A78)
private val midnightBlue get() = if (SuperhumanAppearance.darkMode) Color(0xFF58A9FF) else Color(0xFF0D6CB4)
private val midnightPurple get() = if (SuperhumanAppearance.darkMode) Color(0xFFAA87F7) else Color(0xFF7662B9)
private val midnightOrange get() = if (SuperhumanAppearance.darkMode) Color(0xFFFFAE70) else Color(0xFFD98B2B)

@Composable
private fun MidnightHeader(
    title: String, subtitle: String, accent: Color, icon: Int,
    trailing: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = superhumanTextPrimary, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = superhumanTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        trailing()
    }
}

@Composable
private fun MidnightAction(icon: Int) {
    Box(
        Modifier.size(34.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, tint = superhumanTextMuted, modifier = Modifier.size(19.dp))
    }
}

@Composable
internal fun MidnightNutritionCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val calories = snapshot.caloriesToday.coerceAtLeast(0)
    val goal = snapshot.calorieGoal?.takeIf { it > 0 }
    val percent = goal?.let { ((calories * 100f) / it).roundToInt() }
    val progress = goal?.let { (calories.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightGreen)
            .padding(horizontal = 19.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        MidnightHeader("Nutrition", "Fuel your day", midnightGreen, R.drawable.tabler_leaf) {
            MidnightAction(R.drawable.tabler_chevron_right)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (snapshot.nutritionCaloriesComplete) "%,d".format(Locale.US, calories)
                         else "~" + "%,d".format(Locale.US, calories),
                        color = superhumanTextPrimary, fontSize = 29.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text("kcal", color = superhumanTextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(if (goal != null) "of %,d kcal".format(Locale.US, goal) else "Logged today",
                    color = superhumanTextMuted, fontSize = 11.sp, maxLines = 1)
                if (goal != null) Text("%,d kcal left".format(Locale.US, (goal - calories).coerceAtLeast(0)),
                    color = superhumanTextMuted, fontSize = 10.sp)
            }
            if (goal != null) {
                Box(Modifier.size(99.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(99.dp)) {
                        val width = 8.dp.toPx()
                        val bounds = Size(size.width - width * 2f, size.height - width * 2f)
                        val offset = Offset(width, width)
                        drawArc(superhumanSurfaceSoft, 145f, 250f, false, topLeft = offset, size = bounds,
                            style = Stroke(width, cap = StrokeCap.Round))
                        if (progress > 0f) drawArc(midnightGreen, 145f, 250f * progress, false,
                            topLeft = offset, size = bounds, style = Stroke(width, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${percent ?: 0}%", color = superhumanTextPrimary, fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Goal", color = superhumanTextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MidnightMacro("Protein", snapshot.proteinToday.coerceAtLeast(0),
                snapshot.proteinGoal, midnightGreen, Modifier.weight(1f))
            MidnightMacro("Carbs", snapshot.carbsToday.coerceAtLeast(0),
                snapshot.carbsGoal, midnightBlue, Modifier.weight(1f))
            MidnightMacro("Fat", snapshot.fatToday.coerceAtLeast(0),
                snapshot.fatGoal, midnightOrange, Modifier.weight(1f))
        }

    }
}

@Composable
private fun MidnightMacro(name: String, amount: Int, goal: Int?, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(name, color = superhumanTextMuted, fontSize = 10.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$amount g", color = superhumanTextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (goal != null && goal > 0) {
                Spacer(Modifier.width(3.dp))
                Text("${((amount * 100f) / goal).roundToInt()}%", color = superhumanTextMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(superhumanSurfaceSoft, CircleShape)) {
            if (goal != null && goal > 0 && amount > 0) {
                Box(Modifier.fillMaxWidth((amount.toFloat() / goal).coerceIn(0f, 1f))
                    .height(5.dp).background(accent, CircleShape))
            }
        }
    }
}

@Composable
internal fun MidnightTrainingCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightBlue)
            .padding(horizontal = 19.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        MidnightHeader("Training", "Move with purpose", midnightBlue, R.drawable.tabler_barbell) {
            MidnightAction(R.drawable.tabler_chevron_right)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text("${snapshot.workoutsToday}", color = superhumanTextPrimary, fontSize = 30.sp,
                    lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(7.dp))
                Text(if (snapshot.workoutsToday == 1) "session" else "sessions",
                    color = superhumanTextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("${snapshot.trainingMinutesToday} min today", color = superhumanTextMuted, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MidnightTrainingSegment(R.drawable.tabler_dumbbell, "${snapshot.strengthWorkoutsToday}", "strength", Modifier.weight(1f))
            MidnightTrainingSegment(R.drawable.tabler_run, "${snapshot.cardioWorkoutsToday}", "cardio", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MidnightTrainingSegment(icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = midnightBlue, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(value, color = superhumanTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, color = superhumanTextMuted, fontSize = 11.sp)
    }
}

@Composable
internal fun MidnightSleepCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val minutes = snapshot.sleepMinutes
    val score = snapshot.sleepScore?.coerceIn(0, 100)
    val sleepTime = minutes?.let { "%d:%02d".format(Locale.US, it / 60, it % 60) } ?: "—"
    val scoreLabel = when {
        score == null -> "NO SCORE"
        score >= 85 -> "EXCELLENT"
        score >= 70 -> "GOOD"
        score >= 55 -> "FAIR"
        else -> "LOW"
    }
    val stageParts = listOf(
        snapshot.sleepAwakeMinutes?.coerceAtLeast(0) ?: 0,
        snapshot.sleepRemMinutes?.coerceAtLeast(0) ?: 0,
        snapshot.sleepLightMinutes?.coerceAtLeast(0) ?: 0,
        snapshot.sleepDeepMinutes?.coerceAtLeast(0) ?: 0
    )
    val stageTotal = stageParts.sum()
    val stageColors = listOf(Color(0xFFD8A0AD), Color(0xFFC5A6EC),
        Color(0xFF9270CE), Color(0xFF5A418E))
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightPurple)
            .padding(horizontal = 21.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.tabler_moon), null,
                tint = midnightPurple, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(11.dp))
            Text("Sleep", color = superhumanTextPrimary, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.tabler_chevron_right), null,
                tint = superhumanTextMuted, modifier = Modifier.size(18.dp))
        }
        // Never constrain the metrics row to a fixed height: font scaling can
        // otherwise clip the supporting caption against the divider.
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("TOTAL SLEEP", color = superhumanTextMuted,
                    fontSize = 10.sp, letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(sleepTime, color = superhumanTextPrimary,
                    fontSize = 48.sp, lineHeight = 54.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, maxLines = 1)
                Text(
                    if (minutes == null) "No sleep recorded"
                    else "Recorded duration",
                    color = superhumanTextMuted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                val scoreProgress = (score ?: 0) / 100f
                Canvas(Modifier.size(99.dp)) {
                    val strokeWidth = 7.dp.toPx()
                    val inset = strokeWidth / 2f + 1.dp.toPx()
                    val arcBounds = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val arcOffset = Offset(inset, inset)
                    drawArc(superhumanSurfaceSoft, -90f, 360f, false,
                        topLeft = arcOffset, size = arcBounds,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round))
                    if (score != null && scoreProgress > 0f) {
                        drawArc(midnightPurple, -90f, 360f * scoreProgress, false,
                            topLeft = arcOffset, size = arcBounds,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score?.toString() ?: "—", color = superhumanTextPrimary,
                        fontSize = 38.sp, lineHeight = 43.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text(scoreLabel, color = if (score == null) superhumanTextMuted else midnightPurple,
                        fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(superhumanSurfaceSoft))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SLEEP COMPOSITION", color = superhumanTextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
                Text(if (stageTotal > 0) "${stageParts.count { it > 0 }} stages" else "Stages unavailable",
                    color = superhumanTextMuted, fontSize = 10.sp)
            }
            if (stageTotal > 0) {
                Row(Modifier.fillMaxWidth().height(9.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    stageParts.forEachIndexed { index, stageMinutes ->
                        if (stageMinutes > 0) {
                            Box(Modifier.weight(stageMinutes.toFloat()).height(9.dp)
                                .background(stageColors[index], RoundedCornerShape(4.dp)))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(9.dp)
                    .background(superhumanSurfaceSoft, RoundedCornerShape(4.dp)))
            }
        }
    }
}
