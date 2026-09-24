package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LocalFoodPass2DeterminismTest {
    private fun row(
        id: String,
        protein: Double = 1.0,
        tags: Set<FoodTag> = setOf(FoodTag.PLANT, FoodTag.FRUIT)
    ) = LocalFoodDatabasePass2Auditor.Row(
        id = id,
        name = "Test food $id",
        kcal = 50.0,
        protein = protein,
        carbs = 10.0,
        fat = 0.5,
        fibre = 2.0,
        sugar = 5.0,
        saturatedFat = 0.1,
        sodiumMg = 2.0,
        saltG = NutritionIntegrity.sodiumMgToSaltG(2.0),
        proteinKnown = true,
        carbsKnown = true,
        fatKnown = true,
        fibreKnown = true,
        sugarKnown = true,
        saturatedFatKnown = true,
        sodiumKnown = true,
        saltKnown = true,
        unit = "100 g",
        source = "USDA Foundation Foods test",
        sourceRecordId = id.removePrefix("usda:"),
        micronutrientsJson = """{"calcium":{"value":10.0,"unit":"mg"}}""",
        unknownMicronutrientsJson = """["magnesium"]""",
        tags = tags,
        preparationState = FoodPreparationState.RAW,
        servingQuantity = null,
        servingUnit = "",
        servingLabel = ""
    )

    @Test
    fun fingerprintIsIndependentOfSqliteRowOrder() {
        val a = row("usda:1")
        val b = row("usda:2", protein = 2.0)

        val first = LocalFoodDatabasePass2Auditor.databaseFingerprint(listOf(a, b))
        val second = LocalFoodDatabasePass2Auditor.databaseFingerprint(listOf(b, a))

        assertEquals(first, second)
    }

    @Test
    fun fingerprintChangesWhenPersistedNutritionOrTaxonomyChanges() {
        val original = row("usda:1")
        val nutritionChanged = original.copy(protein = 1.1)
        val taxonomyChanged = original.copy(tags = setOf(FoodTag.PLANT, FoodTag.VEGETABLE))

        val baseline = LocalFoodDatabasePass2Auditor.databaseFingerprint(listOf(original))

        assertNotEquals(baseline, LocalFoodDatabasePass2Auditor.databaseFingerprint(listOf(nutritionChanged)))
        assertNotEquals(baseline, LocalFoodDatabasePass2Auditor.databaseFingerprint(listOf(taxonomyChanged)))
    }
}
