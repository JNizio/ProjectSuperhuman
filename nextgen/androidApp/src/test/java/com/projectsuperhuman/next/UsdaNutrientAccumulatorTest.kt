package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UsdaNutrientAccumulatorTest {
    @Test
    fun respectsEnergyPriorityAndStrictMajorNutrientSemantics() {
        val out = LargeLocalFoodDatabase.NutrientAccumulator()

        out.accept(1008, "Energy", "kcal", 80.0)
        out.accept(2047, "Metabolizable energy (Atwater General Factors)", "kcal", 100.0)
        out.accept(2048, "Metabolizable energy (Atwater Specific Factors)", "kcal", 110.0)
        out.accept(1062, "Energy", "kJ", 9999.0)

        assertEquals(110.0, out.kcal)

        out.accept(1003, "Protein", "g", 12.5)
        out.accept(1005, "Carbohydrate, by difference", "g", 20.0)
        out.accept(1004, "Total lipid (fat)", "g", 5.0)

        assertEquals(12.5, out.protein)
        assertEquals(20.0, out.carbs)
        assertEquals(5.0, out.fat)

        // Correct ID with the wrong semantic meaning must not overwrite carbohydrate.
        out.accept(1005, "Carbohydrate, available", "g", 99.0)
        assertEquals(20.0, out.carbs)
    }

    @Test
    fun convertsCanonicalMassUnitsWithoutCollapsingDistinctNutrients() {
        val out = LargeLocalFoodDatabase.NutrientAccumulator()

        out.accept(1087, "Calcium, Ca", "g", 0.12)
        out.accept(1103, "Selenium, Se", "mg", 0.055)
        out.accept(1177, "Folate, total", "µg", 100.0)
        out.accept(1190, "Folate, DFE", "µg", 160.0)
        out.accept(1235, "Sugars, added", "g", 4.5)

        assertEquals(120.0, out.micros["calcium"]?.valuePer100)
        assertEquals("mg", out.micros["calcium"]?.unit)
        assertEquals(55.0, out.micros["selenium"]?.valuePer100)
        assertEquals("µg", out.micros["selenium"]?.unit)
        assertEquals(100.0, out.micros["folate"]?.valuePer100)
        assertEquals(160.0, out.micros["folate_dfe"]?.valuePer100)
        assertEquals(4.5, out.micros["added_sugars"]?.valuePer100)
    }

    @Test
    fun rejectsMismatchedStableIdsAndIncompatibleVitaminMeasures() {
        val out = LargeLocalFoodDatabase.NutrientAccumulator()

        out.accept(1092, "Sodium, Na", "mg", 500.0)
        assertNull(out.sodiumMg)
        assertNull(out.micros["sodium"])

        out.accept(1104, "Vitamin A, IU", "IU", 5000.0)
        assertNull(out.micros["vitamin_a"])

        out.accept(1110, "Vitamin D (D2 + D3), International Units", "IU", 400.0)
        assertNull(out.micros["vitamin_d"])

        out.accept(1093, "Sodium, Na", "mg", 120.0)
        assertEquals(120.0, out.sodiumMg)
        assertEquals(120.0, out.micros["sodium"]?.valuePer100)
    }
    @Test
    fun absentSourceNutrientsRemainUnknownRatherThanSyntheticZero() {
        val out = LargeLocalFoodDatabase.NutrientAccumulator()

        out.accept(1008, "Energy", "kcal", 100.0)
        out.accept(1003, "Protein", "g", 10.0)
        out.accept(1005, "Carbohydrate, by difference", "g", 20.0)
        out.accept(1004, "Total lipid (fat)", "g", 5.0)

        assertNull(out.fibre)
        assertNull(out.sugar)
        assertNull(out.saturatedFat)
        assertNull(out.sodiumMg)
        assertFalse("sodium" in out.micros)
        assertFalse("calcium" in out.micros)
    }
}
