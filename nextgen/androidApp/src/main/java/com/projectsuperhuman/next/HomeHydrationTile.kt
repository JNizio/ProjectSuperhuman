package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Premium Home hydration tile. */
@Composable
internal fun PremiumHomeHydrationTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val goalMl = snapshot.waterGoalMl.coerceAtLeast(1)
    val rawMl = (snapshot.waterLitres * 1000.0).roundToInt()
    val shownMl = rawMl.coerceIn(0, goalMl)
    val targetFraction = (shownMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetFraction, tween(650), label = "home-water-progress")
    val pct = (animatedFraction * 100).roundToInt()
    val remaining = (goalMl - shownMl).coerceAtLeast(0)

    val context = LocalContext.current
    val waterImage = remember {
        runCatching {
            context.assets.open("dashboard_water.png").use(BitmapFactory::decodeStream)?.asImageBitmap()
        }.getOrNull()
    }

    Box(
        Modifier.fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Color(0xFFFCFDFE))
            .border(1.dp, Color(0xFFE1E9EF), RoundedCornerShape(27.dp))
            .superhumanClickable(onClick = onClick)
    ) {
        if (waterImage != null) {
            Image(
                bitmap = waterImage,
                contentDescription = null,
                modifier = Modifier.width(182.dp).fillMaxSize().align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
                alpha = .92f
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFCFDFE),
                        Color(0xFFFCFDFE).copy(alpha = .98f),
                        Color(0xFFFCFDFE).copy(alpha = .72f),
                        Color.Transparent
                    )
                )
            )
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    "HYDRATION",
                    color = Color(0xFF748294),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp
                )
                Spacer(Modifier.height(9.dp))
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        drawCircle(Color(0xFFD9EAF2), style = Stroke(width = stroke))
                        if (animatedFraction > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(listOf(Color(0xFF0D6CB4), Color(0xFF20A7C4), Color(0xFF0D6CB4))),
                                startAngle = -90f,
                                sweepAngle = animatedFraction * 360f,
                                useCenter = false,
                                style = Stroke(width = stroke)
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((-4).dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("$pct%", color = Color(0xFF123D70), fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Text("today", color = Color(0xFF748294), fontSize = 8.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (shownMl >= 1000) "%.1f L".format(shownMl / 1000.0) else "$shownMl ml",
                    color = Color(0xFF123D70),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    "of ${if (goalMl >= 1000) "%.1f L".format(goalMl / 1000.0) else "$goalMl ml"} daily target",
                    color = Color(0xFF748294),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (remaining == 0) "Goal reached" else "${if (remaining >= 1000) "%.1f L".format(remaining / 1000.0) else "$remaining ml"} remaining",
                    color = if (remaining == 0) Color(0xFF4AAE91) else Color(0xFF0D6CB4),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
