package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodTaxonomyAdversarialTest {
    @Test
    fun plantDairyAlternativesStayPlantButNeverBecomeDairyOrDiversityEligible() {
        listOf(
            "Coconut yogurt" to "coconut",
            "Oat milk, unsweetened" to "oat",
            "Soy yogurt, plain" to "soy",
            "Coconut cream" to "coconut"
        ).forEach { (name, expectedKey) ->
            val plant = PlantFoodClassifier.classify(name)
            val tags = FoodTaxonomyClassifier.classify(name, plantIdentity = plant)
            assertTrue(plant.isPlantFood, name)
            assertEquals(expectedKey, plant.diversityKey, name)
            assertFalse(plant.diversityEligible, name)
            assertTrue(FoodTag.PLANT in tags, name)
            assertFalse(FoodTag.DAIRY in tags, name)
            assertFalse(FoodTag.ANIMAL_DERIVED in tags, name)
        }

        val veganCheese = FoodTaxonomyClassifier.classify("Vegan cheese")
        assertFalse(FoodTag.DAIRY in veganCheese)
        assertFalse(FoodTag.ANIMAL_DERIVED in veganCheese)
    }

    @Test
    fun ambiguousFoodNamesDoNotTriggerAnimalFalsePositives() {
        val butterBeans = FoodTaxonomyClassifier.classify("Butter beans, cooked")
        assertTrue(FoodTag.LEGUME in butterBeans)
        assertFalse(FoodTag.DAIRY in butterBeans)
        assertFalse(FoodTag.BUTTER in butterBeans)

        val eggplant = FoodTaxonomyClassifier.classify("Eggplant, raw")
        assertTrue(FoodTag.VEGETABLE in eggplant)
        assertFalse(FoodTag.EGG in eggplant)
        assertFalse(FoodTag.ANIMAL_DERIVED in eggplant)

        val cocoaButter = PlantFoodClassifier.classify("Cocoa butter")
        val cocoaTags = FoodTaxonomyClassifier.classify("Cocoa butter", plantIdentity = cocoaButter)
        assertTrue(cocoaButter.isPlantFood)
        assertFalse(cocoaButter.diversityEligible)
        assertFalse(FoodTag.DAIRY in cocoaTags)
        assertFalse(FoodTag.BUTTER in cocoaTags)
    }

    @Test
    fun compositeFoodsDoNotInheritPlantDiversityFromIngredientWords() {
        listOf("Apple pie", "Mushroom soup", "Tomato sauce").forEach { name ->
            val plant = PlantFoodClassifier.classify(name)
            assertFalse(plant.diversityEligible, name)
        }

        val chickenRice = PlantFoodClassifier.classify("Chicken with rice")
        assertFalse(chickenRice.isPlantFood)
        assertFalse(chickenRice.diversityEligible)

        val fishSauce = FoodTaxonomyClassifier.classify("Fish sauce")
        assertTrue(FoodTag.FISH in fishSauce)
        assertTrue(FoodTag.SAUCE in fishSauce)
        assertTrue(FoodTag.ANIMAL_DERIVED in fishSauce)
    }

    @Test
    fun oilsRemainPlantDerivedButDoNotCountForDiversity() {
        listOf(
            "Olive oil" to "olive",
            "Sesame oil" to "sesame"
        ).forEach { (name, key) ->
            val plant = PlantFoodClassifier.classify(name)
            val tags = FoodTaxonomyClassifier.classify(name, plantIdentity = plant)
            assertTrue(plant.isPlantFood, name)
            assertEquals(key, plant.diversityKey, name)
            assertFalse(plant.diversityEligible, name)
            assertTrue(FoodTag.OIL in tags, name)
        }
    }

    @Test
    fun minimallyProcessedPlantIdentitiesRemainSpecific() {
        val almondFlour = PlantFoodClassifier.classify("Almond flour")
        assertTrue(almondFlour.isPlantFood)
        assertEquals(PlantFoodKind.NUT, almondFlour.kind)
        assertEquals("almond", almondFlour.diversityKey)

        val coconutFlour = PlantFoodClassifier.classify("Coconut flour")
        assertTrue(coconutFlour.isPlantFood)
        assertEquals("coconut", coconutFlour.diversityKey)

        val tahini = PlantFoodClassifier.classify("Tahini")
        assertTrue(tahini.isPlantFood)
        assertEquals(PlantFoodKind.SEED, tahini.kind)
        assertEquals("sesame", tahini.diversityKey)

        val tofu = PlantFoodClassifier.classify("Tofu, firm")
        assertEquals("soy", tofu.diversityKey)
        assertTrue(tofu.diversityEligible)

        val tempehTags = FoodTaxonomyClassifier.classify("Tempeh")
        assertTrue(FoodTag.LEGUME in tempehTags)
        assertTrue(FoodTag.FERMENTED in tempehTags)

        val yeast = FoodTaxonomyClassifier.classify("Nutritional yeast")
        assertFalse(FoodTag.PLANT in yeast)
        assertFalse(FoodTag.ANIMAL in yeast)
    }
}
