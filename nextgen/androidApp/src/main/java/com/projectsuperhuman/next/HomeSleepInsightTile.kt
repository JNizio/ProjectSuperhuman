package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val HomeNightTop = Color(0xFF11173F)
private val HomeNightMid = Color(0xFF24205D)
private val HomeNightBottom = Color(0xFF4C3F9F)
private val HomeNightLavender = Color(0xFFD7D2FF)
private val HomeNightMint = Color(0xFF8FE2CE)

/** Full-width home sleep tile using the same night-sky visual language as the Sleep module hero. */
@Composable
internal fun HomeSleepInsightTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val score = snapshot.sleepScore
    val minutes = snapshot.sleepMinutes
    val durationDelta = minutes?.minus(480)

    val headline = when {
        score == null || minutes == null -> "Your night at a glance"
        score >= 85 -> "Strong recovery night"
        score >= 70 -> "A solid night of sleep"
        score >= 55 -> "Recovery has room to improve"
        else -> "Your sleep needs attention"
    }

    val insight = when {
        minutes == null -> "Sync your wearable to bring last night's sleep into your recovery picture."
        durationDelta != null && durationDelta >= 30 -> "You slept ${formatMinutesHome(durationDelta)} beyond the 8-hour reference."
        durationDelta != null && durationDelta <= -30 -> "You were ${formatMinutesHome(abs(durationDelta))} short of the 8-hour reference."
        else -> "Your sleep duration landed close to the 8-hour reference."
    }

    Box(
        Modifier.fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Brush.verticalGradient(listOf(HomeNightTop, HomeNightMid, HomeNightBottom)))
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stars = listOf(
                .07f to .17f, .16f to .31f, .29f to .12f, .43f to .23f,
                .59f to .11f, .72f to .27f, .84f to .15f, .93f to .35f,
                .18f to .76f, .51f to .68f, .77f to .80f, .90f to .64f
            )
            stars.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = Color.White.copy(alpha = if (index % 3 == 0) .55f else .28f),
                    radius = if (index % 4 == 0) 2.2f else 1.35f,
                    center = Offset(size.width * x, size.height * y)
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = .035f),
                radius = size.width * .34f,
                center = Offset(size.width * .94f, size.height * .24f)
            )
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "SLEEP INTELLIGENCE",
                        color = HomeNightLavender.copy(alpha = .78f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.05.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        headline,
                        color = Color.White,
                        fontSize = 21.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        score?.toString() ?: "—",
                        color = Color.White,
                        fontSize = 34.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("sleep score", color = Color.White.copy(alpha = .58f), fontSize = 8.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(insight, color = Color.White.copy(alpha = .80f), fontSize = 10.sp, lineHeight = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        HomeSleepMetric("DURATION", minutes?.let(::formatMinutesHome) ?: "—")
                        HomeSleepMetric("TARGET", "8h 0m")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("Open sleep  →", color = HomeNightMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeSleepMetric(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = .48f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}
