package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val SleepAccent = Color(0xFFB0A5EE)

/**
 * Compact, content-sized daily sleep summary. Never clip content just to fit the fold:
 * large font settings and smaller phones must scroll naturally.
 */
@Composable
internal fun HomeSleepInsightTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val score = snapshot.sleepScore
    val minutes = snapshot.sleepMinutes
    val durationDelta = minutes?.minus(480)

    val status = when {
        minutes == null -> "Awaiting sleep data"
        score == null -> "Sleep recorded"
        score >= 85 -> "Strong recovery"
        score >= 70 -> "Solid recovery"
        score >= 55 -> "Mixed recovery"
        else -> "Low recovery"
    }

    val insight = when {
        minutes == null -> "Sync your sleep to see duration, score and recovery insights."
        score == null -> "Open Sleep to review your night and connect your sleep score."
        score >= 85 && minutes >= 450 -> "Sleep duration and score both look strong."
        durationDelta != null && durationDelta <= -45 ->
            "${formatMinutesHome(abs(durationDelta))} below the 8-hour reference."
        score < 70 -> "Open Sleep to understand what affected your score."
        durationDelta != null && durationDelta >= 45 ->
            "${formatMinutesHome(durationDelta)} above the 8-hour reference."
        else -> "Explore stages, continuity and trends in Sleep."
    }

    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick)
            .padding(SuperhumanLayout.homeCardPadding),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(SleepAccent, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text("Sleep", color = superhumanTextPrimary, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (score != null) "Score $score" else "Not scored",
                color = if (score != null) SleepAccent else superhumanTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                minutes?.let(::formatMinutesHome) ?: "No sleep data",
                modifier = Modifier.weight(1f),
                color = superhumanTextPrimary,
                fontSize = if (minutes == null) 25.sp else 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold
            )
            if (minutes != null) {
                Spacer(Modifier.width(8.dp))
                Text("last night", color = superhumanTextMuted, fontSize = 12.sp,
                    lineHeight = 18.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(status, color = if (minutes == null) superhumanTextMuted else SleepAccent,
                fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(insight, color = superhumanTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
