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
    fun matchesPlantAndAnimalTermsAtEndOfNames() {
        val freshApple = PlantFoodClassifier.classify("Fresh apple")
        assertTrue(freshApple.isPlantFood)
        assertEquals("apple", freshApple.diversityKey)

        val riceWithChicken = PlantFoodClassifier.classify("Rice with chicken")
        assertFalse(riceWithChicken.isPlantFood)
    }

    @Test
    fun plantBasedDairyAlternativesAreNotTaggedAsDairy() {
        val coconutYogurt = FoodTaxonomyClassifier.classify("Coconut yogurt")
        assertTrue(FoodTag.PLANT in coconutYogurt)
        assertFalse(FoodTag.DAIRY in coconutYogurt)
        assertFalse(FoodTag.ANIMAL_DERIVED in coconutYogurt)

        val coconutCream = FoodTaxonomyClassifier.classify("Coconut cream")
        assertTrue(FoodTag.PLANT in coconutCream)
        assertFalse(FoodTag.DAIRY in coconutCream)

        val cocoaButter = FoodTaxonomyClassifier.classify("Cocoa butter")
        assertTrue(FoodTag.PLANT in cocoaButter)
        assertFalse(FoodTag.DAIRY in cocoaButter)
        assertFalse(FoodTag.BUTTER in cocoaButter)
    }

    @Test
    fun excludesAnimalAndUnrecognisedFoods() {
        assertFalse(PlantFoodClassifier.classify("Chicken breast, grilled").isPlantFood)
        assertFalse(PlantFoodClassifier.classify("Cheddar cheese").isPlantFood)
        assertFalse(PlantFoodClassifier.classify("Mushrooms, raw").isPlantFood)
    }

    @Test
    fun separatesPlantClassificationFromDiversityEligibility() {
        val apple = PlantFoodClassifier.classify("Apple, raw")
        assertTrue(apple.isPlantFood)
        assertTrue(apple.diversityEligible)

        val oliveOil = PlantFoodClassifier.classify("Olive oil, extra virgin")
        assertTrue(oliveOil.isPlantFood)
        assertEquals("olive", oliveOil.diversityKey)
        assertFalse(oliveOil.diversityEligible)

        val oatMilk = PlantFoodClassifier.classify("Oat milk, unsweetened")
        assertTrue(oatMilk.isPlantFood)
        assertFalse(oatMilk.diversityEligible)
    }

    @Test
    fun tomatoAndRocketArePlantDiversityEligible() {
        val tomato = PlantFoodClassifier.classify("Tomato, raw")
        assertTrue(tomato.isPlantFood)
        assertEquals(PlantFoodKind.VEGETABLE, tomato.kind)
        assertEquals("tomato", tomato.diversityKey)
        assertTrue(tomato.diversityEligible)

        val rocket = PlantFoodClassifier.classify("Rocket, raw")
        assertTrue(rocket.isPlantFood)
        assertEquals(PlantFoodKind.VEGETABLE, rocket.kind)
        assertEquals("rocket", rocket.diversityKey)
        assertTrue(rocket.diversityEligible)

        val wildRocket = PlantFoodClassifier.classify("Wild rocket, raw")
        assertTrue(wildRocket.isPlantFood)
        assertEquals(PlantFoodKind.VEGETABLE, wildRocket.kind)
        assertEquals("rocket", wildRocket.diversityKey)
        assertTrue(wildRocket.diversityEligible)
    }

    @Test
    fun deterministicTaxonomyCoversRepresentativeFoodFamilies() {
        val rocket = FoodTaxonomyClassifier.classify("Arugula, raw")
        assertTrue(FoodTag.PLANT in rocket)
        assertTrue(FoodTag.VEGETABLE in rocket)
        assertTrue(FoodTag.LEAFY_GREEN in rocket)
        assertTrue(FoodTag.RAW in rocket)

        val salmon = FoodTaxonomyClassifier.classify("Salmon, cooked")
        assertTrue(FoodTag.ANIMAL in salmon)
        assertTrue(FoodTag.FISH in salmon)
        assertTrue(FoodTag.SEAFOOD in salmon)

        val sourdough = FoodTaxonomyClassifier.classify("Sourdough bread")
        assertTrue(FoodTag.PLANT in sourdough)
        assertTrue(FoodTag.GRAIN in sourdough)
        assertTrue(FoodTag.BREAD in sourdough)
        assertTrue(FoodTag.BAKERY in sourdough)
        assertTrue(FoodTag.FERMENTED in sourdough)

        val mushrooms = FoodTaxonomyClassifier.classify("Mushrooms, raw")
        assertTrue(FoodTag.MUSHROOM in mushrooms)
        assertFalse(FoodTag.PLANT in mushrooms)
    }
}
