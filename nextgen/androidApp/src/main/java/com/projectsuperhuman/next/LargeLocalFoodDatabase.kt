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
 * Current USDA Foundation Foods are imported first for high-quality analytical references. FNDDS
 * 2021-2023 then adds foods people actually report eating, and SR Legacy expands the long tail.
 * Only records with complete kcal/protein/carbohydrate/fat data and at least four recognised
 * micronutrients are kept.
 * The importer is restart-safe: each archive is one transaction and only marked complete afterwards.
 */
internal object LargeLocalFoodDatabase {
    const val MINIMUM_FOOD_TARGET = 10_000

    private const val FOUNDATION_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip"
    private const val FNDDS_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"
    private const val SR_LEGACY_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip"

    private const val FOUNDATION_META = "usda_foundation_2026_04_complete"
    private const val FNDDS_META = "usda_fndds_2021_2023_complete"
    private const val SR_META = "usda_sr_legacy_complete"
    private const val USER_AGENT = "ProjectSuperhuman/11.3 (Android food reference importer)"

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bootstrapStarted = AtomicBoolean(false)
    private val prioritySeeded = AtomicBoolean(false)

    fun ensureStarted(context: Context) {
        val app = context.applicationContext
        seedPriorityFoodsOnce(app)
        if (sourcesComplete(app)) return
        if (!bootstrapStarted.compareAndSet(false, true)) return

        bootstrapScope.launch {
            try {
                if (!metaFlag(app, FOUNDATION_META)) {
                    runCatching {
                        importArchive(
                            app,
                            FOUNDATION_URL,
                            sourceLabel = "USDA Foundation Foods 2026-04",
                            completionKey = FOUNDATION_META
                        )
                    }
                }
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
        seedPriorityFoodsOnce(context.applicationContext)
        countBlocking(context.applicationContext)
    }

    suspend fun isReady(context: Context): Boolean = count(context) >= MINIMUM_FOOD_TARGET

    suspend fun search(context: Context, query: String, limit: Int = 28): List<NativeFood> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            ensureStarted(app)
            val q = normalize(query)
            if (q.length < 2) return@withContext emptyList()
            val contains = "%$q%"
            val prefix = "$q%"

            LargeFoodDb(app).use { helper ->
                helper.readableDatabase.rawQuery(
                    """
                    SELECT id, name, country, kcal, protein, carbs, fat, fibre, sugar,
                           protein_known, carbs_known, fat_known, fibre_known, sugar_known,
                           unit, source, search_text, brand, micronutrients_json
                    FROM food_reference
                    WHERE normalized_name LIKE ? OR search_text LIKE ?
                    ORDER BY
                        CASE
                            WHEN normalized_name = ? THEN 0
                            WHEN normalized_name LIKE ? THEN 1
                            ELSE 2
                        END,
                        CASE
                            WHEN source LIKE 'USDA Foundation Foods%' THEN 0
                            WHEN source LIKE 'USDA FNDDS%' THEN 1
                            WHEN source LIKE 'USDA SR Legacy%' THEN 2
                            ELSE 3
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
                                    carbohydrateDefinition = if (cursor.getString(15).startsWith("USDA")) {
                                        CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE
                                    } else {
                                        CarbohydrateDefinition.UNKNOWN
                                    },
                                    fat = cursor.getDouble(6),
                                    fibre = cursor.getDouble(7),
                                    sugar = cursor.getDouble(8),
                                    proteinKnown = cursor.getInt(9) != 0,
                                    carbsKnown = cursor.getInt(10) != 0,
                                    fatKnown = cursor.getInt(11) != 0,
                                    fibreKnown = cursor.getInt(12) != 0,
                                    sugarKnown = cursor.getInt(13) != 0,
                                    unit = cursor.getString(14),
                                    source = cursor.getString(15),
                                    searchText = cursor.getString(16),
                                    brand = cursor.getString(17),
                                    micronutrients = decodeMicros(cursor.getString(18))
                                )
                            )
                        }
                    }
                }
            }
        }

    private fun sourcesComplete(context: Context): Boolean =
        metaFlag(context, FOUNDATION_META) && metaFlag(context, FNDDS_META) && metaFlag(context, SR_META)

    private fun countBlocking(context: Context): Int = LargeFoodDb(context).use { helper ->
        DatabaseUtils.queryNumEntries(helper.readableDatabase, "food_reference")
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun metaFlag(context: Context, key: String): Boolean = LargeFoodDb(context).use { helper ->
        helper.readableDatabase.rawQuery(
            "SELECT value FROM food_reference_meta WHERE key = ? LIMIT 1",
            arrayOf(key)
        ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }
    }

    private fun seedPriorityFoodsOnce(context: Context) {
        if (!prioritySeeded.compareAndSet(false, true)) return
        runCatching { seedPriorityFoods(context) }
            .onFailure { prioritySeeded.set(false) }
    }

    private fun seedPriorityFoods(context: Context) {
        LargeFoodDb(context).use { helper ->
            val db = helper.writableDatabase
            priorityFoods().forEach { insertFood(db, it, replace = false) }
        }
    }

    /**
     * High-value foods are bundled as tiny label-backed records so searches such as "skyr" and
     * "sourdough" work immediately, before the larger USDA import has finished. Values are per
     * 100 g. Only micronutrients actually present on the source label (or sodium derived from the
     * label's salt value) are recorded; missing micronutrients stay unknown rather than guessed.
     */
    private fun priorityFoods(): List<NativeFood> = listOf(
        NativeFood(
            id = "priority:piatnica-skyr-natural-0",
            name = "Piątnica Skyr Naturalny, 0% fat",
            country = "PL",
            kcal = 64.0,
            protein = 12.0,
            carbs = 4.1,
            fat = 0.0,
            fibre = 0.0,
            sugar = 4.1,
            unit = "100 g",
            source = "Piątnica label reference",
            searchText = "piatnica skyr natural naturalny plain zero fat 0% yoghurt yogurt",
            brand = "Piątnica",
            micronutrients = mapOf(
                "sodium" to NativeNutrient("sodium", "Sodium", 40.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:piatnica-skyr-drinking-18",
            name = "Piątnica Skyr drinking natural, 1.8% fat",
            country = "PL",
            kcal = 64.0,
            protein = 7.6,
            carbs = 4.3,
            fat = 1.8,
            fibre = 0.0,
            sugar = 3.9,
            unit = "100 g",
            source = "Piątnica label reference",
            searchText = "piatnica skyr drinking pitny natural naturalny 1.8% low fat yoghurt yogurt",
            brand = "Piątnica",
            micronutrients = mapOf(
                "sodium" to NativeNutrient("sodium", "Sodium", 40.0, "mg")
            )
        ),
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
                "sodium" to NativeNutrient("sodium", "Sodium", 52.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:waitrose-white-sourdough",
            name = "White sourdough bread",
            country = "UK",
            kcal = 240.0,
            protein = 9.6,
            carbs = 47.0,
            fat = 0.8,
            fibre = 3.2,
            sugar = 2.3,
            unit = "100 g",
            source = "Waitrose No.1 label reference",
            searchText = "sourdough bread white loaf chleb na zakwasie",
            brand = "Waitrose No.1",
            micronutrients = mapOf(
                "sodium" to NativeNutrient("sodium", "Sodium", 444.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:bertinet-wholemeal-sourdough",
            name = "Wholemeal sourdough bread",
            country = "UK",
            kcal = 205.0,
            protein = 7.1,
            carbs = 37.0,
            fat = 1.5,
            fibre = 7.5,
            sugar = 2.0,
            unit = "100 g",
            source = "Bertinet Bakery label reference",
            searchText = "sourdough bread wholemeal whole wheat wholegrain chleb na zakwasie",
            brand = "Bertinet Bakery",
            micronutrients = mapOf(
                "sodium" to NativeNutrient("sodium", "Sodium", 560.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:bertinet-seeded-sourdough",
            name = "Seeded sourdough bread",
            country = "UK",
            kcal = 232.0,
            protein = 9.5,
            carbs = 36.0,
            fat = 4.1,
            fibre = 6.7,
            sugar = 1.2,
            unit = "100 g",
            source = "Bertinet Bakery label reference",
            searchText = "sourdough bread seeded seeds wholegrain chleb na zakwasie",
            brand = "Bertinet Bakery",
            micronutrients = mapOf(
                "sodium" to NativeNutrient("sodium", "Sodium", 400.0, "mg")
            )
        ),
        NativeFood(
            id = "priority:waitrose-spelt-sourdough",
            name = "Spelt sourdough bread",
            country = "UK",
            kcal = 241.0,
            protein = 9.4,
            carbs = 46.0,
            fat = 1.2,
            fibre = 4.3,
            sugar = 1.0,
            unit = "100 g",
            source = "Waitrose No.1 label reference",
            searchText = "sourdough bread spelt orkisz chleb na zakwasie",
            brand = "Waitrose No.1",
            micronutrients = mapOf(
                "sodium" to NativeNutrient("sodium", "Sodium", 308.0, "mg")
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
            carbohydrateDefinition = CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE,
            fat = fat,
            fibre = nutrients.fibre ?: 0.0,
            sugar = nutrients.sugar ?: 0.0,
            proteinKnown = true,
            carbsKnown = true,
            fatKnown = true,
            fibreKnown = nutrients.fibre != null,
            sugarKnown = nutrients.sugar != null,
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
            put("protein_known", if (food.proteinKnown) 1 else 0)
            put("carbs_known", if (food.carbsKnown) 1 else 0)
            put("fat_known", if (food.fatKnown) 1 else 0)
            put("fibre_known", if (food.fibreKnown) 1 else 0)
            put("sugar_known", if (food.sugarKnown) 1 else 0)
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
        .replace('ą', 'a')
        .replace('ć', 'c')
        .replace('ę', 'e')
        .replace('ł', 'l')
        .replace('ń', 'n')
        .replace('ó', 'o')
        .replace('ś', 's')
        .replace('ź', 'z')
        .replace('ż', 'z')
        .replace('µ', 'u')
        .replace('μ', 'u')
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

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
    2
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
                protein_known INTEGER NOT NULL,
                carbs_known INTEGER NOT NULL,
                fat_known INTEGER NOT NULL,
                fibre_known INTEGER NOT NULL,
                sugar_known INTEGER NOT NULL,
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Reference data is reproducible from bundled priority rows + USDA archives. Rebuilding is
        // safer than carrying forward rows where missing fibre/sugar had previously been stored as
        // known zero.
        db.execSQL("DROP TABLE IF EXISTS food_reference")
        db.execSQL("DROP TABLE IF EXISTS food_reference_meta")
        onCreate(db)
    }
}
