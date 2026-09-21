package com.projectsuperhuman.next

internal data class RecipeIngredientEvidence(
    val food: CanonicalFoodRecord,
    val originalAmount: Double,
    val originalUnit: FoodUnit,
    val factor: Double,
    val grams: Double?,
    val millilitres: Double?
)

internal data class RecipeNutrientTotal(
    val nutrientId: String,
    val value: Double,
    val unit: String,
    val knownIngredients: Int,
    val totalIngredients: Int
) {
    val complete: Boolean get() = totalIngredients > 0 && knownIngredients == totalIngredients
}

internal data class RecipeEvidence(
    val id: String,
    val name: String,
    val ingredients: List<RecipeIngredientEvidence>,
    val servings: Double,
    val finalCookedWeightG: Double? = null
)

/**
 * Ingredient-resolved recipe math.
 *
 * Missing nutrient evidence stays missing. A partial total is explicitly marked by coverage instead
 * of treating unknown ingredients as zero. Cooked yield changes portioning, not the conserved
 * nutrient total of the ingredient evidence.
 */
internal object RecipeEvidenceCalculator {
    fun ingredient(food: NativeFood, amount: Double, unit: FoodUnit): RecipeIngredientEvidence? {
        val enriched = FoodEvidenceEngine.enrich(food)
        val conversion = FoodUnitSystem.convert(enriched, amount, unit) ?: return null
        return RecipeIngredientEvidence(
            food = FoodEvidenceEngine.canonicalize(enriched),
            originalAmount = amount,
            originalUnit = unit,
            factor = conversion.factor,
            grams = conversion.grams,
            millilitres = conversion.millilitres
        )
    }

    fun nutrientTotal(recipe: RecipeEvidence, nutrientId: String): RecipeNutrientTotal? {
        if (recipe.ingredients.isEmpty()) return null
        val known = recipe.ingredients.mapNotNull { ingredient ->
            ingredient.food.nutrients[nutrientId]?.let { nutrient ->
                nutrient to ingredient.factor
            }
        }
        if (known.isEmpty()) return null

        val unit = known.first().first.unit
        val compatible = known.filter { it.first.unit == unit }
        if (compatible.isEmpty()) return null
        return RecipeNutrientTotal(
            nutrientId = nutrientId,
            value = compatible.sumOf { (nutrient, factor) -> nutrient.valuePerBasis * factor },
            unit = unit,
            knownIngredients = compatible.size,
            totalIngredients = recipe.ingredients.size
        )
    }

    fun nutrientPerServing(recipe: RecipeEvidence, nutrientId: String): RecipeNutrientTotal? {
        val total = nutrientTotal(recipe, nutrientId) ?: return null
        val servings = recipe.servings.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return total.copy(value = total.value / servings)
    }

    fun nutrientForCookedWeight(
        recipe: RecipeEvidence,
        nutrientId: String,
        consumedCookedWeightG: Double
    ): RecipeNutrientTotal? {
        val total = nutrientTotal(recipe, nutrientId) ?: return null
        val finalWeight = recipe.finalCookedWeightG?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (!consumedCookedWeightG.isFinite() || consumedCookedWeightG <= 0.0) return null
        return total.copy(value = total.value * (consumedCookedWeightG / finalWeight))
    }
}
