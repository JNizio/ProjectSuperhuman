package com.projectsuperhuman.next

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    val searchText: String = ""
)

internal object NativeFoodCatalog {
    @Volatile private var cached: List<NativeFood>? = null

    suspend fun all(context: Context): List<NativeFood> = withContext(Dispatchers.IO) {
        cached ?: loadLocal(context).also { cached = it }
    }

    suspend fun search(context: Context, query: String, limit: Int = 30): List<NativeFood> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return all(context)
            .asSequence()
            .map { food ->
                val haystack = (food.name + " " + food.searchText + " " + food.country).lowercase()
                val score = when {
                    food.name.equals(query, ignoreCase = true) -> 0
                    food.name.lowercase().startsWith(q) -> 1
                    haystack.contains(q) -> 2
                    else -> 99
                }
                score to food
            }
            .filter { it.first < 99 }
            .sortedWith(compareBy<Pair<Int, NativeFood>> { it.first }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
            .toList()
    }

    suspend fun lookupBarcode(code: String): NativeFood? = withContext(Dispatchers.IO) {
        val digits = code.filter(Char::isDigit)
        if (digits.length !in setOf(8, 12, 13)) return@withContext null
        val conn = (URL("https://world.openfoodfacts.org/api/v2/product/$digits.json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ProjectSuperhuman/NextGen Android")
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val root = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
            if (root.optInt("status", 0) != 1) return@withContext null
            val p = root.optJSONObject("product") ?: return@withContext null
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            val name = p.optString("product_name").ifBlank { p.optString("generic_name") }.ifBlank { "Product $digits" }
            NativeFood(
                id = "off:$digits",
                name = name,
                country = p.optString("countries_tags").take(48),
                kcal = n.optDoubleSafe("energy-kcal_100g"),
                protein = n.optDoubleSafe("proteins_100g"),
                carbs = n.optDoubleSafe("carbohydrates_100g"),
                fat = n.optDoubleSafe("fat_100g"),
                fibre = n.optDoubleSafe("fiber_100g"),
                sugar = n.optDoubleSafe("sugars_100g"),
                unit = "100 g",
                source = "Open Food Facts",
                barcode = digits,
                searchText = p.optString("brands") + " " + p.optString("categories")
            )
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
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
                        source = j.optString("source", "Local reference"),
                        searchText = j.optString("salt") + " " + j.optString("aliases") + " " + j.optString("brand")
                    )
                )
            }
        }
    }

    private fun JSONObject.optDoubleSafe(key: String): Double {
        val value = opt(key) ?: return 0.0
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
