package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionIntegrityTest {
    @Test
    fun suppressesImpossibleCapriSunFatLikeSourceError() {
        val result = NutritionIntegrity.sanitizeMacros(
            kcal = 37.0,
            protein = 0.0,
            carbs = 9.0,
            fat = 9.0,
            proteinKnown = true,
            carbsKnown = true,
            fatKnown = true
        )

        assertTrue(result.proteinKnown)
        assertTrue(result.carbsKnown)
        assertFalse(result.fatKnown)
        assertTrue(result.warning?.contains("fat") == true)
    }

    @Test
    fun missingEnergyDoesNotInvalidateOtherwiseReportedMacros() {
        val result = NutritionIntegrity.sanitizeMacros(
            kcal = 0.0,
            protein = 3.0,
            carbs = 12.0,
            fat = 4.0,
            proteinKnown = true,
            carbsKnown = true,
            fatKnown = true,
            kcalKnown = false
        )

        assertTrue(result.proteinKnown)
        assertTrue(result.carbsKnown)
        assertTrue(result.fatKnown)
        assertNull(result.warning)
    }

    @Test
    fun keepsPlausibleKitKatMacros() {
        val result = NutritionIntegrity.sanitizeMacros(
            kcal = 518.0,
            protein = 7.0,
            carbs = 65.0,
            fat = 26.0,
            proteinKnown = true,
            carbsKnown = true,
            fatKnown = true
        )

        assertTrue(result.proteinKnown)
        assertTrue(result.carbsKnown)
        assertTrue(result.fatKnown)
        assertNull(result.warning)
    }

    @Test
    fun flagsSugarAboveCarbsAndSaturatedFatAboveFat() {
        val report = NutritionIntegrity.validateFoodValues(
            basisAmount = 100.0,
            basisUnit = FoodUnit.G,
            kcal = 120.0,
            kcalKnown = true,
            protein = 2.0,
            proteinKnown = true,
            carbs = 10.0,
            carbsKnown = true,
            fat = 2.0,
            fatKnown = true,
            saturatedFat = 3.0,
            saturatedFatKnown = true,
            fibre = 1.0,
            fibreKnown = true,
            sugar = 15.0,
            sugarKnown = true,
            saltG = 0.0,
            saltKnown = false,
            sodiumMg = 0.0,
            sodiumKnown = false
        )
        assertTrue(report.conflicted)
        assertTrue(report.warnings.any { it.contains("Sugars") })
        assertTrue(report.warnings.any { it.contains("Saturated fat") })
    }

    @Test
    fun derivesSodiumFromSaltButMarksItAsDerivedAtTheEvidenceLayer() {
        val report = NutritionIntegrity.validateFoodValues(
            basisAmount = 100.0,
            basisUnit = FoodUnit.G,
            kcal = 100.0,
            kcalKnown = true,
            protein = 5.0,
            proteinKnown = true,
            carbs = 15.0,
            carbsKnown = true,
            fat = 2.0,
            fatKnown = true,
            saturatedFat = 0.0,
            saturatedFatKnown = false,
            fibre = 1.0,
            fibreKnown = true,
            sugar = 2.0,
            sugarKnown = true,
            saltG = 1.0,
            saltKnown = true,
            sodiumMg = 0.0,
            sodiumKnown = false
        )
        assertTrue(report.derivedSodiumMg != null)
        assertTrue(kotlin.math.abs((report.derivedSodiumMg ?: 0.0) - 400.0) < 0.0001)
        assertFalse(report.conflicted)
    }

    @Test
    fun detectsSaltSodiumConflictWithoutOverwritingEitherValue() {
        val report = NutritionIntegrity.validateFoodValues(
            basisAmount = 100.0,
            basisUnit = FoodUnit.G,
            kcal = 100.0,
            kcalKnown = true,
            protein = 5.0,
            proteinKnown = true,
            carbs = 15.0,
            carbsKnown = true,
            fat = 2.0,
            fatKnown = true,
            saturatedFat = 0.0,
            saturatedFatKnown = false,
            fibre = 1.0,
            fibreKnown = true,
            sugar = 2.0,
            sugarKnown = true,
            saltG = 1.0,
            saltKnown = true,
            sodiumMg = 900.0,
            sodiumKnown = true
        )
        assertTrue(report.conflicted)
        assertTrue(report.warnings.any { it.contains("Salt and sodium") })
        assertNull(report.derivedSodiumMg)
        assertNull(report.derivedSaltG)
    }

}
