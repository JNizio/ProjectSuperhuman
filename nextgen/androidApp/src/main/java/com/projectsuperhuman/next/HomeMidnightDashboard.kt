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
    title: String, subtitle: String, accent: Color, icon: Int?, symbol: String,
    trailing: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).background(accent.copy(alpha = .17f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(25.dp))
            } else Text(symbol, color = accent, fontSize = 28.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = superhumanTextPrimary, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = superhumanTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        trailing()
    }
}

@Composable
private fun MidnightAction(icon: Int) {
    Box(
        Modifier.size(38.dp).background(superhumanSurfaceSoft, CircleShape)
            .border(1.dp, superhumanBorder.copy(alpha = .44f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, tint = superhumanTextMuted, modifier = Modifier.size(21.dp))
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
            .padding(horizontal = 18.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MidnightHeader("Nutrition", "Fuel your day", midnightGreen, null, "❧") {
            MidnightAction(R.drawable.tabler_plus)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (snapshot.nutritionCaloriesComplete) "$calories" else "~$calories",
                        color = superhumanTextPrimary, fontSize = 38.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("kcal", color = superhumanTextMuted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(if (goal != null) "of " + "%,d".format(Locale.US, goal) else "Logged today",
                    color = superhumanTextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(superhumanSurfaceSoft, CircleShape)) {
                    if (goal != null && progress > 0f) {
                        Box(Modifier.fillMaxWidth(progress).height(6.dp).background(midnightGreen, CircleShape))
                    }
                }
                Text(if (goal != null) "${(progress * 100).roundToInt()}% of daily goal" else "No calorie goal set",
                    color = superhumanTextMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(18.dp))
            MidnightNutritionRing(calories, goal, protein, carbs, fat)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            MidnightMacro("Protein", protein, snapshot.proteinGoal, midnightGreen, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(54.dp).background(superhumanDivider))
            MidnightMacro("Carbs", carbs, snapshot.carbsGoal, midnightBlue, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(54.dp).background(superhumanDivider))
            MidnightMacro("Fat", fat, snapshot.fatGoal, midnightOrange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MidnightMacro(name: String, amount: Int, goal: Int?, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(name, color = superhumanTextMuted, fontSize = 11.sp)
        }
        Text("$amount g", color = superhumanTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(goal?.takeIf { it > 0 }?.let { "/ $it g" } ?: "Logged", color = superhumanTextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun MidnightNutritionRing(calories: Int, goal: Int?, protein: Int, carbs: Int, fat: Int) {
    val energy = listOf(protein * 4f, carbs * 4f, fat * 9f)
    val total = energy.sum()
    val denominator = (goal?.toFloat() ?: total).coerceAtLeast(1f)
    val scaling = if (total > denominator) denominator / total else 1f
    val proportions = energy.map { (it * scaling / denominator).coerceIn(0f, 1f) }
    val accents = listOf(midnightGreen, midnightBlue, midnightOrange)
    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(108.dp)) {
            val width = 8.dp.toPx()
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$calories", color = superhumanTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("kcal", color = superhumanTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun MidnightTrainingCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightBlue)
            .padding(horizontal = 18.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MidnightHeader("Training", "Move with purpose", midnightBlue, R.drawable.tabler_barbell, "") {
            MidnightAction(R.drawable.tabler_chevron_right)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${snapshot.workoutsToday}", color = superhumanTextPrimary, fontSize = 39.sp,
                        lineHeight = 43.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(if (snapshot.workoutsToday == 1) "session" else "sessions",
                        color = superhumanTextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text("${snapshot.trainingMinutesToday} minutes today", color = superhumanTextMuted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("TODAY", color = superhumanTextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    val count = snapshot.workoutsToday.coerceIn(0, 5)
                    repeat(5) { index ->
                        Box(Modifier.width(9.dp).height((17 + index * 6).dp)
                            .background(if (index < count) midnightBlue else superhumanSurfaceSoft, RoundedCornerShape(5.dp)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MidnightTrainingSegment(R.drawable.tabler_dumbbell, "${snapshot.strengthWorkoutsToday}", "strength", Modifier.weight(1f))
            MidnightTrainingSegment(R.drawable.tabler_run, "${snapshot.cardioWorkoutsToday}", "cardio", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MidnightTrainingSegment(icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.background(superhumanSurfaceSoft.copy(alpha = .75f), RoundedCornerShape(15.dp))
            .border(1.dp, superhumanBorder.copy(alpha = .55f), RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = midnightBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(value, color = superhumanTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(5.dp))
        Text(label, color = superhumanTextMuted, fontSize = 11.sp)
    }
}

@Composable
internal fun MidnightSleepCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val minutes = snapshot.sleepMinutes
    val score = snapshot.sleepScore
    val delta = minutes?.minus(480)
    val reference = minutes?.let { (it / 480f).coerceIn(0f, 1f) } ?: 0f
    val status = when {
        minutes == null -> "No sleep data"
        score == null -> "Sleep recorded"
        score >= 85 -> "Strong recovery"
        score >= 70 -> "Solid recovery"
        score >= 55 -> "Mixed recovery"
        else -> "Low recovery"
    }
    val detail = when {
        minutes == null -> "Sync your wearable to see your nightly insights."
        delta != null && delta < 0 -> "${formatMinutesHome(abs(delta))} below your 8-hour reference."
        delta != null && delta > 0 -> "${formatMinutesHome(delta)} above your 8-hour reference."
        else -> "Reached your 8-hour reference."
    }
    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = midnightPurple)
            .padding(horizontal = 18.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MidnightHeader("Sleep", "Rest and recover", midnightPurple, null, "☾") {
            if (score != null) {
                Box(Modifier.background(midnightPurple.copy(alpha = .12f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("Score $score", color = midnightPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else MidnightAction(R.drawable.tabler_chevron_right)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(minutes?.let(::formatMinutesHome) ?: "No reading", color = superhumanTextPrimary,
                    fontSize = if (minutes != null) 33.sp else 22.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
                Text("Last night", color = superhumanTextMuted, fontSize = 13.sp)
            }
            Column(Modifier.weight(.9f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("8-HOUR REFERENCE", color = superhumanTextMuted, fontSize = 10.sp, letterSpacing = .7.sp)
                Box(Modifier.fillMaxWidth().height(7.dp).background(superhumanSurfaceSoft, CircleShape)) {
                    if (reference > 0f) {
                        Box(Modifier.fillMaxWidth(reference).height(7.dp).background(midnightPurple, CircleShape))
                    }
                }
                val span = listOfNotNull(snapshot.sleepStartLabel, snapshot.sleepEndLabel).joinToString(" – ")
                Text(if (span.isNotEmpty()) span else "${(reference * 100).roundToInt()}% of reference",
                    color = superhumanTextMuted, fontSize = 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(midnightPurple.copy(alpha = .13f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Text("☾", color = midnightPurple, fontSize = 23.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(status, color = midnightPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = superhumanTextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}
