package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodUnitSystemTest {
    private fun food(
        unit: String,
        basisUnit: FoodUnit,
        density: Double? = null,
        servingQuantity: Double? = null,
        servingUnit: FoodUnit? = null,
        productQuantity: Double? = null,
        productUnit: FoodUnit? = null
    ) = NativeFood(
        id = "test",
        name = "Test food",
        country = "",
        kcal = 100.0,
        protein = 10.0,
        carbs = 10.0,
        fat = 2.0,
        fibre = 1.0,
        sugar = 1.0,
        unit = unit,
        source = "test",
        basisAmount = 100.0,
        basisUnit = basisUnit,
        densityGPerMl = density,
        servingQuantity = servingQuantity,
        servingQuantityUnit = servingUnit,
        servingLabel = if (servingQuantity != null) "1 serving" else "",
        productQuantity = productQuantity,
        productQuantityUnit = productUnit
    )

    @Test
    fun oliveOilVolumeUsesDensityAgainstGramBasis() {
        val oil = food("100 g", FoodUnit.G, density = 0.91)
        val converted = FoodUnitSystem.convert(oil, 15.0, FoodUnit.ML)
        assertNotNull(converted)
        assertEquals(13.65, converted.grams!!, 0.001)
        assertEquals(0.1365, converted.factor, 0.0001)
    }

    @Test
    fun massAndVolumeAreNotInterchangedWithoutDensity() {
        val drink = food("100 ml", FoodUnit.ML)
        assertNull(FoodUnitSystem.convert(drink, 100.0, FoodUnit.G))
        assertNotNull(FoodUnitSystem.convert(drink, 250.0, FoodUnit.ML))
        assertFalse(FoodUnit.G in FoodUnitSystem.availableUnits(drink))
        assertTrue(FoodUnit.L in FoodUnitSystem.availableUnits(drink))
    }

    @Test
    fun servingAndPackageResolveThroughOffStyleMetadata() {
        val drink = food(
            unit = "100 ml",
            basisUnit = FoodUnit.ML,
            servingQuantity = 330.0,
            servingUnit = FoodUnit.ML,
            productQuantity = 500.0,
            productUnit = FoodUnit.ML
        )
        val serving = FoodUnitSystem.convert(drink, 1.0, FoodUnit.SERVING)
        val pack = FoodUnitSystem.convert(drink, 1.0, FoodUnit.PACKAGE)
        assertNotNull(serving)
        assertNotNull(pack)
        assertEquals(3.3, serving.factor, 0.0001)
        assertEquals(5.0, pack.factor, 0.0001)
        assertTrue(FoodUnit.SERVING in FoodUnitSystem.availableUnits(drink))
        assertTrue(FoodUnit.PACKAGE in FoodUnitSystem.availableUnits(drink))
    }

    @Test
    fun kitchenVolumeConversionsArePhysical() {
        val drink = food("100 ml", FoodUnit.ML)
        val tablespoon = FoodUnitSystem.convert(drink, 1.0, FoodUnit.TBSP)
        val cup = FoodUnitSystem.convert(drink, 1.0, FoodUnit.CUP)
        assertNotNull(tablespoon)
        assertNotNull(cup)
        assertEquals(14.78676478125, tablespoon.millilitres!!, 0.000001)
        assertEquals(236.5882365, cup.millilitres!!, 0.000001)
    }

    @Test
    fun switchingUnitsCanPreserveOneNutritionBasis() {
        val oil = food("100 g", FoodUnit.G, density = 0.91)
        val ml = FoodUnitSystem.amountForBasis(oil, FoodUnit.ML)
        assertNotNull(ml)
        assertEquals(109.8901, ml, 0.001)
        val conversion = FoodUnitSystem.convert(oil, ml, FoodUnit.ML)
        assertNotNull(conversion)
        assertEquals(1.0, conversion.factor, 0.0001)
    }
}
