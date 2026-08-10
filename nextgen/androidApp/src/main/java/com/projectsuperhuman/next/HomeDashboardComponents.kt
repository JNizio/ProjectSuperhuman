package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Project Superhuman legacy-home visual vocabulary.
 * Keep imagery/gradients/card treatment here instead of scattering them through feature logic.
 * Legacy assets are intentionally loaded from app/src/main/assets (wired in androidApp/build.gradle.kts).
 */
private val HomeNavy = Color(0xFF123D70)
private val HomeBlue = Color(0xFF0D6CB4)
private val HomeCyan = Color(0xFF20A7C4)
private val HomeInk = Color(0xFF16334E)
private val HomeMuted = Color(0xFF748294)
private val HomeGreen = Color(0xFF5CB79E)
private val HomeRed = Color(0xFFD96767)
private val HomePurple = Color(0xFF7260BF)
private val HomeCard = Color(0xFFFCFDFE)
private val HomeBorder = Color(0xFFE3EAF0)

@Composable
private fun LegacyAssetImage(
    assetName: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    val bitmap = remember(assetName) {
        runCatching { context.assets.open(assetName).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = contentScale, alpha = alpha)
    }
}

@Composable
internal fun LegacyHomeHero(snapshot: NativeHomeSnapshot) {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase()
    val hydrationPct = ((snapshot.waterLitres / 3.6) * 100).roundToInt().coerceIn(0, 100)
    val target = when {
        hydrationPct < 50 -> "Today’s clearest live\ntarget"
        snapshot.sleepMinutes == null -> "Connect last night’s\nsleep"
        snapshot.workoutsToday == 0 -> "Ready for today’s\ntraining"
        else -> "Keep the day\nmoving"
    }
    val guidance = when {
        hydrationPct < 50 -> "Hydration is at $hydrationPct%. Small, regular top-ups will move this first."
        snapshot.sleepMinutes == null -> "Sleep data is missing. Sync your wearable to complete today’s recovery picture."
        snapshot.workoutsToday == 0 -> "Recovery data looks ready. Training is the clearest next performance signal."
        else -> "Your core signals are moving. Keep logging the things that change today."
    }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF0965A7), Color(0xFF13A7C3)))
        )
    ) {
        // Quiet concentric geometry mirrors the legacy hero without turning it into a flat gradient.
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width * .88f, size.height * .26f)
            drawCircle(Color.White.copy(alpha = .055f), radius = size.width * .42f, center = centre, style = Stroke(width = 1.5f))
            drawCircle(Color.White.copy(alpha = .045f), radius = size.width * .31f, center = centre, style = Stroke(width = 1.5f))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 19.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY • $today", color = Color.White.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Box(Modifier.width(42.dp).height(42.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("PS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(target, color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .19f))) {
                Box(Modifier.fillMaxWidth((hydrationPct / 100f).coerceAtLeast(.01f)).height(5.dp).background(Color.White))
            }
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroSignal("HYDRATION", "$hydrationPct%")
                HeroSignal("SLEEP", snapshot.sleepMinutes?.let(::formatMinutesHome) ?: "—")
                HeroSignal("TRAINING", if (snapshot.workoutsToday > 0) "Done" else "Ready")
                HeroSignal("CLINICAL", "${snapshot.clinicalMarkers} markers")
            }
            Spacer(Modifier.height(17.dp))
            Text(guidance, color = Color.White.copy(alpha = .89f), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun HeroSignal(label: String, value: String) {
    Column(Modifier.width(72.dp)) {
        Text(label, color = Color.White.copy(alpha = .62f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .75.sp, maxLines = 1)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
internal fun LegacyHydrationCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val ml = (snapshot.waterLitres * 1000).roundToInt()
    val pct = ((snapshot.waterLitres / 3.6) * 100).roundToInt().coerceIn(0, 100)
    Box(
        Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(25.dp)).background(HomeCard)
            .border(1.dp, HomeBorder, RoundedCornerShape(25.dp)).clickable(onClick = onClick)
    ) {
        LegacyAssetImage("dashboard_water.png", Modifier.width(170.dp).fillMaxSize().align(Alignment.CenterEnd), alpha = .88f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(HomeCard, HomeCard.copy(alpha = .96f), Color.Transparent))))
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(102.dp)) {
                Text("HYDRATION", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(82.dp).height(82.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0xFFDDECF3), style = Stroke(width = 9.dp.toPx()))
                        if (pct > 0) {
                            drawArc(HomeBlue, -90f, pct * 3.6f, false, style = Stroke(width = 9.dp.toPx()))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pct%", color = HomeNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("today", color = HomeMuted, fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("$ml ml", color = HomeNavy, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("3,600 ml daily target", color = HomeMuted, fontSize = 10.sp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.background(Color(0xFFE5F3FA), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text("Open tracker", color = HomeBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
internal fun LegacyClinicalCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val normal = (snapshot.clinicalMarkers - snapshot.clinicalAlerts).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(HomeCard)
            .border(1.dp, HomeBorder, RoundedCornerShape(25.dp)).clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CLINICAL", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("→", color = Color(0xFF8CA6B5), fontSize = 23.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(13.dp).height(13.dp).background(if (snapshot.clinicalAlerts > 0) HomeRed else HomeGreen, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${snapshot.clinicalMarkers} markers", color = HomeNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            repeat(20) { index ->
                val isAlert = snapshot.clinicalMarkers > 0 && index < snapshot.clinicalAlerts.coerceAtMost(6)
                val isHealthy = snapshot.clinicalMarkers > 0 && !isAlert && index % 5 == 0
                val tone = when { isAlert -> HomeRed; isHealthy -> HomeGreen; else -> Color(0xFFC6D4DE) }
                Box(Modifier.weight(1f).height(if (isAlert || isHealthy) 20.dp else 10.dp).background(tone, RoundedCornerShape(7.dp)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("$normal normal · ${snapshot.clinicalAlerts} outside range", color = HomeMuted, fontSize = 10.sp)
    }
}

@Composable
internal fun LegacyTrainingCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(153.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFFFFF8F3))
            .border(1.dp, Color(0xFFE9E5DF), RoundedCornerShape(25.dp)).clickable(onClick = onClick)
    ) {
        LegacyAssetImage("dashboard_training.png", Modifier.width(225.dp).fillMaxSize().align(Alignment.CenterEnd), alpha = .8f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFFF8F3), Color(0xFFFFF8F3).copy(alpha = .94f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TRAINING", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Text("→", color = Color(0xFF8CA6B5), fontSize = 23.sp)
            }
            Text(if (snapshot.workoutsToday > 0) "Training logged" else "Ready when you are", color = Color(0xFF6C4132), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(if (snapshot.workoutsToday > 0) "${snapshot.workoutsToday} workout today" else "No workout logged today", color = HomeMuted, fontSize = 10.sp)
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(29.dp)) {
                TrainingStat(snapshot.workoutsToday.toString(), "workouts")
                TrainingStat(snapshot.workoutSetsToday.toString(), "sets")
                TrainingStat(snapshot.workoutVolumeToday.toString(), "volume")
            }
        }
    }
}

@Composable
private fun TrainingStat(value: String, label: String) {
    Column {
        Text(value, color = Color(0xFF704431), fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(label, color = HomeMuted, fontSize = 9.sp)
    }
}

@Composable
internal fun LegacyBodyCard(snapshot: NativeHomeSnapshot, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(148.dp).clip(RoundedCornerShape(23.dp)).background(HomeCard)
            .border(1.dp, HomeBorder, RoundedCornerShape(23.dp)).clickable(onClick = onClick).padding(15.dp)
    ) {
        LegacyCardHeader("BODY")
        Spacer(Modifier.height(8.dp))
        Text(snapshot.bodyWeightKg?.let { "%.1f kg".format(it) } ?: "—", color = Color(0xFF443A79), fontSize = 20.sp, fontWeight = FontWeight.Black)
        val change = snapshot.bodyWeightChange30d
        Text(change?.let { "${if (it > 0) "+" else ""}${"%.1f".format(it)} kg · 30d" } ?: "Progress & measurements", color = HomeMuted, fontSize = 8.sp)
        Spacer(Modifier.height(8.dp))
        BodySparkline(snapshot.bodyWeightTrend)
    }
}

@Composable
private fun BodySparkline(values: List<Double>) {
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        if (values.size < 2) {
            drawLine(Color(0xFFD7D0F0), Offset(0f, size.height * .65f), Offset(size.width, size.height * .65f), strokeWidth = 3f)
            return@Canvas
        }
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val range = (max - min).coerceAtLeast(.2)
        val path = Path()
        values.forEachIndexed { i, value ->
            val x = i.toFloat() / (values.size - 1) * size.width
            val y = size.height - (((value - min) / range).toFloat() * size.height * .78f) - size.height * .08f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, HomePurple, style = Stroke(width = 4f))
        val last = values.last()
        val lastY = size.height - (((last - min) / range).toFloat() * size.height * .78f) - size.height * .08f
        drawCircle(Color.White, 7f, Offset(size.width - 2f, lastY))
        drawCircle(HomePurple, 7f, Offset(size.width - 2f, lastY), style = Stroke(width = 3f))
    }
}

@Composable
internal fun LegacySleepCard(snapshot: NativeHomeSnapshot, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(148.dp).clip(RoundedCornerShape(23.dp)).background(HomeCard)
            .border(1.dp, HomeBorder, RoundedCornerShape(23.dp)).clickable(onClick = onClick)
    ) {
        LegacyAssetImage("dashboard_sleep.png", Modifier.width(115.dp).fillMaxSize().align(Alignment.CenterEnd), alpha = .62f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(HomeCard, HomeCard.copy(alpha = .93f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(15.dp)) {
            LegacyCardHeader("SLEEP")
            Spacer(Modifier.height(8.dp))
            Text(snapshot.sleepMinutes?.let(::formatMinutesHome) ?: "—", color = HomeNavy, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFD7E2F4))) {
                val progress = ((snapshot.sleepMinutes ?: 0) / 480f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth(progress.coerceAtLeast(.02f)).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF5C84DE), Color(0xFF7C9EE9)))))
            }
            Spacer(Modifier.height(6.dp))
            Text(snapshot.sleepScore?.let { "Sleep score $it · 8.0 h target" } ?: "Sync sleep data", color = HomeMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun LegacyCardHeader(label: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text("→", color = Color(0xFF9AAEBB), fontSize = 18.sp)
    }
}

@Composable
internal fun LegacyNutritionCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(24.dp)).background(HomeCard)
            .border(1.dp, HomeBorder, RoundedCornerShape(24.dp)).clickable(onClick = onClick)
    ) {
        LegacyAssetImage("dashboard_nutrition.png", Modifier.width(235.dp).fillMaxSize().align(Alignment.CenterEnd), alpha = .72f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(HomeCard, HomeCard.copy(alpha = .96f), HomeCard.copy(alpha = .35f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("NUTRITION", color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("→", color = Color(0xFF8CA6B5), fontSize = 19.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text("${snapshot.caloriesToday} kcal", color = HomeNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text(if (snapshot.caloriesToday > 0) "${snapshot.proteinToday} g protein logged today" else "Estimated daily energy target", color = HomeMuted, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NutritionChip("MACRO")
                NutritionChip("MICRO")
                NutritionChip("COMPOUNDS")
            }
        }
    }
}

@Composable
private fun NutritionChip(label: String) {
    Box(Modifier.background(Color(0xFFE9F3F7).copy(alpha = .9f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, color = HomeNavy, fontSize = 7.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun LegacyHomeLinks(openMindfulness: () -> Unit, openExercise: () -> Unit, openInsights: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeLinkCard("Mindfulness →", "Breathing and meditation tools.", Modifier.weight(1f), openMindfulness)
        HomeLinkCard("Training progress →", "PRs, volume and weekly muscle work.", Modifier.weight(1f), openExercise)
    }
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth()) {
        HomeLinkCard("Superhuman Insights →", "See how sleep and body trends interact.", Modifier.fillMaxWidth(.52f), openInsights)
    }
}

@Composable
private fun HomeLinkCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)) {
        Text(title, color = HomeInk, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = HomeMuted, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
internal fun LegacyBloodPressureLink(onClick: () -> Unit) {
    Text(
        "Blood pressure tools",
        color = HomeMuted,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}
