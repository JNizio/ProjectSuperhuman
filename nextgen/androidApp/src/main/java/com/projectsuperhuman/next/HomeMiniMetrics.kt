package com.projectsuperhuman.next

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal enum class HomeMiniMetric(val title: String, val subtitle: String) {
    HEART_RATE("Heart rate", "Pulse & daily range"),
    STEPS("Steps", "Daily movement"),
    BLOOD_OXYGEN("Blood oxygen", "SpO₂ readings"),
    CALORIES("Calories", "Burned vs eaten")
}

private data class MiniMetricSnapshot(
    val heartRateBpm: Int? = null,
    val heartRateTimestampMs: Long? = null,
    val steps: Int? = null,
    val stepsRecentAverage: Int? = null,
    val stepsSourceUpdatedAtMs: Long? = null,
    val bloodOxygenPct: Int? = null,
    val bloodOxygenTimestampMs: Long? = null,
    val caloriesActiveBurned: Int? = null,
    val caloriesTotalBurned: Int? = null,
    val caloriesEaten: Int? = null
)

private data class MiniMetricDetailData(
    val current: Double? = null,
    val unit: String = "",
    val primaryLabel: String = "TODAY",
    val primaryValue: String = "—",
    val secondaryLabel: String = "RANGE",
    val secondaryValue: String = "—",
    val tertiaryLabel: String = "SOURCE",
    val tertiaryValue: String = "—",
    val history: List<Double?> = emptyList(),
    val sourceLabel: String? = null
)

private val MiniNavy get() = superhumanBrandText
private val MiniMuted get() = superhumanTextMuted
private val MiniBorder get() = superhumanBorder
private val MiniHeart get() = if (SuperhumanAppearance.darkMode) Color(0xFFFF8FA3) else Color(0xFFD46072)
private val MiniSteps get() = superhumanBlue
private val MiniOxygen get() = if (SuperhumanAppearance.darkMode) Color(0xFF5ACBE1) else Color(0xFF20A7C4)
private val MiniCalories get() = if (SuperhumanAppearance.darkMode) Color(0xFFFFB15E) else Color(0xFFE08A2E)

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
private fun rememberMiniMetricsForeground(): Boolean {
    val context = LocalContext.current
    val owner = remember(context) { context.findLifecycleOwner() }
    var resumed by remember(owner) {
        mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true)
    }

    DisposableEffect(owner) {
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

@Composable
internal fun HomeDateStrip() {
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    }
    val gradient = if (SuperhumanAppearance.darkMode) {
        listOf(superhumanSurface, superhumanSurfaceSoft, superhumanSurface)
    } else {
        listOf(Color(0xFFF7FBFE), Color(0xFFEEF8FC), Color(0xFFF7FBFE))
    }
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .background(Brush.horizontalGradient(gradient), RoundedCornerShape(20.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(date, color = MiniNavy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .35.sp)
    }
}

@Composable
internal fun HomeMiniMetricsGrid(openMetric: (HomeMiniMetric) -> Unit) {
    val context = LocalContext.current
    val isForeground = rememberMiniMetricsForeground()
    var metrics by remember { mutableStateOf(MiniMetricSnapshot()) }

    LaunchedEffect(isForeground) {
        metrics = loadMiniMetricSnapshot()
        if (!isForeground) return@LaunchedEffect
        val hasHealthConnect = MiniMetricsHealthConnect.hasAnyPermission(context)
        if (hasHealthConnect) {
            MiniMetricsHealthConnect.sync(context)
            metrics = loadMiniMetricSnapshot()
        }
        while (true) {
            delay(5_000L)
            if (hasHealthConnect) MiniMetricsHealthConnect.syncCurrent(context)
            metrics = loadMiniMetricSnapshot()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniMetricCard(
                metric = HomeMiniMetric.STEPS,
                value = metrics.steps?.let(::compactCount) ?: "—",
                unit = "",
                status = if (metrics.steps != null) {
                    val age = freshnessLabel(metrics.stepsSourceUpdatedAtMs)
                    metrics.stepsRecentAverage?.let { "$age · avg ${compactCount(it)}" } ?: age
                } else "Tap to connect",
                accent = MiniSteps,
                modifier = Modifier.weight(1f),
                style = MiniVisualStyle.DOTS,
                onClick = { openMetric(HomeMiniMetric.STEPS) }
            )
            MiniMetricCard(
                metric = HomeMiniMetric.BLOOD_OXYGEN,
                value = metrics.bloodOxygenPct?.toString() ?: "—",
                unit = if (metrics.bloodOxygenPct != null) "%" else "",
                status = if (metrics.bloodOxygenPct != null) freshnessLabel(metrics.bloodOxygenTimestampMs) else "Tap to connect",
                accent = MiniOxygen,
                modifier = Modifier.weight(1f),
                style = MiniVisualStyle.RING,
                onClick = { openMetric(HomeMiniMetric.BLOOD_OXYGEN) }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniMetricCard(
                metric = HomeMiniMetric.HEART_RATE,
                value = metrics.heartRateBpm?.toString() ?: "—",
                unit = if (metrics.heartRateBpm != null) "bpm" else "",
                status = if (metrics.heartRateBpm != null) freshnessLabel(metrics.heartRateTimestampMs) else "Tap to connect",
                accent = MiniHeart,
                modifier = Modifier.weight(1f),
                style = MiniVisualStyle.PULSE,
                onClick = { openMetric(HomeMiniMetric.HEART_RATE) }
            )
            MiniMetricCard(
                metric = HomeMiniMetric.CALORIES,
                value = metrics.caloriesActiveBurned?.let(::compactCount) ?: "—",
                unit = if (metrics.caloriesActiveBurned != null) "kcal" else "",
                status = if (metrics.caloriesActiveBurned != null) {
                    val eaten = compactCount(metrics.caloriesEaten ?: 0)
                    val total = metrics.caloriesTotalBurned?.let(::compactCount)
                    if (total != null) "$eaten eaten · $total total" else "$eaten eaten"
                } else {
                    metrics.caloriesEaten?.let { "${compactCount(it)} eaten · connect burn" } ?: "Tap to connect"
                },
                accent = MiniCalories,
                modifier = Modifier.weight(1f),
                style = MiniVisualStyle.WAVES,
                onClick = { openMetric(HomeMiniMetric.CALORIES) }
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
            .background(Brush.linearGradient(listOf(superhumanSurfaceElevated, accent.copy(alpha = if (SuperhumanAppearance.darkMode) .11f else .065f))), RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = if (SuperhumanAppearance.darkMode) .28f else .18f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(metric.title.uppercase(), color = MiniMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .85.sp)
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
        MiniMetricVisual(style, accent, Modifier.align(Alignment.BottomEnd).width(52.dp).height(28.dp))
    }
}

@Composable
private fun MiniMetricVisual(style: MiniVisualStyle, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        when (style) {
            MiniVisualStyle.PULSE -> {
                val path = Path().apply {
                    moveTo(0f, size.height * .62f); lineTo(size.width * .24f, size.height * .62f)
                    lineTo(size.width * .36f, size.height * .30f); lineTo(size.width * .49f, size.height * .84f)
                    lineTo(size.width * .63f, size.height * .48f); lineTo(size.width, size.height * .48f)
                }
                drawPath(path, accent.copy(alpha = .7f), style = Stroke(width = 3f))
            }
            MiniVisualStyle.DOTS -> listOf(.18f, .48f, .78f).forEachIndexed { index, x ->
                drawCircle(accent.copy(alpha = .30f + index * .18f), 5f + index * 2f, Offset(size.width * x, size.height * (.66f - index * .10f)))
            }
            MiniVisualStyle.RING -> {
                drawCircle(accent.copy(alpha = .16f), size.minDimension * .38f, center, style = Stroke(width = 5f))
                drawArc(accent.copy(alpha = .72f), -90f, 250f, false, style = Stroke(width = 5f))
            }
            MiniVisualStyle.WAVES -> repeat(3) { row ->
                val y = size.height * (.28f + row * .24f)
                drawLine(accent.copy(alpha = .24f + row * .17f), Offset(size.width * .08f, y), Offset(size.width * (.72f + row * .08f), y), strokeWidth = 3f)
            }
        }
    }
}

@Composable
internal fun NativeMiniMetricPlaceholderPage(metric: HomeMiniMetric, onBack: () -> Unit) {
    val context = LocalContext.current
    val isForeground = rememberMiniMetricsForeground()
    val accent = accentFor(metric)

    var detail by remember(metric) { mutableStateOf(MiniMetricDetailData()) }
    var syncing by remember(metric) { mutableStateOf(false) }
    var connected by remember(metric) { mutableStateOf(false) }
    var status by remember(metric) { mutableStateOf("Checking connected data sources…") }

    suspend fun refresh() {
        detail = loadMiniMetricDetail(metric)
    }

    suspend fun syncExistingSources() {
        if (!MiniMetricsHealthConnect.hasPermission(context, metric)) {
            connected = false
            status = "Health Connect is not connected · manage sources in Smart Devices"
            refresh()
            return
        }
        syncing = true
        val result = MiniMetricsHealthConnect.sync(context)
        val advanced = if (metric == HomeMiniMetric.HEART_RATE && result.success) {
            HeartRateAdvancedHealthConnect.sync(context)
        } else null
        syncing = false
        connected = true
        status = advanced?.message ?: result.message
        refresh()
    }

    LaunchedEffect(metric) {
        refresh()
        val available = MiniMetricsHealthConnect.availability(context) == HealthConnectClient.SDK_AVAILABLE
        connected = available && MiniMetricsHealthConnect.hasPermission(context, metric)
        status = when {
            !available -> "Health Connect is unavailable on this device"
            connected -> "Health Connect available as historical/backfill source"
            else -> "Manage external data sources in Settings → Smart Devices"
        }
        if (connected) syncExistingSources()
    }

    LaunchedEffect(metric, connected, isForeground) {
        if (!connected || !isForeground) return@LaunchedEffect
        val intervalMs = when (metric) {
            HomeMiniMetric.HEART_RATE, HomeMiniMetric.STEPS -> 5_000L
            HomeMiniMetric.BLOOD_OXYGEN -> 15_000L
            HomeMiniMetric.CALORIES -> 10_000L
        }
        while (true) {
            delay(intervalMs)
            MiniMetricsHealthConnect.syncCurrent(context, metric)
            refresh()
        }
    }

    LaunchedEffect(metric, isForeground) {
        if (!isForeground) return@LaunchedEffect
        while (true) {
            delay(3_000L)
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().background(superhumanBackground).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MiniMetricHeader(metric, onBack)
        MiniMetricHero(metric, detail, accent)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStat(detail.primaryLabel, detail.primaryValue, Modifier.weight(1f))
            MetricStat(detail.secondaryLabel, detail.secondaryValue, Modifier.weight(1f))
            MetricStat(detail.tertiaryLabel, detail.tertiaryValue, Modifier.weight(1f))
        }

        if (metric == HomeMiniMetric.HEART_RATE) {
            HeartRateDeviceSourcesCard()
            AdvancedHeartRateSection(syncing = syncing, refreshSignal = status)
        } else {
            MetricSourcesReadOnlyCard(
                sourceLabel = detail.sourceLabel,
                status = status,
                healthConnectConnected = connected
            )
        }

        MiniMetricHistoryCard(metric, detail.history, accent)
        ManageDevicesShortcut()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun HeartRateDeviceSourcesCard() {
    val context = LocalContext.current
    SmartDeviceRuntime.initialize(context)

    val h19c by H19cWearableRuntime.state.collectAsState()
    val ble by CardioSensorRuntime.bleSensorState.collectAsState()
    var historical by remember { mutableStateOf<HealthValue?>(null) }
    var enabledH19cForPage by remember { mutableStateOf(false) }

    LaunchedEffect(h19c.connected) {
        if (h19c.connected && !h19c.liveHeartRate) {
            enabledH19cForPage = true
            H19cWearableRuntime.setLiveHeartRate(true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (
                enabledH19cForPage &&
                H19cWearableRuntime.state.value.connected &&
                !(CardioSensorRuntime.hasActiveSession() &&
                    CardioSensorRuntime.preferredProviderType() == CardioSensorProviderType.H19C)
            ) {
                H19cWearableRuntime.setLiveHeartRate(false)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            historical = NativeDataHub.between(
                HealthDomain.EXERCISE,
                "heart_rate_bpm",
                now - 7L * 24L * 60L * 60L * 1000L,
                now
            ).filter { it.source == MiniMetricsHealthConnect.SOURCE }
                .maxByOrNull { it.timestampEpochMs }
            delay(3_000L)
        }
    }

    val h19cReading = SmartDeviceObservationMapper.h19cHeartRate(h19c)
    val bleReading = SmartDeviceObservationMapper.bleHeartRate(ble)
    val historicalReading = historical?.let(SmartDeviceObservationMapper::historicalHeartRate)
    val hasAnyDirect = h19c.deviceAddress != null || CardioSensorRuntime.hasSavedBleDevice()

    Column(
        Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(22.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HEART-RATE SOURCES", color = MiniMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Live devices stay separate", color = MiniNavy, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    "Each reading keeps its device, measurement time and data path.",
                    color = MiniMuted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
            Box(
                Modifier.background(superhumanAccentSoft, RoundedCornerShape(13.dp))
                    .clickable { SmartDevicesNavigationBridge.open?.invoke() }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("MANAGE", color = MiniSteps, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }

        if (h19c.deviceAddress != null) {
            HeartRateSourceRow(
                name = h19c.deviceName ?: "H19C",
                reading = h19cReading,
                stateLabel = directStateLabel(h19c.connected, h19cReading?.ageMs()),
                path = "Direct BLE · H19C / FEEA"
            )
        }

        if (CardioSensorRuntime.hasSavedBleDevice() || ble.provenance != null) {
            HeartRateSourceRow(
                name = ble.provenance?.deviceName ?: CardioSensorRuntime.savedBleDeviceName() ?: "Bluetooth HR sensor",
                reading = bleReading,
                stateLabel = when (ble.connection) {
                    CardioSensorConnectionState.CONNECTED -> directStateLabel(true, bleReading?.ageMs())
                    CardioSensorConnectionState.STALE -> "STALE"
                    CardioSensorConnectionState.CONNECTING -> "CONNECTING"
                    CardioSensorConnectionState.RECONNECTING -> "RECONNECTING"
                    CardioSensorConnectionState.ERROR -> "ERROR"
                    else -> "DISCONNECTED"
                },
                path = "Direct Bluetooth · Heart Rate Service"
            )
        }

        historicalReading?.let { reading ->
            HeartRateSourceRow(
                name = reading.provenance.device.displayName ?: "Samsung Health / Health Connect",
                reading = reading,
                stateLabel = "HISTORICAL",
                path = "Samsung Health → Health Connect",
                historical = true
            )
        }

        if (!hasAnyDirect && historicalReading == null) {
            Text(
                "No heart-rate source is configured yet. Add a live sensor or health service from Smart Devices.",
                color = MiniMuted,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun HeartRateSourceRow(
    name: String,
    reading: SmartDeviceReading?,
    stateLabel: String,
    path: String,
    historical: Boolean = false
) {
    val measuredAt = reading?.measuredAtEpochMs
    val receivedAt = reading?.receivedAtEpochMs
    val importedAt = reading?.importedAtEpochMs
    val bpm = reading?.value?.roundToInt()
    val stateAccent = when {
        stateLabel == "LIVE" -> superhumanGreen
        stateLabel == "HISTORICAL" -> MiniSteps
        stateLabel == "STALE" -> MiniCalories
        else -> MiniMuted
    }

    Column(
        Modifier.fillMaxWidth().background(superhumanSurfaceSoft, RoundedCornerShape(16.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = MiniNavy, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(path, color = MiniMuted, fontSize = 8.sp)
            }
            Box(
                Modifier.background(stateAccent.copy(alpha = .12f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(stateLabel, color = stateAccent, fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(bpm?.toString() ?: "—", color = MiniNavy, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(4.dp))
            Text("bpm", color = MiniMuted, fontSize = 8.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        measuredAt?.let {
            val age = observationFreshness(it)
            Text(
                "Measured ${formatObservationClock(it)} · $age",
                color = MiniMuted,
                fontSize = 8.sp
            )
        }

        if (historical && importedAt != null) {
            Text(
                "Imported ${formatObservationClock(importedAt)} · source-reported timestamp",
                color = MiniMuted,
                fontSize = 8.sp
            )
        } else if (reading != null) {
            val latencyMs = if (measuredAt != null && receivedAt != null) {
                (receivedAt - measuredAt).coerceAtLeast(0L)
            } else null
            Text(
                buildString {
                    if (receivedAt != null) {
                        append("Received ").append(formatObservationClockMillis(receivedAt))
                        latencyMs?.let { append(" · latency ").append(it).append(" ms") }
                        append(" · ")
                    }
                    append("timestamp basis ")
                    append(reading.provenance.timing.timeBasis.name.lowercase().replace('_', ' '))
                },
                color = MiniMuted,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun MetricSourcesReadOnlyCard(
    sourceLabel: String?,
    status: String,
    healthConnectConnected: Boolean
) {
    Column(
        Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(20.dp))
            .border(1.dp, MiniBorder, RoundedCornerShape(20.dp)).padding(15.dp)
    ) {
        Text("DATA SOURCE", color = MiniMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(5.dp))
        Text(sourceLabel ?: "No source data yet", color = MiniNavy, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text(status, color = MiniMuted, fontSize = 9.sp, lineHeight = 13.sp)
        if (!healthConnectConnected) {
            Spacer(Modifier.height(5.dp))
            Text("Connections are managed only in Settings → Smart Devices.", color = MiniMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ManageDevicesShortcut() {
    Row(
        Modifier.fillMaxWidth().background(superhumanSurfaceSoft, RoundedCornerShape(16.dp))
            .clickable { SmartDevicesNavigationBridge.open?.invoke() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manage Smart Devices", color = MiniNavy, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("Pairing, permissions, disconnect and forget controls", color = MiniMuted, fontSize = 8.sp)
        }
        Text("→", color = MiniSteps, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun directStateLabel(connected: Boolean, ageMs: Long?): String {
    if (!connected) return "DISCONNECTED"
    if (ageMs == null) return "CONNECTED"
    return if (ageMs <= CARDIO_HR_STALE_AFTER_MS) "LIVE" else "STALE"
}

private fun observationFreshness(timestampEpochMs: Long): String {
    val age = (System.currentTimeMillis() - timestampEpochMs).coerceAtLeast(0L)
    return when {
        age < 1_000L -> "<1s ago"
        age < 60_000L -> "${age / 1_000L}s ago"
        age < 3_600_000L -> "${age / 60_000L}m ago"
        age < 86_400_000L -> "${age / 3_600_000L}h ago"
        else -> "${age / 86_400_000L}d ago"
    }
}

private fun formatObservationClock(timestampEpochMs: Long): String =
    Instant.ofEpochMilli(timestampEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))

private fun formatObservationClockMillis(timestampEpochMs: Long): String =
    Instant.ofEpochMilli(timestampEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))

@Composable
private fun MiniMetricHeader(metric: HomeMiniMetric, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(46.dp).height(46.dp).background(superhumanSurface, RoundedCornerShape(16.dp))
                .border(1.dp, MiniBorder, RoundedCornerShape(16.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = MiniNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(metric.title, color = MiniNavy, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(metric.subtitle, color = MiniMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MiniMetricHero(metric: HomeMiniMetric, detail: MiniMetricDetailData, accent: Color) {
    val displayValue = detail.current?.let { value -> if (metric == HomeMiniMetric.STEPS) compactCount(value.roundToInt()) else value.roundToInt().toString() } ?: "—"
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .17f else .15f), superhumanSurfaceElevated)), RoundedCornerShape(26.dp))
            .border(1.dp, accent.copy(alpha = .20f), RoundedCornerShape(26.dp)).padding(20.dp)
    ) {
        Text(detail.sourceLabel?.uppercase() ?: "NO WEARABLE DATA YET", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(displayValue, color = MiniNavy, fontSize = 38.sp, fontWeight = FontWeight.Black)
            if (detail.unit.isNotBlank() && detail.current != null) {
                Spacer(Modifier.width(6.dp)); Text(detail.unit, color = MiniMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            when (metric) {
                HomeMiniMetric.HEART_RATE -> "Latest heart-rate reading from a connected wearable source"
                HomeMiniMetric.STEPS -> "Wearable steps accumulated today"
                HomeMiniMetric.BLOOD_OXYGEN -> "Latest blood-oxygen reading from a connected wearable source"
                HomeMiniMetric.CALORIES -> "Wearable active burn today compared with food logged in Project Superhuman"
            }, color = MiniMuted, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun MetricStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(superhumanSurface, RoundedCornerShape(18.dp)).border(1.dp, MiniBorder, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text(label, color = MiniMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(6.dp)); Text(value, color = MiniNavy, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MiniMetricHistoryCard(metric: HomeMiniMetric, history: List<Double?>, accent: Color) {
    Column(Modifier.fillMaxWidth().background(superhumanSurface, RoundedCornerShape(22.dp)).border(1.dp, MiniBorder, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text("LAST 7 DAYS", color = MiniMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            when (metric) {
                HomeMiniMetric.HEART_RATE -> "Daily average heart rate"
                HomeMiniMetric.STEPS -> "Daily steps"
                HomeMiniMetric.BLOOD_OXYGEN -> "Daily average SpO₂"
                HomeMiniMetric.CALORIES -> "Daily active calories burned"
            }, color = MiniNavy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(14.dp)); MiniHistoryChart(history, accent); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day -> Text(day, color = MiniMuted, fontSize = 7.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun MiniHistoryChart(values: List<Double?>, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(82.dp)) {
        if (values.isEmpty()) return@Canvas
        val existing = values.filterNotNull()
        if (existing.isEmpty()) { drawLine(MiniBorder, Offset(0f, size.height * .7f), Offset(size.width, size.height * .7f), strokeWidth = 2f); return@Canvas }
        val min = existing.minOrNull() ?: 0.0
        val max = existing.maxOrNull() ?: min
        val range = (max - min).coerceAtLeast(1.0)
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val x = slot * index + slot / 2f
            if (value == null) drawCircle(MiniBorder, 4f, Offset(x, size.height * .82f))
            else {
                val normalized = ((value - min) / range).toFloat()
                val top = size.height * (.82f - normalized * .62f)
                drawLine(accent.copy(alpha = .72f), Offset(x, size.height * .86f), Offset(x, top), strokeWidth = 8f)
                drawCircle(accent, 5f, Offset(x, top))
            }
        }
    }
}

@Composable
private fun UnsupportedStressCard() {
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MiniCalories.copy(alpha = .14f), superhumanSurfaceElevated)), RoundedCornerShape(26.dp))
            .border(1.dp, MiniCalories.copy(alpha = .16f), RoundedCornerShape(26.dp)).padding(20.dp)
    ) {
        Text("SOURCE LIMITATION", color = MiniCalories, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp)); Text("Samsung Stress isn’t exported", color = MiniNavy, fontSize = 21.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(7.dp))
        Text("Samsung Health currently shares steps, heart rate and blood oxygen with Health Connect, but not its proprietary Stress score. Project Superhuman will keep this blank rather than inventing a measurement.", color = MiniMuted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        Text("A future Project Superhuman recovery/stress model can be built separately from sleep, heart rate, HRV and activity with its confidence shown clearly.", color = MiniNavy, fontSize = 9.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun accentFor(metric: HomeMiniMetric): Color = when (metric) {
    HomeMiniMetric.HEART_RATE -> MiniHeart
    HomeMiniMetric.STEPS -> MiniSteps
    HomeMiniMetric.BLOOD_OXYGEN -> MiniOxygen
    HomeMiniMetric.CALORIES -> MiniCalories
}

private fun isSupportedWearableSource(value: HealthValue): Boolean =
    value.source == MiniMetricsHealthConnect.SOURCE || value.source == H19cWearableRuntime.SOURCE

private fun wearableSourceLabel(value: HealthValue?): String? = when (value?.source) {
    H19cWearableRuntime.SOURCE -> "H19C direct"
    MiniMetricsHealthConnect.SOURCE -> "Samsung Health"
    else -> null
}

private suspend fun loadMiniMetricSnapshot(): MiniMetricSnapshot {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val now = System.currentTimeMillis()
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val sevenDaysAgo = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()

    fun latestWearable(rows: List<HealthValue>): HealthValue? = rows.filter(::isSupportedWearableSource).maxByOrNull { it.timestampEpochMs }

    val heart = latestWearable(NativeDataHub.between(HealthDomain.EXERCISE, "heart_rate_bpm", sevenDaysAgo, now))
    val stepRows = NativeDataHub.between(HealthDomain.EXERCISE, "steps", sevenDaysAgo, now).filter { isSupportedWearableSource(it) && it.metadata["summaryDate"] != null }
    val steps = latestWearable(stepRows.filter { it.timestampEpochMs >= todayStart })
    val latestStepPerDay = stepRows.groupBy { it.metadata["summaryDate"].orEmpty() }.values.mapNotNull { rows -> rows.maxByOrNull { it.timestampEpochMs } }
    val completedStepDays = latestStepPerDay.filter { it.metadata["summaryDate"] != today.toString() }
    val stepsRecentAverage = completedStepDays.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.roundToInt()
    val oxygen = latestWearable(NativeDataHub.between(HealthDomain.BODY, "blood_oxygen_percent", sevenDaysAgo, now))
    val caloriesActiveBurned = latestWearable(NativeDataHub.between(HealthDomain.EXERCISE, "calories_burned_active_kcal", todayStart, now))?.value
    val caloriesTotalBurned = latestWearable(NativeDataHub.between(HealthDomain.EXERCISE, "calories_burned_total_kcal", todayStart, now))?.value
    val caloriesEaten = NativeDataHub.between(HealthDomain.NUTRITION, "food_kcal", todayStart, now).sumOf { it.value }

    return MiniMetricSnapshot(
        heartRateBpm = heart?.value?.roundToInt(), heartRateTimestampMs = heart?.timestampEpochMs,
        steps = steps?.value?.roundToInt(), stepsRecentAverage = stepsRecentAverage,
        stepsSourceUpdatedAtMs = steps?.metadata?.get("sourceLastModifiedMs")?.toLongOrNull() ?: steps?.timestampEpochMs,
        bloodOxygenPct = oxygen?.value?.roundToInt(), bloodOxygenTimestampMs = oxygen?.timestampEpochMs,
        caloriesActiveBurned = caloriesActiveBurned?.roundToInt(), caloriesTotalBurned = caloriesTotalBurned?.roundToInt(),
        caloriesEaten = caloriesEaten.takeIf { it > 0.0 }?.roundToInt()
    )
}

private suspend fun loadMiniMetricDetail(metric: HomeMiniMetric): MiniMetricDetailData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val now = System.currentTimeMillis()

    suspend fun latestWearable(domain: HealthDomain, metricName: String, from: Long): HealthValue? =
        NativeDataHub.between(domain, metricName, from, now).filter(::isSupportedWearableSource).maxByOrNull { it.timestampEpochMs }

    suspend fun summaryRow(domain: HealthDomain, metricName: String, date: LocalDate): HealthValue? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return NativeDataHub.between(domain, metricName, start, minOf(end, now))
            .filter { isSupportedWearableSource(it) && it.metadata["summaryDate"] == date.toString() }
            .maxByOrNull { it.timestampEpochMs }
    }

    suspend fun summary(domain: HealthDomain, metricName: String, date: LocalDate): Double? = summaryRow(domain, metricName, date)?.value

    suspend fun directHeartRateValues(date: LocalDate): List<Double> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L, now)
        return NativeDataHub.between(HealthDomain.EXERCISE, "heart_rate_bpm", start, end).filter { it.source == H19cWearableRuntime.SOURCE }.map { it.value }
    }

    val sevenDaysAgo = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
    return when (metric) {
        HomeMiniMetric.HEART_RATE -> {
            val currentRow = latestWearable(HealthDomain.EXERCISE, "heart_rate_bpm", sevenDaysAgo)
            val current = currentRow?.value
            val directToday = directHeartRateValues(today)
            val avg = summary(HealthDomain.EXERCISE, "heart_rate_avg_bpm", today) ?: directToday.takeIf { it.isNotEmpty() }?.average()
            val min = summary(HealthDomain.EXERCISE, "heart_rate_min_bpm", today) ?: directToday.minOrNull()
            val max = summary(HealthDomain.EXERCISE, "heart_rate_max_bpm", today) ?: directToday.maxOrNull()
            val history = dates.map { date -> summary(HealthDomain.EXERCISE, "heart_rate_avg_bpm", date) ?: directHeartRateValues(date).takeIf { it.isNotEmpty() }?.average() }
            MiniMetricDetailData(current, "bpm", "TODAY AVG", avg?.roundToInt()?.let { "$it bpm" } ?: "—", "TODAY RANGE", if (min != null && max != null) "${min.roundToInt()}–${max.roundToInt()}" else "—", "SOURCE", wearableSourceLabel(currentRow) ?: "—", history, wearableSourceLabel(currentRow) ?: if (history.any { it != null }) "Wearable history" else null)
        }
        HomeMiniMetric.STEPS -> {
            val rows = dates.map { summaryRow(HealthDomain.EXERCISE, "steps", it) }
            val history = rows.map { it?.value }; val currentRow = rows.lastOrNull(); val current = currentRow?.value
            val average = history.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            MiniMetricDetailData(current, "steps", "7 DAY AVG", average?.roundToInt()?.let(::compactCount) ?: "—", "TODAY", current?.roundToInt()?.let(::compactCount) ?: "—", "SOURCE", wearableSourceLabel(currentRow) ?: "—", history, wearableSourceLabel(currentRow) ?: rows.asReversed().firstNotNullOfOrNull(::wearableSourceLabel))
        }
        HomeMiniMetric.BLOOD_OXYGEN -> {
            val currentRow = latestWearable(HealthDomain.BODY, "blood_oxygen_percent", sevenDaysAgo); val current = currentRow?.value
            val avg = summary(HealthDomain.BODY, "blood_oxygen_avg_percent", today); val min = summary(HealthDomain.BODY, "blood_oxygen_min_percent", today); val max = summary(HealthDomain.BODY, "blood_oxygen_max_percent", today)
            val history = dates.map { summary(HealthDomain.BODY, "blood_oxygen_avg_percent", it) }
            MiniMetricDetailData(current, "%", "TODAY AVG", avg?.let { "${it.roundToInt()}%" } ?: current?.let { "${it.roundToInt()}%" } ?: "—", "TODAY RANGE", if (min != null && max != null) "${min.roundToInt()}–${max.roundToInt()}%" else "—", "SOURCE", wearableSourceLabel(currentRow) ?: "—", history, wearableSourceLabel(currentRow) ?: if (history.any { it != null }) "Wearable history" else null)
        }
        HomeMiniMetric.CALORIES -> {
            suspend fun eaten(date: LocalDate): Double? {
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli(); val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L, now)
                return NativeDataHub.between(HealthDomain.NUTRITION, "food_kcal", start, end).sumOf { it.value }.takeIf { it > 0.0 }
            }
            val rows = dates.map { summaryRow(HealthDomain.EXERCISE, "calories_burned_active_kcal", it) }; val history = rows.map { it?.value }; val currentRow = rows.lastOrNull(); val current = currentRow?.value
            val eatenToday = eaten(today)
            MiniMetricDetailData(current, "kcal", "ACTIVE", current?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "—", "EATEN", eatenToday?.roundToInt()?.let { "${compactCount(it)} kcal" } ?: "0 kcal", "SOURCE", wearableSourceLabel(currentRow) ?: "—", history, wearableSourceLabel(currentRow) ?: rows.asReversed().firstNotNullOfOrNull(::wearableSourceLabel))
        }
    }
}

private fun freshnessLabel(timestampMs: Long?): String {
    if (timestampMs == null) return "wearable data"
    val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = ageMs / 60_000L
    return when { minutes <= 1L -> "just updated"; minutes < 60L -> "${minutes}m ago"; minutes < 24L * 60L -> "${minutes / 60L}h ago"; else -> "saved history" }
}

private fun compactCount(value: Int): String = when {
    value >= 100_000 -> "${value / 1000}k"
    value >= 10_000 -> "%.1fk".format(value / 1000.0)
    else -> "%,d".format(value)
}
