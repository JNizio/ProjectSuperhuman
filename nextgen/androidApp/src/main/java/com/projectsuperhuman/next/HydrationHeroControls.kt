package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Hydration's primary interaction surface.
 *
 * Performance rule: slider thumb movement stays continuous locally, while the app state is updated
 * only when the user crosses a 50 ml boundary. Correction controls are deliberately separate from
 * normal logging so accidental taps cannot silently rewrite today's hydration history.
 */
@Composable
internal fun HydrationHeroControls(
    todayMl: Int,
    goalMl: Int,
    lastDrinkMl: Int?,
    lastDrinkLabel: String?,
    source: String,
    amountMl: Int,
    status: String,
    onSourceChange: (String) -> Unit,
    onAmountChange: (Int) -> Unit,
    onQuickLog: (Int) -> Unit,
    onQuickSubtract: (Int) -> Unit,
    onClearToday: () -> Unit,
    onLog: () -> Unit
) {
    val remaining = (goalMl - todayMl).coerceAtLeast(0)
    val currentFraction = todayMl.toFloat() / goalMl.coerceAtLeast(1)
    val projectedTotal = (todayMl + amountMl.coerceAtMost(remaining)).coerceAtMost(goalMl)
    val projectedFraction = projectedTotal.toFloat() / goalMl.coerceAtLeast(1)
    val currentPct = (currentFraction * 100f).roundToInt().coerceIn(0, 100)
    val projectedPct = (projectedFraction * 100f).roundToInt().coerceIn(0, 100)
    val maxAmount = remaining.coerceAtLeast(0)
    val sliderMax = maxAmount.coerceAtLeast(50)

    var dragValue by remember { mutableFloatStateOf(amountMl.toFloat()) }
    LaunchedEffect(amountMl, sliderMax) {
        val target = amountMl.toFloat().coerceIn(0f, sliderMax.toFloat())
        if ((dragValue - target).let { kotlin.math.abs(it) } > 55f || amountMl == 0) dragValue = target
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0864A7), Color(0xFF11A6C3))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY", color = Color.White.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text(if (remaining > 0) "${formatMlHydration(remaining)} remaining" else "Goal reached", color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedHydrationOrb(fraction = projectedFraction, percentLabel = if (amountMl > 0 && remaining > 0) "$projectedPct%" else "$currentPct%")
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(formatMlHydration(todayMl), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("of ${formatMlHydration(goalMl)}", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                if (remaining > 0 && amountMl > 0) {
                    Spacer(Modifier.height(7.dp))
                    Text("Preview +${formatMlHydration(amountMl)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("→ ${formatMlHydration(projectedTotal)} total", color = Color.White.copy(alpha = .72f), fontSize = 9.sp)
                }
            }
        }

        if (remaining > 0) {
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(22.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Water", "Electrolyte", "Other").forEach { option ->
                        val selected = source == option
                        Box(
                            Modifier.background(if (selected) Color.White.copy(alpha = .22f) else Color.White.copy(alpha = .08f), RoundedCornerShape(13.dp))
                                .superhumanClickable { onSourceChange(option) }
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                        ) { Text(option, color = Color.White.copy(alpha = if (selected) 1f else .68f), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(250, 330, 500, 750).forEach { quick ->
                        val enabled = remaining >= quick
                        Box(
                            Modifier.weight(1f).background(Color.White.copy(alpha = if (enabled) .14f else .05f), RoundedCornerShape(13.dp))
                                .superhumanClickable(enabled = enabled) { onQuickLog(quick) }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("+$quick", color = Color.White.copy(alpha = if (enabled) .95f else .35f), fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("DRINK AMOUNT", color = Color.White.copy(alpha = .58f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                        Text(formatMlHydration(amountMl), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Text("max ${formatMlHydration(remaining)}", color = Color.White.copy(alpha = .64f), fontSize = 8.sp)
                }

                Slider(
                    value = dragValue.coerceIn(0f, sliderMax.toFloat()),
                    onValueChange = { raw ->
                        dragValue = raw.coerceIn(0f, sliderMax.toFloat())
                        val snapped = ((raw / 50f).roundToInt() * 50).coerceIn(0, maxAmount)
                        if (snapped != amountMl) onAmountChange(snapped)
                    },
                    onValueChangeFinished = { dragValue = amountMl.toFloat().coerceIn(0f, sliderMax.toFloat()) },
                    valueRange = 0f..sliderMax.toFloat(),
                    steps = 0,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .22f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
                )

                Box(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).superhumanClickable(enabled = amountMl > 0) { onLog() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (amountMl > 0) "LOG ${formatMlHydration(amountMl).uppercase()}" else "SELECT AMOUNT", color = Color(0xFF0B77B5), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .14f), RoundedCornerShape(18.dp)).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                Text("DAILY GOAL COMPLETE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        if (todayMl > 0) {
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("CORRECT TODAY", color = Color.White.copy(alpha = .62f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                    Box(Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(11.dp)).superhumanClickable { onClearToday() }.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("CLEAR", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(250, 330, 500, 750).forEach { quick ->
                        val enabled = todayMl > 0
                        val actual = minOf(quick, todayMl)
                        Box(
                            Modifier.weight(1f).background(Color.White.copy(alpha = .10f), RoundedCornerShape(13.dp))
                                .superhumanClickable(enabled = enabled) { onQuickSubtract(actual) }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("−$actual", color = Color.White.copy(alpha = .90f), fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }

        if (lastDrinkLabel != null) Text("Last drink ${lastDrinkMl ?: 0} ml · $lastDrinkLabel", color = Color.White.copy(alpha = .70f), fontSize = 8.sp)
        if (status.isNotBlank()) Text(status, color = Color.White.copy(alpha = .92f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun formatMlHydration(ml: Int): String = if (ml >= 1000) {
    val litres = ml / 1000.0
    if (ml % 1000 == 0) "${litres.roundToInt()} L" else "%.1f L".format(java.util.Locale.ENGLISH, litres)
} else "$ml ml"
