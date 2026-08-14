package com.projectsuperhuman.next.trudy.nutrition

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.MetricRegistry
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds
import com.projectsuperhuman.next.trudy.TrudyToolOperation

/**
 * Read-only semantic bindings to metrics already emitted by Project Superhuman.
 * This catalogue does not persist data and does not create a dashboard-only schema.
 */
object TrudyNutritionMetricCatalog {
    val energyAndMacros: List<TrudyNutritionMetricBinding> = listOf(
        binding("energy_intake", HealthDomain.NUTRITION, "food_kcal", "kcal", "Logged dietary energy"),
        binding("protein_intake", HealthDomain.NUTRITION, "food_protein", "g", "Logged protein"),
        binding("carbohydrate_intake", HealthDomain.NUTRITION, "food_carbs", "g", "Logged carbohydrate"),
        binding("fat_intake", HealthDomain.NUTRITION, "food_fat", "g", "Logged total fat"),
        binding("fibre_intake", HealthDomain.NUTRITION, "food_fibre", "g", "Logged dietary fibre"),
        binding("sugar_intake", HealthDomain.NUTRITION, "food_sugar", "g", "Logged sugar; not necessarily free sugar")
    )

    /**
     * These IDs exactly mirror NativeDataHub.saveFood's existing `food_<nutrient>_<unit>` rows.
     * They are dynamic Nutrition metrics today, so registryRequired is false until the central
     * registry explicitly adopts them. Absence of a row means unknown, not zero intake.
     */
    val micronutrients: List<TrudyNutritionMetricBinding> = listOf(
        micro("calcium", "mg"),
        micro("chloride", "mg"),
        micro("copper", "mg"),
        micro("iron", "mg"),
        micro("iodine", "µg"),
        micro("magnesium", "mg"),
        micro("manganese", "mg"),
        micro("phosphorus", "mg"),
        micro("potassium", "mg"),
        micro("selenium", "µg"),
        micro("sodium", "mg"),
        micro("zinc", "mg"),
        micro("vitamin_a", "µg"),
        micro("vitamin_b1", "mg"),
        micro("vitamin_b2", "mg"),
        micro("niacin", "mg"),
        micro("pantothenic_acid", "mg"),
        micro("vitamin_b6", "mg"),
        micro("folate", "µg"),
        micro("vitamin_b12", "µg"),
        micro("biotin", "µg"),
        micro("vitamin_c", "mg"),
        micro("vitamin_d", "µg"),
        micro("vitamin_e", "mg"),
        micro("vitamin_k", "µg"),
        micro("choline", "mg")
    )

    val hydration: List<TrudyNutritionMetricBinding> = listOf(
        binding("water_intake", HealthDomain.HYDRATION, "water_intake_ml", "ml", "Signed water intake events"),
        binding("water_daily_total", HealthDomain.HYDRATION, "water_total_l", "L", "Latest derived daily water total"),
        binding("hydration_goal", HealthDomain.HYDRATION, "hydration_goal_ml", "ml", "User-configured guide, not a universal requirement")
    )

    val body: List<TrudyNutritionMetricBinding> = listOf(
        binding("body_weight", HealthDomain.BODY, "body_weight_kg", "kg", "Body-weight trend"),
        binding("body_fat", HealthDomain.BODY, "body_fat_pct", "%", "Scale-estimated body-fat percentage"),
        binding("body_fat_mass", HealthDomain.BODY, "body_fat_mass_kg", "kg", "Scale-estimated fat mass"),
        binding("fat_free_mass", HealthDomain.BODY, "body_fat_free_mass_kg", "kg", "Scale-estimated fat-free mass"),
        binding("body_water", HealthDomain.BODY, listOf("body_water_pct", "body_water_l"), "mixed", "Scale-estimated body water"),
        binding("muscle_mass", HealthDomain.BODY, listOf("body_muscle_mass_kg", "body_skeletal_muscle_mass_kg"), "kg", "Scale-estimated muscle mass"),
        binding("body_impedance", HealthDomain.BODY, "body_impedance_ohm", "ohm", "Raw impedance context for scale estimates"),
        binding("waist_measurement", HealthDomain.BODY, "body_waist_cm", "cm", "Waist measurement trend")
    )

    val relatedSignals: List<TrudyNutritionMetricBinding> = listOf(
        binding("active_energy", HealthDomain.EXERCISE, "calories_burned_active_kcal", "kcal", "Wearable-estimated active energy"),
        binding("total_energy_expenditure", HealthDomain.EXERCISE, "calories_burned_total_kcal", "kcal", "Wearable-estimated total energy expenditure"),
        binding("exercise_duration", HealthDomain.EXERCISE, "exercise_minutes", "min", "Exercise context"),
        binding("steps_activity", HealthDomain.EXERCISE, "steps", "count", "Daily activity context"),
        binding("environment_temperature", HealthDomain.ENVIRONMENT, EnvironmentalMetricIds.TEMPERATURE_C, "°C", "Heat context for hydration", registryRequired = false),
        binding("environment_humidity", HealthDomain.ENVIRONMENT, EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, "%", "Humidity context for sweating and comfort", registryRequired = false)
    )

    val all: List<TrudyNutritionMetricBinding> =
        energyAndMacros + micronutrients + hydration + body + relatedSignals

    fun byId(id: String): TrudyNutritionMetricBinding? = all.firstOrNull { it.id == id }

    fun forNutrient(nutrientId: String): TrudyNutritionMetricBinding? =
        micronutrients.firstOrNull { it.id == "nutrient_$nutrientId" }

    fun forIntent(intent: NutritionQuestionIntent): List<TrudyNutritionMetricBinding> = when (intent) {
        NutritionQuestionIntent.PROTEIN_ADEQUACY -> select("protein_intake", "energy_intake", "body_weight", "exercise_duration")
        NutritionQuestionIntent.HYDRATION_ADEQUACY -> hydration + select("exercise_duration", "environment_temperature", "environment_humidity")
        NutritionQuestionIntent.OVERNIGHT_WEIGHT_CHANGE -> select("body_weight", "body_water", "carbohydrate_intake", "nutrient_sodium")
        NutritionQuestionIntent.BODY_COMPOSITION_CHANGE -> body
        NutritionQuestionIntent.ENERGY_BALANCE -> select("energy_intake", "active_energy", "total_energy_expenditure", "body_weight")
        NutritionQuestionIntent.FOOD_AND_ENERGY -> select("energy_intake", "carbohydrate_intake", "protein_intake", "fat_intake", "fibre_intake", "exercise_duration")
        NutritionQuestionIntent.DIETARY_NUTRIENT_GAP -> energyAndMacros + micronutrients
        NutritionQuestionIntent.EXERCISE_NUTRITION -> energyAndMacros + hydration + select("exercise_duration", "active_energy", "environment_temperature")
        NutritionQuestionIntent.GENERAL_NUTRITION -> energyAndMacros + hydration + select("body_weight")
    }.distinctBy { it.id }

    fun unresolvedRegistryBindings(registry: MetricRegistry = CoreMetricRegistry): List<TrudyNutritionMetricBinding> =
        all.filter { binding ->
            binding.registryRequired && binding.metricIds.any { registry.definition(binding.domain, it) == null }
        }

    private fun select(vararg ids: String): List<TrudyNutritionMetricBinding> = ids.mapNotNull(::byId)

    private fun micro(nutrientId: String, unit: String): TrudyNutritionMetricBinding {
        val suffix = if (unit == "µg") "ug" else unit.lowercase()
        return binding(
            id = "nutrient_$nutrientId",
            domain = HealthDomain.NUTRITION,
            metricIds = listOf("food_${nutrientId}_$suffix"),
            unit = unit,
            purpose = "Logged $nutrientId from food records that supplied this value; missing values remain unknown",
            registryRequired = false
        )
    }

    private fun binding(
        id: String,
        domain: HealthDomain,
        metricId: String,
        unit: String,
        purpose: String,
        registryRequired: Boolean = true
    ) = binding(id, domain, listOf(metricId), unit, purpose, registryRequired)

    private fun binding(
        id: String,
        domain: HealthDomain,
        metricIds: List<String>,
        unit: String,
        purpose: String,
        registryRequired: Boolean = true
    ) = TrudyNutritionMetricBinding(id, domain, metricIds, unit, purpose, registryRequired = registryRequired)
}

/** Uses only the existing typed Trudy tool contract; no Nutrition-specific storage path is added. */
class TrudyNutritionToolPlanner {
    fun plan(intent: NutritionQuestionIntent): List<TrudyToolOperation> {
        if (intent == NutritionQuestionIntent.DIETARY_NUTRIENT_GAP) {
            return listOf(
                TrudyToolOperation.GetDomainHistory(HealthDomain.NUTRITION, limit = 250),
                TrudyToolOperation.GetDataQuality(HealthDomain.NUTRITION)
            )
        }

        val bindings = TrudyNutritionMetricCatalog.forIntent(intent)
        return buildList {
            bindings.forEach { binding ->
                binding.metricIds.forEach { metricId ->
                    add(TrudyToolOperation.GetMetricHistory(binding.domain, metricId, binding.historyLimit))
                }
            }
            bindings.map { it.domain }.distinct().forEach { domain ->
                add(TrudyToolOperation.GetDataQuality(domain))
            }
        }.distinct()
    }
}
