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
import com.projectsuperhuman.next.core.HealthValue
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class HomeMiniMetric(val title: String, val subtitle: String) {
    HEART_RATE("Heart rate", "Pulse & daily range"),
    STEPS("Steps", "Daily movement"),
    BLOOD_OXYGEN("Blood oxygen", "SpO₂ readings"),
    STRESS("Stress", "Recovery & strain")
}

private data class MiniMetricSnapshot(
    val heartRateBpm: Int? = null,
    val heartRateTimestampMs: Long? = null,
    val steps: Int? = null,
    val steps7dAverage: Int? = null,
    val bloodOxygenPct: Int? = null,
    val bloodOxygenTimestampMs: Long? = null,
    val stressScore: Int? = null
)

private data class MiniMetricDetailData(
    val current: Double? = null,
    val unit: String = "",
    val primaryLabel: String = "TODAY",
    val primaryValue: String = "—",
    val secondaryLabel: String = "RANGE",
    val secondaryValue: String = "—",
    val sourceValue: String = "Samsung",
    val history: List<Double?> = emptyList(),
    val hasSamsungData: Boolean = false
)

private val MiniNavy = Color(0xFF123D70)
private val MiniMuted = Color(0xFF748294)
private val MiniBorder = Color(0xFFE3EAF0)
private val MiniHeart = Color(0xFFD46072)
private val MiniSteps = Color(0xFF0D6CB4)
private val MiniOxygen = Color(0xFF20A7C4)
private val MiniStress = Color(0xFF7260BF)

@Composable
internal fun HomeDateStrip() {
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    }
    Box(
        Modifier.fillMaxWidth()
            .height(54.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFF7FBFE), Color(0xFFEEF8FC), Color(0xFFF7FBFE))
                ),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, MiniBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            date,
            color = MiniNavy,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .35.sp
        )
    }
}

@Composable
internal fun HomeMiniMetricsGrid(openMetric: (HomeMiniMetric) -> Unit) {
    val context = LocalContext.current
    var metrics by remember { mutableStateOf(MiniMetricSnapshot()) }

    LaunchedEffect(Unit) {
        metrics = loadMiniMetricSnapshot()
        if (MiniMetricsHealthConnect.hasAnyPermission(context)) {
            MiniMetricsHealthConnect.sync(context)
            metrics = loadMiniMetricSnapshot()
            while (true) {
                delay(30_000L)
                MiniMetricsHealthConnect.syncCurrent(context)
                metrics = loadMiniMetricSnapshot()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniMetricCard(
                metric = HomeMiniMetric.HEART_RATE,
                value = metrics.heartRateBpm?.toString() ?: "—",
                unit = if (metrics.heartRateBpm != null) "bpm" else "",
                status = if (metrics.heartRateBpm != null) freshnessLabel(metrics.heartRateTimestampMs) else "Tap to connect",
                accent = MiniHeart,
                modifier = Modifier.weight(1.25f),
                style = MiniVisualStyle.PULSE,
                onClick = { openMetric(HomeMiniMetric.HEART_RATE) }
            )
            MiniMetricCard(
                metric = HomeMiniMetric.STEPS,
                value = metrics.steps?.let(::compactCount) ?: "—",
                unit = "",
                status = if (metrics.steps != null) metrics.steps7dAverage?.let { "7d avg ${compactCount(it)}" } ?: "today · auto refresh" else "Tap to connect",
                accent = MiniSteps,
                modifier = Modifier.weight(.75f),
                style = MiniVisualStyle.DOTS,
                onClick = { openMetric(HomeMiniMetric.STEPS) }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniMetricCard(
                metric = HomeMiniMetric.BLOOD_OXYGEN,
                value = metrics.bloodOxygenPct?.toString() ?: "—",
                unit = if (metrics.bloodOxygenPct != null) "%" else "",
                status = if (metrics.bloodOxygenPct != null) freshnessLabel(metrics.bloodOxygenTimestampMs) else "Tap to connect",
                accent = MiniOxygen,
                modifier = Modifier.weight(.82f),
                style = MiniVisualStyle.RING,
                onClick = { openMetric(HomeMiniMetric.BLOOD_OXYGEN) }
            )
            MiniMetricCard(
                metric = HomeMiniMetric.STRESS,
                value = metrics.stressScore?.toString() ?: "—",
                unit = if (metrics.stressScore != null) "/100" else "",
                status = if (metrics.stressScore != null) "Latest reading" else "Not shared by Samsung",
                accent = MiniStress,
                modifier = Modifier.weight(1.18f),
                style = MiniVisualStyle.WAVES,
                onClick = { openMetric(HomeMiniMetric.STRESS) }
            )
        }
    }
}

private enum class MiniVisualStyle { PULSE, DOTS, RING, WAVES }

@Composable
private fun MiniMetricCard(
    metric: HomeMiniMetric,
    value: String,
    unit: String,
    status: String,
    accent: Color,
    modifier: Modifier,
    style: MiniVisualStyle,
    onClick: () -> Unit
) {
    Box(
        modifier.height(106.dp)
            .background(
                Brush.linearGradient(listOf(Color.White, accent.copy(alpha = .065f))),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, accent.copy(alpha = .18f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    metric.title.uppercase(),
                    color = MiniMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .85.sp
                )
                Text("→", color = accent.copy(alpha = .72f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = MiniNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, color = MiniMuted, fontSize = 8.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(status, color = MiniMuted, fontSize = 8.sp, maxLines = 1)
        }

        MiniMetricVisual(
            style = style,
            accent = accent,
            modifier = Modifier.align(Alignment.BottomEnd).width(52.dp).height(28.dp)
        )
    }
}

@Composable
private fun MiniMetricVisual(style: MiniVisualStyle, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        when (style) {
            MiniVisualStyle.PULSE -> {
                val path = Path().apply {
                    moveTo(0f, size.height * .62f)
                    lineTo(size.width * .24f, size.height * .62f)
                    lineTo(size.width * .36f, size.height * .30f)
                    lineTo(size.width * .49f, size.height * .84f)
                    lineTo(size.width * .63f, size.height * .48f)
                    lineTo(size.width, size.height * .48f)
                }
                drawPath(path, accent.copy(alpha = .7f), style = Stroke(width = 3f))
            }
            MiniVisualStyle.DOTS -> {
                listOf(.18f, .48f, .78f).forEachIndexed { index, x ->
                    drawCircle(
                        accent.copy(alpha = .30f + index * .18f),
                        radius = 5f + index * 2f,
                        center = Offset(size.width * x, size.height * (.66f - index * .10f))
                    )
                }
            }
            MiniVisualStyle.RING -> {
                drawCircle(accent.copy(alpha = .16f), radius = size.minDimension * .38f, center = center, style = Stroke(width = 5f))
                drawArc(accent.copy(alpha = .72f), -90f, 250f, false, style = Stroke(width = 5f))
            }
            MiniVisualStyle.WAVES -> {
                repeat(3) { row ->
                    val y = size.height * (.28f + row * .24f)
                    drawLine(
                        accent.copy(alpha = .24f + row * .17f),
                        Offset(size.width * .08f, y),
                        Offset(size.width * (.72f + row * .08f), y),
                        strokeWidth = 3f
                    )
                }
            }
        }
    }
}

@Composable
internal fun NativeMiniMetricPlaceholderPage(metric: HomeMiniMetric, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = accentFor(metric)
    val permission = MiniMetricsHealthConnect.permissionFor(metric)

    var detail by remember(metric) { mutableStateOf(MiniMetricDetailData()) }
    var syncing by remember(metric) { mutableStateOf(false) }
    var connected by remember(metric) { mutableStateOf(false) }
    var status by remember(metric) { mutableStateOf("Checking Samsung Health…") }

    suspend fun refresh() {
        detail = loadMiniMetricDetail(metric)
    }

    suspend fun sync() {
        syncing = true
        val result = MiniMetricsHealthConnect.sync(context)
        val advanced = if (metric == HomeMiniMetric.HEART_RATE && result.success) {
            HeartRateAdvancedHealthConnect.sync(context)
        } else null
        syncing = false
        status = advanced?.message ?: result.message
        connected = permission != null && MiniMetricsHealthConnect.hasPermission(context, metric)
        refresh()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (permission != null && permission in granted) {
            connected = true
            status = "Connected to Samsung Health through Health Connect"
            scope.launch { sync() }
        } else if (permission != null) {
            connected = false
            status = "Access wasn’t enabled"
        }
    }

    fun connectOrSync() {
        if (metric == HomeMiniMetric.STRESS) return
        scope.launch {
            when (MiniMetricsHealthConnect.availability(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (MiniMetricsHealthConnect.hasPermission(context, metric)) sync()
                    else if (permission != null) permissionLauncher.launch(setOf(permission))
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    status = "Health Connect needs an update"
                else -> status = "Health Connect isn’t available on this device"
            }
        }
    }

    LaunchedEffect(metric) {
        refresh()
        if (metric == HomeMiniMetric.STRESS) {
            status = "Samsung Health does not currently share its Stress score through Health Connect"
            return@LaunchedEffect
        }
        val available = MiniMetricsHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE
        connected = available && MiniMetricsHealthConnect.hasPermission(context, metric)
        status = when {
            !available -> "Health Connect isn’t available on this device"
            connected -> "Samsung Health connected through Health Connect"
            else -> "Connect this metric from Samsung Health"
        }
        if (connected) sync()
    }

    LaunchedEffect(metric, connected) {
        if (!connected || metric == HomeMiniMetric.STRESS) return@LaunchedEffect
        val intervalMs = when (metric) {
            HomeMiniMetric.HEART_RATE, HomeMiniMetric.STEPS -> 15_000L
            HomeMiniMetric.BLOOD_OXYGEN -> 30_000L
            HomeMiniMetric.STRESS -> 60_000L
        }
        while (true) {
            delay(intervalMs)
            MiniMetricsHealthConnect.syncCurrent(context, metric)
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFFF8FBFD))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MiniMetricHeader(metric, onBack)

        if (metric == HomeMiniMetric.STRESS && detail.current == null) {
            UnsupportedStressCard()
        } else {
            MiniMetricHero(metric, detail, accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricStat(detail.primaryLabel, detail.primaryValue, Modifier.weight(1f))
                MetricStat(detail.secondaryLabel, detail.secondaryValue, Modifier.weight(1f))
                MetricStat("SOURCE", detail.sourceValue, Modifier.weight(1f))
            }
            MiniMetricHistoryCard(metric, detail.history, accent)
            if (metric == HomeMiniMetric.HEART_RATE) {
                AdvancedHeartRateSection(syncing = syncing, refreshSignal = status)
            }
        }

        if (metric != HomeMiniMetric.STRESS) {
            HealthConnectMiniCard(
                connected = connected,
                syncing = syncing,
                status = status,
                onClick = ::connectOrSync
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MiniMetricHeader(metric: HomeMiniMetric, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(46.dp).height(46.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, MiniBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = MiniNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(metric.title, color = MiniNavy, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(metric.subtitle, color = MiniMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MiniMetricHero(metric: HomeMiniMetric, detail: MiniMetricDetailData, accent: Color) {
    val displayValue = detail.current?.let { value ->
        when (metric) {
            HomeMiniMetric.STEPS -> compactCount(value.roundToInt())
            else -> value.roundToInt().toString()
        }
    } ?: "—"

    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = .15f), Color.White)),
                RoundedCornerShape(26.dp)
            )
            .border(1.dp, accent.copy(alpha = .16f), RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Text(
            if (detail.hasSamsungData) "SAMSUNG HEALTH" else "NO SAMSUNG DATA YET",
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(displayValue, color = MiniNavy, fontSize = 38.sp, fontWeight = FontWeight.Black)
            if (detail.unit.isNotBlank() && detail.current != null) {
                Spacer(Modifier.width(6.dp))
                Text(detail.unit, color = MiniMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            when (metric) {
                HomeMiniMetric.HEART_RATE -> "Latest Fit3 / Samsung Health heart-rate reading"
                HomeMiniMetric.STEPS -> "Samsung Health steps accumulated today"
                HomeMiniMetric.BLOOD_OXYGEN -> "Latest blood-oxygen reading shared by Samsung Health"
                HomeMiniMetric.STRESS -> "Latest stress value"
            },
            color = MiniMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun MetricStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text(label, color = MiniMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, color = MiniNavy, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MiniMetricHistoryCard(metric: HomeMiniMetric, history: List<Double?>, accent: Color) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("LAST 7 DAYS", color = MiniMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            when (metric) {
                HomeMiniMetric.HEART_RATE -> "Daily average heart rate"
                HomeMiniMetric.STEPS -> "Daily steps"
                HomeMiniMetric.BLOOD_OXYGEN -> "Daily average SpO₂"
                HomeMiniMetric.STRESS -> "Daily stress"
            },
            color = MiniNavy,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(14.dp))
        MiniHistoryChart(history, accent)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(day, color = MiniMuted, fontSize = 7.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MiniHistoryChart(values: List<Double?>, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(82.dp)) {
        if (values.isEmpty()) return@Canvas
        val existing = values.filterNotNull()
        if (existing.isEmpty()) {
            drawLine(MiniBorder, Offset(0f, size.height * .7f), Offset(size.width, size.height * .7f), strokeWidth = 2f)
            return@Canvas
        }
        val min = existing.minOrNull() ?: 0.0
        val max = existing.maxOrNull() ?: min
        val range = (max - min).coerceAtLeast(1.0)
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val x = slot * index + slot / 2f
            if (value == null) {
                drawCircle(MiniBorder, radius = 4f, center = Offset(x, size.height * .82f))
            } else {
                val normalized = ((value - min) / range).toFloat()
                val top = size.height * (.82f - normalized * .62f)
                drawLine(accent.copy(alpha = .72f), Offset(x, size.height * .86f), Offset(x, top), strokeWidth = 8f)
                drawCircle(accent, radius = 5f, center = Offset(x, top))
            }
        }
    }
}

@Composable
private fun HealthConnectMiniCard(
    connected: Boolean,
    syncing: Boolean,
    status: String,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text("HEALTH CONNECT", color = MiniMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (connected) "Samsung Health connected" else "Bring in your Fit3 data",
            color = MiniNavy,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(status, color = MiniMuted, fontSize = 9.sp, lineHeight = 13.sp)
        Spacer(Modifier.height(11.dp))
        Box(
            Modifier.background(Color(0xFFE8F3FA), RoundedCornerShape(14.dp))
                .clickable(enabled = !syncing, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                when {
                    syncing -> "SYNCING…"
                    connected -> "SYNC NOW"
                    else -> "CONNECT"
                },
                color = Color(0xFF0D6CB4),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun UnsupportedStressCard() {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(MiniStress.copy(alpha = .14f), Color.White)), RoundedCornerShape(26.dp))
            .border(1.dp, MiniStress.copy(alpha = .16f), RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Text("SOURCE LIMITATION", color = MiniStress, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp))
        Text("Samsung Stress isn’t exported", color = MiniNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Text(
            "Samsung Health currently shares steps, heart rate and blood oxygen with Health Connect, but not its proprietary Stress score. Project Superhuman will keep this blank rather than inventing a measurement.",
            color = MiniMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "A future Project Superhuman recovery/stress model can be built separately from sleep, heart rate, HRV and activity with its confidence shown clearly.",
            color = MiniNavy,
            fontSize = 9.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun accentFor(metric: HomeMiniMetric): Color = when (metric) {
    HomeMiniMetric.HEART_RATE -> MiniHeart
    HomeMiniMetric.STEPS -> MiniSteps
    HomeMiniMetric.BLOOD_OXYGEN -> MiniOxygen
    HomeMiniMetric.STRESS -> MiniStress
}

private suspend fun loadMiniMetricSnapshot(): MiniMetricSnapshot {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = System.currentTimeMillis()
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val sevenDaysAgo = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()

    fun latestSamsung(rows: List<HealthValue>): HealthValue? =
        rows.filter { it.source == MiniMetricsHealthConnect.SOURCE }.maxByOrNull { it.timestampEpochMs }

    val heart = latestSamsung(NativeDataHub.between(HealthDomain.EXERCISE, "heart_rate_bpm", sevenDaysAgo, now))
    val stepRows = NativeDataHub.between(HealthDomain.EXERCISE, "steps", sevenDaysAgo, now)
        .filter { it.source == MiniMetricsHealthConnect.SOURCE && it.metadata["summaryDate"] != null }
    val steps = latestSamsung(stepRows.filter { it.timestampEpochMs >= todayStart })
    val latestStepPerDay = stepRows.groupBy { it.metadata["summaryDate"].orEmpty() }
        .values
        .mapNotNull { rows -> rows.maxByOrNull { it.timestampEpochMs } }
    val steps7dAverage = latestStepPerDay.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()
    val oxygen = latestSamsung(NativeDataHub.between(HealthDomain.BODY, "blood_oxygen_percent", sevenDaysAgo, now))

    suspend fun fallback(vararg names: String): Double? {
        names.forEach { name -> NativeDataHub.latest(name)?.value?.let { return it } }
        return null
    }

    val stress = fallback("stress_score", "stress_level")
    return MiniMetricSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        heartRateTimestampMs = heart?.timestampEpochMs,
        steps = steps?.value?.roundToInt(),
        steps7dAverage = steps7dAverage,
        bloodOxygenPct = oxygen?.value?.roundToInt(),
        bloodOxygenTimestampMs = oxygen?.timestampEpochMs,
        stressScore = stress?.let { if (it <= 10.0) (it * 10.0).roundToInt() else it.roundToInt() }
    )
}

private suspend fun loadMiniMetricDetail(metric: HomeMiniMetric): MiniMetricDetailData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val now = System.currentTimeMillis()

    suspend fun latestSamsung(domain: HealthDomain, metricName: String, from: Long): HealthValue? =
        NativeDataHub.between(domain, metricName, from, now)
            .filter { it.source == MiniMetricsHealthConnect.SOURCE }
            .maxByOrNull { it.timestampEpochMs }

    suspend fun summary(domain: HealthDomain, metricName: String, date: LocalDate): Double? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return NativeDataHub.between(domain, metricName, start, end)
            .filter { it.source == MiniMetricsHealthConnect.SOURCE && it.metadata["summaryDate"] == date.toString() }
            .maxByOrNull { it.timestampEpochMs }
            ?.value
    }

    val sevenDaysAgo = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
    return when (metric) {
        HomeMiniMetric.HEART_RATE -> {
            val current = latestSamsung(HealthDomain.EXERCISE, "heart_rate_bpm", sevenDaysAgo)?.value
            val avg = summary(HealthDomain.EXERCISE, "heart_rate_avg_bpm", today)
            val min = summary(HealthDomain.EXERCISE, "heart_rate_min_bpm", today)
            val max = summary(HealthDomain.EXERCISE, "heart_rate_max_bpm", today)
            val history = dates.map { summary(HealthDomain.EXERCISE, "heart_rate_avg_bpm", it) }
            MiniMetricDetailData(
                current = current,
                unit = "bpm",
                primaryLabel = "TODAY AVG",
                primaryValue = avg?.roundToInt()?.let { "$it bpm" } ?: "—",
                secondaryLabel = "TODAY RANGE",
                secondaryValue = if (min != null && max != null) "${min.roundToInt()}–${max.roundToInt()}" else "—",
                history = history,
                hasSamsungData = current != null || history.any { it != null }
            )
        }
        HomeMiniMetric.STEPS -> {
            val history = dates.map { summary(HealthDomain.EXERCISE, "steps", it) }
            val current = history.lastOrNull()
            val average = history.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            MiniMetricDetailData(
                current = current,
                unit = "steps",
                primaryLabel = "7 DAY AVG",
                primaryValue = average?.roundToInt()?.let(::compactCount) ?: "—",
                secondaryLabel = "TODAY",
                secondaryValue = current?.roundToInt()?.let(::compactCount) ?: "—",
                history = history,
                hasSamsungData = history.any { it != null }
            )
        }
        HomeMiniMetric.BLOOD_OXYGEN -> {
            val current = latestSamsung(HealthDomain.BODY, "blood_oxygen_percent", sevenDaysAgo)?.value
            val avg = summary(HealthDomain.BODY, "blood_oxygen_avg_percent", today)
            val min = summary(HealthDomain.BODY, "blood_oxygen_min_percent", today)
            val max = summary(HealthDomain.BODY, "blood_oxygen_max_percent", today)
            val history = dates.map { summary(HealthDomain.BODY, "blood_oxygen_avg_percent", it) }
            MiniMetricDetailData(
                current = current,
                unit = "%",
                primaryLabel = "TODAY AVG",
                primaryValue = avg?.let { "${it.roundToInt()}%" } ?: "—",
                secondaryLabel = "TODAY RANGE",
                secondaryValue = if (min != null && max != null) "${min.roundToInt()}–${max.roundToInt()}%" else "—",
                history = history,
                hasSamsungData = current != null || history.any { it != null }
            )
        }
        HomeMiniMetric.STRESS -> {
            val raw = NativeDataHub.latest("stress_score")?.value ?: NativeDataHub.latest("stress_level")?.value
            val current = raw?.let { if (it <= 10.0) it * 10.0 else it }
            MiniMetricDetailData(
                current = current,
                unit = "/100",
                primaryLabel = "SOURCE",
                primaryValue = if (current != null) "Local" else "—",
                secondaryLabel = "SAMSUNG",
                secondaryValue = "Not shared",
                history = emptyList(),
                hasSamsungData = false
            )
        }
    }
}

private fun freshnessLabel(timestampMs: Long?): String {
    if (timestampMs == null) return "Samsung Health"
    val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = ageMs / 60_000L
    return when {
        minutes <= 1L -> "just updated"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        else -> "saved history"
    }
}

private fun compactCount(value: Int): String = when {
    value >= 100_000 -> "${value / 1000}k"
    value >= 10_000 -> "%.1fk".format(value / 1000.0)
    else -> "%,d".format(value)
}
