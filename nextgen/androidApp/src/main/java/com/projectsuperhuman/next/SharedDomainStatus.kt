package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain

@Composable
internal fun SharedDomainStatus(domain: HealthDomain) {
    var summary by remember(domain) { mutableStateOf("Reading shared store…") }
    LaunchedEffect(domain) {
        val current = NativeModuleParity.currentState(domain)
        summary = if (current.latestByMetric.isEmpty()) {
            "No native records yet. New migrated entries will appear here automatically."
        } else {
            val latest = current.latestByMetric.maxByOrNull { it.timestampEpochMs }
            val latestText = latest?.let { " Latest: ${it.metric} ${formatValue(it.value)} ${it.unit}." } ?: ""
            "${current.latestByMetric.size} latest metrics available from the shared Data Vault.$latestText"
        }
    }
    Column(
        Modifier.fillMaxWidth().background(superhumanAccentSoft, RoundedCornerShape(17.dp)).padding(14.dp)
    ) {
        Text("SHARED DATA · LIVE", color = superhumanBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(4.dp))
        Text(summary, color = superhumanTextPrimary, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

private fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.2f", value)
