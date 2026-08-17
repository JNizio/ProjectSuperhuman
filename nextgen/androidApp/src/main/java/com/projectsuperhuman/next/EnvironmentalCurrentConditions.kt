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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnvCurrentBlue = Color(0xFF0D6CB4)
private val EnvCurrentTeal = Color(0xFF168A78)

@Composable
internal fun EnvironmentalContent(conditions: EnvironmentalConditionsUi) {
    EnvironmentalWeatherTile(conditions)
    conditions.detailSections().forEach { EnvironmentalDetailSection(it) }
    EnvironmentalWhyCard()
    EnvironmentalSourceCard(conditions)
}

@Composable
private fun EnvironmentalWeatherTile(conditions: EnvironmentalConditionsUi) {
    val headline = conditions.headlineMetric()
    val shape = RoundedCornerShape(29.dp)
    val palette = superhumanPalette
    val dark = SuperhumanAppearance.darkMode
    val blue = if (dark) palette.blue else EnvCurrentBlue
    val teal = if (dark) palette.green else EnvCurrentTeal
    val quickMetrics = listOfNotNull(
        conditions.metric(EnvironmentalMetricKind.FEELS_LIKE),
        conditions.metric(EnvironmentalMetricKind.HUMIDITY),
        conditions.metric(EnvironmentalMetricKind.WIND),
        conditions.metric(EnvironmentalMetricKind.PRECIPITATION)
    ).take(4)

    Column(
        Modifier.fillMaxWidth()
            .testTag("environment_current_conditions")
            .background(
                Brush.linearGradient(
                    listOf(
                        if (dark) palette.accentSoft else Color(0xFFE5F4FF),
                        palette.surface,
                        if (dark) palette.surfaceElevated else Color(0xFFEAF8F4)
                    )
                ),
                shape
            )
            .border(1.dp, palette.border, shape)
            .padding(19.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("LOCAL WEATHER", color = blue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
            conditions.freshnessLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = teal,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(palette.surfaceElevated.copy(alpha = .90f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(210.dp)) {
                headline?.let {
                    Text(it.displayValue(), color = palette.brandText, fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    conditions.weatherLabel?.takeIf { it.isNotBlank() } ?: "Current conditions",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2
                )
                conditions.locationLabel?.takeIf { it.isNotBlank() }?.let { location ->
                    Spacer(Modifier.height(3.dp))
                    Text(location, color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }

            Box(
                Modifier.size(78.dp).background(palette.surfaceElevated.copy(alpha = .90f), RoundedCornerShape(24.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) { Text(weatherGlyph(conditions.weatherLabel), fontSize = 38.sp) }
        }

        if (quickMetrics.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            quickMetrics.chunked(2).forEachIndexed { rowIndex, metrics ->
                if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    metrics.forEach { metric -> WeatherQuickMetric(metric) }
                    if (metrics.size == 1) Spacer(Modifier.width(145.dp))
                }
            }
        }

        val timing = listOfNotNull(
            conditions.observedAtLabel?.takeIf { it.isNotBlank() }?.let { "Observed $it" },
            conditions.providerLabel?.takeIf { it.isNotBlank() }
        )
        if (timing.isNotEmpty()) {
            Spacer(Modifier.height(13.dp))
            Text(timing.joinToString("  •  "), color = palette.textMuted, fontSize = 8.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun WeatherQuickMetric(metric: EnvironmentalMetricUi) {
    val palette = superhumanPalette
    Column(
        Modifier.width(145.dp).background(palette.surfaceElevated.copy(alpha = .90f), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(metric.label.uppercase(), color = palette.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(metric.displayValue(), color = palette.brandText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        metric.supportingText?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = palette.textMuted, fontSize = 7.5.sp, maxLines = 1)
        }
    }
}

private fun weatherGlyph(label: String?): String {
    val text = label.orEmpty().lowercase()
    return when {
        "thunder" in text -> "⛈"
        "snow" in text -> "❄"
        "rain" in text -> "🌧"
        "drizzle" in text -> "🌦"
        "fog" in text -> "🌫"
        "cloud" in text || "overcast" in text -> "☁"
        "clear" in text -> "☀"
        else -> "◌"
    }
}
