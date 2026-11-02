package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Canonical back button based on Nutrition's 11.4 design.
 * Use in all module headers instead of standalone glyphs or top-button variants.
 */
@Composable
internal fun SuperhumanModuleBackButton(onBack: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(SuperhumanLayout.iconTouchTarget)
            .background(superhumanSurface, shape)
            .border(1.dp, superhumanBorder, shape)
            .superhumanClickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.tabler_arrow_left),
            contentDescription = "Back",
            tint = superhumanTextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}
