package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The real Home tile material, shared verbatim by the dashboard and detail modules.
 * A restrained neutral gradient and optical border; no coloured card fills.
 */
internal fun Modifier.midnightCardSurface(
    accent: Color,
    radius: Dp = SuperhumanLayout.homeCardRadius
): Modifier {
    val dark = SuperhumanAppearance.darkMode
    val shape = RoundedCornerShape(radius)
    val neutral = if (dark) {
        listOf(superhumanSurfaceElevated, superhumanSurface)
    } else {
        listOf(Color(0xFFFDFEFF), Color(0xFFF5F8FA))
    }
    val edge = if (dark) {
        listOf(Color.White.copy(alpha = .055f), Color.White.copy(alpha = .020f))
    } else {
        listOf(Color(0xFFB1C4D1).copy(alpha = .52f), Color(0xFFB1C4D1).copy(alpha = .22f))
    }
    return this
        .shadow(if (dark) 1.dp else 2.dp, shape, clip = false)
        .clip(shape)
        .background(Brush.linearGradient(neutral))
        .background(
            Brush.linearGradient(
                listOf(
                    accent.copy(alpha = if (dark) .030f else .055f),
                    Color.Transparent,
                    Color.Transparent
                )
            )
        )
        .border(1.dp, Brush.verticalGradient(edge), shape)
}

internal fun Modifier.premiumHomeTile(
    onClick: () -> Unit,
    accent: Color = Color(0xFF84BBDC)
): Modifier = midnightCardSurface(accent).superhumanClickable(onClick = onClick)
