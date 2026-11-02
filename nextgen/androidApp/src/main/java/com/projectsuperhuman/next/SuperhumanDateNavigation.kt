package com.projectsuperhuman.next

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared side-to-side date strip, matching the Nutrition diary's 11.4
 * visual language. Never add independent arrow backgrounds or inner padding.
 * The caller owns date bounds and label formatting.
 */
@Composable
internal fun SuperhumanDateNavigation(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCenter: () -> Unit,
    canGoPrevious: Boolean = true
) {
    Box(Modifier.fillMaxWidth().height(50.dp).midnightControlStrip(radius = 18.dp)) {
        Box(
            Modifier.align(Alignment.CenterStart).size(SuperhumanLayout.iconTouchTarget)
                .clickable(enabled = canGoPrevious, onClick = onPrevious),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.tabler_chevron_left),
                contentDescription = "Previous day",
                tint = if (canGoPrevious) superhumanTextPrimary else superhumanTextMuted.copy(alpha = .35f),
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            label,
            color = superhumanTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Center).clickable(onClick = onCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Box(
            Modifier.align(Alignment.CenterEnd).size(SuperhumanLayout.iconTouchTarget)
                .clickable(enabled = canGoNext, onClick = onNext),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.tabler_chevron_right),
                contentDescription = "Next day",
                tint = if (canGoNext) superhumanTextPrimary else superhumanTextMuted.copy(alpha = .35f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
