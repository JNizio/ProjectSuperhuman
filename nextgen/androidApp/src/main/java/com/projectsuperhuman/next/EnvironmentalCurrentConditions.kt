package com.projectsuperhuman.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EnvCurrentNavy = Color(0xFF123D70)
private val EnvCurrentInk = Color(0xFF0B1F35)
private val EnvCurrentBlue = Color(0xFF0D6CB4)
private val EnvCurrentTeal = Color(0xFF168A78)
private val EnvCurrentMuted = Color(0xFF748294)
private val EnvCurrentBorder = Color(0xFFE1E9EF)

@Composable
internal fun EnvironmentalContent(conditions: EnvironmentalConditionsUi) {
    EnvironmentalCurrentConditions(conditions)
    conditions.detailSections().forEach { EnvironmentalDetailSection(it) }
    EnvironmentalWhyCard()
    EnvironmentalSourceCard(conditions)
}

@Composable
private fun EnvironmentalCurrentConditions(conditions: EnvironmentalConditionsUi) {
    val headline = conditions.headlineMetric()
    val feelsLike = conditions.metric(EnvironmentalMetricKind.FEELS_LIKE)
    val shape = RoundedCornerShape(27.dp)

    Column(
        Modifier.fillMaxWidth()
            .testTag("environment_current_conditions")
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFEAF5FB), Color.White, Color(0xFFEAF8F4))
                ),
                shape
            )
            .border(1.dp, EnvCurrentBorder, shape)
            .padding(19.dp)
    ) {
        Text(
            "CURRENT CONDITIONS",
            color = EnvCurrentBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                headline?.let {
                    Text(
                        it.displayValue(),
                        color = EnvCurrentNavy,
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                conditions.weatherLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = EnvCurrentInk, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                }
                feelsLike?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Feels like ${it.displayValue()}", color = EnvCurrentMuted, fontSize = 10.sp)
                }
            }

            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(EnvCurrentBlue.copy(alpha = .08f), radius = size.minDimension * .46f)
                    drawCircle(
                        EnvCurrentTeal.copy(alpha = .28f),
                        radius = size.minDimension * .34f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        EnvCurrentBlue,
                        radius = size.minDimension * .07f,
                        center = Offset(size.width * .62f, size.height * .36f)
                    )
                }
            }
        }

        val context = listOfNotNull(
            conditions.locationLabel?.takeIf { it.isNotBlank() },
            conditions.freshnessLabel?.takeIf { it.isNotBlank() }
        )
        if (context.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                context.joinToString("  •  "),
                color = EnvCurrentMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
