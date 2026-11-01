package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Exactly the same surface as a Home dashboard card; no second, brighter
 * gradient, stronger outline or extra tint on internal module cards.
 */
internal fun Modifier.midnightModuleCard(
    accent: Color = superhumanAccent,
    radius: Dp = SuperhumanLayout.homeCardRadius
): Modifier = midnightCardSurface(accent = accent, radius = radius)

/** Low-contrast interior content, deliberately not a second elevated card. */
internal fun Modifier.midnightInsetPanel(
    radius: Dp = 20.dp
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(
            if (SuperhumanAppearance.darkMode) superhumanSurface.copy(alpha = .34f)
            else superhumanSurfaceSoft,
            shape
        )
        .border(
            1.dp,
            if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .022f)
            else superhumanBorder.copy(alpha = .5f),
            shape
        )
}

/** Navigation-like strips are recessive, unlike the primary metric cards. */
internal fun Modifier.midnightControlStrip(
    radius: Dp = 18.dp
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(superhumanSurface, shape)
        .border(
            1.dp,
            if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = .028f)
            else superhumanBorder,
            shape
        )
}

/** Low-chroma selection that does not pull focus away from the metrics. */
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
    if (selected && SuperhumanAppearance.darkMode) Color.White.copy(alpha = .03f)
    else if (selected) superhumanBorder
    else Color.Transparent
