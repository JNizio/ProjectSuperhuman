package com.projectsuperhuman.next

internal const val CARDIO_SESSION_SCHEMA_VERSION = 2
internal const val CARDIO_LIVE_DRAFT_SCHEMA_VERSION = 2

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

internal enum class CardioPaceMode {
    NONE,
    PER_KM,
    SPEED,
    PER_500M,
    PER_100M
}

internal data class CardioSession(
    val id: String,
    val activity: CardioActivityType,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
    val pausedDurationSeconds: Int = 0,
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
    val avgPace100mSeconds: Int? = null,
    val schemaVersion: Int = CARDIO_SESSION_SCHEMA_VERSION,
    val extensions: Map<String, String> = emptyMap()
)

internal enum class CardioLivePhase {
    RECORDING,
    PAUSED,
    FINISHING
}

internal data class CardioLiveDraft(
    val schemaVersion: Int = CARDIO_LIVE_DRAFT_SCHEMA_VERSION,
    val sessionId: String,
    val activity: CardioActivityType,
    val workoutType: CardioWorkoutType = CardioWorkoutType.FREE,
    val startedAtEpochMs: Long,
    val accumulatedActiveMs: Long = 0L,
    val accumulatedPausedMs: Long = 0L,
    val phase: CardioLivePhase,
    val phaseStartedEpochMs: Long,
    val phaseStartedElapsedRealtimeMs: Long,
    val pendingCompletionEpochMs: Long? = null
)

internal data class CardioLiveTiming(
    val activeMs: Long,
    val pausedMs: Long
) {
    val activeSeconds: Int get() = (activeMs / 1000L).toInt().coerceAtLeast(0)
    val pausedSeconds: Int get() = (pausedMs / 1000L).toInt().coerceAtLeast(0)
}

internal data class CardioPreparedCompletion(
    val draft: CardioLiveDraft,
    val session: CardioSession
)
