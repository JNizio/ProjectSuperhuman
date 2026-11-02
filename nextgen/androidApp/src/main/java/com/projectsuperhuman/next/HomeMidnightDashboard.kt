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
    val progress = goal?.let { (calories.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val protein = snapshot.proteinToday.coerceAtLeast(0)
    val carbs = snapshot.carbsToday.coerceAtLeast(0)
    val fat = snapshot.fatToday.coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightGreen)
            .padding(horizontal = 19.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        MidnightHeader("Nutrition", "Fuel your day", midnightGreen, R.drawable.tabler_leaf) {
            MidnightAction(R.drawable.tabler_plus)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (snapshot.nutritionCaloriesComplete) "$calories" else "~$calories",
                        color = superhumanTextPrimary, fontSize = 31.sp, lineHeight = 35.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(7.dp))
                    Text("kcal", color = superhumanTextMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(if (goal != null) "of " + "%,d".format(Locale.US, goal) else "Logged today",
                    color = superhumanTextMuted, fontSize = 11.sp, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(5.dp).background(superhumanSurfaceSoft, CircleShape)) {
                    if (goal != null && progress > 0f)
                        Box(Modifier.fillMaxWidth(progress).height(5.dp).background(midnightGreen, CircleShape))
                }
                Text(if (goal != null) "${(progress * 100).roundToInt()}% of goal" else "No goal set",
                    color = superhumanTextMuted, fontSize = 10.sp)
            }
            if (calories > 0 || protein + carbs + fat > 0) {
                Spacer(Modifier.width(15.dp))
                MidnightNutritionRing(goal, protein, carbs, fat)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MidnightMacro("Protein", protein, snapshot.proteinGoal, midnightGreen, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(38.dp).background(superhumanDivider))
            MidnightMacro("Carbs", carbs, snapshot.carbsGoal, midnightBlue, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(38.dp).background(superhumanDivider))
            MidnightMacro("Fat", fat, snapshot.fatGoal, midnightOrange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MidnightMacro(name: String, amount: Int, goal: Int?, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(name, color = superhumanTextMuted, fontSize = 10.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$amount g", color = superhumanTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (goal != null && goal > 0) {
                Spacer(Modifier.width(3.dp))
                Text("/$goal", color = superhumanTextMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MidnightNutritionRing(goal: Int?, protein: Int, carbs: Int, fat: Int) {
    val energy = listOf(protein * 4f, carbs * 4f, fat * 9f)
    val total = energy.sum()
    val denominator = (goal?.toFloat() ?: total).coerceAtLeast(1f)
    val scaling = if (total > denominator) denominator / total else 1f
    val proportions = energy.map { (it * scaling / denominator).coerceIn(0f, 1f) }
    val accents = listOf(midnightGreen, midnightBlue, midnightOrange)
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(78.dp)) {
            val width = 6.dp.toPx()
            val bounds = Size(size.width - width * 1.8f, size.height - width * 1.8f)
            val offset = Offset(width * .9f, width * .9f)
            drawArc(superhumanSurfaceSoft, -90f, 360f, false, topLeft = offset, size = bounds,
                style = Stroke(width, cap = StrokeCap.Round))
            var angle = -90f
            proportions.forEachIndexed { index, value ->
                val sweep = value * 360f
                if (sweep > 1f) {
                    drawArc(accents[index], angle + 1.5f, (sweep - 3f).coerceAtLeast(.1f), false,
                        topLeft = offset, size = bounds, style = Stroke(width, cap = StrokeCap.Round))
                }
                angle += sweep
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
    val score = snapshot.sleepScore
    val status = when {
        minutes == null -> "No sleep data"
        score == null -> "Sleep recorded"
        score >= 85 -> "Strong recovery"
        score >= 70 -> "Solid recovery"
        score >= 55 -> "Mixed recovery"
        else -> "Low recovery"
    }
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightPurple)
            .padding(horizontal = 19.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        MidnightHeader("Sleep", "Rest and recover", midnightPurple, R.drawable.tabler_moon) {
            if (score != null) Text("Score $score", color = midnightPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else MidnightAction(R.drawable.tabler_chevron_right)
        }
        if (snapshot.sleepStages.isNotEmpty() &&
            snapshot.sleepStageStartMs != null && snapshot.sleepStageEndMs != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(.37f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(minutes?.let(::formatMinutesHome) ?: "—", color = superhumanTextPrimary,
                        fontSize = 27.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Last night", color = superhumanTextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(status, color = midnightPurple, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium, maxLines = 2)
                }
                HomeSleepCyclePlot(
                    segments = snapshot.sleepStages,
                    startMs = snapshot.sleepStageStartMs,
                    endMs = snapshot.sleepStageEndMs,
                    modifier = Modifier.weight(.63f)
                )
            }
            val totalStages = listOfNotNull(
                snapshot.sleepAwakeMinutes, snapshot.sleepRemMinutes,
                snapshot.sleepLightMinutes, snapshot.sleepDeepMinutes
            ).sum()
            if (totalStages > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        Triple("Awake", snapshot.sleepAwakeMinutes, Color(0xFFE8A9A0)),
                        Triple("REM", snapshot.sleepRemMinutes, Color(0xFFC6A9EB)),
                        Triple("Light", snapshot.sleepLightMinutes, Color(0xFF9574D1)),
                        Triple("Deep", snapshot.sleepDeepMinutes, Color(0xFF7151AE))
                    ).forEach { (name, value, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(color, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("$name ${value?.let { "${(it * 100f / totalStages).roundToInt()}%" } ?: "—"}",
                                color = superhumanTextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(minutes?.let(::formatMinutesHome) ?: "No reading", color = superhumanTextPrimary,
                        fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                    Text("Last night", color = superhumanTextMuted, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(status, color = midnightPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sleep stage graph appears when stages are available",
                        color = superhumanTextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
