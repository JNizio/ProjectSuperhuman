package com.projectsuperhuman.next

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val CardioAccent get() = superhumanGreen
private val CardioBlue get() = superhumanBlue
private val CardioInk get() = superhumanTextPrimary
private val CardioMuted get() = superhumanTextMuted
private val CardioBg get() = superhumanBackground
private val CardioSurface get() = superhumanSurface
private val CardioSoft get() = superhumanSurfaceSoft
private val CardioBorder get() = superhumanBorder
private val CardioData = NativeDomainData.forDomain(HealthDomain.EXERCISE)
private val cardioDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal enum class CardioActivityType(
    val displayName: String,
    val supportsDistance: Boolean,
    val paceMode: CardioPaceMode = CardioPaceMode.NONE,
    val supportsCadence: Boolean = false,
    val supportsElevation: Boolean = false
) {
    WALKING("Walking", true, CardioPaceMode.PER_KM, true, true),
    RUNNING("Running", true, CardioPaceMode.PER_KM, true, true),
    TREADMILL("Treadmill", true, CardioPaceMode.PER_KM, true, false),
    CYCLING("Cycling", true, CardioPaceMode.SPEED, true, true),
    STATIONARY_BIKE("Stationary Bike", true, CardioPaceMode.SPEED, true, false),
    ROWING("Rowing", true, CardioPaceMode.PER_500M, true, false),
    ELLIPTICAL("Elliptical", true, CardioPaceMode.SPEED, false, false),
    STAIR_CLIMBER("Stair Climber", false),
    SWIMMING("Swimming", true, CardioPaceMode.PER_100M, false, false),
    HIKING("Hiking", true, CardioPaceMode.PER_KM, false, true),
    JUMP_ROPE("Jump Rope", false, CardioPaceMode.NONE, true, false),
    HIIT("HIIT", false),
    GENERAL_CARDIO("General Cardio", false),
    CUSTOM("Custom", true, CardioPaceMode.SPEED, false, true);

    companion object {
        fun fromStored(raw: String?): CardioActivityType =
            entries.firstOrNull { it.name == raw } ?: GENERAL_CARDIO
    }
}

internal enum class CardioPaceMode { NONE, PER_KM, SPEED, PER_500M, PER_100M }

internal data class CardioSession(
    val id: String,
    val activity: CardioActivityType,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val distanceKm: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val minHeartRate: Int? = null,
    val caloriesKcal: Double? = null,
    val avgPaceSecPerKm: Int? = null,
    val bestPaceSecPerKm: Int? = null,
    val avgSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val elevationGainM: Double? = null,
    val cadence: Int? = null,
    val rpe: Double? = null,
    val notes: String = "",
    val source: String = "manual",
    val zoneSeconds: Map<Int, Int> = emptyMap(),
    val avgSplit500mSeconds: Int? = null,
    val avgPace100mSeconds: Int? = null
)

private data class CardioLiveDraft(
    val activity: CardioActivityType,
    val startedAt: Long,
    val accumulatedSeconds: Int,
    val runningSinceEpochMs: Long,
    val isRunning: Boolean
)

private enum class CardioScreen {
    HOME, PICK_ACTIVITY, LIVE, MANUAL, HISTORY, DETAIL, PROGRESS, RECORDS
}

private fun cardioFormatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun cardioFormatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun cardioFormatPace(seconds: Int?): String =
    seconds?.takeIf { it > 0 }?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "-"

private fun cardioSessionFromValue(row: HealthValue): CardioSession {
    val meta = row.metadata
    fun d(key: String) = meta[key]?.toDoubleOrNull()
    fun i(key: String) = meta[key]?.toIntOrNull()
    val ended = meta["endedAt"]?.toLongOrNull() ?: row.timestampEpochMs
    val duration = i("durationSeconds") ?: (row.value * 60.0).roundToInt().coerceAtLeast(0)
    val started = meta["startedAt"]?.toLongOrNull() ?: (ended - duration * 1000L)
    val zones = (1..5).mapNotNull { zone ->
        i("zone${zone}Seconds")?.takeIf { it > 0 }?.let { zone to it }
    }.toMap()
    return CardioSession(
        id = meta["sessionId"].orEmpty().ifBlank { "legacy-cardio-${row.timestampEpochMs}" },
        activity = CardioActivityType.fromStored(meta["activityType"]),
        startedAt = started,
        endedAt = ended,
        durationSeconds = duration,
        distanceKm = d("distanceKm"),
        avgHeartRate = i("avgHeartRate"),
        maxHeartRate = i("maxHeartRate"),
        minHeartRate = i("minHeartRate"),
        caloriesKcal = d("caloriesKcal"),
        avgPaceSecPerKm = i("avgPaceSecPerKm"),
        bestPaceSecPerKm = i("bestPaceSecPerKm"),
        avgSpeedKmh = d("avgSpeedKmh"),
        maxSpeedKmh = d("maxSpeedKmh"),
        elevationGainM = d("elevationGainM"),
        cadence = i("cadence"),
        rpe = d("rpe"),
        notes = meta["notes"].orEmpty(),
        source = meta["cardioSource"].orEmpty().ifBlank { row.source },
        zoneSeconds = zones,
        avgSplit500mSeconds = i("avgSplit500mSeconds"),
        avgPace100mSeconds = i("avgPace100mSeconds")
    )
}

private fun CardioSession.toHealthValue(): HealthValue {
    val meta = mutableMapOf(
        "sessionId" to id,
        "sourceRecordId" to "cardio:$id",
        "activityType" to activity.name,
        "activityName" to activity.displayName,
        "startedAt" to startedAt.toString(),
        "endedAt" to endedAt.toString(),
        "durationSeconds" to durationSeconds.toString(),
        "notes" to notes,
        "cardioSource" to source
    )
    distanceKm?.let { meta["distanceKm"] = it.toString() }
    avgHeartRate?.let { meta["avgHeartRate"] = it.toString() }
    maxHeartRate?.let { meta["maxHeartRate"] = it.toString() }
    minHeartRate?.let { meta["minHeartRate"] = it.toString() }
    caloriesKcal?.let { meta["caloriesKcal"] = it.toString() }
    avgPaceSecPerKm?.let { meta["avgPaceSecPerKm"] = it.toString() }
    bestPaceSecPerKm?.let { meta["bestPaceSecPerKm"] = it.toString() }
    avgSpeedKmh?.let { meta["avgSpeedKmh"] = it.toString() }
    maxSpeedKmh?.let { meta["maxSpeedKmh"] = it.toString() }
    elevationGainM?.let { meta["elevationGainM"] = it.toString() }
    cadence?.let { meta["cadence"] = it.toString() }
    rpe?.let { meta["rpe"] = it.toString() }
    avgSplit500mSeconds?.let { meta["avgSplit500mSeconds"] = it.toString() }
    avgPace100mSeconds?.let { meta["avgPace100mSeconds"] = it.toString() }
    zoneSeconds.forEach { (zone, seconds) -> meta["zone${zone}Seconds"] = seconds.toString() }

    return HealthValue(
        domain = HealthDomain.EXERCISE,
        metric = "cardio_session",
        value = durationSeconds / 60.0,
        unit = "min",
        timestampEpochMs = endedAt,
        source = "native-cardio",
        metadata = meta
    )
}

private fun cardioPrefs(context: Context) = context.getSharedPreferences("superhuman_cardio", 0)

private fun loadCardioDraft(context: Context): CardioLiveDraft? {
    val prefs = cardioPrefs(context)
    val startedAt = prefs.getLong("live_started_at", 0L)
    if (startedAt <= 0L) return null
    return CardioLiveDraft(
        activity = CardioActivityType.fromStored(prefs.getString("live_activity", null)),
        startedAt = startedAt,
        accumulatedSeconds = prefs.getInt("live_accumulated_seconds", 0).coerceAtLeast(0),
        runningSinceEpochMs = prefs.getLong("live_running_since", 0L),
        isRunning = prefs.getBoolean("live_is_running", false)
    )
}

private fun saveCardioDraft(context: Context, draft: CardioLiveDraft) {
    cardioPrefs(context).edit()
