package com.projectsuperhuman.next

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class ActiveWorkoutSetDraft(
    val exerciseId: String,
    val reps: Int,
    val loadKg: Double,
    val timestamp: Long,
    val type: String,
    val rir: Int?,
    val rpe: Double?,
    val supersetTag: String?
)

internal data class ActiveWorkoutDraft(
    val startedAt: Long,
    val selectedExerciseId: String?,
    val exerciseIds: List<String>,
    val sets: List<ActiveWorkoutSetDraft>,
    val restEndsAt: Long
)

private const val ACTIVE_WORKOUT_PREFS = "superhuman_training"
private const val ACTIVE_WORKOUT_KEY = "active_workout_v1"

internal fun saveActiveWorkoutDraft(context: Context, draft: ActiveWorkoutDraft?) {
    val prefs = context.getSharedPreferences(ACTIVE_WORKOUT_PREFS, Context.MODE_PRIVATE)
    if (draft == null || draft.startedAt <= 0L) {
        prefs.edit().remove(ACTIVE_WORKOUT_KEY).apply()
        return
    }

    val root = JSONObject()
        .put("startedAt", draft.startedAt)
        .put("selectedExerciseId", draft.selectedExerciseId ?: JSONObject.NULL)
        .put("exerciseIds", JSONArray(draft.exerciseIds))
        .put("restEndsAt", draft.restEndsAt)

    val sets = JSONArray()
    draft.sets.forEach { set ->
        sets.put(
            JSONObject()
                .put("exerciseId", set.exerciseId)
                .put("reps", set.reps)
                .put("loadKg", set.loadKg)
                .put("timestamp", set.timestamp)
                .put("type", set.type)
                .put("rir", set.rir ?: JSONObject.NULL)
                .put("rpe", set.rpe ?: JSONObject.NULL)
                .put("supersetTag", set.supersetTag ?: JSONObject.NULL)
        )
    }
    root.put("sets", sets)
    prefs.edit().putString(ACTIVE_WORKOUT_KEY, root.toString()).apply()
}

internal fun loadActiveWorkoutDraft(context: Context): ActiveWorkoutDraft? {
    val raw = context.getSharedPreferences(ACTIVE_WORKOUT_PREFS, Context.MODE_PRIVATE)
        .getString(ACTIVE_WORKOUT_KEY, null) ?: return null

    return runCatching {
        val root = JSONObject(raw)
        val startedAt = root.optLong("startedAt", 0L)
        if (startedAt <= 0L) return@runCatching null

        val exerciseArray = root.optJSONArray("exerciseIds") ?: JSONArray()
        val exerciseIds = (0 until exerciseArray.length()).mapNotNull { index ->
            exerciseArray.optString(index).takeIf { it.isNotBlank() }
        }

        val setArray = root.optJSONArray("sets") ?: JSONArray()
        val sets = (0 until setArray.length()).mapNotNull { index ->
            val item = setArray.optJSONObject(index) ?: return@mapNotNull null
            val exerciseId = item.optString("exerciseId")
            if (exerciseId.isBlank()) return@mapNotNull null
            ActiveWorkoutSetDraft(
                exerciseId = exerciseId,
                reps = item.optInt("reps", 0),
                loadKg = item.optDouble("loadKg", 0.0),
                timestamp = item.optLong("timestamp", startedAt),
                type = item.optString("type", "Work"),
                rir = if (item.isNull("rir")) null else item.optInt("rir"),
                rpe = if (item.isNull("rpe")) null else item.optDouble("rpe"),
                supersetTag = if (item.isNull("supersetTag")) null else item.optString("supersetTag").takeIf { it.isNotBlank() }
            )
        }

        ActiveWorkoutDraft(
            startedAt = startedAt,
            selectedExerciseId = if (root.isNull("selectedExerciseId")) null else root.optString("selectedExerciseId").takeIf { it.isNotBlank() },
            exerciseIds = exerciseIds,
            sets = sets,
            restEndsAt = root.optLong("restEndsAt", 0L)
        )
    }.getOrNull()
}

internal fun clearActiveWorkoutDraft(context: Context) {
    context.getSharedPreferences(ACTIVE_WORKOUT_PREFS, Context.MODE_PRIVATE)
        .edit().remove(ACTIVE_WORKOUT_KEY).apply()
}
