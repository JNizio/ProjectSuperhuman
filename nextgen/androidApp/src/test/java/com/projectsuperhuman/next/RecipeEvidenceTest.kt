package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecipeEvidenceTest {
    private fun ingredientFood(
        id: String,
        kcal: Double,
        protein: Double,
        magnesiumMg: Double? = null
    ) = NativeFood(
        id = id,
        name = id,
        country = "",
        kcal = kcal,
        protein = protein,
        carbs = 10.0,
        fat = 2.0,
        fibre = 1.0,
        sugar = 1.0,
        unit = "100 g",
        source = "USDA Foundation Foods",
        micronutrients = magnesiumMg?.let {
            mapOf(
                "magnesium" to NativeNutrient(
                    id = "magnesium",
                    label = "Magnesium",
                    valuePer100 = it,
                    unit = "mg",
                    evidenceKind = NutrientEvidenceKind.LABORATORY_REFERENCE,
                    source = "USDA Foundation Foods",
                    sourceRecordId = id
                )
            )
        } ?: emptyMap()
    )

    @Test
    fun recipeYieldUsesFinalCookedWeightForConsumedPortion() {
        val chicken = RecipeEvidenceCalculator.ingredient(
            ingredientFood("chicken", 165.0, 31.0),
            500.0,
            FoodUnit.G
        )
        val rice = RecipeEvidenceCalculator.ingredient(
            ingredientFood("rice", 360.0, 7.0),
            250.0,
            FoodUnit.G
        )
        assertNotNull(chicken)
        assertNotNull(rice)

        val recipe = RecipeEvidence(
            id = "meal-prep",
            name = "Chicken rice",
            ingredients = listOf(chicken, rice),
            servings = 3.0,
            finalCookedWeightG = 970.0
        )

        val total = RecipeEvidenceCalculator.nutrientTotal(recipe, "energy_kcal")
        val portion = RecipeEvidenceCalculator.nutrientForCookedWeight(recipe, "energy_kcal", 323.0)
        assertNotNull(total)
        assertNotNull(portion)
        assertEquals(1725.0, total.value, 0.001)
        assertEquals(total.value * 323.0 / 970.0, portion.value, 0.001)
        assertTrue(total.complete)
    }

    @Test
    fun recipeUnknownMicronutrientsRemainPartialNotZero() {
        val a = RecipeEvidenceCalculator.ingredient(
            ingredientFood("a", 100.0, 10.0, magnesiumMg = 50.0),
            100.0,
            FoodUnit.G
        )
        val b = RecipeEvidenceCalculator.ingredient(
            ingredientFood("b", 100.0, 10.0, magnesiumMg = null),
            100.0,
            FoodUnit.G
        )
        assertNotNull(a)
        assertNotNull(b)

        val recipe = RecipeEvidence(
            id = "partial",
            name = "Partial evidence",
            ingredients = listOf(a, b),
            servings = 2.0
        )
        val magnesium = RecipeEvidenceCalculator.nutrientTotal(recipe, "magnesium")
        assertNotNull(magnesium)
        assertEquals(50.0, magnesium.value, 0.001)
        assertEquals(1, magnesium.knownIngredients)
        assertEquals(2, magnesium.totalIngredients)
        assertFalse(magnesium.complete)
    }
}
