package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledFoodMicronutrientMatcherTest {
    private fun bundled(
        id: String,
        name: String,
        kcal: Double,
        protein: Double,
        fat: Double,
        basisUnit: FoodUnit = FoodUnit.G,
        density: Double? = null
    ) = NativeFood(
        id = id, name = name, country = "Both", kcal = kcal, protein = protein,
        carbs = 10.0, fat = fat, fibre = 0.0, sugar = 0.0, unit = "100 g",
        source = "Local reference", nutritionApproximate = true,
        basisUnit = basisUnit, densityGPerMl = density
    )

    private fun usda(
        name: String,
        kcal: Double,
        protein: Double,
        fat: Double
    ) = NativeFood(
        id = "usda:999", name = name, originalName = name, country = "US",
        kcal = kcal, protein = protein, carbs = 10.0, fat = fat,
        fibre = 0.0, sugar = 0.0, unit = "100 g",
        source = "USDA SR Legacy", sourceRecordId = "999",
        micronutrients = listOf("calcium", "iron", "magnesium", "potassium",
            "vitamin_c", "vitamin_b6").associateWith { nutrient ->
                NativeNutrient(nutrient, nutrient, 10.0, "mg",
                    NutrientEvidenceKind.SOURCE_REPORTED, "USDA SR Legacy", "999")
            }
    )

    @Test
    fun matchesGenericAppleWithProvenanceAndPreservesMacros() {
        val local = bundled("f63", "Apple", 52.0, 0.3, 0.2)
        val enriched = BundledFoodMicronutrientMatcher.enrich(
            local, listOf(usda("Apples, raw, with skin", 52.0, 0.3, 0.2))
        )
        assertEquals(6, enriched.micronutrients.size)
        assertEquals(52.0, enriched.kcal)
        assertEquals("f63", enriched.id)
        assertEquals("999", enriched.micronutrients.getValue("iron").sourceRecordId)
        assertEquals(NutrientEvidenceKind.DERIVED, enriched.micronutrients.getValue("iron").evidenceKind)
    }

    @Test
    fun refusesJuiceAsWholeFruitAndWrongPreparation() {
        val local = bundled("f63", "Apple", 52.0, 0.3, 0.2)
        assertTrue(BundledFoodMicronutrientMatcher.enrich(
            local, listOf(usda("Apple juice, raw", 46.0, 0.3, 0.2))
        ).micronutrients.isEmpty())
        val boiled = bundled("f53", "Potato, boiled", 87.0, 1.9, 0.1)
        assertTrue(BundledFoodMicronutrientMatcher.enrich(
            boiled, listOf(usda("Potatoes, raw", 78.0, 2.0, 0.1))
        ).micronutrients.isEmpty())
    }

    @Test
    fun preservesUnknownForUnmatchedOrMixedDish() {
        assertTrue(BundledFoodMicronutrientMatcher.enrich(
            bundled("f63", "Apple", 52.0, 0.3, 0.2), emptyList()
        ).micronutrients.isEmpty())
        assertTrue(BundledFoodMicronutrientMatcher.enrich(
            bundled("f99", "Pierogi ruskie, cooked", 200.0, 5.0, 8.0),
            listOf(usda("Pierogi ruskie, cooked", 200.0, 5.0, 8.0))
        ).micronutrients.isEmpty())
    }

    @Test
    fun convertsUsdaPerHundredGramsToMillilitreBasis() {
        val milk = bundled("f6", "Milk, semi-skimmed 2%", 50.0, 3.4, 2.0,
            basisUnit = FoodUnit.ML, density = 1.03)
        val enriched = BundledFoodMicronutrientMatcher.enrich(
            milk, listOf(usda("Milk, reduced fat, fluid, 2% milkfat", 50.0, 3.3, 2.0))
        )
        assertEquals(10.3, enriched.micronutrients.getValue("calcium").valuePer100, 0.0001)
    }
}
