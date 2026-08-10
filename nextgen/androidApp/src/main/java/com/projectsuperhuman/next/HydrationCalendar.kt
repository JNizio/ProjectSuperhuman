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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Read-only calendar over persisted water_intake_ml events.
 *
 * The database remains the source of truth; this component only renders daily totals. Keeping the
 * calendar separate from persistence makes it safe to expand later with month navigation or
 * historical editing without bloating NativeHydration.kt.
 */
@Composable
internal fun HydrationCalendarCard(
    dailyTotals: Map<LocalDate, Int>,
    goalMl: Int
) {
    val month = YearMonth.now()
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val padded = cells + List((7 - cells.size % 7) % 7) { null }

    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFFE3EAF0), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Water history", color = Color(0xFF16334E), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
                    color = Color(0xFF748294), fontSize = 9.sp
                )
            }
            Text(
                formatMlHydration(dailyTotals[selectedDate] ?: 0),
                color = Color(0xFF0D6CB4), fontSize = 13.sp, fontWeight = FontWeight.Black
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, color = Color(0xFF9AA6B3), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(42.dp))
                    } else {
                        val ml = dailyTotals[date] ?: 0
                        val fraction = (ml.toFloat() / goalMl.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val selected = date == selectedDate
                        val isToday = date == today
                        Box(
                            Modifier.weight(1f).height(42.dp)
                                .background(
                                    when {
                                        selected -> Color(0xFFDDF1FA)
                                        ml > 0 -> Color(0xFFF2F9FC)
                                        else -> Color.Transparent
                                    }, RoundedCornerShape(11.dp)
                                )
                                .then(
                                    if (isToday) Modifier.border(1.dp, Color(0xFF55A8CF), RoundedCornerShape(11.dp)) else Modifier
                                )
                                .superhumanClickable { selectedDate = date },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfMonth.toString(), color = Color(0xFF29445D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                if (ml > 0) {
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        Modifier.fillMaxWidth(fraction.coerceAtLeast(.14f)).height(3.dp)
                                            .background(Color(0xFF5FB4D7), RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · ${formatMlHydration(dailyTotals[selectedDate] ?: 0)} logged",
            color = Color(0xFF748294), fontSize = 9.sp
        )
    }
}
