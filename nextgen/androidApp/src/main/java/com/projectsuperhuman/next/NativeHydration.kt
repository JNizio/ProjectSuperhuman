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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Native hydration orchestration and persistence.
 *
 * The interactive hero lives in HydrationHeroControls.kt and the animated liquid primitive lives
 * in HydrationOrb.kt. Keep persistence and analytics here so future design changes stay low-risk.
 *
 * Input safety:
 * - New intake can never take today's total above the configured goal.
 * - UI updates optimistically before SQLite completes, so the orb reacts immediately to a log.
 * - Slider values snap to 50 ml increments and preview the projected orb fill before saving.
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
    val history: List<HydrationDay> = emptyList()
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
    var source by remember { mutableStateOf("Water") }
    var goalDraft by remember { mutableStateOf(3600f) }
    var amountSlider by remember { mutableStateOf(250) }
    var status by remember { mutableStateOf("") }

    suspend fun refresh() {
        snapshot = loadHydrationSnapshot()
        goalDraft = snapshot.goalMl.toFloat()
        amountSlider = when {
            snapshot.remainingMl <= 0 -> 0
            amountSlider <= 0 -> minOf(250, snapshot.remainingMl)
            else -> amountSlider.coerceAtMost(snapshot.remainingMl)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun logDrink(requestedMl: Int) {
        if (requestedMl <= 0) return
        val allowed = minOf(requestedMl, snapshot.remainingMl)
        if (allowed <= 0) {
            status = "Daily goal already reached"
            return
        }

        val now = System.currentTimeMillis()
        val previous = snapshot
        snapshot = snapshot.copy(
            todayMl = (snapshot.todayMl + allowed).coerceAtMost(snapshot.goalMl),
            lastDrinkMl = allowed,
            lastDrinkEpochMs = now
        )
        amountSlider = minOf(250, snapshot.remainingMl)
        status = if (allowed < requestedMl) "+$allowed ml logged · capped at daily goal" else "+$allowed ml logged"

        scope.launch {
            runCatching {
                NativeDataHub.saveMetric(
                    domain = HealthDomain.HYDRATION,
                    metric = "water_intake_ml",
                    value = allowed.toDouble(),
                    unit = "ml",
                    source = "native-hydration",
                    metadata = mapOf("drinkSource" to source)
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
            }.onSuccess {
                refresh()
            }.onFailure {
                snapshot = previous
                status = "Couldn’t save that drink — nothing was changed"
            }
        }
    }

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
            source = source,
            amountMl = amountSlider,
            status = status,
            onSourceChange = { source = it },
            onAmountChange = { amountSlider = it.coerceIn(0, snapshot.remainingMl) },
            onQuickLog = ::logDrink,
            onLog = { logDrink(amountSlider) }
        )

        HydrationPacingCard(snapshot)
        HydrationHistoryCard(snapshot)

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).border(1.dp, HydBorder, RoundedCornerShape(24.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Daily goal", color = HydInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
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
        ) { Text("‹", color = HydNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Hydration", color = HydInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Daily fluids, pace & consistency", color = HydMuted, fontSize = 10.sp)
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
        snapshot.remainingMl == 0 -> "Goal complete. New drinks are locked for today so intake cannot run past your target."
        delta >= 300 -> "You’re comfortably ahead of an even hydration pace."
        delta >= -300 -> "You’re roughly on pace for today’s goal."
        else -> "You’re ${formatMlHydration(-delta)} behind an even pace. A small top-up would close the gap."
    }
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, HydBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("Hydration pace", color = HydInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
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
                Text("7-day consistency", color = HydInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
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
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val goal = NativeDataHub.latest("hydration_goal_ml")?.value?.roundToInt()?.coerceIn(1500, 6000) ?: 3600
    val todayEvents = NativeDataHub.between("water_intake_ml", start, now).sortedBy { it.timestampEpochMs }
    val eventTotal = todayEvents.sumOf { it.value }.roundToInt()
    val compatibilityTotal = NativeDataHub.latest("water_total_l")?.takeIf { it.timestampEpochMs >= start }?.value?.times(1000.0)?.roundToInt() ?: 0
    val todayMl = (if (todayEvents.isNotEmpty()) eventTotal else compatibilityTotal).coerceAtMost(goal)

    val history = (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val events = NativeDataHub.between("water_intake_ml", from, to)
        val total = if (events.isNotEmpty()) events.sumOf { it.value }.roundToInt() else
            NativeDataHub.between("water_total_l", from, to).maxByOrNull { it.timestampEpochMs }?.value?.times(1000.0)?.roundToInt() ?: 0
        HydrationDay(date, total)
    }
    val last = todayEvents.lastOrNull()
    return HydrationSnapshot(todayMl, goal, last?.value?.roundToInt(), last?.timestampEpochMs, history)
}

private fun formatClock(epochMs: Long): String {
    val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return local.format(DateTimeFormatter.ofPattern("HH:mm"))
}
