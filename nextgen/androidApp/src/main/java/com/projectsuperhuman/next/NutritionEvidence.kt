package com.projectsuperhuman.next

import java.util.Locale

internal const val NUTRITION_CANONICAL_SCHEMA_VERSION = 4

internal enum class FoodIdentityKind {
    UNKNOWN,
    GENERIC_FOOD,
    BRANDED_PRODUCT,
    RECIPE,
    COMPOSITE_ESTIMATE,
    USER_CREATED,
    INGREDIENT,
    MEAL_GROUP
}

internal enum class FoodDataSourceType {
    UNKNOWN,
    PACKAGE_LABEL,
    MANUFACTURER,
    USDA_FOUNDATION,
    USDA_FNDDS,
    USDA_SR_LEGACY,
    OPEN_FOOD_FACTS,
    PROJECT_SUPERHUMAN_REFERENCE,
    USER_CREATED,
    USER_CORRECTED,
    COMPOSITE_ESTIMATE
}

internal enum class NutrientEvidenceKind {
    UNSPECIFIED,
    LABEL_REPORTED,
    LABORATORY_REFERENCE,
    REFERENCE_DATABASE,
    SOURCE_REPORTED,
    DERIVED,
    GENERIC_INFERRED,
    USER_ENTERED,
    MISSING
}

internal enum class FoodVerificationState {
    UNVERIFIED,
    SOURCE_VALIDATED,
    INTEGRITY_VALIDATED,
    LABEL_VERIFIED,
    USER_CORRECTED,
    BRAND_VERIFIED,
    CONFLICTED
}

internal enum class FoodDataConfidence {
    UNASSESSED,
    HIGH,
    MEDIUM_HIGH,
    MEDIUM,
    LOW,
    CONFLICTED
}

internal enum class EnergyEvidenceKind {
    UNKNOWN,
    REPORTED_KCAL,
    CONVERTED_KJ,
    CALCULATED_FROM_MACROS,
    GENERIC_ESTIMATE,
    RECIPE_CALCULATION,
    USER_ENTERED
}

internal enum class FoodPreparationState {
    UNSPECIFIED,
    RAW,
    COOKED,
    BOILED,
    POACHED,
    GRILLED,
    ROASTED,
    BAKED,
    FRIED,
    STEAMED,
    CANNED,
    DRAINED,
    FROZEN,
    DRIED,
    RECONSTITUTED
}

internal enum class DensityEvidenceSource {
    UNKNOWN,
    MANUFACTURER,
    REFERENCE_DATABASE,
    CURATED_GENERIC,
    HEURISTIC
}

internal enum class FoodLabelEvidenceType {
    FRONT,
    NUTRITION_LABEL,
    INGREDIENTS
}

internal data class FoodLabelEvidence(
    val type: FoodLabelEvidenceType,
    val uri: String,
    val capturedEpochMs: Long,
    val sourceRecordId: String = ""
)

internal data class FoodOcrCandidate(
    val field: String,
    val rawText: String,
    val numericValue: Double? = null,
    val unit: String = "",
    val confidence: Double? = null
)

/**
 * OCR is deliberately a proposal layer. Implementations may extract candidates from images, but
 * candidates must still pass unit normalisation, integrity validation and user review before they
 * can become canonical food evidence.
 */
internal interface FoodLabelOcrGateway {
    suspend fun extract(evidence: FoodLabelEvidence): List<FoodOcrCandidate>
}

internal data class CanonicalNutrientEvidence(
    val id: String,
    val label: String,
    val valuePerBasis: Double,
    val unit: String,
    val known: Boolean,
    val evidenceKind: NutrientEvidenceKind,
    val source: String,
    val sourceRecordId: String = "",
    val derivedFrom: String = ""
)

internal data class CarbohydrateEvidenceProfile(
    val total: CanonicalNutrientEvidence? = null,
    val available: CanonicalNutrientEvidence? = null,
    val fibre: CanonicalNutrientEvidence? = null,
    val sugars: CanonicalNutrientEvidence? = null,
    val addedSugars: CanonicalNutrientEvidence? = null,
    val starch: CanonicalNutrientEvidence? = null,
    val polyols: CanonicalNutrientEvidence? = null
)

internal data class CanonicalFoodRecord(
    val id: String,
    val identityKind: FoodIdentityKind,
    val name: String,
    val originalName: String,
    val brand: String,
    val barcode: String?,
    val preparationState: FoodPreparationState,
    val sourceType: FoodDataSourceType,
    val sourceName: String,
    val sourceRecordId: String,
    val sourceRevision: String,
    val verification: FoodVerificationState,
    val confidence: FoodDataConfidence,
    val basisAmount: Double,
    val basisUnit: FoodUnit,
    val carbohydrateDefinition: CarbohydrateDefinition,
    val carbohydrates: CarbohydrateEvidenceProfile,
    val energyEvidence: EnergyEvidenceKind,
    val densityGPerMl: Double?,
    val densitySource: DensityEvidenceSource,
    val imageReferences: Map<String, String>,
    val nutrients: Map<String, CanonicalNutrientEvidence>,
    val integrityWarnings: List<String>,
    val canonicalSchemaVersion: Int = NUTRITION_CANONICAL_SCHEMA_VERSION
)

/**
 * Canonical evidence policy used at the ingestion boundary. External source structures are allowed
 * to differ, but anything consumed by the diary is enriched here into one stable evidence model.
 *
 * This deliberately uses categorical confidence instead of a pseudo-precise percentage.
 */
internal object FoodEvidenceEngine {
    fun enrich(food: NativeFood): NativeFood {
        val sourceType = if (food.sourceType == FoodDataSourceType.UNKNOWN) {
            sourceTypeFor(food.source)
        } else food.sourceType

        val identityKind = if (food.identityKind == FoodIdentityKind.UNKNOWN) {
            identityKindFor(food, sourceType)
        } else food.identityKind

        val preparation = if (food.preparationState == FoodPreparationState.UNSPECIFIED) {
            inferPreparationState(food.name + " " + food.searchText)
        } else food.preparationState

        // Plant identity is canonical derived metadata, not something callers should have to
        // remember to set. Explicit valid identities are preserved; otherwise every food entering
        // the evidence layer is classified from its name/search/ingredient evidence.
        val plantIdentity = if (food.isPlantFood && food.plantDiversityKey.isNotBlank()) {
            PlantFoodIdentity(true, food.plantFoodKind, food.plantDiversityKey)
        } else {
            PlantFoodClassifier.classify(food.name, food.searchText, food.ingredientsText)
        }
        val foodTags = (food.foodTags + FoodTaxonomyClassifier.classify(
            food.name,
            food.searchText,
            food.ingredientsText,
            plantIdentity
        )).toSet()

        val warnings = buildList {
            addAll(food.sourceWarnings.filter(String::isNotBlank))
            food.nutritionIntegrityWarning?.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()

        val verification = if (food.verificationState == FoodVerificationState.UNVERIFIED) {
            inferVerification(food, sourceType, warnings)
        } else food.verificationState

        val confidence = if (food.confidence == FoodDataConfidence.UNASSESSED) {
            inferConfidence(food, sourceType, verification, warnings)
        } else food.confidence

        val defaultEvidence = evidenceKindForSource(sourceType, food.nutritionApproximate)
        val evidence = food.nutrientEvidence.toMutableMap()
        fun mark(id: String, known: Boolean) {
            if (known && id !in evidence) evidence[id] = defaultEvidence
        }
        mark("energy_kcal", food.kcalKnown)
        mark("protein", food.proteinKnown)
        mark("carbohydrate", food.carbsKnown)
        mark("fat", food.fatKnown)
        mark("saturated_fat", food.saturatedFatKnown)
        mark("fibre", food.fibreKnown)
        mark("sugars", food.sugarKnown)
        mark("salt", food.saltKnown)
        mark("sodium", food.sodiumKnown)

        val enrichedMicros = food.micronutrients.mapValues { (_, nutrient) ->
            if (nutrient.evidenceKind != NutrientEvidenceKind.UNSPECIFIED) nutrient
            else nutrient.copy(
                evidenceKind = defaultEvidence,
                source = nutrient.source.ifBlank { food.source },
                sourceRecordId = nutrient.sourceRecordId.ifBlank { food.sourceRecordId }
            )
        }

        val densitySource = when {
            food.densityGPerMl == null -> DensityEvidenceSource.UNKNOWN
            food.densitySource != DensityEvidenceSource.UNKNOWN -> food.densitySource
            food.densityApproximate -> DensityEvidenceSource.HEURISTIC
            sourceType == FoodDataSourceType.MANUFACTURER || sourceType == FoodDataSourceType.PACKAGE_LABEL ->
                DensityEvidenceSource.MANUFACTURER
            sourceType == FoodDataSourceType.USDA_FOUNDATION ||
                sourceType == FoodDataSourceType.USDA_FNDDS ||
                sourceType == FoodDataSourceType.USDA_SR_LEGACY ->
                DensityEvidenceSource.REFERENCE_DATABASE
            else -> DensityEvidenceSource.CURATED_GENERIC
        }

        val energyEvidence = if (food.energyEvidence != EnergyEvidenceKind.UNKNOWN) {
            food.energyEvidence
        } else if (!food.kcalKnown) {
            EnergyEvidenceKind.UNKNOWN
        } else when (sourceType) {
            FoodDataSourceType.USER_CREATED,
            FoodDataSourceType.USER_CORRECTED -> EnergyEvidenceKind.USER_ENTERED
            FoodDataSourceType.COMPOSITE_ESTIMATE -> EnergyEvidenceKind.GENERIC_ESTIMATE
            FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE ->
                if (food.nutritionApproximate) EnergyEvidenceKind.GENERIC_ESTIMATE else EnergyEvidenceKind.REPORTED_KCAL
            else -> EnergyEvidenceKind.REPORTED_KCAL
        }

        return food.copy(
            identityKind = identityKind,
            sourceType = sourceType,
            sourceRecordId = food.sourceRecordId.ifBlank { sourceRecordIdFor(food, sourceType) },
            sourceRevision = food.sourceRevision.ifBlank { sourceRevisionFor(food, sourceType) },
            verificationState = verification,
            confidence = confidence,
            preparationState = preparation,
            isPlantFood = plantIdentity.isPlantFood,
            plantFoodKind = plantIdentity.kind,
            plantDiversityKey = plantIdentity.diversityKey,
            foodTags = foodTags,
            foodTaxonomyVersion = FoodTaxonomyClassifier.SCHEMA_VERSION,
            sourceWarnings = warnings,
            nutrientEvidence = evidence,
            micronutrients = enrichedMicros,
            densitySource = densitySource,
            energyEvidence = energyEvidence,
            canonicalSchemaVersion = NUTRITION_CANONICAL_SCHEMA_VERSION
        )
    }

    fun canonicalize(food: NativeFood): CanonicalFoodRecord {
        val f = enrich(food)
        val defaultEvidence = evidenceKindForSource(f.sourceType, f.nutritionApproximate)
        val nutrients = linkedMapOf<String, CanonicalNutrientEvidence>()

        fun add(id: String, label: String, value: Double, unit: String, known: Boolean) {
            if (!known) return
            nutrients[id] = CanonicalNutrientEvidence(
                id = id,
                label = label,
                valuePerBasis = value,
                unit = unit,
                known = true,
                evidenceKind = f.nutrientEvidence[id] ?: defaultEvidence,
                source = f.source,
                sourceRecordId = f.sourceRecordId
            )
        }

        add("energy_kcal", "Energy", f.kcal, "kcal", f.kcalKnown)
        add("protein", "Protein", f.protein, "g", f.proteinKnown)
        add("carbohydrate", "Carbohydrate", f.carbs, "g", f.carbsKnown)
        add("fat", "Fat", f.fat, "g", f.fatKnown)
        add("saturated_fat", "Saturated fat", f.saturatedFat, "g", f.saturatedFatKnown)
        add("fibre", "Fibre", f.fibre, "g", f.fibreKnown)
        add("sugars", "Sugars", f.sugar, "g", f.sugarKnown)
        add("salt", "Salt", f.salt, "g", f.saltKnown)
        add("sodium", "Sodium", f.sodiumMg, "mg", f.sodiumKnown)

        f.micronutrients.values.forEach { nutrient ->
            nutrients[nutrient.id] = CanonicalNutrientEvidence(
                id = nutrient.id,
                label = nutrient.label,
                valuePerBasis = nutrient.valuePer100,
                unit = nutrient.unit,
                known = true,
                evidenceKind = nutrient.evidenceKind.takeUnless { it == NutrientEvidenceKind.UNSPECIFIED }
                    ?: defaultEvidence,
                source = nutrient.source.ifBlank { f.source },
                sourceRecordId = nutrient.sourceRecordId.ifBlank { f.sourceRecordId },
                derivedFrom = nutrient.derivedFrom
            )
        }

        val carbNutrient = nutrients["carbohydrate"]
        val carbohydrateProfile = CarbohydrateEvidenceProfile(
            total = if (f.carbohydrateDefinition == CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE) carbNutrient else null,
            available = if (f.carbohydrateDefinition == CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE) carbNutrient else null,
            fibre = nutrients["fibre"],
            sugars = nutrients["sugars"]
        )

        return CanonicalFoodRecord(
            id = f.id,
            identityKind = f.identityKind,
            name = f.name,
            originalName = f.originalName,
            brand = f.brand,
            barcode = f.barcode,
            preparationState = f.preparationState,
            sourceType = f.sourceType,
            sourceName = f.source,
            sourceRecordId = f.sourceRecordId,
            sourceRevision = f.sourceRevision,
            verification = f.verificationState,
            confidence = f.confidence,
            basisAmount = f.basisAmount,
            basisUnit = f.basisUnit,
            carbohydrateDefinition = f.carbohydrateDefinition,
            carbohydrates = carbohydrateProfile,
            energyEvidence = f.energyEvidence,
            densityGPerMl = f.densityGPerMl,
            densitySource = f.densitySource,
            imageReferences = f.imageReferences,
            nutrients = nutrients,
            integrityWarnings = f.sourceWarnings,
            canonicalSchemaVersion = f.canonicalSchemaVersion
        )
    }

    fun sourceTypeFor(source: String): FoodDataSourceType {
        val s = source.lowercase(Locale.ROOT)
        return when {
            "edited locally" in s || "user corrected" in s -> FoodDataSourceType.USER_CORRECTED
            "label reference" in s || "package label" in s -> FoodDataSourceType.PACKAGE_LABEL
            "manufacturer" in s || "brand verified" in s -> FoodDataSourceType.MANUFACTURER
            "foundation foods" in s -> FoodDataSourceType.USDA_FOUNDATION
            "fndds" in s -> FoodDataSourceType.USDA_FNDDS
            "sr legacy" in s -> FoodDataSourceType.USDA_SR_LEGACY
            "open food facts" in s -> FoodDataSourceType.OPEN_FOOD_FACTS
            "composite" in s || "estimate" in s -> FoodDataSourceType.COMPOSITE_ESTIMATE
            "user created" in s -> FoodDataSourceType.USER_CREATED
            "project superhuman" in s || "local reference" in s -> FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE
            else -> FoodDataSourceType.UNKNOWN
        }
    }

    fun evidenceKindForSource(sourceType: FoodDataSourceType, approximate: Boolean = false): NutrientEvidenceKind {
        if (approximate) return NutrientEvidenceKind.GENERIC_INFERRED
        return when (sourceType) {
            FoodDataSourceType.PACKAGE_LABEL,
            FoodDataSourceType.MANUFACTURER -> NutrientEvidenceKind.LABEL_REPORTED
            FoodDataSourceType.USDA_FOUNDATION -> NutrientEvidenceKind.LABORATORY_REFERENCE
            FoodDataSourceType.USDA_FNDDS,
            FoodDataSourceType.USDA_SR_LEGACY,
            FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE -> NutrientEvidenceKind.REFERENCE_DATABASE
            FoodDataSourceType.OPEN_FOOD_FACTS -> NutrientEvidenceKind.SOURCE_REPORTED
            FoodDataSourceType.USER_CREATED,
            FoodDataSourceType.USER_CORRECTED -> NutrientEvidenceKind.USER_ENTERED
            FoodDataSourceType.COMPOSITE_ESTIMATE -> NutrientEvidenceKind.GENERIC_INFERRED
            FoodDataSourceType.UNKNOWN -> NutrientEvidenceKind.SOURCE_REPORTED
        }
    }

    fun sourcePriority(food: NativeFood): Int = when (enrich(food).sourceType) {
        FoodDataSourceType.USER_CORRECTED -> 0
        FoodDataSourceType.PACKAGE_LABEL -> 1
        FoodDataSourceType.MANUFACTURER -> 2
        FoodDataSourceType.USDA_FOUNDATION -> 3
        FoodDataSourceType.USDA_FNDDS -> 4
        FoodDataSourceType.OPEN_FOOD_FACTS -> 5
        FoodDataSourceType.USDA_SR_LEGACY -> 6
        FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE -> 7
        FoodDataSourceType.USER_CREATED -> 3
        FoodDataSourceType.COMPOSITE_ESTIMATE -> 9
        FoodDataSourceType.UNKNOWN -> 8
    }

    fun dedupKey(food: NativeFood): String {
        food.barcode?.filter(Char::isDigit)?.takeIf(String::isNotBlank)?.let { return "barcode:$it" }
        val f = enrich(food)
        val words = normalizeName(f.name)
            .split(' ')
            .filter(String::isNotBlank)
            .filterNot { it in nameNoise }
            .map(::singularize)
        val normalizedName = words.joinToString(" ").ifBlank { normalizeName(f.name) }
        val brandKey = if (f.identityKind == FoodIdentityKind.BRANDED_PRODUCT) normalizeName(f.brand) else ""
        return "name:" + normalizedName + "|prep:" + f.preparationState.name + "|brand:" + brandKey
    }

    fun userFacingSourceLabel(food: NativeFood): String {
        val f = enrich(food)
        return when (f.sourceType) {
            FoodDataSourceType.USDA_FOUNDATION -> "Generic · USDA Foundation"
            FoodDataSourceType.USDA_FNDDS -> "Generic · USDA FNDDS"
            FoodDataSourceType.USDA_SR_LEGACY -> "Generic · USDA reference"
            FoodDataSourceType.OPEN_FOOD_FACTS -> "Branded · barcode product"
            FoodDataSourceType.PACKAGE_LABEL -> "Branded · package label"
            FoodDataSourceType.MANUFACTURER -> "Branded · manufacturer"
            FoodDataSourceType.USER_CORRECTED -> "Corrected locally"
            FoodDataSourceType.COMPOSITE_ESTIMATE -> "Composite estimate"
            FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE -> "Generic · reference"
            FoodDataSourceType.USER_CREATED -> "User-created food"
            FoodDataSourceType.UNKNOWN -> f.source
        }
    }

    fun userFacingDataQualityMessage(food: NativeFood): String? {
        val f = enrich(food)
        return when {
            f.verificationState == FoodVerificationState.CONFLICTED ||
                f.confidence == FoodDataConfidence.CONFLICTED ->
                "Nutrition data may be inaccurate · check label"
            f.verificationState == FoodVerificationState.LABEL_VERIFIED ->
                "Verified from package label"
            f.verificationState == FoodVerificationState.USER_CORRECTED ->
                "Using your corrected nutrition"
            else -> null
        }
    }

    fun snapshotMetadata(
        food: NativeFood,
        amount: Double,
        inputUnit: FoodUnit,
        conversion: FoodConversion
    ): Map<String, String> {
        val f = enrich(food)
        return buildMap {
            put("nutritionSnapshotVersion", "4")
            put("canonicalFoodSchemaVersion", f.canonicalSchemaVersion.toString())
            put("foodIdentityKind", f.identityKind.name)
            put("sourceType", f.sourceType.name)
            put("sourceRecordIdCanonical", f.sourceRecordId)
            put("sourceRevision", f.sourceRevision)
            f.lastRetrievedEpochMs?.let { put("sourceRetrievedEpochMs", it.toString()) }
            put("verificationState", f.verificationState.name)
            put("foodDataConfidence", f.confidence.name)
            put("preparationState", f.preparationState.name)
            put("energyEvidence", f.energyEvidence.name)
            put("carbohydrateDefinition", f.carbohydrateDefinition.name)
            put("originalAmount", amount.toString())
            put("originalAmountUnit", inputUnit.symbol)
            put("conversionBasisAmount", conversion.basisAmount.toString())
            put("conversionBasisUnit", conversion.basisUnit.symbol)
            f.densityGPerMl?.let {
                put("densityGPerMl", it.toString())
                put("densityApproximate", f.densityApproximate.toString())
                put("densitySource", f.densitySource.name)
            }
            if (f.originalName.isNotBlank()) put("originalProductName", f.originalName)
            if (f.displayLanguage.isNotBlank()) put("sourceLanguage", f.displayLanguage)
            if (f.ingredientsText.isNotBlank()) put("ingredientsText", f.ingredientsText)
            if (f.allergens.isNotEmpty()) put("allergens", f.allergens.joinToString("|"))
            if (f.additives.isNotEmpty()) put("additives", f.additives.joinToString("|"))
            f.novaGroup?.let { put("novaGroup", it.toString()) }
            if (f.imageReferences.isNotEmpty()) {
                put("imageReferences", f.imageReferences.entries.joinToString("|") { it.key + "=" + it.value })
            }
            if (f.correctedFields.isNotEmpty()) {
                put("correctedFields", f.correctedFields.sorted().joinToString("|"))
            }
            if (f.sourceWarnings.isNotEmpty()) put("sourceWarnings", f.sourceWarnings.joinToString("|"))
            put("nutritionApproximate", f.nutritionApproximate.toString())
        }
    }

    fun nutrientMetadata(food: NativeFood, nutrientId: String): Map<String, String> {
        val f = enrich(food)
        val kind = f.nutrientEvidence[nutrientId]
            ?: evidenceKindForSource(f.sourceType, f.nutritionApproximate)
        return mapOf(
            "nutrientEvidenceKind" to kind.name,
            "nutrientEvidenceSource" to f.source,
            "nutrientSourceRecordId" to f.sourceRecordId
        )
    }

    private fun identityKindFor(food: NativeFood, sourceType: FoodDataSourceType): FoodIdentityKind = when {
        sourceType == FoodDataSourceType.COMPOSITE_ESTIMATE -> FoodIdentityKind.COMPOSITE_ESTIMATE
        sourceType == FoodDataSourceType.USER_CREATED -> FoodIdentityKind.USER_CREATED
        !food.barcode.isNullOrBlank() ||
            sourceType == FoodDataSourceType.PACKAGE_LABEL ||
            sourceType == FoodDataSourceType.MANUFACTURER -> FoodIdentityKind.BRANDED_PRODUCT
        else -> FoodIdentityKind.GENERIC_FOOD
    }

    private fun inferVerification(
        food: NativeFood,
        sourceType: FoodDataSourceType,
        warnings: List<String>
    ): FoodVerificationState {
        if (warnings.isNotEmpty()) return FoodVerificationState.CONFLICTED
        return when (sourceType) {
            FoodDataSourceType.USER_CORRECTED -> FoodVerificationState.USER_CORRECTED
            FoodDataSourceType.PACKAGE_LABEL -> FoodVerificationState.LABEL_VERIFIED
            FoodDataSourceType.MANUFACTURER -> FoodVerificationState.BRAND_VERIFIED
            FoodDataSourceType.USDA_FOUNDATION -> FoodVerificationState.INTEGRITY_VALIDATED
            FoodDataSourceType.USDA_FNDDS,
            FoodDataSourceType.USDA_SR_LEGACY,
            FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE -> FoodVerificationState.SOURCE_VALIDATED
            FoodDataSourceType.OPEN_FOOD_FACTS -> {
                val macroKnown = listOf(food.proteinKnown, food.carbsKnown, food.fatKnown).count { it }
                if (!food.barcode.isNullOrBlank() && food.kcalKnown && macroKnown >= 2) {
                    FoodVerificationState.INTEGRITY_VALIDATED
                } else FoodVerificationState.SOURCE_VALIDATED
            }
            FoodDataSourceType.USER_CREATED -> FoodVerificationState.SOURCE_VALIDATED
            FoodDataSourceType.COMPOSITE_ESTIMATE,
            FoodDataSourceType.UNKNOWN -> FoodVerificationState.UNVERIFIED
        }
    }

    private fun inferConfidence(
        food: NativeFood,
        sourceType: FoodDataSourceType,
        verification: FoodVerificationState,
        warnings: List<String>
    ): FoodDataConfidence {
        if (warnings.isNotEmpty() || verification == FoodVerificationState.CONFLICTED) {
            return FoodDataConfidence.CONFLICTED
        }
        if (food.nutritionApproximate || sourceType == FoodDataSourceType.COMPOSITE_ESTIMATE) {
            return FoodDataConfidence.LOW
        }
        return when (sourceType) {
            FoodDataSourceType.USER_CORRECTED,
            FoodDataSourceType.PACKAGE_LABEL,
            FoodDataSourceType.MANUFACTURER,
            FoodDataSourceType.USDA_FOUNDATION -> FoodDataConfidence.HIGH
            FoodDataSourceType.USDA_FNDDS -> FoodDataConfidence.MEDIUM_HIGH
            FoodDataSourceType.OPEN_FOOD_FACTS ->
                if (!food.barcode.isNullOrBlank() && food.kcalKnown) FoodDataConfidence.MEDIUM_HIGH
                else FoodDataConfidence.MEDIUM
            FoodDataSourceType.USDA_SR_LEGACY,
            FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE,
            FoodDataSourceType.USER_CREATED -> FoodDataConfidence.MEDIUM
            FoodDataSourceType.COMPOSITE_ESTIMATE -> FoodDataConfidence.LOW
            FoodDataSourceType.UNKNOWN -> FoodDataConfidence.MEDIUM
        }
    }

    private fun sourceRecordIdFor(food: NativeFood, sourceType: FoodDataSourceType): String = when {
        !food.barcode.isNullOrBlank() && sourceType == FoodDataSourceType.OPEN_FOOD_FACTS ->
            food.barcode.filter(Char::isDigit)
        else -> food.id
    }

    private fun sourceRevisionFor(food: NativeFood, sourceType: FoodDataSourceType): String = when (sourceType) {
        FoodDataSourceType.USDA_FOUNDATION,
        FoodDataSourceType.USDA_FNDDS,
        FoodDataSourceType.USDA_SR_LEGACY -> food.source
        FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE -> "bundled-reference"
        else -> ""
    }

    fun inferPreparationState(raw: String): FoodPreparationState {
        val s = raw.lowercase(Locale.ROOT)
        return when {
            Regex("""\breconstitut(ed|ed from|ion)?\b""").containsMatchIn(s) -> FoodPreparationState.RECONSTITUTED
            Regex("""\bdrained\b""").containsMatchIn(s) -> FoodPreparationState.DRAINED
            Regex("""\b(canned|tinned)\b""").containsMatchIn(s) -> FoodPreparationState.CANNED
            Regex("""\bfrozen\b""").containsMatchIn(s) -> FoodPreparationState.FROZEN
            Regex("""\b(dried|dry)\b""").containsMatchIn(s) -> FoodPreparationState.DRIED
            Regex("""\bsteamed\b""").containsMatchIn(s) -> FoodPreparationState.STEAMED
            Regex("""\bpoached\b""").containsMatchIn(s) -> FoodPreparationState.POACHED
            Regex("""\bboiled\b""").containsMatchIn(s) -> FoodPreparationState.BOILED
            Regex("""\bgrilled\b""").containsMatchIn(s) -> FoodPreparationState.GRILLED
            Regex("""\broasted\b""").containsMatchIn(s) -> FoodPreparationState.ROASTED
            Regex("""\bbaked\b""").containsMatchIn(s) -> FoodPreparationState.BAKED
            Regex("""\bfried\b""").containsMatchIn(s) -> FoodPreparationState.FRIED
            Regex("""\bcooked\b""").containsMatchIn(s) -> FoodPreparationState.COOKED
            Regex("""\braw\b""").containsMatchIn(s) -> FoodPreparationState.RAW
            else -> FoodPreparationState.UNSPECIFIED
        }
    }

    private fun normalizeName(raw: String): String = raw.lowercase(Locale.ROOT)
        .replace('ą', 'a')
        .replace('ć', 'c')
        .replace('ę', 'e')
        .replace('ł', 'l')
        .replace('ń', 'n')
        .replace('ó', 'o')
        .replace('ś', 's')
        .replace('ź', 'z')
        .replace('ż', 'z')
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

    private fun singularize(word: String): String = when {
        word == "bananas" -> "banana"
        word == "tomatoes" -> "tomato"
        word == "potatoes" -> "potato"
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("s") && !word.endsWith("ss") && word.length > 4 -> word.dropLast(1)
        else -> word
    }

    private val nameNoise = setOf(
        "fresh", "raw", "cooked", "boiled", "grilled", "roasted", "baked", "fried",
        "steamed", "generic"
    )
}
