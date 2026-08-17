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

/** Premium Home hydration tile that follows the active Superhuman colour scheme. */
@Composable
internal fun PremiumHomeHydrationTile(snapshot: NativeHomeSnapshot, onClick: () -> Unit) {
    val goalMl = snapshot.waterGoalMl.coerceAtLeast(1)
    val shownMl = (snapshot.waterLitres * 1000.0).roundToInt().coerceIn(0, goalMl)
    val targetFraction = (shownMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetFraction, tween(500), label = "home-water-progress")
    val pct = (animatedFraction * 100).roundToInt()
    val remaining = (goalMl - shownMl).coerceAtLeast(0)
    val shape = RoundedCornerShape(27.dp)
    val palette = superhumanPalette()
    val dark = superhumanDarkMode()
    val accent = if (dark) Color(0xFF64C7F2) else Color(0xFF0D6CB4)
    val ringTrack = if (dark) palette.surfaceRaised else Color(0xFFD9EAF2)

    val context = LocalContext.current
    val waterImage = remember {
        runCatching {
            context.assets.open("dashboard_water.png").use(BitmapFactory::decodeStream)?.asImageBitmap()
        }.getOrNull()
    }

    Box(
        Modifier.fillMaxWidth()
            .height(158.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .superhumanHomeTileClickable(onClick = onClick)
    ) {
        if (waterImage != null) {
            Image(
                bitmap = waterImage,
                contentDescription = null,
                modifier = Modifier.width(196.dp).fillMaxHeight().align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
                alpha = if (dark) .68f else .93f
            )
        }

        // Keep a surface-coloured veil over the photograph all the way to the right in dark mode.
        // This removes the hard light/dark seam that a transparent gradient produced with bright assets.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to palette.surface,
                        0.42f to palette.surface,
                        0.60f to palette.surface.copy(alpha = .98f),
                        0.75f to palette.surface.copy(alpha = if (dark) .82f else .68f),
                        0.89f to palette.surface.copy(alpha = if (dark) .61f else .22f),
                        1.00f to palette.surface.copy(alpha = if (dark) .46f else .04f)
                    )
                )
            )
        )

        Text(
            "HYDRATION",
            color = palette.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.15.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 19.dp, top = 17.dp)
        )

        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 19.dp, end = 132.dp, top = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 9.dp.toPx()
                    drawCircle(ringTrack, style = Stroke(width = stroke))
                    if (animatedFraction > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(accent, Color(0xFF20A7C4), accent)
                            ),
                            startAngle = -90f,
                            sweepAngle = animatedFraction * 360f,
                            useCenter = false,
                            style = Stroke(width = stroke)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", color = palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 20.sp)
                    Text("today", color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.Medium, lineHeight = 8.sp)
                }
            }

            Spacer(Modifier.width(15.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    if (shownMl >= 1000) "%.1f L".format(shownMl / 1000.0) else "$shownMl ml",
                    color = palette.ink,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 28.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${if (goalMl >= 1000) "%.1f L".format(goalMl / 1000.0) else "$goalMl ml"} daily goal",
                    color = palette.muted,
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (remaining == 0) "Goal reached" else "${if (remaining >= 1000) "%.1f L".format(remaining / 1000.0) else "$remaining ml"} remaining",
                    color = if (remaining == 0) palette.success else accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp
                )
            }
        }
    }
}
