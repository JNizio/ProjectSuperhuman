package com.projectsuperhuman.next

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ExerciseNavy = Color(0xFF082D66)
private val ExerciseBlue = Color(0xFF0D6CB4)
private val ExerciseInk = Color(0xFF0B1F35)
private val ExerciseMuted = Color(0xFF64748B)
private val ExerciseOrange = Color(0xFFD97706)
private val ExerciseGreen = Color(0xFF168A78)
private val ExerciseBg = Color(0xFFF6F9FC)

data class NativeExercise(val id: String, val name: String, val group: String, val equipment: String)

data class NativeWorkoutSet(
    val exercise: NativeExercise,
    val reps: Int,
    val loadKg: Double,
    val timestamp: Long
) {
    val volume: Double get() = reps * loadKg
}

private val NativeExerciseCatalog = listOf(
    NativeExercise("push_up", "Push-up", "Chest", "Bodyweight"),
    NativeExercise("incline_push_up", "Incline push-up", "Chest", "Bodyweight"),
    NativeExercise("dip", "Dip", "Chest", "Bodyweight"),
    NativeExercise("bench_press", "Bench press", "Chest", "Barbell"),
    NativeExercise("db_press", "Dumbbell press", "Chest", "Dumbbells"),
    NativeExercise("pull_up", "Pull-up", "Back", "Pull-up bar"),
    NativeExercise("chin_up", "Chin-up", "Back", "Pull-up bar"),
    NativeExercise("row", "Bent-over row", "Back", "Barbell"),
    NativeExercise("cable_row", "Seated cable row", "Back", "Cable"),
    NativeExercise("lat_pulldown", "Lat pulldown", "Back", "Machine"),
    NativeExercise("ohp", "Overhead press", "Shoulders", "Barbell"),
    NativeExercise("db_shoulder_press", "Dumbbell shoulder press", "Shoulders", "Dumbbells"),
    NativeExercise("lateral_raise", "Lateral raise", "Shoulders", "Dumbbells"),
    NativeExercise("rear_delt_fly", "Rear-delt fly", "Shoulders", "Dumbbells"),
    NativeExercise("biceps_curl", "Biceps curl", "Arms", "Dumbbells"),
    NativeExercise("hammer_curl", "Hammer curl", "Arms", "Dumbbells"),
    NativeExercise("triceps_extension", "Triceps extension", "Arms", "Cable"),
    NativeExercise("diamond_push_up", "Diamond push-up", "Arms", "Bodyweight"),
    NativeExercise("squat", "Squat", "Legs", "Bodyweight / barbell"),
    NativeExercise("split_squat", "Bulgarian split squat", "Legs", "Bodyweight / dumbbells"),
    NativeExercise("lunge", "Lunge", "Legs", "Bodyweight / dumbbells"),
    NativeExercise("leg_press", "Leg press", "Legs", "Machine"),
    NativeExercise("leg_curl", "Leg curl", "Legs", "Machine"),
    NativeExercise("calf_raise", "Calf raise", "Legs", "Bodyweight / machine"),
    NativeExercise("rdl", "Romanian deadlift", "Posterior chain", "Barbell"),
    NativeExercise("deadlift", "Deadlift", "Posterior chain", "Barbell"),
    NativeExercise("hip_thrust", "Hip thrust", "Posterior chain", "Barbell"),
    NativeExercise("plank", "Plank", "Core", "Bodyweight"),
    NativeExercise("hanging_knee_raise", "Hanging knee raise", "Core", "Pull-up bar"),
    NativeExercise("crunch", "Crunch", "Core", "Bodyweight")
)

@Composable
internal fun NativeExerciseParityScreen(onBack: () -> Unit, openLegacy: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(NativeExerciseCatalog.first()) }
    var repsText by remember { mutableStateOf("10") }
    var loadText by remember { mutableStateOf("0") }
    var status by remember { mutableStateOf("Ready") }
    val sessionSets = remember { mutableStateListOf<NativeWorkoutSet>() }
    var recentSets by remember { mutableStateOf<List<HealthValue>>(emptyList()) }

    suspend fun refreshRecent() {
        val now = System.currentTimeMillis()
        recentSets = NativeDataHub.between("exercise_set", now - 90L * 24L * 60L * 60L * 1000L, now)
            .sortedByDescending { it.timestampEpochMs }
            .take(30)
    }

    LaunchedEffect(Unit) { refreshRecent() }

    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) NativeExerciseCatalog else NativeExerciseCatalog.filter {
            it.name.lowercase().contains(q) || it.group.lowercase().contains(q) || it.equipment.lowercase().contains(q)
        }
    }

    val sessionVolume = sessionSets.sumOf { it.volume }
    val bestLoad = recentSets.filter { it.metadata["exerciseId"] == selected.id }
        .mapNotNull { it.metadata["loadKg"]?.toDoubleOrNull() }.maxOrNull()

    Column(
        Modifier.fillMaxSize().background(ExerciseBg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(42.dp).height(42.dp).background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", color = ExerciseBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Exercise", color = ExerciseInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Library, sets, progression & workout history", color = ExerciseMuted, fontSize = 10.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExerciseStat("SETS", sessionSets.size.toString(), Modifier.weight(1f))
            ExerciseStat("VOLUME", if (sessionVolume > 0) "${sessionVolume.roundToInt()} kg" else "—", Modifier.weight(1f))
            ExerciseStat("PR", bestLoad?.let { "${trimNumber(it)} kg" } ?: "—", Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Exercise library", color = ExerciseInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Search by exercise, body area or equipment.", color = ExerciseMuted, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search exercises") }
            )
            Spacer(Modifier.height(8.dp))
            filtered.take(8).forEach { exercise ->
                Row(
                    Modifier.fillMaxWidth().clickable { selected = exercise }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(38.dp).height(38.dp).background(
                            if (selected.id == exercise.id) ExerciseBlue.copy(alpha = .14f) else ExerciseBg,
                            RoundedCornerShape(12.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) { Text(exercise.group.take(2).uppercase(), color = ExerciseBlue, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, color = ExerciseInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${exercise.group} · ${exercise.equipment}", color = ExerciseMuted, fontSize = 8.sp)
                    }
                    if (selected.id == exercise.id) Text("✓", color = ExerciseGreen, fontWeight = FontWeight.Black)
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Color(0xFFFFF4E8), RoundedCornerShape(22.dp)).padding(16.dp)) {
            Text("Log set", color = ExerciseOrange, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(selected.name, color = ExerciseInk, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Reps") }
                )
                OutlinedTextField(
                    value = loadText,
                    onValueChange = { loadText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Load kg") }
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().background(ExerciseOrange, RoundedCornerShape(16.dp)).clickable {
                    val reps = repsText.toIntOrNull() ?: 0
                    val load = loadText.toDoubleOrNull() ?: 0.0
                    if (reps <= 0) {
                        status = "Enter at least 1 rep"
                    } else {
                        val set = NativeWorkoutSet(selected, reps, load, System.currentTimeMillis())
                        sessionSets.add(set)
                        scope.launch {
                            NativeDataHub.saveValues(listOf(
                                HealthValue(
                                    domain = HealthDomain.EXERCISE,
                                    metric = "exercise_set",
                                    value = set.volume,
                                    unit = "kg-reps",
                                    timestampEpochMs = set.timestamp,
                                    source = "native-exercise",
                                    metadata = mapOf(
                                        "exerciseId" to selected.id,
                                        "exerciseName" to selected.name,
                                        "group" to selected.group,
                                        "equipment" to selected.equipment,
                                        "reps" to reps.toString(),
                                        "loadKg" to load.toString()
                                    )
                                )
                            ))
                            refreshRecent()
                            status = "Saved ${selected.name}: $reps reps${if (load > 0) " × ${trimNumber(load)} kg" else ""}"
                        }
                    }
                }.padding(14.dp),
                contentAlignment = Alignment.Center
            ) { Text("ADD SET", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(7.dp))
            Text(status, color = ExerciseMuted, fontSize = 9.sp)
        }

        if (sessionSets.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(15.dp)) {
                Text("Current workout", color = ExerciseInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                sessionSets.takeLast(10).forEachIndexed { index, set ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${index + 1}. ${set.exercise.name}", color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("${set.reps} × ${if (set.loadKg > 0) trimNumber(set.loadKg) + " kg" else "BW"}", color = ExerciseMuted, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier.fillMaxWidth().background(ExerciseGreen, RoundedCornerShape(15.dp)).clickable {
                        val now = System.currentTimeMillis()
                        val metadata = mapOf(
                            "sets" to sessionSets.size.toString(),
                            "volumeKg" to sessionVolume.toString(),
                            "exerciseCount" to sessionSets.map { it.exercise.id }.distinct().size.toString()
                        )
                        scope.launch {
                            NativeDataHub.saveValues(listOf(
                                HealthValue(HealthDomain.EXERCISE, "workout_session", sessionSets.size.toDouble(), "sets", now, "native-exercise", metadata),
                                HealthValue(HealthDomain.EXERCISE, "workout_volume", sessionVolume, "kg-reps", now, "native-exercise", metadata)
                            ))
                            status = "Workout saved"
                            sessionSets.clear()
                        }
                    }.padding(13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("FINISH WORKOUT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(15.dp)) {
            Text("Recent sets", color = ExerciseInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("Stored directly in the shared Project Superhuman database.", color = ExerciseMuted, fontSize = 9.sp)
            Spacer(Modifier.height(7.dp))
            if (recentSets.isEmpty()) {
                Text("No native workout sets yet.", color = ExerciseMuted, fontSize = 10.sp)
            } else {
                recentSets.take(8).forEach { value ->
                    val name = value.metadata["exerciseName"] ?: "Exercise"
                    val reps = value.metadata["reps"] ?: "—"
                    val load = value.metadata["loadKg"]?.toDoubleOrNull() ?: 0.0
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(name, color = ExerciseInk, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("$reps × ${if (load > 0) trimNumber(load) + " kg" else "BW"}", color = ExerciseMuted, fontSize = 9.sp)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(15.dp)) {
            Text("Existing exercise assets", color = ExerciseBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("The established full-bodied Project Superhuman exercise icon library and legacy workout history remain preserved while final visual parity is completed later.", color = ExerciseMuted, fontSize = 9.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().background(ExerciseNavy, RoundedCornerShape(15.dp)).clickable(onClick = openLegacy).padding(13.dp), contentAlignment = Alignment.Center) {
                Text("OPEN EXISTING EXERCISE TOOLS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ExerciseStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(11.dp)) {
        Text(label, color = ExerciseMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = ExerciseInk, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
