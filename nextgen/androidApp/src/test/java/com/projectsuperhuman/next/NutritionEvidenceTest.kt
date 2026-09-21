package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutritionEvidenceTest {
    private fun food(
        id: String = "test-food",
        name: String = "Banana",
        source: String = "Project Superhuman local reference",
        kcal: Double = 0.0,
        kcalKnown: Boolean = false,
        protein: Double = 0.0,
        proteinKnown: Boolean = true,
        carbs: Double = 22.0,
        carbohydrateDefinition: CarbohydrateDefinition = CarbohydrateDefinition.UNKNOWN,
        warning: String? = null
    ) = NativeFood(
        id = id,
        name = name,
        country = "",
        kcal = kcal,
        kcalKnown = kcalKnown,
        protein = protein,
        proteinKnown = proteinKnown,
        carbs = carbs,
        carbohydrateDefinition = carbohydrateDefinition,
        fat = 0.2,
        fibre = 2.6,
        sugar = 12.0,
        unit = "100 g",
        source = source,
        nutritionIntegrityWarning = warning
    )

    @Test
    fun canonicalModelPreservesUnknownEnergyAndKnownZeroProtein() {
        val canonical = FoodEvidenceEngine.canonicalize(food())
        assertFalse("energy_kcal" in canonical.nutrients)
        assertTrue("protein" in canonical.nutrients)
        assertEquals(0.0, canonical.nutrients.getValue("protein").valuePerBasis, 0.0)
    }

    @Test
    fun carbohydrateSemanticsArePreservedNotSilentlyConverted() {
        val off = FoodEvidenceEngine.canonicalize(
            food(
                id = "off",
                source = "Open Food Facts",
                carbohydrateDefinition = CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE
            )
        )
        val usda = FoodEvidenceEngine.canonicalize(
            food(
                id = "usda",
                source = "USDA Foundation Foods 2026-04",
                carbohydrateDefinition = CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE
            )
        )
        assertEquals(CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE, off.carbohydrateDefinition)
        assertEquals(CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE, usda.carbohydrateDefinition)
    }

    @Test
    fun sourceHierarchyPrefersFoundationOverLegacyApproximation() {
        val foundation = food(source = "USDA Foundation Foods 2026-04")
        val legacy = food(source = "USDA SR Legacy")
        assertTrue(FoodEvidenceEngine.sourcePriority(foundation) < FoodEvidenceEngine.sourcePriority(legacy))
    }

    @Test
    fun genericAliasesDeduplicateButPreparationStatesRemainDistinct() {
        val banana = food(name = "Banana")
        val plural = food(id = "plural", name = "Bananas fresh")
        assertEquals(FoodEvidenceEngine.dedupKey(banana), FoodEvidenceEngine.dedupKey(plural))

        val dry = food(id = "dry", name = "Pasta, dry")
        val cooked = food(id = "cooked", name = "Pasta, cooked")
        assertNotEquals(FoodEvidenceEngine.dedupKey(dry), FoodEvidenceEngine.dedupKey(cooked))
    }

    @Test
    fun conflictedEvidenceLowersConfidenceWithoutDeletingSourceValues() {
        val original = food(kcal = 90.0, kcalKnown = true, warning = "Sugars exceed reported carbohydrate")
        val enriched = FoodEvidenceEngine.enrich(original)
        assertEquals(FoodVerificationState.CONFLICTED, enriched.verificationState)
        assertEquals(FoodDataConfidence.CONFLICTED, enriched.confidence)
        assertEquals(90.0, enriched.kcal, 0.0)
        assertTrue(enriched.kcalKnown)
    }

    @Test
    fun snapshotCapturesSourceVersionAndOriginalConversion() {
        val original = FoodEvidenceEngine.enrich(
            food(kcal = 89.0, kcalKnown = true).copy(
                sourceRecordId = "fdc:123",
                sourceRevision = "2026-04",
                densityGPerMl = 0.91,
                densitySource = DensityEvidenceSource.REFERENCE_DATABASE
            )
        )
        val conversion = FoodUnitSystem.convert(original, 100.0, FoodUnit.G)
        requireNotNull(conversion)

        val snapshot = FoodEvidenceEngine.snapshotMetadata(original, 100.0, FoodUnit.G, conversion)
        assertEquals("4", snapshot["nutritionSnapshotVersion"])
        assertEquals("fdc:123", snapshot["sourceRecordIdCanonical"])
        assertEquals("2026-04", snapshot["sourceRevision"])
        assertEquals("g", snapshot["originalAmountUnit"])
        assertEquals(DensityEvidenceSource.REFERENCE_DATABASE.name, snapshot["densitySource"])
    }

    @Test
    fun missingDensityNeverCreatesMassVolumeConversion() {
        val drink = food(kcal = 40.0, kcalKnown = true).copy(
            unit = "100 ml",
            basisUnit = FoodUnit.ML,
            densityGPerMl = null
        )
        assertNull(FoodUnitSystem.convert(drink, 100.0, FoodUnit.G))
    }
}
