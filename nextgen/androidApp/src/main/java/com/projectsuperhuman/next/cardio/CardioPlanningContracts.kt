package com.projectsuperhuman.next

/**
 * Measurement-only seam for a future Cardio planner.
 *
 * It carries evidence and user availability/preferences but deliberately contains no workout
 * recommendation, medical clearance or autonomous coaching decision.
 */
internal data class CardioPlanningContext(
    val goals: List<String> = emptyList(),
    val recentTrainingLoad: CardioTrainingLoadPoint? = null,
    val fitnessTrend: CardioFitnessSnapshot? = null,
    val recoveryContext: CardioRecoveryContext? = null,
    val availableEpochDays: Set<Long> = emptySet(),
    val preferredWorkoutTypes: Set<CardioWorkoutType> = emptySet(),
    val generatedAtEpochMs: Long
)

internal interface CardioPlanningContextSource {
    suspend fun snapshot(): CardioPlanningContext
}
