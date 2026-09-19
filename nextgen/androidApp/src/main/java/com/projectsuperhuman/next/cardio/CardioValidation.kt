package com.projectsuperhuman.next

internal data class CardioValidationIssue(
    val field: String,
    val message: String
)

internal object CardioValidation {
    fun validate(session: CardioSession, nowEpochMs: Long): List<CardioValidationIssue> = buildList {
        if (session.id.isBlank()) add(CardioValidationIssue("id", "Cardio session ID is required"))
        if (session.startedAt <= 0L) add(CardioValidationIssue("startedAt", "Start time is invalid"))
        if (session.endedAt <= 0L) add(CardioValidationIssue("endedAt", "Finish time is invalid"))
        if (session.endedAt > nowEpochMs + FUTURE_TOLERANCE_MS) {
            add(CardioValidationIssue("endedAt", "Finish time cannot be in the future"))
        }
        if (session.source != "live" && session.endedAt < session.startedAt) {
            add(CardioValidationIssue("endedAt", "Finish time cannot be before start time"))
        }
        if (session.durationSeconds <= 0) {
            add(CardioValidationIssue("durationSeconds", "Duration must be greater than zero"))
        }
        if (session.pausedDurationSeconds < 0) {
            add(CardioValidationIssue("pausedDurationSeconds", "Paused duration cannot be negative"))
        }
        if (session.zoneSeconds.keys.any { it !in 1..5 }) {
            add(CardioValidationIssue("zoneSeconds", "Heart-rate zone must be between 1 and 5"))
        }
        if (session.zoneSeconds.values.any { it < 0 }) {
            add(CardioValidationIssue("zoneSeconds", "Heart-rate zone duration cannot be negative"))
        }
        if (session.zoneSeconds.values.sum() > session.durationSeconds) {
            add(CardioValidationIssue("zoneSeconds", "Heart-rate zone time cannot exceed workout duration"))
        }
        session.distanceKm?.let {
            if (!it.isFinite() || it <= 0.0) add(CardioValidationIssue("distanceKm", "Distance must be greater than zero"))
        }
        session.avgHeartRate?.let {
            if (it !in 20..260) add(CardioValidationIssue("avgHeartRate", "Average heart rate is out of bounds"))
        }
        session.maxHeartRate?.let {
            if (it !in 20..260) add(CardioValidationIssue("maxHeartRate", "Maximum heart rate is out of bounds"))
        }
        if (session.avgHeartRate != null && session.maxHeartRate != null && session.maxHeartRate < session.avgHeartRate) {
            add(CardioValidationIssue("maxHeartRate", "Maximum heart rate cannot be below average heart rate"))
        }
        session.rpe?.let {
            if (!it.isFinite() || it !in 0.0..10.0) add(CardioValidationIssue("rpe", "RPE must be between 0 and 10"))
        }
    }

    private const val FUTURE_TOLERANCE_MS = 60_000L
}
