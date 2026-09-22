package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFoodAuditRulesTest {
    private fun row(
        id: String = "test:food",
        name: String = "Apple, raw",
        kcal: Double = 52.0,
        protein: Double = 0.3,
        carbs: Double = 13.8,
        fat: Double = 0.2,
        fibre: Double = 2.4,
        sugar: Double = 10.4,
        saturatedFat: Double = 0.03,
        sodiumMg: Double = 1.0,
        tags: Set<FoodTag> = setOf(FoodTag.PLANT, FoodTag.FRUIT, FoodTag.RAW),
        isPlant: Boolean = true,
        plantKind: PlantFoodKind = PlantFoodKind.FRUIT,
        plantKey: String = "apple",
        plantEligible: Boolean = true,
        micros: Map<String, NativeNutrient> = mapOf(
            "calcium" to NativeNutrient("calcium", "Calcium", 6.0, "mg", NutrientEvidenceKind.REFERENCE_DATABASE),
            "potassium" to NativeNutrient("potassium", "Potassium", 107.0, "mg", NutrientEvidenceKind.REFERENCE_DATABASE)
        )
    ) = LocalFoodAuditRow(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fibre = fibre,
        sugar = sugar,
        saturatedFat = saturatedFat,
        sodiumMg = sodiumMg,
        saltG = NutritionIntegrity.sodiumMgToSaltG(sodiumMg),
        proteinKnown = true,
        carbsKnown = true,
        fatKnown = true,
        fibreKnown = true,
        sugarKnown = true,
        saturatedFatKnown = true,
        sodiumKnown = true,
        saltKnown = true,
        unit = "100 g",
        source = "USDA Foundation Foods test",
        sourceRecordId = id,
        micronutrients = micros,
        unknownMicronutrients = emptySet(),
        foodTags = tags,
        taxonomyVersion = FoodTaxonomyClassifier.SCHEMA_VERSION,
        isPlantFood = isPlant,
        plantFoodKind = plantKind,
        plantDiversityKey = plantKey,
        plantDiversityEligible = plantEligible,
        preparationState = FoodEvidenceEngine.inferPreparationState(name),
        servingQuantity = null,
        servingUnit = "",
        servingLabel = ""
    )

    @Test
    fun acceptsPlausibleReferenceFoodWithoutHardErrors() {
        val findings = LocalFoodAuditRules.validate(row())
        assertFalse(findings.any { it.severity == LocalFoodAuditSeverity.ERROR })
    }

    @Test
    fun catchesCrossNutrientAndUnitScaleFailures() {
        val findings = LocalFoodAuditRules.validate(
            row(
                name = "Broken food",
                carbs = 10.0,
                sugar = 20.0,
                fat = 2.0,
                saturatedFat = 6.0,
                sodiumMg = 75_000.0,
                tags = setOf(FoodTag.OTHER),
                isPlant = false,
                plantKind = PlantFoodKind.NONE,
                plantKey = "",
                plantEligible = false
            )
        )
        val codes = findings.map { it.code }.toSet()
        assertTrue("sugars_gt_carbs" in codes)
        assertTrue("satfat_gt_fat" in codes)
        assertTrue("sodium_unit" in codes)
        assertTrue("taxonomy_missing" in codes)
    }

    @Test
    fun flagsImplausibleFoodIdentityProfiles() {
        val oilFindings = LocalFoodAuditRules.validate(
            row(
                name = "Olive oil",
                kcal = 120.0,
                protein = 0.0,
                carbs = 0.0,
                fat = 10.0,
                fibre = 0.0,
                sugar = 0.0,
                saturatedFat = 1.5,
                sodiumMg = 0.0,
                tags = setOf(FoodTag.PLANT, FoodTag.OIL_FAT, FoodTag.OIL),
                plantKind = PlantFoodKind.FRUIT,
                plantKey = "olive",
                plantEligible = false
            )
        )
        assertTrue(oilFindings.any { it.code == "identity_oil" })

        val chickenFindings = LocalFoodAuditRules.validate(
            row(
                name = "Chicken breast, grilled",
                kcal = 165.0,
                protein = 31.0,
                carbs = 0.0,
                fat = 3.6,
                fibre = 5.0,
                sugar = 0.0,
                saturatedFat = 1.0,
                sodiumMg = 74.0,
                tags = setOf(FoodTag.ANIMAL, FoodTag.ANIMAL_DERIVED, FoodTag.MEAT, FoodTag.POULTRY, FoodTag.CHICKEN, FoodTag.CHICKEN_BREAST, FoodTag.GRILLED),
                isPlant = false,
                plantKind = PlantFoodKind.NONE,
                plantKey = "",
                plantEligible = false
            )
        )
        assertTrue(chickenFindings.any { it.code == "identity_animal_fibre" })
    }

    @Test
    fun catchesCanonicalMicronutrientUnitMismatch() {
        val findings = LocalFoodAuditRules.validate(
            row(
                micros = mapOf(
                    "selenium" to NativeNutrient(
                        "selenium",
                        "Selenium",
                        20.0,
                        "mg",
                        NutrientEvidenceKind.REFERENCE_DATABASE
                    )
                )
            )
        )
        assertTrue(findings.any { it.code == "micro_unit_definition_selenium" })
    }

    @Test
    fun catchesKnownUnknownMicronutrientConflict() {
        val base = row()
        val findings = LocalFoodAuditRules.validate(
            base.copy(unknownMicronutrients = setOf("calcium"))
        )
        assertTrue(findings.any { it.code == "micro_known_unknown_conflict" })
    }

    @Test
    fun refusesProcessedFoodAsPlantDiversityEligible() {
        val findings = LocalFoodAuditRules.validate(
            row(
                name = "Apple cake",
                tags = setOf(FoodTag.PLANT, FoodTag.FRUIT, FoodTag.BAKED_GOOD, FoodTag.DESSERT, FoodTag.PREPARED_FOOD),
                plantEligible = true
            )
        )
        assertTrue(findings.any { it.code == "plant_diversity_processed" })
    }
}
