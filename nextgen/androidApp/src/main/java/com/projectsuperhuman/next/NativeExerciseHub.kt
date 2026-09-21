package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ExerciseDestination {
    HUB,
    STRENGTH,
    CARDIO
}

@Composable
internal fun NativeExerciseHub(
    onBackToHome: () -> Unit,
    openLegacy: () -> Unit
) {
    var destination by remember { mutableStateOf(ExerciseDestination.HUB) }

    BackHandler {
        if (destination == ExerciseDestination.HUB) {
            onBackToHome()
        } else {
            destination = ExerciseDestination.HUB
        }
    }

    when (destination) {
        ExerciseDestination.HUB -> NativeExerciseLandingPage(
            onBack = onBackToHome,
            onOpenStrength = { destination = ExerciseDestination.STRENGTH },
            onOpenCardio = { destination = ExerciseDestination.CARDIO }
        )

        ExerciseDestination.STRENGTH -> NativeStrengthTrainingScreen(
            onBack = { destination = ExerciseDestination.HUB },
            openLegacy = openLegacy
        )

        ExerciseDestination.CARDIO -> NativeCardioEntryScreen(
            onBack = { destination = ExerciseDestination.HUB }
        )
    }
}

@Composable
internal fun NativeStrengthTrainingScreen(
    onBack: () -> Unit,
    openLegacy: () -> Unit
) {
    NativeExerciseParityScreen(onBack = onBack, openLegacy = openLegacy)
}

private data class ExerciseRecentItem(
    val timestamp: Long,
    val type: String,
    val title: String,
    val detail: String,
    val accent: Color
)

@Composable
private fun NativeExerciseLandingPage(
    onBack: () -> Unit,
    onOpenStrength: () -> Unit,
    onOpenCardio: () -> Unit
) {
    val background = superhumanBackground
    val ink = superhumanTextPrimary
    val muted = superhumanTextMuted
    val surface = superhumanSurface
    val softSurface = superhumanSurfaceSoft
    val border = superhumanBorder
    val strengthAccent = superhumanBlue
    val cardioAccent = superhumanGreen

    val cardioViewModel: CardioViewModel = viewModel()
    val cardioState by cardioViewModel.state.collectAsState()
    val cardioSessions = cardioState.sessions

    var strengthSessions by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    val exerciseData = remember { NativeDomainData.forDomain(HealthDomain.EXERCISE) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val lookback = 5L * 365L * 86_400_000L
        strengthSessions = exerciseData
            .between("workout_session", now - lookback, now)
            .sortedByDescending { it.timestampEpochMs }
            .take(1000)
    }

    val now = System.currentTimeMillis()
    val weekStart = now - 7L * 86_400_000L
    val strengthWeek = strengthSessions.filter { it.timestampEpochMs >= weekStart }
    val cardioWeek = cardioSessions.filter { it.endedAt >= weekStart }

    val strengthWeekMinutes = strengthWeek.sumOf {
        it.metadata["durationMin"]?.toIntOrNull() ?: 0
    }
    val cardioWeekMinutes = cardioWeek.sumOf { it.durationSeconds } / 60
    val weekWorkoutCount = strengthWeek.size + cardioWeek.size
    val weekMinutes = strengthWeekMinutes + cardioWeekMinutes
    val weekActiveDays = (
        strengthWeek.map { exerciseDay(it.timestampEpochMs) } +
            cardioWeek.map { exerciseDay(it.endedAt) }
        ).distinct().size

    val lastStrength = strengthSessions.firstOrNull()
    val lastCardio = cardioSessions.maxByOrNull { it.endedAt }
    val cardioWeekDistance = cardioWeek.mapNotNull { it.distanceKm }.sum()

    val recentItems = remember(strengthSessions, cardioSessions, ink, strengthAccent, cardioAccent) {
        val strength = strengthSessions.take(8).map { row ->
            val name = row.metadata["workoutName"].orEmpty().ifBlank { "Strength workout" }
            val duration = row.metadata["durationMin"]?.toIntOrNull()
            val sets = row.metadata["workingSets"]?.toIntOrNull()
            ExerciseRecentItem(
                timestamp = row.timestampEpochMs,
                type = "Strength",
                title = name,
                detail = buildList {
                    duration?.takeIf { it > 0 }?.let { add("${it} min") }
                    sets?.takeIf { it > 0 }?.let { add("${it} sets") }
                }.joinToString(" · ").ifBlank { "Completed workout" },
                accent = strengthAccent
            )
        }
        val cardio = cardioSessions.sortedByDescending { it.endedAt }.take(8).map { session ->
            ExerciseRecentItem(
                timestamp = session.endedAt,
                type = "Cardio",
                title = session.activity.displayName,
                detail = buildList {
                    add("${session.durationSeconds / 60} min")
                    session.distanceKm?.let {
                        add(String.format(Locale.US, "%.1f km", it))
                    }
                }.joinToString(" · "),
                accent = cardioAccent
            )
        }
        (strength + cardio).sortedByDescending { it.timestamp }.take(5)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.superhumanTopButton(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    color = superhumanBrandText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "Exercise",
                    color = ink,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Training",
                    color = muted,
                    fontSize = 10.sp
                )
            }
        }

        if (cardioState.liveDraft != null) {
            ExerciseActiveCardioCard(
                activity = cardioState.liveDraft?.activity?.displayName ?: "Cardio",
                elapsedSeconds = cardioState.liveElapsedSeconds,
                accent = cardioAccent,
                surface = surface,
                border = border,
                ink = ink,
                muted = muted,
                onClick = onOpenCardio
            )
        }

        ExerciseModuleCard(
            visualKind = "strength",
            title = "STRENGTH",
            primary = if (strengthWeek.isEmpty()) "No strength sessions this week"
            else "${strengthWeek.size} session${if (strengthWeek.size == 1) "" else "s"} this week",
            secondary = lastStrength?.let {
                val name = it.metadata["workoutName"].orEmpty().ifBlank { "Strength workout" }
                "Last: $name · ${exerciseRelativeDate(it.timestampEpochMs)}"
            } ?: "Start a workout, use routines and track PRs",
            accent = strengthAccent,
            surface = surface,
            border = border,
            ink = ink,
            muted = muted,
            onClick = onOpenStrength
        )

        ExerciseModuleCard(
            visualKind = "cardio",
            title = "CARDIO",
            primary = if (cardioWeek.isEmpty()) "No cardio sessions this week"
            else buildString {
                append("${cardioWeekMinutes} min")
                if (cardioWeekDistance > 0.0) {
                    append(" · ")
                    append(String.format(Locale.US, "%.1f km", cardioWeekDistance))
                }
                append(" this week")
            },
            secondary = lastCardio?.let {
                "Last: ${it.activity.displayName} · ${exerciseRelativeDate(it.endedAt)}"
            } ?: "Run, walk, cycle and track cardio fitness",
            accent = cardioAccent,
            surface = surface,
            border = border,
            ink = ink,
            muted = muted,
            onClick = onOpenCardio
        )

        Text(
            "THIS WEEK",
            color = muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(softSurface, RoundedCornerShape(16.dp))
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExerciseCompactMetric(
                value = weekWorkoutCount.toString(),
                label = "WORKOUTS",
                ink = ink,
                muted = muted,
                modifier = Modifier.weight(1f)
            )
            ExerciseMetricDivider(border)
            ExerciseCompactMetric(
                value = weekMinutes.toString(),
                label = "MINUTES",
                ink = ink,
                muted = muted,
                modifier = Modifier.weight(1f)
            )
            ExerciseMetricDivider(border)
            ExerciseCompactMetric(
                value = weekActiveDays.toString(),
                label = "ACTIVE DAYS",
                ink = ink,
                muted = muted,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "RECENT",
            color = muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 2.dp)
        )

        if (recentItems.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(softSurface, RoundedCornerShape(18.dp))
                    .border(1.dp, border, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(
                    "No workouts yet",
                    color = ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your completed strength and cardio sessions will appear here.",
                    color = muted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(surface, RoundedCornerShape(16.dp))
                    .border(1.dp, border, RoundedCornerShape(16.dp))
            ) {
                recentItems.forEachIndexed { index, item ->
                    ExerciseRecentRow(
                        item = item,
                        ink = ink,
                        muted = muted,
                        onClick = if (item.type == "Strength") onOpenStrength else onOpenCardio
                    )
                    if (index != recentItems.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(border.copy(alpha = .75f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ExerciseActiveCardioCard(
    activity: String,
    elapsedSeconds: Int,
    accent: Color,
    surface: Color,
    border: Color,
    ink: Color,
    muted: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .13f else .08f), RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = .42f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(accent, CircleShape)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                "${activity.uppercase()} IN PROGRESS",
                color = ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                exerciseDuration(elapsedSeconds),
                color = muted,
                fontSize = 9.sp
            )
        }
        Text(
            "RESUME  →",
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ExerciseModuleCard(
    visualKind: String,
    title: String,
    primary: String,
    secondary: String,
    accent: Color,
    surface: Color,
    border: Color,
    ink: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val strength = visualKind == "strength"

    Box(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (strength) {
                        listOf(
                            Color(0xFF071B31),
                            Color(0xFF0A2B4B),
                            Color(0xFF0E426B)
                        )
                    } else {
                        listOf(
                            Color(0xFF08262B),
                            Color(0xFF0A3A40),
                            Color(0xFF0E625C)
                        )
                    }
                )
            )
            .border(1.dp, accent.copy(alpha = .34f), shape)
            .superhumanClickable(onClick = onClick)
    ) {
        ExerciseRemoteBackground(
            imageUrl = if (strength) {
                "https://images.pexels.com/photos/16080056/pexels-photo-16080056.jpeg?auto=compress&cs=tinysrgb&w=1200&h=400&fit=crop"
            } else {
                "https://images.pexels.com/photos/15875672/pexels-photo-15875672.jpeg?auto=compress&cs=tinysrgb&w=1200&h=400&fit=crop"
            },
            modifier = Modifier.fillMaxSize()
        )

        val tintOverlay = if (strength) {
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF081C33).copy(alpha = .82f),
                    Color(0xFF0C3C63).copy(alpha = .62f),
                    Color(0xFF1E5C8E).copy(alpha = .38f),
                    Color.Black.copy(alpha = .20f)
                )
            )
        } else {
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF082124).copy(alpha = .84f),
                    Color(0xFF0C4A4C).copy(alpha = .62f),
                    Color(0xFF1D7B75).copy(alpha = .38f),
                    Color.Black.copy(alpha = .20f)
                )
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(tintOverlay)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .10f))
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 17.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(Color.White.copy(alpha = .10f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = .15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (strength) R.drawable.tabler_dumbbell else R.drawable.tabler_run
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    primary,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    secondary,
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 1
                )
            }

            Box(
                Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = .10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "→",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExerciseRemoteBackground(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imageUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(imageUrl) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                URL(imageUrl).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ExerciseCompactMetric(
    value: String,
    label: String,
    ink: Color,
    muted: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            value,
            color = ink,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = muted,
            fontSize = 7.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExerciseMetricDivider(border: Color) {
    Box(
        Modifier
            .height(30.dp)
            .size(width = 1.dp, height = 30.dp)
            .background(border)
    )
}

@Composable
private fun ExerciseRecentRow(
    item: ExerciseRecentItem,
    ink: Color,
    muted: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .superhumanClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(
                    item.accent.copy(alpha = if (SuperhumanAppearance.darkMode) .16f else .09f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (item.type == "Strength") "S" else "C",
                color = item.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 11.dp)
        ) {
            Text(
                item.title,
                color = ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${exerciseRelativeDate(item.timestamp)} · ${item.detail}",
                color = muted,
                fontSize = 8.sp
            )
        }
        Text(
            "→",
            color = item.accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun exerciseDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

private fun exerciseDay(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

private fun exerciseRelativeDate(timestamp: Long): String {
    val date = exerciseDay(timestamp)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

@Composable
internal fun NativeCardioEntryScreen(onBack: () -> Unit) {
    NativeCardioScreen(onBack = onBack)
}
