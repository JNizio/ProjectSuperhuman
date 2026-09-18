package com.projectsuperhuman.next

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class ExerciseDestination {
    HUB,
    STRENGTH,
    CARDIO
}

/**
 * Top-level Exercise router.
 *
 * Home owns entry/exit from Exercise. This router owns navigation between the
 * Exercise landing page and its Strength/Cardio submodules.
 */
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

/**
 * Stable Strength integration seam.
 *
 * The current parity implementation stays untouched so Strength feature work
 * can continue independently of top-level Exercise navigation.
 */
@Composable
internal fun NativeStrengthTrainingScreen(
    onBack: () -> Unit,
    openLegacy: () -> Unit
) {
    NativeExerciseParityScreen(onBack = onBack, openLegacy = openLegacy)
}

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
    val border = superhumanBorder
    val strengthAccent = superhumanBlue
    val cardioAccent = superhumanGreen

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    "Choose how you want to train",
                    color = muted,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            superhumanBrandText.copy(alpha = if (SuperhumanAppearance.darkMode) .92f else 1f),
                            superhumanBlue.copy(alpha = .90f)
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(21.dp)
        ) {
            Text(
                "TRAINING",
                color = Color.White.copy(alpha = .68f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "What are you training?",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Strength and cardio stay separate so logging and progress remain clear.",
                color = Color.White.copy(alpha = .76f),
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        ExerciseModuleCard(
            mark = "S",
            title = "STRENGTH",
            description = "Resistance training, routines, sets and personal records",
            accent = strengthAccent,
            surface = surface,
            border = border,
            ink = ink,
            muted = muted,
            onClick = onOpenStrength
        )

        ExerciseModuleCard(
            mark = "C",
            title = "CARDIO",
            description = "Running, walking, cycling, heart rate and endurance",
            accent = cardioAccent,
            surface = surface,
            border = border,
            ink = ink,
            muted = muted,
            onClick = onOpenCardio
        )

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ExerciseModuleCard(
    mark: String,
    title: String,
    description: String,
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
            .background(surface, RoundedCornerShape(23.dp))
            .border(1.dp, border, RoundedCornerShape(23.dp))
            .clickable { onClick() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(
                    accent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                mark,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                title,
                color = ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                color = muted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        Text(
            "→",
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Cardio integration seam owned by the Exercise router.
 *
 * Task 2 can replace this placeholder body or move the implementation to its
 * own file while keeping the same signature. No cardio persistence/progress
 * logic belongs here.
 */
@Composable
internal fun NativeCardioEntryScreen(onBack: () -> Unit) {
    val ink = superhumanTextPrimary
    val muted = superhumanTextMuted
    val accent = superhumanGreen

    Column(
        Modifier
            .fillMaxSize()
            .background(superhumanBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.superhumanTopButton(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    color = accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "Cardio",
                    color = ink,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Running · walking · cycling · endurance",
                    color = muted,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(superhumanSurface, RoundedCornerShape(23.dp))
                .border(1.dp, superhumanBorder, RoundedCornerShape(23.dp))
                .padding(18.dp)
        ) {
            Text(
                "CARDIO",
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Cardio training",
                color = ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Cardio logging and progress will plug into this entry point.",
                color = muted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}
