package com.projectsuperhuman.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Global no-ripple indication. Project Superhuman owns its interaction feedback and never uses
 * Android/Material press ripples or rectangular touch highlights.
 *
 * Compose 1.7+ indication implementations use IndicationNodeFactory. A plain Modifier.Node draws
 * nothing, so content renders normally while default ripple/highlight feedback is suppressed.
 */
internal object SuperhumanNoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x53555045
}

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
 * Home-tile click target: no Android ripple and no custom rectangular overlay.
 * Keep semantics, accessibility and onClick intact, without animating the tile.
 */
internal fun Modifier.superhumanHomeTileClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.clickable(
    enabled = enabled,
    interactionSource = null,
    indication = null,
    onClick = onClick
)

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
