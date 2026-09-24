package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals

class NutritionMealStructureTest {
    @Test
    fun defaultMealsKeepCanonicalOrderWhileCustomMealsRemainSeparate() {
        assertEquals(
            listOf("Breakfast", "Lunch", "Dinner", "Snack", "Second breakfast", "Pre-workout"),
            n2MealOrder(
                listOf(
                    "Second breakfast",
                    "Dinner",
                    "Breakfast",
                    "Pre-workout",
                    "Lunch",
                    "Snack"
                )
            )
        )
    }

    @Test
    fun customMealNamesAreDeduplicatedCaseInsensitively() {
        assertEquals(
            listOf("Breakfast", "pre-workout"),
            n2MealOrder(listOf("pre-workout", "Breakfast", "Pre-workout"))
        )
    }

    @Test
    fun defaultMealNamesAreCanonicalizedByOrder() {
        assertEquals(
            listOf("Breakfast", "Snack"),
            n2MealOrder(listOf("snack", "BREAKFAST", "Snack"))
        )
    }

    @Test
    fun blankLegacyMealFallsBackToOther() {
        assertEquals(listOf("Other"), n2MealOrder(listOf("   ")))
    }
}
