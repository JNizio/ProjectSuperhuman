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
 */
object CoreMetricRegistry : MetricRegistry {
    private val items = listOf(
        // Sleep / Health Connect
        MetricDefinition("sleep_score", HealthDomain.SLEEP, "score", aggregation = MetricAggregation.LAST, derived = true, minAccepted = 0.0, maxAccepted = 100.0),
        MetricDefinition("sleep_total_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("sleep_time_minutes", HealthDomain.SLEEP, "min", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 1440.0),
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

        // Nutrition
        MetricDefinition("food_kcal", HealthDomain.NUTRITION, "kcal", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("food_protein", HealthDomain.NUTRITION, "g", aggregation = MetricAggregation.SUM, minAccepted = 0.0),

        // Body / scale. These are stable metrics emitted by the native smart-scale path.
        MetricDefinition("body_weight_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 400.0),
        MetricDefinition("body_fat_pct", HealthDomain.BODY, "%", aggregation = MetricAggregation.AVERAGE, minAccepted = 1.0, maxAccepted = 75.0),
        MetricDefinition("body_fat_mass_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 250.0),
        MetricDefinition("body_fat_free_mass_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.AVERAGE, minAccepted = 0.0, maxAccepted = 300.0),
        MetricDefinition("body_water_pct", HealthDomain.BODY, "%", aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 80.0),
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
        MetricDefinition("body_waist_cm", HealthDomain.BODY, "cm", aggregation = MetricAggregation.AVERAGE, minAccepted = 30.0, maxAccepted = 300.0),
        MetricDefinition("body_goal_weight_kg", HealthDomain.BODY, "kg", aggregation = MetricAggregation.LAST, minAccepted = 20.0, maxAccepted = 400.0),

        // Exercise / future wearable-style streams
        MetricDefinition("exercise_set", HealthDomain.EXERCISE, "kg-reps", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("workout_session", HealthDomain.EXERCISE, "sets", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("workout_volume", HealthDomain.EXERCISE, "kg-reps", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("steps", HealthDomain.EXERCISE, "count", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 200_000.0),
        MetricDefinition("active_calories_kcal", HealthDomain.EXERCISE, "kcal", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 10_000.0),
        MetricDefinition("exercise_minutes", HealthDomain.EXERCISE, "min", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 1440.0),
        MetricDefinition("resting_heart_rate_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.AVERAGE, minAccepted = 20.0, maxAccepted = 250.0),
        MetricDefinition("heart_rate_bpm", HealthDomain.EXERCISE, "bpm", aggregation = MetricAggregation.MIN_MAX_AVG, minAccepted = 20.0, maxAccepted = 260.0),

        // Hydration. Intake events are signed because corrections subtract from a day.
        MetricDefinition("water_intake_ml", HealthDomain.HYDRATION, "ml", aggregation = MetricAggregation.SUM, minAccepted = -10_000.0, maxAccepted = 10_000.0),
        MetricDefinition("water_total_l", HealthDomain.HYDRATION, "L", aggregation = MetricAggregation.LAST, minAccepted = 0.0, maxAccepted = 10.0),
        MetricDefinition("hydration_goal_ml", HealthDomain.HYDRATION, "ml", aggregation = MetricAggregation.LAST, minAccepted = 500.0, maxAccepted = 10_000.0),

        // Mindfulness / self-report
        MetricDefinition("mindfulness_session_minutes", HealthDomain.MINDFULNESS, "min", aggregation = MetricAggregation.SUM, minAccepted = 0.0, maxAccepted = 240.0),
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
