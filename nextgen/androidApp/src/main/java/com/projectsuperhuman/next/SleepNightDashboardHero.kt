package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val NightTop = Color(0xFF11173F)
private val NightMid = Color(0xFF24205D)
private val NightBottom = Color(0xFF4C3F9F)
private val NightLavender = Color(0xFFD7D2FF)
private val NightGold = Color(0xFFFFD98A)
private val NightMint = Color(0xFF8FE2CE)

@Composable
internal fun SleepNightDashboardHero(s: NativeSleepSnapshot?) {
    val analysis = s?.let { SleepIntelligenceEngine.analyse(it) }
    val score = analysis?.recoveryScore ?: s?.score
    val total = s?.totalMinutes
    val recentMinutes = s?.recentAverageMinutes
    val recentScore = s?.recentAverageScore
    val durationDelta = if (total != null && recentMinutes != null) total - recentMinutes else null
    val scoreDelta = if (score != null && recentScore != null) score - recentScore else null

    val historicalMessage = when {
        s == null || total == null -> "Connect sleep data to unlock your nightly intelligence."
        durationDelta != null && durationDelta >= 30 -> "You slept ${minutesCompact(durationDelta)} longer than your recent average."
        durationDelta != null && durationDelta <= -30 -> "You slept ${minutesCompact(abs(durationDelta))} less than your recent average."
        durationDelta != null -> "Your sleep duration was close to your recent baseline."
        else -> analysis?.insight ?: "Your nightly pattern will sharpen as more nights are recorded."
    }

    val patternLabel = when {
        scoreDelta != null && scoreDelta >= 8 -> "Above your baseline"
        scoreDelta != null && scoreDelta <= -8 -> "Below your baseline"
        scoreDelta != null -> "Near your baseline"
        (s?.sessionsImported ?: 0) >= 5 -> "Building your pattern"
        else -> "Learning your sleep"
    }

    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(NightTop, NightMid, NightBottom)),
                RoundedCornerShape(28.dp)
            )
    ) {
        NightSkyDecoration()

        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "SLEEP INTELLIGENCE",
                        color = NightLavender.copy(alpha = .78f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        analysis?.headline ?: "Your night at a glance",
                        color = Color.White,
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        patternLabel,
                        color = NightMint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        score?.toString() ?: "—",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("recovery", color = Color.White.copy(alpha = .60f), fontSize = 8.sp)
                }
            }

            Text(
                historicalMessage,
                color = Color.White.copy(alpha = .82f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                NightMetric(
                    label = "LAST NIGHT",
                    value = minutesCompact(total),
                    detail = when {
                        durationDelta == null -> "sleep"
                        durationDelta > 0 -> "+${minutesCompact(durationDelta)} vs usual"
                        durationDelta < 0 -> "−${minutesCompact(abs(durationDelta))} vs usual"
                        else -> "same as usual"
                    },
                    modifier = Modifier.weight(1f)
                )
                NightMetric(
                    label = "RECENT AVG",
                    value = minutesCompact(recentMinutes),
                    detail = recentScore?.let { "score $it" } ?: "building baseline",
                    modifier = Modifier.weight(1f)
                )
                NightMetric(
                    label = "DEEP + REM",
                    value = if (s?.deepMinutes != null || s?.remMinutes != null) {
                        minutesCompact((s.deepMinutes ?: 0) + (s.remMinutes ?: 0))
                    } else "—",
                    detail = analysis?.let { "${it.deepPct}% + ${it.remPct}%" } ?: "recovery stages",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                Modifier.fillMaxWidth()
                    .background(Color.White.copy(alpha = .09f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(7.dp).background(NightGold, CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tonight's focus",
                        color = Color.White.copy(alpha = .58f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        analysis?.priority ?: "Build enough history for personalised guidance",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if ((s?.sessionsImported ?: 0) > 0) {
                    Text(
                        "${s?.sessionsImported} nights",
                        color = NightLavender,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NightMetric(label: String, value: String, detail: String, modifier: Modifier) {
    Column(
        modifier.background(Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp)).padding(11.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = .50f), fontSize = 7.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(detail, color = Color.White.copy(alpha = .58f), fontSize = 7.sp, lineHeight = 10.sp)
    }
}

@Composable
private fun NightSkyDecoration() {
    Canvas(Modifier.fillMaxWidth().height(210.dp)) {
        val stars = listOf(
            .08f to .12f, .17f to .27f, .29f to .10f, .40f to .22f,
            .52f to .08f, .63f to .17f, .74f to .10f, .84f to .25f,
            .91f to .12f, .96f to .34f, .69f to .34f, .34f to .36f
        )
        stars.forEachIndexed { index, pair ->
            drawCircle(
                color = Color.White.copy(alpha = if (index % 3 == 0) .72f else .42f),
                radius = if (index % 4 == 0) 2.4f else 1.5f,
                center = Offset(size.width * pair.first, size.height * pair.second)
            )
        }

        val moonCenter = Offset(size.width * .88f, size.height * .22f)
        drawCircle(
            color = NightGold.copy(alpha = .20f),
            radius = 27f,
            center = moonCenter,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = NightGold.copy(alpha = .85f),
            radius = 13f,
            center = moonCenter
        )
        drawCircle(
            color = NightTop.copy(alpha = .96f),
            radius = 13f,
            center = Offset(moonCenter.x + 7f, moonCenter.y - 4f)
        )
    }
}

private fun minutesCompact(minutes: Int?): String {
    if (minutes == null) return "—"
    val safe = minutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
