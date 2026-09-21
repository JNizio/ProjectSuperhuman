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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                Text(
                    "TRAINING",
                    color = Color.White.copy(alpha = .64f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp
                )
                Box(
                    Modifier.width(36.dp).height(36.dp)
                        .background(Color.White.copy(alpha = .09f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "→",
                        color = Color.White.copy(alpha = .88f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            Text(headline, color = Color.White, fontSize = 25.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
            Text(
                summaryLine,
                color = Color.White.copy(alpha = .56f),
                fontSize = 9.sp
            )

            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .10f)))
            Spacer(Modifier.height(11.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TrainingSummaryColumn(
                    title = "STRENGTH",
                    accent = strengthAccent,
                    primary = when {
                        snapshot.strengthWorkoutsToday > 0 && !snapshot.latestStrengthWorkoutName.isNullOrBlank() ->
                            snapshot.latestStrengthWorkoutName
                        snapshot.strengthWorkoutsToday > 0 -> "${snapshot.strengthWorkoutsToday} session${if (snapshot.strengthWorkoutsToday == 1) "" else "s"}"
                        else -> "No strength today"
                    },
                    secondary = if (snapshot.strengthWorkoutsToday > 0) {
                        "${snapshot.workoutSetsToday} sets · $volumeLabel"
                    } else {
                        "Routines · PRs · history"
                    },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    Modifier.width(1.dp).height(49.dp)
                        .background(Color.White.copy(alpha = .10f))
                )

                TrainingSummaryColumn(
                    title = "CARDIO",
                    accent = cardioAccent,
                    primary = when {
                        snapshot.cardioWorkoutsToday > 0 && !snapshot.latestCardioActivityName.isNullOrBlank() ->
                            snapshot.latestCardioActivityName
                        snapshot.cardioWorkoutsToday > 0 -> "${snapshot.cardioWorkoutsToday} session${if (snapshot.cardioWorkoutsToday == 1) "" else "s"}"
                        else -> "No cardio today"
                    },
                    secondary = if (snapshot.cardioWorkoutsToday > 0) {
                        listOfNotNull(
                            distanceLabel.takeIf { snapshot.cardioDistanceTodayKm > 0.0 },
                            "${snapshot.cardioMinutesToday} min".takeIf { snapshot.cardioMinutesToday > 0 }
                        ).joinToString(" · ").ifBlank { "Session logged" }
                    } else {
                        "Run · walk · cycle"
                    },
                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun TrainingSummaryColumn(
    title: String,
    accent: Color,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(7.dp).height(7.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = accent,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = .65.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            primary,
            color = Color.White,
            fontSize = 12.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            secondary,
            color = Color.White.copy(alpha = .50f),
            fontSize = 7.sp,
            lineHeight = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun LegacyBodyCard(snapshot: NativeHomeSnapshot, modifier: Modifier, onClick: () -> Unit) {
    val weightLabel = snapshot.bodyWeightKg?.let { "%.1f kg".format(it) } ?: "—"
    val changeLabel = snapshot.bodyWeightChange30d?.let {
        val sign = if (it > 0) "+" else ""
        "$sign${"%.1f".format(it)} kg"
    } ?: "—"
    val entriesLabel = snapshot.bodyWeightTrend.size.takeIf { it > 0 }?.toString() ?: "—"
    val gradient = if (SuperhumanAppearance.darkMode) {
        listOf(superhumanSurfaceElevated, Color(0xFF1B1929))
    } else {
        listOf(Color(0xFFFCFCFF), Color(0xFFF7F5FC))
    }

    Column(
        modifier.height(116.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.horizontalGradient(gradient))
            .border(1.dp, HomeBorder, RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("BODY", color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("→", color = HomeMuted, fontSize = 21.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyHomeStat("WEIGHT", weightLabel, Modifier.weight(1f))
            BodyHomeStat("30 DAYS", changeLabel, Modifier.weight(1f))
            BodyHomeStat("ENTRIES", entriesLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BodyHomeStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = HomeMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, color = HomePurple, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
internal fun HomeBodyMindfulnessRow(snapshot: NativeHomeSnapshot, openBody: () -> Unit, openMindfulness: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyBodyCard(snapshot, Modifier.fillMaxWidth(), openBody)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegacyMindfulnessCard(snapshot, Modifier.weight(1f), openMindfulness)
            LegacyBreathworkCard(Modifier.weight(1f), openMindfulness)
        }
    }
}

@Composable
private fun LegacyMindfulnessCard(snapshot: NativeHomeSnapshot, modifier: Modifier, onClick: () -> Unit) {
    val minutes = snapshot.mindfulnessMinutesToday
    val card = if (SuperhumanAppearance.darkMode) Color(0xFF0F252A) else Color(0xFFF4FAFB)
    Column(modifier.height(132.dp).clip(RoundedCornerShape(23.dp)).background(card).border(1.dp, HomeBorder, RoundedCornerShape(23.dp)).clickable(onClick = onClick).padding(15.dp)) {
        LegacyCardHeader("MINDFULNESS")
        Spacer(Modifier.height(7.dp))
        Text(if (minutes > 0) "$minutes min" else "Ready", color = if (SuperhumanAppearance.darkMode) Color(0xFF75D5D0) else Color(0xFF176B72), fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(if (minutes > 0) "mindful time today" else "Meditate · reflect · reset", color = HomeMuted, fontSize = 8.sp)
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            listOf(10, 17, 25, 18, 12).forEachIndexed { index, height ->
                Box(Modifier.weight(1f).height(height.dp).background(if (minutes > 0 && index < 3) Color(0xFF5CB7AE) else superhumanSurfaceSoft, RoundedCornerShape(8.dp)))
            }
        }
    }
}

@Composable
private fun LegacyBreathworkCard(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(132.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0A3769), Color(0xFF0D7394), Color(0xFF24AFB0))))
            .border(1.dp, Color(0xFF3A9BB0), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .08f), radius = size.minDimension * .32f, center = Offset(size.width * .77f, size.height * .54f))
            drawCircle(Color.White.copy(alpha = .06f), radius = size.minDimension * .21f, center = Offset(size.width * .77f, size.height * .54f), style = Stroke(width = 2f))
        }
        Column(Modifier.fillMaxSize().padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BREATHWORK", color = Color.White.copy(alpha = .68f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("→", color = Color.White.copy(alpha = .72f), fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("3 rounds", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text("30 breaths · guided retention", color = Color.White.copy(alpha = .72f), fontSize = 8.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(56.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) {
                Box(Modifier.fillMaxWidth(.66f).fillMaxSize().background(Color(0xFF8DEBDD)))
            }
        }
    }
}

@Composable
private fun BodySparkline(values: List<Double>) {
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        if (values.size < 2) { drawLine(HomePurple.copy(alpha = .32f), Offset(0f, size.height * .65f), Offset(size.width, size.height * .65f), strokeWidth = 3f); return@Canvas }
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
        drawCircle(superhumanSurface, 7f, Offset(size.width - 2f, lastY))
        drawCircle(HomePurple, 7f, Offset(size.width - 2f, lastY), style = Stroke(width = 3f))
    }
}

@Composable
internal fun LegacySleepCard(snapshot: NativeHomeSnapshot, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(148.dp).clip(RoundedCornerShape(23.dp)).background(HomeCard).border(1.dp, HomeBorder, RoundedCornerShape(23.dp)).clickable(onClick = onClick)) {
        LegacyAssetImage("dashboard_sleep.png", Modifier.width(115.dp).fillMaxSize().align(Alignment.CenterEnd), alpha = if (SuperhumanAppearance.darkMode) .28f else .62f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(HomeCard, HomeCard.copy(alpha = .93f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(15.dp)) {
            LegacyCardHeader("SLEEP")
            Spacer(Modifier.height(8.dp))
            Text(snapshot.sleepMinutes?.let(::formatMinutesHome) ?: "—", color = HomeNavy, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(superhumanSurfaceSoft)) {
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
        Text("→", color = HomeMuted, fontSize = 18.sp)
    }
}

@Composable
internal fun LegacyNutritionCard(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val hasFood = snapshot.nutritionEntriesToday > 0
    val nutritionGradient = if (SuperhumanAppearance.darkMode) {
        listOf(Color(0xFF071824), Color(0xFF0B2530), Color(0xFF12353A))
    } else {
        listOf(Color(0xFFF7FBFA), Color(0xFFEFF7F4), Color(0xFFE7F1EC))
    }
    val accent = if (SuperhumanAppearance.darkMode) Color(0xFF67D4C0) else Color(0xFF167C6B)
    val statSurface = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .075f) else Color.White.copy(alpha = .72f)
    val statBorder = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .08f) else Color(0xFF1D6F62).copy(alpha = .10f)

    Box(
        Modifier.fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(nutritionGradient))
            .border(1.dp, accent.copy(alpha = if (SuperhumanAppearance.darkMode) .20f else .13f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        // Treat the food photography as atmosphere rather than a separate image panel.
        // It fills the tile, then two scrims dissolve it into the surface so no hard image edge is visible.
        LegacyAssetImage(
            "dashboard_nutrition.png",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (SuperhumanAppearance.darkMode) .20f else .30f
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        nutritionGradient.first(),
                        nutritionGradient.first().copy(alpha = .98f),
                        nutritionGradient[1].copy(alpha = .83f),
                        nutritionGradient.last().copy(alpha = .34f),
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
                        nutritionGradient.first().copy(alpha = .18f),
                        nutritionGradient.first().copy(alpha = .58f)
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
                Text(
                    "NUTRITION",
                    color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .67f) else HomeMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp
                )
                Box(
                    Modifier.width(34.dp).height(34.dp)
                        .background(accent.copy(alpha = .12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    snapshot.caloriesToday.toString(),
                    color = if (SuperhumanAppearance.darkMode) Color.White else HomeNavy,
                    fontSize = 29.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "kcal",
                    color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .65f) else HomeMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                if (hasFood) "Logged today" else "No food logged yet",
                color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .55f) else HomeMuted,
                fontSize = 9.sp
            )

            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutritionStat(
                    label = "PROTEIN",
                    value = if (hasFood) "${snapshot.proteinToday} g" else "—",
                    accent = accent,
                    surface = statSurface,
                    border = statBorder,
                    modifier = Modifier.weight(1f)
                )
                NutritionStat(
                    label = "FOODS",
                    value = if (hasFood) snapshot.nutritionEntriesToday.toString() else "0",
                    accent = accent,
                    surface = statSurface,
                    border = statBorder,
                    modifier = Modifier.weight(1f)
                )
                NutritionStat(
                    label = "STATUS",
                    value = if (hasFood) "Active" else "Start",
                    accent = accent,
                    surface = statSurface,
                    border = statBorder,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NutritionStat(
    label: String,
    value: String,
    accent: Color,
    surface: Color,
    border: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.height(46.dp)
            .background(surface, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .46f) else HomeMuted,
            fontSize = 6.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .55.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
