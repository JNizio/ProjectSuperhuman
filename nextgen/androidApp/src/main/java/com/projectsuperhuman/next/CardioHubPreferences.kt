package com.projectsuperhuman.next

import android.content.Context

internal data class CardioHubGoals(
    val totalMinutes: Int? = null,
    val zone2Minutes: Int? = null,
    val zone3Minutes: Int? = null
)

internal class CardioHubPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun quickActivities(): List<CardioActivityType> {
        val raw = prefs.getString(KEY_QUICK_ACTIVITIES, null)
        val parsed = raw
            ?.split(',')
            ?.mapNotNull { token -> runCatching { CardioActivityType.valueOf(token) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        return if (parsed.size == QUICK_SLOT_COUNT) parsed else DEFAULT_QUICK_ACTIVITIES
    }

    fun saveQuickActivities(activities: List<CardioActivityType>) {
        val normalized = activities.distinct().take(QUICK_SLOT_COUNT)
        if (normalized.size != QUICK_SLOT_COUNT) return
        prefs.edit()
            .putString(KEY_QUICK_ACTIVITIES, normalized.joinToString(",") { it.name })
            .apply()
    }

    fun goals(): CardioHubGoals = CardioHubGoals(
        totalMinutes = prefs.readPositiveInt(KEY_TOTAL_GOAL),
        zone2Minutes = prefs.readPositiveInt(KEY_ZONE2_GOAL),
        zone3Minutes = prefs.readPositiveInt(KEY_ZONE3_GOAL)
    )

    fun saveGoals(goals: CardioHubGoals) {
        prefs.edit().apply {
            putOrRemovePositiveInt(KEY_TOTAL_GOAL, goals.totalMinutes)
            putOrRemovePositiveInt(KEY_ZONE2_GOAL, goals.zone2Minutes)
            putOrRemovePositiveInt(KEY_ZONE3_GOAL, goals.zone3Minutes)
        }.apply()
    }

    companion object {
        const val QUICK_SLOT_COUNT = 4
        val DEFAULT_QUICK_ACTIVITIES = listOf(
            CardioActivityType.RUNNING,
            CardioActivityType.WALKING,
            CardioActivityType.CYCLING,
            CardioActivityType.SWIMMING
        )

        private const val PREFS_NAME = "project_superhuman_cardio_hub"
        private const val KEY_QUICK_ACTIVITIES = "quick_activities"
        private const val KEY_TOTAL_GOAL = "weekly_total_minutes_goal"
        private const val KEY_ZONE2_GOAL = "weekly_zone2_minutes_goal"
        private const val KEY_ZONE3_GOAL = "weekly_zone3_minutes_goal"
    }
}

private fun android.content.SharedPreferences.readPositiveInt(key: String): Int? =
    if (contains(key)) getInt(key, 0).takeIf { it > 0 } else null

private fun android.content.SharedPreferences.Editor.putOrRemovePositiveInt(
    key: String,
    value: Int?
): android.content.SharedPreferences.Editor {
    return if (value != null && value > 0) putInt(key, value) else remove(key)
}

internal fun replaceCardioQuickActivity(
    current: List<CardioActivityType>,
    slot: Int,
    replacement: CardioActivityType
): List<CardioActivityType> {
    if (slot !in 0 until CardioHubPreferences.QUICK_SLOT_COUNT) return current
    val result = current.toMutableList()
    while (result.size < CardioHubPreferences.QUICK_SLOT_COUNT) {
        result += CardioHubPreferences.DEFAULT_QUICK_ACTIVITIES[result.size]
    }

    val existingIndex = result.indexOf(replacement)
    if (existingIndex >= 0 && existingIndex != slot) {
        val displaced = result[slot]
        result[slot] = replacement
        result[existingIndex] = displaced
    } else {
        result[slot] = replacement
    }
    return result.take(CardioHubPreferences.QUICK_SLOT_COUNT)
}
