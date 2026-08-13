package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnvHomeNavy = Color(0xFF123D70)
private val EnvHomeInk = Color(0xFF0B1F35)
private val EnvHomeMuted = Color(0xFF748294)
private val EnvHomeBorder = Color(0xFFE1E9EF)

@Composable
internal fun EnvironmentalTileData(conditions: EnvironmentalConditionsUi) {
    val headline = conditions.headlineMetric()
    val feelsLike = conditions.metric(EnvironmentalMetricKind.FEELS_LIKE)
    val supporting = conditions.homeSupportingMetrics()

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                headline?.displayValue() ?: conditions.weatherLabel.orEmpty(),
                color = EnvHomeNavy,
                fontSize = if (headline == null) 21.sp else 29.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            if (headline != null && !conditions.weatherLabel.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    conditions.weatherLabel.orEmpty(),
                    color = EnvHomeInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1
                )
            }
        }

        val secondaryLine = when {
            feelsLike != null -> "Feels like ${feelsLike.displayValue()}"
            !conditions.locationLabel.isNullOrBlank() -> conditions.locationLabel
            !conditions.freshnessLabel.isNullOrBlank() -> conditions.freshnessLabel
            else -> null
        }
        secondaryLine?.let {
            Spacer(Modifier.height(3.dp))
            Text(it, color = EnvHomeMuted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
        }

        if (supporting.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                supporting.forEach { metric ->
                    Row(
                        Modifier.background(Color.White.copy(alpha = .78f), RoundedCornerShape(11.dp))
                            .border(1.dp, EnvHomeBorder.copy(alpha = .8f), RoundedCornerShape(11.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(metric.label, color = EnvHomeMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Text(metric.displayValue(), color = EnvHomeNavy, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EnvironmentalTileStatus(title: String, detail: String) {
    Column {
        Text(title, color = EnvHomeNavy, fontSize = 19.sp, fontWeight = FontWeight.Black, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = EnvHomeMuted, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

internal fun environmentalTileDescription(state: EnvironmentalRenderState): String =
    when (state) {
        EnvironmentalRenderState.Loading -> "Environment. Loading local conditions."
        is EnvironmentalRenderState.Ready -> when (val result = state.result) {
            is EnvironmentalLoadResult.Data -> {
                val conditions = result.conditions
                listOfNotNull(
                    "Environment",
                    conditions.headlineMetric()?.let { "${it.label} ${it.displayValue()}" },
                    conditions.weatherLabel,
                    conditions.locationLabel,
                    conditions.freshnessLabel
                ).joinToString(". ")
            }
            EnvironmentalLoadResult.NoPermission -> "Environment. Location access needed."
            EnvironmentalLoadResult.NoData -> "Environment. No local reading yet."
            is EnvironmentalLoadResult.Error -> "Environment. Conditions unavailable."
        }
    }
