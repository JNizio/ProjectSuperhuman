package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mentalHealthAccent: Color
    get() = if (SuperhumanAppearance.darkMode) Color(0xFFB6A0EE) else Color(0xFF7662B9)

/** One dashboard module for emotional check-ins, mindfulness, and guided breathing. */
@Composable
internal fun HomeMentalHealthTile(
    emotional: EmotionalPresentationSnapshot?,
    mindfulnessMinutesToday: Int,
    onClick: () -> Unit
) {
    val palette = superhumanPalette
    val mood = if (emotional == null) "No check-in yet" else
        emotionalTileInterpretation(emotional.axisValues)
    Column(
        Modifier.fillMaxWidth()
            .midnightCardSurface(accent = mentalHealthAccent, radius = 25.dp)
            .superhumanHomeTileClickable(onClick = onClick)
            .padding(horizontal = 19.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.tabler_activity_heartbeat),
                contentDescription = null,
                tint = mentalHealthAccent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Mental health", color = palette.textPrimary, fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("Mood, mindfulness & breathwork", color = palette.textMuted, fontSize = 11.sp)
            }
            Icon(painterResource(R.drawable.tabler_chevron_right), contentDescription = null,
                tint = palette.textMuted, modifier = Modifier.size(20.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1.5f)) {
                Text("LATEST MOOD", color = palette.textMuted, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp)
                Spacer(Modifier.height(5.dp))
                Text(mood, color = palette.textPrimary, fontSize = 15.sp,
                    lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f)) {
                Text("MINDFULNESS", color = palette.textMuted, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp)
                Spacer(Modifier.height(5.dp))
                Text(if (mindfulnessMinutesToday > 0) "${mindfulnessMinutesToday} min" else "Ready",
                    color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Today", color = palette.textMuted, fontSize = 10.sp)
            }
        }
    }
}

/** Lightweight hub: the existing individual features and their saved records remain unchanged. */
@Composable
internal fun NativeMentalHealthPage(
    onBack: () -> Unit,
    onMood: () -> Unit,
    onMindfulness: () -> Unit,
    onBreathwork: () -> Unit
) {
    val palette = superhumanPalette
    Column(Modifier.fillMaxSize().background(superhumanBackground)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuperhumanModuleBackButton(onBack)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Mental health", color = palette.textPrimary, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("Understand how you feel and take time to reset",
                    color = palette.textMuted, fontSize = 11.sp)
            }
        }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SuperhumanLayout.pageHorizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("CHECK IN & RESET", color = palette.textMuted,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            MentalHealthDestination(
                title = "Mood check-in",
                subtitle = "Record how you're feeling across six emotional signals",
                icon = R.drawable.tabler_activity_heartbeat,
                onClick = onMood
            )
            MentalHealthDestination(
                title = "Mindfulness",
                subtitle = "Meditation, reflection and your mindful-session history",
                icon = R.drawable.tabler_user,
                onClick = onMindfulness
            )
            MentalHealthDestination(
                title = "Breathwork",
                subtitle = "Open your existing guided breathing routine",
                icon = R.drawable.tabler_activity_heartbeat,
                onClick = onBreathwork
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MentalHealthDestination(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit
) {
    val palette = superhumanPalette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 94.dp)
            .midnightCardSurface(accent = mentalHealthAccent, radius = 22.dp)
            .superhumanHomeTileClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null,
            tint = mentalHealthAccent, modifier = Modifier.size(23.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = palette.textMuted, fontSize = 12.sp,
                lineHeight = 17.sp)
        }
        Icon(painterResource(R.drawable.tabler_chevron_right),
            contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
    }
}
