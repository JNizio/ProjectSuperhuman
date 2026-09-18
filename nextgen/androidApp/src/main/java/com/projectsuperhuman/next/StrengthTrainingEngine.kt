package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthValue
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

internal const val STRENGTH_PRIMARY_MUSCLE_WEIGHT = 1.0
internal const val STRENGTH_SECONDARY_MUSCLE_WEIGHT = 0.5

internal data class StrengthWorkoutSession(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val durationMin: Int,
    val name: String,
    val sets: List<HealthValue>,
    val totalSets: Int,
    val workingSets: Int,
    val totalVolumeKg: Double,
    val exerciseCount: Int,
    val sessionRpe: Double?,
    val notes: String
)

internal data class StrengthExercisePr(
    val exerciseId: String,
    val exerciseName: String,
    val heaviestLoadKg: Double,
    val highestReps: Int,
    val bestEstimated1RmKg: Double,
    val bestSessionVolumeKg: Double,
    val bestRepLoads: Map<Int, Double>
)

internal data class StrengthProgressSnapshot(
    val weeklyWorkingSets: Int,
    val weeklyVolumeKg: Double,
    val trainingFrequency: Int,
    val exerciseFrequency: Map<String, Int>,
    val estimated1RmTrend: List<Pair<Long, Double>>,
    val volumeTrend: List<Pair<Long, Double>>,
    val bestLoadTrend: List<Pair<Long, Double>>,
    val recentProgression: List<String>
)

internal data class StrengthMuscleVolume(
    val muscle: String,
    val estimatedWorkingSets: Double
)

internal fun strengthEstimated1Rm(loadKg: Double, reps: Int): Double {
    if (loadKg <= 0.0 || reps <= 0) return 0.0
    // Epley formula: estimated 1RM = load × (1 + reps / 30).
    return loadKg * (1.0 + reps.toDouble() / 30.0)
}

internal fun isStrengthWorkingSet(value: HealthValue): Boolean {
    val type = value.metadata["setType"].orEmpty()
    return !type.equals("Warmup", ignoreCase = true)
}

internal fun buildStrengthSessions(
    sessionRows: List<HealthValue>,
    setRows: List<HealthValue>
): List<StrengthWorkoutSession> {
    val sortedSessions = sessionRows.sortedByDescending { it.timestampEpochMs }
    val claimedLegacyTimestamps = mutableSetOf<Long>()

    return sortedSessions.mapIndexed { index, row ->
        val meta = row.metadata
        val end = meta["endTime"]?.toLongOrNull() ?: row.timestampEpochMs
        val durationMin = meta["durationMin"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val start = meta["startTime"]?.toLongOrNull()
            ?: if (durationMin > 0) end - durationMin * 60_000L else end - 4L * 60L * 60L * 1000L
        val sessionId = meta["sessionId"].takeUnless { it.isNullOrBlank() } ?: "legacy-${row.timestampEpochMs}-$index"

        val matching = setRows.filter { set ->
            val explicitId = set.metadata["sessionId"]
            when {
                !explicitId.isNullOrBlank() -> explicitId == sessionId
                set.timestampEpochMs in start..end && set.timestampEpochMs !in claimedLegacyTimestamps -> true
                else -> false
            }
        }.sortedBy { it.timestampEpochMs }

        if (meta["sessionId"].isNullOrBlank()) claimedLegacyTimestamps += matching.map { it.timestampEpochMs }

        val working = matching.filter(::isStrengthWorkingSet)
        val volume = meta["volumeKg"]?.toDoubleOrNull() ?: working.sumOf { it.value.coerceAtLeast(0.0) }
        val name = meta["workoutName"].takeUnless { it.isNullOrBlank() }
            ?: inferStrengthWorkoutName(matching)

        StrengthWorkoutSession(
            sessionId = sessionId,
            startTime = start,
            endTime = end,
            durationMin = if (durationMin > 0) durationMin else max(1, ((end - start) / 60_000L).toInt()),
            name = name,
            sets = matching,
            totalSets = meta["totalSets"]?.toIntOrNull() ?: matching.size,
            workingSets = meta["workingSets"]?.toIntOrNull() ?: working.size,
            totalVolumeKg = volume,
            exerciseCount = meta["exerciseCount"]?.toIntOrNull()
                ?: matching.mapNotNull { it.metadata["exerciseId"] }.distinct().size,
            sessionRpe = meta["sessionRpe"]?.toDoubleOrNull(),
            notes = meta["notes"].orEmpty()
        )
    }
}

internal fun inferStrengthWorkoutName(sets: List<HealthValue>): String {
    val groups = sets.mapNotNull { it.metadata["group"]?.lowercase(Locale.US) }
    if (groups.isEmpty()) return "Strength Workout"
    val joined = groups.joinToString(" ")
    val chest = "chest" in joined
    val back = "back" in joined || "lats" in joined
    val shoulders = "shoulder" in joined
    val legs = listOf("quad", "hamstring", "glute", "calf", "leg").any { it in joined }
    val arms = listOf("bicep", "tricep").any { it in joined }
    return when {
        chest && back && legs -> "Full Body"
        legs && !(chest || back || shoulders) -> "Lower Body"
        (chest || back || shoulders) && !legs -> if (chest && arms && !back) "Chest & Triceps" else "Upper Body"
        else -> "Strength Workout"
    }
}

internal fun calculateStrengthPrs(
    sets: List<HealthValue>,
    sessions: List<StrengthWorkoutSession>
): List<StrengthExercisePr> {
    return sets.groupBy { it.metadata["exerciseId"] ?: it.metadata["exerciseName"] ?: "unknown" }
        .map { (id, rows) ->
            val name = rows.firstNotNullOfOrNull { it.metadata["exerciseName"] } ?: "Exercise"
            val parsed = rows.mapNotNull { row ->
                val reps = row.metadata["reps"]?.toIntOrNull() ?: return@mapNotNull null
                val load = row.metadata["loadKg"]?.toDoubleOrNull() ?: return@mapNotNull null
                Triple(row, reps, load)
            }
            val bestRepLoads = parsed
                .filter { it.second in 1..20 }
                .groupBy { it.second }
                .mapValues { (_, values) -> values.maxOf { it.third } }
                .toSortedMap()

            val bestSessionVolume = sessions.maxOfOrNull { session ->
                session.sets.filter { (it.metadata["exerciseId"] ?: it.metadata["exerciseName"]) == id }
                    .filter(::isStrengthWorkingSet)
                    .sumOf { it.value.coerceAtLeast(0.0) }
            } ?: 0.0

            StrengthExercisePr(
                exerciseId = id,
                exerciseName = name,
                heaviestLoadKg = parsed.maxOfOrNull { it.third } ?: 0.0,
                highestReps = parsed.maxOfOrNull { it.second } ?: 0,
                bestEstimated1RmKg = parsed.maxOfOrNull { strengthEstimated1Rm(it.third, it.second) } ?: 0.0,
                bestSessionVolumeKg = bestSessionVolume,
                bestRepLoads = bestRepLoads
            )
        }.sortedByDescending { it.bestEstimated1RmKg }
}

internal fun calculateStrengthProgress(
    sets: List<HealthValue>,
    sessions: List<StrengthWorkoutSession>,
    now: Long = System.currentTimeMillis()
): StrengthProgressSnapshot {
    val weekStart = now - 7L * 24L * 60L * 60L * 1000L
    val weekSets = sets.filter { it.timestampEpochMs >= weekStart && isStrengthWorkingSet(it) }
    val weekSessions = sessions.filter { it.endTime >= weekStart }

    val exerciseFrequency = weekSets
        .groupBy { it.metadata["exerciseName"] ?: "Exercise" }
        .mapValues { (_, rows) -> rows.map { it.metadata["sessionId"] ?: it.timestampEpochMs / 86_400_000L }.distinct().size }
        .toList().sortedByDescending { it.second }.toMap()

    fun weekKey(timestamp: Long): Long {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val wf = WeekFields.of(Locale.getDefault())
        val week = date.get(wf.weekOfWeekBasedYear()).toLong()
        return date.get(wf.weekBasedYear()).toLong() * 100L + week
    }

    val weeklySets = sets.filter(::isStrengthWorkingSet).groupBy { weekKey(it.timestampEpochMs) }
    val volumeTrend = weeklySets.entries.sortedBy { it.key }.takeLast(12).map { (week, rows) -> week to rows.sumOf { it.value.coerceAtLeast(0.0) } }
    val bestLoadTrend = weeklySets.entries.sortedBy { it.key }.takeLast(12).map { (week, rows) ->
        week to (rows.mapNotNull { it.metadata["loadKg"]?.toDoubleOrNull() }.maxOrNull() ?: 0.0)
    }
    val estimated1RmTrend = weeklySets.entries.sortedBy { it.key }.takeLast(12).map { (week, rows) ->
        week to (rows.mapNotNull {
            val load = it.metadata["loadKg"]?.toDoubleOrNull()
            val reps = it.metadata["reps"]?.toIntOrNull()
            if (load != null && reps != null) strengthEstimated1Rm(load, reps) else null
        }.maxOrNull() ?: 0.0)
    }

    val recent = sets.filter(::isStrengthWorkingSet).sortedByDescending { it.timestampEpochMs }
    val recentProgression = recent.groupBy { it.metadata["exerciseId"] ?: it.metadata["exerciseName"] ?: "Exercise" }
        .mapNotNull { (_, rows) ->
            val latest = rows.take(3)
            val previous = rows.drop(3).take(3)
            if (latest.isEmpty() || previous.isEmpty()) return@mapNotNull null
            val latestBest = latest.mapNotNull { it.metadata["loadKg"]?.toDoubleOrNull() }.maxOrNull() ?: 0.0
            val previousBest = previous.mapNotNull { it.metadata["loadKg"]?.toDoubleOrNull() }.maxOrNull() ?: 0.0
            if (latestBest > previousBest) {
                "${rows.first().metadata["exerciseName"] ?: "Exercise"}: +${formatStrengthNumber(latestBest - previousBest)} kg best load"
            } else null
        }.take(4)

    return StrengthProgressSnapshot(
        weeklyWorkingSets = weekSets.size,
        weeklyVolumeKg = weekSets.sumOf { it.value.coerceAtLeast(0.0) },
        trainingFrequency = weekSessions.size,
        exerciseFrequency = exerciseFrequency,
        estimated1RmTrend = estimated1RmTrend,
        volumeTrend = volumeTrend,
        bestLoadTrend = bestLoadTrend,
        recentProgression = recentProgression
    )
}

internal fun calculateMuscleVolume(
    sets: List<HealthValue>,
    catalog: List<NativeExercise>,
    now: Long = System.currentTimeMillis()
): List<StrengthMuscleVolume> {
    val weekStart = now - 7L * 24L * 60L * 60L * 1000L
    val byId = catalog.associateBy { it.id }
    val totals = linkedMapOf(
        "Chest" to 0.0, "Back" to 0.0, "Shoulders" to 0.0, "Biceps" to 0.0, "Triceps" to 0.0,
        "Quads" to 0.0, "Hamstrings" to 0.0, "Glutes" to 0.0, "Calves" to 0.0, "Core" to 0.0
    )

    sets.asSequence()
        .filter { it.timestampEpochMs >= weekStart && isStrengthWorkingSet(it) }
        .forEach { set ->
            val exercise = set.metadata["exerciseId"]?.let(byId::get) ?: return@forEach
            exercise.primaryMuscles.mapNotNull(::canonicalStrengthMuscle).distinct().forEach { totals[it] = (totals[it] ?: 0.0) + STRENGTH_PRIMARY_MUSCLE_WEIGHT }
            exercise.secondaryMuscles.mapNotNull(::canonicalStrengthMuscle).distinct().forEach { totals[it] = (totals[it] ?: 0.0) + STRENGTH_SECONDARY_MUSCLE_WEIGHT }
        }

    return totals.map { StrengthMuscleVolume(it.key, it.value) }
}

private fun canonicalStrengthMuscle(raw: String): String? {
    val s = raw.lowercase(Locale.US).replace('_', ' ').replace('-', ' ')
    return when {
        "chest" in s || "pector" in s -> "Chest"
        "lat" in s || "trape" in s || "rhombo" in s || "back" in s || "erector" in s -> "Back"
        "deltoid" in s || "shoulder" in s -> "Shoulders"
        "bicep" in s || "brachialis" in s -> "Biceps"
        "tricep" in s -> "Triceps"
        "quad" in s || "vastus" in s || "rectus femoris" in s -> "Quads"
        "hamstring" in s || "biceps femoris" in s || "semitend" in s || "semimembr" in s -> "Hamstrings"
        "glute" in s -> "Glutes"
        "calf" in s || "gastrocnem" in s || "soleus" in s -> "Calves"
        "abs" in s || "abdom" in s || "oblique" in s || "core" in s -> "Core"
        else -> null
    }
}

internal fun formatStrengthNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
