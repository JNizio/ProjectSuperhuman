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
internal fun EnvironmentalSourceCard(conditions: EnvironmentalConditionsUi) {
    val rows = listOfNotNull(
        conditions.locationLabel?.takeIf { it.isNotBlank() }?.let { "Location" to it },
        conditions.observedAtLabel?.takeIf { it.isNotBlank() }?.let { "Observed" to it },
        conditions.retrievedAtLabel?.takeIf { it.isNotBlank() }?.let { "Retrieved" to it },
        conditions.providerLabel?.takeIf { it.isNotBlank() }?.let { "Source" to it },
        conditions.freshnessLabel?.takeIf { it.isNotBlank() }?.let { "Freshness" to it }
    )
    if (rows.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFFE1E9EF), RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("READING DETAILS", color = Color(0xFF123D70), fontSize = 9.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(9.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(label, color = Color(0xFF748294), fontSize = 9.sp, modifier = Modifier.width(72.dp))
                Text(value, color = Color(0xFF0B1F35), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
            }
        }
    }
}
