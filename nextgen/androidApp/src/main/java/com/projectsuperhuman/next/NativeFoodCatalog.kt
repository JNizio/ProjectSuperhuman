package com.projectsuperhuman.next

import android.content.Context
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.LinkedHashMap

internal enum class CarbohydrateDefinition {
    /** EU/Open Food Facts carbohydrate: available carbohydrate, excluding fibre. */
    AVAILABLE_EXCLUDING_FIBRE,
    /** USDA carbohydrate by difference: includes dietary fibre. */
    TOTAL_INCLUDING_FIBRE,
    /** Source does not establish a compatible carbohydrate definition. */
    UNKNOWN
}

internal data class NativeNutrient(
    val id: String,
    val label: String,
    val valuePer100: Double,
    val unit: String,
    val evidenceKind: NutrientEvidenceKind = NutrientEvidenceKind.UNSPECIFIED,
    val source: String = "",
    val sourceRecordId: String = "",
    val derivedFrom: String = ""
)

internal data class NativeFood(
    val id: String,
    val name: String,
    val country: String,
    val kcal: Double,
    val kcalKnown: Boolean = true,
    val protein: Double,
    val carbs: Double,
    val carbohydrateDefinition: CarbohydrateDefinition = CarbohydrateDefinition.UNKNOWN,
    val fat: Double,
    val fibre: Double,
    val sugar: Double,
    val unit: String,
    val source: String,
    val barcode: String? = null,
    val searchText: String = "",
    val brand: String = "",
    val quantity: String = "",
    val servingSize: String = "",
    val micronutrients: Map<String, NativeNutrient> = emptyMap(),
    val proteinKnown: Boolean = true,
    val carbsKnown: Boolean = true,
    val fatKnown: Boolean = true,
    val fibreKnown: Boolean = true,
    val sugarKnown: Boolean = true,
    val saturatedFat: Double = 0.0,
    val saturatedFatKnown: Boolean = false,
    val salt: Double = 0.0,
    val saltKnown: Boolean = false,
    val sodiumMg: Double = 0.0,
    val sodiumKnown: Boolean = false,
    val nutritionIntegrityWarning: String? = null,
    val nutritionApproximate: Boolean = false,
    val originalName: String = "",
    val displayLanguage: String = "",
    val hasVerifiedEnglishName: Boolean = false,
    val basisAmount: Double = 100.0,
    val basisUnit: FoodUnit = FoodUnit.G,
    val densityGPerMl: Double? = null,
    val densityApproximate: Boolean = false,
    val densitySource: DensityEvidenceSource = DensityEvidenceSource.UNKNOWN,
    val productQuantity: Double? = null,
    val productQuantityUnit: FoodUnit? = null,
    val servingQuantity: Double? = null,
    val servingQuantityUnit: FoodUnit? = null,
    val servingLabel: String = "",
    val identityKind: FoodIdentityKind = FoodIdentityKind.UNKNOWN,
    val sourceType: FoodDataSourceType = FoodDataSourceType.UNKNOWN,
    val sourceRecordId: String = "",
    val sourceRevision: String = "",
    val lastRetrievedEpochMs: Long? = null,
    val verificationState: FoodVerificationState = FoodVerificationState.UNVERIFIED,
    val confidence: FoodDataConfidence = FoodDataConfidence.UNASSESSED,
    val preparationState: FoodPreparationState = FoodPreparationState.UNSPECIFIED,
    val energyEvidence: EnergyEvidenceKind = EnergyEvidenceKind.UNKNOWN,
    val nutrientEvidence: Map<String, NutrientEvidenceKind> = emptyMap(),
    val ingredientsText: String = "",
    val allergens: List<String> = emptyList(),
    val additives: List<String> = emptyList(),
    val novaGroup: Int? = null,
    val sourceWarnings: List<String> = emptyList(),
    val canonicalSchemaVersion: Int = NUTRITION_CANONICAL_SCHEMA_VERSION
)

internal data class NativeFoodSearchResult(
    val foods: List<NativeFood>,
    val remoteAvailable: Boolean,
    val remoteCount: Int
)

internal object NativeFoodCatalog {
    private const val USER_AGENT = "ProjectSuperhuman/11.4 (Android; https://github.com/JNizio/ProjectSuperhuman)"
    private const val OFF_FIELDS = "code,lang,languages_tags,product_name,product_name_en,generic_name,generic_name_en,brands,countries_tags,categories,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutrition_data_per,data_quality_errors_tags,data_quality_warnings_tags,nutriments,ingredients_text,allergens_tags,additives_tags,nova_group,last_modified_t,last_modified_datetime"
    private const val FAST_RESULT_COUNT = 8
    private const val MAX_RESULT_COUNT = 10

    @Volatile private var cached: List<NativeFood>? = null

    private val remoteSearchCache = object : LinkedHashMap<String, List<NativeFood>>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<NativeFood>>?): Boolean = size > 12
    }
    private val remoteCacheLock = Any()

    private data class NutrientSpec(
        val offId: String,
        val id: String,
        val label: String,
        val unit: String,
        val multiplierFromGrams: Double
    )

    /**
     * Open Food Facts normalises weight-based `<nutrient>_100g` values to grams.
     * Convert common vitamins/minerals into human-friendly units before they enter the diary.
     */
    private val micronutrientSpecs = listOf(
        NutrientSpec("calcium", "calcium", "Calcium", "mg", 1_000.0),
        NutrientSpec("chloride", "chloride", "Chloride", "mg", 1_000.0),
        NutrientSpec("copper", "copper", "Copper", "mg", 1_000.0),
        NutrientSpec("iron", "iron", "Iron", "mg", 1_000.0),
        NutrientSpec("iodine", "iodine", "Iodine", "µg", 1_000_000.0),
        NutrientSpec("magnesium", "magnesium", "Magnesium", "mg", 1_000.0),
        NutrientSpec("manganese", "manganese", "Manganese", "mg", 1_000.0),
        NutrientSpec("phosphorus", "phosphorus", "Phosphorus", "mg", 1_000.0),
        NutrientSpec("potassium", "potassium", "Potassium", "mg", 1_000.0),
        NutrientSpec("selenium", "selenium", "Selenium", "µg", 1_000_000.0),
        NutrientSpec("sodium", "sodium", "Sodium", "mg", 1_000.0),
        NutrientSpec("zinc", "zinc", "Zinc", "mg", 1_000.0),
        NutrientSpec("vitamin-a", "vitamin_a", "Vitamin A", "µg", 1_000_000.0),
        NutrientSpec("vitamin-b1", "vitamin_b1", "Vitamin B1", "mg", 1_000.0),
        NutrientSpec("vitamin-b2", "vitamin_b2", "Vitamin B2", "mg", 1_000.0),
        NutrientSpec("vitamin-pp", "niacin", "Niacin (B3)", "mg", 1_000.0),
        NutrientSpec("pantothenic-acid", "pantothenic_acid", "Pantothenic acid (B5)", "mg", 1_000.0),
        NutrientSpec("vitamin-b6", "vitamin_b6", "Vitamin B6", "mg", 1_000.0),
        NutrientSpec("vitamin-b9", "folate", "Folate (B9)", "µg", 1_000_000.0),
        NutrientSpec("folates", "folate", "Folate (B9)", "µg", 1_000_000.0),
        NutrientSpec("vitamin-b12", "vitamin_b12", "Vitamin B12", "µg", 1_000_000.0),
        NutrientSpec("biotin", "biotin", "Biotin (B7)", "µg", 1_000_000.0),
        NutrientSpec("vitamin-c", "vitamin_c", "Vitamin C", "mg", 1_000.0),
        NutrientSpec("vitamin-d", "vitamin_d", "Vitamin D", "µg", 1_000_000.0),
        NutrientSpec("vitamin-e", "vitamin_e", "Vitamin E", "mg", 1_000.0),
        NutrientSpec("vitamin-k", "vitamin_k", "Vitamin K", "µg", 1_000_000.0),
        NutrientSpec("choline", "choline", "Choline", "mg", 1_000.0)
    )

    /**
     * The large USDA importer writes in the background. On some Android SQLite builds, opening
     * another helper while that transaction is active can throw SQLITE_BUSY while applying
     * PRAGMA journal_mode. Food search must never crash because the optional expanded catalogue is
     * busy: bundled foods and Open Food Facts remain usable while the importer finishes.
     */
    private fun startLargeLocalSafely(context: Context) {
        try {
            LargeLocalFoodDatabase.ensureStarted(context)
        } catch (_: SQLiteException) {
            // The background importer already owns the DB. Search can continue with other sources.
        }
    }

    suspend fun all(context: Context): List<NativeFood> = withContext(Dispatchers.IO) {
        FoodNutritionOverrideStore.attach(context)
        startLargeLocalSafely(context)
        val base = cached ?: loadLocal(context).also { cached = it }
        FoodNutritionOverrideStore.applyAll(context, base.map(FoodEvidenceEngine::enrich))
    }

    /** Number of local reference foods available without depending on an Open Food Facts search. */
    suspend fun localReferenceCount(context: Context): Int {
        val bundled = all(context).size
        val expanded = try {
            LargeLocalFoodDatabase.count(context)
        } catch (_: SQLiteException) {
            0
        }
        return bundled + expanded
    }

    /**
     * Fast progressive search.
     *
     * A broad query should not build and render dozens of cards or wait on the network when the
     * local database already has strong matches. We rank a small local candidate set first and only
     * call Open Food Facts when local coverage is thin or the user typed a more specific multi-word
     * product query. The UI therefore gets the most useful 8-10 results quickly; typing a more
     * specific query is the cheap way to drill further into the catalogue.
     */
    suspend fun search(context: Context, query: String, limit: Int = MAX_RESULT_COUNT): NativeFoodSearchResult {
        FoodNutritionOverrideStore.attach(context)
        startLargeLocalSafely(context)
        val q = query.trim()
        if (q.length < 2) return NativeFoodSearchResult(emptyList(), remoteAvailable = true, remoteCount = 0)

        val requested = limit.coerceIn(1, MAX_RESULT_COUNT)
        val bundledLocal = searchLocal(context, q, limit = 10)
        val expandedLocal = try {
            LargeLocalFoodDatabase.search(context, q, limit = 16)
        } catch (_: SQLiteException) {
            emptyList()
        }

        val localCandidates = (bundledLocal + expandedLocal)
            .map(FoodEvidenceEngine::enrich)
            .sortedWith(foodComparator(q))
            .distinctBy(FoodEvidenceEngine::dedupKey)

        // Broad/common searches stay completely local when we already have enough good candidates.
        // Multi-word queries are more likely to be a specific branded product, so OFF remains useful.
        val specificProductQuery = q.length >= 6 && q.any(Char::isWhitespace)
        val shouldQueryRemote = localCandidates.size < FAST_RESULT_COUNT || specificProductQuery
        val remoteResult = if (shouldQueryRemote) {
            searchOpenFoodFacts(context, q, limit = 12)
        } else {
            emptyList<NativeFood>() to true
        }

        val merged = FoodNutritionOverrideStore.applyAll(
            context,
            (localCandidates + remoteResult.first)
                .map(FoodEvidenceEngine::enrich)
                .sortedWith(foodComparator(q))
                .distinctBy(FoodEvidenceEngine::dedupKey)
        ).take(requested)

        return NativeFoodSearchResult(
            foods = merged,
            remoteAvailable = remoteResult.second,
            remoteCount = remoteResult.first.size
        )
    }

    suspend fun lookupBarcode(context: Context, code: String): NativeFood? = withContext(Dispatchers.IO) {
        FoodNutritionOverrideStore.attach(context)
        val digits = code.filter(Char::isDigit)
        if (!NutritionMath.isValidBarcode(digits)) return@withContext null

        val url = "https://world.openfoodfacts.org/api/v2/product/" + digits + ".json?fields=" + OFF_FIELDS
        val conn = openConnection(url)
        try {
            if (conn.responseCode in 200..299) {
                val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
                if (root.optInt("status", 0) == 1) {
                    val product = root.optJSONObject("product")
                    if (product != null) {
                        val retrieved = System.currentTimeMillis()
                        FoodProductCache.put(context, digits, product.toString(), retrieved)
                        return@withContext parseOpenFoodFactsProduct(product, digits, retrieved)
                            ?.let(FoodEvidenceEngine::enrich)
                            ?.let(FoodNutritionOverrideStore::applyIfAttached)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to cached source evidence.
        } finally {
            conn.disconnect()
        }

        val cachedProduct = FoodProductCache.get(context, digits) ?: return@withContext null
        runCatching { JSONObject(cachedProduct.productJson) }
            .getOrNull()
            ?.let { parseOpenFoodFactsProduct(it, digits, cachedProduct.retrievedEpochMs) }
            ?.let(FoodEvidenceEngine::enrich)
            ?.let(FoodNutritionOverrideStore::applyIfAttached)
    }

    /**
     * Search only the tiny bundled list here. Do not call [all]: that would apply overrides to the
     * whole bundled catalogue and touch extra SQLite state before we even know which rows matched.
     * Overrides are applied once, to the small merged result set, at the end of [search].
     */
    private fun searchLocal(context: Context, query: String, limit: Int): List<NativeFood> {
        val q = query.trim().lowercase()
        val base = cached ?: loadLocal(context).also { cached = it }
        return base.asSequence()
            .map { food -> foodSearchRank(food, q) to food }
            .filter { it.first < 99 }
            .sortedWith(compareBy<Pair<Int, NativeFood>> { it.first }.thenBy { sourcePriority(it.second) }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
            .toList()
    }

    private suspend fun searchOpenFoodFacts(context: Context, query: String, limit: Int): Pair<List<NativeFood>, Boolean> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        synchronized(remoteCacheLock) { remoteSearchCache[key] }?.let { return@withContext it.take(limit) to true }

        // Open Food Facts currently keeps full-text search on the v1 CGI endpoint.
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + encoded +
            "&search_simple=1&action=process&json=1&page_size=" + limit + "&fields=" + OFF_FIELDS
        val conn = openConnection(url)
        try {
            if (conn.responseCode !in 200..299) return@withContext emptyList<NativeFood>() to false
            val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
            val products = root.optJSONArray("products") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until products.length()) {
                    val p = products.optJSONObject(i) ?: continue
                    val code = p.optString("code").filter(Char::isDigit)
                    val retrieved = System.currentTimeMillis()
                    if (code.isNotBlank()) FoodProductCache.put(context, code, p.toString(), retrieved)
                    val food = parseOpenFoodFactsProduct(p, code, retrieved)
                        ?.let(FoodEvidenceEngine::enrich) ?: continue
                    if (food.name.isNotBlank()) add(food)
                }
            }
            synchronized(remoteCacheLock) { remoteSearchCache[key] = parsed }
            parsed.take(limit) to true
        } catch (_: Exception) {
            emptyList<NativeFood>() to false
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOpenFoodFactsProduct(
        p: JSONObject,
        fallbackCode: String,
        retrievedEpochMs: Long = System.currentTimeMillis()
    ): NativeFood? {
        val code = p.optString("code").ifBlank { fallbackCode }.filter(Char::isDigit)
        val nutriments = p.optJSONObject("nutriments") ?: JSONObject()
        val localizedName = resolveOpenFoodFactsDisplayName(p, code)
        val name = localizedName.displayName
        if (name.isBlank()) return null

        val kcalDirectKnown = nutriments.hasNonNegativeNumber("energy-kcal_100g")
        val kjKnown = nutriments.hasNonNegativeNumber("energy-kj_100g")
        val kcal = when {
            kcalDirectKnown -> nutriments.optDoubleSafe("energy-kcal_100g")
            kjKnown -> nutriments.optDoubleSafe("energy-kj_100g") / 4.184
            else -> 0.0
        }.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val kcalKnown = kcalDirectKnown || kjKnown

        val productQuantityUnit = FoodUnit.fromSymbol(p.optString("product_quantity_unit"))
        val servingQuantityUnit = FoodUnit.fromSymbol(p.optString("serving_quantity_unit"))
        val basisUnit = when {
            productQuantityUnit?.dimension == FoodMeasureDimension.VOLUME -> FoodUnit.ML
            productQuantityUnit?.dimension == FoodMeasureDimension.MASS -> FoodUnit.G
            servingQuantityUnit?.dimension == FoodMeasureDimension.VOLUME -> FoodUnit.ML
            else -> FoodUnit.G
        }
        val basis = if (basisUnit == FoodUnit.ML) "100 ml" else "100 g"
        val brand = p.optString("brands").trim()
        val country = p.optStringList("countries_tags").take(80)
        val categories = p.optString("categories").take(180)

        val proteinRaw = nutriments.optDoubleSafe("proteins_100g")
        val carbsRaw = nutriments.optDoubleSafe("carbohydrates_100g")
        val fatRaw = nutriments.optDoubleSafe("fat_100g")
        val proteinKnown = nutriments.hasNonNegativeNumber("proteins_100g")
        val carbsKnown = nutriments.hasNonNegativeNumber("carbohydrates_100g")
        val fatKnown = nutriments.hasNonNegativeNumber("fat_100g")
        val saturatedFatRaw = nutriments.optDoubleSafe("saturated-fat_100g")
        val saturatedFatKnown = nutriments.hasNonNegativeNumber("saturated-fat_100g")
        val fibreRaw = nutriments.optDoubleSafe("fiber_100g")
        val fibreKnown = nutriments.hasNonNegativeNumber("fiber_100g")
        val sugarRaw = nutriments.optDoubleSafe("sugars_100g")
        val sugarKnown = nutriments.hasNonNegativeNumber("sugars_100g")
        val saltRaw = nutriments.optDoubleSafe("salt_100g")
        val saltKnown = nutriments.hasNonNegativeNumber("salt_100g")
        val sodiumRawMg = nutriments.optDoubleSafe("sodium_100g") * 1_000.0
        val sodiumKnown = nutriments.hasNonNegativeNumber("sodium_100g")

        val macroIntegrity = NutritionIntegrity.sanitizeMacros(
            kcal = kcal,
            protein = proteinRaw,
            carbs = carbsRaw,
            fat = fatRaw,
            proteinKnown = proteinKnown,
            carbsKnown = carbsKnown,
            fatKnown = fatKnown,
            kcalKnown = kcalKnown
        )

        val integrity = NutritionIntegrity.validateFoodValues(
            basisAmount = 100.0,
            basisUnit = basisUnit,
            kcal = kcal,
            kcalKnown = kcalKnown,
            protein = macroIntegrity.protein,
            proteinKnown = macroIntegrity.proteinKnown,
            carbs = macroIntegrity.carbs,
            carbsKnown = macroIntegrity.carbsKnown,
            fat = macroIntegrity.fat,
            fatKnown = macroIntegrity.fatKnown,
            saturatedFat = saturatedFatRaw,
            saturatedFatKnown = saturatedFatKnown,
            fibre = fibreRaw,
            fibreKnown = fibreKnown,
            sugar = sugarRaw,
            sugarKnown = sugarKnown,
            saltG = saltRaw,
            saltKnown = saltKnown,
            sodiumMg = sodiumRawMg,
            sodiumKnown = sodiumKnown,
            servingQuantity = p.optNullableDouble("serving_quantity"),
            productQuantity = p.optNullableDouble("product_quantity")
        )

        val finalSodiumMg = when {
            sodiumKnown -> sodiumRawMg
            integrity.derivedSodiumMg != null -> integrity.derivedSodiumMg
            else -> 0.0
        }
        val finalSodiumKnown = sodiumKnown || integrity.derivedSodiumMg != null
        val finalSalt = when {
            saltKnown -> saltRaw
            integrity.derivedSaltG != null -> integrity.derivedSaltG
            else -> 0.0
        }
        val finalSaltKnown = saltKnown || integrity.derivedSaltG != null

        val offWarnings = listOf(
            p.optStringList("data_quality_errors_tags"),
            p.optStringList("data_quality_warnings_tags")
        )
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { warning -> warning.isNotBlank() && isNutritionQualityWarning(warning) }
            .distinct()

        val sourceWarnings = (offWarnings + integrity.warnings + listOfNotNull(macroIntegrity.warning))
            .filter(String::isNotBlank)
            .distinct()

        val sourceRecordId = code.ifBlank { "off-name-" + name.lowercase().hashCode() }
        val nutrientEvidence = buildMap<String, NutrientEvidenceKind> {
            if (kcalKnown) put("energy_kcal", if (kcalDirectKnown) NutrientEvidenceKind.SOURCE_REPORTED else NutrientEvidenceKind.DERIVED)
            if (macroIntegrity.proteinKnown) put("protein", NutrientEvidenceKind.SOURCE_REPORTED)
            if (macroIntegrity.carbsKnown) put("carbohydrate", NutrientEvidenceKind.SOURCE_REPORTED)
            if (macroIntegrity.fatKnown) put("fat", NutrientEvidenceKind.SOURCE_REPORTED)
            if (saturatedFatKnown) put("saturated_fat", NutrientEvidenceKind.SOURCE_REPORTED)
            if (fibreKnown) put("fibre", NutrientEvidenceKind.SOURCE_REPORTED)
            if (sugarKnown) put("sugars", NutrientEvidenceKind.SOURCE_REPORTED)
            if (finalSaltKnown) put("salt", if (saltKnown) NutrientEvidenceKind.SOURCE_REPORTED else NutrientEvidenceKind.DERIVED)
            if (finalSodiumKnown) put("sodium", if (sodiumKnown) NutrientEvidenceKind.SOURCE_REPORTED else NutrientEvidenceKind.DERIVED)
        }

        return NativeFood(
            id = if (code.isNotBlank()) "off:$code" else "off:" + name.lowercase().hashCode(),
            name = name,
            country = country,
            kcal = kcal,
            kcalKnown = kcalKnown,
            protein = macroIntegrity.protein,
            carbs = macroIntegrity.carbs,
            carbohydrateDefinition = CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE,
            fat = macroIntegrity.fat,
            fibre = fibreRaw,
            sugar = sugarRaw,
            unit = basis,
            source = "Open Food Facts",
            barcode = code.ifBlank { null },
            searchText = brand + " " + categories,
            brand = brand,
            quantity = p.optString("quantity"),
            servingSize = p.optString("serving_size"),
            micronutrients = extractMicronutrients(nutriments, sourceRecordId),
            proteinKnown = macroIntegrity.proteinKnown,
            carbsKnown = macroIntegrity.carbsKnown,
            fatKnown = macroIntegrity.fatKnown,
            fibreKnown = fibreKnown,
            sugarKnown = sugarKnown,
            saturatedFat = saturatedFatRaw,
            saturatedFatKnown = saturatedFatKnown,
            salt = finalSalt,
            saltKnown = finalSaltKnown,
            sodiumMg = finalSodiumMg,
            sodiumKnown = finalSodiumKnown,
            nutritionIntegrityWarning = sourceWarnings.joinToString(" · ").ifBlank { null },
            originalName = localizedName.originalName,
            displayLanguage = localizedName.displayLanguage,
            hasVerifiedEnglishName = localizedName.hasVerifiedEnglishName,
            basisAmount = 100.0,
            basisUnit = basisUnit,
            productQuantity = p.optNullableDouble("product_quantity"),
            productQuantityUnit = productQuantityUnit,
            servingQuantity = p.optNullableDouble("serving_quantity"),
            servingQuantityUnit = servingQuantityUnit,
            servingLabel = p.optString("serving_size"),
            identityKind = FoodIdentityKind.BRANDED_PRODUCT,
            sourceType = FoodDataSourceType.OPEN_FOOD_FACTS,
            sourceRecordId = sourceRecordId,
            sourceRevision = p.optString("last_modified_datetime").ifBlank { p.optString("last_modified_t") },
            lastRetrievedEpochMs = retrievedEpochMs,
            preparationState = FoodEvidenceEngine.inferPreparationState(name + " " + categories),
            energyEvidence = when {
                kcalDirectKnown -> EnergyEvidenceKind.REPORTED_KCAL
                kjKnown -> EnergyEvidenceKind.CONVERTED_KJ
                else -> EnergyEvidenceKind.UNKNOWN
            },
            nutrientEvidence = nutrientEvidence,
            ingredientsText = p.optString("ingredients_text"),
            allergens = p.optTagList("allergens_tags"),
            additives = p.optTagList("additives_tags"),
            novaGroup = p.optInt("nova_group", 0).takeIf { it in 1..4 },
            sourceWarnings = sourceWarnings
        )
    }

    private data class LocalizedProductName(
        val displayName: String,
        val originalName: String,
        val displayLanguage: String,
        val hasVerifiedEnglishName: Boolean
    )

    /**
     * Prefer Open Food Facts' own language-specific English fields instead of machine translating.
     * Product/brand names with no verified English label remain in the packaging language so we
     * never invent awkward or misleading translations.
     */
    private fun resolveOpenFoodFactsDisplayName(p: JSONObject, code: String): LocalizedProductName {
        val brand = p.optString("brands").trim()
        val mainLanguage = p.optString("lang").trim().lowercase()
        val original = p.optString("product_name").trim()
            .ifBlank { p.optString("generic_name").trim() }
            .ifBlank { brand }
            .ifBlank { if (code.isNotBlank()) "Product $code" else "" }

        val english = p.optString("product_name_en").trim()
            .ifBlank { p.optString("generic_name_en").trim() }

        if (english.isNotBlank()) {
            return LocalizedProductName(
                displayName = english,
                originalName = original,
                displayLanguage = "en",
                hasVerifiedEnglishName = true
            )
        }

        // For any non-English source language, keep the authentic packaging name when OFF does
        // not provide an explicit English field. This applies equally to Polish, French, German,
        // Spanish, Italian, Japanese, Korean and every other supported source language.
        // Correct product identity beats a guessed machine translation.
        return LocalizedProductName(
            displayName = original,
            originalName = original,
            displayLanguage = mainLanguage.ifBlank { "source" },
            hasVerifiedEnglishName = false
        )
    }

    private fun extractMicronutrients(n: JSONObject, sourceRecordId: String = ""): Map<String, NativeNutrient> {
        val out = linkedMapOf<String, NativeNutrient>()
        micronutrientSpecs.forEach { spec ->
            // Several OFF taxonomy ids can map to one canonical nutrient (e.g. folate).
            if (out.containsKey(spec.id)) return@forEach
            val key = "${spec.offId}_100g"
            if (!n.hasFiniteNumber(key)) return@forEach
            val grams = n.optDoubleSafe(key)
            if (grams < 0.0 || !grams.isFinite()) return@forEach
            val converted = grams * spec.multiplierFromGrams
            if (converted < 0.0 || !converted.isFinite()) return@forEach
            out[spec.id] = NativeNutrient(
                id = spec.id,
                label = spec.label,
                valuePer100 = converted,
                unit = spec.unit,
                evidenceKind = NutrientEvidenceKind.SOURCE_REPORTED,
                source = "Open Food Facts",
                sourceRecordId = sourceRecordId
            )
        }
        if (!out.containsKey("sodium")) {
            val saltKey = "salt_100g"
            if (n.hasFiniteNumber(saltKey)) {
                val saltGrams = n.optDoubleSafe(saltKey)
                if (saltGrams.isFinite() && saltGrams >= 0.0) {
                    out["sodium"] = NativeNutrient(
                        id = "sodium",
                        label = "Sodium",
                        valuePer100 = NutritionIntegrity.saltGToSodiumMg(saltGrams),
                        unit = "mg",
                        evidenceKind = NutrientEvidenceKind.DERIVED,
                        source = "Open Food Facts",
                        sourceRecordId = sourceRecordId,
                        derivedFrom = "salt"
                    )
                }
            }
        }
        return out
    }

    private fun isNutritionQualityWarning(raw: String): Boolean {
        val warning = raw.lowercase()
        return listOf(
            "nutrition", "nutrient", "energy", "kcal", "kj", "calorie",
            "protein", "carbohydrate", "sugar", "fat", "fiber", "fibre",
            "salt", "sodium", "serving"
        ).any { it in warning }
    }

    private fun foodComparator(query: String): Comparator<NativeFood> =
        compareBy<NativeFood> { foodSearchRank(it, query) }
            .thenBy { if (it.nutritionIntegrityWarning.isNullOrBlank()) 0 else 1 }
            .thenBy { if (it.nutritionApproximate) 1 else 0 }
            .thenBy(::sourcePriority)
            .thenByDescending { it.micronutrients.size }
            .thenBy { it.name.length }
            .thenBy { it.name.lowercase() }

    /** Evidence quality breaks ties after exact identity and integrity state. */
    private fun sourcePriority(food: NativeFood): Int = FoodEvidenceEngine.sourcePriority(food)

    private fun foodSearchRank(food: NativeFood, query: String): Int {
        val q = query.trim().lowercase()
        if (q.length < 2) return 99
        val name = food.name.lowercase()
        val haystack = "$name ${food.originalName.lowercase()} ${food.searchText.lowercase()} ${food.country.lowercase()} ${food.brand.lowercase()}"
        return when {
            name == q -> 0
            name.startsWith(q) -> 1
            name.split(' ', '-', '_').any { it.startsWith(q) } -> 2
            haystack.contains(q) -> 3
            else -> 99
        }
    }

    private fun loadLocal(context: Context): List<NativeFood> {
        val raw = context.assets.open("food_db.js").bufferedReader().use { it.readText() }
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = JSONArray(raw.substring(start, end + 1))
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                add(
                    NativeFood(
                        id = j.optString("id", "local:$i"),
                        name = j.optString("name", "Food"),
                        country = j.optString("country", ""),
                        kcal = j.optDoubleSafe("kcal"),
                        kcalKnown = j.hasNonNegativeNumber("kcal"),
                        protein = j.optDoubleSafe("protein"),
                        carbs = j.optDoubleSafe("carbs"),
                        fat = j.optDoubleSafe("fat"),
                        fibre = j.optDoubleSafe("fibre"),
                        sugar = j.optDoubleSafe("sugar"),
                        proteinKnown = j.hasNonNegativeNumber("protein"),
                        carbsKnown = j.hasNonNegativeNumber("carbs"),
                        fatKnown = j.hasNonNegativeNumber("fat"),
                        fibreKnown = j.hasNonNegativeNumber("fibre"),
                        sugarKnown = j.hasNonNegativeNumber("sugar"),
                        unit = j.optString("unit", "100 g"),
                        source = j.optString("source", "Project Superhuman reference"),
                        searchText = j.optString("salt") + " " + j.optString("aliases") + " " + j.optString("brand"),
                        brand = j.optString("brand", ""),
                        basisAmount = FoodUnitSystem.parseBasis(j.optString("unit", "100 g"))?.first ?: 100.0,
                        basisUnit = FoodUnitSystem.parseBasis(j.optString("unit", "100 g"))?.second ?: FoodUnit.G,
                        densityGPerMl = j.optNullableDouble("density_g_ml"),
                        densityApproximate = j.optBoolean("density_approx", false),
                        nutritionApproximate = j.optBoolean("approx", false)
                    )
                )
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 7_000
            requestMethod = "GET"
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) ?: return null
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return parsed?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun JSONObject.hasNonNegativeNumber(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        val value = opt(key) ?: return false
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return parsed?.let { it.isFinite() && it >= 0.0 } == true
    }

    private fun JSONObject.hasFiniteNumber(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        val value = opt(key) ?: return false
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return parsed?.isFinite() == true
    }

    private fun JSONObject.optTagList(key: String): List<String> {
        val value = opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    value.optString(i)
                        .substringAfter(':')
                        .trim()
                        .takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
            is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
            else -> emptyList()
        }
    }

    private fun JSONObject.optDoubleSafe(key: String): Double {
        val value = opt(key) ?: return 0.0
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun JSONObject.optStringList(key: String): String {
        val value = opt(key) ?: return ""
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) value.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(", ") { it.substringAfter(':').replace('-', ' ') }
            is String -> value
            else -> value.toString()
        }
    }
}
