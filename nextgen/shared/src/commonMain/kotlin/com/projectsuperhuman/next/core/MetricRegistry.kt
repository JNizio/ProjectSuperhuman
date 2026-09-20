package com.projectsuperhuman.next.core

/**
 * Defines how a metric is stored and later aggregated/interpreted.
 *
 * The registry is intentionally extensible: unregistered metrics remain valid data.
 * This is important for Clinical, where new laboratory markers can arrive before a
 * catalogue update. Registered metrics gain canonical IDs/units and aggregation rules.
 */
enum class MetricAggregation {
    LAST,
    SUM,
    AVERAGE,
    MIN_MAX_AVG,
    NONE
}

data class MetricDefinition(
    val id: String,
    val domain: HealthDomain,
    val canonicalUnit: String,
    val aliases: Set<String> = emptySet(),
    val aggregation: MetricAggregation = MetricAggregation.LAST,
    val derived: Boolean = false,
    val minAccepted: Double? = null,
    val maxAccepted: Double? = null
)

interface MetricRegistry {
    fun definition(domain: HealthDomain, metricOrAlias: String): MetricDefinition?
    fun definitions(domain: HealthDomain? = null): List<MetricDefinition>
}

/**
 * Canonical metrics currently emitted by native modules/importers.
 * Add new module metrics here instead of scattering storage semantics through UI code.
 *
 * Aliases are compatibility-only. New ingestion is normalised to [MetricDefinition.id], while
 * ModuleParityService maps legacy stored aliases to that same ID on read without rewriting history.
 */
object CoreMetricRegistry : MetricRegistry {
    private val items = listOf(
        // Clinical remains intentionally open-ended. Lab marker IDs are dynamic `clinical.*` values
        // and therefore are preserved even when no static registry entry exists.

        // Blood pressure. Native capture is still deferred, but defining the canonical vocabulary now
        // means legacy/manual import can migrate without inventing another naming scheme later.
        MetricDefinition(
            "blood_pressure_systolic_mmhg",
            HealthDomain.BLOOD_PRESSURE,
            "mmHg",
            aliases = setOf("bp_systolic", "systolic", "systolic_mmhg"),
            aggregation = MetricAggregation.AVERAGE,
            minAccepted = 40.0,
            maxAccepted = 300.0
        ),
        MetricDefinition(
            "blood_pressure_diastolic_mmhg",
            HealthDomain.BLOOD_PRESSURE,
            "mmHg",
            aliases = setOf("bp_diastolic", "diastolic", "diastolic_mmhg"),
            aggregation = MetricAggregation.AVERAGE,
            minAccepted = 20.0,
            maxAccepted = 200.0
        ),
        MetricDefinition(
            "blood_pressure_pulse_bpm",
            HealthDomain.BLOOD_PRESSURE,
            "bpm",
            aliases = setOf("bp_pulse", "pulse", "pulse_bpm"),
            aggregation = MetricAggregation.AVERAGE,
            minAccepted = 20.0,
            maxAccepted = 260.0
        ),

        // Sleep / Health Connect
        MetricDefinition("sleep_score", HealthDomain.SLEEP, "score", aggregation = MetricAggregation.LAST, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition(
            "sleep_total_minutes",
            HealthDomain.SLEEP,
            "min",
            aliases = setOf("sleep_time_minutes"),
            aggregation = MetricAggregation.AVERAGE,
            minAccepted = 0.0,
            maxAccepted = 1440.0
        ),
        MetricDefinition("sleep_awake_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("sleep_light_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("sleep_deep_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("sleep_rem_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("sleep_efficiency_pct", HealthDomain.SLEEP, "%", aggregation = MetricAggregation.AVERAGE, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition("sleep_duration_score", HealthDomain.SLEEP, "score", aggregation = MetricAggregation.AVERAGE, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition("sleep_continuity_score", HealthDomain.SLEEP, "score", aggregation = MetricAggregation.AVERAGE, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition("sleep_stage_balance_score", HealthDomain.SLEEP, "score", aggregation = MetricAggregation.AVERAGE, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition("sleep_start_epoch_ms", HealthDomain.SLEEP, "ms", aggregation = MetricAggregation.LAST),
        MetricDefinition("sleep_end_epoch_ms", HealthDomain.SLEEP, "ms", aggregation = MetricAggregation.LAST),
        MetricDefinition("sleep_interruption_count", HealthDomain.SLEEP, "count", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0),
        MetricDefinition("sleep_longest_interruption_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0),
        MetricDefinition("sleep_block_count", HealthDomain.SLEEP, "count", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0),
        MetricDefinition("sleep_stage_timeline", HealthDomain.SLEEP, "timeline", aggregation = MetricAggregation.NONE),
        MetricDefinition("sleep_sessions_imported", HealthDomain.SLEEP, "count", aggregation = MetricAggregation.LAST, minAccepted = 0.0),
        MetricDefinition("sleep_blocks_imported", HealthDomain.SLEEP, "count", aggregation = MetricAggregation.LAST, minAccepted = 0.0),

        // Nutrition. Food diary nutrients are first-class rows so later experiments can correlate
        // meal composition and timing against sleep, mood, body and performance outcomes.
        MetricDefinition("food_kcal", HealthDomain.NUTRITION, "kcal", aliases = setOf("calories_kcal", "nutrition_kcal"), aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("food_protein", HealthDomain.NUTRITION, "g", aliases = setOf("protein_g"), aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("food_carbs", HealthDomain.NUTRITION, "g", aliases = setOf("carbs_g", "carbohydrate_g"), aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("food_fat", HealthDomain.NUTRITION, "g", aliases = setOf("fat_g"), aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("food_fibre", HealthDomain.NUTRITION, "g", aliases = setOf("fibre_g", "fiber_g"), aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("food_sugar", HealthDomain.NUTRITION, "g", aliases = setOf("sugar_g"), aggregation = MetricAggregation.SUM, minAccepted = 0.0),

        // Body / scale. These are stable metrics emitted by the native smart-scale path.
        MetricDefinition("body_weight_kg", HealthDomain.BODY, "kg", aliases = setOf("weight_kg"), aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 400.0),
        MetricDefinition("body_fat_pct", HealthDomain.BODY, "%", aliases = setOf("body_fat_percent"), aggregation = MetricAggregation.AVERAGE, minAccepted = 1.0, maxAccepted = 75.0),
        MetricDefinition("body_fat_mass_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 250.0),
        MetricDefinition("body_fat_free_mass_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 300.0),
        MetricDefinition("body_water_pct", HealthDomain.BODY, "%", aliases = setOf("body_water_percent"), aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 80.0),
        MetricDefinition("body_water_l", HealthDomain.BODY, "L", aggregation = MetricAggregation.AVERAGE, minAccepted = 5.0, maxAccepted = 200.0),
        MetricDefinition("body_muscle_pct", HealthDomain.BODY, "%", aggregation = MetricAggregation.AVERAGE, minAccepted = 10.0, maxAccepted = 95.0),
        MetricDefinition("body_muscle_mass_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 5.0, maxAccepted = 300.0),
        MetricDefinition("body_skeletal_muscle_pct", HealthDomain.BODY, "%", aggregation = MetricAggregation.AVERAGE, minAccepted = 5.0, maxAccepted = 70.0),
        MetricDefinition("body_skeletal_muscle_mass_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 3.0, maxAccepted = 200.0),
        MetricDefinition("body_visceral_fat_estimate", HealthDomain.BODY, "index", aggregation = MetricAggregation.AVERAGE, minAccepted = 1.0, maxAccepted = 30.0),
        MetricDefinition("body_bmi", HealthDomain.BODY, "kg/m2", aggregation = MetricAggregation.AVERAGE, minAccepted = 8.0, maxAccepted = 80.0),
        MetricDefinition("body_ffmi", HealthDomain.BODY, "kg/m2", aggregation = MetricAggregation.AVERAGE, minAccepted = 5.0, maxAccepted = 50.0),
        MetricDefinition("body_fmi", HealthDomain.BODY, "kg/m2", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 50.0),
        MetricDefinition("body_impedance_ohm", HealthDomain.BODY, "ohm", aggregation = MetricAggregation.AVERAGE, minAccepted = 100.0, maxAccepted = 2_000.0),
        MetricDefinition("body_waist_cm", HealthDomain.BODY, "cm", aliases = setOf("waist_cm"), aggregation = MetricAggregation.AVERAGE, minAccepted = 30.0, maxAccepted = 300.0),
        MetricDefinition("body_goal_weight_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.LAST, minAccepted = 20.0, maxAccepted = 400.0),

        // Manual body-temperature capture. Site/method remains observation metadata because
        // values from different sites should be preserved rather than silently adjusted.
        MetricDefinition(
            "body_temperature_celsius",
            HealthDomain.BODY,
            "°C",
            aliases = setOf("body_temperature_c", "temperature_celsius", "temperature_c"),
            aggregation = MetricAggregation.MIN_MAX_AVG,
            minAccepted = 20.0,
            maxAccepted = 50.0
        ),

        // Blood oxygen arrives from Samsung Health / Health Connect and is currently grouped with
        // body vitals. These IDs match the native importer so no historical row rewrite is needed.
        MetricDefinition("blood_oxygen_percent", HealthDomain.BODY, "%", aliases = setOf("spo2_pct", "spo2_percent"), aggregation = MetricAggregation.AVERAGE, minAccepted = 40.0, maxAccepted = 100.0),
        MetricDefinition("blood_oxygen_avg_percent", HealthDomain.BODY, "%", aliases = setOf("spo2_avg_pct"), aggregation = MetricAggregation.AVERAGE, minAccepted = 40.0, maxAccepted = 100.0),
        MetricDefinition("blood_oxygen_min_percent", HealthDomain.BODY, "%", aliases = setOf("spo2_min_pct"), aggregation = MetricAggregation.MIN_MAX_AVG, minAccepted = 40.0, maxAccepted = 100.0),
        MetricDefinition("blood_oxygen_max_percent", HealthDomain.BODY, "%", aliases = setOf("spo2_max_pct"), aggregation = MetricAggregation.MIN_MAX_AVG, minAccepted = 40.0, maxAccepted = 100.0),

        // Exercise / wearable-style streams
        MetricDefinition("cardio_session", HealthDomain.EXERCISE, "min", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("exercise_set", HealthDomain.EXERCISE, "kg-reps", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("workout_session", HealthDomain.EXERCISE, "sets", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("workout_volume", HealthDomain.EXERCISE, "kg-reps", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("steps", HealthDomain.EXERCISE, "count", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 200_000.0),
        MetricDefinition(
            "calories_burned_active_kcal",
            HealthDomain.EXERCISE,
            "kcal",
            aliases = setOf("active_calories_kcal"),
            aggregation = MetricAggregation.SUM,
            minAccepted = 0.0,
            maxAccepted = 10_000.0
        ),
        MetricDefinition("calories_burned_total_kcal", HealthDomain.EXERCISE, "kcal", aggregation = MetricAggregation.LAST, minAccepted = 0.0, maxAccepted = 15_000.0),
        MetricDefinition("exercise_minutes", HealthDomain.EXERCISE, "min", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("resting_heart_rate_bpm", HealthDomain.EXERCISE, "bpm", aliases = setOf("resting_hr_bpm"), aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 250.0),
        MetricDefinition("heart_rate_bpm", HealthDomain.EXERCISE, "bpm", aliases = setOf("hr_bpm"), aggregation = MetricAggregation.MIN_MAX_AVG, minAccepted = 20.0, maxAccepted = 260.0),
        MetricDefinition("heart_rate_avg_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 260.0),
        MetricDefinition("heart_rate_min_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.MIN_MAX_AVG, minAccepted = 20.0, maxAccepted = 260.0),
        MetricDefinition("heart_rate_max_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.MIN_MAX_AVG, minAccepted = 20.0, maxAccepted = 260.0),

        // Cardio n-of-1 evidence. Raw streams deliberately retain values outside the normal
        // analytic range; quality metadata determines whether a sample is included in derivations.
        MetricDefinition("cardio_hr_sample_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.MIN_MAX_AVG),
        MetricDefinition("cardio_rr_interval_ms", HealthDomain.EXERCISE, "ms", aggregation = MetricAggregation.MIN_MAX_AVG),
        MetricDefinition("cardio_physiology_profile_revision", HealthDomain.EXERCISE, "revision", aggregation = MetricAggregation.NONE),
        MetricDefinition("cardio_hrmax_candidate_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.LAST, derived = true, minAccepted = 20.0, maxAccepted = 260.0),
        MetricDefinition("heart_rate_variability_rmssd_ms", HealthDomain.EXERCISE, "ms", aliases = setOf("hrv_rmssd_ms"), aggregation = MetricAggregation.AVERAGE, derived = true, minAccepted = 1.0, maxAccepted = 500.0),
        MetricDefinition("cardio_fitness_efficiency_delta_pct", HealthDomain.EXERCISE, "%", aggregation = MetricAggregation.LAST, derived = true),
        MetricDefinition("cardio_training_readiness_score", HealthDomain.EXERCISE, "score", aggregation = MetricAggregation.LAST, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition("cardio_chronic_training_load", HealthDomain.EXERCISE, "load", aggregation = MetricAggregation.LAST, derived = true, minAccepted = 0.0),
        MetricDefinition("cardio_acute_training_load", HealthDomain.EXERCISE, "load", aggregation = MetricAggregation.LAST, derived = true, minAccepted = 0.0),
        MetricDefinition("cardio_training_stress_balance", HealthDomain.EXERCISE, "load", aggregation = MetricAggregation.LAST, derived = true),

        // Hydration. Intake events are signed because corrections subtract from a day.
        MetricDefinition("water_intake_ml", HealthDomain.HYDRATION, "ml", aliases = setOf("hydration_intake_ml"), aggregation = MetricAggregation.SUM, minAccepted = -10_000.0, maxAccepted = 10_000.0),
        MetricDefinition("water_total_l", HealthDomain.HYDRATION, "L", aliases = setOf("hydration_total_l"), aggregation = MetricAggregation.LAST, minAccepted = 0.0, maxAccepted = 10.0),
        MetricDefinition("hydration_goal_ml", HealthDomain.HYDRATION, "ml", aliases = setOf("water_goal_ml"), aggregation = MetricAggregation.LAST, minAccepted = 500.0, maxAccepted = 10_000.0),

        // Mindfulness / self-report
        MetricDefinition("mindfulness_session_minutes", HealthDomain.MINDFULNESS, "min", aliases = setOf("meditation_minutes"), aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 240.0),
        MetricDefinition("stress_before", HealthDomain.MINDFULNESS, "0-10", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 10.0),
        MetricDefinition("stress_after", HealthDomain.MINDFULNESS, "0-10", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 10.0),
        MetricDefinition("mood_score", HealthDomain.MINDFULNESS, "0-10", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 10.0)
    )

    private val byDomainAndName: Map<Pair<HealthDomain, String>, MetricDefinition> = buildMap {
        items.forEach { definition ->
            put(definition.domain to definition.id.lowercase(), definition)
            definition.aliases.forEach { alias ->
                put(definition.domain to alias.lowercase(), definition)
            }
        }
    }

    override fun definition(domain: HealthDomain, metricOrAlias: String): MetricDefinition? =
        byDomainAndName[domain to metricOrAlias.trim().lowercase()]

    override fun definitions(domain: HealthDomain?): List<MetricDefinition> =
        if (domain == null) items else items.filter { it.domain == domain }
}
