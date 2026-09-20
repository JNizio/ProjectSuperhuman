package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private val ExerciseNavy get() = if (SuperhumanAppearance.darkMode) Color(0xFF8FC5FF) else Color(0xFF082D66)
private val ExerciseBlue get() = if (SuperhumanAppearance.darkMode) superhumanBlue else Color(0xFF0D6CB4)
private val ExerciseInk get() = if (SuperhumanAppearance.darkMode) superhumanTextPrimary else Color(0xFF0B1F35)
private val ExerciseMuted get() = if (SuperhumanAppearance.darkMode) superhumanTextMuted else Color(0xFF64748B)
private val ExerciseGreen get() = superhumanGreen
private val ExerciseBg get() = if (SuperhumanAppearance.darkMode) superhumanBackground else Color(0xFFF5F8FC)
private val ExerciseSoft get() = if (SuperhumanAppearance.darkMode) superhumanSurfaceSoft else Color(0xFFEAF4FF)
private val ExercisePurple get() = if (SuperhumanAppearance.darkMode) Color(0xFFA99BFF) else Color(0xFF6559C7)
private val ExerciseSurface get() = if (SuperhumanAppearance.darkMode) superhumanSurface else Color.White
private val ExerciseCardBorder get() = if (SuperhumanAppearance.darkMode) superhumanBorder else Color(0xFFE9EFF5)
private val ExerciseRowSurface get() = if (SuperhumanAppearance.darkMode) superhumanSurfaceSoft else Color(0xFFF9FBFD)
private val ExerciseQuietSurface get() = if (SuperhumanAppearance.darkMode) superhumanSurfaceSoft else Color(0xFFF8FAFC)
private val ExerciseSelectionOff get() = if (SuperhumanAppearance.darkMode) superhumanSurfaceSoft else Color(0xFFF0F4F7)
private val ExerciseHeroButtonText = Color(0xFF082D66)
private val ExerciseData = NativeDomainData.forDomain(HealthDomain.EXERCISE)

data class NativeExercise(
    val id: String,
    val name: String,
    val group: String,
    val equipment: String,
    val difficulty: String = "",
    val mechanic: String = "",
    val met: Double = 0.0,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val imageStart: String? = null,
    val imagePeak: String? = null,
    val imageMain: String? = null
)

data class NativeWorkoutSet(
    val exercise: NativeExercise,
    val reps: Int,
    val loadKg: Double,
    val timestamp: Long,
    val type: String = "Work",
    val rir: Int? = null,
    val rpe: Double? = null,
    val supersetTag: String? = null
) { val volume: Double get() = reps * loadKg }

data class WorkoutRoutine(val name: String, val exerciseIds: List<String>)

private fun jsonStrings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
private fun pretty(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun exerciseNumber(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

internal fun isCardioOnlyRepDbExercise(id: String, name: String, equipment: String): Boolean {
    val key = "$id $name $equipment".lowercase()
    val cardioOnlyTerms = listOf(
        "treadmill",
        "stationary bike",
        "exercise bike",
        "spin bike",
        "air bike",
        "assault bike",
        "indoor cycling",
        "cycling",
        "bicycle",
        "elliptical",
        "cross trainer",
        "stair climber",
        "stair stepper",
        "stepmill",
        "rowing machine",
        "rowing erg",
        "row erg",
        "rower",
        "ski erg",
        "ski-erg",
        "jump rope",
        "skipping rope",
        "running",
        "walking",
        "swimming"
    )
    return cardioOnlyTerms.any { term -> key.contains(term) }
}

private suspend fun loadRepDb(context: android.content.Context): List<NativeExercise> = withContext(Dispatchers.IO) {
    runCatching {
        val root = JSONObject(context.assets.open("repdb/exercises.json").bufferedReader().use { it.readText() })
        val a = root.getJSONArray("exercises")
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val id = o.getString("id")
            val name = o.optString("name_en", id)
            val equipment = pretty(o.optString("equipment", "Bodyweight"))
            if (isCardioOnlyRepDbExercise(id, name, equipment)) return@mapNotNull null
            val flat = o.optJSONObject("images")?.optJSONObject("flat")
            NativeExercise(
                id, name, pretty(o.optString("body_part", "Other")), equipment,
                pretty(o.optString("difficulty")), pretty(o.optString("mechanic")), o.optDouble("met", 0.0), jsonStrings(o.optJSONArray("primary_muscles")), jsonStrings(o.optJSONArray("secondary_muscles")),
                jsonStrings(o.optJSONArray("instructions_en")), jsonStrings(o.optJSONArray("tips_en")), flat?.optString("start")?.takeIf(String::isNotBlank), flat?.optString("peak")?.takeIf(String::isNotBlank), flat?.optString("main")?.takeIf(String::isNotBlank)
            )
        }
    }.getOrElse { emptyList() }
}

private fun loadRoutines(context: android.content.Context): List<WorkoutRoutine> {
    val raw = context.getSharedPreferences("superhuman_training", 0).getString("routines", "[]") ?: "[]"
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { i -> val o = a.getJSONObject(i); WorkoutRoutine(o.getString("name"), jsonStrings(o.optJSONArray("exerciseIds"))) }
    }.getOrElse { emptyList() }
}

private fun saveRoutines(context: android.content.Context, routines: List<WorkoutRoutine>) {
    val a = JSONArray(); routines.forEach { r -> a.put(JSONObject().put("name", r.name).put("exerciseIds", JSONArray(r.exerciseIds))) }
    context.getSharedPreferences("superhuman_training", 0).edit().putString("routines", a.toString()).apply()
}

@Composable
private fun RepDbImage(path: String?, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { path?.let { runCatching { context.assets.open("repdb/$it").use(BitmapFactory::decodeStream) }.getOrNull() } } }
    val imageGradient = if (SuperhumanAppearance.darkMode) {
        listOf(superhumanSurfaceElevated, superhumanSurfaceSoft)
    } else {
        listOf(Color(0xFFF7FBFF), Color(0xFFE9F4FC))
    }
    Box(modifier.background(Brush.linearGradient(imageGradient), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else Text("EX", color = ExerciseBlue, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun NativeExerciseParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<List<NativeExercise>>(emptyList()) }
    var recent by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var sessionRows by remember { mutableStateOf<List<HealthValue>>(emptyList()) }
    var mode by remember { mutableStateOf("home") }
    var selected by remember { mutableStateOf<NativeExercise?>(null) }
    var query by remember { mutableStateOf("") }
    var showCount by remember { mutableIntStateOf(12) }
    var muscleFilter by remember { mutableStateOf<String?>(null) }
    var equipmentFilter by remember { mutableStateOf<String?>(null) }
    var difficultyFilter by remember { mutableStateOf<String?>(null) }
    var mechanicFilter by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(loadStrengthFavorites(context)) }
    var selectedHistorySessionId by remember { mutableStateOf<String?>(null) }
    var historyEditName by remember { mutableStateOf("") }
    var historyEditNotes by remember { mutableStateOf("") }
    var historyEditRpe by remember { mutableStateOf("") }
    var pendingWorkoutDelete by remember { mutableStateOf<String?>(null) }
    var pendingSetDeleteTimestamp by remember { mutableStateOf<Long?>(null) }
    val session = remember { mutableStateListOf<NativeWorkoutSet>() }
    val workoutExercises = remember { mutableStateListOf<NativeExercise>() }
    var repsText by remember { mutableStateOf("10") }
    var loadText by remember { mutableStateOf("0") }
    var rirText by remember { mutableStateOf("2") }
    var rpeText by remember { mutableStateOf("") }
    var setType by remember { mutableStateOf("Work") }
    var supersetTag by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var activeSessionId by remember { mutableStateOf("") }
    var workoutName by remember { mutableStateOf("") }
    var workoutNotes by remember { mutableStateOf("") }
    var sessionRpeText by remember { mutableStateOf("") }
    var restSeconds by remember { mutableIntStateOf(0) }
    var restTarget by remember { mutableIntStateOf(120) }
    var restEndsAt by remember { mutableLongStateOf(0L) }
    var routines by remember { mutableStateOf(loadRoutines(context)) }
    var routineName by remember { mutableStateOf("") }
    var routineSelection by remember { mutableStateOf<List<String>>(emptyList()) }
    var routineQuery by remember { mutableStateOf("") }
    var editingRoutineIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRoutineDelete by remember { mutableStateOf<Int?>(null) }
    var summarySets by remember { mutableIntStateOf(0) }
    var summaryWorkingSets by remember { mutableIntStateOf(0) }
    var summaryExercises by remember { mutableIntStateOf(0) }
    var summaryVolume by remember { mutableDoubleStateOf(0.0) }
    var summaryDuration by remember { mutableIntStateOf(0) }
    var summaryPrs by remember { mutableStateOf<List<String>>(emptyList()) }
    var summaryProgression by remember { mutableStateOf<List<String>>(emptyList()) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val now = System.currentTimeMillis()
        val lookback = 5L * 365L * 86400000L
        recent = ExerciseData.between("exercise_set", now - lookback, now).sortedByDescending { it.timestampEpochMs }.take(5000)
        sessionRows = ExerciseData.between("workout_session", now - lookback, now).sortedByDescending { it.timestampEpochMs }.take(1000)
    }

    fun writeActiveDraft() {
        if (startedAt <= 0L) return
        saveActiveWorkoutDraft(
            context,
            ActiveWorkoutDraft(
                startedAt = startedAt,
                selectedExerciseId = selected?.id,
                exerciseIds = workoutExercises.map { it.id },
                sets = session.map { set ->
                    ActiveWorkoutSetDraft(
                        exerciseId = set.exercise.id,
                        reps = set.reps,
                        loadKg = set.loadKg,
                        timestamp = set.timestamp,
                        type = set.type,
                        rir = set.rir,
                        rpe = set.rpe,
                        supersetTag = set.supersetTag
                    )
                },
                restEndsAt = restEndsAt
            )
        )
    }

    suspend fun persistSet(set: NativeWorkoutSet) {
        NativeDataHub.saveValues(listOf(HealthValue(
            HealthDomain.EXERCISE, "exercise_set", set.volume, "kg-reps", set.timestamp, "repdb-exercise",
            mapOf(
                "exerciseId" to set.exercise.id, "exerciseName" to set.exercise.name, "group" to set.exercise.group, "equipment" to set.exercise.equipment,
                "reps" to set.reps.toString(), "loadKg" to set.loadKg.toString(), "met" to set.exercise.met.toString(), "setType" to set.type,
                "rir" to (set.rir?.toString() ?: ""), "rpe" to (set.rpe?.toString() ?: ""), "superset" to (set.supersetTag ?: ""),
                "sessionId" to activeSessionId
            )
        )))
        refresh()
        feedbackMessage = "Set saved"
    }

    fun addSet(set: NativeWorkoutSet, startRest: Boolean = true) {
        session.add(set)
        if (startRest) {
            restSeconds = restTarget
            restEndsAt = System.currentTimeMillis() + restTarget * 1000L
        }
        writeActiveDraft()
        scope.launch { persistSet(set) }
    }

    LaunchedEffect(Unit) {
        catalog = loadRepDb(context)
        refresh()
        loadActiveWorkoutDraft(context)?.let { draft ->
            startedAt = draft.startedAt
            activeSessionId = "strength-${draft.startedAt}"
            loadStrengthActiveMeta(context, draft.startedAt).let { meta ->
                workoutName = meta.workoutName
                workoutNotes = meta.notes
                sessionRpeText = meta.sessionRpe
            }
            workoutExercises.clear()
            workoutExercises.addAll(draft.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } })
            session.clear()
            session.addAll(
                draft.sets.mapNotNull { stored ->
                    val exercise = catalog.find { it.id == stored.exerciseId } ?: return@mapNotNull null
                    NativeWorkoutSet(
                        exercise = exercise,
                        reps = stored.reps,
                        loadKg = stored.loadKg,
                        timestamp = stored.timestamp,
                        type = stored.type,
                        rir = stored.rir,
                        rpe = stored.rpe,
                        supersetTag = stored.supersetTag
                    )
                }
            )
            selected = draft.selectedExerciseId?.let { id -> catalog.find { it.id == id } } ?: workoutExercises.firstOrNull()
            restEndsAt = draft.restEndsAt
            restSeconds = (((restEndsAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L).toInt()
            mode = "home"
        }
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2200)
            feedbackMessage = null
        }
    }

    LaunchedEffect(startedAt, workoutExercises.size, session.size, selected?.id, restEndsAt) {
        if (startedAt > 0L) writeActiveDraft()
    }

    LaunchedEffect(startedAt, workoutName, workoutNotes, sessionRpeText) {
        if (startedAt > 0L) saveStrengthActiveMeta(context, startedAt, workoutName, workoutNotes, sessionRpeText)
    }

    LaunchedEffect(restSeconds) {
        if (restSeconds > 0) {
            delay(1000)
            restSeconds -= 1
            if (restSeconds <= 0) restEndsAt = 0L
        }
    }

    fun startWorkout(exercises: List<NativeExercise> = emptyList()) {
        session.clear()
        workoutExercises.clear()
        workoutExercises.addAll(exercises)
        selected = exercises.firstOrNull()
        startedAt = System.currentTimeMillis()
        activeSessionId = "strength-$startedAt"
        workoutName = ""
        workoutNotes = ""
        sessionRpeText = ""
        restSeconds = 0
        restEndsAt = 0L
        mode = "workout"
        writeActiveDraft()
    }

    fun finishWorkout() {
        val now = System.currentTimeMillis()
        val workoutStartedAt = startedAt
        val finishedSessionId = activeSessionId.ifBlank { "strength-$workoutStartedAt" }
        val finalName = workoutName.trim().ifBlank { inferStrengthWorkoutNameFromExercises(workoutExercises) }
        summarySets = session.size
        summaryWorkingSets = session.count { it.type != "Warmup" }
        summaryExercises = workoutExercises.distinctBy { it.id }.size
        summaryVolume = session.filter { it.type != "Warmup" }.sumOf { it.volume }
        summaryDuration = max(1, ((now - workoutStartedAt) / 60000L).toInt())
        summaryPrs = detectStrengthSessionPrs(session, recent.filter { it.timestampEpochMs < workoutStartedAt })
        summaryProgression = summaryPrs.take(3)
        restSeconds = 0
        restEndsAt = 0L
        clearActiveWorkoutDraft(context)
        clearStrengthActiveMeta(context, workoutStartedAt)
        startedAt = 0L
        scope.launch {
            NativeDataHub.saveValues(listOf(HealthValue(
                HealthDomain.EXERCISE,
                "workout_session",
                summaryWorkingSets.toDouble(),
                "sets",
                now,
                "repdb-exercise",
                mapOf(
                    "sessionId" to finishedSessionId,
                    "startTime" to workoutStartedAt.toString(),
                    "endTime" to now.toString(),
                    "durationMin" to summaryDuration.toString(),
                    "workoutName" to finalName,
                    "totalSets" to summarySets.toString(),
                    "workingSets" to summaryWorkingSets.toString(),
                    "volumeKg" to summaryVolume.toString(),
                    "exerciseCount" to summaryExercises.toString(),
                    "sessionRpe" to sessionRpeText.trim(),
                    "notes" to workoutNotes.trim()
                )
            )))
            refresh()
            mode = "summary"
        }
    }

    fun removeLoggedSet(set: NativeWorkoutSet) {
        session.remove(set)
        writeActiveDraft()
        scope.launch {
            recent.firstOrNull { it.timestampEpochMs == set.timestamp && it.metadata["exerciseId"] == set.exercise.id }?.let {
                NativeDataHub.deleteValue(it)
            }
            refresh()
        }
    }

    fun editLoggedSet(set: NativeWorkoutSet) {
        loadText = exerciseNumber(set.loadKg)
        repsText = set.reps.toString()
        rirText = set.rir?.toString() ?: ""
        rpeText = set.rpe?.toString() ?: ""
        setType = set.type
        supersetTag = set.supersetTag
        removeLoggedSet(set)
    }

    val completedSessions = buildStrengthSessions(sessionRows, recent)
    val strengthPrs = calculateStrengthPrs(recent, completedSessions)
    val strengthProgress = calculateStrengthProgress(recent, completedSessions)
    val muscleVolume = calculateMuscleVolume(recent, catalog)
    val weekRecent = recent.filter { it.timestampEpochMs > System.currentTimeMillis() - 7L * 86400000L && isStrengthWorkingSet(it) }

    BackHandler {
        if (mode == "home") onBack() else mode = "home"
    }

    Column(Modifier.fillMaxSize().background(ExerciseBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TrainingHeader(mode) { if (mode == "home") onBack() else mode = "home" }
        feedbackMessage?.let { ExerciseFeedbackBanner(it) }
        when (mode) {
            "home" -> {
                val lastName = recent.firstOrNull()?.metadata?.get("exerciseName") ?: "No workout logged yet"
                TrainingHero(lastName, weekRecent.size, weekRecent.sumOf { it.value }.roundToInt(), startedAt > 0L) {
                    if (startedAt > 0L) mode = "workout" else startWorkout()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    TrainingNavTile("R", "ROUTINES", "Reusable workouts", ExerciseBlue, Modifier.weight(1f)) { mode = "routines" }
                    TrainingNavTile("H", "HISTORY", "Completed workouts", ExercisePurple, Modifier.weight(1f)) { mode = "history" }
                    TrainingNavTile("P", "PROGRESS", "Trends & records", ExerciseGreen, Modifier.weight(1f)) { mode = "progress" }
                }
                if (routines.isNotEmpty()) PolishedSection("QUICK ROUTINES", "Jump straight into a saved plan") { routines.take(3).forEach { r -> RoutineRow(r, catalog) { startWorkout(it) } } }
                PolishedSection("TRAINING OVERVIEW", "Your last 7 days") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("WORKING SETS", weekRecent.size.toString(), "this week", ExerciseBlue, Modifier.weight(1f))
                        MetricTile("TRAINING VOLUME", weekRecent.sumOf { it.value }.roundToInt().toString(), "kg this week", ExerciseGreen, Modifier.weight(1f))
                        MetricTile("EXERCISES", catalog.size.toString(), "in library", ExercisePurple, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp)); WideActionTile("EXERCISE LIBRARY", "Browse exercises", ExerciseBlue) { mode = "library" }
                }
            }
            "library" -> {
                val muscles = catalog.flatMap { it.primaryMuscles + it.secondaryMuscles }.map(::pretty).distinct().sorted()
                val equipment = catalog.map { it.equipment }.filter(String::isNotBlank).distinct().sorted()
                val difficulties = catalog.map { it.difficulty }.filter(String::isNotBlank).distinct().sorted()
                val mechanics = catalog.map { it.mechanic }.filter(String::isNotBlank).distinct().sorted()
                fun next(current: String?, values: List<String>): String? {
                    if (values.isEmpty()) return null
                    if (current == null) return values.first()
                    val index = values.indexOf(current)
                    return if (index < 0 || index == values.lastIndex) null else values[index + 1]
                }
                val filtered = remember(catalog, query, muscleFilter, equipmentFilter, difficultyFilter, mechanicFilter, favoritesOnly, favorites) {
                    val q = query.trim().lowercase()
                    catalog.filter { e ->
                        val searchable = listOf(e.name, e.group, e.equipment, e.difficulty, e.mechanic) + e.primaryMuscles + e.secondaryMuscles
                        (q.isBlank() || searchable.any { it.lowercase().contains(q) }) &&
                            (muscleFilter == null || (e.primaryMuscles + e.secondaryMuscles).map(::pretty).contains(muscleFilter)) &&
                            (equipmentFilter == null || e.equipment == equipmentFilter) &&
                            (difficultyFilter == null || e.difficulty == difficultyFilter) &&
                            (mechanicFilter == null || e.mechanic == mechanicFilter) &&
                            (!favoritesOnly || e.id in favorites)
                    }
                }
                val recentIds = recent.mapNotNull { it.metadata["exerciseId"] }.distinct().take(6)
                HeroStrip("EXERCISE LIBRARY", "${catalog.size} exercises", "", ExerciseBlue)
                if (recentIds.isNotEmpty() && query.isBlank() && !favoritesOnly) {
                    PolishedSection("RECENTLY USED", "") {
                        recentIds.mapNotNull { id -> catalog.find { it.id == id } }.forEach { e ->
                            ExerciseResultRow(
                                e, startedAt > 0L, e.id in favorites,
                                { selected = e; mode = "detail" },
                                { if (workoutExercises.none { it.id == e.id }) workoutExercises.add(e); selected = e; mode = "workout"; writeActiveDraft() },
                                {
                                    favorites = if (e.id in favorites) favorites - e.id else favorites + e.id
                                    saveStrengthFavorites(context, favorites)
                                }
                            )
                        }
                    }
                }
                PolishedSection("FIND AN EXERCISE", "") {
                    OutlinedTextField(query, { query = it; showCount = 12 }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search exercises") })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LibraryFilterButton(
                            label = "Favorites",
                            value = if (favoritesOnly) "On" else null,
                            active = favoritesOnly,
                            modifier = Modifier.weight(1f)
                        ) { favoritesOnly = !favoritesOnly; showCount = 12 }
                        LibraryFilterButton(
                            label = "Muscle",
                            value = muscleFilter,
                            active = muscleFilter != null,
                            modifier = Modifier.weight(1f)
                        ) { muscleFilter = next(muscleFilter, muscles); showCount = 12 }
                        LibraryFilterButton(
                            label = "Equipment",
                            value = equipmentFilter,
                            active = equipmentFilter != null,
                            modifier = Modifier.weight(1f)
                        ) { equipmentFilter = next(equipmentFilter, equipment); showCount = 12 }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LibraryFilterButton(
                            label = "Difficulty",
                            value = difficultyFilter,
                            active = difficultyFilter != null,
                            modifier = Modifier.weight(1f)
                        ) { difficultyFilter = next(difficultyFilter, difficulties); showCount = 12 }
                        LibraryFilterButton(
                            label = "Movement",
                            value = mechanicFilter,
                            active = mechanicFilter != null,
                            modifier = Modifier.weight(1f)
                        ) { mechanicFilter = next(mechanicFilter, mechanics); showCount = 12 }
                        val hasFilters = favoritesOnly || muscleFilter != null || equipmentFilter != null || difficultyFilter != null || mechanicFilter != null
                        LibraryFilterButton(
                            label = "Clear",
                            value = null,
                            active = false,
                            enabled = hasFilters,
                            modifier = Modifier.weight(1f)
                        ) {
                            favoritesOnly = false
                            muscleFilter = null
                            equipmentFilter = null
                            difficultyFilter = null
                            mechanicFilter = null
                            showCount = 12
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    filtered.take(showCount).forEach { e ->
                        ExerciseResultRow(
                            e, startedAt > 0L, e.id in favorites,
                            { selected = e; mode = "detail" },
                            { if (workoutExercises.none { it.id == e.id }) workoutExercises.add(e); selected = e; mode = "workout"; writeActiveDraft() },
                            {
                                favorites = if (e.id in favorites) favorites - e.id else favorites + e.id
                                saveStrengthFavorites(context, favorites)
                            }
                        )
                    }
                    if (filtered.isEmpty()) EmptyState("No matches", "Try clearing a filter.")
                    if (filtered.size > showCount) Text("LOAD 12 MORE", color = ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { showCount += 12 }.padding(12.dp))
                }
            }
            "detail" -> selected?.let { e ->
                HeroStrip("MOVEMENT PROFILE", e.name, "${e.group} · ${e.equipment}", ExercisePurple)
                PolishedSection("FORM & EXECUTION", "${e.difficulty} · ${e.mechanic}${if (e.met > 0) " · ${exerciseNumber(e.met)} MET" else ""}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { RepDbImage(e.imageMain ?: e.imageStart, Modifier.weight(1f).height(160.dp)); if (e.imagePeak != null) RepDbImage(e.imagePeak, Modifier.weight(1f).height(160.dp)) }
                    if (e.primaryMuscles.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp)); Text("PRIMARY", color = ExerciseMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp)); ChipRow(e.primaryMuscles.take(5).map(::pretty))
                    }
                    if (e.secondaryMuscles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp)); Text("SECONDARY", color = ExerciseMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp)); ChipRow(e.secondaryMuscles.take(5).map(::pretty))
                    }
                }
                WideActionTile(if (e.id in favorites) "★ FAVORITED" else "☆ ADD TO FAVORITES", "Stored locally on this device", ExercisePurple) {
                    favorites = if (e.id in favorites) favorites - e.id else favorites + e.id
                    saveStrengthFavorites(context, favorites)
                }
                if (e.instructions.isNotEmpty()) PolishedSection("HOW TO", "Movement sequence") { e.instructions.take(6).forEachIndexed { i, s -> InstructionRow(i + 1, s) } }
                if (e.tips.isNotEmpty()) PolishedSection("FORM TIPS", "Keep the movement clean") { e.tips.take(3).forEach { TipRow(it) } }
                WideActionTile(if (startedAt == 0L) "START WITH THIS EXERCISE" else "ADD TO WORKOUT", if (startedAt == 0L) "Begin a new session" else "Add it to the live session", ExerciseNavy) {
                    if (startedAt == 0L) startWorkout(listOf(e)) else { if (workoutExercises.none { it.id == e.id }) workoutExercises.add(e); selected = e; mode = "workout"; writeActiveDraft() }
                }
            }
            "routines" -> {
                HeroStrip("ROUTINES", "Your repeatable training plans", "Start, edit, reorder, duplicate or delete", ExerciseBlue)
                PolishedSection("SAVED ROUTINES", "START launches the routine immediately") {
                    if (routines.isEmpty()) EmptyState("No routines yet", "Create a reusable workout below.")
                    routines.forEachIndexed { i, r ->
                        Column(Modifier.fillMaxWidth().background(ExerciseRowSurface, RoundedCornerShape(16.dp)).padding(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, color = ExerciseNavy, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                    Text("${r.exerciseIds.size} exercises", color = ExerciseMuted, fontSize = 10.sp)
                                }
                                Text("START", color = ExerciseGreen, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable {
                                    startWorkout(r.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } })
                                    workoutName = r.name
                                }.padding(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("EDIT", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable {
                                    editingRoutineIndex = i
                                    routineName = r.name
                                    routineSelection = r.exerciseIds
                                    routineQuery = ""
                                    pendingRoutineDelete = null
                                }.padding(vertical = 8.dp))
                                Text("DUPLICATE", color = ExercisePurple, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable {
                                    val base = r.name + " Copy"
                                    val existing = routines.map { it.name }.toSet()
                                    var candidate = base
                                    var suffix = 2
                                    while (candidate in existing) { candidate = "$base $suffix"; suffix++ }
                                    routines = routines + WorkoutRoutine(candidate, r.exerciseIds.toList())
                                    saveRoutines(context, routines)
                                    feedbackMessage = "Routine duplicated"
                                }.padding(vertical = 8.dp))
                                val confirm = pendingRoutineDelete == i
                                Text(if (confirm) "CONFIRM DELETE" else "DELETE", color = if (SuperhumanAppearance.darkMode) superhumanRed else Color(0xFFAA4444), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable {
                                    if (!confirm) pendingRoutineDelete = i
                                    else {
                                        routines = routines.filterIndexed { idx, _ -> idx != i }
                                        saveRoutines(context, routines)
                                        pendingRoutineDelete = null
                                        if (editingRoutineIndex == i) {
                                            editingRoutineIndex = null
                                            routineName = ""
                                            routineSelection = emptyList()
                                        }
                                        feedbackMessage = "Routine deleted"
                                    }
                                }.padding(vertical = 8.dp))
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
                PolishedSection(if (editingRoutineIndex == null) "CREATE ROUTINE" else "EDIT ROUTINE", "Add/remove movements and drag order with UP/DOWN") {
                    OutlinedTextField(routineName, { routineName = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Routine name") })
                    if (routineSelection.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        Text("EXERCISE ORDER", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        routineSelection.forEachIndexed { index, id ->
                            val exercise = catalog.find { it.id == id }
                            if (exercise != null) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${exercise.name}", color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    if (index > 0) Text("↑", color = ExerciseBlue, fontSize = 16.sp, modifier = Modifier.clickable {
                                        val mutable = routineSelection.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        routineSelection = mutable
                                    }.padding(8.dp))
                                    if (index < routineSelection.lastIndex) Text("↓", color = ExerciseBlue, fontSize = 16.sp, modifier = Modifier.clickable {
                                        val mutable = routineSelection.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        routineSelection = mutable
                                    }.padding(8.dp))
                                    Text("×", color = if (SuperhumanAppearance.darkMode) superhumanRed else Color(0xFFAA4444), fontSize = 16.sp, modifier = Modifier.clickable {
                                        routineSelection = routineSelection.filterIndexed { idx, _ -> idx != index }
                                    }.padding(8.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(routineQuery, { routineQuery = it.take(50) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Find exercise to add") })
                    val routineMatches = catalog.filter { e ->
                        val q = routineQuery.trim()
                        q.isBlank() || e.name.contains(q, true) || e.group.contains(q, true) || e.primaryMuscles.any { it.contains(q, true) }
                    }.take(20)
                    Spacer(Modifier.height(6.dp))
                    routineMatches.forEach { e ->
                        val picked = routineSelection.contains(e.id)
                        Row(Modifier.fillMaxWidth().clickable {
                            routineSelection = if (picked) routineSelection - e.id else routineSelection + e.id
                        }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(25.dp).background(if (picked) ExerciseGreen else ExerciseSelectionOff, CircleShape), contentAlignment = Alignment.Center) { Text(if (picked) "✓" else "+", color = if (picked) Color.White else ExerciseBlue, fontSize = 10.sp) }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, color = ExerciseInk, fontSize = 11.sp)
                                Text("${e.group} · ${e.equipment}", color = ExerciseMuted, fontSize = 9.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    WideActionTile(if (editingRoutineIndex == null) "SAVE ROUTINE" else "SAVE CHANGES", "${routineSelection.size} exercises selected", ExerciseNavy) {
                        if (routineName.isNotBlank() && routineSelection.isNotEmpty()) {
                            val newRoutine = WorkoutRoutine(routineName.trim(), routineSelection)
                            val editIndex = editingRoutineIndex
                            routines = if (editIndex == null) routines + newRoutine else routines.mapIndexed { idx, old -> if (idx == editIndex) newRoutine else old }
                            saveRoutines(context, routines)
                            feedbackMessage = if (editIndex == null) "Routine saved" else "Routine updated"
                            editingRoutineIndex = null
                            routineName = ""
                            routineSelection = emptyList()
                            routineQuery = ""
                        } else feedbackMessage = "Add a routine name and at least one exercise"
                    }
                    if (editingRoutineIndex != null) {
                        Spacer(Modifier.height(7.dp))
                        WideActionTile("CANCEL EDIT", "Leave the saved routine unchanged", ExerciseMuted) {
                            editingRoutineIndex = null
                            routineName = ""
                            routineSelection = emptyList()
                            routineQuery = ""
                        }
                    }
                }
            }
            "workout" -> {
                LiveWorkoutHero(session.size, workoutExercises.distinctBy { it.id }.size, startedAt) { finishWorkout() }
                PolishedSection("SESSION DETAILS", "Name is optional; blank uses an automatic workout name") {
                    val automaticName = inferStrengthWorkoutNameFromExercises(workoutExercises)
                    OutlinedTextField(workoutName, { workoutName = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Workout name · auto: $automaticName") })
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(sessionRpeText, { sessionRpeText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) }, Modifier.weight(.7f), singleLine = true, label = { Text("Session RPE") })
                        OutlinedTextField(workoutNotes, { workoutNotes = it.take(240) }, Modifier.weight(1.3f), singleLine = true, label = { Text("Notes optional") })
                    }
                }
                if (restSeconds > 0) RestTimerTile(
                    restSeconds,
                    {
                        restSeconds = max(0, restSeconds - 15)
                        restEndsAt = if (restSeconds > 0) System.currentTimeMillis() + restSeconds * 1000L else 0L
                        writeActiveDraft()
                    },
                    {
                        restSeconds += 15
                        restEndsAt = System.currentTimeMillis() + restSeconds * 1000L
                        writeActiveDraft()
                    },
                    {
                        restSeconds = 0
                        restEndsAt = 0L
                        writeActiveDraft()
                    }
                )
                if (workoutExercises.isEmpty()) PolishedSection("BUILD YOUR SESSION", "Choose your first movement") { EmptyState("No exercises yet", "Open the library and add a movement."); Spacer(Modifier.height(8.dp)); WideActionTile("ADD EXERCISE", "Browse the RepDB library", ExerciseBlue) { mode = "library" } }
                else selected?.let { e ->
                    PolishedSection("ACTIVE EXERCISE", e.name) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RepDbImage(e.imageMain ?: e.imageStart, Modifier.size(72.dp)); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(e.name, color = ExerciseInk, fontSize = 15.sp, fontWeight = FontWeight.Black); Text("${e.group} · ${e.equipment}", color = ExerciseMuted, fontSize = 11.sp) }; StatusPill(if (supersetTag == null) "SUPERSET" else "LINKED", supersetTag != null) { supersetTag = if (supersetTag == null) "A" else null } }
                        Spacer(Modifier.height(12.dp)); SetTableHeader()
                        val old = recent.filter { it.metadata["exerciseId"] == e.id }
                        val completed = session.filter { it.exercise.id == e.id }
                        completed.forEachIndexed { i, s ->
                            SetRow(
                                i + 1, s, old.firstOrNull { it.timestampEpochMs < startedAt } ?: old.getOrNull(i),
                                {
                                    val duplicate = s.copy(timestamp = System.currentTimeMillis())
                                    addSet(duplicate)
                                    loadText = exerciseNumber(duplicate.loadKg)
                                    repsText = duplicate.reps.toString()
                                    rirText = duplicate.rir?.toString() ?: rirText
                                    rpeText = duplicate.rpe?.toString() ?: ""
                                    setType = duplicate.type
                                },
                                { editLoggedSet(s) },
                                {
                                    if (pendingSetDeleteTimestamp == s.timestamp) {
                                        pendingSetDeleteTimestamp = null
                                        removeLoggedSet(s)
                                    } else pendingSetDeleteTimestamp = s.timestamp
                                },
                                pendingSetDeleteTimestamp == s.timestamp
                            )
                        }
                        completed.lastOrNull()?.let { last ->
                            Spacer(Modifier.height(9.dp))
                            DuplicateLastSetTile(last) {
                                val duplicate = last.copy(timestamp = System.currentTimeMillis())
                                addSet(duplicate)
                            }
                        }
                    }
                    PolishedSection("LOG NEXT SET", "Load and reps first · effort fields are optional") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Warmup", "Work", "Drop", "Failure").forEach { t -> ChoiceChip(t, setType == t) { setType = t } } }
                        Spacer(Modifier.height(9.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedTextField(loadText, { loadText = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), singleLine = true, label = { Text("Load (kg)") }); OutlinedTextField(repsText, { repsText = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text("Reps") }); OutlinedTextField(rirText, { rirText = it.filter(Char::isDigit).take(1) }, Modifier.weight(.8f), singleLine = true, label = { Text("RIR") }) }
                        Spacer(Modifier.height(7.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedTextField(rpeText, { rpeText = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, Modifier.weight(1f), singleLine = true, label = { Text("RPE (optional)") }); OutlinedTextField(restTarget.toString(), { text -> text.toIntOrNull()?.let { restTarget = it.coerceIn(15, 600) } }, Modifier.weight(1f), singleLine = true, label = { Text("Rest (seconds)") }) }
                        Spacer(Modifier.height(10.dp)); WideActionTile("COMPLETE SET", "Save set and start a ${restTarget}s rest timer", ExerciseGreen) { val reps = repsText.toIntOrNull() ?: 0; val load = loadText.toDoubleOrNull() ?: 0.0; if (reps > 0) addSet(NativeWorkoutSet(e, reps, load, System.currentTimeMillis(), setType, rirText.toIntOrNull(), rpeText.toDoubleOrNull(), supersetTag)) else feedbackMessage = "Enter at least 1 rep to save this set" }
                    }
                    WideActionTile("+ ADD EXERCISE", "Keep building this session", ExerciseBlue) { mode = "library" }
                }
            }
            "history" -> {
                HeroStrip("HISTORY", "Completed strength sessions", "Tap a workout to inspect every exercise and set", ExercisePurple)
                PolishedSection("WORKOUT SESSIONS", "Newest first") {
                    if (completedSessions.isEmpty()) EmptyState("No completed workouts yet", "Finish a strength workout to create a session.")
                    completedSessions.take(60).forEach { workout ->
                        SessionHistoryRow(workout) {
                            selectedHistorySessionId = workout.sessionId
                            historyEditName = workout.name
                            historyEditNotes = workout.notes
                            historyEditRpe = workout.sessionRpe?.let(::exerciseNumber) ?: ""
                            pendingWorkoutDelete = null
                            mode = "session_detail"
                        }
                    }
                }
            }
            "session_detail" -> {
                val workout = completedSessions.firstOrNull { it.sessionId == selectedHistorySessionId }
                if (workout == null) {
                    EmptyState("Workout unavailable", "This session may have been removed.")
                } else {
                    HeroStrip("WORKOUT DETAIL", workout.name, "${workout.durationMin} min · ${workout.workingSets} working sets · ${workout.totalVolumeKg.roundToInt()} kg", ExercisePurple)
                    PolishedSection("EDIT WORKOUT", "Update metadata without changing logged sets") {
                        OutlinedTextField(historyEditName, { historyEditName = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Workout name") })
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(historyEditRpe, { historyEditRpe = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) }, Modifier.weight(.7f), singleLine = true, label = { Text("Session RPE") })
                            OutlinedTextField(historyEditNotes, { historyEditNotes = it.take(240) }, Modifier.weight(1.3f), singleLine = true, label = { Text("Notes") })
                        }
                        Spacer(Modifier.height(8.dp))
                        WideActionTile("SAVE WORKOUT DETAILS", "Preserves the existing session and set history", ExerciseBlue) {
                            val row = sessionRows.firstOrNull { it.metadata["sessionId"] == workout.sessionId } ?: sessionRows.firstOrNull { it.timestampEpochMs == workout.endTime }
                            if (row != null) scope.launch {
                                NativeDataHub.deleteValue(row)
                                NativeDataHub.saveValues(listOf(row.copy(metadata = row.metadata + mapOf(
                                    "workoutName" to historyEditName.trim().ifBlank { workout.name },
                                    "notes" to historyEditNotes.trim(),
                                    "sessionRpe" to historyEditRpe.trim()
                                ))))
                                refresh()
                                feedbackMessage = "Workout details updated"
                            }
                        }
                    }
                    PolishedSection("EXERCISES & SETS", "${workout.exerciseCount} exercises · ${workout.totalSets} total sets") {
                        workout.sets.groupBy { it.metadata["exerciseName"] ?: "Exercise" }.forEach { (name, rows) ->
                            Text(name, color = ExerciseInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            rows.forEach { HistoryRow(it) }
                            Spacer(Modifier.height(5.dp))
                        }
                    }
                    val confirmDelete = pendingWorkoutDelete == workout.sessionId
                    WideActionTile(if (confirmDelete) "CONFIRM DELETE WORKOUT" else "DELETE WORKOUT", if (confirmDelete) "Removes this session and its linked sets" else "Tap once more to confirm", if (SuperhumanAppearance.darkMode) superhumanRed else Color(0xFFAA4444)) {
                        if (!confirmDelete) pendingWorkoutDelete = workout.sessionId
                        else {
                            val row = sessionRows.firstOrNull { it.metadata["sessionId"] == workout.sessionId } ?: sessionRows.firstOrNull { it.timestampEpochMs == workout.endTime }
                            scope.launch {
                                NativeDataHub.deleteValues(workout.sets + listOfNotNull(row))
                                pendingWorkoutDelete = null
                                selectedHistorySessionId = null
                                refresh()
                                feedbackMessage = "Workout deleted"
                                mode = "history"
                            }
                        }
                    }
                }
            }
            "progress" -> {
                HeroStrip("PROGRESS", "Strength progression", "Frequency, volume, estimated 1RM, PRs and muscle work", ExerciseGreen)
                PolishedSection("LAST 7 DAYS", "Working sets exclude warm-ups") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("WORK SETS", strengthProgress.weeklyWorkingSets.toString(), "7 days", ExerciseBlue, Modifier.weight(1f))
                        MetricTile("VOLUME", strengthProgress.weeklyVolumeKg.roundToInt().toString(), "kg", ExerciseGreen, Modifier.weight(1f))
                        MetricTile("FREQUENCY", strengthProgress.trainingFrequency.toString(), "sessions", ExercisePurple, Modifier.weight(1f))
                    }
                    if (strengthProgress.exerciseFrequency.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        Text("Exercise frequency · " + strengthProgress.exerciseFrequency.entries.take(4).joinToString(" · ") { "${it.key} ${it.value}×" }, color = ExerciseMuted, fontSize = 9.sp)
                    }
                }
                PolishedSection("PERSONAL RECORDS", "Estimated 1RM uses Epley; it is not a tested 1RM") {
                    if (strengthPrs.isEmpty()) EmptyState("No PR data yet", "Log repeated exercises to establish records.")
                    strengthPrs.take(10).forEach { pr -> StrengthPrRow(pr) }
                }
                PolishedSection("MUSCLE-GROUP VOLUME", "Approximate weekly working sets · primary 1.0, secondary 0.5") {
                    muscleVolume.forEach { volume -> MuscleVolumeRow(volume) }
                    Spacer(Modifier.height(6.dp))
                    Text("RepDB-based training estimates, not exact physiological measurements.", color = ExerciseMuted, fontSize = 9.sp)
                }
                PolishedSection("TRENDS", "Last 12 logged weeks") {
                    TrendSummaryRow("Volume", strengthProgress.volumeTrend.map { it.second })
                    TrendSummaryRow("Best load", strengthProgress.bestLoadTrend.map { it.second })
                    TrendSummaryRow("Estimated 1RM", strengthProgress.estimated1RmTrend.map { it.second })
                    if (strengthProgress.recentProgression.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        strengthProgress.recentProgression.forEach { Text("• $it", color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
            }
            "summary" -> {
                SummaryHero(summaryWorkingSets, summaryVolume, summaryDuration)
                PolishedSection("SESSION METRICS", "Concrete results from this workout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricTile("EXERCISES", summaryExercises.toString(), "completed", ExerciseBlue, Modifier.weight(1f))
                        MetricTile("ALL SETS", summarySets.toString(), "incl. warm-ups", ExercisePurple, Modifier.weight(1f))
                        MetricTile("WORK SETS", summaryWorkingSets.toString(), "training sets", ExerciseGreen, Modifier.weight(1f))
                    }
                }
                PolishedSection("PERSONAL RECORDS", "Detected against strength history before this session") {
                    if (summaryPrs.isEmpty()) Text("No new PRs this session.", color = ExerciseMuted, fontSize = 10.sp)
                    else summaryPrs.forEach { Text("• $it", color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                }
                if (summaryProgression.isNotEmpty()) {
                    PolishedSection("NOTABLE PROGRESSION", "Changes supported by logged metrics") {
                        summaryProgression.forEach { Text("• $it", color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
                WideActionTile("DONE", "Return to training dashboard", ExerciseNavy) { session.clear(); workoutExercises.clear(); selected = null; activeSessionId = ""; workoutName = ""; workoutNotes = ""; sessionRpeText = ""; mode = "home" }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable private fun TrainingHeader(mode: String, onBack: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.superhumanTopButton(onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { Text("←", color = ExerciseBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Column { Text(when (mode) { "workout" -> "Live workout"; "library" -> "Exercises"; "routines" -> "Routines"; "history" -> "History"; "session_detail" -> "Workout detail"; "progress" -> "Progress"; "summary" -> "Workout complete"; else -> "Strength" }, color = ExerciseInk, fontSize = 25.sp, fontWeight = FontWeight.Black); if (mode == "workout") Text("Saved automatically", color = ExerciseMuted, fontSize = 12.sp) } } }
@Composable private fun TrainingHero(lastName: String, sets: Int, volume: Int, activeWorkout: Boolean, onStart: () -> Unit) { Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0A3168), Color(0xFF0D6CB4), Color(0xFF5BA9DB))), RoundedCornerShape(28.dp)).padding(21.dp)) { Text(if (activeWorkout) "WORKOUT IN PROGRESS" else "STRENGTH TRAINING", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.Black); Text(if (activeWorkout) "Continue your workout" else "Ready to train?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Text(if (activeWorkout) "Your active workout is saved on this device" else "Last activity · $lastName", color = Color.White.copy(alpha = .78f), fontSize = 12.sp); Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { GlassMetric("WORKING SETS", sets.toString(), Modifier.weight(1f)); GlassMetric("TRAINING VOLUME", "$volume kg", Modifier.weight(1f)) }; Spacer(Modifier.height(15.dp)); Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Color.White, RoundedCornerShape(16.dp)).clickable { onStart() }.padding(15.dp), contentAlignment = Alignment.Center) { Text(if (activeWorkout) "RESUME WORKOUT" else "START WORKOUT", color = ExerciseHeroButtonText, fontSize = 14.sp, fontWeight = FontWeight.Black) } } }
@Composable private fun GlassMetric(label: String, value: String, modifier: Modifier) { Column(modifier.background(Color.White.copy(alpha = .13f), RoundedCornerShape(14.dp)).padding(10.dp)) { Text(label, color = Color.White.copy(alpha = .62f), fontSize = 10.sp); Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black) } }
@Composable private fun TrainingNavTile(mark: String, title: String, subtitle: String, accent: Color, modifier: Modifier, onClick: () -> Unit) { Column(modifier.heightIn(min = 96.dp).background(ExerciseSurface, RoundedCornerShape(20.dp)).clickable { onClick() }.padding(12.dp)) { Box(Modifier.size(32.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) { Text(mark, color = accent, fontWeight = FontWeight.Black) }; Spacer(Modifier.height(9.dp)); Text(title, color = ExerciseNavy, fontSize = 11.sp, fontWeight = FontWeight.Black); Text(subtitle, color = ExerciseMuted, fontSize = 10.sp) } }
@Composable private fun PolishedSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().background(ExerciseSurface, RoundedCornerShape(23.dp)).border(1.dp, ExerciseCardBorder, RoundedCornerShape(23.dp)).padding(16.dp)) { Text(title, color = ExerciseInk, fontSize = 16.sp, fontWeight = FontWeight.Black); if (subtitle.isNotBlank()) { Text(subtitle, color = ExerciseMuted, fontSize = 11.sp); Spacer(Modifier.height(11.dp)) } else { Spacer(Modifier.height(8.dp)) }; content() } }
@Composable private fun MetricTile(label: String, value: String, detail: String, accent: Color, modifier: Modifier) { Column(modifier.background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .13f else .07f), RoundedCornerShape(16.dp)).padding(11.dp)) { Text(label, color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(value, color = ExerciseNavy, fontSize = 16.sp, fontWeight = FontWeight.Black); Text(detail, color = ExerciseMuted, fontSize = 10.sp) } }
@Composable private fun WideActionTile(title: String, subtitle: String, accent: Color, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(accent.copy(alpha = if (SuperhumanAppearance.darkMode) .14f else .09f), RoundedCornerShape(17.dp)).clickable { onClick() }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(accent, CircleShape)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black); Text(subtitle, color = ExerciseMuted, fontSize = 10.sp) }; Text("→", color = accent, fontSize = 18.sp) } }
@Composable private fun HeroStrip(kicker: String, title: String, subtitle: String, accent: Color) { Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha = .96f), accent.copy(alpha = .72f))), RoundedCornerShape(24.dp)).padding(18.dp)) { Text(kicker, color = Color.White.copy(alpha = .76f), fontSize = 10.sp, fontWeight = FontWeight.Black); Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = .82f), fontSize = 11.sp) } }
@Composable private fun RoutineRow(r: WorkoutRoutine, catalog: List<NativeExercise>, start: (List<NativeExercise>) -> Unit) { Row(Modifier.fillMaxWidth().background(ExerciseQuietSurface, RoundedCornerShape(15.dp)).clickable { start(r.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } }) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Text(r.name, color = ExerciseNavy, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("${r.exerciseIds.size} exercises →", color = ExerciseBlue, fontSize = 10.sp) } }
@Composable private fun ExerciseResultRow(e: NativeExercise, inWorkout: Boolean, favorite: Boolean, onOpen: () -> Unit, onAdd: () -> Unit, onFavorite: () -> Unit) { Row(Modifier.fillMaxWidth().background(ExerciseRowSurface, RoundedCornerShape(17.dp)).clickable { onOpen() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RepDbImage(e.imageMain ?: e.imageStart, Modifier.size(62.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(e.name, color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold); Text("${e.group} · ${e.equipment}", color = ExerciseMuted, fontSize = 11.sp); if (e.primaryMuscles.isNotEmpty()) Text("Primary · ${e.primaryMuscles.take(2).joinToString { pretty(it) }}", color = ExerciseMuted, fontSize = 9.sp) }; Text(if (favorite) "★" else "☆", color = ExercisePurple, fontSize = 20.sp, modifier = Modifier.clickable { onFavorite() }.padding(10.dp)); if (inWorkout) Text("+", color = ExerciseBlue, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "Add ${e.name} to workout" }.clickable { onAdd() }.padding(12.dp)) }; Spacer(Modifier.height(6.dp)) }
@Composable private fun ChipRow(items: List<String>) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items.forEach { Text(it, color = ExerciseBlue, fontSize = 10.sp, modifier = Modifier.background(ExerciseSoft, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) } } }
@Composable private fun InstructionRow(n: Int, text: String) { Row(Modifier.padding(vertical = 5.dp)) { Text("$n", color = ExerciseBlue, fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp)); Text(text, color = ExerciseInk, fontSize = 11.sp, lineHeight = 16.sp) } }
@Composable private fun TipRow(text: String) { Row(Modifier.padding(vertical = 5.dp)) { Text("•", color = ExerciseGreen, modifier = Modifier.width(16.dp)); Text(text, color = ExerciseMuted, fontSize = 11.sp) } }
@Composable private fun EmptyState(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().background(ExerciseQuietSurface, RoundedCornerShape(16.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.Black); Text(subtitle, color = ExerciseMuted, fontSize = 11.sp, textAlign = TextAlign.Center) } }
@Composable private fun ExerciseFeedbackBanner(message: String) { Row(Modifier.fillMaxWidth().background(ExerciseGreen.copy(alpha = if (SuperhumanAppearance.darkMode) .18f else .10f), RoundedCornerShape(14.dp)).border(1.dp, ExerciseGreen.copy(alpha = .28f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Text("✓", color = ExerciseGreen, fontSize = 14.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(9.dp)); Text(message, color = ExerciseInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) } }
@Composable private fun LiveWorkoutHero(sets: Int, exercises: Int, startedAt: Long, onFinish: () -> Unit) { val mins = if (startedAt > 0) ((System.currentTimeMillis() - startedAt) / 60000L).coerceAtLeast(0) else 0; Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF092D63), Color(0xFF154F8F))), RoundedCornerShape(24.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("ACTIVE WORKOUT", color = Color.White.copy(alpha = .72f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("$sets sets · $exercises exercises", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black); Text("${mins}m elapsed · saved automatically", color = Color.White.copy(alpha = .72f), fontSize = 10.sp) }; Box(Modifier.heightIn(min = 48.dp).background(Color.White, RoundedCornerShape(13.dp)).clickable { onFinish() }.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Text("FINISH", color = ExerciseHeroButtonText, fontSize = 11.sp, fontWeight = FontWeight.Black) } } }
@Composable private fun RestTimerTile(seconds: Int, minus: () -> Unit, plus: () -> Unit, skip: () -> Unit) { Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0E7C70), Color(0xFF2EA995))), RoundedCornerShape(19.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text("REST ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("−15", color = Color.White, fontSize = 11.sp, modifier = Modifier.heightIn(min = 48.dp).clickable { minus() }.padding(horizontal = 8.dp, vertical = 15.dp)); Text("+15", color = Color.White, fontSize = 11.sp, modifier = Modifier.heightIn(min = 48.dp).clickable { plus() }.padding(horizontal = 8.dp, vertical = 15.dp)); Text("SKIP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 48.dp).clickable { skip() }.padding(horizontal = 8.dp, vertical = 15.dp)) } }
@Composable private fun StatusPill(label: String, active: Boolean, onClick: () -> Unit) { Text(label, color = if (active) Color.White else ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.heightIn(min = 44.dp).background(if (active) ExerciseGreen else ExerciseSoft, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp)) }
@Composable private fun SetTableHeader() { Row { Text("SET", Modifier.width(34.dp), color = ExerciseMuted, fontSize = 9.sp); Text("PREVIOUS", Modifier.weight(1f), color = ExerciseMuted, fontSize = 9.sp); Text("LOAD", Modifier.width(50.dp), color = ExerciseMuted, fontSize = 9.sp); Text("REPS", Modifier.width(42.dp), color = ExerciseMuted, fontSize = 9.sp); Text("", Modifier.width(54.dp)) } }
@Composable private fun SetRow(i: Int, s: NativeWorkoutSet, old: HealthValue?, onDuplicate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, deletePending: Boolean) { Column(Modifier.fillMaxWidth().background(if (i % 2 == 0) ExerciseRowSurface else Color.Transparent, RoundedCornerShape(10.dp)).padding(vertical = 7.dp, horizontal = 4.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(i.toString(), Modifier.width(30.dp), color = ExerciseInk, fontSize = 11.sp); Text(old?.let { "${it.metadata["loadKg"] ?: "0"} × ${it.metadata["reps"] ?: "—"}" } ?: "—", Modifier.weight(1f), color = ExerciseMuted, fontSize = 10.sp); Text(exerciseNumber(s.loadKg), Modifier.width(50.dp), color = ExerciseInk, fontSize = 11.sp); Text(s.reps.toString(), Modifier.width(42.dp), color = ExerciseInk, fontSize = 11.sp) }; Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(start = 30.dp, top = 3.dp)) { Text("COPY", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { onDuplicate() }.padding(vertical = 7.dp)); Text("EDIT", color = ExercisePurple, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { onEdit() }.padding(vertical = 7.dp)); Text(if (deletePending) "CONFIRM DELETE" else "DELETE", color = if (SuperhumanAppearance.darkMode) superhumanRed else Color(0xFFAA4444), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { onDelete() }.padding(vertical = 7.dp)) } } }
@Composable private fun DuplicateLastSetTile(set: NativeWorkoutSet, onDuplicate: () -> Unit) { Row(Modifier.fillMaxWidth().background(ExerciseSoft, RoundedCornerShape(14.dp)).clickable { onDuplicate() }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("REPEAT LAST SET", color = ExerciseBlue, fontSize = 11.sp, fontWeight = FontWeight.Black); Text("${exerciseNumber(set.loadKg)} kg × ${set.reps}${set.rir?.let { " · RIR $it" } ?: ""}", color = ExerciseMuted, fontSize = 10.sp) }; Text("+1", color = ExerciseBlue, fontSize = 14.sp, fontWeight = FontWeight.Black) } }
@Composable
private fun LibraryFilterButton(
    label: String,
    value: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier
            .height(58.dp)
            .background(
                if (active) ExerciseBlue.copy(alpha = if (SuperhumanAppearance.darkMode) .22f else .12f) else ExerciseSoft,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (enabled) ExerciseInk else ExerciseMuted.copy(alpha = .55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value ?: if (label == "Clear") "Reset" else "All",
            color = if (active) ExerciseBlue else ExerciseMuted,
            fontSize = 8.sp,
            maxLines = 1
        )
    }
}

@Composable private fun ChoiceChip(label: String, active: Boolean, onClick: () -> Unit) { val displayLabel = if (label == "Warmup") "Warm-up" else label; Text(displayLabel, color = if (active) Color.White else ExerciseBlue, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.heightIn(min = 44.dp).background(if (active) ExerciseBlue else ExerciseSoft, RoundedCornerShape(11.dp)).clickable { onClick() }.padding(horizontal = 9.dp, vertical = 12.dp)) }
@Composable private fun HistoryRow(v: HealthValue) { Row(Modifier.fillMaxWidth().background(ExerciseRowSurface, RoundedCornerShape(14.dp)).padding(10.dp)) { Column(Modifier.weight(1f)) { Text(v.metadata["exerciseName"] ?: "Exercise", color = ExerciseInk, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(v.metadata["setType"]?.replace("Warmup", "Warm-up") ?: "Working set", color = ExerciseMuted, fontSize = 10.sp) }; Text("${v.metadata["loadKg"] ?: "0"} kg × ${v.metadata["reps"] ?: "—"}", color = ExerciseNavy, fontSize = 11.sp) }; Spacer(Modifier.height(5.dp)) }
@Composable private fun ProgressTile(name: String, sets: Int, maxLoad: Double) { Column(Modifier.fillMaxWidth().background(ExerciseRowSurface, RoundedCornerShape(16.dp)).padding(11.dp)) { Text(name, color = ExerciseInk, fontSize = 11.sp, fontWeight = FontWeight.Black); Text("$sets working sets · best load ${exerciseNumber(maxLoad)} kg", color = ExerciseMuted, fontSize = 10.sp) }; Spacer(Modifier.height(7.dp)) }

@Composable private fun SessionHistoryRow(session: StrengthWorkoutSession, onOpen: () -> Unit) { val whenText = remember(session.endTime) { java.time.Instant.ofEpochMilli(session.endTime).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM · HH:mm")) }; Row(Modifier.fillMaxWidth().background(ExerciseRowSurface, RoundedCornerShape(16.dp)).clickable { onOpen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(session.name, color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.Black); Text(whenText, color = ExercisePurple, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text("${session.exerciseCount} exercises · ${session.workingSets} working sets · ${session.durationMin} min", color = ExerciseMuted, fontSize = 10.sp); Text("${session.totalVolumeKg.roundToInt()} kg volume", color = ExerciseMuted, fontSize = 10.sp) }; Text("→", color = ExercisePurple, fontSize = 18.sp) }; Spacer(Modifier.height(7.dp)) }
@Composable private fun StrengthPrRow(pr: StrengthExercisePr) { val repPrs = listOf(1, 3, 5, 8, 10, 12).mapNotNull { reps -> pr.bestRepLoads[reps]?.let { reps to it } }.take(3); Column(Modifier.fillMaxWidth().background(ExerciseRowSurface, RoundedCornerShape(14.dp)).padding(11.dp)) { Text(pr.exerciseName, color = ExerciseInk, fontSize = 11.sp, fontWeight = FontWeight.Black); Text("Heaviest ${formatStrengthNumber(pr.heaviestLoadKg)} kg · max ${pr.highestReps} reps", color = ExerciseMuted, fontSize = 9.sp); Text("Estimated 1RM ${formatStrengthNumber(pr.bestEstimated1RmKg)} kg · best session ${pr.bestSessionVolumeKg.roundToInt()} kg", color = ExerciseGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold); if (repPrs.isNotEmpty()) Text("Rep/load PRs · " + repPrs.joinToString(" · ") { "${it.first} reps @ ${formatStrengthNumber(it.second)} kg" }, color = ExerciseMuted, fontSize = 9.sp) }; Spacer(Modifier.height(6.dp)) }
@Composable private fun MuscleVolumeRow(volume: StrengthMuscleVolume) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(volume.muscle, color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text("${formatStrengthNumber(volume.estimatedWorkingSets)} est. sets", color = ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun TrendSummaryRow(label: String, values: List<Double>) { val first = values.firstOrNull(); val last = values.lastOrNull(); val delta = if (first != null && last != null) last - first else 0.0; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text(if (values.isEmpty()) "No trend yet" else "${formatStrengthNumber(last ?: 0.0)} · ${if (delta >= 0) "+" else ""}${formatStrengthNumber(delta)}", color = if (delta >= 0) ExerciseGreen else ExerciseMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun SummaryHero(sets: Int, volume: Double, duration: Int) { Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF103A70), Color(0xFF166E9F), Color(0xFF1D9B85))), RoundedCornerShape(28.dp)).padding(21.dp)) { Text("WORKOUT SAVED", color = Color.White.copy(alpha = .74f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("Workout complete", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(13.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { GlassMetric("WORKING SETS", sets.toString(), Modifier.weight(1f)); GlassMetric("TRAINING VOLUME", "${volume.roundToInt()} kg", Modifier.weight(1f)); GlassMetric("DURATION", "${duration}m", Modifier.weight(1f)) } } }
