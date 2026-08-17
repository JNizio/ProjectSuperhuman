package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EnvironmentalDetailSection(section: EnvironmentalDetailSectionUi) {
    val palette = superhumanPalette()
    Column(
        Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(24.dp))
            .border(1.dp, palette.border, RoundedCornerShape(24.dp)).padding(16.dp)
    ) {
        Text(section.title, color = palette.ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(section.subtitle, color = palette.muted, fontSize = 9.sp, lineHeight = 13.sp)
        Spacer(Modifier.height(12.dp))
        section.metrics.chunked(2).forEachIndexed { index, metrics ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { EnvironmentalMetricCard(it, Modifier.weight(1f)) }
                if (metrics.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
