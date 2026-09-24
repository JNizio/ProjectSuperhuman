package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsdaNutrientSemanticsTest {
    @Test
    fun mapsCanonicalMassBasedVitaminDefinitionsOnly() {
        val vitaminA = UsdaNutrientSemantics.matchMicronutrient(1106, "Vitamin A, RAE")
        assertNotNull(vitaminA)
        assertEquals("vitamin_a", vitaminA.canonicalId)
        assertEquals("µg", vitaminA.targetUnit)

        // Historical IU is a different measure and must never become µg RAE by name similarity.
        assertNull(UsdaNutrientSemantics.matchMicronutrient(1104, "Vitamin A, IU"))
        assertNull(
            UsdaNutrientSemantics.matchMicronutrient(
                1104,
                "Vitamin A, RAE"
            )
        )

        val vitaminD = UsdaNutrientSemantics.matchMicronutrient(1114, "Vitamin D (D2 + D3)")
        assertNotNull(vitaminD)
        assertEquals("µg", vitaminD.targetUnit)
        assertNull(
            UsdaNutrientSemantics.matchMicronutrient(
                1110,
                "Vitamin D (D2 + D3), International Units"
            )
        )
    }

    @Test
    fun preservesFolateFormsAsDifferentNutrients() {
        assertEquals(
            "folate",
            UsdaNutrientSemantics.matchMicronutrient(1177, "Folate, total")?.canonicalId
        )
        assertEquals(
            "folic_acid",
            UsdaNutrientSemantics.matchMicronutrient(1186, "Folic acid")?.canonicalId
        )
        assertEquals(
            "folate_food",
            UsdaNutrientSemantics.matchMicronutrient(1187, "Folate, food")?.canonicalId
        )
        assertEquals(
            "folate_dfe",
            UsdaNutrientSemantics.matchMicronutrient(1190, "Folate, DFE")?.canonicalId
        )
        assertNull(UsdaNutrientSemantics.matchMicronutrient(1190, "Folate, total"))
    }

    @Test
    fun mapsUsdaChlorineSemanticNameToCanonicalChlorideKey() {
        val chlorine = UsdaNutrientSemantics.matchMicronutrient(null, "Chlorine, Cl")
        assertNotNull(chlorine)
        assertEquals("chloride", chlorine.canonicalId)
        assertEquals("mg", chlorine.targetUnit)
    }

    @Test
    fun stableIdsMustAgreeWithSemanticNames() {
        assertNotNull(UsdaNutrientSemantics.matchMicronutrient(1093, "Sodium, Na"))
        assertNull(UsdaNutrientSemantics.matchMicronutrient(1092, "Sodium, Na"))
        assertNull(UsdaNutrientSemantics.matchMicronutrient(1093, "Potassium, K"))
    }

    @Test
    fun aggregateOmegaValuesRequireSourceReportedAggregateNames() {
        assertEquals(
            "omega_3",
            UsdaNutrientSemantics.matchMicronutrient(9999, "Fatty acids, total n-3")?.canonicalId
        )
        assertEquals(
            "omega_6",
            UsdaNutrientSemantics.matchMicronutrient(9999, "Fatty acids, total n-6")?.canonicalId
        )
        // Individual ALA must not be silently promoted to a complete omega-3 total.
        assertNull(
            UsdaNutrientSemantics.matchMicronutrient(
                1404,
                "PUFA 18:3 n-3 c,c,c (ALA)"
            )
        )
    }

    @Test
    fun majorNutrientGuardRequiresIdAndMeaningToAgree() {
        assertTrue(
            UsdaNutrientSemantics.sourceMatches(
                1005,
                "Carbohydrate, by difference",
                1005,
                "Carbohydrate, by difference"
            )
        )
        assertFalse(
            UsdaNutrientSemantics.sourceMatches(
                1005,
                "Carbohydrate, available",
                1005,
                "Carbohydrate, by difference"
            )
        )
        assertFalse(
            UsdaNutrientSemantics.sourceMatches(
                1079,
                "Carbohydrate, by difference",
                1005,
                "Carbohydrate, by difference"
            )
        )
    }
    @Test
    fun keepsAddedSugarsSeparateFromTotalSugars() {
        val added = UsdaNutrientSemantics.matchMicronutrient(1235, "Sugars, added")
        assertNotNull(added)
        assertEquals("added_sugars", added.canonicalId)
        assertEquals("g", added.targetUnit)

        assertNull(UsdaNutrientSemantics.matchMicronutrient(1235, "Sugars, total"))
        assertNull(UsdaNutrientSemantics.matchMicronutrient(2000, "Sugars, added"))
    }

    @Test
    fun stableCanonicalMappingsDoNotReuseFdcIds() {
        val stableIds = UsdaNutrientSemantics.micronutrients.mapNotNull { it.fdcNutrientId }
        assertEquals(stableIds.size, stableIds.toSet().size)

        val canonicalIds = UsdaNutrientSemantics.micronutrients.map { it.canonicalId }
        assertEquals(canonicalIds.size, canonicalIds.toSet().size)
    }

}
