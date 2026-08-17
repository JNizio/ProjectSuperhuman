package com.projectsuperhuman.next

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val VitalsBlue = Color(0xFF0D6CB4)
private val VitalsRed = Color(0xFFD46072)
private const val HOME_VITALS_REFRESH_MS = 15_000L

internal data class HomeVitalsSnapshot(
    val heartRateBpm: Int? = null,
    val heartRateTimestampMs: Long? = null,
    val systolicMmhg: Int? = null,
    val diastolicMmhg: Int? = null,
    val bloodPressureTimestampMs: Long? = null,
    val bodyTemperatureCelsius: Double? = null,
    val bodyTemperatureTimestampMs: Long? = null
)

internal object HomeVitalsDataContract {
    const val HEART_RATE = "heart_rate_bpm"
    const val HEART_RATE_AVERAGE = "heart_rate_avg_bpm"
    const val BLOOD_PRESSURE_SYSTOLIC = "blood_pressure_systolic_mmhg"
    const val BLOOD_PRESSURE_DIASTOLIC = "blood_pressure_diastolic_mmhg"

    data class MetricLocation(val domain: HealthDomain, val metric: String)

    val bodyTemperature = MetricLocation(HealthDomain.BODY, "body_temperature_celsius")
}

@Composable
internal fun HomeVitalsTile(onClick: () -> Unit) {
    var snapshot by remember { mutableStateOf(HomeVitalsSnapshot()) }
    val palette = superhumanPalette
    val dark = SuperhumanAppearance.darkMode
    val accentBlue = if (dark) palette.blue else VitalsBlue

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = loadHomeVitalsSnapshot()
            delay(HOME_VITALS_REFRESH_MS)
        }
    }

    val accessibility = buildString {
        append("Vitals. Heart rate ")
        append(snapshot.heartRateBpm?.let { "$it beats per minute" } ?: "no reading yet")
        append(". Blood pressure ")
        append(
            if (snapshot.systolicMmhg != null && snapshot.diastolicMmhg != null) {
                "${snapshot.systolicMmhg} over ${snapshot.diastolicMmhg} millimetres of mercury"
            } else {
                "no reading yet"
            }
        )
        append(". Body temperature ")
        append(snapshot.bodyTemperatureCelsius?.let { String.format(Locale.US, "%.1f degrees Celsius", it) } ?: "no reading yet")
    }

    Box(
        Modifier.fillMaxWidth()
            .height(190.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.surface,
                        palette.surfaceElevated.copy(alpha = if (dark) .90f else .48f),
                        palette.accentSoft.copy(alpha = if (dark) .66f else .32f)
                    )
                ),
                RoundedCornerShape(27.dp)
            )
            .border(1.dp, palette.border, RoundedCornerShape(27.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibility }
            .padding(18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(28.dp).height(28.dp).background(VitalsRed.copy(alpha = if (dark) .18f else .10f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("♥", color = if (dark) palette.red else VitalsRed, fontSize = 13.sp) }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("VITALS", color = palette.brandText, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                        Text("Latest health readings", color = palette.textMuted, fontSize = 8.sp)
                    }
                }
                Text("OPEN  →", color = accentBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                HeartRateSection(snapshot, Modifier.weight(.95f))
                Box(Modifier.width(1.dp).height(102.dp).background(palette.divider))
                Column(Modifier.weight(1.15f).padding(start = 16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    BloodPressureSection(snapshot)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider.copy(alpha = .8f)))
                    TemperatureSection(snapshot)
                }
            }
        }
    }
}

@Composable
private fun HeartRateSection(snapshot: HomeVitalsSnapshot, modifier: Modifier) {
    val palette = superhumanPalette
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("HEART RATE", color = palette.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(3.dp))
        if (snapshot.heartRateBpm != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(snapshot.heartRateBpm.toString(), color = palette.brandText, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(5.dp))
                Text("BPM", color = palette.red, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
        } else Text("No reading yet", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        PulseTrace(Modifier.fillMaxWidth(.82f).height(22.dp))
        Text(vitalsFreshness("Heart rate", snapshot.heartRateTimestampMs), color = palette.textMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun BloodPressureSection(snapshot: HomeVitalsSnapshot) {
    val palette = superhumanPalette
    val accent = if (SuperhumanAppearance.darkMode) palette.blue else VitalsBlue
    Column(Modifier.padding(vertical = 5.dp)) {
        Text("BLOOD PRESSURE", color = palette.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .72.sp)
        Spacer(Modifier.height(3.dp))
        if (snapshot.systolicMmhg != null && snapshot.diastolicMmhg != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${snapshot.systolicMmhg} / ${snapshot.diastolicMmhg}", color = palette.brandText, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(5.dp))
                Text("mmHg", color = accent, fontSize = 7.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        } else Text("No reading yet", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(vitalsFreshness("BP", snapshot.bloodPressureTimestampMs), color = palette.textMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun TemperatureSection(snapshot: HomeVitalsSnapshot) {
    val palette = superhumanPalette
    Column(Modifier.padding(vertical = 5.dp)) {
        Text("BODY TEMPERATURE", color = palette.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .66.sp)
        Spacer(Modifier.height(3.dp))
        if (snapshot.bodyTemperatureCelsius != null) {
            Text(String.format(Locale.US, "%.1f°C", snapshot.bodyTemperatureCelsius), color = palette.brandText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        } else Text("No reading yet", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(vitalsFreshness("Temp", snapshot.bodyTemperatureTimestampMs), color = palette.textMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun PulseTrace(modifier: Modifier) {
    val red = if (SuperhumanAppearance.darkMode) superhumanPalette.red else VitalsRed
    Canvas(modifier) {
        drawLine(red.copy(alpha = .12f), Offset(0f, size.height * .55f), Offset(size.width, size.height * .55f), strokeWidth = 2f)
        val path = Path().apply {
            moveTo(0f, size.height * .55f)
            lineTo(size.width * .22f, size.height * .55f)
            lineTo(size.width * .31f, size.height * .25f)
            lineTo(size.width * .40f, size.height * .87f)
            lineTo(size.width * .51f, size.height * .42f)
            lineTo(size.width * .62f, size.height * .55f)
            lineTo(size.width, size.height * .55f)
        }
        drawPath(path, red.copy(alpha = .78f), style = Stroke(width = 3f))
    }
}

internal fun selectHomeVitalsSnapshot(
    heartRows: List<HealthValue>,
    bloodPressureRows: List<HealthValue>,
    temperatureRows: List<HealthValue>
): HomeVitalsSnapshot {
    var heart: HealthValue? = null
    for (row in heartRows) {
        if ((row.metric == HomeVitalsDataContract.HEART_RATE || row.metric == HomeVitalsDataContract.HEART_RATE_AVERAGE) &&
            (heart == null || row.timestampEpochMs > heart.timestampEpochMs)
        ) heart = row
    }

    var systolic: HealthValue? = null
    var diastolic: HealthValue? = null
    for (row in bloodPressureRows) {
        when (row.metric) {
            HomeVitalsDataContract.BLOOD_PRESSURE_SYSTOLIC ->
                if (systolic == null || row.timestampEpochMs > systolic.timestampEpochMs) systolic = row
            HomeVitalsDataContract.BLOOD_PRESSURE_DIASTOLIC ->
                if (diastolic == null || row.timestampEpochMs > diastolic.timestampEpochMs) diastolic = row
        }
    }

    var temperature: HealthValue? = null
    for (row in temperatureRows) {
        if (temperature == null || row.timestampEpochMs > temperature.timestampEpochMs) temperature = row
    }

    val isPairedBloodPressure = systolic != null && diastolic != null &&
        abs(systolic.timestampEpochMs - diastolic.timestampEpochMs) <= 5 * 60_000L

    return HomeVitalsSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        heartRateTimestampMs = heart?.timestampEpochMs,
        systolicMmhg = systolic?.value?.roundToInt()?.takeIf { isPairedBloodPressure },
        diastolicMmhg = diastolic?.value?.roundToInt()?.takeIf { isPairedBloodPressure },
        bloodPressureTimestampMs = if (isPairedBloodPressure) listOfNotNull(systolic?.timestampEpochMs, diastolic?.timestampEpochMs).maxOrNull() else null,
        bodyTemperatureCelsius = temperature?.value,
        bodyTemperatureTimestampMs = temperature?.timestampEpochMs
    )
}

private suspend fun loadHomeVitalsSnapshot(): HomeVitalsSnapshot {
    val heartRows = NativeDomainData.forDomain(HealthDomain.EXERCISE).latestState()
    val bloodPressureRows = NativeDomainData.forDomain(HealthDomain.BLOOD_PRESSURE).latestState()
    val location = HomeVitalsDataContract.bodyTemperature
    val temperatureRows = listOfNotNull(NativeDomainData.forDomain(location.domain).latest(location.metric))
    return selectHomeVitalsSnapshot(heartRows, bloodPressureRows, temperatureRows)
}

internal fun vitalsFreshness(label: String, timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
    if (timestampMs == null) return "$label · No reading"
    val ageMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val minutes = ageMs / 60_000L
    if (minutes < 1L) return "$label · Just now"
    if (minutes < 60L) return "$label · ${minutes}m ago"

    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestampMs).atZone(zone)
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
    return when (dateTime.toLocalDate()) {
        today -> "$label · Today ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        today.minusDays(1) -> "$label · Yesterday"
        else -> "$label · ${dateTime.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"
    }
}
