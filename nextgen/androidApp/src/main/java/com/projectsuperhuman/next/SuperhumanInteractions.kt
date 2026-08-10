package com.projectsuperhuman.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
 * Global top-of-screen control style used for back, profile, settings and equivalent header actions.
 * Keeps every module visually consistent: 44dp white rounded tile, no grey ripple, subtle press scale.
 */
internal fun Modifier.superhumanTopButton(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this
    .width(44.dp)
    .height(44.dp)
    .background(Color.White, RoundedCornerShape(15.dp))
    .superhumanClickable(enabled = enabled, onClick = onClick)
