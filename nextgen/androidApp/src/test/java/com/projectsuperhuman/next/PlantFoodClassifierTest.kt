package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlantFoodClassifierTest {
    @Test
    fun identifiesCommonPlantFoods() {
        val apple = PlantFoodClassifier.classify("Apple, raw")
        assertTrue(apple.isPlantFood)
        assertEquals(PlantFoodKind.FRUIT, apple.kind)
        assertEquals("apple", apple.diversityKey)

        val tofu = PlantFoodClassifier.classify("Tofu, firm")
        assertTrue(tofu.isPlantFood)
        assertEquals(PlantFoodKind.LEGUME, tofu.kind)
        assertEquals("soy", tofu.diversityKey)
    }

    @Test
    fun prefersSpecificPlantIdentityOverGenericToken() {
        val sweetPotato = PlantFoodClassifier.classify("Sweet potato, baked")
        assertTrue(sweetPotato.isPlantFood)
        assertEquals("sweet_potato", sweetPotato.diversityKey)

        val pumpkinSeed = PlantFoodClassifier.classify("Pumpkin seeds, roasted")
        assertTrue(pumpkinSeed.isPlantFood)
        assertEquals(PlantFoodKind.SEED, pumpkinSeed.kind)
        assertEquals("pumpkin_seed", pumpkinSeed.diversityKey)
    }

    @Test
    fun supportsExplicitPlantMilkWithoutTreatingMilkAsDairy() {
        val oatMilk = PlantFoodClassifier.classify("Oat milk, unsweetened")
        assertTrue(oatMilk.isPlantFood)
        assertEquals(PlantFoodKind.GRAIN, oatMilk.kind)
        assertEquals("oat", oatMilk.diversityKey)
    }

    @Test
    fun excludesAnimalAndUnrecognisedFoods() {
        assertFalse(PlantFoodClassifier.classify("Chicken breast, grilled").isPlantFood)
        assertFalse(PlantFoodClassifier.classify("Cheddar cheese").isPlantFood)
        assertFalse(PlantFoodClassifier.classify("Mushrooms, raw").isPlantFood)
    }
}
