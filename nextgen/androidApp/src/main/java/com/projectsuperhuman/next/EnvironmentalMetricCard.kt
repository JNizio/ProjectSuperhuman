package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EnvironmentalMetricCard(metric: EnvironmentalMetricUi, modifier: Modifier) {
    val palette = superhumanPalette
    Column(
        modifier.background(palette.surfaceElevated, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(metric.label.uppercase(), color = palette.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(metric.displayValue(), color = palette.brandText, fontSize = 17.sp, fontWeight = FontWeight.Black)
        metric.supportingText?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(3.dp))
            Text(it, color = palette.textMuted, fontSize = 8.sp, maxLines = 2)
        }
    }
}
