package com.projectsuperhuman.next

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

internal enum class LocalFoodAuditSeverity { INFO, WARNING, ERROR }

internal data class LocalFoodAuditFinding(
    val foodId: String,
    val foodName: String,
    val code: String,
    val severity: LocalFoodAuditSeverity,
    val message: String
)

internal data class LocalFoodNutrientCoverage(
    val nutrient: String,
    val valid: Int,
    val missing: Int,
    val zero: Int,
    val suspiciousZero: Int
) {
    val coveragePercent: Double
        get() = if (valid + missing == 0) 0.0 else valid * 100.0 / (valid + missing).toDouble()
}

internal data class LocalFoodRepresentativeSnapshot(
    val requested: String,
    val matchedFoodId: String?,
    val matchedName: String?,
    val source: String?,
    val sourceRecordId: String?,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val micronutrientCount: Int?,
    val tags: Set<FoodTag>,
    val preparationState: FoodPreparationState?,
    val plantDiversityEligible: Boolean?
)

internal data class LocalFoodDatabaseAuditReport(
    val generatedEpochMs: Long,
    val totalRecords: Int,
    val uniqueCanonicalFoods: Int,
    val coreRecords: Int,
    val duplicateCandidateGroups: Int,
    val exactSourceSnapshotPairs: Int,
    val highConfidenceRecords: Int,
    val recordsNeedingReview: Int,
    val foodsMissingTags: Int,
    val plantFoodsMissingPlantTag: Int,
    val suspiciousPlantClassifications: Int,
    val representativeFoods: List<LocalFoodRepresentativeSnapshot>,
    val findings: List<LocalFoodAuditFinding>,
    val nutrientCoverage: List<LocalFoodNutrientCoverage>,
    val duplicateExamples: List<List<String>>,
    val suspiciousProfileExamples: List<List<String>>
) {
    fun toJson(): String = JSONObject().apply {
        put("generatedEpochMs", generatedEpochMs)
        put("totalRecords", totalRecords)
        put("uniqueCanonicalFoods", uniqueCanonicalFoods)
        put("coreRecords", coreRecords)
        put("duplicateCandidateGroups", duplicateCandidateGroups)
        put("exactSourceSnapshotPairs", exactSourceSnapshotPairs)
        put("highConfidenceRecords", highConfidenceRecords)
        put("recordsNeedingReview", recordsNeedingReview)
        put("foodsMissingTags", foodsMissingTags)
        put("plantFoodsMissingPlantTag", plantFoodsMissingPlantTag)
        put("suspiciousPlantClassifications", suspiciousPlantClassifications)
        put("representativeFoods", JSONArray().apply {
            representativeFoods.forEach { item ->
                put(JSONObject().apply {
                    put("requested", item.requested)
                    put("matchedFoodId", item.matchedFoodId)
                    put("matchedName", item.matchedName)
                    put("source", item.source)
                    put("sourceRecordId", item.sourceRecordId)
                    put("kcal", item.kcal)
                    put("protein", item.protein)
                    put("carbs", item.carbs)
                    put("fat", item.fat)
                    put("micronutrientCount", item.micronutrientCount)
                    put("tags", JSONArray(item.tags.map { it.name }.sorted()))
                    put("preparationState", item.preparationState?.name)
                    put("plantDiversityEligible", item.plantDiversityEligible)
                })
            }
        })
        put("nutrientCoverage", JSONArray().apply {
            nutrientCoverage.forEach { item ->
                put(JSONObject().apply {
                    put("nutrient", item.nutrient)
                    put("valid", item.valid)
                    put("missing", item.missing)
                    put("zero", item.zero)
                    put("suspiciousZero", item.suspiciousZero)
                    put("coveragePercent", item.coveragePercent)
                })
            }
        })
        put("findings", JSONArray().apply {
            findings.forEach { finding ->
                put(JSONObject().apply {
                    put("foodId", finding.foodId)
                    put("foodName", finding.foodName)
                    put("code", finding.code)
                    put("severity", finding.severity.name)
                    put("message", finding.message)
                })
            }
        })
        put("duplicateExamples", JSONArray().apply {
            duplicateExamples.forEach { group -> put(JSONArray(group)) }
        })
        put("suspiciousProfileExamples", JSONArray().apply {
            suspiciousProfileExamples.forEach { group -> put(JSONArray(group)) }
        })
    }.toString(2)

    fun toMarkdown(): String = buildString {
        appendLine("# Project Superhuman local food database audit")
        appendLine()
        appendLine("- Total records: $totalRecords")
        appendLine("- Unique canonical food identities: $uniqueCanonicalFoods")
        appendLine("- Core records: $coreRecords")
        appendLine("- Duplicate candidate groups: $duplicateCandidateGroups")
        appendLine("- Intentional raw/core source snapshot pairs: $exactSourceSnapshotPairs")
        appendLine("- High-confidence records: $highConfidenceRecords")
        appendLine("- Records needing review: $recordsNeedingReview")
        appendLine("- Foods missing taxonomy tags: $foodsMissingTags")
        appendLine("- Plant foods missing PLANT tag: $plantFoodsMissingPlantTag")
        appendLine("- Suspicious plant classifications: $suspiciousPlantClassifications")
        appendLine()
        appendLine("## Representative food checks")
        appendLine()
        appendLine("| Requested | Matched food | Source record | kcal | P | C | F | Micros | Preparation | Tags | Plant diversity |")
        appendLine("|---|---|---|---:|---:|---:|---:|---:|---|---|---|")
        representativeFoods.forEach { item ->
            appendLine(
                "| ${item.requested} | ${item.matchedName ?: "MISSING"} | ${item.sourceRecordId ?: "—"} | " +
                    "${item.kcal ?: "—"} | ${item.protein ?: "—"} | ${item.carbs ?: "—"} | ${item.fat ?: "—"} | " +
                    "${item.micronutrientCount ?: "—"} | ${item.preparationState?.name ?: "—"} | " +
                    "${item.tags.map { it.name }.sorted().joinToString(", ")} | ${item.plantDiversityEligible ?: "—"} |"
            )
        }
        appendLine()
        appendLine("## Nutrient coverage")
        appendLine()
        appendLine("| Nutrient | Valid | Missing | Zero | Suspicious zero | Coverage |")
        appendLine("|---|---:|---:|---:|---:|---:|")
        nutrientCoverage.forEach {
            appendLine("| ${it.nutrient} | ${it.valid} | ${it.missing} | ${it.zero} | ${it.suspiciousZero} | ${"%.1f".format(Locale.US, it.coveragePercent)}% |")
        }
        appendLine()
        appendLine("## Findings")
        findings.take(1500).forEach {
            appendLine("- [${it.severity}] ${it.foodName} (${it.foodId}) — ${it.code}: ${it.message}")
        }
        if (findings.size > 1500) appendLine("- … ${findings.size - 1500} additional findings retained in JSON.")
        appendLine()
        appendLine("## Duplicate examples")
        duplicateExamples.forEach { appendLine("- " + it.joinToString(" | ")) }
        appendLine()
        appendLine("## Suspicious identical nutrient-profile examples")
        suspiciousProfileExamples.forEach { appendLine("- " + it.joinToString(" | ")) }
    }
}

internal data class LocalFoodAuditRow(
    val id: String,
    val name: String,
    val normalizedName: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fibre: Double,
    val sugar: Double,
    val saturatedFat: Double,
    val sodiumMg: Double,
    val saltG: Double,
    val proteinKnown: Boolean,
    val carbsKnown: Boolean,
    val fatKnown: Boolean,
    val fibreKnown: Boolean,
    val sugarKnown: Boolean,
    val saturatedFatKnown: Boolean,
    val sodiumKnown: Boolean,
    val saltKnown: Boolean,
    val unit: String,
    val source: String,
    val sourceRecordId: String,
    val micronutrients: Map<String, NativeNutrient>,
    val unknownMicronutrients: Set<String>,
    val foodTags: Set<FoodTag>,
    val taxonomyVersion: Int,
    val isPlantFood: Boolean,
    val plantFoodKind: PlantFoodKind,
    val plantDiversityKey: String,
    val plantDiversityEligible: Boolean,
    val preparationState: FoodPreparationState,
    val servingQuantity: Double?,
    val servingUnit: String,
    val servingLabel: String
)

internal object LocalFoodAuditRules {
    private val preparationTerms = linkedMapOf(
        "raw" to FoodTag.RAW,
        "boiled" to FoodTag.BOILED,
        "steamed" to FoodTag.STEAMED,
        "baked" to FoodTag.BAKED,
        "roasted" to FoodTag.ROASTED,
        "grilled" to FoodTag.GRILLED,
        "broiled" to FoodTag.GRILLED,
        "fried" to FoodTag.FRIED,
        "canned" to FoodTag.CANNED,
        "frozen" to FoodTag.FROZEN,
        "dried" to FoodTag.DRIED,
        "dehydrated" to FoodTag.DRIED
    )

    private val nutrientMaxima = mapOf(
        "calcium" to 5_000.0,
        "chloride" to 70_000.0,
        "copper" to 100.0,
        "iron" to 1_000.0,
        "iodine" to 100_000.0,
        "magnesium" to 5_000.0,
        "manganese" to 100.0,
        "phosphorus" to 10_000.0,
        "potassium" to 20_000.0,
        "selenium" to 20_000.0,
        "sodium" to 50_000.0,
        "zinc" to 1_000.0,
        "vitamin_a" to 100_000.0,
        "vitamin_b1" to 1_000.0,
        "vitamin_b2" to 1_000.0,
        "niacin" to 2_000.0,
        "pantothenic_acid" to 1_000.0,
        "vitamin_b6" to 1_000.0,
        "biotin" to 100_000.0,
        "folate" to 100_000.0,
        "folic_acid" to 100_000.0,
        "folate_food" to 100_000.0,
        "folate_dfe" to 100_000.0,
        "vitamin_b12" to 100_000.0,
        "vitamin_c" to 10_000.0,
        "vitamin_d" to 100_000.0,
        "vitamin_e" to 5_000.0,
        "vitamin_k" to 100_000.0,
        "choline" to 10_000.0,
        "cholesterol" to 10_000.0,
        "caffeine" to 10_000.0,
        "water" to 100.5,
        "alcohol" to 100.5,
        "starch" to 100.5,
        "monounsaturated_fat" to 100.5,
        "polyunsaturated_fat" to 100.5,
        "trans_fat" to 100.5,
        "omega_3" to 100.5,
        "omega_6" to 100.5
    )

    private val nutrientExpectedUnits = mapOf(
        "calcium" to "mg",
        "chloride" to "mg",
        "copper" to "mg",
        "iron" to "mg",
        "iodine" to "µg",
        "magnesium" to "mg",
        "manganese" to "mg",
        "phosphorus" to "mg",
        "potassium" to "mg",
        "selenium" to "µg",
        "sodium" to "mg",
        "zinc" to "mg",
        "vitamin_a" to "µg",
        "vitamin_b1" to "mg",
        "vitamin_b2" to "mg",
        "niacin" to "mg",
        "pantothenic_acid" to "mg",
        "vitamin_b6" to "mg",
        "biotin" to "µg",
        "folate" to "µg",
        "folic_acid" to "µg",
        "folate_food" to "µg",
        "folate_dfe" to "µg",
        "vitamin_b12" to "µg",
        "vitamin_c" to "mg",
        "vitamin_d" to "µg",
        "vitamin_e" to "mg",
        "vitamin_k" to "µg",
        "choline" to "mg",
        "cholesterol" to "mg",
        "caffeine" to "mg",
        "water" to "g",
        "alcohol" to "g",
        "starch" to "g",
        "monounsaturated_fat" to "g",
        "polyunsaturated_fat" to "g",
        "trans_fat" to "g",
        "omega_3" to "g",
        "omega_6" to "g"
    )

    private fun canonicalNutrientUnit(raw: String): String = when (
        raw.trim().lowercase(Locale.ROOT)
            .replace("μ", "µ")
            .replace("mcg", "µg")
            .replace("ug", "µg")
    ) {
        "g", "gram", "grams" -> "g"
        "mg", "milligram", "milligrams" -> "mg"
        "µg", "microgram", "micrograms" -> "µg"
        else -> raw.trim()
    }

    fun canonicalIdentity(name: String): String =
        name.lowercase(Locale.ROOT)
            .replace(Regex("\\brocket\\b"), "arugula")
            .replace(Regex("\\bcourgette\\b"), "zucchini")
            .replace(Regex("\\baubergine\\b"), "eggplant")
            .replace(Regex("\\bgarbanzo beans?\\b"), "chickpea")
            .replace(Regex("\\b(apples|bananas|carrots|onions|peppers|walnuts|almonds|lentils|chickpeas|eggs|mushrooms|grapes|oats|seeds)\\b")) {
                when (it.value) {
                    "apples" -> "apple"
                    "bananas" -> "banana"
                    "carrots" -> "carrot"
                    "onions" -> "onion"
                    "peppers" -> "pepper"
                    "walnuts" -> "walnut"
                    "almonds" -> "almond"
                    "lentils" -> "lentil"
                    "chickpeas" -> "chickpea"
                    "eggs" -> "egg"
                    "mushrooms" -> "mushroom"
                    "grapes" -> "grape"
                    "oats" -> "oat"
                    "seeds" -> "seed"
                    else -> it.value
                }
            }
            .replace(Regex("\\b(leaves|leaf|fresh)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun validate(row: LocalFoodAuditRow): List<LocalFoodAuditFinding> {
        val findings = mutableListOf<LocalFoodAuditFinding>()
        fun add(code: String, severity: LocalFoodAuditSeverity, message: String) {
            findings += LocalFoodAuditFinding(row.id, row.name, code, severity, message)
        }
        fun knownInvalid(value: Double, known: Boolean, label: String) {
            if (known && (!value.isFinite() || value < 0.0)) add("invalid_$label", LocalFoodAuditSeverity.ERROR, "$label is negative or non-finite")
        }

        knownInvalid(row.kcal, true, "energy")
        knownInvalid(row.protein, row.proteinKnown, "protein")
        knownInvalid(row.carbs, row.carbsKnown, "carbohydrate")
        knownInvalid(row.fat, row.fatKnown, "fat")
        knownInvalid(row.fibre, row.fibreKnown, "fibre")
        knownInvalid(row.sugar, row.sugarKnown, "sugars")
        knownInvalid(row.saturatedFat, row.saturatedFatKnown, "saturated_fat")
        knownInvalid(row.sodiumMg, row.sodiumKnown, "sodium")
        knownInvalid(row.saltG, row.saltKnown, "salt")

        if (row.unit.trim().lowercase(Locale.ROOT) != "100 g") {
            add("reference_basis", LocalFoodAuditSeverity.WARNING, "Local reference row is not stored on a 100 g basis")
        }

        listOf(
            "protein" to (row.protein to row.proteinKnown),
            "carbohydrate" to (row.carbs to row.carbsKnown),
            "fat" to (row.fat to row.fatKnown),
            "fibre" to (row.fibre to row.fibreKnown),
            "sugars" to (row.sugar to row.sugarKnown),
            "saturated_fat" to (row.saturatedFat to row.saturatedFatKnown)
        ).forEach { (label, pair) ->
            if (pair.second && pair.first > 100.5) add("mass_$label", LocalFoodAuditSeverity.ERROR, "$label exceeds 100 g per 100 g")
        }

        if (row.sugarKnown && row.carbsKnown && row.sugar > row.carbs + max(0.5, row.carbs * 0.05)) {
            add("sugars_gt_carbs", LocalFoodAuditSeverity.ERROR, "Sugars exceed total carbohydrate")
        }
        if (row.saturatedFatKnown && row.fatKnown && row.saturatedFat > row.fat + max(0.2, row.fat * 0.03)) {
            add("satfat_gt_fat", LocalFoodAuditSeverity.ERROR, "Saturated fat exceeds total fat")
        }

        if (row.fatKnown) {
            fun fatComponent(id: String): Double? = row.micronutrients[id]
                ?.takeIf { canonicalNutrientUnit(it.unit) == "g" }
                ?.valuePer100

            val mono = fatComponent("monounsaturated_fat")
            val poly = fatComponent("polyunsaturated_fat")
            val tolerance = max(0.25, row.fat * 0.05)
            if (mono != null && mono > row.fat + tolerance) {
                add("monofat_gt_fat", LocalFoodAuditSeverity.ERROR, "Monounsaturated fat exceeds total fat")
            }
            if (poly != null && poly > row.fat + tolerance) {
                add("polyfat_gt_fat", LocalFoodAuditSeverity.ERROR, "Polyunsaturated fat exceeds total fat")
            }
            if (row.saturatedFatKnown && mono != null && poly != null &&
                row.saturatedFat + mono + poly > row.fat + max(0.5, row.fat * 0.10)
            ) {
                add("fat_components_gt_total", LocalFoodAuditSeverity.WARNING, "Major fatty-acid classes materially exceed total fat")
            }

            val omega3 = fatComponent("omega_3")
            val omega6 = fatComponent("omega_6")
            if (poly != null && omega3 != null && omega3 > poly + 0.1) {
                add("omega3_gt_polyfat", LocalFoodAuditSeverity.WARNING, "Omega-3 exceeds total polyunsaturated fat")
            }
            if (poly != null && omega6 != null && omega6 > poly + 0.1) {
                add("omega6_gt_polyfat", LocalFoodAuditSeverity.WARNING, "Omega-6 exceeds total polyunsaturated fat")
            }
        }
        if (row.proteinKnown && row.carbsKnown && row.fatKnown) {
            val majorMass = row.protein + row.carbs + row.fat
            if (majorMass > 105.0) add("major_mass", LocalFoodAuditSeverity.ERROR, "Protein + carbohydrate + fat exceed a plausible 100 g composition")

            val alcoholG = row.micronutrients["alcohol"]
                ?.takeIf { canonicalNutrientUnit(it.unit) == "g" }
                ?.valuePer100 ?: 0.0
            val implied = row.protein * 4.0 + row.carbs * 4.0 + row.fat * 9.0 + alcoholG * 7.0
            val tolerance = max(45.0, max(row.kcal, implied) * 0.35)
            if (abs(implied - row.kcal) > tolerance) {
                add(
                    "energy_macro_conflict",
                    LocalFoodAuditSeverity.WARNING,
                    "Macro-derived energy materially disagrees with source kcal; investigate identity, basis or source mapping"
                )
            }

            row.micronutrients["water"]
                ?.takeIf { canonicalNutrientUnit(it.unit) == "g" }
                ?.let { water ->
                    val approximateMass = majorMass + water.valuePer100 + alcoholG
                    if (approximateMass > 110.0) {
                        add(
                            "component_mass_conflict",
                            LocalFoodAuditSeverity.ERROR,
                            "Protein + carbohydrate + fat + water + alcohol exceed a plausible 100 g composition"
                        )
                    }
                }
        }

        if (row.sodiumKnown && row.sodiumMg > 50_000.0) add("sodium_unit", LocalFoodAuditSeverity.ERROR, "Sodium exceeds a conservative physical plausibility ceiling")
        if (row.saltKnown && row.saltG > 100.5) add("salt_unit", LocalFoodAuditSeverity.ERROR, "Salt exceeds 100 g per 100 g")
        if (row.sodiumKnown && row.saltKnown) {
            val expectedSalt = NutritionIntegrity.sodiumMgToSaltG(row.sodiumMg)
            if (abs(row.saltG - expectedSalt) > max(0.15, expectedSalt * 0.18)) {
                add("salt_sodium_conflict", LocalFoodAuditSeverity.ERROR, "Salt and sodium disagree beyond tolerance")
            }
        }

        row.micronutrients.forEach { (id, nutrient) ->
            if (!nutrient.valuePer100.isFinite() || nutrient.valuePer100 < 0.0) {
                add("micro_invalid_$id", LocalFoodAuditSeverity.ERROR, "$id is negative or non-finite")
                return@forEach
            }
            nutrientMaxima[id]?.let { maxValue ->
                if (nutrient.valuePer100 > maxValue) {
                    add("micro_unit_$id", LocalFoodAuditSeverity.WARNING, "$id exceeds a conservative plausibility ceiling; check mg/µg/g conversion")
                }
            }
            if (nutrient.unit.isBlank()) {
                add("micro_unit_missing_$id", LocalFoodAuditSeverity.WARNING, "$id has no unit")
            } else {
                nutrientExpectedUnits[id]?.let { expected ->
                    val actual = canonicalNutrientUnit(nutrient.unit)
                    if (actual != expected) {
                        add(
                            "micro_unit_definition_$id",
                            LocalFoodAuditSeverity.ERROR,
                            "$id uses '$actual' but the canonical database unit is '$expected'"
                        )
                    }
                }
            }
            if (nutrient.valuePer100 == 0.0 &&
                nutrient.evidenceKind in setOf(
                    NutrientEvidenceKind.UNSPECIFIED,
                    NutrientEvidenceKind.GENERIC_INFERRED,
                    NutrientEvidenceKind.MISSING
                )
            ) {
                add("micro_zero_uncertain_$id", LocalFoodAuditSeverity.INFO, "$id is zero without strong source evidence; verify that zero is not a missing-value placeholder")
            }
        }

        val contradictoryKnownness = row.unknownMicronutrients.intersect(row.micronutrients.keys)
        if (contradictoryKnownness.isNotEmpty()) {
            add(
                "micro_known_unknown_conflict",
                LocalFoodAuditSeverity.ERROR,
                "Nutrients are simultaneously stored as known and unknown: " +
                    contradictoryKnownness.sorted().joinToString(", ")
            )
        }

        row.micronutrients["sodium"]?.let { sodium ->
            if (row.sodiumKnown && canonicalNutrientUnit(sodium.unit) == "mg") {
                val tolerance = max(0.5, row.sodiumMg * 0.01)
                if (abs(sodium.valuePer100 - row.sodiumMg) > tolerance) {
                    add(
                        "sodium_duplicate_field_conflict",
                        LocalFoodAuditSeverity.ERROR,
                        "Sodium macro field and micronutrient evidence disagree"
                    )
                }
            }
        }

        if (row.source.isBlank() || row.sourceRecordId.isBlank()) {
            add("provenance_missing", LocalFoodAuditSeverity.WARNING, "Source or source record ID is missing")
        } else if (row.source.contains("USDA", ignoreCase = true) && row.sourceRecordId.toLongOrNull() == null) {
            add("usda_provenance_id", LocalFoodAuditSeverity.WARNING, "USDA-backed record does not retain a numeric FDC source ID")
        }
        if (row.foodTags.isEmpty() || row.foodTags == setOf(FoodTag.OTHER)) {
            add("taxonomy_missing", LocalFoodAuditSeverity.WARNING, "Food has no useful deterministic taxonomy")
        }
        if (row.isPlantFood && FoodTag.PLANT !in row.foodTags) {
            add("plant_tag_missing", LocalFoodAuditSeverity.ERROR, "Plant-classified food is missing PLANT tag")
        }
        if (FoodTag.PLANT in row.foodTags && FoodTag.ANIMAL_DERIVED in row.foodTags) {
            add("plant_animal_conflict", LocalFoodAuditSeverity.WARNING, "Food is simultaneously tagged PLANT and ANIMAL_DERIVED")
        }
        if (row.plantDiversityEligible && (!row.isPlantFood || row.plantDiversityKey.isBlank())) {
            add("plant_diversity_identity", LocalFoodAuditSeverity.ERROR, "Plant-diversity eligibility lacks a stable plant identity")
        }
        if (row.plantDiversityEligible && row.foodTags.any { it in setOf(FoodTag.OIL_FAT, FoodTag.CONFECTIONERY, FoodTag.DESSERT, FoodTag.MIXED_DISH) }) {
            add("plant_diversity_processed", LocalFoodAuditSeverity.WARNING, "Processed/composite food is eligible for plant diversity")
        }

        val lower = row.name.lowercase(Locale.ROOT)
        preparationTerms.forEach { (term, tag) ->
            if (Regex("\\b" + Regex.escape(term) + "\\b").containsMatchIn(lower) && tag !in row.foodTags) {
                add("prep_tag_$term", LocalFoodAuditSeverity.WARNING, "Name says '$term' but preparation tag is absent")
            }
        }
        val prepTags = row.foodTags.intersect(
            setOf(FoodTag.RAW, FoodTag.BOILED, FoodTag.STEAMED, FoodTag.BAKED, FoodTag.ROASTED, FoodTag.GRILLED, FoodTag.FRIED, FoodTag.CANNED, FoodTag.FROZEN, FoodTag.DRIED)
        )
        if (FoodTag.RAW in prepTags && prepTags.size > 1) {
            add("prep_conflict", LocalFoodAuditSeverity.WARNING, "RAW appears with another preparation-state tag")
        }
        val inferredPreparation = FoodEvidenceEngine.inferPreparationState(row.name)
        if (inferredPreparation != FoodPreparationState.UNSPECIFIED &&
            row.preparationState != inferredPreparation
        ) {
            add(
                "prep_state_mismatch",
                LocalFoodAuditSeverity.WARNING,
                "Stored preparation state ${row.preparationState} disagrees with the food name ($inferredPreparation)"
            )
        }
        row.servingQuantity?.let { quantity ->
            if (!quantity.isFinite() || quantity <= 0.0 || quantity > 100_000.0) {
                add("serving_quantity", LocalFoodAuditSeverity.ERROR, "Serving quantity is implausible")
            }
            if (row.servingUnit.isBlank()) {
                add("serving_unit", LocalFoodAuditSeverity.WARNING, "Serving quantity is present but serving unit is missing")
            }
        }
        if (row.servingLabel.isNotBlank() && row.servingQuantity == null) {
            add("serving_reference", LocalFoodAuditSeverity.WARNING, "Serving label is present without a reference quantity")
        }

        if (FoodTag.OIL_FAT in row.foodTags && lower.contains("oil") && row.fatKnown && row.fat < 80.0) {
            add("identity_oil", LocalFoodAuditSeverity.WARNING, "Plain oil identity has unexpectedly low total fat")
        }
        if ((lower == "sugar" || lower.startsWith("sugar,")) && row.carbsKnown && row.carbs < 90.0) {
            add("identity_sugar", LocalFoodAuditSeverity.WARNING, "Sugar identity has unexpectedly low carbohydrate")
        }
        if ((FoodTag.CHICKEN in row.foodTags || FoodTag.BEEF in row.foodTags || FoodTag.PORK in row.foodTags || FoodTag.LAMB in row.foodTags) &&
            row.fibreKnown && row.fibre > 1.0 && FoodTag.MIXED_DISH !in row.foodTags
        ) {
            add("identity_animal_fibre", LocalFoodAuditSeverity.WARNING, "Plain animal muscle food has meaningful fibre")
        }
        if (FoodTag.MILK in row.foodTags && "calcium" !in row.micronutrients) {
            add("identity_milk_calcium", LocalFoodAuditSeverity.WARNING, "Milk record is missing calcium")
        }
        if (FoodTag.MILK in row.foodTags && (row.micronutrients["calcium"]?.valuePer100 ?: 0.0) <= 0.0) {
            add("identity_milk_calcium_zero", LocalFoodAuditSeverity.WARNING, "Milk record has zero/unknown calcium")
        }

        val looksFortified = listOf("fortified", "enriched", "added vitamin", "nutritional yeast")
            .any(lower::contains)
        if (row.isPlantFood &&
            FoodTag.PREPARED_FOOD !in row.foodTags &&
            (row.micronutrients["cholesterol"]?.valuePer100 ?: 0.0) > 0.5
        ) {
            add("identity_plant_cholesterol", LocalFoodAuditSeverity.WARNING, "Plant food reports cholesterol; verify identity/source mapping")
        }
        if (row.isPlantFood &&
            !looksFortified &&
            FoodTag.FERMENTED !in row.foodTags &&
            (row.micronutrients["vitamin_b12"]?.valuePer100 ?: 0.0) > 0.2
        ) {
            add("identity_plant_b12", LocalFoodAuditSeverity.WARNING, "Unfortified/non-fermented plant food reports meaningful vitamin B12")
        }

        return findings
    }

    fun profileFingerprint(row: LocalFoodAuditRow): String {
        val macro = listOf(row.kcal, row.protein, row.carbs, row.fat, row.fibre, row.sugar)
            .joinToString("|") { (round(it * 100.0) / 100.0).toString() }
        val micro = row.micronutrients.entries.sortedBy { it.key }.joinToString("|") {
            it.key + "=" + (round(it.value.valuePer100 * 100.0) / 100.0)
        }
        return macro + "||" + micro
    }
}

internal object LocalFoodDatabaseAuditor {
    private val essentialMicronutrients = setOf(
        "calcium", "chloride", "copper", "iron", "iodine", "magnesium", "manganese",
        "phosphorus", "potassium", "selenium", "sodium", "zinc",
        "vitamin_a", "vitamin_b1", "vitamin_b2", "niacin", "pantothenic_acid",
        "vitamin_b6", "biotin", "folate", "vitamin_b12", "vitamin_c", "vitamin_d",
        "vitamin_e", "vitamin_k", "choline"
    )

    private val monitoredNutrients = listOf(
        "protein", "carbohydrate", "fat", "saturated_fat", "fibre", "sugars", "sodium", "salt",
        "potassium", "calcium", "magnesium", "phosphorus", "iron", "zinc", "copper", "manganese",
        "selenium", "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e", "vitamin_k",
        "vitamin_b1", "vitamin_b2", "niacin", "pantothenic_acid", "vitamin_b6", "folate",
        "folic_acid", "folate_food", "folate_dfe", "vitamin_b12", "choline", "omega_3", "omega_6", "cholesterol", "caffeine", "water",
        "starch", "alcohol", "monounsaturated_fat", "polyunsaturated_fat", "trans_fat"
    )

    fun audit(context: Context): LocalFoodDatabaseAuditReport =
        LargeFoodDb(context.applicationContext).use { helper -> audit(helper.readableDatabase) }

    fun writeReports(context: Context): Pair<File, File> = writeReports(context, audit(context))

    fun writeReports(context: Context, report: LocalFoodDatabaseAuditReport): Pair<File, File> {
        val dir = File(context.filesDir, "nutrition_audits").apply { mkdirs() }
        val json = File(dir, "local_food_audit_latest.json").apply { writeText(report.toJson()) }
        val md = File(dir, "local_food_audit_latest.md").apply { writeText(report.toMarkdown()) }
        return json to md
    }

    internal fun audit(db: SQLiteDatabase): LocalFoodDatabaseAuditReport {
        val rows = mutableListOf<LocalFoodAuditRow>()
        db.rawQuery(
            """
            SELECT id, name, normalized_name, kcal, protein, carbs, fat, fibre, sugar,
                   saturated_fat, sodium_mg, salt_g,
                   protein_known, carbs_known, fat_known, fibre_known, sugar_known,
                   saturated_fat_known, sodium_known, salt_known,
                   unit, source, source_record_id, micronutrients_json, unknown_micronutrients_json,
                   food_tags_json, taxonomy_version, plant_food, plant_food_kind,
                   plant_diversity_key, plant_diversity_eligible,
                   preparation_state, serving_quantity, serving_unit, serving_label
            FROM food_reference
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += LocalFoodAuditRow(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    normalizedName = cursor.getString(2),
                    kcal = cursor.getDouble(3),
                    protein = cursor.getDouble(4),
                    carbs = cursor.getDouble(5),
                    fat = cursor.getDouble(6),
                    fibre = cursor.getDouble(7),
                    sugar = cursor.getDouble(8),
                    saturatedFat = cursor.getDouble(9),
                    sodiumMg = cursor.getDouble(10),
                    saltG = cursor.getDouble(11),
                    proteinKnown = cursor.getInt(12) != 0,
                    carbsKnown = cursor.getInt(13) != 0,
                    fatKnown = cursor.getInt(14) != 0,
                    fibreKnown = cursor.getInt(15) != 0,
                    sugarKnown = cursor.getInt(16) != 0,
                    saturatedFatKnown = cursor.getInt(17) != 0,
                    sodiumKnown = cursor.getInt(18) != 0,
                    saltKnown = cursor.getInt(19) != 0,
                    unit = cursor.getString(20),
                    source = cursor.getString(21),
                    sourceRecordId = cursor.getString(22),
                    micronutrients = decodeMicros(cursor.getString(23)),
                    unknownMicronutrients = decodeStringSet(cursor.getString(24)),
                    foodTags = decodeTags(cursor.getString(25)),
                    taxonomyVersion = cursor.getInt(26),
                    isPlantFood = cursor.getInt(27) != 0,
                    plantFoodKind = runCatching { PlantFoodKind.valueOf(cursor.getString(28)) }.getOrDefault(PlantFoodKind.NONE),
                    plantDiversityKey = cursor.getString(29),
                    plantDiversityEligible = cursor.getInt(30) != 0,
                    preparationState = runCatching { FoodPreparationState.valueOf(cursor.getString(31)) }.getOrDefault(FoodPreparationState.UNSPECIFIED),
                    servingQuantity = if (cursor.isNull(32)) null else cursor.getDouble(32),
                    servingUnit = cursor.getString(33),
                    servingLabel = cursor.getString(34)
                )
            }
        }

        val representativeFoods = representativeSnapshots(rows)
        val representativeMissingFindings = representativeFoods
            .filter { it.matchedFoodId == null }
            .map {
                LocalFoodAuditFinding(
                    foodId = "representative:" + it.requested,
                    foodName = it.requested,
                    code = "representative_food_missing",
                    severity = LocalFoodAuditSeverity.WARNING,
                    message = "No local reference row matched the required representative food"
                )
            }
        val findings = rows.flatMap(LocalFoodAuditRules::validate) + representativeMissingFindings
        val errorIds = findings.filter { it.severity == LocalFoodAuditSeverity.ERROR }.map { it.foodId }.toSet()
        val warningIds = findings.filter { it.severity == LocalFoodAuditSeverity.WARNING }.map { it.foodId }.toSet()
        val reviewIds = errorIds + warningIds

        val canonicalGroups = rows.groupBy {
            LocalFoodAuditRules.canonicalIdentity(it.name) + "|" + preparationSignature(it.foodTags)
        }
        val duplicateGroups = canonicalGroups.values.filter { group ->
            group.map { it.sourceRecordId.ifBlank { it.id } }.distinct().size > 1
        }
        val intentionalSnapshots = rows.groupBy { it.sourceRecordId }
            .values
            .count { group -> group.size > 1 && group.any { it.id.startsWith("core:usda:") } && group.any { it.id.startsWith("usda:") } }

        val profileGroups = rows.groupBy(LocalFoodAuditRules::profileFingerprint)
            .values
            .filter { group ->
                group.size >= 3 &&
                    group.map { LocalFoodAuditRules.canonicalIdentity(it.name) }.distinct().size >= 3
            }

        val coverage = monitoredNutrients.map { nutrient ->
            var valid = 0
            var missing = 0
            var zero = 0
            var suspiciousZero = 0
            rows.forEach { row ->
                val valueAndKnown: Pair<Double, Boolean>? = when (nutrient) {
                    "protein" -> row.protein to row.proteinKnown
                    "carbohydrate" -> row.carbs to row.carbsKnown
                    "fat" -> row.fat to row.fatKnown
                    "saturated_fat" -> row.saturatedFat to row.saturatedFatKnown
                    "fibre" -> row.fibre to row.fibreKnown
                    "sugars" -> row.sugar to row.sugarKnown
                    "sodium" -> row.sodiumMg to row.sodiumKnown
                    "salt" -> row.saltG to row.saltKnown
                    else -> row.micronutrients[nutrient]?.let { it.valuePer100 to true }
                }
                if (valueAndKnown == null || !valueAndKnown.second) {
                    missing++
                } else {
                    valid++
                    if (valueAndKnown.first == 0.0) {
                        zero++
                        val microEvidence = row.micronutrients[nutrient]?.evidenceKind
                        if (microEvidence in setOf(
                                NutrientEvidenceKind.UNSPECIFIED,
                                NutrientEvidenceKind.GENERIC_INFERRED,
                                NutrientEvidenceKind.MISSING
                            )
                        ) suspiciousZero++
                    }
                }
            }
            LocalFoodNutrientCoverage(nutrient, valid, missing, zero, suspiciousZero)
        }

        val foodsMissingTags = rows.count { it.foodTags.isEmpty() || it.foodTags == setOf(FoodTag.OTHER) }
        val plantMissing = rows.count { it.isPlantFood && FoodTag.PLANT !in it.foodTags }
        val suspiciousPlant = rows.count {
            (FoodTag.PLANT in it.foodTags && FoodTag.ANIMAL_DERIVED in it.foodTags) ||
                (it.plantDiversityEligible && (!it.isPlantFood || it.plantDiversityKey.isBlank()))
        }

        val highConfidence = rows.count {
            it.id !in reviewIds &&
                it.sourceRecordId.isNotBlank() &&
                it.source.contains("USDA", ignoreCase = true) &&
                it.micronutrients.keys.count(essentialMicronutrients::contains) >= 12 &&
                it.foodTags.isNotEmpty()
        }

        return LocalFoodDatabaseAuditReport(
            generatedEpochMs = System.currentTimeMillis(),
            totalRecords = rows.size,
            uniqueCanonicalFoods = canonicalGroups.size,
            coreRecords = rows.count { it.id.startsWith("core:") },
            duplicateCandidateGroups = duplicateGroups.size,
            exactSourceSnapshotPairs = intentionalSnapshots,
            highConfidenceRecords = highConfidence,
            recordsNeedingReview = reviewIds.size,
            foodsMissingTags = foodsMissingTags,
            plantFoodsMissingPlantTag = plantMissing,
            suspiciousPlantClassifications = suspiciousPlant,
            representativeFoods = representativeFoods,
            findings = findings.sortedWith(
                compareByDescending<LocalFoodAuditFinding> { it.severity.ordinal }
                    .thenBy { it.foodName }
            ),
            nutrientCoverage = coverage,
            duplicateExamples = duplicateGroups.take(100).map { group -> group.map { it.name + " [" + it.id + "]" } },
            suspiciousProfileExamples = profileGroups.take(100).map { group -> group.map { it.name + " [" + it.id + "]" } }
        )
    }

    private fun representativeSnapshots(rows: List<LocalFoodAuditRow>): List<LocalFoodRepresentativeSnapshot> {
        val specs = listOf(
            "Apple, raw" to listOf("apple", "raw"),
            "Banana, raw" to listOf("banana", "raw"),
            "Rocket / arugula" to listOf("arugula", "raw"),
            "Spinach, raw" to listOf("spinach", "raw"),
            "Broccoli, raw" to listOf("broccoli", "raw"),
            "Potato, raw" to listOf("potato", "raw"),
            "Potato, boiled" to listOf("potato", "boiled"),
            "Rice, dry/raw" to listOf("rice", "raw"),
            "Rice, cooked" to listOf("rice", "cooked"),
            "Lentils" to listOf("lentil"),
            "Chickpeas" to listOf("chickpea"),
            "Walnuts" to listOf("walnut"),
            "Flaxseed" to listOf("flaxseed"),
            "Olive oil" to listOf("olive", "oil"),
            "Chicken breast, raw" to listOf("chicken", "breast", "raw"),
            "Chicken breast, grilled" to listOf("chicken", "breast", "grilled"),
            "Beef" to listOf("beef"),
            "Salmon" to listOf("salmon"),
            "Egg, boiled" to listOf("egg", "boiled"),
            "Milk, whole" to listOf("milk", "whole"),
            "Greek yogurt, plain" to listOf("greek", "yogurt"),
            "Cheddar cheese" to listOf("cheddar", "cheese"),
            "Sourdough bread" to listOf("sourdough")
        )
        return specs.map { (requested, terms) ->
            val match = rows
                .asSequence()
                .filter { row ->
                    val haystack = (row.name + " " + row.normalizedName).lowercase(Locale.ROOT)
                    terms.all(haystack::contains)
                }
                .sortedWith(
                    compareBy<LocalFoodAuditRow> {
                        when {
                            it.id.startsWith("core:") && !it.id.startsWith("core:usda:") -> 0
                            it.source.contains("USDA Foundation", ignoreCase = true) -> 1
                            it.id.startsWith("core:usda:") -> 2
                            it.source.contains("USDA SR", ignoreCase = true) -> 3
                            it.source.contains("USDA FNDDS", ignoreCase = true) -> 4
                            else -> 5
                        }
                    }.thenByDescending { it.micronutrients.size }
                        .thenBy { it.name.length }
                )
                .firstOrNull()
            LocalFoodRepresentativeSnapshot(
                requested = requested,
                matchedFoodId = match?.id,
                matchedName = match?.name,
                source = match?.source,
                sourceRecordId = match?.sourceRecordId,
                kcal = match?.kcal,
                protein = match?.protein,
                carbs = match?.carbs,
                fat = match?.fat,
                micronutrientCount = match?.micronutrients?.size,
                tags = match?.foodTags.orEmpty(),
                preparationState = match?.preparationState,
                plantDiversityEligible = match?.plantDiversityEligible
            )
        }
    }

    private fun preparationSignature(tags: Set<FoodTag>): String =
        tags.intersect(
            setOf(FoodTag.RAW, FoodTag.BOILED, FoodTag.STEAMED, FoodTag.BAKED, FoodTag.ROASTED, FoodTag.GRILLED, FoodTag.FRIED, FoodTag.CANNED, FoodTag.FROZEN, FoodTag.DRIED)
        ).map { it.name }.sorted().joinToString(",")

    private fun decodeTags(raw: String?): Set<FoodTag> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val a = JSONArray(raw)
            buildSet {
                for (i in 0 until a.length()) runCatching { FoodTag.valueOf(a.optString(i)) }.getOrNull()?.let(::add)
            }
        }.getOrDefault(emptySet())
    }

    private fun decodeStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val a = JSONArray(raw)
            buildSet { for (i in 0 until a.length()) a.optString(i).takeIf(String::isNotBlank)?.let(::add) }
        }.getOrDefault(emptySet())
    }

    private fun decodeMicros(raw: String?): Map<String, NativeNutrient> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = root.optJSONObject(id) ?: continue
                    val value = item.optDouble("value", Double.NaN)
                    if (!value.isFinite() || value < 0.0) continue
                    put(
                        id,
                        NativeNutrient(
                            id = id,
                            label = item.optString("label").ifBlank { id },
                            valuePer100 = value,
                            unit = item.optString("unit"),
                            evidenceKind = item.optString("evidenceKind")
                                .let { rawKind -> runCatching { NutrientEvidenceKind.valueOf(rawKind) }.getOrDefault(NutrientEvidenceKind.UNSPECIFIED) },
                            source = item.optString("source"),
                            sourceRecordId = item.optString("sourceRecordId"),
                            derivedFrom = item.optString("derivedFrom")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }
}
