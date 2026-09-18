package com.projectsuperhuman.next

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val CardioGold = Color(0xFFC9902E)
private val CardioCoral = Color(0xFFC8575E)
private val CardioDeep = Color(0xFF0A3440)
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
    val workoutType: CardioWorkoutType = CardioWorkoutType.FREE,
    val zoneSeconds: Map<Int, Int> = emptyMap(),
    val avgSplit500mSeconds: Int? = null,
    val avgPace100mSeconds: Int? = null
)

private data class CardioLiveDraft(
    val activity: CardioActivityType,
    val startedAt: Long,
    val accumulatedSeconds: Int,
    val runningSinceEpochMs: Long,
    val isRunning: Boolean,
    val workoutType: CardioWorkoutType = CardioWorkoutType.FREE
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
        workoutType = CardioWorkoutType.fromStored(meta["workoutType"]),
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
        "cardioSource" to source,
        "workoutType" to workoutType.name
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
        isRunning = prefs.getBoolean("live_is_running", false),
        workoutType = CardioWorkoutType.fromStored(prefs.getString("live_workout_type", null))
    )
}

private fun saveCardioDraft(context: Context, draft: CardioLiveDraft) {
    cardioPrefs(context).edit()
        .putString("live_activity", draft.activity.name)
        .putLong("live_started_at", draft.startedAt)
        .putInt("live_accumulated_seconds", draft.accumulatedSeconds)
        .putLong("live_running_since", draft.runningSinceEpochMs)
        .putBoolean("live_is_running", draft.isRunning)
        .putString("live_workout_type", draft.workoutType.name)
        .apply()
}

private fun clearCardioDraft(context: Context) {
    cardioPrefs(context).edit()
        .remove("live_activity")
        .remove("live_started_at")
        .remove("live_accumulated_seconds")
        .remove("live_running_since")
        .remove("live_is_running")
        .remove("live_workout_type")
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
    var pendingDeleteSessionId by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }

    var liveActivity by remember { mutableStateOf(CardioActivityType.WALKING) }
    var liveWorkoutType by remember { mutableStateOf(CardioWorkoutType.FREE) }
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
    var formWorkoutType by remember { mutableStateOf(CardioWorkoutType.FREE) }
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
        formWorkoutType = CardioWorkoutType.FREE
        formEditingId = null
        formLiveStartedAt = 0L
        showZones = false
        for (i in zoneMinutes.indices) zoneMinutes[i] = ""
    }

    fun loadSessionIntoForm(session: CardioSession) {
        formActivity = session.activity
        formDateTime = Instant.ofEpochMilli(session.endedAt)
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
        formWorkoutType = session.workoutType
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
                isRunning = liveRunning,
                workoutType = liveWorkoutType
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
        formDateTime = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault()).toLocalDateTime().format(cardioDateTimeFormatter)
        formDurationMin = String.format(Locale.US, "%.1f", elapsed / 60.0)
        formSource = "live"
        formWorkoutType = liveWorkoutType
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
        val enteredEpoch = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (formLiveStartedAt <= 0L && enteredEpoch > System.currentTimeMillis() + 60_000L) {
            feedback = "Finish time cannot be in the future"
            return
        }
        val endedAt = if (formLiveStartedAt > 0L) {
            formLiveStartedAt + durationSeconds * 1000L
        } else {
            enteredEpoch
        }
        val startedAt = if (formLiveStartedAt > 0L) formLiveStartedAt else endedAt - durationSeconds * 1000L
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
        val finishingLiveSession = formLiveStartedAt > 0L
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
            workoutType = formWorkoutType,
            zoneSeconds = zones,
            avgSplit500mSeconds = split500,
            avgPace100mSeconds = pace100
        )

        scope.launch {
            if (existingId != null) {
                rows.firstOrNull { it.metadata["sessionId"] == existingId }?.let { NativeDataHub.deleteValue(it) }
            }
            NativeDataHub.saveValues(listOf(session.toHealthValue()))
            if (finishingLiveSession) {
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
            liveWorkoutType = draft.workoutType
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

    val weekSummary = remember(sessions) {
        val weekStart = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val recent = sessions.filter { it.endedAt >= weekStart }
        listOf(
            recent.sumOf { it.durationSeconds } / 60,
            recent.size,
            recent.sumOf { it.zoneSeconds[2] ?: 0 } / 60
        ) to recent.mapNotNull { it.distanceKm }.sum()
    }
    val weekMinutes = weekSummary.first[0]
    val weekSessionCount = weekSummary.first[1]
    val weekZone2 = weekSummary.first[2]
    val weekDistance = weekSummary.second
    val loadSnapshot = remember(sessions) { calculateCardioLoadSnapshot(sessions) }
    val weekZoneTotals = remember(sessions) {
        val weekStart = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val recent = sessions.filter { it.endedAt >= weekStart }
        (1..5).associateWith { zone -> recent.sumOf { it.zoneSeconds[zone] ?: 0 } }
    }
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
                CardioHero(activeDraft, weekMinutes, weekSessionCount) {
                    if (activeDraft) screen = CardioScreen.LIVE else screen = CardioScreen.PICK_ACTIVITY
                }
                CardioQuickAccessPanel(
                    onLog = {
                        resetForm()
                        screen = CardioScreen.MANUAL
                    },
                    onHistory = { screen = CardioScreen.HISTORY },
                    onProgress = { screen = CardioScreen.PROGRESS },
                    onRecords = { screen = CardioScreen.RECORDS }
                )
                CardioWeeklyOverview(
                    minutes = weekMinutes,
                    sessions = weekSessionCount,
                    distanceKm = weekDistance,
                    zone2Minutes = weekZone2,
                    loadSnapshot = loadSnapshot
                )
                sessions.firstOrNull()?.let { latest ->
                    CardioLatestActivityPanel(latest) {
                        selectedSessionId = latest.id
                        screen = CardioScreen.DETAIL
                    }
                } ?: CardioSection("GET STARTED", "Build a useful baseline with your first session") {
                    CardioEmpty("No cardio sessions yet", "Start a workout or log something you already completed.")
                }
            }

            CardioScreen.PICK_ACTIVITY -> {
                CardioHeroStrip("START CARDIO", "Choose an activity", "The live timer works without GPS or a wearable.", CardioAccent)
                CardioSection("ACTIVITY", "Choose what you are about to do") {
                    CardioActivityPicker(liveActivity) { activity ->
                        liveActivity = activity
                    }
                }
                CardioSection("WORKOUT PURPOSE", "Optional structure for how this session should feel") {
                    CardioWorkoutTypePicker(liveWorkoutType) { liveWorkoutType = it }
                }
                CardioAction(
                    "START ${liveActivity.displayName.uppercase()}",
                    "${liveWorkoutType.label} session · begin live timer",
                    CardioAccent
                ) {
                    startLive(liveActivity)
                }
            }

            CardioScreen.LIVE -> {
                val elapsed = currentLiveElapsedSeconds()
                CardioLiveHero(liveActivity, liveWorkoutType, elapsed, liveRunning)
                CardioLiveControls(
                    running = liveRunning,
                    onToggle = { if (liveRunning) pauseLive() else resumeLive() },
                    onFinish = { prepareFinishedLive() }
                )
                CardioLiveMetricsPanel(liveActivity)
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
                CardioSection("WORKOUT PURPOSE", "Classify the training stimulus") {
                    CardioWorkoutTypePicker(formWorkoutType) { formWorkoutType = it }
                }
                CardioSection("SESSION", "Duration is required; other fields are optional") {
                    OutlinedTextField(
                        formDateTime,
                        { formDateTime = it.take(16) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Finished - YYYY-MM-DD HH:MM") }
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
                            pendingDeleteSessionId = null
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
                    CardioSessionHero(session)
                    CardioSessionOverview(session)

                    val load = cardioSessionLoad(session)
                    val comparison = findCardioEfficiencyComparison(session, sessions)
                    if (load != null || comparison != null) {
                        CardioInsightPanel(load, comparison?.message)
                    }
                    if (session.zoneSeconds.isNotEmpty()) {
                        CardioZonePanel("HEART-RATE ZONES", session.zoneSeconds)
                    }
                    if (session.notes.isNotBlank()) {
                        CardioSection("NOTES", "Session context") {
                            Text(session.notes, color = CardioInk, fontSize = 11.sp, lineHeight = 17.sp)
                        }
                    }

                    CardioSection("SESSION OPTIONS", "Keep analytics primary; maintenance stays out of the way") {
                        CardioActionCompact("Edit session", "Correct saved details", CardioBlue) {
                            loadSessionIntoForm(session)
                            screen = CardioScreen.MANUAL
                        }
                        Spacer(Modifier.height(7.dp))
                        val confirmDelete = pendingDeleteSessionId == session.id
                        CardioActionCompact(
                            if (confirmDelete) "Confirm delete" else "Delete session",
                            if (confirmDelete) "Tap again to permanently remove this record" else "Remove this local record",
                            CardioCoral
                        ) {
                            if (!confirmDelete) {
                                pendingDeleteSessionId = session.id
                            } else {
                                rows.firstOrNull { it.metadata["sessionId"] == session.id }?.let { row ->
                                    scope.launch {
                                        NativeDataHub.deleteValue(row)
                                        refresh()
                                        selectedSessionId = null
                                        pendingDeleteSessionId = null
                                        feedback = "Cardio session deleted"
                                        screen = CardioScreen.HISTORY
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CardioScreen.PROGRESS -> {
                val totalMinutes = remember(sessions) { sessions.sumOf { it.durationSeconds } / 60 }
                val totalDistance = remember(sessions) { sessions.mapNotNull { it.distanceKm }.sum() }
                CardioHeroStrip("PROGRESS", "Training analytics", "Volume, load and measured intensity.", CardioAccent)
                CardioLoadPanel(loadSnapshot)
                CardioWeeklyProgressPanel(
                    minutes = weekMinutes,
                    sessions = weekSessionCount,
                    distanceKm = weekDistance,
                    zone2Minutes = weekZone2
                )
                if (weekZoneTotals.values.sum() > 0) {
                    CardioZonePanel("7-DAY ZONE DISTRIBUTION", weekZoneTotals)
                }
                CardioSection("ACTIVITY MIX", "$totalMinutes total minutes · ${cardioFormatNumber(totalDistance)} km with distance recorded") {
                    sessions.groupBy { it.activity }.entries.sortedByDescending { it.value.size }.take(8).forEachIndexed { index, (activity, values) ->
                        val minutes = values.sumOf { it.durationSeconds } / 60
                        val distance = values.mapNotNull { it.distanceKm }.sum()
                        CardioActivityProgressRow(activity, values.size, minutes, distance)
                        if (index < sessions.groupBy { it.activity }.size.coerceAtMost(8) - 1) {
                            Spacer(Modifier.height(5.dp))
                        }
                    }
                }
                CardioSection("EFFICIENCY", "Unlock richer comparisons with consistent HR and pace data") {
                    Text(
                        "Matched-session insights appear automatically when comparable sessions contain both pace and heart-rate data.",
                        color = CardioMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            CardioScreen.RECORDS -> {
                CardioHeroStrip("PERSONAL RECORDS", "Your best work", "Only verified values from saved sessions count.", CardioGold)
                if (sessions.isEmpty()) {
                    CardioSection("FEATURED BESTS", "Your performance board will build automatically") {
                        CardioEmpty("No records yet", "Complete or log a few sessions to start building personal bests.")
                    }
                } else {
                    CardioFeaturedRecords(sessions)
                    CardioSection("BY ACTIVITY", "Best verified performance for each discipline") {
                        sessions.groupBy { it.activity }.entries.sortedBy { it.key.displayName }.forEach { (activity, values) ->
                            CardioRecordBlock(activity, values)
                        }
                    }
                }
                CardioSection("DISTANCE PRS", "GPS, lap or split data unlocks exact-distance records") {
                    Text(
                        "1 km, mile, 5 km, 10 km, 500 m and 2 km records stay locked until exact split data exists. Average pace is never used to fabricate a PR.",
                        color = CardioMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
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
            Text("←", color = CardioAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
            Text(
                when (screen) {
                    CardioScreen.HOME -> "Train smarter · see the week at a glance"
                    CardioScreen.PICK_ACTIVITY -> "Choose your activity and training purpose"
                    CardioScreen.LIVE -> "Stay focused · only the metrics that matter"
                    CardioScreen.MANUAL -> "Log what you actually measured"
                    CardioScreen.HISTORY -> "Review every completed session"
                    CardioScreen.DETAIL -> "See what this session actually did"
                    CardioScreen.PROGRESS -> "Load, consistency and aerobic progress"
                    CardioScreen.RECORDS -> "Your best verified performances"
                },
                color = CardioMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CardioHero(active: Boolean, weekMinutes: Int, weekSessions: Int, onPrimary: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF082F39), Color(0xFF0B7567), Color(0xFF2FB494))
                )
            )
            .padding(horizontal = 19.dp, vertical = 18.dp)
    ) {
        Box(
            Modifier.size(150.dp).align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-58).dp)
                .background(Color.White.copy(alpha = .055f), CircleShape)
        )
        Box(
            Modifier.size(74.dp).align(Alignment.BottomStart)
                .offset(x = (-28).dp, y = 35.dp)
                .background(Color.White.copy(alpha = .035f), CircleShape)
        )
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (active) Color(0xFFFFD166) else Color(0xFF7DE3BD), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active) "SESSION ACTIVE" else "CARDIO",
                    color = Color.White.copy(alpha = .80f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (active) "Keep the momentum." else "Train with intent.",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (active) "Your workout is saved and ready to resume." else "Track the work. Watch the engine improve.",
                color = Color.White.copy(alpha = .72f),
                fontSize = 10.sp
            )
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CardioHeroStat("7D MIN", weekMinutes.toString(), Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = .18f)))
                CardioHeroStat("SESSIONS", weekSessions.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .clickable { onPrimary() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (active) "RESUME CARDIO" else "START CARDIO",
                    color = CardioDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Text("→", color = CardioDeep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CardioLiveHero(
    activity: CardioActivityType,
    workoutType: CardioWorkoutType,
    elapsed: Int,
    running: Boolean
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF082F39), Color(0xFF0A6F63), Color(0xFF18A083))
                )
            )
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Box(
            Modifier.size(118.dp).align(Alignment.TopEnd)
                .offset(x = 42.dp, y = (-44).dp)
                .background(Color.White.copy(alpha = .055f), CircleShape)
        )
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(activity.displayName.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.background(Color.White.copy(alpha = .13f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(workoutType.label.uppercase(), color = Color.White.copy(alpha = .90f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(cardioFormatDuration(elapsed), color = Color.White, fontSize = 49.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (running) Color(0xFF77E1B8) else Color(0xFFFFD166), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (running) "RECORDING" else "PAUSED",
                    color = Color.White.copy(alpha = .88f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
                Text("  ·  draft saved", color = Color.White.copy(alpha = .64f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun CardioHeroStrip(kicker: String, title: String, subtitle: String, accent: Color) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(CardioDeep, accent.copy(alpha = .96f))
                )
            )
            .padding(horizontal = 18.dp, vertical = 17.dp)
    ) {
        Box(
            Modifier.size(92.dp).align(Alignment.TopEnd)
                .offset(x = 34.dp, y = (-34).dp)
                .background(Color.White.copy(alpha = .06f), CircleShape)
        )
        Column {
            Text(kicker, color = Color.White.copy(alpha = .68f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Spacer(Modifier.height(3.dp))
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = .76f), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun CardioGlassMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .13f), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Text(label, color = Color.White.copy(alpha = .70f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(CardioSurface, RoundedCornerShape(21.dp))
            .border(1.dp, CardioBorder.copy(alpha = .72f), RoundedCornerShape(21.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Text(title, color = CardioInk, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = .2.sp)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = CardioMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.height(11.dp))
        content()
    }
}

@Composable
private fun CardioMetric(label: String, value: String, detail: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .16f else .08f), RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(label, color = CardioMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = CardioInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(detail, color = CardioMuted, fontSize = 10.sp)
    }
}

@Composable
private fun CardioQuickAction(
    mark: String,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.heightIn(min = 68.dp)
            .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .14f else .08f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(accent.copy(alpha = .14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(mark, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CardioInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = CardioMuted, fontSize = 9.sp)
        }
        Text("→", color = accent, fontSize = 18.sp)
    }
}

@Composable
private fun CardioSensorChip(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .14f else .07f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(value, color = CardioInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardioNavTile(mark: String, title: String, subtitle: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 96.dp).background(CardioSurface, RoundedCornerShape(20.dp))
            .border(1.dp, CardioBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(12.dp)
    ) {
        Box(Modifier.size(32.dp).background(accent.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, color = accent, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(9.dp))
        Text(title, color = CardioInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = CardioMuted, fontSize = 10.sp)
    }
}

@Composable
private fun CardioAction(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .16f else .09f), RoundedCornerShape(17.dp))
            .clickable { onClick() }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = CardioMuted, fontSize = 10.sp)
        }
        Text("→", color = accent, fontSize = 22.sp)
    }
}

@Composable
private fun CardioFeedback(message: String) {
    Row(
        Modifier.fillMaxWidth().background(CardioAccent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f), RoundedCornerShape(14.dp))
            .border(1.dp, CardioAccent.copy(alpha = .28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("OK", color = CardioAccent, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(9.dp))
        Text(message, color = CardioInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardioEmpty(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().background(CardioSoft, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = CardioInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = CardioMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CardioActivityPicker(selected: CardioActivityType, onSelect: (CardioActivityType) -> Unit) {
    CardioActivityType.entries.toList().chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEach { activity ->
                val active = activity == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .background(if (active) CardioAccent else CardioSoft, RoundedCornerShape(13.dp))
                        .clickable { onSelect(activity) }.padding(horizontal = 10.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        activity.displayName,
                        color = if (active) Color.White else CardioInk,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun CardioWorkoutTypePicker(
    selected: CardioWorkoutType,
    onSelect: (CardioWorkoutType) -> Unit
) {
    CardioWorkoutType.entries.toList().chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEach { type ->
                val active = type == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 58.dp)
                        .background(if (active) CardioBlue else CardioSoft, RoundedCornerShape(13.dp))
                        .clickable { onSelect(type) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(
                        type.label,
                        color = if (active) Color.White else CardioInk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        type.description,
                        color = if (active) Color.White.copy(alpha = .78f) else CardioMuted,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun CardioSessionRow(session: CardioSession, onOpen: () -> Unit) {
    val whenText = remember(session.endedAt) {
        Instant.ofEpochMilli(session.endedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM - HH:mm"))
    }
    Row(
        Modifier.fillMaxWidth().background(CardioSoft, RoundedCornerShape(16.dp))
            .clickable { onOpen() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(session.activity.displayName, color = CardioInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(whenText, color = CardioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                if (session.workoutType != CardioWorkoutType.FREE) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier.background(CardioBlue.copy(alpha = .10f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(session.workoutType.label.uppercase(), color = CardioBlue, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            val facts = buildList {
                add(cardioFormatDuration(session.durationSeconds))
                session.distanceKm?.let { add("${cardioFormatNumber(it)} km") }
                session.avgHeartRate?.let { add("Avg HR $it") }
            }
            Text(facts.joinToString(" - "), color = CardioMuted, fontSize = 10.sp)
            CardioDerivedSummary(session)
        }
        Text("→", color = CardioAccent, fontSize = 22.sp)
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun CardioDerivedSummary(session: CardioSession) {
    val text = when (session.activity.paceMode) {
        CardioPaceMode.PER_KM -> session.avgPaceSecPerKm?.let { "Avg pace ${cardioFormatPace(it)}/km" }
        CardioPaceMode.SPEED -> session.avgSpeedKmh?.let { "Avg speed ${cardioFormatNumber(it)} km/h" }
        CardioPaceMode.PER_500M -> session.avgSplit500mSeconds?.let { "Avg split ${cardioFormatPace(it)}/500m" }
        CardioPaceMode.PER_100M -> session.avgPace100mSeconds?.let { "Avg pace ${cardioFormatPace(it)}/100m" }
        CardioPaceMode.NONE -> null
    }
    if (text != null) Text(text, color = CardioMuted, fontSize = 10.sp)
}

@Composable
private fun CardioRecordBlock(activity: CardioActivityType, sessions: List<CardioSession>) {
    val longestDuration = sessions.maxByOrNull { it.durationSeconds }
    val longestDistance = sessions.filter { it.distanceKm != null }.maxByOrNull { it.distanceKm ?: 0.0 }
    val performance = when (activity.paceMode) {
        CardioPaceMode.PER_KM -> sessions.mapNotNull { it.avgPaceSecPerKm }.minOrNull()?.let { "Best avg pace ${cardioFormatPace(it)}/km" }
        CardioPaceMode.SPEED -> sessions.mapNotNull { it.avgSpeedKmh }.maxOrNull()?.let { "Best avg speed ${cardioFormatNumber(it)} km/h" }
        CardioPaceMode.PER_500M -> sessions.mapNotNull { it.avgSplit500mSeconds }.minOrNull()?.let { "Best avg split ${cardioFormatPace(it)}/500m" }
        CardioPaceMode.PER_100M -> sessions.mapNotNull { it.avgPace100mSeconds }.minOrNull()?.let { "Best avg pace ${cardioFormatPace(it)}/100m" }
        CardioPaceMode.NONE -> null
    }

    Column(Modifier.fillMaxWidth().background(CardioSoft, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text(activity.displayName, color = CardioInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
        longestDuration?.let { Text("Longest session ${cardioFormatDuration(it.durationSeconds)}", color = CardioMuted, fontSize = 10.sp) }
        longestDistance?.distanceKm?.let { Text("Longest distance ${cardioFormatNumber(it)} km", color = CardioMuted, fontSize = 10.sp) }
        performance?.let { Text(it, color = CardioAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    }
    Spacer(Modifier.height(7.dp))
}
