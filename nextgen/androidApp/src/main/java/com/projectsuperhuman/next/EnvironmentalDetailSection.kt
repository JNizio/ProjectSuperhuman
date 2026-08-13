package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EnvironmentalDetailSection(section: EnvironmentalDetailSectionUi) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFE1E9EF), RoundedCornerShape(24.dp)).padding(16.dp)
    ) {
        Text(section.title, color = Color(0xFF123D70), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(section.subtitle, color = Color(0xFF748294), fontSize = 9.sp, lineHeight = 13.sp)
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
