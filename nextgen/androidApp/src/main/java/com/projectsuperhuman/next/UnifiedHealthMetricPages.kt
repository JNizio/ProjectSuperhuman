package com.projectsuperhuman.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.projectsuperhuman.next.core.HealthDomain
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UnifiedNavy = Color(0xFF123D70)
private val UnifiedMuted = Color(0xFF748294)
private val UnifiedBorder = Color(0xFFE3EAF0)
private val UnifiedHeart = Color(0xFFD46072)
private val UnifiedSteps = Color(0xFF0D6CB4)
private val UnifiedOxygen = Color(0xFF20A7C4)
private val UnifiedCalories = Color(0xFFE08A2E)
private val UnifiedGreen = Color(0xFF379B7E)

private data class UnifiedMetricData(
    val latest: Double? = null,
    val todayAverage: Double? = null,
    val todayMin: Double? = null,
    val todayMax: Double? = null,
    val sevenDay: List<Double?> = emptyList(),
    val totalBurn: Double? = null,
    val activeBurn: Double? = null,
    val eaten: Double? = null,
    val totalBurnHistory: List<Double?> = emptyList(),
    val eatenHistory: List<Double?> = emptyList()
)

@Composable
internal fun UnifiedMiniMetricPage(metric: HomeMiniMetric, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = unifiedAccent(metric)
    var data by remember(metric) { mutableStateOf(UnifiedMetricData()) }
    var syncing by remember(metric) { mutableStateOf(false) }
    var fullyConnected by remember(metric) { mutableStateOf(false) }
    var backgroundAvailable by remember(metric) { mutableStateOf(false) }
    var backgroundEnabled by remember(metric) { mutableStateOf(false) }
    var status by remember(metric) { mutableStateOf("Checking Health Connect…") }
    var refreshToken by remember(metric) { mutableStateOf("initial") }

    suspend fun refresh() {
        data = loadUnifiedMetricData(metric)
        fullyConnected = GlobalHealthConnect.hasAllCorePermissions(context)
        backgroundAvailable = MiniMetricsHealthConnect.backgroundReadAvailable(context)
        backgroundEnabled = GlobalHealthConnect.hasBackgroundPermission(context)
    }

    suspend fun syncAll() {
        syncing = true
        val result = GlobalHealthConnect.sync(context)
        status = result.message
        refreshToken = System.currentTimeMillis().toString()
        refresh()
        syncing = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        scope.launch {
            refresh()
            if (backgroundEnabled) MiniMetricsBackgroundSync.ensureScheduled(context)
            if (GlobalHealthConnect.hasAnyCorePermission(context)) syncAll()
            status = if (fullyConnected) "Health Connect connected across Superhuman" else "Some Health Connect permissions are still disabled"
        }
    }

    fun connectOrSync() {
        scope.launch {
            when (GlobalHealthConnect.availability(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (GlobalHealthConnect.hasAllCorePermissions(context)) syncAll()
                    else permissionLauncher.launch(GlobalHealthConnect.requestPermissions(context))
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> status = "Health Connect needs an update"
                else -> status = "Health Connect isn’t available on this device"
            }
        }
    }

    LaunchedEffect(metric) {
        refresh()
        status = when {
            GlobalHealthConnect.availability(context) != HealthConnectClient.SDK_AVAILABLE -> "Health Connect isn’t available on this device"
            fullyConnected && backgroundEnabled -> "All supported data connected · 15 min background sync"
            fullyConnected -> "All supported Samsung Health data connected"
            GlobalHealthConnect.hasAnyCorePermission(context) -> "Finish setup once to connect every supported data type"
            else -> "Connect once for sleep, heart rate, steps, SpO₂ and calorie burn"
        }
        if (fullyConnected) syncAll()
    }

    LaunchedEffect(metric, fullyConnected) {
        if (!fullyConnected) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            MiniMetricsHealthConnect.syncCurrent(context)
            if (metric == HomeMiniMetric.CALORIES) {
                CalorieAccuracyEngine.syncCurrent(context)
            }
            refreshToken = System.currentTimeMillis().toString()
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8FBFD)).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UnifiedHeader(metric.title, metric.subtitle, onBack)
        when (metric) {
            HomeMiniMetric.HEART_RATE -> HeartRateUnifiedContent(data, syncing, refreshToken)
            HomeMiniMetric.CALORIES -> CaloriesUnifiedContent(data)
            HomeMiniMetric.STEPS -> GenericUnifiedContent(metric, data, accent)
            HomeMiniMetric.BLOOD_OXYGEN -> GenericUnifiedContent(metric, data, accent)
        }
        UnifiedHealthConnectCard(
            fullyConnected = fullyConnected,
            syncing = syncing,
            status = status,
            backgroundEnabled = backgroundEnabled,
            onClick = ::connectOrSync
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun UnifiedSleepPage(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking Health Connect…") }

    suspend fun refreshReady() {
        ready = GlobalHealthConnect.hasAllCorePermissions(context)
    }

    suspend fun syncAll() {
        syncing = true
        val result = GlobalHealthConnect.sync(context)
        status = result.message
        refreshReady()
        syncing = false
    }

    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        scope.launch {
            refreshReady()
            if (GlobalHealthConnect.hasAnyCorePermission(context)) syncAll()
        }
    }

    LaunchedEffect(Unit) {
        refreshReady()
        if (ready) syncAll()
    }

    if (ready) {
        NativeSleepHistoryPage(onBack, openLegacy)
        return
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8FBFD)).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UnifiedHeader("Sleep", "Wearable sleep & recovery", onBack)
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF111E4D), Color(0xFF493F91))), RoundedCornerShape(28.dp)
            ).padding(20.dp)
        ) {
            Text("ONE HEALTH CONNECT SETUP", color = Color.White.copy(alpha = .68f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text("Connect the whole health system", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("One permission flow enables sleep, heart rate, steps, blood oxygen and calorie burn together. You won’t have to set each module up separately.", color = Color.White.copy(alpha = .82f), fontSize = 10.sp, lineHeight = 15.sp)
        }
        UnifiedHealthConnectCard(false, syncing, status, false) {
            scope.launch {
                when (GlobalHealthConnect.availability(context)) {
                    HealthConnectClient.SDK_AVAILABLE -> launcher.launch(GlobalHealthConnect.requestPermissions(context))
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> status = "Health Connect needs an update"
                    else -> status = "Health Connect isn’t available on this device"
                }
            }
        }
    }
}

@Composable
private fun HeartRateUnifiedContent(data: UnifiedMetricData, syncing: Boolean, refreshToken: String) {
    UnifiedHero(
        label = "SAMSUNG HEALTH",
        value = data.latest?.roundToInt()?.toString() ?: "—",
        unit = if (data.latest != null) "bpm" else "",
        caption = "Latest Fit3 / Samsung Health heart-rate reading",
        accent = UnifiedHeart
    )
    AdvancedHeartRateSection(syncing = syncing, refreshSignal = refreshToken)
}

@Composable
private fun CaloriesUnifiedContent(data: UnifiedMetricData) {
    val total = data.totalBurn
    val active = data.activeBurn
    val eaten = data.eaten
    val resting = if (total != null && active != null) (total - active).coerceAtLeast(0.0) else null
    val balance = if (total != null && eaten != null) eaten - total else null

    UnifiedHero(
        label = "ENERGY TODAY",
        value = total?.roundToInt()?.toString() ?: "—",
        unit = if (total != null) "kcal" else "",
        caption = if (total != null) "Samsung Health burn with Health Connect coverage repair when needed" else "Waiting for Samsung Health total calorie burn",
        accent = UnifiedCalories
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        UnifiedStat("ACTIVE", active?.roundToInt()?.let { "$it kcal" } ?: "—", Modifier.weight(1f))
        UnifiedStat("RESTING", resting?.roundToInt()?.let { "$it kcal" } ?: "—", Modifier.weight(1f))
        UnifiedStat("EATEN", eaten?.roundToInt()?.let { "$it kcal" } ?: "—", Modifier.weight(1f))
    }

    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(UnifiedCalories.copy(alpha = .10f), Color.White)), RoundedCornerShape(22.dp)
        ).border(1.dp, UnifiedCalories.copy(alpha = .16f), RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("ENERGY BALANCE", color = UnifiedCalories, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(6.dp))
        when {
            eaten == null -> {
                Text("Log food to calculate balance", color = UnifiedNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("We won’t treat an empty nutrition log as 0 kcal eaten.", color = UnifiedMuted, fontSize = 9.sp)
            }
            total == null -> {
                Text("Waiting for burn data", color = UnifiedNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("Food is logged, but Samsung total burn isn’t available yet.", color = UnifiedMuted, fontSize = 9.sp)
            }
            else -> {
                val amount = absInt(balance ?: 0.0)
                val label = if ((balance ?: 0.0) >= 0) "${amount} kcal above burn" else "${amount} kcal below burn"
                Text(label, color = UnifiedNavy, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("Eaten ${eaten.roundToInt()} · Burned ${total.roundToInt()}", color = UnifiedMuted, fontSize = 9.sp)
            }
        }
        if (total != null && active == null) {
            Spacer(Modifier.height(10.dp))
            Text("Samsung has shared total burn but not enough component data for a reliable active/resting split. Total burn remains usable without inventing the missing split.", color = UnifiedMuted, fontSize = 8.sp, lineHeight = 12.sp)
        }
    }

    CaloriesHistoryCard(data.totalBurnHistory, data.eatenHistory)
}

@Composable
private fun CaloriesHistoryCard(burn: List<Double?>, eaten: List<Double?>) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, UnifiedBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("LAST 7 DAYS", color = UnifiedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(3.dp))
        Text("Burn vs intake", color = UnifiedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        DualEnergyChart(burn, eaten)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = UnifiedMuted, fontSize = 7.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(3.dp).background(UnifiedCalories)); Spacer(Modifier.width(5.dp)); Text("total burn", color = UnifiedMuted, fontSize = 7.sp)
            Spacer(Modifier.width(14.dp)); Box(Modifier.width(18.dp).height(3.dp).background(UnifiedGreen)); Spacer(Modifier.width(5.dp)); Text("food logged", color = UnifiedMuted, fontSize = 7.sp)
        }
    }
}

@Composable
private fun DualEnergyChart(burn: List<Double?>, eaten: List<Double?>) {
    Canvas(Modifier.fillMaxWidth().height(104.dp)) {
        val max = (burn + eaten).filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val slots = maxOf(burn.size, eaten.size, 7)
        val slot = size.width / slots
        repeat(slots) { i ->
            val center = slot * i + slot / 2f
            val burnValue = burn.getOrNull(i)
            val eatenValue = eaten.getOrNull(i)
            burnValue?.let {
                val height = (it / max).toFloat() * size.height * .82f
                drawLine(UnifiedCalories.copy(alpha = .78f), Offset(center - 5f, size.height), Offset(center - 5f, size.height - height), strokeWidth = 7f)
            }
            eatenValue?.let {
                val height = (it / max).toFloat() * size.height * .82f
                drawLine(UnifiedGreen.copy(alpha = .78f), Offset(center + 5f, size.height), Offset(center + 5f, size.height - height), strokeWidth = 7f)
            }
        }
    }
}

@Composable
private fun GenericUnifiedContent(metric: HomeMiniMetric, data: UnifiedMetricData, accent: Color) {
    val unit = if (metric == HomeMiniMetric.BLOOD_OXYGEN) "%" else "steps"
    val current = if (metric == HomeMiniMetric.STEPS) data.todayAverage else data.latest
    UnifiedHero(
        "SAMSUNG HEALTH",
        current?.roundToInt()?.toString() ?: "—",
        if (current != null) unit else "",
        if (metric == HomeMiniMetric.STEPS) "Samsung Health steps accumulated today" else "Latest blood-oxygen reading shared by Samsung Health",
        accent
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        UnifiedStat(if (metric == HomeMiniMetric.STEPS) "TODAY" else "TODAY AVG", data.todayAverage?.roundToInt()?.let { if (metric == HomeMiniMetric.BLOOD_OXYGEN) "$it%" else it.toString() } ?: "—", Modifier.weight(1f))
        UnifiedStat("TODAY RANGE", if (data.todayMin != null && data.todayMax != null) "${data.todayMin.roundToInt()}–${data.todayMax.roundToInt()}${if (metric == HomeMiniMetric.BLOOD_OXYGEN) "%" else ""}" else "—", Modifier.weight(1f))
        UnifiedStat("SOURCE", "Samsung", Modifier.weight(1f))
    }
    GenericHistory(metric, data.sevenDay, accent)
}

@Composable
private fun GenericHistory(metric: HomeMiniMetric, values: List<Double?>, accent: Color) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, UnifiedBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("LAST 7 DAYS", color = UnifiedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(if (metric == HomeMiniMetric.STEPS) "Daily steps" else "Daily average SpO₂", color = UnifiedNavy, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(86.dp)) {
            val existing = values.filterNotNull()
            if (existing.isEmpty()) {
                drawLine(UnifiedBorder, Offset(0f, size.height * .75f), Offset(size.width, size.height * .75f), strokeWidth = 2f)
            } else {
                val min = existing.minOrNull() ?: 0.0
                val max = existing.maxOrNull() ?: min
                val range = (max - min).coerceAtLeast(1.0)
                val path = Path()
                var started = false
                values.forEachIndexed { i, value ->
                    if (value != null) {
                        val x = if (values.size <= 1) size.width / 2f else i.toFloat() / (values.size - 1) * size.width
                        val y = size.height * .82f - (((value - min) / range).toFloat() * size.height * .60f)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                        drawCircle(accent, 4f, Offset(x, y))
                    }
                }
                if (started) drawPath(path, accent.copy(alpha = .78f), style = Stroke(width = 4f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = UnifiedMuted, fontSize = 7.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun UnifiedHero(label: String, value: String, unit: String, caption: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha = .14f), Color.White)), RoundedCornerShape(26.dp))
            .border(1.dp, accent.copy(alpha = .16f), RoundedCornerShape(26.dp)).padding(20.dp)
    ) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = UnifiedNavy, fontSize = 38.sp, fontWeight = FontWeight.Black)
            if (unit.isNotBlank()) { Spacer(Modifier.width(6.dp)); Text(unit, color = UnifiedMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp)) }
        }
        Spacer(Modifier.height(5.dp)); Text(caption, color = UnifiedMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun UnifiedStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).border(1.dp, UnifiedBorder, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text(label, color = UnifiedMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(6.dp)); Text(value, color = UnifiedNavy, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun UnifiedHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", color = UnifiedNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column { Text(title, color = UnifiedNavy, fontSize = 23.sp, fontWeight = FontWeight.Black); Text(subtitle, color = UnifiedMuted, fontSize = 10.sp) }
    }
}

@Composable
private fun UnifiedHealthConnectCard(
    fullyConnected: Boolean,
    syncing: Boolean,
    status: String,
    backgroundEnabled: Boolean,
    onClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).border(1.dp, UnifiedBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("HEALTH CONNECT · ALL MODULES", color = UnifiedMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (fullyConnected) "Superhuman health data connected" else "Connect once across the app", color = UnifiedNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp)); Text(status, color = UnifiedMuted, fontSize = 9.sp, lineHeight = 13.sp)
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.background(Color(0xFFE8F3FA), RoundedCornerShape(14.dp)).clickable(enabled = !syncing, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(if (syncing) "SYNCING ALL…" else if (fullyConnected) "SYNC ALL" else "CONNECT ALL", color = Color(0xFF0D6CB4), fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            if (fullyConnected) Text(if (backgroundEnabled) "15 min background sync" else "All supported parameters", color = UnifiedMuted, fontSize = 8.sp)
        }
    }
}

private fun unifiedAccent(metric: HomeMiniMetric): Color = when (metric) {
    HomeMiniMetric.HEART_RATE -> UnifiedHeart
    HomeMiniMetric.STEPS -> UnifiedSteps
    HomeMiniMetric.BLOOD_OXYGEN -> UnifiedOxygen
    HomeMiniMetric.CALORIES -> UnifiedCalories
}

private suspend fun loadUnifiedMetricData(metric: HomeMiniMetric): UnifiedMetricData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val now = System.currentTimeMillis()

    suspend fun summary(domain: HealthDomain, name: String, date: LocalDate): Double? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L)
        return NativeDataHub.between(domain, name, start, end)
            .filter { it.source == MiniMetricsHealthConnect.SOURCE && it.metadata["summaryDate"] == date.toString() }
            .maxByOrNull { it.timestampEpochMs }?.value
    }

    suspend fun latest(domain: HealthDomain, name: String): Double? {
        val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        return NativeDataHub.between(domain, name, start, now)
            .filter { it.source == MiniMetricsHealthConnect.SOURCE }
            .maxByOrNull { it.timestampEpochMs }?.value
    }

    suspend fun eaten(date: LocalDate): Double? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L)
        val rows = NativeDataHub.between(HealthDomain.NUTRITION, "food_kcal", start, end)
        return if (rows.isEmpty()) null else rows.sumOf { it.value }.takeIf { it > 0.0 }
    }

    return when (metric) {
        HomeMiniMetric.HEART_RATE -> UnifiedMetricData(latest = latest(HealthDomain.EXERCISE, "heart_rate_bpm"))
        HomeMiniMetric.STEPS -> {
            val history = dates.map { summary(HealthDomain.EXERCISE, "steps", it) }
            UnifiedMetricData(todayAverage = history.lastOrNull(), sevenDay = history)
        }
        HomeMiniMetric.BLOOD_OXYGEN -> UnifiedMetricData(
            latest = latest(HealthDomain.BODY, "blood_oxygen_percent"),
            todayAverage = summary(HealthDomain.BODY, "blood_oxygen_avg_percent", today),
            todayMin = summary(HealthDomain.BODY, "blood_oxygen_min_percent", today),
            todayMax = summary(HealthDomain.BODY, "blood_oxygen_max_percent", today),
            sevenDay = dates.map { summary(HealthDomain.BODY, "blood_oxygen_avg_percent", it) }
        )
        HomeMiniMetric.CALORIES -> {
            val activeHistory = dates.map { summary(HealthDomain.EXERCISE, "calories_burned_active_kcal", it) }
            val totalHistory = dates.map { summary(HealthDomain.EXERCISE, "calories_burned_total_kcal", it) }
            val eatenHistory = dates.map { eaten(it) }
            UnifiedMetricData(
                activeBurn = activeHistory.lastOrNull(),
                totalBurn = totalHistory.lastOrNull(),
                eaten = eatenHistory.lastOrNull(),
                sevenDay = activeHistory,
                totalBurnHistory = totalHistory,
                eatenHistory = eatenHistory
            )
        }
    }
}

private fun absInt(value: Double): Int = kotlin.math.abs(value).roundToInt()
