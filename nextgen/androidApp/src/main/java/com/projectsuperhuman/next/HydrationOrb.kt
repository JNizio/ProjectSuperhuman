package com.projectsuperhuman.next

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
 * Hydration-specific visual primitive.
 *
 * Performance rule: the fill itself follows input immediately. We intentionally do not spring/tween
 * the fill because the orb is also an input preview and animation latency makes slider dragging feel
 * disconnected. Only the lightweight surface wave animates continuously.
 */
@Composable
internal fun AnimatedHydrationOrb(
    fraction: Float,
    percentLabel: String,
    modifier: Modifier = Modifier
) {
    val fill = fraction.coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "hydration-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2400), repeatMode = RepeatMode.Restart),
        label = "hydration-wave-phase"
    )

    Box(
        modifier.size(126.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
            clipPath(circle) {
                val waterTop = size.height * (1f - fill)
                val amplitude = size.height * .026f
                val wavePath = Path().apply {
                    moveTo(0f, waterTop)
                    val steps = 24
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
                        listOf(Color.White.copy(alpha = .70f), Color(0xFFBCEBFA).copy(alpha = .50f)),
                        startY = waterTop,
                        endY = size.height
                    )
                )
                drawOval(
                    color = Color.White.copy(alpha = .11f),
                    topLeft = Offset(size.width * .17f, size.height * .13f),
                    size = Size(size.width * .28f, size.height * .13f)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                percentLabel,
                color = Color(0xFFF2FAFE).copy(alpha = .92f),
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "of goal",
                color = Color(0xFFE4F4FB).copy(alpha = .68f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
