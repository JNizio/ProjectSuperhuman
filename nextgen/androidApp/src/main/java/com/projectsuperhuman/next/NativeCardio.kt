package com.projectsuperhuman.next

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
import androidx.compose.ui.semantics.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
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
private val cardioDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

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

@Composable
internal fun NativeCardioScreen(onBack: () -> Unit) {
    val cardioViewModel: CardioViewModel = viewModel()
    val cardioState by cardioViewModel.state.collectAsState()
    val sensorMetrics by CardioSensorRuntime.liveMetrics.collectAsState()
    val sessions = cardioState.sessions

    var screen by remember { mutableStateOf(CardioScreen.HOME) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteSessionId by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    var liveActivity by remember { mutableStateOf(CardioActivityType.WALKING) }
    var liveWorkoutType by remember { mutableStateOf(CardioWorkoutType.FREE) }

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
    var formLiveEndedAt by remember { mutableLongStateOf(0L) }
    var formLiveSessionId by remember { mutableStateOf<String?>(null) }
    var formLivePausedSeconds by remember { mutableIntStateOf(0) }
    var showZones by remember { mutableStateOf(false) }
    val zoneMinutes = remember { mutableStateListOf("", "", "", "", "") }

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
        formLiveEndedAt = 0L
        formLiveSessionId = null
        formLivePausedSeconds = 0
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
        formLiveEndedAt = 0L
        formLiveSessionId = null
        formLivePausedSeconds = 0
        showZones = session.zoneSeconds.isNotEmpty()
        for (i in zoneMinutes.indices) {
            zoneMinutes[i] = session.zoneSeconds[i + 1]?.let { String.format(Locale.US, "%.1f", it / 60.0) }.orEmpty()
        }
    }

    fun prepareFinishedLive() {
        cardioViewModel.prepareDetailedFinish { session ->
            resetForm(session.activity)
            formDateTime = Instant.ofEpochMilli(session.endedAt)
                .atZone(ZoneId.systemDefault()).toLocalDateTime().format(cardioDateTimeFormatter)
            formDurationMin = String.format(Locale.US, "%.1f", session.durationSeconds / 60.0)
            formSource = "live"
            formWorkoutType = session.workoutType
            formLiveStartedAt = session.startedAt
            formLiveEndedAt = session.endedAt
            formLiveSessionId = session.id
            formLivePausedSeconds = session.pausedDurationSeconds
            formAvgHr = session.avgHeartRate?.toString().orEmpty()
            formMaxHr = session.maxHeartRate?.toString().orEmpty()
            if (session.zoneSeconds.isNotEmpty()) {
                showZones = true
                for (i in zoneMinutes.indices) {
                    zoneMinutes[i] = session.zoneSeconds[i + 1]
                        ?.let { String.format(Locale.US, "%.1f", it / 60.0) }
                        .orEmpty()
                }
            }
            screen = CardioScreen.MANUAL
        }
    }

    fun saveForm() {
        val localDateTime = runCatching {
            LocalDateTime.parse(formDateTime.trim(), cardioDateTimeFormatter)
        }.getOrNull()
        if (localDateTime == null) {
            feedback = "Use date and time format YYYY-MM-DD HH:MM"
            return
        }

        val durationSeconds = ((CardioUnits.parseLocalizedDecimal(formDurationMin) ?: 0.0) * 60.0).roundToInt()
        if (durationSeconds <= 0) {
            feedback = "Enter a cardio duration"
            return
        }

        val enteredEpoch = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val finishingLiveSession = formLiveStartedAt > 0L && formLiveSessionId != null
        val endedAt = if (finishingLiveSession) formLiveEndedAt else enteredEpoch
        if (endedAt > System.currentTimeMillis() + 60_000L) {
            feedback = "Finish time cannot be in the future"
            return
        }
        val startedAt = if (finishingLiveSession) {
            formLiveStartedAt
        } else {
            endedAt - durationSeconds * 1000L
        }

        val distance = CardioUnits.parseLocalizedDecimal(formDistanceKm)?.takeIf { it > 0.0 }
        val avgHr = formAvgHr.toIntOrNull()?.takeIf { it in 30..250 }
        val maxHr = formMaxHr.toIntOrNull()?.takeIf { it in 30..250 }
        val calories = CardioUnits.parseLocalizedDecimal(formCalories)?.takeIf { it >= 0.0 }
        val cadence = formCadence.toIntOrNull()?.takeIf { it > 0 }
        val elevation = CardioUnits.parseLocalizedDecimal(formElevation)?.takeIf { it >= 0.0 }
        val rpe = CardioUnits.parseLocalizedDecimal(formRpe)?.takeIf { it in 0.0..10.0 }
        val zones = zoneMinutes.mapIndexedNotNull { index, text ->
            val seconds = ((CardioUnits.parseLocalizedDecimal(text) ?: 0.0) * 60.0).roundToInt()
            if (seconds > 0) (index + 1) to seconds else null
        }.toMap()
        if (zones.values.sum() > durationSeconds) {
            feedback = "Heart-rate zone time cannot exceed session duration"
            return
        }

        val avgPace = if (distance != null && formActivity.paceMode == CardioPaceMode.PER_KM) {
            (durationSeconds / distance).roundToInt()
        } else null
        val avgSpeed = if (distance != null && formActivity.paceMode == CardioPaceMode.SPEED) {
            distance / (durationSeconds / 3600.0)
        } else null
        val split500 = if (distance != null && formActivity.paceMode == CardioPaceMode.PER_500M) {
            (durationSeconds / (distance * 2.0)).roundToInt()
        } else null
        val pace100 = if (distance != null && formActivity.paceMode == CardioPaceMode.PER_100M) {
            (durationSeconds / (distance * 10.0)).roundToInt()
        } else null

        val existingId = formEditingId
        val session = CardioSession(
            id = existingId ?: formLiveSessionId ?: java.util.UUID.randomUUID().toString(),
            activity = formActivity,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            pausedDurationSeconds = if (finishingLiveSession) formLivePausedSeconds else 0,
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
            source = if (finishingLiveSession) "live" else formSource,
            workoutType = formWorkoutType,
            zoneSeconds = zones,
            avgSplit500mSeconds = split500,
            avgPace100mSeconds = pace100
        )

        val validation = CardioValidation.validate(session, System.currentTimeMillis())
        if (validation.isNotEmpty()) {
            feedback = validation.first().message
            return
        }

        cardioViewModel.saveSession(
            session = session,
            editing = existingId != null,
            finishingLive = finishingLiveSession
        ) { success ->
            if (success) {
                formEditingId = null
                formLiveStartedAt = 0L
                formLiveEndedAt = 0L
                formLiveSessionId = null
                formLivePausedSeconds = 0
                screen = CardioScreen.HOME
            }
        }
    }

    LaunchedEffect(Unit) {
        resetForm()
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2400)
            feedback = null
        }
    }

    LaunchedEffect(cardioState.feedback) {
        if (cardioState.feedback != null) {
            delay(3200)
            cardioViewModel.clearFeedback()
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
    val activityGroups = remember(sessions) { sessions.groupBy { it.activity } }
    val activeDraftState = cardioState.liveDraft
    val activeDraft = activeDraftState != null

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
        cardioState.feedback?.let {
            CardioFeedback(it, isError = cardioState.saveState == CardioSaveState.FAILED)
        }
        cardioState.undo?.let {
            CardioUndoBanner(onUndo = cardioViewModel::undoQuickSave)
        }

        when (screen) {
            CardioScreen.HOME -> {
                CardioHero(
                    active = activeDraft,
                    weekMinutes = weekMinutes,
                    weekSessions = weekSessionCount,
                    activeActivity = activeDraftState?.activity,
                    activeWorkoutType = activeDraftState?.workoutType,
                    activeElapsedSeconds = cardioState.liveElapsedSeconds,
                    activeRunning = cardioState.isRecording,
                    saveInProgress = cardioState.saveInProgress,
                    onPrimary = {
                        if (activeDraft) screen = CardioScreen.LIVE else screen = CardioScreen.PICK_ACTIVITY
                    },
                    onToggleActive = {
                        if (cardioState.isRecording) cardioViewModel.pause() else cardioViewModel.resume()
                    },
                    onSaveActive = {
                        cardioViewModel.quickSave { success ->
                            if (success) screen = CardioScreen.HOME
                        }
                    }
                )
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
                CardioSensorPickerPanel()
                CardioAction(
                    "START ${liveActivity.displayName.uppercase()}",
                    "${liveWorkoutType.label} session · begin live timer",
                    CardioAccent
                ) {
                    cardioViewModel.start(liveActivity, liveWorkoutType) {
                        screen = CardioScreen.LIVE
                    }
                }
            }

            CardioScreen.LIVE -> {
                val draft = cardioState.liveDraft
                if (draft == null) {
                    CardioEmpty(
                        "No active workout",
                        "The session may already have been saved or discarded."
                    )
                } else {
                    CardioLiveHero(
                        draft.activity,
                        draft.workoutType,
                        cardioState.liveElapsedSeconds,
                        cardioState.isRecording
                    )
                    CardioLiveControls(
                        running = cardioState.isRecording,
                        onToggle = {
                            if (cardioState.isRecording) cardioViewModel.pause() else cardioViewModel.resume()
                        },
                        onFinish = { prepareFinishedLive() }
                    )
                    CardioSection("WORKOUT STATE", "Recording and pause time are tracked independently") {
                        Text(
                            "Started " + Instant.ofEpochMilli(draft.startedAtEpochMs)
                                .atZone(ZoneId.systemDefault()).toLocalDateTime()
                                .format(cardioDateTimeFormatter),
                            color = CardioInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Active " + cardioFormatDuration(cardioState.liveElapsedSeconds) +
                                " · paused " + cardioFormatDuration(cardioState.pausedElapsedSeconds),
                            color = CardioMuted,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(9.dp))
                        CardioActionCompact(
                            if (confirmDiscard) "Confirm discard" else "Discard workout",
                            if (confirmDiscard) {
                                "This permanently removes the recoverable live draft"
                            } else {
                                "Stop without saving this workout"
                            },
                            CardioCoral
                        ) {
                            if (confirmDiscard) {
                                cardioViewModel.discardLive {
                                    confirmDiscard = false
                                    screen = CardioScreen.HOME
                                }
                            } else {
                                confirmDiscard = true
                            }
                        }
                    }
                    CardioLiveMetricsPanel(sensorMetrics)
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
                CardioSection("WORKOUT PURPOSE", "Classify the training stimulus") {
                    CardioWorkoutTypePicker(formWorkoutType) { formWorkoutType = it }
                }
                CardioSection("SESSION", "Duration is required; other fields are optional") {
                    CardioDateTimePickerField(
                        value = formDateTime,
                        onValueChange = { formDateTime = it },
                        label = "Finished"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            formDurationMin,
                            { formDurationMin = CardioUnits.sanitizeDecimalInput(it, maxLength = 7) },
                            Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = cardioDecimalKeyboardOptions,
                            label = { Text("Duration (min)") }
                        )
                        if (formActivity.supportsDistance) {
                            OutlinedTextField(
                                formDistanceKm,
                                { formDistanceKm = CardioUnits.sanitizeDecimalInput(it, maxLength = 8) },
                                Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = cardioDecimalKeyboardOptions,
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
                            keyboardOptions = cardioIntegerKeyboardOptions,
                            label = { Text("Avg HR") }
                        )
                        OutlinedTextField(
                            formMaxHr,
                            { formMaxHr = it.filter(Char::isDigit).take(3) },
                            Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = cardioIntegerKeyboardOptions,
                            label = { Text("Max HR") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            formCalories,
                            { formCalories = CardioUnits.sanitizeDecimalInput(it, maxLength = 7) },
                            Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = cardioDecimalKeyboardOptions,
                            label = { Text("Calories (optional)") }
                        )
                        OutlinedTextField(
                            formRpe,
                            { formRpe = CardioUnits.sanitizeDecimalInput(it, maxLength = 4) },
                            Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = cardioDecimalKeyboardOptions,
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
                                    keyboardOptions = cardioIntegerKeyboardOptions,
                                    label = { Text("Cadence") }
                                )
                            }
                            if (formActivity.supportsElevation) {
                                OutlinedTextField(
                                    formElevation,
                                    { formElevation = CardioUnits.sanitizeDecimalInput(it, maxLength = 7) },
                                    Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = cardioDecimalKeyboardOptions,
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
                                        { value -> zoneMinutes[index] = CardioUnits.sanitizeDecimalInput(value, maxLength = 6) },
                                        Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = cardioDecimalKeyboardOptions,
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
                    if (cardioState.saveInProgress) {
                        "SAVING…"
                    } else if (editing) {
                        "SAVE CHANGES"
                    } else {
                        "SAVE CARDIO SESSION"
                    },
                    if (cardioState.saveInProgress) {
                        "Verifying Data Vault write"
                    } else {
                        "Store this session in Exercise history"
                    },
                    CardioAccent
                ) {
                    if (!cardioState.saveInProgress) saveForm()
                }
            }

            CardioScreen.HISTORY -> {
                CardioHeroStrip("HISTORY", "Cardio sessions", "Filter, search and review completed sessions.", Color(0xFF7B61C9))
                CardioAnalyticsHistoryPanel(
                    sessions = sessions,
                    onOpenSession = { session ->
                        selectedSessionId = session.id
                        pendingDeleteSessionId = null
                        screen = CardioScreen.DETAIL
                    }
                )
                if (cardioState.canLoadMoreHistory) {
                    CardioSection("OLDER HISTORY", "The Data Vault has more sessions than are currently loaded") {
                        CardioActionCompact(
                            "Load 250 more",
                            "Extend the analytics/history window without loading the entire archive",
                            CardioBlue
                        ) { cardioViewModel.loadMoreHistory() }
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
                    CardioSessionComparisonPanel(session, sessions)

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
                                cardioViewModel.deleteSession(session.id) { deleted ->
                                    if (deleted) {
                                        selectedSessionId = null
                                        pendingDeleteSessionId = null
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
                CardioAnalyticsProgressPanel(sessions)
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
                    activityGroups.entries.sortedByDescending { it.value.size }.take(8).forEachIndexed { index, (activity, values) ->
                        val minutes = values.sumOf { it.durationSeconds } / 60
                        val distance = values.mapNotNull { it.distanceKm }.sum()
                        CardioActivityProgressRow(activity, values.size, minutes, distance)
                        if (index < activityGroups.size.coerceAtMost(8) - 1) {
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
                CardioAnalyticsRecordsPanel(sessions)
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
private fun CardioHero(
    active: Boolean,
    weekMinutes: Int,
    weekSessions: Int,
    activeActivity: CardioActivityType?,
    activeWorkoutType: CardioWorkoutType?,
    activeElapsedSeconds: Int,
    activeRunning: Boolean,
    saveInProgress: Boolean,
    onPrimary: () -> Unit,
    onToggleActive: () -> Unit,
    onSaveActive: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    if (active) {
                        listOf(Color(0xFF072B38), Color(0xFF075E56), Color(0xFF159A7D))
                    } else {
                        listOf(Color(0xFF082F39), Color(0xFF0B7567), Color(0xFF2FB494))
                    }
                )
            )
            .padding(horizontal = 19.dp, vertical = 18.dp)
    ) {
        CardioHeroBackdrop(active)

        if (active) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (activeRunning) Color(0xFF78E0B8) else Color(0xFFFFD166), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (activeRunning) "LIVE SESSION" else "SESSION PAUSED",
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = .8.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.background(Color.White.copy(alpha = .11f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            activeWorkoutType?.label?.uppercase() ?: "FREE",
                            color = Color.White.copy(alpha = .88f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    activeActivity?.displayName ?: "Cardio",
                    color = Color.White.copy(alpha = .76f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    cardioFormatDuration(activeElapsedSeconds),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (activeRunning) "Recording now · draft protected locally" else "Paused · ready when you are",
                    color = Color.White.copy(alpha = .66f),
                    fontSize = 9.sp
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f).heightIn(min = 50.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .clickable { onPrimary() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OPEN SESSION", color = CardioDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text("→", color = CardioDeep, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    CardioHeroControlButton(
                        icon = if (activeRunning) CardioUiIcon.PAUSE else CardioUiIcon.PLAY,
                        label = if (activeRunning) "Pause" else "Resume",
                        accent = Color(0xFF7DE3BD),
                        onClick = onToggleActive
                    )
                    CardioHeroControlButton(
                        icon = CardioUiIcon.STOP,
                        label = if (saveInProgress) "Saving…" else "Stop & Save",
                        accent = Color(0xFFFFA3A7),
                        enabled = !saveInProgress,
                        onClick = onSaveActive
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(0xFF7DE3BD), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CARDIO",
                        color = Color.White.copy(alpha = .80f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = .8.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Train with intent.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("Track the work. Watch the engine improve.", color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
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
                    Text("START CARDIO", color = CardioDeep, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("→", color = CardioDeep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CardioHeroBackdrop(active: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = if (active) .055f else .045f)
        val ring = Color.White.copy(alpha = if (active) .075f else .055f)
        drawCircle(
            color = ring,
            radius = size.minDimension * .38f,
            center = androidx.compose.ui.geometry.Offset(size.width * .91f, size.height * .10f),
            style = Stroke(width = 2.2f)
        )
        drawCircle(
            color = line,
            radius = size.minDimension * .27f,
            center = androidx.compose.ui.geometry.Offset(size.width * .91f, size.height * .10f),
            style = Stroke(width = 1.4f)
        )
        drawLine(
            color = line,
            start = androidx.compose.ui.geometry.Offset(size.width * .58f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height * .42f),
            strokeWidth = 1.2f
        )
        drawLine(
            color = line,
            start = androidx.compose.ui.geometry.Offset(size.width * .72f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height * .28f),
            strokeWidth = 1.2f
        )
    }
}

@Composable
private fun CardioHeroControlButton(
    icon: CardioUiIcon,
    label: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        Modifier.width(72.dp).heightIn(min = 52.dp)
            .background(Color.White.copy(alpha = .10f), RoundedCornerShape(15.dp))
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (enabled) "Available" else "Disabled"
            }
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CardioVectorIcon(icon, accent, Modifier.size(18.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(alpha = .88f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = if (running) "Recording" else "Paused"
                contentDescription = activity.displayName + " cardio. " + workoutType.label + ". " +
                    if (running) "Recording." else "Paused."
            }
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
                    Text(workoutType.label.uppercase(), color = Color.White.copy(alpha = .90f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text("  ·  draft saved", color = Color.White.copy(alpha = .64f), fontSize = 11.sp)
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


private enum class CardioUiIcon {
    ADD, HISTORY, PROGRESS, TROPHY, PLAY, PAUSE, STOP
}

@Composable
private fun CardioVectorIcon(
    icon: CardioUiIcon,
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * .105f
        when (icon) {
            CardioUiIcon.ADD -> {
                drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * .2f, h * .5f), end = androidx.compose.ui.geometry.Offset(w * .8f, h * .5f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, start = androidx.compose.ui.geometry.Offset(w * .5f, h * .2f), end = androidx.compose.ui.geometry.Offset(w * .5f, h * .8f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            CardioUiIcon.HISTORY -> {
                drawCircle(tint, radius = size.minDimension * .36f, center = center, style = Stroke(stroke))
                drawLine(tint, center, androidx.compose.ui.geometry.Offset(w * .5f, h * .30f), strokeWidth = stroke * .75f, cap = StrokeCap.Round)
                drawLine(tint, center, androidx.compose.ui.geometry.Offset(w * .68f, h * .56f), strokeWidth = stroke * .75f, cap = StrokeCap.Round)
                drawLine(tint, androidx.compose.ui.geometry.Offset(w * .17f, h * .20f), androidx.compose.ui.geometry.Offset(w * .17f, h * .42f), strokeWidth = stroke * .75f, cap = StrokeCap.Round)
                drawLine(tint, androidx.compose.ui.geometry.Offset(w * .17f, h * .20f), androidx.compose.ui.geometry.Offset(w * .36f, h * .20f), strokeWidth = stroke * .75f, cap = StrokeCap.Round)
            }
            CardioUiIcon.PROGRESS -> {
                val path = Path().apply {
                    moveTo(w * .12f, h * .72f)
                    lineTo(w * .34f, h * .52f)
                    lineTo(w * .52f, h * .60f)
                    lineTo(w * .77f, h * .28f)
                    lineTo(w * .88f, h * .36f)
                }
                drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(tint, androidx.compose.ui.geometry.Offset(w * .12f, h * .82f), androidx.compose.ui.geometry.Offset(w * .88f, h * .82f), strokeWidth = stroke * .7f, cap = StrokeCap.Round)
            }
            CardioUiIcon.TROPHY -> {
                drawArc(tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .25f, h * .18f),
                    size = androidx.compose.ui.geometry.Size(w * .50f, h * .50f),
                    style = Stroke(stroke))
                drawLine(tint, androidx.compose.ui.geometry.Offset(w * .5f, h * .43f), androidx.compose.ui.geometry.Offset(w * .5f, h * .74f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, androidx.compose.ui.geometry.Offset(w * .32f, h * .80f), androidx.compose.ui.geometry.Offset(w * .68f, h * .80f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawArc(tint, 90f, 180f, false,
                    androidx.compose.ui.geometry.Offset(w * .10f, h * .24f),
                    androidx.compose.ui.geometry.Size(w * .30f, h * .32f),
                    style = Stroke(stroke * .8f))
                drawArc(tint, -90f, 180f, false,
                    androidx.compose.ui.geometry.Offset(w * .60f, h * .24f),
                    androidx.compose.ui.geometry.Size(w * .30f, h * .32f),
                    style = Stroke(stroke * .8f))
            }
            CardioUiIcon.PLAY -> {
                val path = Path().apply {
                    moveTo(w * .34f, h * .22f)
                    lineTo(w * .78f, h * .50f)
                    lineTo(w * .34f, h * .78f)
                    close()
                }
                drawPath(path, tint)
            }
            CardioUiIcon.PAUSE -> {
                drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(w * .27f, h * .20f), size = androidx.compose.ui.geometry.Size(w * .16f, h * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke))
                drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(w * .57f, h * .20f), size = androidx.compose.ui.geometry.Size(w * .16f, h * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke))
            }
            CardioUiIcon.STOP -> {
                drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(w * .25f, h * .25f), size = androidx.compose.ui.geometry.Size(w * .50f, h * .50f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke))
            }
        }
    }
}

@Composable
private fun CardioHeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 10.dp)) {
        Text(label, color = Color.White.copy(alpha = .62f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioQuickAccessPanel(
    onLog: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onRecords: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(CardioSurface, RoundedCornerShape(21.dp))
            .border(1.dp, CardioBorder.copy(alpha = .72f), RoundedCornerShape(21.dp))
            .padding(horizontal = 13.dp, vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("QUICK ACCESS", color = CardioInk, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("ONE TAP", color = CardioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth()) {
            CardioQuickAccessItem(CardioUiIcon.ADD, "Log activity", "Previous session", CardioBlue, Modifier.weight(1f), onLog)
            Box(Modifier.width(1.dp).height(60.dp).background(CardioBorder.copy(alpha = .55f)))
            CardioQuickAccessItem(CardioUiIcon.HISTORY, "History", "All sessions", Color(0xFF7663C6), Modifier.weight(1f), onHistory)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardioBorder.copy(alpha = .55f)))
        Row(Modifier.fillMaxWidth()) {
            CardioQuickAccessItem(CardioUiIcon.PROGRESS, "Progress", "Load & trends", CardioAccent, Modifier.weight(1f), onProgress)
            Box(Modifier.width(1.dp).height(60.dp).background(CardioBorder.copy(alpha = .55f)))
            CardioQuickAccessItem(CardioUiIcon.TROPHY, "Records", "Personal bests", CardioGold, Modifier.weight(1f), onRecords)
        }
    }
}

@Composable
private fun CardioQuickAccessItem(
    icon: CardioUiIcon,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.heightIn(min = 64.dp).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CardioVectorIcon(icon, accent, Modifier.size(19.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CardioInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = CardioMuted, fontSize = 8.sp)
        }
        Text("›", color = CardioMuted, fontSize = 20.sp)
    }
}

@Composable
private fun CardioWeeklyOverview(
    minutes: Int,
    sessions: Int,
    distanceKm: Double,
    zone2Minutes: Int,
    loadSnapshot: CardioLoadSnapshot
) {
    Column(
        Modifier.fillMaxWidth()
            .background(CardioSurface, RoundedCornerShape(21.dp))
            .border(1.dp, CardioBorder.copy(alpha = .72f), RoundedCornerShape(21.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("THIS WEEK", color = CardioInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("Your last 7 days", color = CardioMuted, fontSize = 9.sp)
            }
            Box(
                Modifier.background(CardioAccent.copy(alpha = if (SuperhumanAppearance.darkMode) .17f else .09f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text("$sessions SESSION${if (sessions == 1) "" else "S"}", color = CardioAccent, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardioInlineStat("MINUTES", minutes.toString(), null, CardioBlue, Modifier.weight(1f))
            CardioStatDivider()
            CardioInlineStat("DISTANCE", cardioFormatNumber(distanceKm), "km", CardioAccent, Modifier.weight(1f))
            CardioStatDivider()
            CardioInlineStat("ZONE 2", zone2Minutes.toString(), "min", Color(0xFF7663C6), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardioBorder.copy(alpha = .55f)))
        Spacer(Modifier.height(11.dp))
        if (loadSnapshot.sourceCoverage > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CardioCompactInsight("CARDIO LOAD", formatCardioLoad(loadSnapshot.recent7DayLoad), CardioBlue, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CardioCompactInsight(
                    "LOAD RATIO",
                    loadSnapshot.loadRatio?.let { String.format(Locale.US, "%.2f", it) } ?: "Baseline",
                    Color(0xFF7663C6),
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(cardioLoadLabel(loadSnapshot), color = CardioMuted, fontSize = 9.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(CardioBlue, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Cardio load unlocks with RPE or measured HR zones", color = CardioInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("No estimate is shown until there is enough real input.", color = CardioMuted, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun CardioInlineStat(
    label: String,
    value: String,
    unit: String?,
    accent: Color,
    modifier: Modifier
) {
    Column(modifier.padding(horizontal = 7.dp)) {
        Box(Modifier.width(18.dp).height(3.dp).background(accent, RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(7.dp))
        Text(label, color = CardioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = CardioInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = CardioMuted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun CardioStatDivider() {
    Box(Modifier.width(1.dp).height(42.dp).background(CardioBorder.copy(alpha = .55f)))
}

@Composable
private fun CardioCompactInsight(label: String, value: String, accent: Color, modifier: Modifier) {
    Row(
        modifier.background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .14f else .07f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = CardioMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text(value, color = CardioInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CardioLatestActivityPanel(session: CardioSession, onOpen: () -> Unit) {
    val whenText = remember(session.endedAt) {
        Instant.ofEpochMilli(session.endedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM · HH:mm"))
    }
    Column(
        Modifier.fillMaxWidth()
            .background(CardioSurface, RoundedCornerShape(21.dp))
            .border(1.dp, CardioBorder.copy(alpha = .72f), RoundedCornerShape(21.dp))
            .clickable { onOpen() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("LATEST ACTIVITY", color = CardioInk, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("VIEW  ›", color = CardioAccent, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(CardioAccent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CardioVectorIcon(CardioUiIcon.PROGRESS, CardioAccent, Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.activity.displayName, color = CardioInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    if (session.workoutType != CardioWorkoutType.FREE) {
                        Spacer(Modifier.width(8.dp))
                        Text(session.workoutType.label.uppercase(), color = CardioBlue, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(whenText, color = CardioMuted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            CardioMiniFact("TIME", cardioFormatDuration(session.durationSeconds), Modifier.weight(1f))
            session.distanceKm?.let { CardioMiniFact("DISTANCE", "${cardioFormatNumber(it)} km", Modifier.weight(1f)) }
            session.avgHeartRate?.let { CardioMiniFact("AVG HR", "$it bpm", Modifier.weight(1f)) }
        }
        cardioPerformanceSummary(session)?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = CardioAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CardioMiniFact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = CardioMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, color = CardioInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioLiveControls(
    running: Boolean,
    onToggle: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(CardioDeep, RoundedCornerShape(21.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CardioControlButton(
            icon = if (running) CardioUiIcon.PAUSE else CardioUiIcon.PLAY,
            label = if (running) "Pause" else "Resume",
            accent = if (running) Color(0xFF63CDB0) else Color(0xFF79D9B5),
            modifier = Modifier.weight(1f),
            onClick = onToggle
        )
        CardioControlButton(
            icon = CardioUiIcon.STOP,
            label = "Finish",
            accent = Color(0xFFF08B8F),
            modifier = Modifier.weight(1f),
            onClick = onFinish
        )
    }
}

@Composable
private fun CardioControlButton(
    icon: CardioUiIcon,
    label: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.heightIn(min = 58.dp)
            .background(Color.White.copy(alpha = .075f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(38.dp).background(accent.copy(alpha = .16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CardioVectorIcon(icon, accent, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioLiveMetricsPanel(metrics: CardioLiveSensorMetrics) {
    val connectionLabel = when (metrics.connection) {
        CardioSensorConnectionState.CONNECTED -> "LIVE"
        CardioSensorConnectionState.STALE -> "STALE"
        CardioSensorConnectionState.RECONNECTING -> "RECONNECTING"
        CardioSensorConnectionState.CONNECTING -> "CONNECTING"
        CardioSensorConnectionState.SCANNING -> "SCANNING"
        CardioSensorConnectionState.ERROR -> "ERROR"
        CardioSensorConnectionState.DISCONNECTED -> "DISCONNECTED"
        CardioSensorConnectionState.NO_SENSOR -> "NO SENSOR"
    }
    val statusColor = when (metrics.connection) {
        CardioSensorConnectionState.CONNECTED -> CardioAccent
        CardioSensorConnectionState.STALE,
        CardioSensorConnectionState.RECONNECTING,
        CardioSensorConnectionState.CONNECTING,
        CardioSensorConnectionState.SCANNING -> CardioGold
        CardioSensorConnectionState.ERROR -> CardioCoral
        else -> CardioMuted
    }

    Column(
        Modifier.fillMaxWidth()
            .background(CardioSurface, RoundedCornerShape(21.dp))
            .border(1.dp, CardioBorder.copy(alpha = .72f), RoundedCornerShape(21.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LIVE METRICS",
                color = CardioInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(7.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                "${metrics.sourceLabel} · $connectionLabel",
                color = statusColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CardioLiveMetricCell(
                "HEART RATE",
                metrics.currentHeartRateBpm?.toString() ?: "—",
                "bpm",
                Color(0xFFC85772),
                Modifier.weight(1f)
            )
            CardioStatDivider()
            CardioLiveMetricCell(
                "AVG HR",
                metrics.averageHeartRateBpm?.toString() ?: "—",
                "bpm",
                CardioBlue,
                Modifier.weight(1f)
            )
            CardioStatDivider()
            CardioLiveMetricCell(
                "MAX HR",
                metrics.maxHeartRateBpm?.toString() ?: "—",
                "bpm",
                CardioAccent,
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        val freshness = metrics.lastSampleAgeMs
            ?.let { " · last ${(it / 1000L).coerceAtLeast(0L)}s ago" }
            .orEmpty()
        val zone = metrics.currentZone?.let { "Z$it" } ?: "—"
        Text(
            "Zone $zone · HR coverage ${String.format(Locale.US, "%.0f", metrics.heartRateCoveragePct)}%$freshness",
            color = CardioMuted,
            fontSize = 10.sp
        )
        if (metrics.connection != CardioSensorConnectionState.CONNECTED) {
            Spacer(Modifier.height(4.dp))
            Text(metrics.message, color = CardioMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CardioLiveMetricCell(
    label: String,
    value: String,
    unit: String?,
    accent: Color,
    modifier: Modifier
) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Box(Modifier.width(16.dp).height(3.dp).background(accent, RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(7.dp))
        Text(label, color = CardioMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = CardioInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = CardioMuted, fontSize = 8.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

private fun cardioPerformanceSummary(session: CardioSession): String? = when (session.activity.paceMode) {
    CardioPaceMode.PER_KM -> session.avgPaceSecPerKm?.let { "${cardioFormatPace(it)}/km average pace" }
    CardioPaceMode.SPEED -> session.avgSpeedKmh?.let { "${cardioFormatNumber(it)} km/h average speed" }
    CardioPaceMode.PER_500M -> session.avgSplit500mSeconds?.let { "${cardioFormatPace(it)}/500m average split" }
    CardioPaceMode.PER_100M -> session.avgPace100mSeconds?.let { "${cardioFormatPace(it)}/100m average pace" }
    CardioPaceMode.NONE -> null
}

@Composable
private fun CardioSessionHero(session: CardioSession) {
    val whenText = remember(session.startedAt) {
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))
    }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(Brush.linearGradient(listOf(CardioDeep, Color(0xFF0B7A69))))
            .padding(18.dp)
    ) {
        Box(
            Modifier.size(88.dp).align(Alignment.TopEnd)
                .offset(x = 31.dp, y = (-30).dp)
                .background(Color.White.copy(alpha = .055f), CircleShape)
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.activity.displayName.uppercase(), color = Color.White.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(session.workoutType.label.uppercase(), color = Color.White.copy(alpha = .88f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(cardioFormatDuration(session.durationSeconds), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            cardioPerformanceSummary(session)?.let {
                Text(it, color = Color.White.copy(alpha = .84f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(7.dp))
            Text(whenText, color = Color.White.copy(alpha = .60f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun CardioSessionOverview(session: CardioSession) {
    val facts = buildList<Pair<String, String>> {
        add("DURATION" to cardioFormatDuration(session.durationSeconds))
        session.distanceKm?.let { add("DISTANCE" to "${cardioFormatNumber(it)} km") }
        session.avgHeartRate?.let { add("AVG HR" to "$it bpm") }
        session.maxHeartRate?.let { add("MAX HR" to "$it bpm") }
        session.caloriesKcal?.let { add("CALORIES" to "${cardioFormatNumber(it)} kcal") }
        session.rpe?.let { add("RPE" to "${cardioFormatNumber(it)}/10") }
        session.elevationGainM?.let { add("ELEVATION" to "${cardioFormatNumber(it)} m") }
        session.cadence?.let { add("CADENCE" to it.toString()) }
        cardioPerformanceSummary(session)?.let { add("PERFORMANCE" to it) }
    }
    CardioSection("SESSION OVERVIEW", "Only values actually saved for this workout") {
        val rows = facts.chunked(2)
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    CardioFactCell(label, value, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex < rows.size - 1) {
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(CardioBorder.copy(alpha = .45f)))
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun CardioFactCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp)) {
        Text(label, color = CardioMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
        Text(value, color = CardioInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioInsightPanel(load: Double?, comparison: String?) {
    CardioSection("INSIGHTS", "Useful signals from the data you actually recorded") {
        load?.let {
            CardioInsightRow("CARDIO LOAD", formatCardioLoad(it), "Session training load", CardioBlue)
        }
        if (load != null && comparison != null) Spacer(Modifier.height(8.dp))
        comparison?.let {
            CardioInsightRow("EFFICIENCY", "Improvement signal", it, CardioAccent)
        }
    }
}

@Composable
private fun CardioInsightRow(kicker: String, value: String, detail: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth()
            .background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .14f else .07f), RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(38.dp).background(accent, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(kicker, color = accent, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
            Text(value, color = CardioInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(detail, color = CardioMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CardioZonePanel(title: String, zones: Map<Int, Int>) {
    val total = zones.values.sum().coerceAtLeast(1)
    CardioSection(title, "Measured zone time only") {
        (1..5).forEach { zone ->
            val seconds = zones[zone] ?: 0
            val fraction = seconds.toFloat() / total.toFloat()
            val accent = cardioZoneColor(zone)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Z$zone", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(26.dp))
                Box(
                    Modifier.weight(1f).height(7.dp)
                        .background(CardioBorder.copy(alpha = .45f), RoundedCornerShape(7.dp))
                ) {
                    if (seconds > 0) {
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceIn(.02f, 1f)).fillMaxHeight()
                                .background(accent, RoundedCornerShape(7.dp))
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(cardioFormatDuration(seconds), color = CardioMuted, fontSize = 8.sp, modifier = Modifier.width(42.dp))
            }
            if (zone < 5) Spacer(Modifier.height(7.dp))
        }
    }
}

private fun cardioZoneColor(zone: Int): Color = when (zone) {
    1 -> Color(0xFF5BA8C9)
    2 -> Color(0xFF2EAA88)
    3 -> Color(0xFFC8A33A)
    4 -> Color(0xFFE07A4F)
    else -> Color(0xFFC8575E)
}

@Composable
private fun CardioActionCompact(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .16f else .09f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CardioInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = CardioMuted, fontSize = 8.sp)
        }
        Text("›", color = accent, fontSize = 22.sp)
    }
}

@Composable
private fun CardioLoadPanel(snapshot: CardioLoadSnapshot) {
    CardioSection("TRAINING LOAD", "7-day strain compared with your recent baseline") {
        if (snapshot.sourceCoverage == 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(CardioBlue.copy(alpha = if (SuperhumanAppearance.darkMode) .16f else .09f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CardioVectorIcon(CardioUiIcon.PROGRESS, CardioBlue, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("Build your load baseline", color = CardioInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Add RPE or measured HR-zone time to score sessions.", color = CardioMuted, fontSize = 9.sp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("7-DAY LOAD", color = CardioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(formatCardioLoad(snapshot.recent7DayLoad), color = CardioInk, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(cardioLoadLabel(snapshot), color = CardioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("BASELINE", color = CardioMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(snapshot.previous21DayWeeklyAverage?.let(::formatCardioLoad) ?: "Building", color = CardioInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(
                        snapshot.loadRatio?.let { "Ratio ${String.format(Locale.US, "%.2f", it)}" } ?: "${snapshot.sourceCoverage} scored session${if (snapshot.sourceCoverage == 1) "" else "s"}",
                        color = CardioMuted,
                        fontSize = 8.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val ratioFraction = ((snapshot.loadRatio ?: 1.0) / 1.5).toFloat().coerceIn(.05f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).background(CardioBorder.copy(alpha = .45f), RoundedCornerShape(8.dp))) {
                Box(
                    Modifier.fillMaxWidth(ratioFraction).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(CardioBlue, CardioAccent)), RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.height(7.dp))
            Text("Load uses measured zone time when available, otherwise RPE × minutes.", color = CardioMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun CardioWeeklyProgressPanel(minutes: Int, sessions: Int, distanceKm: Double, zone2Minutes: Int) {
    CardioSection("LAST 7 DAYS", "Consistency and volume") {
        Row(Modifier.fillMaxWidth()) {
            CardioInlineStat("MINUTES", minutes.toString(), null, CardioBlue, Modifier.weight(1f))
            CardioStatDivider()
            CardioInlineStat("SESSIONS", sessions.toString(), null, CardioAccent, Modifier.weight(1f))
            CardioStatDivider()
            CardioInlineStat("ZONE 2", zone2Minutes.toString(), "min", Color(0xFF7663C6), Modifier.weight(1f))
        }
        if (distanceKm > 0.0) {
            Spacer(Modifier.height(11.dp))
            Text("${cardioFormatNumber(distanceKm)} km recorded this week", color = CardioMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CardioActivityProgressRow(
    activity: CardioActivityType,
    sessionCount: Int,
    minutes: Int,
    distanceKm: Double
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(28.dp).background(CardioAccent, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.displayName, color = CardioInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("$sessionCount session${if (sessionCount == 1) "" else "s"}", color = CardioMuted, fontSize = 8.sp)
        }
        Text(
            "${minutes}m${if (distanceKm > 0.0) " · ${cardioFormatNumber(distanceKm)} km" else ""}",
            color = CardioInk,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CardioFeaturedRecords(sessions: List<CardioSession>) {
    val longest = sessions.maxByOrNull { it.durationSeconds }
    val farthest = sessions.filter { it.distanceKm != null }.maxByOrNull { it.distanceKm ?: 0.0 }
    val fastestPerKm = sessions.mapNotNull { it.avgPaceSecPerKm }.minOrNull()
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(CardioGold.copy(alpha = if (SuperhumanAppearance.darkMode) .26f else .13f), CardioSurface)
                ),
                RoundedCornerShape(21.dp)
            )
            .border(1.dp, CardioGold.copy(alpha = .28f), RoundedCornerShape(21.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(CardioGold.copy(alpha = .14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CardioVectorIcon(CardioUiIcon.TROPHY, CardioGold, Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("FEATURED BESTS", color = CardioGold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                Text("Your strongest verified marks", color = CardioInk, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            CardioRecordStat("LONGEST", longest?.let { cardioFormatDuration(it.durationSeconds) } ?: "—", Modifier.weight(1f))
            CardioStatDivider()
            CardioRecordStat("FARTHEST", farthest?.distanceKm?.let { "${cardioFormatNumber(it)} km" } ?: "—", Modifier.weight(1f))
            CardioStatDivider()
            CardioRecordStat("PACE", fastestPerKm?.let { "${cardioFormatPace(it)}/km" } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardioRecordStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 7.dp)) {
        Text(label, color = CardioGold, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Text(value, color = CardioInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
private fun CardioUndoBanner(onUndo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                CardioBlue.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, CardioBlue.copy(alpha = .28f), RoundedCornerShape(14.dp))
            .clickable { onUndo() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Saved", color = CardioBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(9.dp))
        Text(
            "Undo Stop & Save",
            color = CardioInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("UNDO", color = CardioBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CardioFeedback(message: String, isError: Boolean = false) {
    val accent = if (isError) CardioCoral else CardioAccent
    Row(
        Modifier.fillMaxWidth()
            .background(
                accent.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isError) "!" else "OK", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
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
