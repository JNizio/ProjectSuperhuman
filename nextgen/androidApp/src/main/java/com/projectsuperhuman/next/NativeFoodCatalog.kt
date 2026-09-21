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

internal data class NativeNutrient(
    val id: String,
    val label: String,
    val valuePer100: Double,
    val unit: String
)

internal data class NativeFood(
    val id: String,
    val name: String,
    val country: String,
    val kcal: Double,
    val kcalKnown: Boolean = true,
    val protein: Double,
    val carbs: Double,
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
    val nutritionIntegrityWarning: String? = null,
    val originalName: String = "",
    val displayLanguage: String = "",
    val hasVerifiedEnglishName: Boolean = false,
    val basisAmount: Double = 100.0,
    val basisUnit: FoodUnit = FoodUnit.G,
    val densityGPerMl: Double? = null,
    val densityApproximate: Boolean = false,
    val productQuantity: Double? = null,
    val productQuantityUnit: FoodUnit? = null,
    val servingQuantity: Double? = null,
    val servingQuantityUnit: FoodUnit? = null,
    val servingLabel: String = ""
)

internal data class NativeFoodSearchResult(
    val foods: List<NativeFood>,
    val remoteAvailable: Boolean,
    val remoteCount: Int
)

internal object NativeFoodCatalog {
    private const val USER_AGENT = "ProjectSuperhuman/11.2.9 (Android; https://github.com/JNizio/ProjectSuperhuman)"
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
        FoodNutritionOverrideStore.applyAll(context, base)
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
            .sortedWith(foodComparator(q))
            .distinctBy { food -> food.barcode?.let { "barcode:$it" } ?: "name:${food.name.trim().lowercase()}" }

        // Broad/common searches stay completely local when we already have enough good candidates.
        // Multi-word queries are more likely to be a specific branded product, so OFF remains useful.
        val specificProductQuery = q.length >= 6 && q.any(Char::isWhitespace)
        val shouldQueryRemote = localCandidates.size < FAST_RESULT_COUNT || specificProductQuery
        val remoteResult = if (shouldQueryRemote) {
            searchOpenFoodFacts(q, limit = 12)
        } else {
            emptyList<NativeFood>() to true
        }

        val merged = FoodNutritionOverrideStore.applyAll(
            context,
            (localCandidates + remoteResult.first)
                .sortedWith(foodComparator(q))
                .distinctBy { food ->
                    food.barcode?.let { code -> "barcode:$code" }
                        ?: "name:${food.name.trim().lowercase()}"
                }
        ).take(requested)

        return NativeFoodSearchResult(
            foods = merged,
            remoteAvailable = remoteResult.second,
            remoteCount = remoteResult.first.size
        )
    }

    suspend fun lookupBarcode(code: String): NativeFood? = withContext(Dispatchers.IO) {
        val digits = code.filter(Char::isDigit)
        if (!NutritionMath.isValidBarcode(digits)) return@withContext null
        val fields = "code,lang,languages_tags,product_name,product_name_en,generic_name,generic_name_en,brands,countries_tags,categories,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutrition_data_per,data_quality_errors_tags,data_quality_warnings_tags,nutriments"
        val conn = openConnection("https://world.openfoodfacts.org/api/v2/product/$digits.json?fields=$fields")
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
            if (root.optInt("status", 0) != 1) return@withContext null
            val product = root.optJSONObject("product") ?: return@withContext null
            parseOpenFoodFactsProduct(product, digits)?.let(FoodNutritionOverrideStore::applyIfAttached)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
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

    private suspend fun searchOpenFoodFacts(query: String, limit: Int): Pair<List<NativeFood>, Boolean> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        synchronized(remoteCacheLock) { remoteSearchCache[key] }?.let { return@withContext it.take(limit) to true }

        // Open Food Facts currently keeps full-text search on the v1 CGI endpoint.
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val fields = "code,lang,languages_tags,product_name,product_name_en,generic_name,generic_name_en,brands,countries_tags,categories,quantity,product_quantity,product_quantity_unit,serving_size,serving_quantity,serving_quantity_unit,nutrition_data_per,data_quality_errors_tags,data_quality_warnings_tags,nutriments"
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=$limit&fields=$fields"
        val conn = openConnection(url)
        try {
            if (conn.responseCode !in 200..299) return@withContext emptyList<NativeFood>() to false
            val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
            val products = root.optJSONArray("products") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until products.length()) {
                    val p = products.optJSONObject(i) ?: continue
                    val food = parseOpenFoodFactsProduct(p, p.optString("code")) ?: continue
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

    private fun parseOpenFoodFactsProduct(p: JSONObject, fallbackCode: String): NativeFood? {
        val code = p.optString("code").ifBlank { fallbackCode }.filter(Char::isDigit)
        val nutriments = p.optJSONObject("nutriments") ?: JSONObject()
        val localizedName = resolveOpenFoodFactsDisplayName(p, code)
        val name = localizedName.displayName
        if (name.isBlank()) return null

        val kcalDirectKnown = nutriments.hasFiniteNumber("energy-kcal_100g")
        val kjKnown = nutriments.hasFiniteNumber("energy-kj_100g")
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
        val macroIntegrity = NutritionIntegrity.sanitizeMacros(
            kcal = kcal,
            protein = proteinRaw,
            carbs = carbsRaw,
            fat = fatRaw,
            proteinKnown = nutriments.hasFiniteNumber("proteins_100g"),
            carbsKnown = nutriments.hasFiniteNumber("carbohydrates_100g"),
            fatKnown = nutriments.hasFiniteNumber("fat_100g")
        )

        val offQualityWarnings = listOf(
            p.optStringList("data_quality_errors_tags"),
            p.optStringList("data_quality_warnings_tags")
        ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }

        return NativeFood(
            id = if (code.isNotBlank()) "off:$code" else "off:${name.lowercase().hashCode()}",
            name = name,
            country = country,
            kcal = kcal,
            kcalKnown = kcalKnown,
            protein = macroIntegrity.protein,
            carbs = macroIntegrity.carbs,
            fat = macroIntegrity.fat,
            fibre = nutriments.optDoubleSafe("fiber_100g"),
            sugar = nutriments.optDoubleSafe("sugars_100g"),
            unit = basis,
            source = "Open Food Facts",
            barcode = code.ifBlank { null },
            searchText = "$brand $categories",
            brand = brand,
            quantity = p.optString("quantity"),
            servingSize = p.optString("serving_size"),
            micronutrients = extractMicronutrients(nutriments),
            proteinKnown = macroIntegrity.proteinKnown,
            carbsKnown = macroIntegrity.carbsKnown,
            fatKnown = macroIntegrity.fatKnown,
            fibreKnown = nutriments.hasFiniteNumber("fiber_100g"),
            sugarKnown = nutriments.hasFiniteNumber("sugars_100g"),
            nutritionIntegrityWarning = listOfNotNull(macroIntegrity.warning, offQualityWarnings).joinToString(" · ").ifBlank { null },
            originalName = localizedName.originalName,
            displayLanguage = localizedName.displayLanguage,
            hasVerifiedEnglishName = localizedName.hasVerifiedEnglishName,
            basisAmount = 100.0,
            basisUnit = basisUnit,
            densityGPerMl = null,
            densityApproximate = false,
            productQuantity = p.optNullableDouble("product_quantity"),
            productQuantityUnit = productQuantityUnit,
            servingQuantity = p.optNullableDouble("serving_quantity"),
            servingQuantityUnit = servingQuantityUnit,
            servingLabel = p.optString("serving_size")
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

    private fun extractMicronutrients(n: JSONObject): Map<String, NativeNutrient> {
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
            out[spec.id] = NativeNutrient(spec.id, spec.label, converted, spec.unit)
        }
        if (!out.containsKey("sodium")) {
            val saltKey = "salt_100g"
            if (n.hasFiniteNumber(saltKey)) {
                val saltGrams = n.optDoubleSafe(saltKey)
                if (saltGrams.isFinite() && saltGrams >= 0.0) {
                    out["sodium"] = NativeNutrient(
                        id = "sodium",
                        label = "Sodium",
                        valuePer100 = (saltGrams / 2.5) * 1_000.0,
                        unit = "mg"
                    )
                }
            }
        }
        return out
    }

    private fun foodComparator(query: String): Comparator<NativeFood> =
        compareBy<NativeFood> { foodSearchRank(it, query) }
            .thenBy(::sourcePriority)
            .thenByDescending { it.micronutrients.size }
            .thenBy { it.name.length }
            .thenBy { it.name.lowercase() }

    /** Common/curated foods win ties before long-tail reference rows and remote products. */
    private fun sourcePriority(food: NativeFood): Int = when {
        food.source.contains("label reference", ignoreCase = true) -> 0
        food.source.startsWith("Project Superhuman", ignoreCase = true) || food.source.startsWith("Local reference", ignoreCase = true) -> 1
        food.source.contains("Foundation Foods", ignoreCase = true) -> 2
        food.source.contains("FNDDS", ignoreCase = true) -> 3
        food.source.startsWith("USDA", ignoreCase = true) -> 4
        food.source.contains("Open Food Facts", ignoreCase = true) -> 5
        else -> 3
    }

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
                        protein = j.optDoubleSafe("protein"),
                        carbs = j.optDoubleSafe("carbs"),
                        fat = j.optDoubleSafe("fat"),
                        fibre = j.optDoubleSafe("fibre"),
                        sugar = j.optDoubleSafe("sugar"),
                        unit = j.optString("unit", "100 g"),
                        source = j.optString("source", "Project Superhuman reference"),
                        searchText = j.optString("salt") + " " + j.optString("aliases") + " " + j.optString("brand"),
                        brand = j.optString("brand", ""),
                        basisAmount = FoodUnitSystem.parseBasis(j.optString("unit", "100 g"))?.first ?: 100.0,
                        basisUnit = FoodUnitSystem.parseBasis(j.optString("unit", "100 g"))?.second ?: FoodUnit.G,
                        densityGPerMl = j.optNullableDouble("density_g_ml"),
                        densityApproximate = j.optBoolean("density_approx", false)
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

    private fun JSONObject.hasFiniteNumber(key: String): Boolean {
        if (!has(key) || isNull(key)) return false
        val value = opt(key) ?: return false
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
        return parsed?.isFinite() == true
    }

    private fun JSONObject.optDoubleSafe(key: String): Double {
        val value = opt(key) ?: return 0.0
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
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
