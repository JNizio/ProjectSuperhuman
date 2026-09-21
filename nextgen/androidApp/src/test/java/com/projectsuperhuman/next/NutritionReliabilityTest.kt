package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NutritionReliabilityTest {
    @Test
    fun portionScalingUsesPer100Basis() {
        assertEquals(187.5, NutritionMath.scalePer100(250.0, 75.0), 0.0001)
    }

    @Test
    fun recipeServingMathIsStable() {
        assertEquals(600.0, NutritionMath.recipePerServing(2400.0, 4.0), 0.0001)
        assertEquals(0.0, NutritionMath.recipePerServing(2400.0, 0.0), 0.0001)
    }

    @Test
    fun barcodeChecksumAcceptsCommonValidCodes() {
        assertTrue(NutritionMath.isValidBarcode("4006381333931"))
        assertTrue(NutritionMath.isValidBarcode("96385074"))
        assertFalse(NutritionMath.isValidBarcode("4006381333932"))
        assertFalse(NutritionMath.isValidBarcode("1234"))
    }

    @Test
    fun nutrientCoverageDoesNotTreatMissingAsKnownZero() {
        assertEquals(0.5, NutritionMath.coverage(2, 4), 0.0001)
        assertFalse(NutritionMath.hasEnoughCoverage(2, 4, 0.75))
        assertTrue(NutritionMath.hasEnoughCoverage(3, 4, 0.75))
    }
}
