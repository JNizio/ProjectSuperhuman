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
}
