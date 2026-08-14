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

private val VitalsInk = Color(0xFF123D70)
private val VitalsMuted = Color(0xFF748294)
private val VitalsBlue = Color(0xFF0D6CB4)
private val VitalsRed = Color(0xFFD46072)
private val VitalsBorder = Color(0xFFDCE7EE)

internal data class HomeVitalsSnapshot(
    val heartRateBpm: Int? = null,
    val heartRateTimestampMs: Long? = null,
    val systolicMmhg: Int? = null,
    val diastolicMmhg: Int? = null,
    val bloodPressureTimestampMs: Long? = null,
    val bodyTemperatureCelsius: Double? = null,
    val bodyTemperatureTimestampMs: Long? = null
)

/**
 * Home reads the established Data Vault vocabulary only. Agent 5 should assign
 * [bodyTemperature] to its canonical metric once that metric exists on the integration branch.
 */
internal object HomeVitalsDataContract {
    const val HEART_RATE = "heart_rate_bpm"
    const val HEART_RATE_AVERAGE = "heart_rate_avg_bpm"
    const val BLOOD_PRESSURE_SYSTOLIC = "blood_pressure_systolic_mmhg"
    const val BLOOD_PRESSURE_DIASTOLIC = "blood_pressure_diastolic_mmhg"

    data class MetricLocation(val domain: HealthDomain, val metric: String)

    // Deliberately unset on 11.2: there is no canonical body-temperature metric yet.
    val bodyTemperature: MetricLocation? = null
}

@Composable
internal fun HomeVitalsTile(onClick: () -> Unit) {
    var snapshot by remember { mutableStateOf(HomeVitalsSnapshot()) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = loadHomeVitalsSnapshot()
            delay(5_000L)
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
                    listOf(Color.White, Color(0xFFF7FBFE), Color(0xFFF5FAFC))
                ),
                RoundedCornerShape(27.dp)
            )
            .border(1.dp, VitalsBorder, RoundedCornerShape(27.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibility }
            .padding(18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(28.dp).height(28.dp)
                            .background(VitalsRed.copy(alpha = .10f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♥", color = VitalsRed, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("VITALS", color = VitalsInk, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                        Text("Latest health readings", color = VitalsMuted, fontSize = 8.sp)
                    }
                }
                Text("OPEN  →", color = VitalsBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                HeartRateSection(snapshot, Modifier.weight(.95f))
                Box(Modifier.width(1.dp).height(102.dp).background(VitalsBorder))
                Column(
                    Modifier.weight(1.15f).padding(start = 16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    BloodPressureSection(snapshot)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(VitalsBorder.copy(alpha = .8f)))
                    TemperatureSection(snapshot)
                }
            }
        }
    }
}

@Composable
private fun HeartRateSection(snapshot: HomeVitalsSnapshot, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("HEART RATE", color = VitalsMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        Spacer(Modifier.height(3.dp))
        if (snapshot.heartRateBpm != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(snapshot.heartRateBpm.toString(), color = VitalsInk, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(5.dp))
                Text("BPM", color = VitalsRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
        } else {
            Text("No reading yet", color = VitalsInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        PulseTrace(Modifier.fillMaxWidth(.82f).height(22.dp))
        Text(
            vitalsFreshness("Heart rate", snapshot.heartRateTimestampMs),
            color = VitalsMuted,
            fontSize = 7.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun BloodPressureSection(snapshot: HomeVitalsSnapshot) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text("BLOOD PRESSURE", color = VitalsMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .72.sp)
        Spacer(Modifier.height(3.dp))
        if (snapshot.systolicMmhg != null && snapshot.diastolicMmhg != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${snapshot.systolicMmhg} / ${snapshot.diastolicMmhg}", color = VitalsInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(5.dp))
                Text("mmHg", color = VitalsBlue, fontSize = 7.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        } else {
            Text("No reading yet", color = VitalsInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(vitalsFreshness("BP", snapshot.bloodPressureTimestampMs), color = VitalsMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun TemperatureSection(snapshot: HomeVitalsSnapshot) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text("BODY TEMPERATURE", color = VitalsMuted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .66.sp)
        Spacer(Modifier.height(3.dp))
        if (snapshot.bodyTemperatureCelsius != null) {
            Text(
                String.format(Locale.US, "%.1f°C", snapshot.bodyTemperatureCelsius),
                color = VitalsInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        } else {
            Text("No reading yet", color = VitalsInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(vitalsFreshness("Temp", snapshot.bodyTemperatureTimestampMs), color = VitalsMuted, fontSize = 7.sp, maxLines = 1)
    }
}

@Composable
private fun PulseTrace(modifier: Modifier) {
    Canvas(modifier) {
        drawLine(VitalsRed.copy(alpha = .12f), Offset(0f, size.height * .55f), Offset(size.width, size.height * .55f), strokeWidth = 2f)
        val path = Path().apply {
            moveTo(0f, size.height * .55f)
            lineTo(size.width * .22f, size.height * .55f)
            lineTo(size.width * .31f, size.height * .25f)
            lineTo(size.width * .40f, size.height * .87f)
            lineTo(size.width * .51f, size.height * .42f)
            lineTo(size.width * .62f, size.height * .55f)
            lineTo(size.width, size.height * .55f)
        }
        drawPath(path, VitalsRed.copy(alpha = .78f), style = Stroke(width = 3f))
    }
}

internal fun selectHomeVitalsSnapshot(
    heartRows: List<HealthValue>,
    bloodPressureRows: List<HealthValue>,
    temperatureRows: List<HealthValue>
): HomeVitalsSnapshot {
    fun latest(rows: List<HealthValue>, metric: String): HealthValue? =
        rows.filter { it.metric == metric }.maxByOrNull { it.timestampEpochMs }

    val heart = latest(heartRows, HomeVitalsDataContract.HEART_RATE)
        ?: latest(heartRows, HomeVitalsDataContract.HEART_RATE_AVERAGE)
    val systolic = latest(bloodPressureRows, HomeVitalsDataContract.BLOOD_PRESSURE_SYSTOLIC)
    val diastolic = latest(bloodPressureRows, HomeVitalsDataContract.BLOOD_PRESSURE_DIASTOLIC)
    val isPairedBloodPressure = systolic != null && diastolic != null &&
        abs(systolic.timestampEpochMs - diastolic.timestampEpochMs) <= 5 * 60_000L
    val temperature = temperatureRows.maxByOrNull { it.timestampEpochMs }

    return HomeVitalsSnapshot(
        heartRateBpm = heart?.value?.roundToInt(),
        heartRateTimestampMs = heart?.timestampEpochMs,
        systolicMmhg = systolic?.value?.roundToInt()?.takeIf { isPairedBloodPressure },
        diastolicMmhg = diastolic?.value?.roundToInt()?.takeIf { isPairedBloodPressure },
        bloodPressureTimestampMs = if (isPairedBloodPressure) {
            listOfNotNull(systolic?.timestampEpochMs, diastolic?.timestampEpochMs).maxOrNull()
        } else {
            null
        },
        bodyTemperatureCelsius = temperature?.value,
        bodyTemperatureTimestampMs = temperature?.timestampEpochMs
    )
}

private suspend fun loadHomeVitalsSnapshot(): HomeVitalsSnapshot {
    val heartRows = NativeDomainData.forDomain(HealthDomain.EXERCISE).latestState()
    val bloodPressureRows = NativeDomainData.forDomain(HealthDomain.BLOOD_PRESSURE).latestState()
    val temperatureRows = HomeVitalsDataContract.bodyTemperature?.let { location ->
        listOfNotNull(NativeDomainData.forDomain(location.domain).latest(location.metric))
    }.orEmpty()
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
