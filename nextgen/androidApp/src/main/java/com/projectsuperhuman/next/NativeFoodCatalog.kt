package com.projectsuperhuman.next

import android.content.Context
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
    val micronutrients: Map<String, NativeNutrient> = emptyMap()
)

internal data class NativeFoodSearchResult(
    val foods: List<NativeFood>,
    val remoteAvailable: Boolean,
    val remoteCount: Int
)

internal object NativeFoodCatalog {
    private const val USER_AGENT = "ProjectSuperhuman/11.2.9 (Android; https://github.com/JNizio/ProjectSuperhuman)"
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

    suspend fun all(context: Context): List<NativeFood> = withContext(Dispatchers.IO) {
        FoodNutritionOverrideStore.attach(context)
        LargeLocalFoodDatabase.ensureStarted(context)
        val base = cached ?: loadLocal(context).also { cached = it }
        FoodNutritionOverrideStore.applyAll(context, base)
    }

    /** Number of local reference foods available without depending on an Open Food Facts search. */
    suspend fun localReferenceCount(context: Context): Int {
        val bundled = all(context).size
        return bundled + LargeLocalFoodDatabase.count(context)
    }

    /**
     * One native search surface: bundled generic foods + the large local USDA-backed reference
     * library + Open Food Facts branded products. Network failure never blocks either local source.
     * Any user-edited nutrition values are layered over the source record before it reaches the UI.
     */
    suspend fun search(context: Context, query: String, limit: Int = 36): NativeFoodSearchResult {
        FoodNutritionOverrideStore.attach(context)
        LargeLocalFoodDatabase.ensureStarted(context)
        val q = query.trim()
        if (q.length < 2) return NativeFoodSearchResult(emptyList(), remoteAvailable = true, remoteCount = 0)

        val bundledLocal = searchLocal(context, q, limit = 14)
        val expandedLocal = LargeLocalFoodDatabase.search(context, q, limit = 26)
        val remoteResult = searchOpenFoodFacts(q, limit = 22)
        val merged = FoodNutritionOverrideStore.applyAll(
            context,
            (bundledLocal + expandedLocal + remoteResult.first)
                .distinctBy { food ->
                    food.barcode?.let { code -> "barcode:$code" }
                        ?: "name:${food.name.trim().lowercase()}"
                }
        ).sortedWith(
            compareBy<NativeFood> { foodSearchRank(it, q) }
                .thenBy { if (it.source.startsWith("Project Superhuman") || it.source.startsWith("USDA")) 0 else 1 }
                .thenByDescending { it.micronutrients.size }
                .thenBy { it.name.lowercase() }
        ).take(limit)

        return NativeFoodSearchResult(
            foods = merged,
            remoteAvailable = remoteResult.second,
            remoteCount = remoteResult.first.size
        )
    }

    suspend fun lookupBarcode(code: String): NativeFood? = withContext(Dispatchers.IO) {
        val digits = code.filter(Char::isDigit)
        if (digits.length !in setOf(8, 12, 13, 14)) return@withContext null
        val fields = "code,product_name,generic_name,brands,countries_tags,categories,quantity,serving_size,product_quantity_unit,nutriments"
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

    private suspend fun searchLocal(context: Context, query: String, limit: Int): List<NativeFood> {
        val q = query.trim().lowercase()
        return all(context)
            .asSequence()
            .map { food -> foodSearchRank(food, q) to food }
            .filter { it.first < 99 }
            .sortedWith(compareBy<Pair<Int, NativeFood>> { it.first }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
            .toList()
    }

    private suspend fun searchOpenFoodFacts(query: String, limit: Int): Pair<List<NativeFood>, Boolean> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        synchronized(remoteCacheLock) { remoteSearchCache[key] }?.let { return@withContext it.take(limit) to true }

        // Open Food Facts currently keeps full-text search on the v1 CGI endpoint.
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val fields = "code,product_name,generic_name,brands,countries_tags,categories,quantity,serving_size,product_quantity_unit,nutriments"
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
        val name = p.optString("product_name")
            .ifBlank { p.optString("generic_name") }
            .ifBlank { p.optString("brands") }
            .ifBlank { if (code.isNotBlank()) "Product $code" else "" }
        if (name.isBlank()) return null

        val kcal = nutriments.optDoubleSafe("energy-kcal_100g").takeIf { it > 0.0 }
            ?: (nutriments.optDoubleSafe("energy-kj_100g") / 4.184).takeIf { it > 0.0 }
            ?: 0.0
        val productUnit = p.optString("product_quantity_unit").lowercase()
        val basis = if (productUnit in setOf("ml", "l", "cl", "dl")) "100 ml" else "100 g"
        val brand = p.optString("brands").trim()
        val country = p.optStringList("countries_tags").take(80)
        val categories = p.optString("categories").take(180)

        return NativeFood(
            id = if (code.isNotBlank()) "off:$code" else "off:${name.lowercase().hashCode()}",
            name = name,
            country = country,
            kcal = kcal,
            protein = nutriments.optDoubleSafe("proteins_100g"),
            carbs = nutriments.optDoubleSafe("carbohydrates_100g"),
            fat = nutriments.optDoubleSafe("fat_100g"),
            fibre = nutriments.optDoubleSafe("fiber_100g"),
            sugar = nutriments.optDoubleSafe("sugars_100g"),
            unit = basis,
            source = "Open Food Facts",
            barcode = code.ifBlank { null },
            searchText = "$brand $categories",
            brand = brand,
            quantity = p.optString("quantity"),
            servingSize = p.optString("serving_size"),
            micronutrients = extractMicronutrients(nutriments)
        )
    }

    private fun extractMicronutrients(n: JSONObject): Map<String, NativeNutrient> {
        val out = linkedMapOf<String, NativeNutrient>()
        micronutrientSpecs.forEach { spec ->
            // Several OFF taxonomy ids can map to one canonical nutrient (e.g. folate).
            if (out.containsKey(spec.id)) return@forEach
            val grams = n.optDoubleSafe("${spec.offId}_100g")
            if (grams <= 0.0 || !grams.isFinite()) return@forEach
            val converted = grams * spec.multiplierFromGrams
            if (converted <= 0.0 || !converted.isFinite()) return@forEach
            out[spec.id] = NativeNutrient(spec.id, spec.label, converted, spec.unit)
        }
        return out
    }

    private fun foodSearchRank(food: NativeFood, query: String): Int {
        val q = query.trim().lowercase()
        if (q.length < 2) return 99
        val name = food.name.lowercase()
        val haystack = "$name ${food.searchText.lowercase()} ${food.country.lowercase()} ${food.brand.lowercase()}"
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
                        brand = j.optString("brand", "")
                    )
                )
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 9_000
            requestMethod = "GET"
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
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
