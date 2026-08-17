package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

private val EnvHomeTeal = Color(0xFF168A78)

@Composable
internal fun EnvironmentalTileData(conditions: EnvironmentalConditionsUi) {
    val palette = superhumanPalette
    val dark = SuperhumanAppearance.darkMode
    val headline = conditions.headlineMetric()
    val feelsLike = conditions.metric(EnvironmentalMetricKind.FEELS_LIKE)
    val supporting = listOf(
        EnvironmentalMetricKind.HUMIDITY,
        EnvironmentalMetricKind.WIND,
        EnvironmentalMetricKind.AIR_QUALITY,
        EnvironmentalMetricKind.UV,
        EnvironmentalMetricKind.PRECIPITATION
    ).mapNotNull(conditions::metric).take(3)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxWidth(.72f)) {
                conditions.locationLabel?.takeIf { it.isNotBlank() }?.let { place ->
                    Text(place.uppercase(), color = if (dark) palette.green else EnvHomeTeal, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .75.sp, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(headline?.displayValue() ?: "—", color = palette.brandText, fontSize = 33.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    conditions.weatherLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        Spacer(Modifier.width(9.dp))
                        Text(label, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
                    }
                }

                val secondary = listOfNotNull(
                    feelsLike?.let { "Feels ${it.displayValue()}" },
                    conditions.freshnessLabel?.takeIf { it.isNotBlank() }
                )
                if (secondary.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(secondary.joinToString("  •  "), color = palette.textMuted, fontSize = 8.5.sp, maxLines = 1)
                }
            }

            Box(
                Modifier.size(58.dp).background(palette.surfaceElevated.copy(alpha = .90f), RoundedCornerShape(18.dp)).border(1.dp, palette.border, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { Text(environmentHomeGlyph(conditions.weatherLabel), fontSize = 28.sp) }
        }

        if (supporting.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                supporting.forEach { metric -> EnvironmentalHomeMetricChip(metric) }
            }
        }
    }
}

@Composable
private fun EnvironmentalHomeMetricChip(metric: EnvironmentalMetricUi) {
    val palette = superhumanPalette
    Column(
        Modifier.width(88.dp).background(palette.surfaceElevated.copy(alpha = .90f), RoundedCornerShape(13.dp))
            .border(1.dp, palette.border.copy(alpha = .8f), RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(compactEnvironmentLabel(metric), color = palette.textMuted, fontSize = 6.8.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(metric.displayValue(), color = palette.brandText, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun compactEnvironmentLabel(metric: EnvironmentalMetricUi): String = when (metric.kind) {
    EnvironmentalMetricKind.HUMIDITY -> "HUMIDITY"
    EnvironmentalMetricKind.WIND -> "WIND"
    EnvironmentalMetricKind.AIR_QUALITY -> "AIR QUALITY"
    EnvironmentalMetricKind.UV -> "UV INDEX"
    EnvironmentalMetricKind.PRECIPITATION -> "RAIN"
    else -> metric.label.uppercase()
}

private fun environmentHomeGlyph(label: String?): String {
    val text = label.orEmpty().lowercase()
    return when {
        "thunder" in text -> "⛈"
        "snow" in text -> "❄"
        "rain" in text -> "🌧"
        "drizzle" in text -> "🌦"
        "fog" in text || "mist" in text -> "🌫"
        "overcast" in text -> "☁"
        "cloud" in text -> "⛅"
        "clear" in text || "sun" in text -> "☀"
        else -> "◌"
    }
}

@Composable
internal fun EnvironmentalTileStatus(title: String, detail: String) {
    val palette = superhumanPalette
    Column {
        Text(title, color = palette.brandText, fontSize = 19.sp, fontWeight = FontWeight.Black, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = palette.textMuted, fontSize = 9.sp, lineHeight = 12.sp)
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
