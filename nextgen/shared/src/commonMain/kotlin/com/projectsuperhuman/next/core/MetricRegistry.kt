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

        // Exercise
        MetricDefinition("exercise_set", HealthDomain.EXERCISE, "kg-reps", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("workout_session", HealthDomain.EXERCISE, "sets", aggregation = MetricAggregation.SUM, minAccepted = 0.0),
        MetricDefinition("workout_volume", HealthDomain.EXERCISE, "kg-reps", aggregation = MetricAggregation.SUM, minAccepted = 0.0)
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
