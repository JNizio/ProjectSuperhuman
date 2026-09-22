package com.projectsuperhuman.next

import java.util.Locale

/**
 * Authoritative semantic guard for USDA FoodData Central nutrients used by Project Superhuman.
 *
 * IDs are FDC nutrient IDs, not legacy nutrient numbers. A nutrient with a stable FDC ID must
 * match both that ID and an accepted USDA semantic name. This deliberately prefers an unknown
 * value over silently accepting a similarly named but different measure (for example vitamin A
 * IU as vitamin A RAE, vitamin D IU as micrograms, or folate DFE as total folate).
 */
internal data class UsdaNutrientSpec(
    val canonicalId: String,
    val label: String,
    val fdcNutrientId: Int?,
    val sourceNames: Set<String>,
    val sourceNamePrefixes: Set<String> = emptySet(),
    val targetUnit: String
)

internal object UsdaNutrientSemantics {
    private fun normalizeName(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun nameMatches(spec: UsdaNutrientSpec, normalizedName: String): Boolean =
        normalizedName in spec.sourceNames ||
            spec.sourceNamePrefixes.any(normalizedName::startsWith)

    /**
     * Strict semantic match used for macro/major fields. If an FDC ID is present, both ID and
     * semantic name must agree. The name-only fallback exists for legacy/source rows that omit ID.
     */
    fun sourceMatches(
        id: Int?,
        rawName: String,
        expectedId: Int,
        vararg acceptedNames: String
    ): Boolean {
        val normalized = normalizeName(rawName)
        val names = acceptedNames.map(::normalizeName).toSet()
        return if (id == null) normalized in names
        else id == expectedId && normalized in names
    }

    fun sourceStartsWith(
        id: Int?,
        rawName: String,
        expectedId: Int,
        vararg acceptedPrefixes: String
    ): Boolean {
        val normalized = normalizeName(rawName)
        val prefixes = acceptedPrefixes.map(::normalizeName)
        return if (id == null) prefixes.any(normalized::startsWith)
        else id == expectedId && prefixes.any(normalized::startsWith)
    }

    /**
     * Some aggregate nutrient concepts do not have a cross-release ID contract that this app
     * intentionally depends on. For those, an exact/prefix semantic name match is used instead.
     */
    fun matchMicronutrient(id: Int?, rawName: String): UsdaNutrientSpec? {
        val normalized = normalizeName(rawName)
        return micronutrients.firstOrNull { spec ->
            if (!nameMatches(spec, normalized)) return@firstOrNull false
            spec.fdcNutrientId == null || id == null || id == spec.fdcNutrientId
        }
    }

    val micronutrients: List<UsdaNutrientSpec> = listOf(
        UsdaNutrientSpec("calcium", "Calcium", 1087, setOf("calcium, ca"), targetUnit = "mg"),
        // FoodData Central names this component "Chlorine, Cl". Project Superhuman keeps the
        // user-facing canonical key "chloride"; mass of Cl and chloride differs only by electrons.
        UsdaNutrientSpec("chloride", "Chloride", null, setOf("chlorine, cl", "chloride, cl"), targetUnit = "mg"),
        UsdaNutrientSpec("copper", "Copper", 1098, setOf("copper, cu"), targetUnit = "mg"),
        UsdaNutrientSpec("iron", "Iron", 1089, setOf("iron, fe"), targetUnit = "mg"),
        UsdaNutrientSpec("iodine", "Iodine", 1100, setOf("iodine, i"), targetUnit = "µg"),
        UsdaNutrientSpec("magnesium", "Magnesium", 1090, setOf("magnesium, mg"), targetUnit = "mg"),
        UsdaNutrientSpec("manganese", "Manganese", 1101, setOf("manganese, mn"), targetUnit = "mg"),
        UsdaNutrientSpec("phosphorus", "Phosphorus", 1091, setOf("phosphorus, p"), targetUnit = "mg"),
        UsdaNutrientSpec("potassium", "Potassium", 1092, setOf("potassium, k"), targetUnit = "mg"),
        UsdaNutrientSpec("selenium", "Selenium", 1103, setOf("selenium, se"), targetUnit = "µg"),
        UsdaNutrientSpec("sodium", "Sodium", 1093, setOf("sodium, na"), targetUnit = "mg"),
        UsdaNutrientSpec("zinc", "Zinc", 1095, setOf("zinc, zn"), targetUnit = "mg"),

        // Keep vitamin mass definitions distinct from historical IU measures.
        UsdaNutrientSpec("vitamin_a", "Vitamin A (RAE)", 1106, setOf("vitamin a, rae"), targetUnit = "µg"),
        UsdaNutrientSpec("vitamin_c", "Vitamin C", 1162, setOf("vitamin c", "vitamin c, total ascorbic acid"), targetUnit = "mg"),
        UsdaNutrientSpec("vitamin_d", "Vitamin D (D2 + D3)", 1114, setOf("vitamin d", "vitamin d (d2 + d3)"), targetUnit = "µg"),
        UsdaNutrientSpec("vitamin_e", "Vitamin E (alpha-tocopherol)", 1109, setOf("vitamin e", "vitamin e (alpha-tocopherol)"), targetUnit = "mg"),
        UsdaNutrientSpec("vitamin_k", "Vitamin K (phylloquinone)", 1185, setOf("vitamin k", "vitamin k (phylloquinone)"), targetUnit = "µg"),
        UsdaNutrientSpec("vitamin_b1", "Vitamin B1", 1165, setOf("thiamin"), targetUnit = "mg"),
        UsdaNutrientSpec("vitamin_b2", "Vitamin B2", 1166, setOf("riboflavin"), targetUnit = "mg"),
        UsdaNutrientSpec("niacin", "Niacin (B3)", 1167, setOf("niacin"), targetUnit = "mg"),
        UsdaNutrientSpec("pantothenic_acid", "Pantothenic acid (B5)", 1170, setOf("pantothenic acid"), targetUnit = "mg"),
        UsdaNutrientSpec("vitamin_b6", "Vitamin B6", 1175, setOf("vitamin b-6", "vitamin b6"), targetUnit = "mg"),
        UsdaNutrientSpec("biotin", "Biotin (B7)", 1176, setOf("biotin"), targetUnit = "µg"),

        // These are intentionally separate. The canonical "folate" field means Folate, total.
        UsdaNutrientSpec("folate", "Folate, total (B9)", 1177, setOf("folate, total"), targetUnit = "µg"),
        UsdaNutrientSpec("folic_acid", "Folic acid", 1186, setOf("folic acid"), targetUnit = "µg"),
        UsdaNutrientSpec("folate_food", "Folate, food", 1187, setOf("folate, food"), targetUnit = "µg"),
        UsdaNutrientSpec("folate_dfe", "Folate, DFE", 1190, setOf("folate, dfe"), targetUnit = "µg"),

        UsdaNutrientSpec("vitamin_b12", "Vitamin B12", 1178, setOf("vitamin b-12", "vitamin b12"), targetUnit = "µg"),
        UsdaNutrientSpec("choline", "Choline, total", 1180, setOf("choline", "choline, total"), targetUnit = "mg"),

        UsdaNutrientSpec("starch", "Starch", 1009, setOf("starch"), targetUnit = "g"),
        UsdaNutrientSpec("water", "Water", 1051, setOf("water"), targetUnit = "g"),
        UsdaNutrientSpec("alcohol", "Alcohol", 1018, setOf("alcohol, ethyl"), targetUnit = "g"),
        UsdaNutrientSpec("cholesterol", "Cholesterol", 1253, setOf("cholesterol"), targetUnit = "mg"),
        UsdaNutrientSpec("monounsaturated_fat", "Monounsaturated fat", 1292, setOf("fatty acids, total monounsaturated"), targetUnit = "g"),
        UsdaNutrientSpec("polyunsaturated_fat", "Polyunsaturated fat", 1293, setOf("fatty acids, total polyunsaturated"), targetUnit = "g"),
        UsdaNutrientSpec("trans_fat", "Trans fat", 1257, setOf("fatty acids, total trans"), targetUnit = "g"),
        UsdaNutrientSpec("caffeine", "Caffeine", 1057, setOf("caffeine"), targetUnit = "mg"),

        // Preserve only a source-reported aggregate. Do not fabricate totals from individual fatty
        // acids because source completeness varies between FDC datasets.
        UsdaNutrientSpec("omega_3", "Omega-3 fatty acids", null, emptySet(), setOf("fatty acids, total n-3", "omega-3"), "g"),
        UsdaNutrientSpec("omega_6", "Omega-6 fatty acids", null, emptySet(), setOf("fatty acids, total n-6", "omega-6"), "g")
    )
}
