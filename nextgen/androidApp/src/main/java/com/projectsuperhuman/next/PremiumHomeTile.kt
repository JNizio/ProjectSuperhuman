package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared, restrained home surface. Keeping radius, optical edge and matte finish together
 * prevents tiles drifting into unrelated card styles. Navigation uses the whole card's
 * touch target; no tiny decorative icon is required to reach a module.
 */
internal fun Modifier.premiumHomeTile(onClick: () -> Unit): Modifier {
    val dark = SuperhumanAppearance.darkMode
    val shape = RoundedCornerShape(SuperhumanLayout.homeCardRadius)
    val surface = if (dark) {
        listOf(Color(0xFF192D3B), Color(0xFF10212E))
    } else {
        listOf(Color(0xFFFFFFFF), Color(0xFFF3F7FA))
    }
    val edge = if (dark) {
        listOf(Color.White.copy(alpha = .19f), Color(0xFF7896A9).copy(alpha = .10f))
    } else {
        listOf(Color.White.copy(alpha = .95f), superhumanBorder.copy(alpha = .80f))
    }
    return this
        .shadow(3.dp, shape, clip = false)
        .clip(shape)
        .background(Brush.linearGradient(surface))
        .border(width = 1.dp, brush = Brush.verticalGradient(edge), shape = shape)
        .superhumanClickable(onClick = onClick)
}
