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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HomeNavy get() = superhumanBrandText
private val HomeBlue get() = superhumanBlue
private val HomeCyan get() = if (SuperhumanAppearance.darkMode) superhumanAccent else Color(0xFF20A7C4)
private val HomeInk get() = superhumanTextPrimary
private val HomeMuted get() = superhumanTextMuted
private val HomeGreen get() = superhumanGreen
private val HomeRed get() = superhumanRed
private val HomePurple get() = if (SuperhumanAppearance.darkMode) Color(0xFFA99BEA) else Color(0xFF7260BF)
private val HomeAmber get() = if (SuperhumanAppearance.darkMode) Color(0xFFFFB766) else Color(0xFFD98B2B)
private val HomeCard get() = superhumanSurfaceElevated
private val HomeBorder get() = superhumanBorder

@Composable
private fun LegacyAssetImage(assetName: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, alpha: Float = 1f) {
    val context = LocalContext.current
    val bitmap = remember(assetName) { runCatching { context.assets.open(assetName).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = contentScale, alpha = alpha)
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
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF0965A7), Color(0xFF13A7C3))))) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width * .88f, size.height * .26f)
            drawCircle(Color.White.copy(alpha = .055f), radius = size.width * .42f, center = centre, style = Stroke(width = 1.5f))
            drawCircle(Color.White.copy(alpha = .045f), radius = size.width * .31f, center = centre, style = Stroke(width = 1.5f))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 19.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY • $today", color = Color.White.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Box(Modifier.width(42.dp).height(42.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("PS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(9.dp))
            Text(target, color = Color.White, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .19f))) { Box(Modifier.fillMaxWidth((hydrationPct / 100f).coerceAtLeast(.01f)).height(5.dp).background(Color.White)) }
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
    val goalMl = snapshot.waterGoalMl.coerceIn(1500, 6000)
    val currentMl = (snapshot.waterLitres * 1000).roundToInt().coerceIn(0, goalMl)
    val remainingMl = (goalMl - currentMl).coerceAtLeast(0)
    val progress = (currentMl / goalMl.toFloat()).coerceIn(0f, 1f)
    val pct = (progress * 100).roundToInt()

    val accent = Color(0xFF6FD9FF)
    val deepBlue = Color(0xFF061825)
    val middleBlue = Color(0xFF092A3E)
    val waterBlue = Color(0xFF0D5269)

    fun hydrationAmountLabel(ml: Int): String {
        return if (ml >= 1000 && ml % 100 == 0) {
            String.format(Locale.US, "%.1f L", ml / 1000f)
        } else {
            "$ml ml"
        }
    }

    val goalLabel = hydrationAmountLabel(goalMl)
    val remainingLabel = hydrationAmountLabel(remainingMl)
    val status = when {
        currentMl == 0 -> "No water logged yet"
        progress >= 1f -> "Daily goal reached"
        else -> "$remainingLabel remaining"
    }

    Box(
        Modifier.fillMaxWidth()
            .height(188.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        deepBlue,
                        middleBlue,
                        waterBlue
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = .22f), RoundedCornerShape(29.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width * .87f, size.height * .38f)
            drawCircle(
                color = Color.White.copy(alpha = .035f),
                radius = size.width * .29f,
                center = centre,
                style = Stroke(width = 1.4f)
            )
            drawCircle(
                color = Color.White.copy(alpha = .025f),
                radius = size.width * .21f,
                center = centre,
                style = Stroke(width = 1.2f)
            )
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        deepBlue,
                        deepBlue.copy(alpha = .98f),
                        middleBlue.copy(alpha = .90f),
                        waterBlue.copy(alpha = .52f),
                        Color.Transparent
                    )
                )
            )
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "HYDRATION",
                        color = Color.White.copy(alpha = .64f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.15.sp
                    )
                    Box(
                        Modifier.background(accent.copy(alpha = .12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "$pct%",
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        currentMl.toString(),
                        color = Color.White,
                        fontSize = 29.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ml",
                        color = Color.White.copy(alpha = .62f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    "of $goalLabel daily goal",
                    color = Color.White.copy(alpha = .54f),
                    fontSize = 9.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    status,
                    color = if (currentMl > 0) accent else Color.White.copy(alpha = .54f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HydrationMiniStat(
                        label = "GOAL",
                        value = goalLabel,
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    )
                    HydrationMiniStat(
                        label = "LEFT",
                        value = if (progress >= 1f) "0 ml" else remainingLabel,
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            HydrationDroplet(
                progress = progress,
                accent = accent,
                modifier = Modifier.width(92.dp).height(126.dp)
            )
        }
    }
}

@Composable
private fun HydrationMiniStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.height(45.dp)
            .background(Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = .15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = .42f),
            fontSize = 6.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .55.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun HydrationDroplet(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)

        val dropPath = Path().apply {
            moveTo(size.width * .50f, 0f)
            cubicTo(
                size.width * .30f, size.height * .20f,
                size.width * .10f, size.height * .43f,
                size.width * .10f, size.height * .66f
            )
            cubicTo(
                size.width * .10f, size.height * .88f,
                size.width * .27f, size.height,
                size.width * .50f, size.height
            )
            cubicTo(
                size.width * .73f, size.height,
                size.width * .90f, size.height * .88f,
                size.width * .90f, size.height * .66f
            )
            cubicTo(
                size.width * .90f, size.height * .43f,
                size.width * .70f, size.height * .20f,
                size.width * .50f, 0f
            )
            close()
        }

        drawPath(
            path = dropPath,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = .09f),
                    Color.White.copy(alpha = .025f)
                )
            )
        )

        clipPath(dropPath) {
            val fillTop = size.height * (1f - p)

            if (p > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF8CE8FF),
                            accent,
                            Color(0xFF159FCB)
                        ),
                        startY = fillTop,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, fillTop)
                )

                drawLine(
                    color = Color.White.copy(alpha = .34f),
                    start = Offset(size.width * .16f, fillTop),
                    end = Offset(size.width * .84f, fillTop),
                    strokeWidth = 2.5f
                )

                if (p > .18f) {
                    drawCircle(
                        color = Color.White.copy(alpha = .15f),
                        radius = size.width * .045f,
                        center = Offset(size.width * .37f, size.height * .73f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = .10f),
                        radius = size.width * .032f,
                        center = Offset(size.width * .61f, size.height * .58f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = .08f),
                        radius = size.width * .023f,
                        center = Offset(size.width * .48f, size.height * .43f)
                    )
                }
            }
        }

        drawPath(
            path = dropPath,
            color = Color.White.copy(alpha = .19f),
            style = Stroke(width = 2.2f)
        )

        val highlight = Path().apply {
            moveTo(size.width * .38f, size.height * .20f)
            quadraticTo(
                size.width * .27f,
                size.height * .34f,
                size.width * .29f,
                size.height * .48f
            )
        }
        drawPath(
            path = highlight,
            color = Color.White.copy(alpha = .22f),
            style = Stroke(width = 3f)
        )
    }
}

@Composable
internal fun LegacyClinicalCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val hasMarkers = snapshot.clinicalMarkers > 0
    val hasAlerts = snapshot.clinicalAlerts > 0
    val accent = when {
        hasAlerts -> HomeRed
        hasMarkers -> HomeGreen
        else -> HomePurple
    }
    val headline = when {
        hasAlerts -> "${snapshot.clinicalAlerts} marker${if (snapshot.clinicalAlerts == 1) "" else "s"} flagged"
        hasMarkers -> "${snapshot.clinicalMarkers} markers tracked"
        else -> "Build your clinical picture"
    }
    val supporting = when {
        hasAlerts -> "Outside recorded reference range · review in Clinical"
        hasMarkers -> "No markers currently flagged outside recorded ranges"
        else -> "Import lab results or add your clinical context"
    }
    val cardGradient = if (SuperhumanAppearance.darkMode) {
        listOf(superhumanSurfaceElevated, Color(0xFF171C2A), Color(0xFF211D32))
    } else {
        listOf(Color(0xFFFCFDFE), Color(0xFFFAF9FF), Color(0xFFF7F5FF))
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(Brush.horizontalGradient(cardGradient))
            .border(1.dp, HomePurple.copy(alpha = if (SuperhumanAppearance.darkMode) .26f else .16f), RoundedCornerShape(25.dp))
            .superhumanHomeTileClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(8.dp).height(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("CLINICAL", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            }
            Box(
                Modifier.width(34.dp).height(34.dp).background(HomePurple.copy(alpha = .10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("→", color = HomePurple, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(headline, color = HomeNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(supporting, color = HomeMuted, fontSize = 9.sp, lineHeight = 13.sp)

        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClinicalHomeStat(snapshot.clinicalMarkers.toString(), "MARKERS", HomePurple, Modifier.weight(1f))
            ClinicalHomeStat(snapshot.clinicalAlerts.toString(), "FLAGGED", if (hasAlerts) HomeRed else HomeGreen, Modifier.weight(1f))
            Box(
                Modifier.weight(1.18f).height(47.dp).background(HomePurple.copy(alpha = .09f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (hasAlerts) "Review  →" else "Overview  →", color = HomePurple, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ClinicalHomeStat(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.height(47.dp)
            .background(superhumanSurface.copy(alpha = .90f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = .16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = HomeMuted, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
        Spacer(Modifier.height(1.dp))
        Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun LegacyTrainingCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val trained = snapshot.workoutsToday > 0
    val strengthAccent = Color(0xFF67B8FF)
    val cardioAccent = Color(0xFF66D7B7)
    val volumeLabel = if (snapshot.workoutVolumeToday >= 1000) {
        String.format(Locale.US, "%.1fk kg", snapshot.workoutVolumeToday / 1000.0)
    } else {
        "${snapshot.workoutVolumeToday} kg"
    }
    val distanceLabel = if (snapshot.cardioDistanceTodayKm >= 10.0) {
        String.format(Locale.US, "%.1f km", snapshot.cardioDistanceTodayKm)
    } else {
        String.format(Locale.US, "%.2f km", snapshot.cardioDistanceTodayKm)
    }
    val headline = if (trained) {
        "${snapshot.workoutsToday} session${if (snapshot.workoutsToday == 1) "" else "s"} today"
    } else {
        "Ready to train"
    }
    val summaryLine = if (trained) {
        buildList {
            if (snapshot.strengthWorkoutsToday > 0) add("${snapshot.strengthWorkoutsToday} strength")
            if (snapshot.cardioWorkoutsToday > 0) add("${snapshot.cardioWorkoutsToday} cardio")
            if (snapshot.trainingMinutesToday > 0) add("${snapshot.trainingMinutesToday} min")
        }.joinToString(" · ")
    } else {
        "Strength and cardio"
    }

    Box(
        Modifier.fillMaxWidth()
            .height(205.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF061829),
                        Color(0xFF092B43),
                        Color(0xFF0C4B58)
                    )
                )
            )
            .border(1.dp, Color(0xFF2D6F83).copy(alpha = .62f), RoundedCornerShape(29.dp))
            .clickable(onClick = onClick)
    ) {
        LegacyAssetImage(
            "dashboard_training.png",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = .21f
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF061829),
                        Color(0xFF061829).copy(alpha = .97f),
                        Color(0xFF08283D).copy(alpha = .83f),
                        Color(0xFF0A4D57).copy(alpha = .40f),
                        Color.Transparent
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF061829).copy(alpha = .18f),
                        Color(0xFF061829).copy(alpha = .76f)
                    )
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp)
                            .background(strengthAccent.copy(alpha = .14f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.tabler_dumbbell),
                            contentDescription = null,
                            tint = strengthAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "TRAINING",
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp
                    )
                }
                Box(
                    Modifier.size(36.dp).background(Color.White.copy(alpha = .08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", color = strengthAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(13.dp))
            Text(
                headline,
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(3.dp))
            Text(
                summaryLine,
                color = Color.White.copy(alpha = .60f),
                fontSize = 9.sp,
                maxLines = 1
            )

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Column(
                    Modifier.weight(1f)
                        .background(strengthAccent.copy(alpha = .10f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                ) {
                    Text("STRENGTH", color = strengthAccent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (snapshot.strengthWorkoutsToday > 0) volumeLabel else "Ready",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(
                    Modifier.weight(1f)
                        .background(cardioAccent.copy(alpha = .10f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                ) {
                    Text("CARDIO", color = cardioAccent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (snapshot.cardioWorkoutsToday > 0) distanceLabel else "Ready",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(
                    Modifier.weight(.82f)
                        .background(Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text("TIME", color = Color.White.copy(alpha = .48f), fontSize = 7.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (snapshot.trainingMinutesToday > 0) snapshot.trainingMinutesToday.toString() + " min" else "—",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
internal fun LegacyNutritionCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val nutritionGradient = if (SuperhumanAppearance.darkMode) {
        listOf(Color(0xFF06151F), Color(0xFF0A222B), Color(0xFF103238))
    } else {
        listOf(Color(0xFFF8FBFA), Color(0xFFF0F7F4), Color(0xFFE8F2EE))
    }
    val accent = if (SuperhumanAppearance.darkMode) Color(0xFF70D9C7) else Color(0xFF167C6B)
    val carbsAccent = if (SuperhumanAppearance.darkMode) Color(0xFF69B8E9) else Color(0xFF2A79A9)
    val track = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .09f) else HomeBorder
    val calorieProgress = snapshot.calorieGoal
        ?.takeIf { it > 0 }
        ?.let { (snapshot.caloriesToday.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
        ?: 0f

    Box(
        Modifier.fillMaxWidth()
            .height(192.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(nutritionGradient))
            .border(
                1.dp,
                accent.copy(alpha = if (SuperhumanAppearance.darkMode) .22f else .13f),
                RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onClick)
    ) {
        LegacyAssetImage(
            "dashboard_nutrition.png",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (SuperhumanAppearance.darkMode) .045f else .09f
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        nutritionGradient.first(),
                        nutritionGradient.first().copy(alpha = .995f),
                        nutritionGradient[1].copy(alpha = .97f),
                        nutritionGradient.last().copy(alpha = .82f),
                        Color.Transparent
                    )
                )
            )
        )

        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NUTRITION",
                    color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .68f) else HomeMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp
                )
                Box(
                    Modifier.size(36.dp).background(accent.copy(alpha = .12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeNutritionCalorieRing(
                    snapshot = snapshot,
                    calories = snapshot.caloriesToday,
                    calorieGoal = snapshot.calorieGoal,
                    caloriesComplete = snapshot.nutritionCaloriesComplete,
                    progress = calorieProgress,
                    accent = accent,
                    proteinColor = accent,
                    carbsColor = carbsAccent,
                    fatColor = HomeAmber,
                    fibreColor = HomePurple,
                    track = track,
                    modifier = Modifier.size(112.dp)
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeNutritionMacroRow(
                        label = "Protein",
                        value = snapshot.proteinToday,
                        target = snapshot.proteinGoal,
                        accent = accent
                    )
                    HomeNutritionMacroRow(
                        label = "Carbs",
                        value = snapshot.carbsToday,
                        target = snapshot.carbsGoal,
                        accent = carbsAccent
                    )
                    HomeNutritionMacroRow(
                        label = "Fat",
                        value = snapshot.fatToday,
                        target = snapshot.fatGoal,
                        accent = HomeAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNutritionCalorieRing(
    snapshot: NativeHomeSnapshot,
    calories: Int,
    calorieGoal: Int?,
    caloriesComplete: Boolean,
    progress: Float,
    accent: Color,
    proteinColor: Color,
    carbsColor: Color,
    fatColor: Color,
    fibreColor: Color,
    track: Color,
    modifier: Modifier = Modifier
) {
    val remaining = calorieGoal?.let { (it - calories).coerceAtLeast(0) }
    val macroEnergy = listOf(
        snapshot.proteinToday * 4f,
        snapshot.carbsToday * 4f,
        snapshot.fatToday * 9f,
        snapshot.fibreToday * 2f
    )
    val macroColors = listOf(proteinColor, carbsColor, fatColor, fibreColor)
    val macroTotal = macroEnergy.sum().coerceAtLeast(0f)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val calorieStroke = 8.dp.toPx()
            val macroStroke = 4.dp.toPx()
            val calorieInset = 17.dp.toPx()
            val macroInset = 4.dp.toPx()

            val calorieSize = androidx.compose.ui.geometry.Size(
                size.width - calorieInset * 2f,
                size.height - calorieInset * 2f
            )
            val macroSize = androidx.compose.ui.geometry.Size(
                size.width - macroInset * 2f,
                size.height - macroInset * 2f
            )

            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(calorieInset, calorieInset),
                size = calorieSize,
                style = Stroke(width = calorieStroke, cap = StrokeCap.Round)
            )

            if (calorieGoal != null && progress > 0f) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(calorieInset, calorieInset),
                    size = calorieSize,
                    style = Stroke(width = calorieStroke, cap = StrokeCap.Round)
                )
            }

            if (macroTotal > 0f) {
                val gap = 5f
                val available = 360f - gap * 4f
                var startAngle = -90f

                macroEnergy.forEachIndexed { index, energy ->
                    val sweep = available * (energy / macroTotal)
                    if (sweep > 0f) {
                        drawArc(
                            color = macroColors[index],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(macroInset, macroInset),
                            size = macroSize,
                            style = Stroke(width = macroStroke, cap = StrokeCap.Round)
                        )
                    }
                    startAngle += sweep + gap
                }
            } else {
                drawArc(
                    color = track.copy(alpha = .7f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(macroInset, macroInset),
                    size = macroSize,
                    style = Stroke(width = macroStroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(70.dp)
        ) {
            Text(
                (if (caloriesComplete) "" else "~") + calories,
                color = if (SuperhumanAppearance.darkMode) Color.White else HomeNavy,
                fontSize = 21.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "kcal",
                color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .48f) else HomeMuted,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            if (remaining != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    if (remaining > 0) "$remaining left" else "target met",
                    color = accent,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HomeNutritionMacroRow(
    label: String,
    value: Int,
    target: Int?,
    accent: Color
) {
    val progress = target?.takeIf { it > 0 }?.let {
        (value.toFloat() / it.toFloat()).coerceIn(0f, 1f)
    }
    val complete = target != null && value >= target
    val track = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .09f) else HomeBorder

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .55f) else HomeMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                buildString {
                    append(value)
                    if (target != null) append(" / ").append(target)
                    append(" g")
                    if (complete) append("  ✓")
                },
                color = if (SuperhumanAppearance.darkMode) Color.White else HomeInk,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (progress != null) {
            Box(
                Modifier.fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(track)
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier.fillMaxWidth(progress)
                            .fillMaxSize()
                            .background(accent, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
internal fun LegacyHomeLinks(openMindfulness: () -> Unit, openExercise: () -> Unit, openInsights: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeLinkCard("Mindfulness →", "Breathing and meditation tools.", Modifier.weight(1f), openMindfulness)
        HomeLinkCard("Training progress →", "PRs, volume and weekly muscle work.", Modifier.weight(1f), openExercise)
    }
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth()) { HomeLinkCard("Superhuman Insights →", "See how sleep and body trends interact.", Modifier.fillMaxWidth(.52f), openInsights) }
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
    Text("Blood pressure tools", color = HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp))
}
