package com.projectsuperhuman.next

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Domain-specific pictograms used only when Project Superhuman does not already
 * have an established app-wide icon for the concept.
 *
 * The implementation is deliberately hidden behind this wrapper so UI code does
 * not depend on a third-party icon library directly.
 */
internal enum class SuperhumanDomainGlyph {
    RUNNING,
    WALKING,
    CYCLING,
    ROWING,
    SWIMMING,
    STAIRS,
    JUMP_ROPE,
    TROPHY,
    ROUTE,
    TREND,
    HEARTBEAT,
    MORE
}

@Composable
internal fun SuperhumanDomainIcon(
    glyph: SuperhumanDomainGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Icon(
        painter = painterResource(id = glyph.drawableRes()),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

@DrawableRes
private fun SuperhumanDomainGlyph.drawableRes(): Int = when (this) {
    SuperhumanDomainGlyph.RUNNING -> R.drawable.tabler_run
    SuperhumanDomainGlyph.WALKING -> R.drawable.tabler_walk
    SuperhumanDomainGlyph.CYCLING -> R.drawable.tabler_bike
    // Tabler does not currently ship a dedicated indoor-rower glyph; kayak is
    // the closest rowing-specific pictogram and remains labelled "Row" in UI.
    SuperhumanDomainGlyph.ROWING -> R.drawable.tabler_kayak
    SuperhumanDomainGlyph.SWIMMING -> R.drawable.tabler_swimming
    SuperhumanDomainGlyph.STAIRS -> R.drawable.tabler_stairs
    SuperhumanDomainGlyph.JUMP_ROPE -> R.drawable.tabler_jump_rope
    SuperhumanDomainGlyph.TROPHY -> R.drawable.tabler_trophy
    SuperhumanDomainGlyph.ROUTE -> R.drawable.tabler_route
    SuperhumanDomainGlyph.TREND -> R.drawable.tabler_chart_line
    SuperhumanDomainGlyph.HEARTBEAT -> R.drawable.tabler_activity_heartbeat
    SuperhumanDomainGlyph.MORE -> R.drawable.tabler_dots
}

internal fun cardioDomainGlyph(activity: CardioActivityType): SuperhumanDomainGlyph = when (activity) {
    CardioActivityType.WALKING,
    CardioActivityType.HIKING -> SuperhumanDomainGlyph.WALKING

    CardioActivityType.RUNNING,
    CardioActivityType.TREADMILL -> SuperhumanDomainGlyph.RUNNING

    CardioActivityType.CYCLING,
    CardioActivityType.STATIONARY_BIKE -> SuperhumanDomainGlyph.CYCLING

    CardioActivityType.ROWING -> SuperhumanDomainGlyph.ROWING
    CardioActivityType.SWIMMING -> SuperhumanDomainGlyph.SWIMMING
    CardioActivityType.STAIR_CLIMBER -> SuperhumanDomainGlyph.STAIRS
    CardioActivityType.JUMP_ROPE -> SuperhumanDomainGlyph.JUMP_ROPE

    CardioActivityType.ELLIPTICAL,
    CardioActivityType.HIIT,
    CardioActivityType.GENERAL_CARDIO -> SuperhumanDomainGlyph.HEARTBEAT

    CardioActivityType.CUSTOM -> SuperhumanDomainGlyph.MORE
}
