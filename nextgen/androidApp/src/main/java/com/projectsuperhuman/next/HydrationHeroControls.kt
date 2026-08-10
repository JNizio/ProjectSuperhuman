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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Water-only primary interaction surface.
 *
 * Amount always starts at zero and the orb shows committed database state only. The slider and
 * presets select the next mutation; they never preview a fake future fill. Electrolytes live in
 * Nutrition, keeping hydration semantically clean and easy for future sessions to reason about.
 */
@Composable
internal fun HydrationHeroControls(
    todayMl: Int,
    goalMl: Int,
    lastDrinkMl: Int?,
    lastDrinkLabel: String?,
    amountMl: Int,
    status: String,
    onAmountChange: (Int) -> Unit,
    onQuickSubtract: (Int) -> Unit,
    onClearToday: () -> Unit,
    onLog: () -> Unit
) {
    val remaining = (goalMl - todayMl).coerceAtLeast(0)
    var correcting by remember { mutableStateOf(false) }
    val maxAmount = if (correcting) todayMl.coerceAtLeast(0) else remaining
    val sliderMax = maxAmount.coerceAtLeast(50)
    val selected = amountMl.coerceAtMost(maxAmount)
    val currentFraction = todayMl.toFloat() / goalMl.coerceAtLeast(1)
    val currentPct = (currentFraction * 100f).roundToInt().coerceIn(0, 100)

    var dragValue by remember { mutableFloatStateOf(selected.toFloat()) }
    LaunchedEffect(selected, sliderMax, correcting) {
        val target = selected.toFloat().coerceIn(0f, sliderMax.toFloat())
        if (abs(dragValue - target) > 55f || selected == 0) dragValue = target
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0864A7), Color(0xFF11A6C3))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("WATER TODAY", color = Color.White.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text(
                if (remaining > 0) "${formatMlHydration(remaining)} remaining" else "Goal reached",
                color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedHydrationOrb(fraction = currentFraction, percentLabel = "$currentPct%")
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(formatMlHydration(todayMl), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("of ${formatMlHydration(goalMl)}", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                Spacer(Modifier.height(7.dp))
                Text("Water only", color = Color.White.copy(alpha = .64f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (todayMl > 0 || remaining > 0) {
            Column(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(22.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White.copy(alpha = .08f), RoundedCornerShape(15.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to "ADD", true to "CORRECT").forEach { (mode, label) ->
                        val active = correcting == mode
                        Box(
                            Modifier.weight(1f)
                                .background(if (active) Color.White.copy(alpha = .20f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .superhumanClickable(enabled = !active) {
                                    correcting = mode
                                    onAmountChange(0)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White.copy(alpha = if (active) .96f else .55f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(
                            if (correcting) "CORRECTION AMOUNT" else "WATER AMOUNT",
                            color = Color.White.copy(alpha = .56f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp
                        )
                        Text(formatMlHydration(selected), color = Color.White.copy(alpha = .96f), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Text("max ${formatMlHydration(maxAmount)}", color = Color.White.copy(alpha = .60f), fontSize = 8.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250, 500, 750).forEach { preset ->
                        val enabled = maxAmount >= preset
                        val active = selected == preset
                        Box(
                            Modifier.weight(1f)
                                .background(
                                    when {
                                        active -> Color.White.copy(alpha = .22f)
                                        enabled -> Color.White.copy(alpha = .09f)
                                        else -> Color.White.copy(alpha = .04f)
                                    }, RoundedCornerShape(13.dp)
                                )
                                .superhumanClickable(enabled = enabled) { onAmountChange(preset) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$preset", color = Color.White.copy(alpha = if (enabled) .90f else .30f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Slider(
                    value = dragValue.coerceIn(0f, sliderMax.toFloat()),
                    onValueChange = { raw ->
                        dragValue = raw.coerceIn(0f, sliderMax.toFloat())
                        val snapped = ((raw / 50f).roundToInt() * 50).coerceIn(0, maxAmount)
                        if (snapped != selected) onAmountChange(snapped)
                    },
                    onValueChangeFinished = { dragValue = selected.toFloat().coerceIn(0f, sliderMax.toFloat()) },
                    valueRange = 0f..sliderMax.toFloat(),
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White.copy(alpha = .95f),
                        activeTrackColor = Color.White.copy(alpha = .92f),
                        inactiveTrackColor = Color.White.copy(alpha = .20f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )

                val actionEnabled = selected > 0 && maxAmount > 0
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color.White.copy(alpha = if (actionEnabled) .96f else .35f), RoundedCornerShape(16.dp))
                        .superhumanClickable(enabled = actionEnabled) {
                            if (correcting) onQuickSubtract(selected) else onLog()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            !actionEnabled -> if (correcting) "SELECT CORRECTION" else "SELECT WATER"
                            correcting -> "REMOVE ${formatMlHydration(selected).uppercase()}"
                            else -> "ADD ${formatMlHydration(selected).uppercase()}"
                        },
                        color = Color(0xFF0B77B5), fontSize = 10.sp, fontWeight = FontWeight.Black
                    )
                }

                if (correcting && todayMl > 0) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).superhumanClickable { onClearToday() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("Clear today", color = Color.White.copy(alpha = .58f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (lastDrinkLabel != null) {
            Text("Last water ${lastDrinkMl ?: 0} ml · $lastDrinkLabel", color = Color.White.copy(alpha = .66f), fontSize = 8.sp)
        }
        if (status.isNotBlank()) {
            Text(status, color = Color.White.copy(alpha = .88f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun formatMlHydration(ml: Int): String = if (ml >= 1000) {
    val litres = ml / 1000.0
    if (ml % 1000 == 0) "${litres.roundToInt()} L" else "%.1f L".format(java.util.Locale.ENGLISH, litres)
} else "$ml ml"
