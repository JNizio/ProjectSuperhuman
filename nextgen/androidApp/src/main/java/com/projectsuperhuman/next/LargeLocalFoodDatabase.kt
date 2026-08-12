package com.projectsuperhuman.next

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Large, searchable, local nutrition reference library.
 *
 * Project Superhuman keeps its small hand-curated food_db.js for instant/offline startup, then
 * expands the device-side reference database from USDA FoodData Central. The source archives are
 * streamed directly into SQLite so the app never needs to hold the 60-200 MB source JSON in memory.
 *
 * FNDDS 2021-2023 is attempted first because it represents foods people actually report eating and
 * carries energy plus a broad nutrient panel. SR Legacy then expands the long tail. Only records with
 * complete kcal/protein/carbohydrate/fat data and at least four recognised micronutrients are kept.
 * The importer is restart-safe: each archive is one transaction and only marked complete afterwards.
 */
internal object LargeLocalFoodDatabase {
    const val MINIMUM_FOOD_TARGET = 10_000

    private const val FNDDS_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"
    private const val SR_LEGACY_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip"

    private const val FNDDS_META = "usda_fndds_2021_2023_complete"
    private const val SR_META = "usda_sr_legacy_complete"
    private const val USER_AGENT = "ProjectSuperhuman/11.2 (Android food reference importer)"

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bootstrapStarted = AtomicBoolean(false)

    fun ensureStarted(context: Context) {
        val app = context.applicationContext
        seedPriorityFoods(app)
        if (sourcesComplete(app)) return
        if (!bootstrapStarted.compareAndSet(false, true)) return

        bootstrapScope.launch {
            try {
                if (!metaFlag(app, FNDDS_META)) {
                    runCatching {
                        importArchive(
                            app,
                            FNDDS_URL,
                            sourceLabel = "USDA FNDDS 2021-2023",
                            completionKey = FNDDS_META
                        )
                    }
                }
                if (!metaFlag(app, SR_META)) {
                    runCatching {
                        importArchive(
                            app,
                            SR_LEGACY_URL,
                            sourceLabel = "USDA SR Legacy",
                            completionKey = SR_META
                        )
                    }
                }
            } finally {
                bootstrapStarted.set(false)
            }
        }
    }

    suspend fun count(context: Context): Int = withContext(Dispatchers.IO) {
        seedPriorityFoods(context.applicationContext)
        countBlocking(context.applicationContext)
    }

    suspend fun isReady(context: Context): Boolean = count(context) >= MINIMUM_FOOD_TARGET

    suspend fun search(context: Context, query: String, limit: Int = 28): List<NativeFood> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            ensureStarted(app)
            val q = normalize(query)
            if (q.length < 2) return@withContext emptyList()
            val contains = "%${escapeLike(q)}%"
            val prefix = "${escapeLike(q)}%"

            LargeFoodDb(app).use { helper ->
                helper.readableDatabase.rawQuery(
                    """
                    SELECT id, name, country, kcal, protein, carbs, fat, fibre, sugar,
                           unit, source, search_text, brand, micronutrients_json
                    FROM food_reference
                    WHERE normalized_name LIKE ? ESCAPE '\\' OR search_text LIKE ? ESCAPE '\\'
                    ORDER BY
                        CASE
                            WHEN normalized_name = ? THEN 0
                            WHEN normalized_name LIKE ? ESCAPE '\\' THEN 1
                            ELSE 2
                        END,
                        micronutrient_count DESC,
                        length(name),
                        name COLLATE NOCASE
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(contains, contains, q, prefix, limit.coerceIn(1, 80).toString())
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                NativeFood(
                                    id = cursor.getString(0),
                                    name = cursor.getString(1),
                                    country = cursor.getString(2),
                                    kcal = cursor.getDouble(3),
                                    protein = cursor.getDouble(4),
                                    carbs = cursor.getDouble(5),
                                    fat = cursor.getDouble(6),
                                    fibre = cursor.getDouble(7),
                                    sugar = cursor.getDouble(8),
                                    unit = cursor.getString(9),
                                    source = cursor.getString(10),
                                    searchText = cursor.getString(11),
                                    brand = cursor.getString(12),
                                    micronutrients = decodeMicros(cursor.getString(13))
                                )
                            )
                        }
                    }
                }
            }
        }

    private fun sourcesComplete(context: Context): Boolean =
        metaFlag(context, FNDDS_META) && metaFlag(context, SR_META)

    private fun countBlocking(context: Context): Int = LargeFoodDb(context).use { helper ->
        DatabaseUtils.queryNumEntries(helper.readableDatabase, "food_reference").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun metaFlag(context: Context, key: String): Boolean = LargeFoodDb(context).use { helper ->
        helper.readableDatabase.rawQuery(
            "SELECT value FROM food_reference_meta WHERE key = ? LIMIT 1",
            arrayOf(key)
        ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }
    }

    private fun seedPriorityFoods(context: Context) {
        LargeFoodDb(context).use { helper ->
            val db = helper.writableDatabase
            priorityFoods().forEach { insertFood(db, it, replace = false) }
        }
    }

    /**
     * A few high-value entries are bundled as tiny label-backed records so searches such as "skyr"
     * are useful immediately, before the larger USDA import has finished. Values are per 100 g.
     * The source name deliberately stays visible rather than pretending these are universal values.
     */
    private fun priorityFoods(): List<NativeFood> = listOf(
        NativeFood(
            id = "priority:siggis-skyr-plain-nonfat",
            name = "Skyr, plain, nonfat (0% fat)",
            country = "US",
            kcal = 60.0,
            protein = 10.67,
            carbs = 4.0,
            fat = 0.0,
            fibre = 0.0,
            sugar = 2.67,
            unit = "100 g",
            source = "siggi's label reference",
            searchText = "skyr plain zero fat fat free nonfat 0% icelandic yoghurt yogurt",
            brand = "siggi's",
            micronutrients = mapOf(
                "calcium" to NativeNutrient("calcium", "Calcium", 100.0, "mg"),
                "potassium" to NativeNutrient("potassium", "Potassium", 126.67, "mg"),
                "sodium" to NativeNutrient("sodium", "Sodium", 36.67, "mg")
            )
        ),
        NativeFood(
            id = "priority:arla-skyr-natural-02",
            name = "Arla Skyr Natural, 0.2% fat",
            country = "UK",
            kcal = 61.0,
            protein = 10.0,
            carbs = 4.0,
            fat = 0.2,
            fibre = 0.0,
            sugar = 4.0,
            unit = "100 g",
            source = "Arla UK label reference",
            searchText = "skyr plain natural low fat fat free 0.2% icelandic yoghurt yogurt",
            brand = "Arla",
            micronutrients = mapOf(
                // Label salt 0.14 g/100 g, converted with the standard salt ≈ sodium × 2.5 relation.
                "sodium" to NativeNutrient("sodium", "Sodium", 56.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:siggis-skyr-plain-whole",
            name = "Skyr, plain, whole milk (~4.1% fat)",
            country = "US",
            kcal = 100.0,
            protein = 10.59,
            carbs = 4.71,
            fat = 4.12,
            fibre = 0.0,
            sugar = 2.94,
            unit = "100 g",
            source = "siggi's label reference",
            searchText = "skyr plain whole milk full fat 4% 4.1% icelandic yoghurt yogurt",
            brand = "siggi's",
            micronutrients = mapOf(
                "calcium" to NativeNutrient("calcium", "Calcium", 100.0, "mg"),
                "potassium" to NativeNutrient("potassium", "Potassium", 141.18, "mg"),
                "sodium" to NativeNutrient("sodium", "Sodium", 50.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:arla-skyr-creamy-5",
            name = "Arla Skyr Creamy, 5% fat",
            country = "UK",
            kcal = 99.0,
            protein = 9.0,
            carbs = 3.9,
            fat = 5.0,
            fibre = 0.0,
            sugar = 3.9,
            unit = "100 g",
            source = "Arla UK label reference",
            searchText = "skyr creamy full fat 5% icelandic yoghurt yogurt",
            brand = "Arla",
            micronutrients = mapOf(
                // Label salt 0.13 g/100 g -> about 52 mg sodium.
                "sodium" to NativeNutrient("sodium", "Sodium", 52.0, "mg")
            )
        )
    )

    private fun importArchive(
        context: Context,
        archiveUrl: String,
        sourceLabel: String,
        completionKey: String
    ) {
        val connection = (URL(archiveUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 90_000
            requestMethod = "GET"
            useCaches = true
            setRequestProperty("Accept", "application/zip, application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            if (connection.responseCode !in 200..299) return
            ZipInputStream(BufferedInputStream(connection.inputStream, 64 * 1024)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && !entry.name.endsWith(".json", ignoreCase = true)) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                if (entry == null) return

                LargeFoodDb(context).use { helper ->
                    val db = helper.writableDatabase
                    db.beginTransaction()
                    try {
                        JsonReader(InputStreamReader(zip, Charsets.UTF_8)).use { reader ->
                            parseDownload(reader, db, sourceLabel)
                        }
                        putMeta(db, completionKey, "1")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDownload(reader: JsonReader, db: SQLiteDatabase, sourceLabel: String) {
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> parseFoodArray(reader, db, sourceLabel)
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (reader.peek() == JsonToken.BEGIN_ARRAY &&
                        (name.equals("foods", true) || name.endsWith("Foods", true))
                    ) {
                        parseFoodArray(reader, db, sourceLabel)
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
            else -> reader.skipValue()
        }
    }

    private fun parseFoodArray(reader: JsonReader, db: SQLiteDatabase, sourceLabel: String) {
        reader.beginArray()
        while (reader.hasNext()) {
            parseFood(reader, sourceLabel)?.let { insertFood(db, it, replace = false) }
        }
        reader.endArray()
    }

    private fun parseFood(reader: JsonReader, sourceLabel: String): NativeFood? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var fdcId: Long? = null
        var description = ""
        val nutrients = NutrientAccumulator()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "fdcId" -> fdcId = readLong(reader)
                "description" -> description = readString(reader)
                "foodNutrients" -> parseFoodNutrients(reader, nutrients)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val id = fdcId ?: return null
        if (description.isBlank()) return null
        val kcal = nutrients.kcal ?: return null
        val protein = nutrients.protein ?: return null
        val carbs = nutrients.carbs ?: return null
        val fat = nutrients.fat ?: return null
        if (!listOf(kcal, protein, carbs, fat).all { it.isFinite() && it >= 0.0 }) return null
        if (nutrients.micros.size < 4) return null

        return NativeFood(
            id = "usda:$id",
            name = description.trim(),
            country = "US",
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fibre = nutrients.fibre ?: 0.0,
            sugar = nutrients.sugar ?: 0.0,
            unit = "100 g",
            source = "$sourceLabel · FDC $id",
            searchText = "${description.lowercase(Locale.ROOT)} usda fooddata central fdc $id",
            micronutrients = nutrients.micros.toMap()
        )
    }

    private fun parseFoodNutrients(reader: JsonReader, out: NutrientAccumulator) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) parseFoodNutrient(reader, out)
        reader.endArray()
    }

    private fun parseFoodNutrient(reader: JsonReader, out: NutrientAccumulator) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }

        var nutrientId: Int? = null
        var nutrientName = ""
        var unitName = ""
        var amount: Double? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "amount", "value" -> amount = readDouble(reader)
                "nutrientId" -> nutrientId = readLong(reader)?.toInt()
                "nutrientName" -> nutrientName = readString(reader)
                "unitName" -> unitName = readString(reader)
                "nutrient" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> nutrientId = readLong(reader)?.toInt()
                                "name" -> nutrientName = readString(reader)
                                "unitName" -> unitName = readString(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    } else reader.skipValue()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val value = amount ?: return
        out.accept(nutrientId, nutrientName, unitName, value)
    }

    private class NutrientAccumulator {
        var kcal: Double? = null
        var protein: Double? = null
        var carbs: Double? = null
        var fat: Double? = null
        var fibre: Double? = null
        var sugar: Double? = null
        val micros = linkedMapOf<String, NativeNutrient>()

        fun accept(id: Int?, rawName: String, rawUnit: String, rawAmount: Double) {
            if (!rawAmount.isFinite() || rawAmount < 0.0) return
            val name = rawName.trim().lowercase(Locale.ROOT)
            val unit = normalizeUnit(rawUnit)

            when {
                id == 1008 || (name == "energy" && unit == "kcal") ->
                    kcal = rawAmount
                name == "energy" && unit == "kj" && kcal == null ->
                    kcal = rawAmount / 4.184
                id == 1003 || name == "protein" ->
                    protein = convertUnit(rawAmount, unit, "g")
                id == 1005 || name.startsWith("carbohydrate, by difference") ->
                    carbs = convertUnit(rawAmount, unit, "g")
                id == 1004 || name == "total lipid (fat)" || name == "total fat" ->
                    fat = convertUnit(rawAmount, unit, "g")
                id == 1079 || name.startsWith("fiber, total dietary") || name.startsWith("fibre, total") ->
                    fibre = convertUnit(rawAmount, unit, "g")
                id == 2000 || name == "total sugars" || name.startsWith("sugars, total") ->
                    sugar = convertUnit(rawAmount, unit, "g")

                name == "calcium, ca" -> putMicro("calcium", "Calcium", "mg", rawAmount, unit)
                name == "chloride, cl" -> putMicro("chloride", "Chloride", "mg", rawAmount, unit)
                name == "copper, cu" -> putMicro("copper", "Copper", "mg", rawAmount, unit)
                name == "iron, fe" -> putMicro("iron", "Iron", "mg", rawAmount, unit)
                name == "iodine, i" -> putMicro("iodine", "Iodine", "µg", rawAmount, unit)
                name == "magnesium, mg" -> putMicro("magnesium", "Magnesium", "mg", rawAmount, unit)
                name == "manganese, mn" -> putMicro("manganese", "Manganese", "mg", rawAmount, unit)
                name == "phosphorus, p" -> putMicro("phosphorus", "Phosphorus", "mg", rawAmount, unit)
                name == "potassium, k" -> putMicro("potassium", "Potassium", "mg", rawAmount, unit)
                name == "selenium, se" -> putMicro("selenium", "Selenium", "µg", rawAmount, unit)
                name == "sodium, na" -> putMicro("sodium", "Sodium", "mg", rawAmount, unit)
                name == "zinc, zn" -> putMicro("zinc", "Zinc", "mg", rawAmount, unit)
                name == "vitamin a, rae" -> putMicro("vitamin_a", "Vitamin A", "µg", rawAmount, unit)
                name == "thiamin" -> putMicro("vitamin_b1", "Vitamin B1", "mg", rawAmount, unit)
                name == "riboflavin" -> putMicro("vitamin_b2", "Vitamin B2", "mg", rawAmount, unit)
                name == "niacin" -> putMicro("niacin", "Niacin (B3)", "mg", rawAmount, unit)
                name == "pantothenic acid" -> putMicro("pantothenic_acid", "Pantothenic acid (B5)", "mg", rawAmount, unit)
                name == "vitamin b-6" || name == "vitamin b6" ->
                    putMicro("vitamin_b6", "Vitamin B6", "mg", rawAmount, unit)
                name == "folate, total" -> putMicro("folate", "Folate (B9)", "µg", rawAmount, unit)
                name == "vitamin b-12" || name == "vitamin b12" ->
                    putMicro("vitamin_b12", "Vitamin B12", "µg", rawAmount, unit)
                name.startsWith("vitamin c, total ascorbic acid") || name == "vitamin c" ->
                    putMicro("vitamin_c", "Vitamin C", "mg", rawAmount, unit)
                name.startsWith("vitamin d (d2 + d3") || name == "vitamin d" ->
                    putMicro("vitamin_d", "Vitamin D", "µg", rawAmount, unit)
                name.startsWith("vitamin e (alpha-tocopherol") || name == "vitamin e" ->
                    putMicro("vitamin_e", "Vitamin E", "mg", rawAmount, unit)
                name.startsWith("vitamin k (phylloquinone") || name == "vitamin k" ->
                    putMicro("vitamin_k", "Vitamin K", "µg", rawAmount, unit)
                name.startsWith("choline, total") || name == "choline" ->
                    putMicro("choline", "Choline", "mg", rawAmount, unit)
            }
        }

        private fun putMicro(
            id: String,
            label: String,
            targetUnit: String,
            value: Double,
            sourceUnit: String
        ) {
            if (micros.containsKey(id)) return
            val converted = convertUnit(value, sourceUnit, targetUnit) ?: return
            if (!converted.isFinite() || converted < 0.0) return
            micros[id] = NativeNutrient(id, label, converted, targetUnit)
        }
    }

    private fun insertFood(db: SQLiteDatabase, food: NativeFood, replace: Boolean) {
        val values = ContentValues().apply {
            put("id", food.id)
            put("name", food.name)
            put("normalized_name", normalize(food.name))
            put("country", food.country)
            put("kcal", food.kcal)
            put("protein", food.protein)
            put("carbs", food.carbs)
            put("fat", food.fat)
            put("fibre", food.fibre)
            put("sugar", food.sugar)
            put("unit", food.unit)
            put("source", food.source)
            put("search_text", normalize("${food.name} ${food.searchText} ${food.brand}"))
            put("brand", food.brand)
            put("micronutrients_json", encodeMicros(food.micronutrients))
            put("micronutrient_count", food.micronutrients.size)
        }
        db.insertWithOnConflict(
            "food_reference",
            null,
            values,
            if (replace) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun putMeta(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            "food_reference_meta",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun encodeMicros(micros: Map<String, NativeNutrient>): String {
        val root = JSONObject()
        micros.forEach { (id, nutrient) ->
            root.put(id, JSONObject().apply {
                put("label", nutrient.label)
                put("value", nutrient.valuePer100)
                put("unit", nutrient.unit)
            })
        }
        return root.toString()
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
                            unit = item.optString("unit").ifBlank { "mg" }
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun readString(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.NULL -> {
            reader.nextNull()
            ""
        }
        else -> {
            reader.skipValue()
            ""
        }
    }

    private fun readLong(reader: JsonReader): Long? = when (reader.peek()) {
        JsonToken.NUMBER -> runCatching { reader.nextLong() }.getOrNull()
        JsonToken.STRING -> reader.nextString().toLongOrNull()
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun readDouble(reader: JsonReader): Double? = when (reader.peek()) {
        JsonToken.NUMBER -> runCatching { reader.nextDouble() }.getOrNull()
        JsonToken.STRING -> reader.nextString().toDoubleOrNull()
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('µ', 'u')
        .replace('μ', 'u')
        .replace(Regex("[^a-z0-9%]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun normalizeUnit(raw: String): String = raw.trim().lowercase(Locale.ROOT)
        .replace("µ", "u")
        .replace("μ", "u")
        .replace("mcg", "ug")

    private fun convertUnit(value: Double, sourceRaw: String, targetRaw: String): Double? {
        val source = normalizeUnit(sourceRaw)
        val target = normalizeUnit(targetRaw)
        if (source.isBlank() || source == target) return value
        return when (source to target) {
            "g" to "mg" -> value * 1_000.0
            "g" to "ug" -> value * 1_000_000.0
            "mg" to "g" -> value / 1_000.0
            "mg" to "ug" -> value * 1_000.0
            "ug" to "mg" -> value / 1_000.0
            "ug" to "g" -> value / 1_000_000.0
            else -> null
        }
    }
}

private class LargeFoodDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "superhuman_large_food_reference.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_reference(
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                country TEXT NOT NULL,
                kcal REAL NOT NULL,
                protein REAL NOT NULL,
                carbs REAL NOT NULL,
                fat REAL NOT NULL,
                fibre REAL NOT NULL,
                sugar REAL NOT NULL,
                unit TEXT NOT NULL,
                source TEXT NOT NULL,
                search_text TEXT NOT NULL,
                brand TEXT NOT NULL,
                micronutrients_json TEXT NOT NULL,
                micronutrient_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX food_reference_name_idx ON food_reference(normalized_name)")
        db.execSQL("CREATE INDEX food_reference_micro_idx ON food_reference(micronutrient_count DESC)")
        db.execSQL(
            """
            CREATE TABLE food_reference_meta(
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
