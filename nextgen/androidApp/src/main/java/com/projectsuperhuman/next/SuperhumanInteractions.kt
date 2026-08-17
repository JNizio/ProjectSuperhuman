package com.projectsuperhuman.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Shared Project Superhuman press behaviour without Android's grey ripple/highlight. */
internal fun Modifier.superhumanClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "superhuman-press"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Home dashboard cards deliberately do not scale when pressed. A restrained neutral tint gives
 * immediate feedback while keeping imagery, borders and card geometry perfectly stationary.
 */
internal fun Modifier.superhumanHomeTileClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val overlayAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) if (SuperhumanAppearance.darkMode) 0.12f else 0.075f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 90 else 170),
        label = "superhuman-home-highlight"
    )

    this
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
        .drawWithContent {
            drawContent()
            if (overlayAlpha > 0f) {
                drawRect(if (SuperhumanAppearance.darkMode) Color.White.copy(alpha = overlayAlpha) else Color(0xFF64748B).copy(alpha = overlayAlpha))
            }
        }
}

/**
 * Global top-of-screen control style used for back, profile, settings and equivalent header actions.
 * The surface and outline follow the persistent app appearance while preserving the same geometry,
 * no-ripple interaction and restrained press animation.
 */
internal fun Modifier.superhumanTopButton(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val shape = RoundedCornerShape(15.dp)
    return this
        .width(44.dp)
        .height(44.dp)
        .background(superhumanSurface, shape)
        .border(1.dp, superhumanBorder, shape)
        .superhumanClickable(enabled = enabled, onClick = onClick)
}
