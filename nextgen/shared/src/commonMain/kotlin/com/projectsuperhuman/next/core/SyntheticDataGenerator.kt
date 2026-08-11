package com.projectsuperhuman.next.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

const val SYNTHETIC_DATA_SOURCE = "project-superhuman-synthetic-v1"
const val SYNTHETIC_DATA_SCENARIO = "correlated-lifestyle-v1"

data class SyntheticGenerationConfig(
    val days: Int = 90,
    val seed: Int = 20260811,
    val batchSize: Int = 750
)

data class SyntheticGenerationResult(
    val requestedDays: Int,
    val generated: Int,
    val accepted: Int,
    val rejected: Int,
    val deduplicated: Int
)

/**
 * Developer-only realistic history generator.
 *
 * Synthetic observations deliberately use the exact same [DataIngestionPipeline] as
 * real module/import traffic. Relationships are intentionally correlated so query and
 * interpretation code has useful signal to discover, while every row is unmistakably
 * tagged as synthetic and can be removed by source without touching genuine data.
 */
class SyntheticDataGenerator(
    private val ingestion: DataIngestionPipeline,
    private val nowEpochMs: () -> Long
) {
    suspend fun generate(config: SyntheticGenerationConfig = SyntheticGenerationConfig()): SyntheticGenerationResult {
        require(config.days in 1..MAX_DAYS) { "Synthetic history must be between 1 and $MAX_DAYS days" }
        require(config.batchSize in 100..5_000) { "Synthetic batch size must be between 100 and 5000" }

        val random = Random(config.seed)
        val now = nowEpochMs()
        val firstAnchor = now - (config.days - 1L) * DAY_MS
        var weightKg = 82.4 + random.centered(0.8)
        var bodyFatPct = 18.5 + random.centered(0.9)
        val batch = ArrayList<HealthValue>(config.batchSize + 128)
        var generated = 0
        var accepted = 0
        var rejected = 0
        var deduplicated = 0

        suspend fun flush() {
            if (batch.isEmpty()) return
            val result = ingestion.ingestValues(batch.toList())
            generated += batch.size
            accepted += result.accepted
            rejected += result.rejected
            deduplicated += result.deduplicated
            batch.clear()
        }

        fun add(
            dayIndex: Int,
            anchor: Long,
            domain: HealthDomain,
            metric: String,
            value: Double,
            unit: String,
            timestamp: Long,
            ordinal: Int = 0,
            metadata: Map<String, String> = emptyMap()
        ) {
            val dayToken = anchor / DAY_MS
            val recordId = "synthetic:${config.seed}:$dayToken:${domain.name}:$metric:$ordinal"
            batch += HealthValue(
                domain = domain,
                metric = metric,
                value = value,
                unit = unit,
                timestampEpochMs = timestamp.coerceAtMost(now),
                source = SYNTHETIC_DATA_SOURCE,
                metadata = mapOf(
                    "synthetic" to "true",
                    "syntheticGenerator" to "app-wide-v1",
                    "syntheticScenario" to SYNTHETIC_DATA_SCENARIO,
                    "syntheticSeed" to config.seed.toString(),
                    "syntheticDayIndex" to dayIndex.toString(),
                    "sourceRecordId" to recordId
                ) + metadata
            )
        }

        for (dayIndex in 0 until config.days) {
            val anchor = firstAnchor + dayIndex * DAY_MS
            val wave = sin(dayIndex / 10.0)
            val recovery = (0.58 + wave * 0.14 + random.centered(0.16)).coerceIn(0.15, 0.95)
            val stress = (0.50 - recovery * 0.24 + random.centered(0.20)).coerceIn(0.08, 0.92)
            val mindfulnessMinutes = if (random.nextDouble() < 0.70) {
                (6.0 + recovery * 8.0 + random.nextDouble() * 10.0).coerceIn(5.0, 28.0)
            } else 0.0
            val activity = (0.48 + recovery * 0.22 - stress * 0.10 + sin(dayIndex / 5.0) * 0.10 + random.centered(0.16))
                .coerceIn(0.12, 0.98)

            // Sleep: better recovery + lower stress produces longer, more efficient sleep.
            val sleepMinutes = (385.0 + recovery * 115.0 - stress * 48.0 + random.centered(24.0)).coerceIn(300.0, 555.0)
            val awakeMinutes = (52.0 - recovery * 30.0 + stress * 24.0 + random.centered(12.0)).coerceIn(8.0, 95.0)
            val totalMinutes = sleepMinutes + awakeMinutes
            val deepFraction = (0.12 + recovery * 0.10 - stress * 0.018 + random.centered(0.025)).coerceIn(0.08, 0.28)
            val remFraction = (0.17 + recovery * 0.075 - stress * 0.012 + random.centered(0.02)).coerceIn(0.14, 0.30)
            val deepMinutes = sleepMinutes * deepFraction
            val remMinutes = sleepMinutes * remFraction
            val lightMinutes = (sleepMinutes - deepMinutes - remMinutes).coerceAtLeast(0.0)
            val efficiency = (sleepMinutes / totalMinutes * 100.0).coerceIn(60.0, 99.0)
            val durationScore = (100.0 - abs(sleepMinutes - 480.0) * 0.28).coerceIn(35.0, 100.0)
            val continuityScore = (100.0 - awakeMinutes * 0.68 - stress * 10.0).coerceIn(30.0, 100.0)
            val stageScore = (100.0 - abs(deepFraction - 0.20) * 175.0 - abs(remFraction - 0.23) * 145.0).coerceIn(35.0, 100.0)
            val sleepScore = (durationScore * 0.40 + continuityScore * 0.32 + stageScore * 0.28 + random.centered(2.0)).coerceIn(30.0, 99.0)
            val sleepTs = anchor - 8L * HOUR_MS
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_total_minutes", totalMinutes, "min", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_time_minutes", sleepMinutes, "min", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_awake_minutes", awakeMinutes, "min", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_light_minutes", lightMinutes, "min", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_deep_minutes", deepMinutes, "min", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_rem_minutes", remMinutes, "min", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_efficiency_pct", efficiency, "%", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_duration_score", durationScore, "score", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_continuity_score", continuityScore, "score", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_stage_balance_score", stageScore, "score", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_score", sleepScore, "score", sleepTs)
            add(dayIndex, anchor, HealthDomain.SLEEP, "sleep_interruption_count", (1.0 + stress * 4.0 + random.nextDouble() * 2.0).roundToInt().toDouble(), "count", sleepTs)

            // Wearable/activity stream. Steps and calories share a strong relationship.
            val steps = (3_500.0 + activity * 9_000.0 + recovery * 1_200.0 - stress * 650.0 + random.centered(900.0))
                .coerceIn(1_800.0, 19_000.0)
            val workoutDay = dayIndex % 7 in setOf(0, 2, 4, 5) && recovery > 0.28
            val workoutMinutes = if (workoutDay) (30.0 + activity * 38.0 + random.centered(8.0)).coerceIn(22.0, 85.0) else 0.0
            val activeCalories = (150.0 + steps * 0.045 + workoutMinutes * 2.8 + random.centered(22.0)).coerceIn(150.0, 1_250.0)
            val restingHr = (67.0 + stress * 8.0 - recovery * 10.0 - activity * 2.0 + random.centered(2.2)).coerceIn(45.0, 88.0)
            val activityTs = anchor - 2L * HOUR_MS
            add(dayIndex, anchor, HealthDomain.EXERCISE, "steps", steps.roundToInt().toDouble(), "count", activityTs)
            add(dayIndex, anchor, HealthDomain.EXERCISE, "active_calories_kcal", activeCalories, "kcal", activityTs)
            add(dayIndex, anchor, HealthDomain.EXERCISE, "exercise_minutes", workoutMinutes, "min", activityTs)
            add(dayIndex, anchor, HealthDomain.EXERCISE, "resting_heart_rate_bpm", restingHr, "bpm", anchor - 7L * HOUR_MS)
            for (sample in 0 until 8) {
                val sampleHr = (restingHr + 10.0 + activity * 25.0 + if (workoutDay && sample in 5..6) 42.0 else 0.0 + random.centered(8.0))
                    .coerceIn(50.0, 178.0)
                add(dayIndex, anchor, HealthDomain.EXERCISE, "heart_rate_bpm", sampleHr, "bpm", anchor - (15L - sample * 2L) * HOUR_MS, sample)
            }

            if (workoutDay) {
                val exerciseIds = listOf("db_press", "cable_row", "biceps_curl")
                var workoutVolume = 0.0
                exerciseIds.forEachIndexed { setIndex, exerciseId ->
                    val reps = (8 + random.nextInt(5))
                    val load = when (exerciseId) {
                        "db_press" -> 22.0 + activity * 10.0
                        "cable_row" -> 35.0 + activity * 18.0
                        else -> 10.0 + activity * 7.0
                    } + random.centered(2.0)
                    val volume = reps * load
                    workoutVolume += volume
                    add(
                        dayIndex, anchor, HealthDomain.EXERCISE, "exercise_set", volume, "kg-reps",
                        activityTs - (exerciseIds.size - setIndex) * 4L * 60L * 1000L,
                        setIndex,
                        mapOf(
                            "exerciseId" to exerciseId,
                            "exerciseName" to exerciseId.replace('_', ' '),
                            "reps" to reps.toString(),
                            "loadKg" to roundedText(load)
                        )
                    )
                }
                add(dayIndex, anchor, HealthDomain.EXERCISE, "workout_session", exerciseIds.size.toDouble(), "sets", activityTs)
                add(dayIndex, anchor, HealthDomain.EXERCISE, "workout_volume", workoutVolume, "kg-reps", activityTs)
            }

            // Nutrition: calories rise with activity; higher protein is mildly associated with training days.
            val dailyKcal = (2_050.0 + activeCalories * 0.72 + if (workoutDay) 180.0 else 0.0 + random.centered(180.0)).coerceIn(1_700.0, 3_650.0)
            val dailyProtein = (105.0 + if (workoutDay) 42.0 else 18.0 + recovery * 22.0 + random.centered(12.0)).coerceIn(85.0, 210.0)
            val mealSplits = doubleArrayOf(0.26, 0.34, 0.40)
            val mealNames = arrayOf("Breakfast", "Lunch", "Dinner")
            val mealHours = longArrayOf(11L, 6L, 1L)
            mealSplits.forEachIndexed { mealIndex, split ->
                val meta = mapOf(
                    "foodId" to "synthetic-${mealNames[mealIndex].lowercase()}",
                    "name" to "Synthetic ${mealNames[mealIndex].lowercase()}",
                    "grams" to "350",
                    "meal" to mealNames[mealIndex],
                    "sourceName" to "Synthetic testing"
                )
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_kcal", dailyKcal * split, "kcal", anchor - mealHours[mealIndex] * HOUR_MS, mealIndex, meta)
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_protein", dailyProtein * split, "g", anchor - mealHours[mealIndex] * HOUR_MS, mealIndex, meta)
            }

            // Body: slow energy-balance drift plus measurement noise.
            val maintenanceKcal = 2_250.0 + activeCalories * 0.48
            val dailyWeightDelta = ((dailyKcal - maintenanceKcal) / 7_700.0).coerceIn(-0.12, 0.12)
            weightKg = (weightKg + dailyWeightDelta + random.centered(0.035)).coerceIn(68.0, 105.0)
            bodyFatPct = (bodyFatPct + dailyWeightDelta * 0.12 - activity * 0.004 + random.centered(0.025)).coerceIn(10.0, 32.0)
            val waistCm = (82.0 + (weightKg - 78.0) * 0.72 + bodyFatPct * 0.30 + random.centered(0.7)).coerceIn(72.0, 115.0)
            val bodyTs = anchor - 7L * HOUR_MS
            add(dayIndex, anchor, HealthDomain.BODY, "body_weight_kg", weightKg, "kg", bodyTs)
            add(dayIndex, anchor, HealthDomain.BODY, "body_fat_pct", bodyFatPct, "%", bodyTs)
            if (dayIndex % 7 == 0 || dayIndex == config.days - 1) {
                add(dayIndex, anchor, HealthDomain.BODY, "body_waist_cm", waistCm, "cm", bodyTs)
            }
            if (dayIndex == config.days - 1) {
                add(dayIndex, anchor, HealthDomain.BODY, "body_goal_weight_kg", 78.0, "kg", bodyTs)
            }

            // Mindfulness/mood: sessions lower same-day post-session stress; mood benefits from sleep and activity.
            val stressBefore = (2.2 + stress * 6.0 - recovery * 0.7 + random.centered(0.7)).coerceIn(0.0, 10.0)
            val stressAfter = if (mindfulnessMinutes > 0.0) {
                (stressBefore - 0.8 - mindfulnessMinutes * 0.075 + random.centered(0.35)).coerceIn(0.0, 10.0)
            } else stressBefore
            val mood = (5.2 + recovery * 2.3 + activity * 0.8 - stress * 1.6 + random.centered(0.55)).coerceIn(1.0, 10.0)
            val mindTs = anchor - HOUR_MS
            if (mindfulnessMinutes > 0.0) {
                val mindMeta = mapOf(
                    "mode" to if (dayIndex % 3 == 0) "MEDITATION" else "BREATHING",
                    "stressBefore" to roundedText(stressBefore),
                    "stressAfter" to roundedText(stressAfter)
                )
                add(dayIndex, anchor, HealthDomain.MINDFULNESS, "mindfulness_session_minutes", mindfulnessMinutes, "min", mindTs, metadata = mindMeta)
                add(dayIndex, anchor, HealthDomain.MINDFULNESS, "stress_before", stressBefore, "0-10", mindTs, metadata = mindMeta)
                add(dayIndex, anchor, HealthDomain.MINDFULNESS, "stress_after", stressAfter, "0-10", mindTs, metadata = mindMeta)
            }
            add(dayIndex, anchor, HealthDomain.MINDFULNESS, "mood_score", mood, "0-10", mindTs)

            // Hydration: multiple intake events exercise daily sums and history/calendar screens.
            val hydrationMl = (2_300.0 + activity * 1_250.0 + workoutMinutes * 6.0 + random.centered(220.0)).coerceIn(1_800.0, 4_500.0)
            val drinkSplits = doubleArrayOf(0.23, 0.27, 0.28, 0.22)
            val drinkHours = longArrayOf(12L, 8L, 4L, 1L)
            drinkSplits.forEachIndexed { drinkIndex, split ->
                add(
                    dayIndex, anchor, HealthDomain.HYDRATION, "water_intake_ml", hydrationMl * split, "ml",
                    anchor - drinkHours[drinkIndex] * HOUR_MS, drinkIndex,
                    mapOf("drinkSource" to "Synthetic water", "entryType" to "intake")
                )
            }
            add(dayIndex, anchor, HealthDomain.HYDRATION, "water_total_l", hydrationMl / 1_000.0, "L", anchor - 30L * 60L * 1000L)

            // Clinical markers are intentionally sparse and conservative: monthly-like snapshots, not daily pseudo-labs.
            if (dayIndex % 30 == 0 || dayIndex == config.days - 1) {
                val clinicalTs = anchor - 4L * HOUR_MS
                add(dayIndex, anchor, HealthDomain.CLINICAL, "haemoglobin", (148.0 + recovery * 7.0 + random.centered(4.0)).coerceIn(130.0, 175.0), "g/L", clinicalTs)
                add(dayIndex, anchor, HealthDomain.CLINICAL, "ferritin", (82.0 + recovery * 22.0 + random.centered(12.0)).coerceIn(35.0, 180.0), "ug/L", clinicalTs)
                add(dayIndex, anchor, HealthDomain.CLINICAL, "crp", (0.8 + stress * 2.6 + if (workoutDay) 0.6 else 0.0 + random.centered(0.45)).coerceIn(0.2, 8.0), "mg/L", clinicalTs)
                add(dayIndex, anchor, HealthDomain.CLINICAL, "hba1c", (32.0 + (dailyKcal - 2_300.0) / 700.0 + random.centered(1.2)).coerceIn(28.0, 40.0), "mmol/mol", clinicalTs)
            }

            if (batch.size >= config.batchSize) flush()
        }
        flush()

        return SyntheticGenerationResult(
            requestedDays = config.days,
            generated = generated,
            accepted = accepted,
            rejected = rejected,
            deduplicated = deduplicated
        )
    }

    private fun Random.centered(amplitude: Double): Double = (nextDouble() * 2.0 - 1.0) * amplitude

    private fun roundedText(value: Double): String = (value * 10.0).roundToInt().div(10.0).toString()

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
        const val MAX_DAYS = 730
    }
}
