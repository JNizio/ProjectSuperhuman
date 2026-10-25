package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Calm, readable sleep card. The compact progress line uses the 8h reference,
 * not an invented sleep score or sleep-stage timeline. */
@Composable
internal fun HomeSleepInsightTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val score = snapshot.sleepScore
    val minutes = snapshot.sleepMinutes
    val delta = minutes?.minus(480)
    val referenceProgress = minutes?.let { (it.toFloat() / 480f).coerceIn(0f, 1f) } ?: 0f

    val status = when {
        minutes == null -> "No sleep data"
        score == null -> "Sleep recorded"
        score >= 85 -> "Strong recovery"
        score >= 70 -> "Solid recovery"
        score >= 55 -> "Mixed recovery"
        else -> "Low recovery"
    }

    val detail = when {
        minutes == null -> "Sync your sleep data to see your nightly insights."
        score == null -> "Connect a sleep score to complete your overview."
        delta != null && delta <= -30 -> "${formatMinutesHome(abs(delta))} below the 8h reference"
        delta != null && delta >= 30 -> "${formatMinutesHome(delta)} above the 8h reference"
        else -> "Near the 8-hour sleep reference"
    }

    Column(
        Modifier.fillMaxWidth().premiumHomeTile(onClick, accent = SleepAccent)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp)
                .background(SleepAccent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text("Sleep", modifier = Modifier.weight(1f), color = superhumanTextPrimary,
                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Last night", color = superhumanTextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                minutes?.let(::formatMinutesHome) ?: "No reading",
                modifier = Modifier.weight(1f),
                color = superhumanTextPrimary,
                fontSize = if (minutes != null) 30.sp else 23.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Bold
            )
            if (score != null) {
                Box(
                    Modifier.background(SleepAccent.copy(alpha = .12f),
                        RoundedCornerShape(13.dp))
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(score.toString(), color = SleepAccent, fontSize = 21.sp,
                            lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Text("score", color = SleepAccent, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        if (minutes != null) {
            Box(Modifier.fillMaxWidth().height(4.dp)
                .background(superhumanBorder.copy(alpha = .65f), RoundedCornerShape(4.dp))) {
                if (referenceProgress > 0f) {
                    Box(Modifier.fillMaxWidth(referenceProgress).height(4.dp)
                        .background(SleepAccent, RoundedCornerShape(4.dp)))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(status, color = if (score != null) SleepAccent else superhumanTextPrimary,
                fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = superhumanTextMuted, fontSize = 12.sp,
                lineHeight = 17.sp)
        }
    }
}
