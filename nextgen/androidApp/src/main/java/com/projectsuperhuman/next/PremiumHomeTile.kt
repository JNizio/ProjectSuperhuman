package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A genuinely flat Home material. Solid neutral fills, no gradient, shadow or
 * tinted edge; accent colours are reserved for icons, charts and progress.
 * Keep the accent argument for the existing tile call sites.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.midnightCardSurface(
    accent: Color,
    radius: Dp = SuperhumanLayout.homeCardRadius
): Modifier {
    val dark = SuperhumanAppearance.darkMode
    val shape = RoundedCornerShape(radius)
    val base = this.clip(shape).background(superhumanSurface)
    return if (dark) base else base.border(
        1.dp,
        superhumanBorder.copy(alpha = .55f),
        shape
    )
}

internal fun Modifier.premiumHomeTile(
    onClick: () -> Unit,
    accent: Color = Color(0xFF84BBDC)
): Modifier = midnightCardSurface(accent).superhumanClickable(onClick = onClick)
