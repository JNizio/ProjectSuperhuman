package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val SleepSpaceNavy = Color(0xFF071A38)
private val SleepSpaceBlue = Color(0xFF0D376D)
private val SleepSpaceIndigo = Color(0xFF334A9A)
private val SleepSkyBlue = Color(0xFFA9C9FF)
private val SleepMint = Color(0xFF8FE2CE)

/** Full-width home sleep card: last night, score and one useful recovery insight. */
@Composable
internal fun HomeSleepInsightTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val score = snapshot.sleepScore
    val minutes = snapshot.sleepMinutes
    val durationDelta = minutes?.minus(480)
    val durationText = minutes?.let(::formatMinutesHome) ?: "—"

    val status = when {
        score == null || minutes == null -> "Last night"
        score >= 85 -> "Strong recovery"
        score >= 70 -> "Solid recovery"
        score >= 55 -> "Mixed recovery"
        else -> "Low recovery"
    }

    val insight = when {
        score == null || minutes == null -> "Sync your latest sleep to unlock a recovery insight here."
        score >= 85 && minutes >= 450 -> "Strong night — both sleep duration and your score landed well."
        durationDelta != null && durationDelta <= -45 -> "${formatMinutesHome(abs(durationDelta))} short of the 8h reference — more sleep would strengthen recovery."
        score < 70 -> "Your score was lower tonight — open Sleep to see what pulled recovery down."
        durationDelta != null && durationDelta >= 45 -> "A longer night — ${formatMinutesHome(durationDelta)} above the 8h reference."
        else -> "A steady night — open Sleep for stages, continuity and longer-term trends."
    }

    val shape = RoundedCornerShape(27.dp)

    Box(
        Modifier.fillMaxWidth()
            .height(158.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(SleepSpaceNavy, SleepSpaceBlue, SleepSpaceIndigo)))
            .superhumanHomeTileClickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val orbitCenter = Offset(size.width * .91f, size.height * .22f)
            drawCircle(
                color = SleepSkyBlue.copy(alpha = .09f),
                radius = size.width * .29f,
                center = orbitCenter,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Color.White.copy(alpha = .045f),
                radius = size.width * .19f,
                center = orbitCenter,
                style = Stroke(width = 1.2f)
            )

            val stars = listOf(
                .07f to .19f, .18f to .11f, .30f to .28f, .46f to .14f,
                .59f to .24f, .73f to .12f, .84f to .35f, .94f to .18f,
                .37f to .78f, .68f to .73f, .89f to .80f
            )
            stars.forEachIndexed { index, point ->
                drawCircle(
                    color = Color.White.copy(alpha = if (index % 4 == 0) .55f else .28f),
                    radius = if (index % 4 == 0) 2.2f else 1.35f,
                    center = Offset(size.width * point.first, size.height * point.second)
                )
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 14.dp)) {
                    Text(
                        "SLEEP",
                        color = SleepSkyBlue.copy(alpha = .82f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.15.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            durationText,
                            color = Color.White,
                            fontSize = 28.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("last night", color = Color.White.copy(alpha = .58f), fontSize = 8.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(status, color = SleepMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Column(
                    Modifier.background(Color.White.copy(alpha = .10f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        score?.toString() ?: "—",
                        color = Color.White,
                        fontSize = 27.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("SCORE", color = Color.White.copy(alpha = .55f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .65.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(Color.White.copy(alpha = .085f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(SleepMint, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text(
                    insight,
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = .84f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 2
                )
                Spacer(Modifier.width(8.dp))
                Text("→", color = SleepSkyBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
