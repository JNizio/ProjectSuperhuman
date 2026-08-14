package com.projectsuperhuman.next.trudy.nutrition

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyToolOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrudyNutritionBodyKnowledgeTest {
    private val knowledge = CuratedNutritionBodyKnowledgeRepository()

    @Test fun curatedNutrientsAreDeepUniqueAndProvenanceBacked() {
        assertEquals(27, knowledge.nutrientCount)
        val iron = assertNotNull(knowledge.nutrient("haem iron"))
        assertEquals("iron", iron.id)
        assertTrue(iron.role.isNotBlank())
        assertTrue(iron.dietarySources.size >= 3)
        assertTrue(iron.lowIntakeContext.isNotBlank())
        assertTrue(iron.excessContext.isNotBlank())
        assertTrue(iron.populationConsiderations.isNotEmpty())
        assertTrue(iron.interactionsAndCaveats.isNotEmpty())
        assertTrue(iron.sourceIds.all { id -> knowledge.sources().any { it.id == id } })
    }

    @Test fun nutrientAliasesResolveEverydayLanguage() {
        assertEquals("vitamin_b12", knowledge.nutrient("B12")?.id)
        assertEquals("folate", knowledge.nutrient("folic acid")?.id)
        assertEquals("omega_3", knowledge.nutrient("EPA")?.id)
        assertEquals("vitamin_b1", knowledge.nutrient("thiamine")?.id)
    }

    @Test fun chipsAreLocaleAwareAndAmbiguousWithoutLocale() {
        val unknownLocale = knowledge.resolveFood("I had chips with lunch")
        assertTrue(unknownLocale is FoodTermResolution.Ambiguous)
        assertEquals(setOf("potato_crisps", "fried_potatoes"), unknownLocale.candidates.map { it.id }.toSet())

        val uk = knowledge.resolveFood("I had chips with lunch", FoodLocale.UK)
        assertTrue(uk is FoodTermResolution.Resolved)
        assertEquals("fried_potatoes", uk.term.id)

        val us = knowledge.resolveFood("I had chips with lunch", FoodLocale.US)
        assertTrue(us is FoodTermResolution.Resolved)
        assertEquals("potato_crisps", us.term.id)
    }

    @Test fun commonFoodPhrasesResolveWithoutInventingPortions() {
        val latte = knowledge.resolveFood("Breakfast was oats and a latte", FoodLocale.UK)
        assertTrue(latte is FoodTermResolution.Resolved)
        assertEquals("latte", latte.term.id)
        assertTrue("cup size" in latte.term.interpretation)

        val shake = knowledge.resolveFood("I drank a protein shake")
        assertTrue(shake is FoodTermResolution.Resolved)
        assertEquals("protein_shake", shake.term.id)
        assertTrue("scoops" in shake.term.interpretation)
    }

    @Test fun requestedQuestionsMapToCautiousIntents() {
        assertEquals(NutritionQuestionIntent.PROTEIN_ADEQUACY, knowledge.resolveIntent("Am I eating enough protein?"))
        assertEquals(NutritionQuestionIntent.OVERNIGHT_WEIGHT_CHANGE, knowledge.resolveIntent("Why did my weight jump overnight?"))
        assertEquals(NutritionQuestionIntent.HYDRATION_ADEQUACY, knowledge.resolveIntent("Have I been drinking enough?"))
        assertEquals(NutritionQuestionIntent.FOOD_AND_ENERGY, knowledge.resolveIntent("Could my food be affecting my energy?"))
        assertEquals(NutritionQuestionIntent.DIETARY_NUTRIENT_GAP, knowledge.resolveIntent("What nutrients might I be low in?"))
    }

    @Test fun dietaryGapContextDoesNotPretendUnknownMicronutrientsAreZero() {
        val context = knowledge.contextFor("Could I be low in B12?")
        assertEquals(NutritionQuestionIntent.DIETARY_NUTRIENT_GAP, context.intent)
        assertEquals(listOf("vitamin_b12"), context.nutrients.map { it.id })
        assertTrue(context.safetyInstructions.any { "never as a diagnosis" in it })
        assertTrue(context.safetyInstructions.any { "unknown is not zero" in it })
        assertTrue(context.sources.isNotEmpty())
        assertTrue(context.sources.map { it.id }.containsAll(context.nutrients.flatMap { it.sourceIds }))
    }

    @Test fun mappingsUseExistingCanonicalDataVaultIds() {
        assertEquals(listOf("food_kcal"), TrudyNutritionMetricCatalog.byId("energy_intake")?.metricIds)
        assertEquals(listOf("food_protein"), TrudyNutritionMetricCatalog.byId("protein_intake")?.metricIds)
        assertEquals(listOf("water_intake_ml"), TrudyNutritionMetricCatalog.byId("water_intake")?.metricIds)
        assertEquals(listOf("body_weight_kg"), TrudyNutritionMetricCatalog.byId("body_weight")?.metricIds)
        assertEquals(listOf("food_iron_mg"), TrudyNutritionMetricCatalog.forNutrient("iron")?.metricIds)
        assertEquals(listOf("food_vitamin_b12_ug"), TrudyNutritionMetricCatalog.forNutrient("vitamin_b12")?.metricIds)
        assertTrue(TrudyNutritionMetricCatalog.unresolvedRegistryBindings().isEmpty())
    }

    @Test fun unrecordedOmegaThreeHasKnowledgeButNoInventedMetric() {
        assertNotNull(knowledge.nutrient("omega 3"))
        assertNull(TrudyNutritionMetricCatalog.forNutrient("omega_3"))
        assertTrue(knowledge.nutrient("omega 3")!!.interactionsAndCaveats.any { "does not yet have" in it })
    }

    @Test fun hydrationPlannerIncludesActivityAndEnvironmentWithoutNewTools() {
        val operations = TrudyNutritionToolPlanner().plan(NutritionQuestionIntent.HYDRATION_ADEQUACY)
        val histories = operations.filterIsInstance<TrudyToolOperation.GetMetricHistory>()
        assertTrue(histories.any { it.domain == HealthDomain.HYDRATION && it.metricId == "water_intake_ml" })
        assertTrue(histories.any { it.domain == HealthDomain.EXERCISE && it.metricId == "exercise_minutes" })
        assertTrue(histories.any { it.domain == HealthDomain.ENVIRONMENT && it.metricId == "environment_temperature_c" })
        assertEquals(3, operations.filterIsInstance<TrudyToolOperation.GetDataQuality>().size)
    }

    @Test fun nutrientGapPlannerRequestsBoundedDomainHistory() {
        val operations = TrudyNutritionToolPlanner().plan(NutritionQuestionIntent.DIETARY_NUTRIENT_GAP)
        val history = operations.filterIsInstance<TrudyToolOperation.GetDomainHistory>().single()
        assertEquals(HealthDomain.NUTRITION, history.domain)
        assertEquals(250, history.limit)
        assertEquals(1, operations.filterIsInstance<TrudyToolOperation.GetDataQuality>().size)
    }

    @Test fun policyRejectsDiagnosisExcessWaterAndDisorderedInterpretation() {
        val policy = TrudyNutritionSafetyPolicy.MODEL_INSTRUCTION.lowercase()
        assertTrue("cannot diagnose" in policy)
        assertTrue("missing data is unknown, not zero" in policy)
        assertTrue("never encourage forced or excessive water intake" in policy)
        assertTrue("short-term change" in policy)
        assertTrue("neutral and non-judgemental" in policy)
        assertTrue("do not prescribe supplement doses" in policy)
    }
}
