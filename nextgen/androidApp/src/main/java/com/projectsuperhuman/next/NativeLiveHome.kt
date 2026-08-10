package com.projectsuperhuman.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectsuperhuman.next.core.HealthDomain
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Home orchestration only. Visual language lives in dedicated Home component files so future
 * sessions can change dashboard visuals without disturbing repository/data calculations.
 */
data class NativeHomeSnapshot(
    val sleepScore: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepStartLabel: String? = null,
    val sleepEndLabel: String? = null,
    val waterLitres: Double = 0.0,
    val waterGoalMl: Int = 3600,
    val caloriesToday: Int = 0,
    val proteinToday: Int = 0,
    val workoutsToday: Int = 0,
    val workoutSetsToday: Int = 0,
    val workoutVolumeToday: Int = 0,
    val bodyWeightKg: Double? = null,
    val bodyWeightChange30d: Double? = null,
    val bodyWeightTrend: List<Double> = emptyList(),
    val clinicalMarkers: Int = 0,
    val clinicalAlerts: Int = 0,
    val mindfulnessMinutesToday: Int = 0
)

@Composable
internal fun NativeLiveHome(
    openClinical: () -> Unit,
    openBody: () -> Unit,
    openSleep: () -> Unit,
    openBloodPressure: () -> Unit,
    openHydration: () -> Unit,
    openNutrition: () -> Unit,
    openExercise: () -> Unit,
    openMindfulness: () -> Unit
) {
    var snapshot by remember { mutableStateOf(NativeHomeSnapshot()) }
    LaunchedEffect(Unit) { snapshot = loadNativeHomeSnapshot() }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFFF8FBFD))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegacyHomeHero(snapshot)
        PremiumHomeHydrationTile(snapshot, openHydration)
        LegacyClinicalCard(snapshot, openClinical)
        LegacyTrainingCard(snapshot, openExercise)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegacyBodyCard(snapshot, Modifier.weight(1f), openBody)
            LegacySleepCard(snapshot, Modifier.weight(1f), openSleep)
        }
        LegacyNutritionCard(snapshot, openNutrition)
        LegacyHomeLinks(
            openMindfulness = openMindfulness,
            openExercise = openExercise,
            openInsights = openClinical
        )
        LegacyBloodPressureLink(openBloodPressure)
        Spacer(Modifier.height(24.dp))
    }
}

private suspend fun loadNativeHomeSnapshot(): NativeHomeSnapshot {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

    val sleep = NativeDataHub.latestForDomain(HealthDomain.SLEEP)
    val body = NativeDataHub.latestForDomain(HealthDomain.BODY)
    val clinical = NativeDataHub.latestForDomain(HealthDomain.CLINICAL)
    val kcal = NativeDataHub.between("food_kcal", start, now).sumOf { it.value }.roundToInt()
    val protein = NativeDataHub.between("food_protein", start, now).sumOf { it.value }.roundToInt()
    val waterGoalMl = NativeDataHub.latest("hydration_goal_ml")?.value?.roundToInt()?.coerceIn(1500, 6000) ?: 3600
    val waterEvents = NativeDataHub.between("water_intake_ml", start, now)
    val rawWaterLitres = if (waterEvents.isNotEmpty()) {
        waterEvents.sumOf { it.value } / 1000.0
    } else {
        NativeDataHub.between("water_total_l", start, now).maxByOrNull { it.timestampEpochMs }?.value
            ?: NativeDataHub.latest("water_total_l")?.takeIf { it.timestampEpochMs >= start }?.value
            ?: 0.0
    }
    // Old validation builds allowed accidental over-logging. Preserve source events for history,
    // but the current Home contract never presents more than the active daily goal.
    val water = rawWaterLitres.coerceIn(0.0, waterGoalMl / 1000.0)

    val workouts = NativeDataHub.between("workout_session", start, now)
    val sets = NativeDataHub.between("exercise_set", start, now)
    val volumes = NativeDataHub.between("workout_volume", start, now)
    val mindfulness = NativeDataHub.latestForDomain(HealthDomain.MINDFULNESS)
    val bodyHistory = NativeDataHub.between("body_weight_kg", thirtyDaysAgo, now).sortedBy { it.timestampEpochMs }

    fun sleepMetric(name: String) = sleep.firstOrNull { it.metric == name }?.value
    fun sleepText(name: String) = sleep.firstOrNull { it.metric == name }?.metadata?.get("display")
    fun bodyMetric(name: String) = body.firstOrNull { it.metric == name }?.value

    val clinicalAlerts = clinical.count { it.metadata["status"] == "LOW" || it.metadata["status"] == "HIGH" }
    val mindfulnessToday = mindfulness
        .filter { it.timestampEpochMs >= start }
        .filter { it.metric.contains("minute", true) || it.unit == "min" }
        .sumOf { it.value }.roundToInt()

    val trend = bodyHistory.map { it.value }.takeLast(12)
    val weightChange = if (bodyHistory.size >= 2) bodyHistory.last().value - bodyHistory.first().value else null

    return NativeHomeSnapshot(
        sleepScore = sleepMetric("sleep_score")?.roundToInt(),
        sleepMinutes = sleepMetric("sleep_total_minutes")?.roundToInt(),
        sleepStartLabel = sleepText("sleep_total_minutes"),
        sleepEndLabel = null,
        waterLitres = water,
        waterGoalMl = waterGoalMl,
        caloriesToday = kcal,
        proteinToday = protein,
        workoutsToday = workouts.size,
        workoutSetsToday = sets.size,
        workoutVolumeToday = volumes.sumOf { it.value }.roundToInt(),
        bodyWeightKg = bodyMetric("body_weight_kg"),
        bodyWeightChange30d = weightChange,
        bodyWeightTrend = trend,
        clinicalMarkers = clinical.size,
        clinicalAlerts = clinicalAlerts,
        mindfulnessMinutesToday = mindfulnessToday
    )
}

internal fun formatMinutesHome(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
