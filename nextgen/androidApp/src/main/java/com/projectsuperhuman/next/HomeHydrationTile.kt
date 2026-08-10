package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Premium Home hydration tile. */
@Composable
internal fun PremiumHomeHydrationTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val goalMl = snapshot.waterGoalMl.coerceAtLeast(1)
    val shownMl = (snapshot.waterLitres * 1000.0).roundToInt().coerceIn(0, goalMl)
    val targetFraction = (shownMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetFraction, tween(500), label = "home-water-progress")
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
                modifier = Modifier.width(176.dp).fillMaxHeight().align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
                alpha = .93f
            )
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.00f to Color(0xFFFCFDFE),
                    0.44f to Color(0xFFFCFDFE),
                    0.66f to Color(0xFFFCFDFE).copy(alpha = .90f),
                    0.82f to Color(0xFFFCFDFE).copy(alpha = .28f),
                    1.00f to Color.Transparent
                )
            )
        )

        Text(
            "HYDRATION",
            color = Color(0xFF748294),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.15.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 19.dp, top = 17.dp)
        )

        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 19.dp, end = 132.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 9.dp.toPx()
                    drawCircle(Color(0xFFD9EAF2), style = Stroke(width = stroke))
                    if (animatedFraction > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color(0xFF0D6CB4), Color(0xFF20A7C4), Color(0xFF0D6CB4))
                            ),
                            startAngle = -90f,
                            sweepAngle = animatedFraction * 360f,
                            useCenter = false,
                            style = Stroke(width = stroke)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", color = Color(0xFF123D70), fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 20.sp)
                    Text("today", color = Color(0xFF748294), fontSize = 8.sp, fontWeight = FontWeight.Medium, lineHeight = 9.sp)
                }
            }

            Spacer(Modifier.width(17.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (shownMl >= 1000) "%.1f L".format(shownMl / 1000.0) else "$shownMl ml",
                    color = Color(0xFF123D70),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 29.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${if (goalMl >= 1000) "%.1f L".format(goalMl / 1000.0) else "$goalMl ml"} daily goal",
                    color = Color(0xFF748294),
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (remaining == 0) "Goal reached" else "${if (remaining >= 1000) "%.1f L".format(remaining / 1000.0) else "$remaining ml"} remaining",
                    color = if (remaining == 0) Color(0xFF4AAE91) else Color(0xFF0D6CB4),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
