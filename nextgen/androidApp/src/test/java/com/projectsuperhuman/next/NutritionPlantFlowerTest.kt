package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals

class NutritionPlantFlowerTest {
    @Test
    fun plantKindsCollapseIntoSixFlowerPetalsWithoutLosingBotanicalCounts() {
        val counts = n2PlantFlowerCounts(
            mapOf(
                PlantFoodKind.VEGETABLE to 3,
                PlantFoodKind.FRUIT to 2,
                PlantFoodKind.LEGUME to 1,
                PlantFoodKind.GRAIN to 4,
                PlantFoodKind.NUT to 2,
                PlantFoodKind.SEED to 3,
                PlantFoodKind.HERB_SPICE to 1,
                PlantFoodKind.OTHER to 2
            )
        )

        assertEquals(3, counts.getValue(N2PlantFlowerFamily.VEGETABLES))
        assertEquals(2, counts.getValue(N2PlantFlowerFamily.FRUITS))
        assertEquals(1, counts.getValue(N2PlantFlowerFamily.LEGUMES))
        assertEquals(4, counts.getValue(N2PlantFlowerFamily.GRAINS))
        assertEquals(5, counts.getValue(N2PlantFlowerFamily.NUTS_SEEDS))
        assertEquals(3, counts.getValue(N2PlantFlowerFamily.HERBS_BOTANICALS))
        assertEquals(18, counts.values.sum())
    }

    @Test
    fun absentFamiliesRenderAsZeroRatherThanInventedProgress() {
        val counts = n2PlantFlowerCounts(
            mapOf(PlantFoodKind.VEGETABLE to 2)
        )

        assertEquals(2, counts.getValue(N2PlantFlowerFamily.VEGETABLES))
        assertEquals(0, counts.getValue(N2PlantFlowerFamily.FRUITS))
        assertEquals(0, counts.getValue(N2PlantFlowerFamily.LEGUMES))
        assertEquals(0, counts.getValue(N2PlantFlowerFamily.GRAINS))
        assertEquals(0, counts.getValue(N2PlantFlowerFamily.NUTS_SEEDS))
        assertEquals(0, counts.getValue(N2PlantFlowerFamily.HERBS_BOTANICALS))
    }
}
