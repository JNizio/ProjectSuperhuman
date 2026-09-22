package com.projectsuperhuman.next

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

data class RoutineExerciseTarget(
    val sets: Int = 3,
    val reps: Int = 10
)

data class WorkoutRoutine(
    val name: String,
    val exerciseIds: List<String>,
    val targets: Map<String, RoutineExerciseTarget> = emptyMap(),
    val plannedDurationMin: Int = 60,
    val intensity: String = "Moderate"
) {
    fun targetFor(exerciseId: String): RoutineExerciseTarget =
        targets[exerciseId] ?: RoutineExerciseTarget()
}

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

internal fun loadWorkoutRoutines(context: android.content.Context): List<WorkoutRoutine> {
    val raw = context.getSharedPreferences("superhuman_training", 0).getString("routines", "[]") ?: "[]"
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val exerciseIds = jsonStrings(o.optJSONArray("exerciseIds"))
            val targetsObject = o.optJSONObject("targets")
            val targets = buildMap {
                exerciseIds.forEach { id ->
                    val target = targetsObject?.optJSONObject(id)
                    put(
                        id,
                        RoutineExerciseTarget(
                            sets = target?.optInt("sets", 3)?.coerceIn(1, 12) ?: 3,
                            reps = target?.optInt("reps", 10)?.coerceIn(1, 100) ?: 10
                        )
                    )
                }
            }
            WorkoutRoutine(
                name = o.optString("name").ifBlank { "Routine" },
                exerciseIds = exerciseIds,
                targets = targets,
                plannedDurationMin = o.optInt("plannedDurationMin", 60).coerceIn(10, 240),
                intensity = o.optString("intensity", "Moderate").takeIf { it in listOf("Easy", "Moderate", "Hard", "Very hard") } ?: "Moderate"
            )
        }
    }.getOrElse { emptyList() }
}

internal fun saveWorkoutRoutines(context: android.content.Context, routines: List<WorkoutRoutine>) {
    val a = JSONArray()
    routines.forEach { r ->
        val targets = JSONObject()
        r.exerciseIds.forEach { id ->
            val target = r.targetFor(id)
            targets.put(id, JSONObject().put("sets", target.sets).put("reps", target.reps))
        }
        a.put(
            JSONObject()
                .put("name", r.name)
                .put("exerciseIds", JSONArray(r.exerciseIds))
                .put("targets", targets)
                .put("plannedDurationMin", r.plannedDurationMin)
                .put("intensity", r.intensity)
        )
    }
    context.getSharedPreferences("superhuman_training", 0).edit().putString("routines", a.toString()).apply()
}

internal fun loadNextWorkoutRoutineName(context: android.content.Context): String =
    context.getSharedPreferences("superhuman_training", 0).getString("next_workout_routine", "").orEmpty()

internal fun saveNextWorkoutRoutineName(context: android.content.Context, name: String) {
    context.getSharedPreferences("superhuman_training", 0).edit()
        .putString("next_workout_routine", name)
        .apply()
}

internal fun loadNextWorkoutRoutine(context: android.content.Context): WorkoutRoutine? {
    val name = loadNextWorkoutRoutineName(context)
    if (name.isBlank()) return null
    return loadWorkoutRoutines(context).firstOrNull { it.name == name }
}

private data class WorkoutPresetTemplate(
    val name: String,
    val exerciseIds: List<String>,
    val targets: Map<String, RoutineExerciseTarget>,
    val plannedDurationMin: Int,
    val intensity: String
) {
    fun asRoutine(): WorkoutRoutine = WorkoutRoutine(
        name = name,
        exerciseIds = exerciseIds,
        targets = targets,
        plannedDurationMin = plannedDurationMin,
        intensity = intensity
    )
}

private fun starterWorkoutPresets(catalog: List<NativeExercise>): List<WorkoutPresetTemplate> {
    fun find(vararg candidates: String): NativeExercise? {
        val normalized = candidates.map { it.lowercase() }
        return catalog.minByOrNull { exercise ->
            val name = exercise.name.lowercase()
            when {
                normalized.any { name == it } -> 0
                normalized.any { name.startsWith(it) || it.startsWith(name) } -> 1
                normalized.any { name.contains(it) || it.contains(name) } -> 2
                else -> 100
            }
        }?.takeIf { exercise ->
            val name = exercise.name.lowercase()
            normalized.any { name == it || name.startsWith(it) || it.startsWith(name) || name.contains(it) || it.contains(name) }
        }
    }

    fun template(
        name: String,
        duration: Int,
        intensity: String,
        items: List<Pair<NativeExercise?, RoutineExerciseTarget>>
    ): WorkoutPresetTemplate? {
        val resolved = items.mapNotNull { (exercise, target) -> exercise?.let { it to target } }
            .distinctBy { it.first.id }
        if (resolved.size < 3) return null
        return WorkoutPresetTemplate(
            name = name,
            exerciseIds = resolved.map { it.first.id },
            targets = resolved.associate { it.first.id to it.second },
            plannedDurationMin = duration,
            intensity = intensity
        )
    }

    return listOfNotNull(
        template(
            "Full Body",
            60,
            "Moderate",
            listOf(
                find("Back Squat", "Squat") to RoutineExerciseTarget(3, 8),
                find("Barbell Bench Press", "Bench Press") to RoutineExerciseTarget(3, 8),
                find("Barbell Row", "Bent Over Row") to RoutineExerciseTarget(3, 10),
                find("Romanian Deadlift", "Stiff Leg Deadlift") to RoutineExerciseTarget(3, 10),
                find("Overhead Press", "Shoulder Press") to RoutineExerciseTarget(3, 10)
            )
        ),
        template(
            "Upper Body",
            55,
            "Moderate",
            listOf(
                find("Barbell Bench Press", "Bench Press") to RoutineExerciseTarget(4, 8),
                find("Pull Up", "Pull-Up", "Lat Pulldown") to RoutineExerciseTarget(4, 8),
                find("Overhead Press", "Shoulder Press") to RoutineExerciseTarget(3, 10),
                find("Barbell Row", "Seated Row") to RoutineExerciseTarget(3, 10),
                find("Biceps Curl", "Barbell Curl") to RoutineExerciseTarget(3, 12),
                find("Triceps Extension", "Cable Triceps") to RoutineExerciseTarget(3, 12)
            )
        ),
        template(
            "Lower Body",
            55,
            "Moderate",
            listOf(
                find("Back Squat", "Squat") to RoutineExerciseTarget(4, 8),
                find("Romanian Deadlift", "Stiff Leg Deadlift") to RoutineExerciseTarget(3, 10),
                find("Leg Press") to RoutineExerciseTarget(3, 10),
                find("Leg Curl", "Lying Leg Curl") to RoutineExerciseTarget(3, 12),
                find("Calf Raise", "Standing Calf Raise") to RoutineExerciseTarget(4, 12)
            )
        )
    )
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
    var historySelectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var historyVisibleMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
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
    var routines by remember { mutableStateOf(loadWorkoutRoutines(context)) }
    var nextRoutineName by remember { mutableStateOf(loadNextWorkoutRoutineName(context)) }
    var routineName by remember { mutableStateOf("") }
    var routineSelection by remember { mutableStateOf<List<String>>(emptyList()) }
    var routineTargets by remember { mutableStateOf<Map<String, RoutineExerciseTarget>>(emptyMap()) }
    var routineDurationText by remember { mutableStateOf("60") }
    var routineIntensity by remember { mutableStateOf("Moderate") }
    var routineQuery by remember { mutableStateOf("") }
    var editingRoutineIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRoutineDelete by remember { mutableStateOf<Int?>(null) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var summarySets by remember { mutableIntStateOf(0) }
    var summaryWorkingSets by remember { mutableIntStateOf(0) }
    var summaryExercises by remember { mutableIntStateOf(0) }
    var summaryVolume by remember { mutableDoubleStateOf(0.0) }
    var summaryDuration by remember { mutableIntStateOf(0) }
    var summaryPrs by remember { mutableStateOf<List<String>>(emptyList()) }
    var summaryProgression by remember { mutableStateOf<List<String>>(emptyList()) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var workoutPaused by remember { mutableStateOf(false) }
    var workoutPausedAt by remember { mutableLongStateOf(0L) }
    var workoutPausedTotalMs by remember { mutableLongStateOf(0L) }
    var workoutNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showWorkoutExercisePicker by remember { mutableStateOf(false) }
    var workoutExerciseQuery by remember { mutableStateOf("") }
    var showWorkoutRoutinePicker by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val now = System.currentTimeMillis()
        val lookback = 5L * 365L * 86400000L
        recent = ExerciseData.boundedBetween("exercise_set", now - lookback, now, limit = 5000)
            .sortedByDescending { it.timestampEpochMs }
        sessionRows = ExerciseData.boundedBetween("workout_session", now - lookback, now, limit = 1000)
            .sortedByDescending { it.timestampEpochMs }
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
        val row = HealthValue(
            HealthDomain.EXERCISE, "exercise_set", set.volume, "kg-reps", set.timestamp, "repdb-exercise",
            mapOf(
                "exerciseId" to set.exercise.id, "exerciseName" to set.exercise.name, "group" to set.exercise.group, "equipment" to set.exercise.equipment,
                "reps" to set.reps.toString(), "loadKg" to set.loadKg.toString(), "met" to set.exercise.met.toString(), "setType" to set.type,
                "rir" to (set.rir?.toString() ?: ""), "rpe" to (set.rpe?.toString() ?: ""), "superset" to (set.supersetTag ?: ""),
                "sessionId" to activeSessionId
            )
        )
        NativeDataHub.saveValues(listOf(row))
        recent = (listOf(row) + recent.filterNot {
            it.timestampEpochMs == row.timestampEpochMs &&
                it.metadata["exerciseId"] == row.metadata["exerciseId"]
        }).take(5000)
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
            val persistedDraftRows = recent.filter { it.metadata["sessionId"] == activeSessionId }.sortedBy { it.timestampEpochMs }
            if (workoutExercises.isEmpty() && persistedDraftRows.isNotEmpty()) {
                val ids = persistedDraftRows.mapNotNull { it.metadata["exerciseId"] }.distinct()
                workoutExercises.addAll(ids.mapNotNull { id -> catalog.find { it.id == id } })
            }
            if (session.isEmpty() && persistedDraftRows.isNotEmpty()) {
                session.addAll(
                    persistedDraftRows.mapNotNull { row ->
                        val exerciseId = row.metadata["exerciseId"] ?: return@mapNotNull null
                        val exercise = catalog.find { it.id == exerciseId } ?: return@mapNotNull null
                        NativeWorkoutSet(
                            exercise = exercise,
                            reps = row.metadata["reps"]?.toIntOrNull() ?: return@mapNotNull null,
                            loadKg = row.metadata["loadKg"]?.toDoubleOrNull() ?: 0.0,
                            timestamp = row.timestampEpochMs,
                            type = row.metadata["setType"] ?: "Work",
                            rir = row.metadata["rir"]?.toIntOrNull(),
                            rpe = row.metadata["rpe"]?.toDoubleOrNull(),
                            supersetTag = row.metadata["superset"]?.takeIf { it.isNotBlank() }
                        )
                    }
                )
            }
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
    LaunchedEffect(workoutPaused, startedAt) {
        while (startedAt > 0L) {
            if (!workoutPaused) workoutNow = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun resumeWorkout() {
        if (startedAt <= 0L) return

        if (workoutExercises.isEmpty() || session.isEmpty()) {
            val matchingRows = recent
                .filter { it.metadata["sessionId"] == activeSessionId }
                .sortedBy { it.timestampEpochMs }

            if (workoutExercises.isEmpty()) {
                val ids = matchingRows.mapNotNull { it.metadata["exerciseId"] }.distinct()
                workoutExercises.clear()
                workoutExercises.addAll(ids.mapNotNull { id -> catalog.find { it.id == id } })
            }

            if (session.isEmpty() && matchingRows.isNotEmpty()) {
                session.clear()
                session.addAll(
                    matchingRows.mapNotNull { row ->
                        val exerciseId = row.metadata["exerciseId"] ?: return@mapNotNull null
                        val exercise = catalog.find { it.id == exerciseId } ?: return@mapNotNull null
                        NativeWorkoutSet(
                            exercise = exercise,
                            reps = row.metadata["reps"]?.toIntOrNull() ?: return@mapNotNull null,
                            loadKg = row.metadata["loadKg"]?.toDoubleOrNull() ?: 0.0,
                            timestamp = row.timestampEpochMs,
                            type = row.metadata["setType"] ?: "Work",
                            rir = row.metadata["rir"]?.toIntOrNull(),
                            rpe = row.metadata["rpe"]?.toDoubleOrNull(),
                            supersetTag = row.metadata["superset"]?.takeIf { it.isNotBlank() }
                        )
                    }
                )
            }
        }

        if (selected == null) selected = workoutExercises.firstOrNull()
        writeActiveDraft()
        mode = "workout"
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
        workoutPaused = false
        workoutPausedAt = 0L
        workoutPausedTotalMs = 0L
        showWorkoutExercisePicker = false
        workoutExerciseQuery = ""
        showWorkoutRoutinePicker = false
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
                val topPrs = strengthPrs.take(3)
                val lastSession = completedSessions.firstOrNull()
                val topMuscle = muscleVolume.maxByOrNull { it.estimatedWorkingSets }
                var trendMetric by remember { mutableStateOf("Volume") }

                StrengthSessionPanel(
                    activeWorkout = startedAt > 0L,
                    workingSets = session.count { it.type != "Warmup" },
                    volumeKg = session.filter { it.type != "Warmup" }.sumOf { it.volume }.roundToInt(),
                    lastWorkout = lastSession?.name ?: "No completed workout yet",
                    onAction = { if (startedAt > 0L) resumeWorkout() else startWorkout() },
                    onDelete = {
                        if (startedAt > 0L) {
                            clearActiveWorkoutDraft(context)
                            clearStrengthActiveMeta(context, startedAt)
                        }
                        session.clear()
                        workoutExercises.clear()
                        selected = null
                        startedAt = 0L
                        activeSessionId = ""
                        workoutName = ""
                        workoutNotes = ""
                        sessionRpeText = ""
                        restSeconds = 0
                        restEndsAt = 0L
                    }
                )

                StrengthQuickActions(
                    onRoutines = { mode = "routines" },
                    onHistory = { mode = "history" },
                    onProgress = { mode = "progress" }
                )

                StrengthProgressCard(
                    selectedMetric = trendMetric,
                    onMetricChange = { trendMetric = it },
                    progress = strengthProgress
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StrengthSummaryCard(
                        label = "THIS WEEK",
                        value = strengthProgress.weeklyWorkingSets.toString(),
                        detail = "working sets",
                        accent = ExerciseBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StrengthSummaryCard(
                        label = "SESSIONS",
                        value = strengthProgress.trainingFrequency.toString(),
                        detail = "last 7 days",
                        accent = ExerciseGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (topMuscle != null && topMuscle.estimatedWorkingSets > 0.0) {
                    StrengthMuscleFocusCard(
                        muscle = topMuscle.muscle,
                        estimatedSets = topMuscle.estimatedWorkingSets,
                        onOpenProgress = { mode = "progress" }
                    )
                }

                if (topPrs.isNotEmpty()) {
                    StrengthRecordsCard(prs = topPrs, onOpenProgress = { mode = "progress" })
                }

                lastSession?.let { session ->
                    StrengthRecentWorkoutCard(
                        session = session,
                        onClick = {
                            selectedHistorySessionId = session.sessionId
                            historyEditName = session.name
                            historyEditNotes = session.notes
                            historyEditRpe = session.sessionRpe?.let(::exerciseNumber) ?: ""
                            pendingWorkoutDelete = null
                            mode = "session_detail"
                        }
                    )
                }

                StrengthLibraryEntry(exerciseCount = catalog.size, onClick = { mode = "library" })

                if (routines.isNotEmpty()) {
                    val nextRoutine = routines.firstOrNull { it.name == nextRoutineName } ?: routines.first()
                    StrengthRoutineShortcut(
                        routine = nextRoutine,
                        catalog = catalog,
                        label = if (nextRoutine.name == nextRoutineName) "NEXT WORKOUT" else "ROUTINE",
                        onStart = {
                            startWorkout(it)
                            workoutName = nextRoutine.name
                        }
                    )
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
                val starterPresets = starterWorkoutPresets(catalog)

                if (starterPresets.isNotEmpty()) {
                    Text("STARTER PRESETS", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        starterPresets.take(3).forEach { preset ->
                            val saved = routines.any { it.name == preset.name }
                            Column(
                                Modifier.weight(1f)
                                    .background(ExerciseSurface, RoundedCornerShape(16.dp))
                                    .border(1.dp, ExerciseCardBorder, RoundedCornerShape(16.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    preset.name,
                                    color = ExerciseInk,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    preset.exerciseIds.size.toString() + " exercises · " +
                                        preset.plannedDurationMin + " min",
                                    color = ExerciseMuted,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (saved) "SAVED ✓" else "ADD",
                                    color = if (saved) ExerciseGreen else ExerciseBlue,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.superhumanClickable(enabled = !saved) {
                                        routines = routines + preset.asRoutine()
                                        saveWorkoutRoutines(context, routines)
                                        feedbackMessage = preset.name + " preset added"
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ROUTINES", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text(
                        "+ NEW",
                        color = ExerciseBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.superhumanClickable {
                            editingRoutineIndex = null
                            routineName = ""
                            routineSelection = emptyList()
                            routineTargets = emptyMap()
                            routineDurationText = "60"
                            routineIntensity = "Moderate"
                            routineQuery = ""
                            pendingRoutineDelete = null
                            showRoutineEditor = true
                        }.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                if (routines.isEmpty() && !showRoutineEditor) {
                    RoutineEmptyCard {
                        editingRoutineIndex = null
                        routineName = ""
                        routineSelection = emptyList()
                        routineTargets = emptyMap()
                        routineDurationText = "60"
                        routineIntensity = "Moderate"
                        routineQuery = ""
                        showRoutineEditor = true
                    }
                } else {
                    routines.forEachIndexed { index, routine ->
                        RoutineVisualCard(
                            routine = routine,
                            catalog = catalog,
                            isNext = routine.name == nextRoutineName,
                            onSetNext = {
                                nextRoutineName = if (routine.name == nextRoutineName) "" else routine.name
                                saveNextWorkoutRoutineName(context, nextRoutineName)
                                feedbackMessage = if (nextRoutineName.isBlank()) "Next workout cleared" else "Next workout set"
                            },
                            onStart = {
                                startWorkout(routine.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } })
                                workoutName = routine.name
                            },
                            onEdit = {
                                editingRoutineIndex = index
                                routineName = routine.name
                                routineSelection = routine.exerciseIds
                                routineTargets = routine.exerciseIds.associateWith { routine.targetFor(it) }
                                routineDurationText = routine.plannedDurationMin.toString()
                                routineIntensity = routine.intensity
                                routineQuery = ""
                                pendingRoutineDelete = null
                                showRoutineEditor = true
                            },
                            onDuplicate = {
                                val base = routine.name + " Copy"
                                val existing = routines.map { it.name }.toSet()
                                var candidate = base
                                var suffix = 2
                                while (candidate in existing) {
                                    candidate = "$base $suffix"
                                    suffix++
                                }
                                routines = routines + routine.copy(name = candidate)
                                saveWorkoutRoutines(context, routines)
                                feedbackMessage = "Routine duplicated"
                            },
                            deleteArmed = pendingRoutineDelete == index,
                            onDelete = {
                                if (pendingRoutineDelete != index) {
                                    pendingRoutineDelete = index
                                } else {
                                    if (nextRoutineName == routine.name) {
                                        nextRoutineName = ""
                                        saveNextWorkoutRoutineName(context, "")
                                    }
                                    routines = routines.filterIndexed { idx, _ -> idx != index }
                                    saveWorkoutRoutines(context, routines)
                                    pendingRoutineDelete = null
                                    if (editingRoutineIndex == index) {
                                        editingRoutineIndex = null
                                        routineName = ""
                                        routineSelection = emptyList()
                                        routineTargets = emptyMap()
                                        routineQuery = ""
                                        showRoutineEditor = false
                                    }
                                    feedbackMessage = "Routine deleted"
                                }
                            }
                        )
                    }
                }

                if (showRoutineEditor) {
                    RoutineEditorCard(
                        title = if (editingRoutineIndex == null) "New routine" else "Edit routine",
                        routineName = routineName,
                        onRoutineNameChange = { routineName = it.take(60) },
                        selectedIds = routineSelection,
                        targets = routineTargets,
                        onTargetChange = { id, sets, reps ->
                            routineTargets = routineTargets + (id to RoutineExerciseTarget(sets.coerceIn(1, 12), reps.coerceIn(1, 100)))
                        },
                        durationText = routineDurationText,
                        onDurationChange = { routineDurationText = it.filter(Char::isDigit).take(3) },
                        intensity = routineIntensity,
                        onIntensityChange = { routineIntensity = it },
                        catalog = catalog,
                        query = routineQuery,
                        onQueryChange = { routineQuery = it.take(50) },
                        onToggleExercise = { id ->
                            if (id in routineSelection) {
                                routineSelection = routineSelection - id
                                routineTargets = routineTargets - id
                            } else {
                                routineSelection = routineSelection + id
                                routineTargets = routineTargets + (id to RoutineExerciseTarget())
                            }
                        },
                        onMoveUp = { index ->
                            if (index > 0) {
                                val mutable = routineSelection.toMutableList()
                                val item = mutable.removeAt(index)
                                mutable.add(index - 1, item)
                                routineSelection = mutable
                            }
                        },
                        onMoveDown = { index ->
                            if (index < routineSelection.lastIndex) {
                                val mutable = routineSelection.toMutableList()
                                val item = mutable.removeAt(index)
                                mutable.add(index + 1, item)
                                routineSelection = mutable
                            }
                        },
                        onRemove = { index ->
                            val id = routineSelection.getOrNull(index)
                            routineSelection = routineSelection.filterIndexed { idx, _ -> idx != index }
                            if (id != null) routineTargets = routineTargets - id
                        },
                        onSave = {
                            if (routineName.isNotBlank() && routineSelection.isNotEmpty()) {
                                val duration = routineDurationText.toIntOrNull()?.coerceIn(10, 240) ?: 60
                                val newRoutine = WorkoutRoutine(
                                    name = routineName.trim(),
                                    exerciseIds = routineSelection,
                                    targets = routineSelection.associateWith { routineTargets[it] ?: RoutineExerciseTarget() },
                                    plannedDurationMin = duration,
                                    intensity = routineIntensity
                                )
                                val editIndex = editingRoutineIndex
                                val oldName = editIndex?.let { routines.getOrNull(it)?.name }
                                routines = if (editIndex == null) routines + newRoutine else routines.mapIndexed { idx, old -> if (idx == editIndex) newRoutine else old }
                                saveWorkoutRoutines(context, routines)
                                if (oldName != null && oldName == nextRoutineName) {
                                    nextRoutineName = newRoutine.name
                                    saveNextWorkoutRoutineName(context, newRoutine.name)
                                }
                                feedbackMessage = if (editIndex == null) "Routine saved" else "Routine updated"
                                editingRoutineIndex = null
                                routineName = ""
                                routineSelection = emptyList()
                                routineTargets = emptyMap()
                                routineDurationText = "60"
                                routineIntensity = "Moderate"
                                routineQuery = ""
                                showRoutineEditor = false
                            } else {
                                feedbackMessage = "Add a name and at least one exercise"
                            }
                        },
                        onCancel = {
                            editingRoutineIndex = null
                            routineName = ""
                            routineSelection = emptyList()
                            routineTargets = emptyMap()
                            routineDurationText = "60"
                            routineIntensity = "Moderate"
                            routineQuery = ""
                            showRoutineEditor = false
                        }
                    )
                }
            }
            "workout" -> {
                val effectiveNow = if (workoutPaused && workoutPausedAt > 0L) workoutPausedAt else workoutNow
                val elapsedMs = (effectiveNow - startedAt - workoutPausedTotalMs).coerceAtLeast(0L)
                val elapsedSeconds = elapsedMs / 1000L
                val elapsedText = String.format(java.util.Locale.US, "%02d:%02d:%02d", elapsedSeconds / 3600L, (elapsedSeconds % 3600L) / 60L, elapsedSeconds % 60L)

                StrengthLiveControlBar(
                    elapsed = elapsedText,
                    paused = workoutPaused,
                    setCount = session.size,
                    exerciseCount = workoutExercises.distinctBy { it.id }.size,
                    onPlayPause = {
                        if (workoutPaused) {
                            if (workoutPausedAt > 0L) workoutPausedTotalMs += System.currentTimeMillis() - workoutPausedAt
                            workoutPausedAt = 0L
                            workoutPaused = false
                        } else {
                            workoutPausedAt = System.currentTimeMillis()
                            workoutPaused = true
                        }
                    },
                    onDelete = {
                        if (startedAt > 0L) {
                            clearActiveWorkoutDraft(context)
                            clearStrengthActiveMeta(context, startedAt)
                        }
                        session.clear()
                        workoutExercises.clear()
                        selected = null
                        startedAt = 0L
                        activeSessionId = ""
                        workoutName = ""
                        workoutNotes = ""
                        sessionRpeText = ""
                        restSeconds = 0
                        restEndsAt = 0L
                        workoutPaused = false
                        workoutPausedAt = 0L
                        workoutPausedTotalMs = 0L
                        showWorkoutExercisePicker = false
                        workoutExerciseQuery = ""
                        showWorkoutRoutinePicker = false
                        mode = "home"
                    },
                    onFinish = { finishWorkout() }
                )

                if (restSeconds > 0) {
                    StrengthCompactRestTimer(
                        seconds = restSeconds,
                        onMinus = {
                            restSeconds = max(0, restSeconds - 15)
                            restEndsAt = if (restSeconds > 0) System.currentTimeMillis() + restSeconds * 1000L else 0L
                            writeActiveDraft()
                        },
                        onPlus = {
                            restSeconds += 15
                            restEndsAt = System.currentTimeMillis() + restSeconds * 1000L
                            writeActiveDraft()
                        },
                        onSkip = {
                            restSeconds = 0
                            restEndsAt = 0L
                            writeActiveDraft()
                        }
                    )
                }

                Text("EXERCISES", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)

                if (workoutExercises.isEmpty()) {
                    StrengthWorkoutEmptyState {
                        showWorkoutExercisePicker = true
                        workoutExerciseQuery = ""
                    }
                } else {
                    workoutExercises.distinctBy { it.id }.forEachIndexed { index, exercise ->
                        val completedSets = session.filter { it.exercise.id == exercise.id }
                        StrengthWorkoutExerciseCard(
                            exercise = exercise,
                            setCount = completedSets.size,
                            selected = selected?.id == exercise.id,
                            onSelect = {
                                selected = exercise
                                showWorkoutExercisePicker = false
                            },
                            onRemove = {
                                workoutExercises.removeAll { it.id == exercise.id }
                                if (selected?.id == exercise.id) selected = workoutExercises.firstOrNull()
                                writeActiveDraft()
                            },
                            order = index + 1
                        )
                    }
                }

                StrengthWorkoutAddRow(
                    onAddExercise = {
                        showWorkoutExercisePicker = !showWorkoutExercisePicker
                        showWorkoutRoutinePicker = false
                        workoutExerciseQuery = ""
                    },
                    onAddRoutine = {
                        showWorkoutRoutinePicker = !showWorkoutRoutinePicker
                        showWorkoutExercisePicker = false
                        workoutExerciseQuery = ""
                    }
                )

                if (workoutExercises.isNotEmpty()) {
                    val inferredPresetName = workoutName.trim().ifBlank {
                        inferStrengthWorkoutNameFromExercises(workoutExercises)
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .background(ExerciseBlue.copy(alpha = .10f), RoundedCornerShape(14.dp))
                            .border(1.dp, ExerciseBlue.copy(alpha = .18f), RoundedCornerShape(14.dp))
                            .superhumanClickable {
                                val baseName = inferredPresetName.ifBlank { "Workout" }
                                val existingNames = routines.map { it.name }.toSet()
                                var finalName = baseName
                                var suffix = 2
                                while (finalName in existingNames) {
                                    finalName = "$baseName $suffix"
                                    suffix++
                                }

                                val targets = workoutExercises.distinctBy { it.id }.associate { exercise ->
                                    val completed = session.filter { it.exercise.id == exercise.id && it.type != "Warmup" }
                                    val setCount = completed.size.takeIf { it > 0 } ?: 3
                                    val reps = completed.map { it.reps }.takeIf { it.isNotEmpty() }
                                        ?.average()?.roundToInt()?.coerceIn(1, 100) ?: 10
                                    exercise.id to RoutineExerciseTarget(setCount.coerceIn(1, 12), reps)
                                }

                                val elapsedMinutes = if (startedAt > 0L) {
                                    ((System.currentTimeMillis() - startedAt - workoutPausedTotalMs)
                                        .coerceAtLeast(0L) / 60_000L).toInt()
                                } else 0
                                val plannedDuration = if (elapsedMinutes >= 10) elapsedMinutes.coerceAtMost(240)
                                    else (workoutExercises.size * 10).coerceIn(30, 90)

                                val intensity = when {
                                    sessionRpeText.toDoubleOrNull()?.let { it >= 8.0 } == true -> "Hard"
                                    sessionRpeText.toDoubleOrNull()?.let { it <= 5.0 } == true -> "Easy"
                                    else -> "Moderate"
                                }

                                routines = routines + WorkoutRoutine(
                                    name = finalName,
                                    exerciseIds = workoutExercises.distinctBy { it.id }.map { it.id },
                                    targets = targets,
                                    plannedDurationMin = plannedDuration,
                                    intensity = intensity
                                )
                                saveWorkoutRoutines(context, routines)
                                feedbackMessage = "Preset saved"
                            }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SAVE AS PRESET",
                            color = ExerciseBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (showWorkoutRoutinePicker) {
                    StrengthWorkoutRoutinePicker(
                        routines = routines,
                        catalog = catalog,
                        onAddRoutine = { routine ->
                            routine.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } }.forEach { exercise ->
                                if (workoutExercises.none { it.id == exercise.id }) workoutExercises.add(exercise)
                            }
                            if (selected == null) selected = workoutExercises.firstOrNull()
                            showWorkoutRoutinePicker = false
                            writeActiveDraft()
                        }
                    )
                }

                if (showWorkoutExercisePicker) {
                    StrengthWorkoutExercisePicker(
                        query = workoutExerciseQuery,
                        onQueryChange = { workoutExerciseQuery = it.take(50) },
                        catalog = catalog,
                        selectedIds = workoutExercises.map { it.id }.toSet(),
                        onAdd = { exercise ->
                            if (workoutExercises.none { it.id == exercise.id }) workoutExercises.add(exercise)
                            selected = exercise
                            workoutExerciseQuery = ""
                            showWorkoutExercisePicker = false
                            writeActiveDraft()
                        }
                    )
                }

                selected?.let { exercise ->
                    val completed = session.filter { it.exercise.id == exercise.id }
                    StrengthActiveExercisePanel(
                        exercise = exercise,
                        completed = completed,
                        recent = recent,
                        plannedTarget = routines.firstOrNull { it.name == workoutName }?.targetFor(exercise.id),
                        startedAt = startedAt,
                        loadText = loadText,
                        repsText = repsText,
                        onLoadChange = { loadText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                        onRepsChange = { repsText = it.filter(Char::isDigit).take(3) },
                        onCompleteSet = {
                            val reps = repsText.toIntOrNull() ?: 0
                            val load = loadText.toDoubleOrNull() ?: 0.0
                            if (reps > 0) {
                                addSet(NativeWorkoutSet(exercise, reps, load, System.currentTimeMillis(), "Work", rirText.toIntOrNull(), rpeText.toDoubleOrNull(), supersetTag))
                            } else feedbackMessage = "Enter reps"
                        },
                        onDuplicate = { last ->
                            addSet(last.copy(timestamp = System.currentTimeMillis()))
                            loadText = exerciseNumber(last.loadKg)
                            repsText = last.reps.toString()
                        },
                        onDeleteSet = { set ->
                            if (pendingSetDeleteTimestamp == set.timestamp) {
                                pendingSetDeleteTimestamp = null
                                removeLoggedSet(set)
                            } else pendingSetDeleteTimestamp = set.timestamp
                        },
                        deletePendingTimestamp = pendingSetDeleteTimestamp
                    )
                }
            }
            "history" -> {
                val sessionsByDate = completedSessions.groupBy { session ->
                    java.time.Instant.ofEpochMilli(session.endTime)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                }
                val selectedSessions = sessionsByDate[historySelectedDate].orEmpty()

                StrengthHistoryCalendar(
                    month = historyVisibleMonth,
                    selectedDate = historySelectedDate,
                    workoutDates = sessionsByDate.keys,
                    onPreviousMonth = { historyVisibleMonth = historyVisibleMonth.minusMonths(1) },
                    onNextMonth = { historyVisibleMonth = historyVisibleMonth.plusMonths(1) },
                    onSelectDate = { historySelectedDate = it }
                )

                Text(
                    historySelectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")),
                    color = ExerciseMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (selectedSessions.isEmpty()) {
                    StrengthHistoryEmptyDay()
                } else {
                    selectedSessions.forEach { workout ->
                        StrengthHistoryDayCard(workout) {
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

@Composable
private fun StrengthLiveControlBar(
    elapsed: String,
    paused: Boolean,
    setCount: Int,
    exerciseCount: Int,
    onPlayPause: () -> Unit,
    onDelete: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(22.dp))
            .border(1.dp, ExerciseBlue.copy(alpha = .36f), RoundedCornerShape(22.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(elapsed, color = ExerciseInk, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(setCount.toString() + " sets · " + exerciseCount + " exercises", color = ExerciseMuted, fontSize = 9.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrengthTopIconButton(if (paused) R.drawable.tabler_player_play else R.drawable.tabler_player_pause, ExerciseBlue, onPlayPause)
                StrengthTopIconButton(R.drawable.tabler_trash, superhumanRed, onDelete)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().background(ExerciseBlue.copy(alpha = .14f), RoundedCornerShape(13.dp))
                .superhumanClickable(onClick = onFinish).padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("FINISH WORKOUT", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StrengthTopIconButton(
    iconRes: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(42.dp)
            .background(ExerciseSoft, RoundedCornerShape(13.dp))
            .border(1.dp, tint.copy(alpha = .28f), RoundedCornerShape(13.dp))
            .superhumanClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StrengthCompactRestTimer(seconds: Int, onMinus: () -> Unit, onPlus: () -> Unit, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ExerciseSoft, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("REST", color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(9.dp))
        Text((seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0'), color = ExerciseInk, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text("-15", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onMinus).padding(8.dp))
        Text("+15", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onPlus).padding(8.dp))
        Text("SKIP", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onSkip).padding(8.dp))
    }
}

@Composable
private fun StrengthWorkoutEmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(ExerciseSurface, RoundedCornerShape(18.dp)).border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("+", color = ExerciseBlue, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Add your first exercise", color = ExerciseInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(9.dp))
        Text("ADD EXERCISE", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onAdd).padding(8.dp))
    }
}

@Composable
private fun StrengthWorkoutExerciseCard(
    exercise: NativeExercise,
    setCount: Int,
    selected: Boolean,
    order: Int,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) ExerciseBlue.copy(alpha = .09f) else ExerciseSurface, RoundedCornerShape(17.dp))
            .border(1.dp, if (selected) ExerciseBlue.copy(alpha = .4f) else ExerciseCardBorder, RoundedCornerShape(17.dp))
            .superhumanClickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(order.toString() + ". " + exercise.name, color = ExerciseInk, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(setCount.toString() + " sets", color = if (selected) ExerciseBlue else ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text("×", color = ExerciseMuted, fontSize = 18.sp, modifier = Modifier.superhumanClickable(onClick = onRemove).padding(8.dp))
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun StrengthWorkoutAddRow(
    onAddExercise: () -> Unit,
    onAddRoutine: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StrengthWorkoutActionButton(
            iconRes = R.drawable.tabler_plus,
            label = "Add exercise",
            modifier = Modifier.weight(1f),
            onClick = onAddExercise
        )
        StrengthWorkoutActionButton(
            iconRes = R.drawable.tabler_list_details,
            label = "Routines",
            modifier = Modifier.weight(1f),
            onClick = onAddRoutine
        )
    }
}

@Composable
private fun StrengthWorkoutActionButton(
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .background(ExerciseBlue.copy(alpha = .12f), RoundedCornerShape(16.dp))
            .superhumanClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = ExerciseBlue,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StrengthWorkoutRoutinePicker(
    routines: List<WorkoutRoutine>,
    catalog: List<NativeExercise>,
    onAddRoutine: (WorkoutRoutine) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .padding(13.dp)
    ) {
        Text("ROUTINES", color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        if (routines.isEmpty()) {
            Text("No routines saved", color = ExerciseMuted, fontSize = 9.sp)
        } else {
            routines.forEach { routine ->
                val preview = routine.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } }.take(3)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .superhumanClickable { onAddRoutine(routine) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        preview.forEach { exercise ->
                            RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.size(36.dp))
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(routine.name, color = ExerciseInk, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(routine.exerciseIds.size.toString() + " exercises", color = ExerciseMuted, fontSize = 8.sp)
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.tabler_plus),
                        contentDescription = null,
                        tint = ExerciseBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StrengthWorkoutExercisePicker(
    query: String,
    onQueryChange: (String) -> Unit,
    catalog: List<NativeExercise>,
    selectedIds: Set<String>,
    onAdd: (NativeExercise) -> Unit
) {
    val common = catalog.filter { e ->
        listOf("bench", "squat", "deadlift", "pull up", "row", "shoulder press", "overhead press").any { term -> e.name.contains(term, true) }
    }.distinctBy { it.id }.take(6)
    val matches = if (query.isBlank()) common else catalog.filter { e ->
        e.name.contains(query, true) || e.group.contains(query, true) || e.equipment.contains(query, true) || e.primaryMuscles.any { it.contains(query, true) }
    }.take(8)

    Column(
        Modifier.fillMaxWidth().background(ExerciseSurface, RoundedCornerShape(18.dp)).border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp)).padding(13.dp)
    ) {
        OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search exercises") })
        Spacer(Modifier.height(9.dp))
        if (query.isBlank()) Text("COMMON", color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        matches.forEach { exercise ->
            val added = exercise.id in selectedIds
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.size(46.dp))
                Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                    Text(exercise.name, color = ExerciseInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(exercise.group, color = ExerciseMuted, fontSize = 8.sp, maxLines = 1)
                }
                Box(
                    Modifier.size(30.dp).background(if (added) ExerciseGreen.copy(alpha = .18f) else ExerciseBlue.copy(alpha = .14f), CircleShape)
                        .superhumanClickable { if (!added) onAdd(exercise) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (added) "✓" else "+", color = if (added) ExerciseGreen else ExerciseBlue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun StrengthActiveExercisePanel(
    exercise: NativeExercise,
    completed: List<NativeWorkoutSet>,
    recent: List<HealthValue>,
    plannedTarget: RoutineExerciseTarget?,
    startedAt: Long,
    loadText: String,
    repsText: String,
    onLoadChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onCompleteSet: () -> Unit,
    onDuplicate: (NativeWorkoutSet) -> Unit,
    onDeleteSet: (NativeWorkoutSet) -> Unit,
    deletePendingTimestamp: Long?
) {
    Column(
        Modifier.fillMaxWidth().background(ExerciseSurface, RoundedCornerShape(20.dp)).border(1.dp, ExerciseCardBorder, RoundedCornerShape(20.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.size(62.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(exercise.name, color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(exercise.group + " · " + exercise.equipment, color = ExerciseMuted, fontSize = 9.sp)
                plannedTarget?.let {
                    Text(
                        "Plan: " + it.sets + " × " + it.reps,
                        color = ExerciseBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (completed.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            completed.forEachIndexed { index, set ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((index + 1).toString(), color = ExerciseMuted, fontSize = 9.sp, modifier = Modifier.width(22.dp))
                    Text(exerciseNumber(set.loadKg) + " kg", color = ExerciseInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(set.reps.toString() + " reps", color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text(if (deletePendingTimestamp == set.timestamp) "CONFIRM" else "×", color = if (deletePendingTimestamp == set.timestamp) superhumanRed else ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.superhumanClickable { onDeleteSet(set) }.padding(6.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(loadText, onLoadChange, Modifier.weight(1f), singleLine = true, label = { Text("kg") })
            OutlinedTextField(repsText, onRepsChange, Modifier.weight(1f), singleLine = true, label = { Text("Reps") })
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.weight(1f).background(ExerciseGreen.copy(alpha = .15f), RoundedCornerShape(13.dp)).superhumanClickable(onClick = onCompleteSet).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("SAVE SET", color = ExerciseGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            completed.lastOrNull()?.let { last ->
                Box(
                    Modifier.background(ExerciseBlue.copy(alpha = .12f), RoundedCornerShape(13.dp)).superhumanClickable { onDuplicate(last) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("REPEAT", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable private fun TrainingHeader(mode: String, onBack: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.superhumanTopButton(onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) { Text("←", color = ExerciseBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Column { Text(when (mode) { "workout" -> "Live workout"; "library" -> "Exercises"; "routines" -> "Routines"; "history" -> "History"; "session_detail" -> "Workout detail"; "progress" -> "Progress"; "summary" -> "Workout complete"; else -> "Strength" }, color = ExerciseInk, fontSize = 25.sp, fontWeight = FontWeight.Black); if (mode == "workout") Text("Saved automatically", color = ExerciseMuted, fontSize = 12.sp) } } }
@Composable
private fun StrengthSessionPanel(
    activeWorkout: Boolean,
    workingSets: Int,
    volumeKg: Int,
    lastWorkout: String,
    onAction: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(ExerciseSurface, shape)
            .border(
                1.dp,
                if (activeWorkout) ExerciseBlue.copy(alpha = .42f) else ExerciseCardBorder,
                shape
            )
            .padding(17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    if (activeWorkout) "WORKOUT IN PROGRESS" else "START STRENGTH WORKOUT",
                    color = ExerciseInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (activeWorkout) "Saved on this device"
                    else "Last: $lastWorkout",
                    color = ExerciseMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 1
                )
            }

            if (activeWorkout) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrengthTopIconButton(
                        iconRes = R.drawable.tabler_player_play,
                        tint = ExerciseBlue,
                        onClick = onAction
                    )
                    StrengthTopIconButton(
                        iconRes = R.drawable.tabler_trash,
                        tint = superhumanRed,
                        onClick = onDelete
                    )
                }
            } else {
                StrengthTopIconButton(
                    iconRes = R.drawable.tabler_plus,
                    tint = ExerciseBlue,
                    onClick = onAction
                )
            }
        }

        if (activeWorkout) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ExerciseSoft, RoundedCornerShape(15.dp))
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StrengthInlineMetric(workingSets.toString(), "WORK SETS")
                Box(Modifier.width(1.dp).height(28.dp).background(ExerciseCardBorder))
                StrengthInlineMetric("$volumeKg kg", "VOLUME")
            }
        }
    }
}

@Composable
private fun StrengthQuickActions(
    onRoutines: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StrengthQuickAction("Routines", ExerciseBlue, Modifier.weight(1f), onRoutines)
        StrengthQuickDivider()
        StrengthQuickAction("History", ExercisePurple, Modifier.weight(1f), onHistory)
        StrengthQuickDivider()
        StrengthQuickAction("Progress", ExerciseGreen, Modifier.weight(1f), onProgress)
    }
}

@Composable
private fun StrengthQuickAction(
    title: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .superhumanClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(7.dp).background(accent, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(
            title,
            color = ExerciseInk,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StrengthQuickDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(ExerciseCardBorder)
    )
}

@Composable
private fun StrengthMetricStrip(
    workingSets: Int,
    volumeKg: Int,
    sessions: Int
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 13.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StrengthInlineMetric(workingSets.toString(), "WORKING SETS")
        Box(Modifier.width(1.dp).height(34.dp).background(ExerciseCardBorder))
        StrengthInlineMetric(volumeKg.toString(), "VOLUME KG")
        Box(Modifier.width(1.dp).height(34.dp).background(ExerciseCardBorder))
        StrengthInlineMetric(sessions.toString(), "SESSIONS")
    }
}

@Composable
private fun StrengthInlineMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = ExerciseInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            label,
            color = ExerciseMuted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StrengthLibraryEntry(
    exerciseCount: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .superhumanClickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(ExerciseBlue.copy(alpha = .14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("DB", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                "Exercise library",
                color = ExerciseInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "$exerciseCount exercises · photos, instructions and muscle data",
                color = ExerciseMuted,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        Text("→", color = ExerciseBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StrengthProgressCard(
    selectedMetric: String,
    onMetricChange: (String) -> Unit,
    progress: StrengthProgressSnapshot
) {
    val options = listOf("Volume", "Estimated 1RM", "Best load")
    val values = when (selectedMetric) {
        "Estimated 1RM" -> progress.estimated1RmTrend.map { it.second }
        "Best load" -> progress.bestLoadTrend.map { it.second }
        else -> progress.volumeTrend.map { it.second }
    }
    val current = values.lastOrNull() ?: 0.0
    val previous = values.dropLast(1).lastOrNull() ?: 0.0
    val change = if (previous > 0.0) ((current - previous) / previous) * 100.0 else 0.0

    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(22.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR STRENGTH", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(3.dp))
                Text(selectedMetric, color = ExerciseInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Text("12W", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { option ->
                val active = option == selectedMetric
                Text(
                    option,
                    color = if (active) ExerciseBlue else ExerciseMuted,
                    fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                    modifier = Modifier
                        .background(if (active) ExerciseBlue.copy(alpha = .14f) else ExerciseSoft, RoundedCornerShape(12.dp))
                        .superhumanClickable { onMetricChange(option) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        StrengthTrendChart(values, ExerciseGreen, Modifier.fillMaxWidth().height(132.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StrengthTrendStat("CURRENT", if (current > 0) formatStrengthNumber(current) + " kg" else "—")
            StrengthTrendStat("PREVIOUS", if (previous > 0) formatStrengthNumber(previous) + " kg" else "—")
            StrengthTrendStat(
                "CHANGE",
                if (previous > 0.0) (if (change >= 0) "+" else "") + change.roundToInt() + "%" else "—",
                if (previous > 0.0 && change < 0) Color(0xFFD06B79) else ExerciseGreen
            )
        }
    }
}

@Composable
private fun StrengthTrendChart(values: List<Double>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 0.0
        val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        repeat(3) { index ->
            val y = size.height * (index + 1) / 4f
            drawLine(
                color = Color.White.copy(alpha = if (SuperhumanAppearance.darkMode) .05f else .08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        val points = values.mapIndexed { index, value ->
            Offset(
                stepX * index,
                size.height - (((value - minValue) / range).toFloat() * size.height * .82f) - size.height * .08f
            )
        }
        for (index in 0 until points.lastIndex) {
            drawLine(
                color = accent,
                start = points[index],
                end = points[index + 1],
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        points.forEach { point -> drawCircle(accent, 4.dp.toPx(), point) }
    }
}

@Composable
private fun StrengthTrendStat(label: String, value: String, valueColor: Color = ExerciseInk) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = ExerciseMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StrengthSummaryCard(label: String, value: String, detail: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(label, color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(detail, color = ExerciseMuted, fontSize = 9.sp)
    }
}

@Composable
private fun StrengthMuscleFocusCard(muscle: String, estimatedSets: Double, onOpenProgress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .superhumanClickable(onClick = onOpenProgress)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(ExerciseGreen.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            Text("M", color = ExerciseGreen, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Most trained this week", color = ExerciseInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(muscle + " · " + formatStrengthNumber(estimatedSets) + " estimated sets", color = ExerciseMuted, fontSize = 9.sp)
        }
        Text("→", color = ExerciseGreen, fontSize = 18.sp)
    }
}

@Composable
private fun StrengthRecordsCard(prs: List<StrengthExercisePr>, onOpenProgress: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(20.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(20.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECORDS", color = ExerciseInk, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("VIEW  ›", color = ExercisePurple, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onOpenProgress))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            prs.take(3).forEach { pr ->
                Column(Modifier.weight(1f).background(ExerciseSoft, RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Text(pr.exerciseName, color = ExerciseMuted, fontSize = 8.sp, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Text(formatStrengthNumber(pr.heaviestLoadKg) + " kg", color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text("e1RM " + formatStrengthNumber(pr.bestEstimated1RmKg), color = ExercisePurple, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StrengthRecentWorkoutCard(session: StrengthWorkoutSession, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .superhumanClickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("RECENT WORKOUT", color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(session.name, color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(
                session.workingSets.toString() + " sets · " + session.totalVolumeKg.roundToInt() + " kg · " + session.durationMin + " min",
                color = ExerciseMuted,
                fontSize = 9.sp
            )
        }
        Text("→", color = ExerciseBlue, fontSize = 18.sp)
    }
}

@Composable
private fun StrengthRoutineShortcut(
    routine: WorkoutRoutine,
    catalog: List<NativeExercise>,
    label: String = "ROUTINE",
    onStart: (List<NativeExercise>) -> Unit
) {
    val plannedSets = routine.exerciseIds.sumOf { routine.targetFor(it).sets }
    Row(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .superhumanClickable { onStart(routine.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } }) }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(routine.name, color = ExerciseInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                routine.exerciseIds.size.toString() + " exercises · " +
                    plannedSets + " sets · " + routine.plannedDurationMin + " min · " + routine.intensity,
                color = ExerciseMuted,
                fontSize = 9.sp
            )
        }
        Text("START  →", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RoutineVisualCard(
    routine: WorkoutRoutine,
    catalog: List<NativeExercise>,
    isNext: Boolean,
    onSetNext: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    deleteArmed: Boolean,
    onDelete: () -> Unit
) {
    val exercises = routine.exerciseIds.mapNotNull { id -> catalog.find { it.id == id } }
    val plannedSets = routine.exerciseIds.sumOf { routine.targetFor(it).sets }
    val repValues = routine.exerciseIds.map { routine.targetFor(it).reps }
    val repSummary = when {
        repValues.isEmpty() -> ""
        repValues.minOrNull() == repValues.maxOrNull() -> repValues.first().toString() + " reps"
        else -> repValues.minOrNull().toString() + "–" + repValues.maxOrNull().toString() + " reps"
    }
    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(20.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, color = ExerciseInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    routine.exerciseIds.size.toString() + " exercises · " +
                        plannedSets + " sets · " + repSummary + " · " +
                        routine.plannedDurationMin + " min · " + routine.intensity,
                    color = ExerciseMuted,
                    fontSize = 9.sp
                )
            }
            Box(
                Modifier.background(ExerciseGreen.copy(alpha = .14f), RoundedCornerShape(12.dp))
                    .superhumanClickable(onClick = onStart)
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text("START", color = ExerciseGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
        if (exercises.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                exercises.take(4).forEach { exercise ->
                    Column(Modifier.weight(1f)) {
                        RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.fillMaxWidth().height(62.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(exercise.name, color = ExerciseInk, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
            if (exercises.size > 4) {
                Spacer(Modifier.height(6.dp))
                Text("+" + (exercises.size - 4) + " more", color = ExerciseMuted, fontSize = 8.sp)
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isNext) "NEXT ✓" else "SET NEXT",
                color = if (isNext) ExerciseGreen else ExerciseMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.superhumanClickable(onClick = onSetNext)
            )
            Text("EDIT", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onEdit))
            Text("DUPLICATE", color = ExercisePurple, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onDuplicate))
            Text(
                if (deleteArmed) "CONFIRM DELETE" else "DELETE",
                color = if (SuperhumanAppearance.darkMode) superhumanRed else Color(0xFFAA4444),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.superhumanClickable(onClick = onDelete)
            )
        }
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun RoutineEmptyCard(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(20.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(46.dp).background(ExerciseBlue.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            Text("+", color = ExerciseBlue, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(9.dp))
        Text("No routines yet", color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.background(ExerciseBlue.copy(alpha = .14f), RoundedCornerShape(12.dp))
                .superhumanClickable(onClick = onCreate)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text("CREATE ROUTINE", color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RoutineEditorCard(
    title: String,
    routineName: String,
    onRoutineNameChange: (String) -> Unit,
    selectedIds: List<String>,
    targets: Map<String, RoutineExerciseTarget>,
    onTargetChange: (String, Int, Int) -> Unit,
    durationText: String,
    onDurationChange: (String) -> Unit,
    intensity: String,
    onIntensityChange: (String) -> Unit,
    catalog: List<NativeExercise>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleExercise: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val commonNames = listOf(
        "Barbell Bench Press",
        "Back Squat",
        "Deadlift",
        "Pull Up",
        "Overhead Press",
        "Barbell Row"
    )
    val commonExercises = commonNames.mapNotNull { target ->
        catalog.minByOrNull { exercise ->
            if (exercise.name.equals(target, true)) 0
            else if (exercise.name.contains(target, true) || target.contains(exercise.name, true)) 1
            else 10
        }?.takeIf { exercise ->
            exercise.name.equals(target, true) || exercise.name.contains(target, true) || target.contains(exercise.name, true)
        }
    }.distinctBy { it.id }.take(6)

    val matches = if (query.isBlank()) emptyList() else catalog.filter { exercise ->
        exercise.name.contains(query, true) ||
            exercise.group.contains(query, true) ||
            exercise.equipment.contains(query, true) ||
            exercise.primaryMuscles.any { it.contains(query, true) }
    }.take(8)

    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(20.dp))
            .border(1.dp, ExerciseBlue.copy(alpha = .28f), RoundedCornerShape(20.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ExerciseInk, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("CANCEL", color = ExerciseMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.superhumanClickable(onClick = onCancel))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = routineName,
            onValueChange = onRoutineNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Routine name") }
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = durationText,
                onValueChange = onDurationChange,
                modifier = Modifier.weight(.8f),
                singleLine = true,
                label = { Text("Minutes") }
            )
            Column(Modifier.weight(1.2f)) {
                Text("INTENSITY", color = ExerciseMuted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Easy", "Moderate", "Hard").forEach { option ->
                        val active = intensity == option
                        Text(
                            option,
                            color = if (active) ExerciseBlue else ExerciseMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(if (active) ExerciseBlue.copy(alpha = .14f) else ExerciseQuietSurface, RoundedCornerShape(10.dp))
                                .superhumanClickable { onIntensityChange(option) }
                                .padding(horizontal = 7.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        if (selectedIds.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("SELECTED", color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            selectedIds.forEachIndexed { index, id ->
                val exercise = catalog.find { it.id == id } ?: return@forEachIndexed
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        val target = targets[id] ?: RoutineExerciseTarget()
                        Text(exercise.name, color = ExerciseInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                target.sets.toString() + " sets",
                                color = ExerciseBlue,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.superhumanClickable {
                                    onTargetChange(id, if (target.sets >= 8) 1 else target.sets + 1, target.reps)
                                }.padding(vertical = 4.dp)
                            )
                            Text("·", color = ExerciseMuted, fontSize = 8.sp)
                            Text(
                                target.reps.toString() + " reps",
                                color = ExerciseGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.superhumanClickable {
                                    val next = when {
                                        target.reps >= 20 -> 5
                                        target.reps >= 12 -> target.reps + 2
                                        else -> target.reps + 1
                                    }
                                    onTargetChange(id, target.sets, next)
                                }.padding(vertical = 4.dp)
                            )
                        }
                    }
                    if (index > 0) Text("↑", color = ExerciseBlue, fontSize = 15.sp, modifier = Modifier.superhumanClickable { onMoveUp(index) }.padding(6.dp))
                    if (index < selectedIds.lastIndex) Text("↓", color = ExerciseBlue, fontSize = 15.sp, modifier = Modifier.superhumanClickable { onMoveDown(index) }.padding(6.dp))
                    Text("×", color = superhumanRed, fontSize = 16.sp, modifier = Modifier.superhumanClickable { onRemove(index) }.padding(6.dp))
                }
            }
        }

        if (commonExercises.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("COMMON", color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                commonExercises.take(3).forEach { exercise ->
                    val picked = exercise.id in selectedIds
                    Column(
                        Modifier.weight(1f)
                            .background(ExerciseRowSurface, RoundedCornerShape(14.dp))
                            .superhumanClickable { onToggleExercise(exercise.id) }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.fillMaxWidth().height(58.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(exercise.name, color = ExerciseInk, fontSize = 8.sp, maxLines = 1, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(24.dp).background(if (picked) ExerciseGreen else ExerciseSelectionOff, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (picked) "✓" else "+", color = if (picked) Color.White else ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            if (commonExercises.size > 3) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    commonExercises.drop(3).take(3).forEach { exercise ->
                        val picked = exercise.id in selectedIds
                        Column(
                            Modifier.weight(1f)
                                .background(ExerciseRowSurface, RoundedCornerShape(14.dp))
                                .superhumanClickable { onToggleExercise(exercise.id) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.fillMaxWidth().height(58.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(exercise.name, color = ExerciseInk, fontSize = 8.sp, maxLines = 1, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.size(24.dp).background(if (picked) ExerciseGreen else ExerciseSelectionOff, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (picked) "✓" else "+", color = if (picked) Color.White else ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search exercises") }
        )

        if (query.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            matches.forEach { exercise ->
                val picked = exercise.id in selectedIds
                Row(
                    Modifier.fillMaxWidth().superhumanClickable { onToggleExercise(exercise.id) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RepDbImage(exercise.imageMain ?: exercise.imageStart, Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                        Text(exercise.name, color = ExerciseInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(exercise.group + " · " + exercise.equipment, color = ExerciseMuted, fontSize = 8.sp, maxLines = 1)
                    }
                    Box(Modifier.size(28.dp).background(if (picked) ExerciseGreen else ExerciseSelectionOff, CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (picked) "✓" else "+", color = if (picked) Color.White else ExerciseBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            if (matches.isEmpty()) Text("No matches", color = ExerciseMuted, fontSize = 9.sp, modifier = Modifier.padding(vertical = 9.dp))
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(ExerciseBlue.copy(alpha = .16f), RoundedCornerShape(14.dp))
                .superhumanClickable(onClick = onSave)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("SAVE ROUTINE", color = ExerciseBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StrengthHistoryCalendar(
    month: java.time.YearMonth,
    selectedDate: java.time.LocalDate,
    workoutDates: Set<java.time.LocalDate>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit
) {
    val first = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val leading = (first.dayOfWeek.value - 1).coerceAtLeast(0)
    val totalCells = leading + daysInMonth
    val rows = (totalCells + 6) / 7
    val monthTitle = month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))

    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(22.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(22.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = ExerciseBlue, fontSize = 24.sp, modifier = Modifier.superhumanClickable(onClick = onPreviousMonth).padding(6.dp))
            Text(monthTitle, color = ExerciseInk, fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("›", color = ExerciseBlue, fontSize = 24.sp, modifier = Modifier.superhumanClickable(onClick = onNextMonth).padding(6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M","T","W","T","F","S","S").forEach { label ->
                Text(label, color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cell = row * 7 + column
                    val dayNumber = cell - leading + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        val selected = date == selectedDate
                        val hasWorkout = date in workoutDates
                        Column(
                            Modifier.weight(1f).height(48.dp).superhumanClickable { onSelectDate(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier.size(32.dp).background(if (selected) ExerciseBlue.copy(alpha = .18f) else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(dayNumber.toString(), color = if (selected) ExerciseBlue else ExerciseInk, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
                            }
                            if (hasWorkout) Box(Modifier.padding(top = 2.dp).size(4.dp).background(ExerciseGreen, CircleShape)) else Spacer(Modifier.height(6.dp))
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthHistoryEmptyDay() {
    Row(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(18.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(ExerciseSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text("—", color = ExerciseMuted, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Text("No strength workout", color = ExerciseMuted, fontSize = 10.sp)
    }
}

@Composable
private fun StrengthHistoryDayCard(session: StrengthWorkoutSession, onOpen: () -> Unit) {
    val exercises = session.sets.mapNotNull { it.metadata["exerciseName"] }.distinct().take(4)
    Column(
        Modifier.fillMaxWidth()
            .background(ExerciseSurface, RoundedCornerShape(20.dp))
            .border(1.dp, ExerciseCardBorder, RoundedCornerShape(20.dp))
            .superhumanClickable(onClick = onOpen)
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.name, color = ExerciseInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(session.durationMin.toString() + " min", color = ExerciseMuted, fontSize = 9.sp)
            }
            Text("→", color = ExercisePurple, fontSize = 19.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().background(ExerciseSoft, RoundedCornerShape(15.dp)).padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StrengthInlineMetric(session.workingSets.toString(), "SETS")
            Box(Modifier.width(1.dp).height(28.dp).background(ExerciseCardBorder))
            StrengthInlineMetric(session.totalVolumeKg.roundToInt().toString(), "VOLUME KG")
            Box(Modifier.width(1.dp).height(28.dp).background(ExerciseCardBorder))
            StrengthInlineMetric(session.exerciseCount.toString(), "EXERCISES")
        }
        if (exercises.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exercises.forEach { name ->
                    Text(name, color = ExerciseInk, fontSize = 8.sp, maxLines = 1, modifier = Modifier.background(ExerciseRowSurface, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(9.dp))
}

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

@Composable
private fun GlassMetric(
    label: String,
    value: String,
    modifier: Modifier
) {
    Column(
        modifier
            .background(Color.White.copy(alpha = .13f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = .62f),
            fontSize = 10.sp
        )
        Text(
            value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable private fun SummaryHero(sets: Int, volume: Double, duration: Int) { Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF103A70), Color(0xFF166E9F), Color(0xFF1D9B85))), RoundedCornerShape(28.dp)).padding(21.dp)) { Text("WORKOUT SAVED", color = Color.White.copy(alpha = .74f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("Workout complete", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(13.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { GlassMetric("WORKING SETS", sets.toString(), Modifier.weight(1f)); GlassMetric("TRAINING VOLUME", "${volume.roundToInt()} kg", Modifier.weight(1f)); GlassMetric("DURATION", "${duration}m", Modifier.weight(1f)) } } }
