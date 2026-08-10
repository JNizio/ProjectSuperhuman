package com.projectsuperhuman.next

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hydration-specific visual primitive. The fill uses a fast spring rather than a long tween so
 * logging feels directly connected to the user's press while still avoiding a harsh jump.
 */
@Composable
internal fun AnimatedHydrationOrb(
    fraction: Float,
    percentLabel: String,
    modifier: Modifier = Modifier
) {
    val animatedFill by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = .86f, stiffness = 1050f),
        label = "hydration-fill"
    )
    val transition = rememberInfiniteTransition(label = "hydration-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1900), repeatMode = RepeatMode.Restart),
        label = "hydration-wave-phase"
    )
    val pulse by transition.animateFloat(
        initialValue = .96f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1300), repeatMode = RepeatMode.Reverse),
        label = "hydration-pulse"
    )

    Box(
        modifier.size(126.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
            clipPath(circle) {
                val waterTop = size.height * (1f - animatedFill)
                val amplitude = size.height * .03f
                val wavePath = Path().apply {
                    moveTo(0f, waterTop)
                    val steps = 48
                    for (i in 0..steps) {
                        val x = size.width * i / steps
                        val angle = phase + (i.toFloat() / steps) * (2f * PI).toFloat()
                        lineTo(x, waterTop + sin(angle) * amplitude)
                    }
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    wavePath,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .76f * pulse), Color(0xFFBCEBFA).copy(alpha = .55f)),
                        startY = waterTop,
                        endY = size.height
                    )
                )
                drawOval(
                    color = Color.White.copy(alpha = .14f),
                    topLeft = Offset(size.width * .17f, size.height * .13f),
                    size = Size(size.width * .28f, size.height * .13f)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(percentLabel, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("of goal", color = Color.White.copy(alpha = .76f), fontSize = 8.sp)
        }
    }
}
