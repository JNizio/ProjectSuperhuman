package com.projectsuperhuman.next.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

const val SYNTHETIC_DATA_SOURCE = "project-superhuman-synthetic-v1"
const val SYNTHETIC_DATA_SCENARIO = "correlated-lifestyle-cardio-v3"

data class SyntheticGenerationConfig(
    val days: Int = 90,
    val seed: Int = 20260811,
    val batchSize: Int = 2_000
)

data class SyntheticGenerationResult(
    val requestedDays: Int,
    val generated: Int,
    val accepted: Int,
    val rejected: Int,
    val deduplicated: Int
)

private data class SyntheticNutrient(
    val value: Double,
    val unit: String
)

private data class SyntheticMealTemplate(
    val id: String,
    val name: String,
    val meal: String,
    val baseGrams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double,
    val sugar: Double,
    val micros: Map<String, SyntheticNutrient>
)

/**
 * Developer-only realistic history generator.
 *
 * Synthetic observations deliberately use the same [DataIngestionPipeline] as real module/import
 * traffic. Relationships are correlated so dashboards and interpretation code have useful signal
 * to discover. Every row is tagged with [SYNTHETIC_DATA_SOURCE], so the complete generated history
 * can be removed without touching genuine user data.
 *
 * Nutrition is intentionally represented as real-looking diary meals rather than anonymous daily
 * totals. Each breakfast/lunch/dinner carries linked calories, macros, fibre/sugar and a broad
 * micronutrient panel. Values are approximate testing data, never a nutrition claim about a real
 * product.
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
        val syntheticHeightM = 1.78
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
                    "syntheticGenerator" to "app-wide-v3",
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
            val activity = (
                0.48 + recovery * 0.22 - stress * 0.10 +
                    sin(dayIndex / 5.0) * 0.10 + random.centered(0.16)
                ).coerceIn(0.12, 0.98)

            // Sleep: one complete staged night per generated day.
            val sleepMinutes = (385.0 + recovery * 115.0 - stress * 48.0 + random.centered(24.0))
                .coerceIn(300.0, 555.0)
            val awakeMinutes = (52.0 - recovery * 30.0 + stress * 24.0 + random.centered(12.0))
                .coerceIn(8.0, 95.0)
            val sleepWindowMinutes = sleepMinutes + awakeMinutes
            val deepFraction = (0.12 + recovery * 0.10 - stress * 0.018 + random.centered(0.025))
                .coerceIn(0.08, 0.28)
            val remFraction = (0.17 + recovery * 0.075 - stress * 0.012 + random.centered(0.02))
                .coerceIn(0.14, 0.30)
            val deepMinutes = sleepMinutes * deepFraction
            val remMinutes = sleepMinutes * remFraction
            val lightMinutes = (sleepMinutes - deepMinutes - remMinutes).coerceAtLeast(0.0)
            val efficiency = (sleepMinutes / sleepWindowMinutes * 100.0).coerceIn(60.0, 99.0)
            val durationScore = (100.0 - abs(sleepMinutes - 480.0) * 0.28).coerceIn(35.0, 100.0)
            val continuityScore = (100.0 - awakeMinutes * 0.68 - stress * 10.0).coerceIn(30.0, 100.0)
            val stageScore = (
                100.0 - abs(deepFraction - 0.20) * 175.0 - abs(remFraction - 0.23) * 145.0
                ).coerceIn(35.0, 100.0)
            val sleepScore = (
                durationScore * 0.40 + continuityScore * 0.32 + stageScore * 0.28 + random.centered(2.0)
                ).coerceIn(30.0, 99.0)
            val interruptionCount = (1.0 + stress * 4.0 + random.nextDouble() * 2.0).roundToInt()
            val longestInterruptionMinutes = (5.0 + stress * 22.0 + random.centered(4.0))
                .roundToInt().coerceAtLeast(1)
            val sleepEnd = anchor
            val sleepStart = sleepEnd - sleepWindowMinutes.roundToInt() * MINUTE_MS
            val sleepTs = (sleepEnd + 1L).coerceAtMost(now)
            val sleepMeta = mapOf(
                "nightStart" to sleepStart.toString(),
                "nightEnd" to sleepEnd.toString(),
                "sleepBlocks" to "1",
                "interruptions" to interruptionCount.toString(),
                "longestInterruptionMinutes" to longestInterruptionMinutes.toString(),
                "syntheticNight" to "true"
            )

            fun sleepAdd(metric: String, value: Double, unit: String, extra: Map<String, String> = emptyMap()) {
                add(dayIndex, anchor, HealthDomain.SLEEP, metric, value, unit, sleepTs, metadata = sleepMeta + extra)
            }

            val lightFirstMinutes = (lightMinutes * 0.55).roundToInt().coerceAtLeast(1)
            val lightSecondMinutes = (lightMinutes.roundToInt() - lightFirstMinutes).coerceAtLeast(1)
            val deepRounded = deepMinutes.roundToInt().coerceAtLeast(1)
            val remRounded = remMinutes.roundToInt().coerceAtLeast(1)
            val awakeRounded = awakeMinutes.roundToInt().coerceAtLeast(1)
            var segmentCursor = sleepStart
            fun segment(type: String, minutes: Int): String {
                val start = segmentCursor
                val end = start + minutes * MINUTE_MS
                segmentCursor = end
                return "$type,$start,$end"
            }
            val timeline = listOf(
                segment("light", lightFirstMinutes),
                segment("deep", deepRounded),
                segment("light", lightSecondMinutes),
                segment("rem", remRounded),
                segment("awake", awakeRounded)
            ).joinToString(";")

            sleepAdd("sleep_total_minutes", sleepMinutes, "min")
            sleepAdd("sleep_time_minutes", sleepWindowMinutes, "min")
            sleepAdd("sleep_awake_minutes", awakeMinutes, "min")
            sleepAdd("sleep_light_minutes", lightMinutes, "min")
            sleepAdd("sleep_deep_minutes", deepMinutes, "min")
            sleepAdd("sleep_rem_minutes", remMinutes, "min")
            sleepAdd("sleep_efficiency_pct", efficiency, "%")
            sleepAdd("sleep_duration_score", durationScore, "score")
            sleepAdd("sleep_continuity_score", continuityScore, "score")
            sleepAdd("sleep_stage_balance_score", stageScore, "score")
            sleepAdd("sleep_score", sleepScore, "score")
            sleepAdd("sleep_start_epoch_ms", sleepStart.toDouble(), "ms")
            sleepAdd("sleep_end_epoch_ms", sleepEnd.toDouble(), "ms")
            sleepAdd("sleep_interruption_count", interruptionCount.toDouble(), "count")
            sleepAdd("sleep_longest_interruption_minutes", longestInterruptionMinutes.toDouble(), "min")
            sleepAdd("sleep_block_count", 1.0, "count")
            sleepAdd("sleep_stage_timeline", 1.0, "timeline", mapOf("segments" to timeline))

            // Wearable/activity stream. Steps and active calories deliberately co-vary strongly.
            val steps = (
                3_500.0 + activity * 9_000.0 + recovery * 1_200.0 - stress * 650.0 + random.centered(900.0)
                ).coerceIn(1_800.0, 19_000.0)
            val strengthWorkoutDay = dayIndex % 7 in setOf(0, 2, 4, 5) && recovery > 0.28
            val cardioWorkoutDay = dayIndex % 7 in setOf(1, 4, 6) && recovery > 0.25
            // Preserve the established generic activity/strength stream. Cardio sessions are
            // generated independently below so adding Cardio coverage does not distort existing
            // cross-module correlations or legacy synthetic expectations.
            val workoutDay = strengthWorkoutDay
            val workoutMinutes = if (workoutDay) {
                (30.0 + activity * 38.0 + random.centered(8.0)).coerceIn(22.0, 85.0)
            } else 0.0
            val activeCalories = (150.0 + steps * 0.045 + workoutMinutes * 2.8 + random.centered(22.0))
                .coerceIn(150.0, 1_250.0)
            val restingHr = (67.0 + stress * 8.0 - recovery * 10.0 - activity * 2.0 + random.centered(2.2))
                .coerceIn(45.0, 88.0)
            val activityTs = anchor - 2L * HOUR_MS
            add(dayIndex, anchor, HealthDomain.EXERCISE, "steps", steps.roundToInt().toDouble(), "count", activityTs)
            add(dayIndex, anchor, HealthDomain.EXERCISE, "active_calories_kcal", activeCalories, "kcal", activityTs)
            add(dayIndex, anchor, HealthDomain.EXERCISE, "exercise_minutes", workoutMinutes, "min", activityTs)
            add(dayIndex, anchor, HealthDomain.EXERCISE, "resting_heart_rate_bpm", restingHr, "bpm", anchor - 7L * HOUR_MS)
            for (sample in 0 until 8) {
                val sampleHr = (
                    restingHr + 10.0 + activity * 25.0 +
                        if (workoutDay && sample in 5..6) 42.0 else 0.0 + random.centered(8.0)
                    ).coerceIn(50.0, 178.0)
                add(
                    dayIndex, anchor, HealthDomain.EXERCISE, "heart_rate_bpm", sampleHr, "bpm",
                    anchor - (15L - sample * 2L) * HOUR_MS, sample
                )
            }

            // Strength sessions use the same records and metadata consumed by the native Strength UI.
            if (strengthWorkoutDay) {
                val dayToken = anchor / DAY_MS
                val sessionId = "synthetic-strength:${config.seed}:$dayToken"
                val sessionEnd = (anchor - 2L * HOUR_MS).coerceAtMost(now)
                val durationMin = workoutMinutes.roundToInt().coerceAtLeast(22)
                val sessionStart = sessionEnd - durationMin * MINUTE_MS
                val workoutVariant = dayIndex % 3
                val workoutName = when (workoutVariant) {
                    0 -> "Upper Body"
                    1 -> "Lower Body"
                    else -> "Full Body"
                }
                val exercises = when (workoutVariant) {
                    0 -> listOf(
                        Triple("bench_press", "Bench Press", "Chest"),
                        Triple("lat_pulldown", "Lat Pulldown", "Back"),
                        Triple("shoulder_press", "Shoulder Press", "Shoulders"),
                        Triple("cable_row", "Cable Row", "Back")
                    )
                    1 -> listOf(
                        Triple("leg_press", "Leg Press", "Quads"),
                        Triple("romanian_deadlift", "Romanian Deadlift", "Hamstrings"),
                        Triple("leg_curl", "Leg Curl", "Hamstrings"),
                        Triple("calf_raise", "Calf Raise", "Calves")
                    )
                    else -> listOf(
                        Triple("goblet_squat", "Goblet Squat", "Quads"),
                        Triple("bench_press", "Bench Press", "Chest"),
                        Triple("cable_row", "Cable Row", "Back"),
                        Triple("dumbbell_shoulder_press", "Dumbbell Shoulder Press", "Shoulders")
                    )
                }

                var totalVolume = 0.0
                var totalSets = 0
                exercises.forEachIndexed { exerciseIndex, exercise ->
                    val baseLoad = when (exercise.second) {
                        "Bench Press" -> 62.5
                        "Lat Pulldown" -> 55.0
                        "Shoulder Press" -> 35.0
                        "Cable Row" -> 52.5
                        "Leg Press" -> 130.0
                        "Romanian Deadlift" -> 72.5
                        "Leg Curl" -> 42.5
                        "Calf Raise" -> 80.0
                        "Goblet Squat" -> 30.0
                        "Dumbbell Shoulder Press" -> 22.5
                        else -> 40.0
                    }
                    repeat(3) { setIndex ->
                        val reps = (10 - setIndex + random.nextInt(-1, 2)).coerceIn(6, 12)
                        val progression = if (config.days <= 1) 0.0 else dayIndex.toDouble() / (config.days - 1).toDouble()
                        val loadKg = (baseLoad * (0.92 + progression * 0.12) + random.centered(2.0))
                            .coerceAtLeast(5.0)
                        val setVolume = loadKg * reps
                        totalVolume += setVolume
                        totalSets += 1
                        val setTs = sessionStart + (exerciseIndex * 3L + setIndex + 1L) * 4L * MINUTE_MS
                        add(
                            dayIndex, anchor, HealthDomain.EXERCISE, "exercise_set",
                            setVolume, "kg-reps", setTs, 100 + exerciseIndex * 10 + setIndex,
                            mapOf(
                                "exerciseId" to exercise.first,
                                "exerciseName" to exercise.second,
                                "group" to exercise.third,
                                "equipment" to "Synthetic gym",
                                "reps" to reps.toString(),
                                "loadKg" to roundedText(loadKg),
                                "met" to "5.0",
                                "setType" to "Work",
                                "rir" to (1 + setIndex.coerceAtMost(2)).toString(),
                                "rpe" to roundedText((7.0 + setIndex * 0.4).coerceAtMost(9.0)),
                                "superset" to "",
                                "sessionId" to sessionId
                            )
                        )
                    }
                }

                val sessionMeta = mapOf(
                    "sessionId" to sessionId,
                    "startTime" to sessionStart.toString(),
                    "endTime" to sessionEnd.toString(),
                    "durationMin" to durationMin.toString(),
                    "workoutName" to workoutName,
                    "totalSets" to totalSets.toString(),
                    "workingSets" to totalSets.toString(),
                    "volumeKg" to roundedText(totalVolume),
                    "exerciseCount" to exercises.size.toString(),
                    "sessionRpe" to roundedText((6.5 + stress * 1.7 - recovery * 0.8).coerceIn(5.0, 9.0)),
                    "notes" to "Synthetic strength session for developer testing"
                )
                add(
                    dayIndex, anchor, HealthDomain.EXERCISE, "workout_session",
                    totalSets.toDouble(), "sets", sessionEnd, 200, sessionMeta
                )
                add(
                    dayIndex, anchor, HealthDomain.EXERCISE, "workout_volume",
                    totalVolume, "kg-reps", sessionEnd, 201,
                    mapOf("sessionId" to sessionId, "workoutName" to workoutName)
                )
            }

            if (cardioWorkoutDay) {
                val dayToken = anchor / DAY_MS
                val sessionId = "synthetic-cardio:${config.seed}:$dayToken"
                val activityType = when (dayIndex % 4) {
                    0 -> "RUNNING"
                    1 -> "WALKING"
                    2 -> "CYCLING"
                    else -> "RUNNING"
                }
                val workoutType = when (dayIndex % 6) {
                    1 -> "ZONE_2"
                    4 -> "EASY"
                    else -> if (recovery > 0.68) "TEMPO" else "EASY"
                }
                val durationMinutes = (
                    28.0 + activity * 34.0 + recovery * 8.0 + random.centered(6.0)
                    ).coerceIn(24.0, 78.0)
                val durationSeconds = (durationMinutes * 60.0).roundToInt()
                val trainingAgeFraction = if (config.days <= 1) 0.0 else dayIndex.toDouble() / (config.days - 1).toDouble()
                val sessionEnd = (anchor - 3L * HOUR_MS + random.nextInt(-20, 21) * MINUTE_MS).coerceAtMost(now)
                val sessionStart = sessionEnd - durationSeconds * 1000L
                val effort = when (workoutType) {
                    "TEMPO" -> 0.76
                    "ZONE_2" -> 0.56
                    else -> 0.48
                }
                val avgHeartRate = (
                    112.0 + effort * 47.0 + stress * 5.0 - recovery * 4.0 + random.centered(4.0)
                    ).roundToInt().coerceIn(96, 174)
                val maxHeartRate = (
                    avgHeartRate + 14.0 + effort * 15.0 + random.centered(4.0)
                    ).roundToInt().coerceIn(avgHeartRate + 4, 194)
                val minHeartRate = (
                    avgHeartRate - 24.0 + random.centered(4.0)
                    ).roundToInt().coerceIn(72, avgHeartRate - 3)
                val rpe = (
                    2.6 + effort * 5.0 + stress * 0.8 - recovery * 0.5 + random.centered(0.55)
                    ).coerceIn(2.0, 8.8)

                val baseRunningPace = 382.0 - trainingAgeFraction * 34.0
                val baseWalkingPace = 665.0 - trainingAgeFraction * 42.0
                val pacePenalty = stress * 18.0 - recovery * 13.0 + random.centered(10.0)
                val avgPaceSecPerKm = when (activityType) {
                    "RUNNING" -> (baseRunningPace + pacePenalty - if (workoutType == "TEMPO") 24.0 else 0.0)
                        .roundToInt().coerceIn(285, 455)
                    "WALKING" -> (baseWalkingPace + pacePenalty).roundToInt().coerceIn(500, 780)
                    else -> null
                }
                val avgSpeedKmh = when (activityType) {
                    "CYCLING" -> (
                        20.5 + trainingAgeFraction * 3.8 + activity * 3.2 - stress * 1.3 + random.centered(1.5)
                        ).coerceIn(16.0, 32.0)
                    else -> avgPaceSecPerKm?.let { 3600.0 / it }
                }
                val distanceKm = when (activityType) {
                    "CYCLING" -> avgSpeedKmh!! * durationMinutes / 60.0
                    else -> avgPaceSecPerKm?.let { durationSeconds.toDouble() / it } ?: 0.0
                }
                val elevationGainM = when (activityType) {
                    "CYCLING" -> (distanceKm * (6.0 + random.nextDouble() * 9.0)).coerceAtLeast(0.0)
                    "RUNNING", "WALKING" -> (distanceKm * (3.0 + random.nextDouble() * 7.0)).coerceAtLeast(0.0)
                    else -> 0.0
                }
                val cadence = when (activityType) {
                    "RUNNING" -> (160 + activity * 18.0 + random.centered(5.0)).roundToInt().coerceIn(148, 184)
                    "WALKING" -> (103 + activity * 18.0 + random.centered(5.0)).roundToInt().coerceIn(92, 132)
                    "CYCLING" -> (76 + activity * 15.0 + random.centered(5.0)).roundToInt().coerceIn(65, 98)
                    else -> 0
                }

                val zoneShares = when (workoutType) {
                    "ZONE_2" -> listOf(0.12, 0.72, 0.13, 0.02)
                    "TEMPO" -> listOf(0.10, 0.28, 0.46, 0.13)
                    else -> listOf(0.15, 0.58, 0.20, 0.04)
                }
                val zone1Seconds = (durationSeconds * zoneShares[0]).roundToInt()
                val zone2Seconds = (durationSeconds * zoneShares[1]).roundToInt()
                val zone3Seconds = (durationSeconds * zoneShares[2]).roundToInt()
                val zone4Seconds = (durationSeconds * zoneShares[3]).roundToInt()
                val allocated = zone1Seconds + zone2Seconds + zone3Seconds + zone4Seconds
                val zone5Seconds = (durationSeconds - allocated).coerceAtLeast(0)

                val cardioMetadata = buildMap {
                    put("sessionId", sessionId)
                    put("cardioSchemaVersion", "3")
                    put("activityType", activityType)
                    put("activityName", activityType.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                    put("startedAt", sessionStart.toString())
                    put("endedAt", sessionEnd.toString())
                    put("durationSeconds", durationSeconds.toString())
                    put("pausedDurationSeconds", "0")
                    put("cardioSource", "synthetic-cardio")
                    put("workoutType", workoutType)
                    put("distanceKm", roundedText(distanceKm))
                    put("avgHeartRate", avgHeartRate.toString())
                    put("maxHeartRate", maxHeartRate.toString())
                    put("minHeartRate", minHeartRate.toString())
                    put("caloriesKcal", roundedText(durationMinutes * (6.0 + effort * 5.0)))
                    avgPaceSecPerKm?.let {
                        put("avgPaceSecPerKm", it.toString())
                        put("bestPaceSecPerKm", (it * 0.90).roundToInt().toString())
                    }
                    avgSpeedKmh?.let {
                        put("avgSpeedKmh", roundedText(it))
                        put("maxSpeedKmh", roundedText(it * (1.12 + effort * 0.08)))
                    }
                    put("elevationGainM", roundedText(elevationGainM))
                    put("cadence", cadence.toString())
                    put("rpe", roundedText(rpe))
                    put("notes", "Synthetic Cardio session for developer testing")
                    put("zoneSchemeId", "synthetic-5-zone-v1")
                    put("physiologyRevisionId", "synthetic-profile-v1")
                    put("zone1Seconds", zone1Seconds.toString())
                    put("zone2Seconds", zone2Seconds.toString())
                    put("zone3Seconds", zone3Seconds.toString())
                    put("zone4Seconds", zone4Seconds.toString())
                    put("zone5Seconds", zone5Seconds.toString())
                }
                add(
                    dayIndex, anchor, HealthDomain.EXERCISE, "cardio_session",
                    durationMinutes, "min", sessionEnd, 500,
                    cardioMetadata
                )

                // A light-weight synthetic raw stream lets quality/provenance and drill-down UI
                // exercise the same code paths as real sensor-backed sessions.
                val sampleCount = 18
                repeat(sampleCount) { sampleIndex ->
                    val fraction = sampleIndex.toDouble() / (sampleCount - 1).coerceAtLeast(1)
                    val warmupEffect = if (fraction < 0.20) (fraction / 0.20) * 12.0 - 12.0 else 0.0
                    val sampleHr = (
                        avgHeartRate + warmupEffect + sin(sampleIndex / 2.2) * 4.0 + random.centered(2.2)
                        ).roundToInt().coerceIn(70, maxHeartRate)
                    val sampleTs = sessionStart + (durationSeconds * 1000L * sampleIndex / (sampleCount - 1).coerceAtLeast(1))
                    add(
                        dayIndex, anchor, HealthDomain.EXERCISE, "cardio_hr_sample_bpm",
                        sampleHr.toDouble(), "bpm", sampleTs, 600 + sampleIndex,
                        mapOf(
                            "sessionId" to sessionId,
                            "valueClass" to "MEASURED",
                            "quality" to "VALID",
                            "syntheticSensor" to "chest-strap"
                        )
                    )
                    val rrMs = (60_000.0 / sampleHr + random.centered(18.0)).coerceIn(320.0, 1_300.0)
                    add(
                        dayIndex, anchor, HealthDomain.EXERCISE, "cardio_rr_interval_ms",
                        rrMs, "ms", sampleTs + 250L, 700 + sampleIndex,
                        mapOf(
                            "sessionId" to sessionId,
                            "valueClass" to "MEASURED",
                            "quality" to "VALID",
                            "syntheticSensor" to "chest-strap"
                        )
                    )
                }
            }

            if (strengthWorkoutDay) {
                val exerciseIds = listOf("db_press", "cable_row", "biceps_curl")
                var workoutVolume = 0.0
                exerciseIds.forEachIndexed { setIndex, exerciseId ->
                    val reps = 8 + random.nextInt(5)
                    val load = when (exerciseId) {
                        "db_press" -> 22.0 + activity * 10.0
                        "cable_row" -> 35.0 + activity * 18.0
                        else -> 10.0 + activity * 7.0
                    } + random.centered(2.0)
                    val volume = reps * load
                    workoutVolume += volume
                    add(
                        dayIndex, anchor, HealthDomain.EXERCISE, "exercise_set", volume, "kg-reps",
                        activityTs - (exerciseIds.size - setIndex) * 4L * MINUTE_MS,
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

            // Nutrition: exactly three realistic diary meals per day, with linked macros + micros.
            val targetDailyKcal = (
                2_050.0 + activeCalories * 0.72 + if (workoutDay) 180.0 else 0.0 + random.centered(180.0)
                ).coerceIn(1_700.0, 3_650.0)
            val chosenMeals = listOf(
                breakfastMeals[random.nextInt(breakfastMeals.size)],
                lunchMeals[random.nextInt(lunchMeals.size)],
                dinnerMeals[random.nextInt(dinnerMeals.size)]
            )
            val baseTotal = chosenMeals.sumOf { it.kcal }.coerceAtLeast(1.0)
            val dayScale = (targetDailyKcal / baseTotal).coerceIn(0.78, 1.42)
            val dayStart = anchor - positiveModulo(anchor, DAY_MS)
            val mealMinutes = intArrayOf(8 * 60, 13 * 60, 19 * 60)
            var dailyKcal = 0.0

            chosenMeals.forEachIndexed { mealIndex, template ->
                val mealScale = (dayScale * (1.0 + random.centered(0.045))).coerceIn(0.72, 1.50)
                val grams = template.baseGrams * mealScale
                val kcal = template.kcal * mealScale
                val protein = template.protein * mealScale
                val carbs = template.carbs * mealScale
                val fat = template.fat * mealScale
                val fibre = template.fibre * mealScale
                val sugar = template.sugar * mealScale
                dailyKcal += kcal
                val minuteJitter = random.nextInt(-18, 19)
                val mealTs = dayStart + (mealMinutes[mealIndex] + minuteJitter) * MINUTE_MS
                val dayToken = anchor / DAY_MS
                val diaryEntryId = "synthetic-meal:${config.seed}:$dayToken:$mealIndex"
                val commonMeta = mapOf(
                    "diaryEntryId" to diaryEntryId,
                    "foodId" to "synthetic:${template.id}",
                    "name" to template.name,
                    "grams" to roundedText(grams),
                    "meal" to template.meal,
                    "sourceName" to "Synthetic realistic meal model",
                    "nutritionEstimate" to "approximate-testing-data",
                    "protein" to roundedText(protein),
                    "carbs" to roundedText(carbs),
                    "fat" to roundedText(fat),
                    "fibre" to roundedText(fibre),
                    "sugar" to roundedText(sugar),
                    "micronutrientCount" to template.micros.size.toString()
                )
                val ordinalBase = mealIndex * 100
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_kcal", kcal, "kcal", mealTs, ordinalBase, commonMeta)
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_protein", protein, "g", mealTs, ordinalBase + 1, commonMeta)
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_carbs", carbs, "g", mealTs, ordinalBase + 2, commonMeta)
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_fat", fat, "g", mealTs, ordinalBase + 3, commonMeta)
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_fibre", fibre, "g", mealTs, ordinalBase + 4, commonMeta)
                add(dayIndex, anchor, HealthDomain.NUTRITION, "food_sugar", sugar, "g", mealTs, ordinalBase + 5, commonMeta)

                template.micros.entries.forEachIndexed { microIndex, (id, nutrient) ->
                    add(
                        dayIndex = dayIndex,
                        anchor = anchor,
                        domain = HealthDomain.NUTRITION,
                        metric = "food_micro_$id",
                        value = nutrient.value * mealScale,
                        unit = nutrient.unit,
                        timestamp = mealTs,
                        ordinal = ordinalBase + 10 + microIndex,
                        metadata = commonMeta + mapOf(
                            "nutrientId" to id,
                            "nutrientLabel" to nutrientLabel(id),
                            "syntheticPerMealBase" to nutrient.value.toString()
                        )
                    )
                }
            }

            // Body: slow energy-balance drift plus a complete BIA-style reading each day.
            val maintenanceKcal = 2_250.0 + activeCalories * 0.48
            val dailyWeightDelta = ((dailyKcal - maintenanceKcal) / 7_700.0).coerceIn(-0.12, 0.12)
            weightKg = (weightKg + dailyWeightDelta + random.centered(0.035)).coerceIn(68.0, 105.0)
            bodyFatPct = (
                bodyFatPct + dailyWeightDelta * 0.12 - activity * 0.004 + random.centered(0.025)
                ).coerceIn(10.0, 32.0)
            val fatMassKg = weightKg * bodyFatPct / 100.0
            val fatFreeMassKg = weightKg - fatMassKg
            val waterPct = (73.0 * (1.0 - bodyFatPct / 100.0) + random.centered(0.35)).coerceIn(45.0, 70.0)
            val waterL = weightKg * waterPct / 100.0
            val musclePct = (100.0 - bodyFatPct - 3.8 + random.centered(0.18)).coerceIn(45.0, 86.0)
            val muscleMassKg = weightKg * musclePct / 100.0
            val skeletalMusclePct = (musclePct * 0.569 + random.centered(0.12)).coerceIn(25.0, 55.0)
            val skeletalMuscleMassKg = weightKg * skeletalMusclePct / 100.0
            val visceralFat = (bodyFatPct * 0.394 + random.centered(0.35)).coerceIn(1.0, 30.0).roundToInt().toDouble()
            val bmi = weightKg / (syntheticHeightM * syntheticHeightM)
            val ffmi = fatFreeMassKg / (syntheticHeightM * syntheticHeightM)
            val fmi = fatMassKg / (syntheticHeightM * syntheticHeightM)
            val impedanceOhm = (
                520.0 + (bodyFatPct - 18.5) * 4.0 - (waterPct - 58.0) * 5.0 + random.centered(18.0)
                ).coerceIn(300.0, 900.0)
            val waistCm = (
                82.0 + (weightKg - 78.0) * 0.72 + bodyFatPct * 0.30 + random.centered(0.7)
                ).coerceIn(72.0, 115.0)
            val bodyTs = anchor - 7L * HOUR_MS
            val bodyMeta = mapOf("measurementType" to "synthetic-bia", "syntheticScale" to "true")
            add(dayIndex, anchor, HealthDomain.BODY, "body_weight_kg", weightKg, "kg", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_impedance_ohm", impedanceOhm, "ohm", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_fat_pct", bodyFatPct, "%", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_fat_mass_kg", fatMassKg, "kg", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_fat_free_mass_kg", fatFreeMassKg, "kg", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_water_pct", waterPct, "%", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_water_l", waterL, "L", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_muscle_pct", musclePct, "%", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_muscle_mass_kg", muscleMassKg, "kg", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_skeletal_muscle_pct", skeletalMusclePct, "%", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_skeletal_muscle_mass_kg", skeletalMuscleMassKg, "kg", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_visceral_fat_estimate", visceralFat, "index", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_bmi", bmi, "kg/m2", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_ffmi", ffmi, "kg/m2", bodyTs, metadata = bodyMeta)
            add(dayIndex, anchor, HealthDomain.BODY, "body_fmi", fmi, "kg/m2", bodyTs, metadata = bodyMeta)
            if (dayIndex % 7 == 0 || dayIndex == config.days - 1) {
                add(dayIndex, anchor, HealthDomain.BODY, "body_waist_cm", waistCm, "cm", bodyTs)
            }
            if (dayIndex == config.days - 1) {
                add(dayIndex, anchor, HealthDomain.BODY, "body_goal_weight_kg", 78.0, "kg", bodyTs)
            }

            // Mindfulness/mood: sessions improve same-session stress, while mood tracks recovery/activity.
            val stressBefore = (2.2 + stress * 6.0 - recovery * 0.7 + random.centered(0.7)).coerceIn(0.0, 10.0)
            val stressAfter = if (mindfulnessMinutes > 0.0) {
                (stressBefore - 1.0 - mindfulnessMinutes * 0.075 + random.centered(0.25)).coerceIn(0.0, 10.0)
            } else stressBefore
            val mood = (5.2 + recovery * 2.3 + activity * 0.8 - stress * 1.6 + random.centered(0.55))
                .coerceIn(1.0, 10.0)
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

            // Hydration: multiple intake events exercise sums and calendar/history screens.
            val hydrationMl = (
                2_300.0 + activity * 1_250.0 + workoutMinutes * 6.0 + random.centered(220.0)
                ).coerceIn(1_800.0, 4_500.0)
            val drinkSplits = doubleArrayOf(0.23, 0.27, 0.28, 0.22)
            val drinkHours = longArrayOf(12L, 8L, 4L, 1L)
            drinkSplits.forEachIndexed { drinkIndex, split ->
                add(
                    dayIndex, anchor, HealthDomain.HYDRATION, "water_intake_ml", hydrationMl * split, "ml",
                    anchor - drinkHours[drinkIndex] * HOUR_MS, drinkIndex,
                    mapOf("drinkSource" to "Synthetic water", "entryType" to "intake")
                )
            }
            add(dayIndex, anchor, HealthDomain.HYDRATION, "water_total_l", hydrationMl / 1_000.0, "L", anchor - 30L * MINUTE_MS)

            // Clinical markers stay sparse: monthly-like snapshots rather than daily pseudo-labs.
            if (dayIndex % 30 == 0 || dayIndex == config.days - 1) {
                val clinicalTs = anchor - 4L * HOUR_MS
                add(
                    dayIndex, anchor, HealthDomain.CLINICAL, "haemoglobin",
                    (148.0 + recovery * 7.0 + random.centered(4.0)).coerceIn(130.0, 175.0), "g/L", clinicalTs
                )
                add(
                    dayIndex, anchor, HealthDomain.CLINICAL, "ferritin",
                    (82.0 + recovery * 22.0 + random.centered(12.0)).coerceIn(35.0, 180.0), "ug/L", clinicalTs
                )
                add(
                    dayIndex, anchor, HealthDomain.CLINICAL, "crp",
                    (0.8 + stress * 2.6 + if (workoutDay) 0.6 else 0.0 + random.centered(0.45)).coerceIn(0.2, 8.0),
                    "mg/L", clinicalTs
                )
                add(
                    dayIndex, anchor, HealthDomain.CLINICAL, "hba1c",
                    (32.0 + (dailyKcal - 2_300.0) / 700.0 + random.centered(1.2)).coerceIn(28.0, 40.0),
                    "mmol/mol", clinicalTs
                )
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

    private fun positiveModulo(value: Long, modulus: Long): Long {
        val raw = value % modulus
        return if (raw >= 0L) raw else raw + modulus
    }

    private fun nutrientLabel(id: String): String = when (id) {
        "calcium" -> "Calcium"
        "iron" -> "Iron"
        "magnesium" -> "Magnesium"
        "phosphorus" -> "Phosphorus"
        "potassium" -> "Potassium"
        "selenium" -> "Selenium"
        "sodium" -> "Sodium"
        "zinc" -> "Zinc"
        "vitamin_a" -> "Vitamin A"
        "vitamin_b1" -> "Vitamin B1"
        "vitamin_b2" -> "Vitamin B2"
        "vitamin_b6" -> "Vitamin B6"
        "vitamin_b12" -> "Vitamin B12"
        "vitamin_c" -> "Vitamin C"
        "vitamin_d" -> "Vitamin D"
        "vitamin_e" -> "Vitamin E"
        "vitamin_k" -> "Vitamin K"
        "folate" -> "Folate (B9)"
        "choline" -> "Choline"
        else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
        const val MINUTE_MS = 60_000L
        const val MAX_DAYS = 1_825

        fun micro(value: Double, unit: String) = SyntheticNutrient(value, unit)

        val breakfastMeals = listOf(
            SyntheticMealTemplate(
                id = "skyr-oats-berries",
                name = "Skyr, oats & berry bowl",
                meal = "Breakfast",
                baseGrams = 465.0,
                kcal = 590.0,
                protein = 41.0,
                carbs = 79.0,
                fat = 13.0,
                fibre = 11.0,
                sugar = 27.0,
                micros = mapOf(
                    "calcium" to micro(420.0, "mg"),
                    "iron" to micro(4.2, "mg"),
                    "magnesium" to micro(150.0, "mg"),
                    "phosphorus" to micro(610.0, "mg"),
                    "potassium" to micro(960.0, "mg"),
                    "zinc" to micro(4.1, "mg"),
                    "vitamin_b2" to micro(0.65, "mg"),
                    "vitamin_b12" to micro(1.5, "µg"),
                    "vitamin_c" to micro(32.0, "mg"),
                    "folate" to micro(72.0, "µg")
                )
            ),
            SyntheticMealTemplate(
                id = "eggs-avocado-sourdough",
                name = "Eggs, avocado & sourdough",
                meal = "Breakfast",
                baseGrams = 390.0,
                kcal = 640.0,
                protein = 30.0,
                carbs = 55.0,
                fat = 34.0,
                fibre = 11.0,
                sugar = 5.0,
                micros = mapOf(
                    "calcium" to micro(170.0, "mg"),
                    "iron" to micro(5.6, "mg"),
                    "magnesium" to micro(115.0, "mg"),
                    "phosphorus" to micro(540.0, "mg"),
                    "potassium" to micro(1_120.0, "mg"),
                    "selenium" to micro(48.0, "µg"),
                    "zinc" to micro(4.3, "mg"),
                    "vitamin_b12" to micro(2.2, "µg"),
                    "vitamin_d" to micro(2.5, "µg"),
                    "folate" to micro(165.0, "µg"),
                    "choline" to micro(365.0, "mg")
                )
            ),
            SyntheticMealTemplate(
                id = "greek-yoghurt-banana-granola",
                name = "Greek yoghurt, banana & granola",
                meal = "Breakfast",
                baseGrams = 450.0,
                kcal = 610.0,
                protein = 32.0,
                carbs = 83.0,
                fat = 18.0,
                fibre = 9.0,
                sugar = 34.0,
                micros = mapOf(
                    "calcium" to micro(390.0, "mg"),
                    "iron" to micro(3.0, "mg"),
                    "magnesium" to micro(135.0, "mg"),
                    "phosphorus" to micro(510.0, "mg"),
                    "potassium" to micro(1_080.0, "mg"),
                    "zinc" to micro(3.6, "mg"),
                    "vitamin_b2" to micro(0.62, "mg"),
                    "vitamin_b12" to micro(1.4, "µg"),
                    "vitamin_b6" to micro(0.55, "mg"),
                    "folate" to micro(68.0, "µg")
                )
            )
        )

        val lunchMeals = listOf(
            SyntheticMealTemplate(
                id = "chicken-rice-broccoli",
                name = "Chicken, rice & broccoli bowl",
                meal = "Lunch",
                baseGrams = 620.0,
                kcal = 760.0,
                protein = 62.0,
                carbs = 93.0,
                fat = 16.0,
                fibre = 10.0,
                sugar = 8.0,
                micros = mapOf(
                    "calcium" to micro(145.0, "mg"),
                    "iron" to micro(4.8, "mg"),
                    "magnesium" to micro(145.0, "mg"),
                    "phosphorus" to micro(690.0, "mg"),
                    "potassium" to micro(1_310.0, "mg"),
                    "selenium" to micro(62.0, "µg"),
                    "zinc" to micro(5.2, "mg"),
                    "vitamin_b6" to micro(1.5, "mg"),
                    "vitamin_c" to micro(96.0, "mg"),
                    "folate" to micro(160.0, "µg")
                )
            ),
            SyntheticMealTemplate(
                id = "lentil-feta-wholegrain",
                name = "Lentil, feta & wholegrain bowl",
                meal = "Lunch",
                baseGrams = 590.0,
                kcal = 730.0,
                protein = 34.0,
                carbs = 101.0,
                fat = 22.0,
                fibre = 23.0,
                sugar = 13.0,
                micros = mapOf(
                    "calcium" to micro(360.0, "mg"),
                    "iron" to micro(9.2, "mg"),
                    "magnesium" to micro(205.0, "mg"),
                    "phosphorus" to micro(670.0, "mg"),
                    "potassium" to micro(1_520.0, "mg"),
                    "zinc" to micro(5.8, "mg"),
                    "vitamin_b1" to micro(0.72, "mg"),
                    "vitamin_b6" to micro(0.78, "mg"),
                    "vitamin_c" to micro(42.0, "mg"),
                    "folate" to micro(390.0, "µg")
                )
            ),
            SyntheticMealTemplate(
                id = "tuna-potato-salad",
                name = "Tuna & potato salad",
                meal = "Lunch",
                baseGrams = 610.0,
                kcal = 690.0,
                protein = 52.0,
                carbs = 72.0,
                fat = 21.0,
                fibre = 11.0,
                sugar = 10.0,
                micros = mapOf(
                    "calcium" to micro(130.0, "mg"),
                    "iron" to micro(5.0, "mg"),
                    "magnesium" to micro(155.0, "mg"),
                    "phosphorus" to micro(650.0, "mg"),
                    "potassium" to micro(1_750.0, "mg"),
                    "selenium" to micro(88.0, "µg"),
                    "zinc" to micro(3.5, "mg"),
                    "vitamin_b12" to micro(4.5, "µg"),
                    "vitamin_c" to micro(46.0, "mg"),
                    "folate" to micro(145.0, "µg")
                )
            )
        )

        val dinnerMeals = listOf(
            SyntheticMealTemplate(
                id = "salmon-potatoes-spinach",
                name = "Salmon, potatoes & spinach",
                meal = "Dinner",
                baseGrams = 650.0,
                kcal = 820.0,
                protein = 55.0,
                carbs = 75.0,
                fat = 32.0,
                fibre = 12.0,
                sugar = 9.0,
                micros = mapOf(
                    "calcium" to micro(220.0, "mg"),
                    "iron" to micro(6.0, "mg"),
                    "magnesium" to micro(190.0, "mg"),
                    "phosphorus" to micro(790.0, "mg"),
                    "potassium" to micro(2_050.0, "mg"),
                    "selenium" to micro(75.0, "µg"),
                    "zinc" to micro(4.2, "mg"),
                    "vitamin_b12" to micro(6.2, "µg"),
                    "vitamin_c" to micro(55.0, "mg"),
                    "vitamin_d" to micro(16.0, "µg"),
                    "folate" to micro(240.0, "µg")
                )
            ),
            SyntheticMealTemplate(
                id = "turkey-tomato-pasta",
                name = "Turkey tomato pasta",
                meal = "Dinner",
                baseGrams = 670.0,
                kcal = 850.0,
                protein = 58.0,
                carbs = 105.0,
                fat = 21.0,
                fibre = 14.0,
                sugar = 16.0,
                micros = mapOf(
                    "calcium" to micro(210.0, "mg"),
                    "iron" to micro(6.8, "mg"),
                    "magnesium" to micro(170.0, "mg"),
                    "phosphorus" to micro(720.0, "mg"),
                    "potassium" to micro(1_620.0, "mg"),
                    "selenium" to micro(66.0, "µg"),
                    "zinc" to micro(6.4, "mg"),
                    "vitamin_b6" to micro(1.25, "mg"),
                    "vitamin_b12" to micro(2.1, "µg"),
                    "vitamin_c" to micro(52.0, "mg"),
                    "folate" to micro(180.0, "µg")
                )
            ),
            SyntheticMealTemplate(
                id = "chicken-curry-rice",
                name = "Chicken vegetable curry & rice",
                meal = "Dinner",
                baseGrams = 700.0,
                kcal = 880.0,
                protein = 52.0,
                carbs = 112.0,
                fat = 25.0,
                fibre = 15.0,
                sugar = 17.0,
                micros = mapOf(
                    "calcium" to micro(175.0, "mg"),
                    "iron" to micro(6.2, "mg"),
                    "magnesium" to micro(180.0, "mg"),
                    "phosphorus" to micro(680.0, "mg"),
                    "potassium" to micro(1_680.0, "mg"),
                    "selenium" to micro(58.0, "µg"),
                    "zinc" to micro(5.1, "mg"),
                    "vitamin_a" to micro(620.0, "µg"),
                    "vitamin_b6" to micro(1.35, "mg"),
                    "vitamin_c" to micro(78.0, "mg"),
                    "folate" to micro(210.0, "µg")
                )
            )
        )
    }
}
