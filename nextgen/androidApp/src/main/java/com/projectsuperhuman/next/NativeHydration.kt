package com.projectsuperhuman.next

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Native water hydration orchestration and persistence.
 *
 * Source of truth is the shared SQLDelight-backed NativeDataHub. Every water change is persisted as
 * a signed water_intake_ml event, so daily totals, corrections, 7-day analytics and the monthly
 * calendar all derive from the same durable history. Electrolytes intentionally belong to Nutrition.
 */
private val HydNavy = Color(0xFF123D70)
private val HydBlue = Color(0xFF0D6CB4)
private val HydInk = Color(0xFF16334E)
private val HydMuted = Color(0xFF748294)
private val HydBg = Color(0xFFF8FBFD)
private val HydBorder = Color(0xFFE3EAF0)
private val HydGreen = Color(0xFF4AAE91)

private data class HydrationDay(val date: LocalDate, val ml: Int)
private data class HydrationSnapshot(
    val todayMl: Int = 0,
    val goalMl: Int = 3600,
    val lastDrinkMl: Int? = null,
    val lastDrinkEpochMs: Long? = null,
    val history: List<HydrationDay> = emptyList(),
    val calendarTotals: Map<LocalDate, Int> = emptyMap()
) {
    val remainingMl: Int get() = (goalMl - todayMl).coerceAtLeast(0)
    val sevenDayAverage: Int get() = if (history.isEmpty()) 0 else history.map { it.ml }.average().roundToInt()
    val streak: Int get() {
        var count = 0
        history.asReversed().forEach { day -> if (day.ml >= goalMl) count++ else return count }
        return count
    }
}

@Composable
internal fun NativeHydrationScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(HydrationSnapshot()) }
    var goalDraft by remember { mutableStateOf(3600f) }
    var amountSlider by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }

    suspend fun refresh() {
        snapshot = loadHydrationSnapshot()
        goalDraft = snapshot.goalMl.toFloat()
        amountSlider = 0
    }

    LaunchedEffect(Unit) { refresh() }

    fun applyHydrationDelta(requestedDeltaMl: Int) {
        if (requestedDeltaMl == 0) return

        val delta = if (requestedDeltaMl > 0) {
            minOf(requestedDeltaMl, snapshot.remainingMl)
        } else {
            -minOf(-requestedDeltaMl, snapshot.todayMl)
        }
        if (delta == 0) {
            status = if (requestedDeltaMl > 0) "Daily goal already reached" else "Nothing to subtract"
            return
        }

        val now = System.currentTimeMillis()
        val previous = snapshot
        val newTotal = (snapshot.todayMl + delta).coerceIn(0, snapshot.goalMl)
        snapshot = snapshot.copy(
            todayMl = newTotal,
            lastDrinkMl = if (delta > 0) delta else snapshot.lastDrinkMl,
            lastDrinkEpochMs = if (delta > 0) now else snapshot.lastDrinkEpochMs,
            calendarTotals = snapshot.calendarTotals + (LocalDate.now() to newTotal)
        )
        amountSlider = 0
        status = if (delta > 0) "+$delta ml water logged" else "${delta} ml corrected"

        scope.launch {
            runCatching {
                NativeDataHub.saveMetric(
                    domain = HealthDomain.HYDRATION,
                    metric = "water_intake_ml",
                    value = delta.toDouble(),
                    unit = "ml",
                    source = "native-hydration",
                    metadata = mapOf(
                        "drinkSource" to "Water",
                        "entryType" to if (delta > 0) "intake" else "correction"
                    )
                )
                val zone = ZoneId.systemDefault()
                val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                val total = NativeDataHub.between("water_intake_ml", start, now + 1500).sumOf { it.value }.coerceAtLeast(0.0)
                NativeDataHub.saveMetric(
                    domain = HealthDomain.HYDRATION,
                    metric = "water_total_l",
                    value = (total / 1000.0).coerceAtMost(snapshot.goalMl / 1000.0),
                    unit = "L",
                    source = "native-hydration",
                    metadata = mapOf("compatibilitySnapshot" to "true")
                )
            }.onSuccess { refresh() }
                .onFailure {
                    snapshot = previous
                    amountSlider = 0
                    status = "Couldn’t save that change — nothing was changed"
                }
        }
    }

    fun logDrink(requestedMl: Int) = applyHydrationDelta(requestedMl)
    fun subtractDrink(requestedMl: Int) = applyHydrationDelta(-requestedMl)
    fun clearToday() = applyHydrationDelta(-snapshot.todayMl)

    fun saveGoal() {
        val ml = (goalDraft / 100f).roundToInt() * 100
        scope.launch {
            NativeDataHub.saveMetric(HealthDomain.HYDRATION, "hydration_goal_ml", ml.toDouble(), "ml", "native-hydration")
            status = "Daily goal set to ${formatMlHydration(ml)}"
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().background(HydBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HydrationHeader(onBack)
        HydrationHeroControls(
            todayMl = snapshot.todayMl.coerceAtMost(snapshot.goalMl),
            goalMl = snapshot.goalMl,
            lastDrinkMl = snapshot.lastDrinkMl,
            lastDrinkLabel = snapshot.lastDrinkEpochMs?.let(::formatClock),
            amountMl = amountSlider,
            status = status,
            onAmountChange = { amountSlider = it.coerceIn(0, maxOf(snapshot.remainingMl, snapshot.todayMl)) },
            onQuickSubtract = ::subtractDrink,
            onClearToday = ::clearToday,
            onLog = { logDrink(amountSlider) }
        )

        HydrationCalendarCard(snapshot.calendarTotals, snapshot.goalMl)
        HydrationPacingCard(snapshot)
        HydrationHistoryCard(snapshot)

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).border(1.dp, HydBorder, RoundedCornerShape(24.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Daily water goal", color = HydInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text("Adjust between 1.5 L and 6.0 L", color = HydMuted, fontSize = 9.sp)
                }
                Text(formatMlHydration(goalDraft.roundToInt()), color = HydBlue, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Slider(value = goalDraft, onValueChange = { goalDraft = it }, valueRange = 1500f..6000f, steps = 44)
            Box(
                Modifier.fillMaxWidth().background(Color(0xFFE5F3FA), RoundedCornerShape(16.dp))
                    .superhumanClickable { saveGoal() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SAVE GOAL", color = HydBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun HydrationHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, HydBorder, RoundedCornerShape(14.dp)).superhumanClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = HydNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Hydration", color = HydInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Water intake, pace & consistency", color = HydMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HydrationPacingCard(snapshot: HydrationSnapshot) {
    val hour = java.time.LocalTime.now().hour
    val elapsed = ((hour - 7).coerceIn(0, 16) / 16.0).coerceIn(0.0, 1.0)
    val expectedMl = (snapshot.goalMl * elapsed).roundToInt()
    val delta = snapshot.todayMl.coerceAtMost(snapshot.goalMl) - expectedMl
    val message = when {
        hour < 7 -> "Your day has barely started — no pace pressure yet."
        snapshot.remainingMl == 0 -> "Goal complete. New water is locked for today so intake cannot run past your target."
        delta >= 300 -> "You’re comfortably ahead of an even water pace."
        delta >= -300 -> "You’re roughly on pace for today’s water goal."
        else -> "You’re ${formatMlHydration(-delta)} behind an even pace. A small glass would close the gap."
    }
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, HydBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("Water pace", color = HydInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(message, color = HydMuted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HydMiniStat(formatMlHydration(snapshot.remainingMl), "remaining")
            HydMiniStat(formatMlHydration(expectedMl.coerceAtMost(snapshot.goalMl)), "pace by now")
            HydMiniStat(snapshot.streak.toString(), "goal streak")
        }
    }
}

@Composable
private fun HydMiniStat(value: String, label: String) {
    Column {
        Text(value, color = HydNavy, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(label, color = HydMuted, fontSize = 8.sp)
    }
}

@Composable
private fun HydrationHistoryCard(snapshot: HydrationSnapshot) {
    val max = (snapshot.history.maxOfOrNull { it.ml } ?: snapshot.goalMl).coerceAtLeast(snapshot.goalMl).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, HydBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("7-day water consistency", color = HydInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Average ${formatMlHydration(snapshot.sevenDayAverage)} / day", color = HydMuted, fontSize = 9.sp)
            }
            Text("${snapshot.streak} day streak", color = HydGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            snapshot.history.forEach { day ->
                val fraction = (day.ml.toFloat() / max).coerceIn(0.03f, 1f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height((64 * fraction).dp)
                            .background(if (day.ml >= snapshot.goalMl) HydGreen.copy(alpha = .75f) else HydBlue.copy(alpha = .25f), RoundedCornerShape(7.dp))
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(day.date.dayOfWeek.name.take(1), color = HydMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private suspend fun loadHydrationSnapshot(): HydrationSnapshot {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = System.currentTimeMillis()
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val goal = NativeDataHub.latest("hydration_goal_ml")?.value?.roundToInt()?.coerceIn(1500, 6000) ?: 3600

    val month = YearMonth.from(today)
    val historyStart = today.minusDays(6)
    val rangeStartDate = minOf(month.atDay(1), historyStart)
    val rangeStart = rangeStartDate.atStartOfDay(zone).toInstant().toEpochMilli()

    // One indexed read per metric for the complete visible range. This keeps Hydration
    // work bounded even when the user has years of stored data.
    val intakeRows = NativeDataHub.between("water_intake_ml", rangeStart, now)
    val compatibilityRows = NativeDataHub.between("water_total_l", rangeStart, now)
    fun dateOf(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val intakeByDay = intakeRows.groupBy { dateOf(it.timestampEpochMs) }
    val compatibilityByDay = compatibilityRows.groupBy { dateOf(it.timestampEpochMs) }

    fun totalFor(date: LocalDate): Int {
        val events = intakeByDay[date].orEmpty()
        return if (events.isNotEmpty()) {
            events.sumOf { it.value }.roundToInt().coerceAtLeast(0)
        } else {
            compatibilityByDay[date].orEmpty().maxByOrNull { it.timestampEpochMs }
                ?.value?.times(1000.0)?.roundToInt()?.coerceAtLeast(0) ?: 0
        }
    }

    val todayEvents = intakeByDay[today].orEmpty().sortedBy { it.timestampEpochMs }
    val todayMl = totalFor(today).coerceIn(0, goal)

    val history = (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        HydrationDay(date, totalFor(date))
    }

    val calendarTotals = (1..month.lengthOfMonth()).associate { day ->
        val date = month.atDay(day)
        date to totalFor(date)
    }

    val lastPositive = todayEvents.lastOrNull { it.value > 0 }
    return HydrationSnapshot(
        todayMl = todayMl,
        goalMl = goal,
        lastDrinkMl = lastPositive?.value?.roundToInt(),
        lastDrinkEpochMs = lastPositive?.timestampEpochMs,
        history = history,
        calendarTotals = calendarTotals
    )
}

private fun formatClock(epochMs: Long): String {
    val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return local.format(DateTimeFormatter.ofPattern("HH:mm"))
}
