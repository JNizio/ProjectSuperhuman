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

private val EnvCurrentNavy = Color(0xFF123D70)
private val EnvCurrentInk = Color(0xFF0B1F35)
private val EnvCurrentBlue = Color(0xFF0D6CB4)
private val EnvCurrentTeal = Color(0xFF168A78)
private val EnvCurrentMuted = Color(0xFF748294)
private val EnvCurrentBorder = Color(0xFFDCE8F0)

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
                    listOf(Color(0xFFE5F4FF), Color(0xFFF8FCFF), Color(0xFFEAF8F4))
                ),
                shape
            )
            .border(1.dp, EnvCurrentBorder, shape)
            .padding(19.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LOCAL WEATHER",
                color = EnvCurrentBlue,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.15.sp
            )
            conditions.freshnessLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = EnvCurrentTeal,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.White.copy(alpha = .8f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(13.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(210.dp)) {
                headline?.let {
                    Text(
                        it.displayValue(),
                        color = EnvCurrentNavy,
                        fontSize = 44.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    conditions.weatherLabel?.takeIf { it.isNotBlank() } ?: "Current conditions",
                    color = EnvCurrentInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2
                )
                val location = conditions.locationLabel?.takeIf { it.isNotBlank() }
                if (location != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(location, color = EnvCurrentMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }

            Box(
                Modifier.size(78.dp)
                    .background(Color.White.copy(alpha = .78f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(weatherGlyph(conditions.weatherLabel), fontSize = 38.sp)
            }
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
            Text(
                timing.joinToString("  •  "),
                color = EnvCurrentMuted,
                fontSize = 8.sp,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun WeatherQuickMetric(metric: EnvironmentalMetricUi) {
    Column(
        Modifier.width(145.dp)
            .background(Color.White.copy(alpha = .78f), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(metric.label.uppercase(), color = EnvCurrentMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(metric.displayValue(), color = EnvCurrentNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        metric.supportingText?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = EnvCurrentMuted, fontSize = 7.5.sp, maxLines = 1)
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
