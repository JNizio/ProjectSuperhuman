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
        .putString("live_activity", draft.activity.name)
        .putLong("live_started_at", draft.startedAt)
        .putInt("live_accumulated_seconds", draft.accumulatedSeconds)
        .putLong("live_running_since", draft.runningSinceEpochMs)
        .putBoolean("live_is_running", draft.isRunning)
        .apply()
}

private fun clearCardioDraft(context: Context) {
    cardioPrefs(context).edit()
        .remove("live_activity")
        .remove("live_started_at")
        .remove("live_accumulated_seconds")
        .remove("live_running_since")
        .remove("live_is_running")
        .apply()
}

@Composable
internal fun NativeCardioScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(CardioScreen.HOME) }
    var rows by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<CardioSession>>(emptyList()) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    var liveActivity by remember { mutableStateOf(CardioActivityType.WALKING) }
    var liveStartedAt by remember { mutableLongStateOf(0L) }
    var liveAccumulatedSeconds by remember { mutableIntStateOf(0) }
    var liveRunningSince by remember { mutableLongStateOf(0L) }
    var liveRunning by remember { mutableStateOf(false) }
    var liveTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var formActivity by remember { mutableStateOf(CardioActivityType.WALKING) }
    var formDateTime by remember { mutableStateOf("") }
    var formDurationMin by remember { mutableStateOf("") }
    var formDistanceKm by remember { mutableStateOf("") }
    var formAvgHr by remember { mutableStateOf("") }
    var formMaxHr by remember { mutableStateOf("") }
    var formCalories by remember { mutableStateOf("") }
    var formCadence by remember { mutableStateOf("") }
    var formElevation by remember { mutableStateOf("") }
    var formRpe by remember { mutableStateOf("") }
    var formNotes by remember { mutableStateOf("") }
    var formSource by remember { mutableStateOf("manual") }
    var formEditingId by remember { mutableStateOf<String?>(null) }
    var formLiveStartedAt by remember { mutableLongStateOf(0L) }
    var showZones by remember { mutableStateOf(false) }
    val zoneMinutes = remember { mutableStateListOf("", "", "", "", "") }

    suspend fun refresh() {
        val now = System.currentTimeMillis()
        val lookback = 5L * 365L * 24L * 60L * 60L * 1000L
        rows = CardioData.between("cardio_session", now - lookback, now)
            .sortedByDescending { it.timestampEpochMs }
            .take(3000)
        sessions = rows.map(::cardioSessionFromValue).sortedByDescending { it.endedAt }
    }

    fun resetForm(activity: CardioActivityType = CardioActivityType.WALKING) {
        formActivity = activity
        formDateTime = LocalDateTime.now().format(cardioDateTimeFormatter)
        formDurationMin = ""
        formDistanceKm = ""
        formAvgHr = ""
        formMaxHr = ""
        formCalories = ""
        formCadence = ""
        formElevation = ""
        formRpe = ""
        formNotes = ""
        formSource = "manual"
        formEditingId = null
        formLiveStartedAt = 0L
        showZones = false
        for (i in zoneMinutes.indices) zoneMinutes[i] = ""
    }

    fun loadSessionIntoForm(session: CardioSession) {
        formActivity = session.activity
        formDateTime = Instant.ofEpochMilli(session.startedAt)
            .atZone(ZoneId.systemDefault()).toLocalDateTime().format(cardioDateTimeFormatter)
        formDurationMin = String.format(Locale.US, "%.1f", session.durationSeconds / 60.0)
        formDistanceKm = session.distanceKm?.let(::cardioFormatNumber).orEmpty()
        formAvgHr = session.avgHeartRate?.toString().orEmpty()
        formMaxHr = session.maxHeartRate?.toString().orEmpty()
        formCalories = session.caloriesKcal?.let(::cardioFormatNumber).orEmpty()
        formCadence = session.cadence?.toString().orEmpty()
        formElevation = session.elevationGainM?.let(::cardioFormatNumber).orEmpty()
        formRpe = session.rpe?.let(::cardioFormatNumber).orEmpty()
        formNotes = session.notes
        formSource = session.source.ifBlank { "manual" }
        formEditingId = session.id
        formLiveStartedAt = 0L
        showZones = session.zoneSeconds.isNotEmpty()
        for (i in zoneMinutes.indices) {
            zoneMinutes[i] = session.zoneSeconds[i + 1]?.let { String.format(Locale.US, "%.1f", it / 60.0) }.orEmpty()
        }
    }

    fun currentLiveElapsedSeconds(): Int {
        val extra = if (liveRunning && liveRunningSince > 0L) {
            ((liveTick - liveRunningSince).coerceAtLeast(0L) / 1000L).toInt()
        } else 0
        return (liveAccumulatedSeconds + extra).coerceAtLeast(0)
    }

    fun persistLiveState() {
        if (liveStartedAt <= 0L) return
        saveCardioDraft(
            context,
            CardioLiveDraft(
                activity = liveActivity,
                startedAt = liveStartedAt,
                accumulatedSeconds = liveAccumulatedSeconds,
                runningSinceEpochMs = liveRunningSince,
                isRunning = liveRunning
            )
        )
    }

    fun startLive(activity: CardioActivityType) {
        val now = System.currentTimeMillis()
        liveActivity = activity
        liveStartedAt = now
        liveAccumulatedSeconds = 0
        liveRunningSince = now
        liveRunning = true
        liveTick = now
        persistLiveState()
        screen = CardioScreen.LIVE
    }

    fun pauseLive() {
        val now = System.currentTimeMillis()
        liveTick = now
        if (liveRunning && liveRunningSince > 0L) {
            liveAccumulatedSeconds += ((now - liveRunningSince).coerceAtLeast(0L) / 1000L).toInt()
        }
        liveRunningSince = 0L
        liveRunning = false
        persistLiveState()
        feedback = "Cardio timer paused"
    }

    fun resumeLive() {
        liveRunningSince = System.currentTimeMillis()
        liveRunning = true
        liveTick = liveRunningSince
        persistLiveState()
        feedback = "Cardio timer resumed"
    }

    fun prepareFinishedLive() {
        val now = System.currentTimeMillis()
        liveTick = now
        val elapsed = currentLiveElapsedSeconds().coerceAtLeast(1)
        if (liveRunning && liveRunningSince > 0L) {
            liveAccumulatedSeconds = elapsed
            liveRunningSince = 0L
            liveRunning = false
            persistLiveState()
        }
        resetForm(liveActivity)
        formDateTime = Instant.ofEpochMilli(liveStartedAt)
            .atZone(ZoneId.systemDefault()).toLocalDateTime().format(cardioDateTimeFormatter)
        formDurationMin = String.format(Locale.US, "%.1f", elapsed / 60.0)
        formSource = "live"
        formLiveStartedAt = liveStartedAt
        screen = CardioScreen.MANUAL
    }

    fun saveForm() {
        val localDateTime = runCatching { LocalDateTime.parse(formDateTime.trim(), cardioDateTimeFormatter) }.getOrNull()
        if (localDateTime == null) {
            feedback = "Use date and time format YYYY-MM-DD HH:MM"
            return
        }
        val durationSeconds = ((formDurationMin.toDoubleOrNull() ?: 0.0) * 60.0).roundToInt()
        if (durationSeconds <= 0) {
            feedback = "Enter a cardio duration"
            return
        }
        val startedAt = if (formLiveStartedAt > 0L) formLiveStartedAt else
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endedAt = startedAt + durationSeconds * 1000L
        val distance = formDistanceKm.toDoubleOrNull()?.takeIf { it > 0.0 }
        val avgHr = formAvgHr.toIntOrNull()?.takeIf { it in 30..250 }
        val maxHr = formMaxHr.toIntOrNull()?.takeIf { it in 30..250 }
        val calories = formCalories.toDoubleOrNull()?.takeIf { it >= 0.0 }
        val cadence = formCadence.toIntOrNull()?.takeIf { it > 0 }
        val elevation = formElevation.toDoubleOrNull()?.takeIf { it >= 0.0 }
        val rpe = formRpe.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
        val zones = zoneMinutes.mapIndexedNotNull { index, text ->
            val sec = ((text.toDoubleOrNull() ?: 0.0) * 60.0).roundToInt()
            if (sec > 0) (index + 1) to sec else null
        }.toMap()
        if (zones.values.sum() > durationSeconds + 60) {
            feedback = "Heart-rate zone time cannot exceed session duration"
            return
        }

        val avgPace = if (distance != null && distance > 0.0 && formActivity.paceMode == CardioPaceMode.PER_KM) {
            (durationSeconds / distance).roundToInt()
        } else null
        val avgSpeed = if (distance != null && distance > 0.0 && formActivity.paceMode == CardioPaceMode.SPEED) {
            distance / (durationSeconds / 3600.0)
        } else null
        val split500 = if (distance != null && distance > 0.0 && formActivity.paceMode == CardioPaceMode.PER_500M) {
            (durationSeconds / (distance * 2.0)).roundToInt()
        } else null
        val pace100 = if (distance != null && distance > 0.0 && formActivity.paceMode == CardioPaceMode.PER_100M) {
            (durationSeconds / (distance * 10.0)).roundToInt()
        } else null

        val existingId = formEditingId
        val id = existingId ?: "cardio-$startedAt-${System.currentTimeMillis()}"
        val session = CardioSession(
            id = id,
            activity = formActivity,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            distanceKm = distance,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            caloriesKcal = calories,
            avgPaceSecPerKm = avgPace,
            avgSpeedKmh = avgSpeed,
            elevationGainM = elevation,
            cadence = cadence,
            rpe = rpe,
            notes = formNotes.trim(),
            source = formSource,
            zoneSeconds = zones,
            avgSplit500mSeconds = split500,
            avgPace100mSeconds = pace100
        )

        scope.launch {
            if (existingId != null) {
                rows.firstOrNull { it.metadata["sessionId"] == existingId }?.let { NativeDataHub.deleteValue(it) }
            }
            NativeDataHub.saveValues(listOf(session.toHealthValue()))
            if (formSource == "live") {
                clearCardioDraft(context)
                liveStartedAt = 0L
                liveAccumulatedSeconds = 0
                liveRunningSince = 0L
                liveRunning = false
            }
            refresh()
            feedback = if (existingId == null) "Cardio session saved" else "Cardio session updated"
            formEditingId = null
            formLiveStartedAt = 0L
            screen = CardioScreen.HOME
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        resetForm()
        loadCardioDraft(context)?.let { draft ->
            liveActivity = draft.activity
            liveStartedAt = draft.startedAt
            liveAccumulatedSeconds = draft.accumulatedSeconds
            liveRunningSince = draft.runningSinceEpochMs
            liveRunning = draft.isRunning
            liveTick = System.currentTimeMillis()
        }
    }

    LaunchedEffect(liveRunning, liveRunningSince) {
        while (liveRunning && liveRunningSince > 0L) {
            liveTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2400)
            feedback = null
        }
    }

    BackHandler {
        when (screen) {
            CardioScreen.HOME -> onBack()
            CardioScreen.LIVE -> screen = CardioScreen.HOME
            else -> screen = CardioScreen.HOME
        }
    }

    val weekStart = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
    val weekSessions = sessions.filter { it.endedAt >= weekStart }
    val weekMinutes = weekSessions.sumOf { it.durationSeconds } / 60
    val weekDistance = weekSessions.mapNotNull { it.distanceKm }.sum()
    val weekZone2 = weekSessions.sumOf { it.zoneSeconds[2] ?: 0 } / 60
    val activeDraft = liveStartedAt > 0L

    Column(
        Modifier.fillMaxSize()
            .background(CardioBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardioHeader(screen) {
            if (screen == CardioScreen.HOME) onBack() else screen = CardioScreen.HOME
        }
        feedback?.let { CardioFeedback(it) }

        when (screen) {
            CardioScreen.HOME -> {
                CardioHero(activeDraft, weekMinutes, weekSessions.size) {
                    if (activeDraft) screen = CardioScreen.LIVE else screen = CardioScreen.PICK_ACTIVITY
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    CardioNavTile("L", "LOG ACTIVITY", "Add a previous session", CardioBlue, Modifier.weight(1f)) {
                        resetForm()
                        screen = CardioScreen.MANUAL
                    }
                    CardioNavTile("H", "HISTORY", "Completed sessions", Color(0xFF7B61C9), Modifier.weight(1f)) {
                        screen = CardioScreen.HISTORY
                    }
                    CardioNavTile("P", "PROGRESS", "Minutes, distance & zones", CardioAccent, Modifier.weight(1f)) {
                        screen = CardioScreen.PROGRESS
                    }
                }
                CardioSection("THIS WEEK", "Cardio completed in the last 7 days") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CardioMetric("MINUTES", weekMinutes.toString(), "cardio", CardioBlue, Modifier.weight(1f))
                        CardioMetric("DISTANCE", cardioFormatNumber(weekDistance), "km logged", CardioAccent, Modifier.weight(1f))
                        CardioMetric("ZONE 2", weekZone2.toString(), "min measured", Color(0xFF7B61C9), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    CardioAction("PERSONAL RECORDS", "Longest sessions and best logged pace or speed", CardioAccent) {
                        screen = CardioScreen.RECORDS
                    }
                }
                sessions.firstOrNull()?.let { latest ->
                    CardioSection("LATEST ACTIVITY", "Your most recent saved cardio session") {
                        CardioSessionRow(latest) {
                            selectedSessionId = latest.id
                            screen = CardioScreen.DETAIL
                        }
                    }
                } ?: CardioSection("GET STARTED", "Your cardio history will build automatically") {
                    CardioEmpty("No cardio sessions yet", "Start a timer or log a previous walk, run, ride or other activity.")
                }
            }

            CardioScreen.PICK_ACTIVITY -> {
                CardioHeroStrip("START CARDIO", "Choose an activity", "The live timer works without GPS or a wearable.", CardioAccent)
                CardioSection("ACTIVITY", "Choose what you are about to do") {
                    CardioActivityPicker(liveActivity) { activity ->
                        liveActivity = activity
                    }
                    Spacer(Modifier.height(10.dp))
                    CardioAction("START ${liveActivity.displayName.uppercase()}", "Begin the live timer", CardioAccent) {
                        startLive(liveActivity)
                    }
                }
            }

            CardioScreen.LIVE -> {
                val elapsed = currentLiveElapsedSeconds()
                CardioLiveHero(liveActivity, elapsed, liveRunning)
                CardioSection("LIVE SESSION", if (liveRunning) "Timer is running" else "Timer is paused") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CardioMetric("TIME", cardioFormatDuration(elapsed), "elapsed", CardioBlue, Modifier.weight(1f))
                        CardioMetric("STATUS", if (liveRunning) "ACTIVE" else "PAUSED", "saved locally", CardioAccent, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    CardioAction(
                        if (liveRunning) "PAUSE" else "RESUME",
                        if (liveRunning) "Stop the timer without ending the session" else "Continue this session",
                        CardioBlue
                    ) {
                        if (liveRunning) pauseLive() else resumeLive()
                    }
                    Spacer(Modifier.height(7.dp))
                    CardioAction("FINISH CARDIO", "Review and add distance, heart rate or notes", CardioAccent) {
                        prepareFinishedLive()
                    }
                }
                CardioSection("SENSOR DATA", "Optional integrations can add these automatically later") {
                    Text(
                        "GPS distance, live heart rate, pace and zone data are intentionally not fabricated. Finish the session to enter anything you measured elsewhere.",
                        color = CardioMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            CardioScreen.MANUAL -> {
                val editing = formEditingId != null
                CardioHeroStrip(
                    if (editing) "EDIT CARDIO" else if (formSource == "live") "FINISH CARDIO" else "LOG CARDIO",
                    if (editing) "Update saved session" else if (formSource == "live") "Add your measured details" else "Log a previous activity",
                    "Only enter data you actually measured. Pace and speed are derived automatically.",
                    CardioBlue
                )
                CardioSection("ACTIVITY", "Choose the session type") {
                    CardioActivityPicker(formActivity) { formActivity = it }
                }
                CardioSection("SESSION", "Duration is required; other fields are optional") {
                    OutlinedTextField(
                        formDateTime,
                        { formDateTime = it.take(16) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Start - YYYY-MM-DD HH:MM") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            formDurationMin,
                            { formDurationMin = it.filter { ch -> ch.isDigit() || ch == '.' }.take(7) },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Duration (min)") }
                        )
                        if (formActivity.supportsDistance) {
                            OutlinedTextField(
                                formDistanceKm,
                                { formDistanceKm = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) },
                                Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Distance (km)") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            formAvgHr,
                            { formAvgHr = it.filter(Char::isDigit).take(3) },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Avg HR") }
                        )
                        OutlinedTextField(
                            formMaxHr,
                            { formMaxHr = it.filter(Char::isDigit).take(3) },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Max HR") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            formCalories,
                            { formCalories = it.filter { ch -> ch.isDigit() || ch == '.' }.take(7) },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Calories (optional)") }
                        )
                        OutlinedTextField(
                            formRpe,
                            { formRpe = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                            Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("RPE 0-10") }
                        )
                    }
                    if (formActivity.supportsCadence || formActivity.supportsElevation) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (formActivity.supportsCadence) {
                                OutlinedTextField(
                                    formCadence,
                                    { formCadence = it.filter(Char::isDigit).take(4) },
                                    Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Cadence") }
                                )
                            }
                            if (formActivity.supportsElevation) {
                                OutlinedTextField(
                                    formElevation,
                                    { formElevation = it.filter { ch -> ch.isDigit() || ch == '.' }.take(7) },
                                    Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Elevation gain (m)") }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        formNotes,
                        { formNotes = it.take(300) },
                        Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        label = { Text("Notes (optional)") }
                    )
                }
                CardioSection("HEART-RATE ZONES", "Optional - enter measured zone time only") {
                    CardioAction(
                        if (showZones) "HIDE ZONE TIMES" else "ADD ZONE TIMES",
                        "Zone distribution is never guessed from average heart rate",
                        Color(0xFF7B61C9)
                    ) { showZones = !showZones }
                    if (showZones) {
                        Spacer(Modifier.height(9.dp))
                        for (rowStart in listOf(0, 3)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                                val end = if (rowStart == 0) 3 else 5
                                for (index in rowStart until end) {
                                    OutlinedTextField(
                                        zoneMinutes[index],
                                        { value -> zoneMinutes[index] = value.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                                        Modifier.weight(1f),
                                        singleLine = true,
                                        label = { Text("Z${index + 1} min") }
                                    )
                                }
                                if (rowStart == 3) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(7.dp))
                        }
                    }
                }
                CardioAction(
                    if (editing) "SAVE CHANGES" else "SAVE CARDIO SESSION",
                    "Store this session in Exercise history",
                    CardioAccent
                ) { saveForm() }
            }

            CardioScreen.HISTORY -> {
                CardioHeroStrip("HISTORY", "Cardio sessions", "Tap a session for full details, editing or deletion.", Color(0xFF7B61C9))
                CardioSection("RECENT CARDIO", "Newest first") {
                    if (sessions.isEmpty()) CardioEmpty("No cardio sessions yet", "Saved sessions will appear here.")
                    sessions.take(100).forEach { session ->
                        CardioSessionRow(session) {
                            selectedSessionId = session.id
                            screen = CardioScreen.DETAIL
                        }
                    }
                }
            }

            CardioScreen.DETAIL -> {
                val session = sessions.firstOrNull { it.id == selectedSessionId }
                if (session == null) {
                    CardioEmpty("Session unavailable", "It may have been deleted.")
                } else {
                    CardioHeroStrip(
                        "CARDIO DETAIL",
                        session.activity.displayName,
                        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy - HH:mm")),
                        CardioAccent
                    )
                    CardioSection("SESSION METRICS", "Saved measurements and derived values") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CardioMetric("TIME", cardioFormatDuration(session.durationSeconds), "duration", CardioBlue, Modifier.weight(1f))
                            CardioMetric("DISTANCE", session.distanceKm?.let(::cardioFormatNumber) ?: "-", "km", CardioAccent, Modifier.weight(1f))
                            CardioMetric("AVG HR", session.avgHeartRate?.toString() ?: "-", "bpm", Color(0xFF7B61C9), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(9.dp))
                        CardioDerivedSummary(session)
                        if (session.zoneSeconds.isNotEmpty()) {
                            Spacer(Modifier.height(9.dp))
                            Text(
                                (1..5).joinToString(" - ") { zone -> "Z$zone ${cardioFormatDuration(session.zoneSeconds[zone] ?: 0)}" },
                                color = CardioMuted,
                                fontSize = 10.sp
                            )
                        }
                        if (session.notes.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(session.notes, color = CardioInk, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    CardioAction("EDIT SESSION", "Correct manually entered details", CardioBlue) {
                        loadSessionIntoForm(session)
                        screen = CardioScreen.MANUAL
                    }
                    CardioAction("DELETE SESSION", "Remove this cardio record", if (SuperhumanAppearance.darkMode) superhumanRed else Color(0xFFAA4444)) {
                        rows.firstOrNull { it.metadata["sessionId"] == session.id }?.let { row ->
                            scope.launch {
                                NativeDataHub.deleteValue(row)
                                refresh()
                                selectedSessionId = null
                                feedback = "Cardio session deleted"
                                screen = CardioScreen.HISTORY
                            }
                        }
                    }
                }
            }

            CardioScreen.PROGRESS -> {
                val totalMinutes = sessions.sumOf { it.durationSeconds } / 60
                val totalDistance = sessions.mapNotNull { it.distanceKm }.sum()
                CardioHeroStrip("PROGRESS", "Cardio trends", "Volume and performance from your saved sessions.", CardioAccent)
                CardioSection("LAST 7 DAYS", "Recent training load") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CardioMetric("MINUTES", weekMinutes.toString(), "7 days", CardioBlue, Modifier.weight(1f))
                        CardioMetric("SESSIONS", weekSessions.size.toString(), "7 days", CardioAccent, Modifier.weight(1f))
                        CardioMetric("ZONE 2", weekZone2.toString(), "measured min", Color(0xFF7B61C9), Modifier.weight(1f))
                    }
                }
                CardioSection("ALL LOGGED CARDIO", "Simple totals; activity-specific records are separate") {
                    Text("$totalMinutes total minutes - ${cardioFormatNumber(totalDistance)} km with recorded distance", color = CardioInk, fontSize = 11.sp)
                    Spacer(Modifier.height(9.dp))
                    sessions.groupBy { it.activity }.entries.sortedByDescending { it.value.size }.take(8).forEach { (activity, values) ->
                        val minutes = values.sumOf { it.durationSeconds } / 60
                        val distance = values.mapNotNull { it.distanceKm }.sum()
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(activity.displayName, color = CardioInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${values.size} sessions - ${minutes}m${if (distance > 0.0) " - ${cardioFormatNumber(distance)} km" else ""}", color = CardioMuted, fontSize = 10.sp)
                        }
                    }
                }
                CardioSection("EFFICIENCY METRICS", "More advanced comparisons unlock with consistent sensor data") {
                    Text(
                        "Future Health Connect, chest-strap and GPS samples can support pace-at-heart-rate, heart-rate-at-pace, VO2max estimates and training load. This screen does not infer those from insufficient data.",
                        color = CardioMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            CardioScreen.RECORDS -> {
                CardioHeroStrip("PERSONAL RECORDS", "Best logged cardio", "Records use only measurements present in saved sessions.", CardioAccent)
                CardioSection("ACTIVITY RECORDS", "Longest duration, longest distance and best average pace/speed") {
                    if (sessions.isEmpty()) CardioEmpty("No personal records yet", "Records appear automatically as you log cardio.")
                    sessions.groupBy { it.activity }.entries.sortedBy { it.key.displayName }.forEach { (activity, values) ->
                        CardioRecordBlock(activity, values)
                    }
                }
                CardioSection("PRECISE DISTANCE PRS", "Split data is required") {
                    Text(
                        "Exact 1 km, mile, 5 km, 10 km, 500 m and 2 km records should come from GPS or split-level data. Project Superhuman will not estimate them from a longer session's average pace.",
                        color = CardioMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CardioHeader(screen: CardioScreen, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.superhumanTopButton(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("<-", color = CardioAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                when (screen) {
                    CardioScreen.HOME -> "Cardio"
                    CardioScreen.PICK_ACTIVITY -> "Start cardio"
                    CardioScreen.LIVE -> "Live cardio"
                    CardioScreen.MANUAL -> "Log cardio"
                    CardioScreen.HISTORY -> "Cardio history"
                    CardioScreen.DETAIL -> "Session detail"
                    CardioScreen.PROGRESS -> "Cardio progress"
                    CardioScreen.RECORDS -> "Personal records"
                },
                color = CardioInk,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black
            )
            Text("Endurance - heart rate - pace - progress", color = CardioMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CardioHero(active: Boolean, weekMinutes: Int, weekSessions: Int, onPrimary: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF0B4D46), Color(0xFF14836F), Color(0xFF4AB69A))),
            RoundedCornerShape(28.dp)
        ).padding(21.dp)
    ) {
        Text(if (active) "CARDIO IN PROGRESS" else "CARDIO TRAINING", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(if (active) "Continue your session" else "Ready to move?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CardioGlassMetric("7D MINUTES", weekMinutes.toString(), Modifier.weight(1f))
            CardioGlassMetric("7D SESSIONS", weekSessions.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(15.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Color.White, RoundedCornerShape(16.dp))
                .clickable { onPrimary() }.padding(15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (active) "RESUME CARDIO" else "START CARDIO", color = Color(0xFF0B4D46), fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CardioLiveHero(activity: CardioActivityType, elapsed: Int, running: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF0B4D46), Color(0xFF14836F))),
            RoundedCornerShape(27.dp)
        ).padding(21.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(activity.displayName.uppercase(), color = Color.White.copy(alpha = .75f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(cardioFormatDuration(elapsed), color = Color.White,