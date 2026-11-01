package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified Midnight material for native detail modules.
 *
 * Mirrors the matte charcoal home dashboard without painting whole cards in
 * their domain accent. Accent stays a quiet corner wash; information carries
 * the actual colour. Intentionally presentation-only: no domain or data logic.
 */
internal fun Modifier.midnightModuleCard(
    accent: Color = superhumanAccent,
    radius: Dp = SuperhumanLayout.homeCardRadius
): Modifier {
    val shape = RoundedCornerShape(radius)
    val dark = SuperhumanAppearance.darkMode
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                if (dark) listOf(superhumanSurfaceElevated, superhumanSurface)
                else listOf(Color.White, superhumanSurfaceElevated)
            )
        )
        .background(
            Brush.linearGradient(
                listOf(accent.copy(alpha = if (dark) .035f else .045f), Color.Transparent, Color.Transparent)
            )
        )
        .border(
            1.dp,
            if (dark) Color.White.copy(alpha = .085f) else superhumanBorder,
            shape
        )
}

/** Subdued selected tabs instead of the old saturated blue solid fills. */
internal fun midnightTabColor(selected: Boolean, accent: Color): Color = when {
    !selected -> Color.Transparent
    SuperhumanAppearance.darkMode -> superhumanSurfaceSoft
    else -> accent.copy(alpha = .12f)
}

internal fun midnightTabTextColor(selected: Boolean, accent: Color): Color = when {
    selected && SuperhumanAppearance.darkMode -> superhumanTextPrimary
    selected -> accent
    else -> superhumanTextMuted
}

internal fun midnightTabOutline(selected: Boolean): Color =
    if (selected) superhumanBorder else Color.Transparent
