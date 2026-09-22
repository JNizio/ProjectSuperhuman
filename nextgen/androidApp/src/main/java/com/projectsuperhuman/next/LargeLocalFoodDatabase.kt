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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.LinkedHashMap
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
    const val CORE_FOOD_TARGET = 10_000

    private val CORE_MICRONUTRIENTS = setOf(
        "calcium", "chloride", "copper", "iron", "iodine", "magnesium", "manganese",
        "phosphorus", "potassium", "selenium", "sodium", "zinc",
        "vitamin_a", "vitamin_b1", "vitamin_b2", "niacin", "pantothenic_acid",
        "vitamin_b6", "biotin", "folate", "vitamin_b12", "vitamin_c", "vitamin_d",
        "vitamin_e", "vitamin_k", "choline"
    )
    // Every generated core food must have broad source-backed micronutrient coverage.
    // Prefer substantially richer profiles, but never fabricate missing values to fill a quota.
    private const val CORE_MIN_MICRONUTRIENTS = 12
    private const val CORE_PREFERRED_MICRONUTRIENTS = 18

    private val localSearchCache = object : LinkedHashMap<String, List<NativeFood>>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<NativeFood>>?): Boolean = size > 48
    }
    private val localSearchCacheLock = Any()

    private const val FOUNDATION_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip"
    private const val FNDDS_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip"
    private const val SR_LEGACY_URL =
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip"

    private const val FOUNDATION_META = "usda_foundation_2026_04_complete"
    private const val FNDDS_META = "usda_fndds_2021_2023_complete"
    private const val SR_META = "usda_sr_legacy_complete"
    private const val USER_AGENT = "ProjectSuperhuman/11.4 (Android food reference importer)"
    private const val PLANT_CLASSIFIER_META = "plant_classifier_schema"
    private const val FOOD_TAXONOMY_META = "food_taxonomy_schema"
    private const val CORE_SCHEMA_META = "project_superhuman_core_food_schema"
    private const val CORE_SCHEMA_VERSION = 10

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

    suspend fun audit(context: Context): LocalFoodDatabaseAuditReport = withContext(Dispatchers.IO) {
        seedPriorityFoodsOnce(context.applicationContext)
        LocalFoodDatabaseAuditor.audit(context.applicationContext)
    }

    suspend fun writeAuditReports(context: Context): Pair<java.io.File, java.io.File> = withContext(Dispatchers.IO) {
        seedPriorityFoodsOnce(context.applicationContext)
        LocalFoodDatabaseAuditor.writeReports(context.applicationContext)
    }

    suspend fun coreCount(context: Context): Int = withContext(Dispatchers.IO) {
        seedPriorityFoodsOnce(context.applicationContext)
        LargeFoodDb(context.applicationContext).use { helper ->
            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM food_reference WHERE core_rank IS NOT NULL",
                null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        }
    }

    suspend fun coreReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        seedPriorityFoodsOnce(context.applicationContext)
        LargeFoodDb(context.applicationContext).use { helper ->
            DatabaseUtils.longForQuery(
                helper.readableDatabase,
                "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%' " +
                    "AND essential_micronutrient_count >= $CORE_MIN_MICRONUTRIENTS " +
                    "AND food_tags_json <> '[]' AND taxonomy_version >= ${FoodTaxonomyClassifier.SCHEMA_VERSION}",
                null
            ) >= CORE_FOOD_TARGET
        }
    }

    suspend fun search(context: Context, query: String, limit: Int = 28): List<NativeFood> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            ensureStarted(app)
            val q = normalize(query)
            if (q.length < 2) return@withContext emptyList()

            val requested = limit.coerceIn(1, 80)
            val cacheKey = q + "|" + requested
            synchronized(localSearchCacheLock) {
                localSearchCache[cacheKey]?.let { return@withContext it }
            }

            val queryTokens = q.split(' ').filter(String::isNotBlank)
            val retrievalQuery = when {
                queryTokens.any { it == "egg" || it == "eggs" } -> "egg"
                "chicken" in queryTokens && "breast" in queryTokens -> "chicken breast"
                q == "rocket" || q.startsWith("rocket ") -> q.replaceFirst("rocket", "arugula")
                q == "courgette" || q.startsWith("courgette ") -> q.replaceFirst("courgette", "zucchini")
                q == "aubergine" || q.startsWith("aubergine ") -> q.replaceFirst("aubergine", "eggplant")
                q == "swede" || q.startsWith("swede ") -> q.replaceFirst("swede", "rutabaga")
                q == "coriander" || q.startsWith("coriander ") -> q.replaceFirst("coriander", "cilantro")
                else -> q
            }
            val prefix = retrievalQuery + "%"
            val contains = "%" + retrievalQuery + "%"

            val raw = LargeFoodDb(app).use { helper ->
                val db = helper.readableDatabase

                fun queryRows(namePattern: String, searchPattern: String, rowLimit: Int): List<NativeFood> =
                    db.rawQuery(
                        """
                        SELECT id, name, country, kcal, protein, carbs, fat, fibre, sugar,
                               protein_known, carbs_known, fat_known, fibre_known, sugar_known,
                               unit, source, search_text, brand, micronutrients_json, core_rank,
                               saturated_fat, saturated_fat_known, sodium_mg, sodium_known,
                               salt_g, salt_known, unknown_micronutrients_json, source_record_id,
                               plant_food, plant_food_kind, plant_diversity_key,
                               plant_diversity_eligible, food_tags_json, taxonomy_version,
                               preparation_state, serving_quantity, serving_unit, serving_label
                        FROM food_reference
                        WHERE normalized_name LIKE ? OR search_text LIKE ?
                        ORDER BY
                            CASE WHEN core_rank IS NULL THEN 1 ELSE 0 END,
                            core_rank,
                            CASE
                                WHEN normalized_name = ? THEN 0
                                WHEN normalized_name LIKE ? THEN 1
                                ELSE 2
                            END,
                            CASE
                                WHEN id LIKE 'core:%' THEN 0
                                WHEN source LIKE 'USDA Foundation Foods%' THEN 1
                                WHEN source LIKE 'USDA FNDDS%' THEN 2
                                WHEN source LIKE 'USDA SR Legacy%' THEN 3
                                ELSE 4
                            END,
                            micronutrient_count DESC,
                            length(name),
                            name COLLATE NOCASE
                        LIMIT ?
                        """.trimIndent(),
                        arrayOf(namePattern, searchPattern, retrievalQuery, prefix, rowLimit.toString())
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                val rawName = cursor.getString(1)
                                val source = cursor.getString(15)
                                val sourceId = cursor.getString(0)
                                add(
                                    NativeFood(
                                        id = sourceId,
                                        name = humanizeReferenceName(rawName),
                                        originalName = rawName,
                                        country = cursor.getString(2),
                                        kcal = cursor.getDouble(3),
                                        protein = cursor.getDouble(4),
                                        carbs = cursor.getDouble(5),
                                        carbohydrateDefinition = if (source.contains("USDA")) {
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
                                        source = source,
                                        searchText = cursor.getString(16),
                                        brand = cursor.getString(17),
                                        micronutrients = decodeMicros(cursor.getString(18)),
                                        saturatedFat = cursor.getDouble(20),
                                        saturatedFatKnown = cursor.getInt(21) != 0,
                                        sodiumMg = cursor.getDouble(22),
                                        sodiumKnown = cursor.getInt(23) != 0,
                                        salt = cursor.getDouble(24),
                                        saltKnown = cursor.getInt(25) != 0,
                                        sourceRecordId = cursor.getString(27).ifBlank { sourceId.removePrefix("usda:") },
                                        isPlantFood = cursor.getInt(28) != 0,
                                        plantFoodKind = cursor.getString(29)
                                            .let { raw -> runCatching { PlantFoodKind.valueOf(raw) }.getOrDefault(PlantFoodKind.NONE) },
                                        plantDiversityKey = cursor.getString(30),
                                        plantDiversityEligible = cursor.getInt(31) != 0,
                                        foodTags = decodeFoodTags(cursor.getString(32)),
                                        foodTaxonomyVersion = cursor.getInt(33),
                                        preparationState = cursor.getString(34)
                                            .let { raw -> runCatching { FoodPreparationState.valueOf(raw) }.getOrDefault(FoodPreparationState.UNSPECIFIED) },
                                        servingQuantity = if (cursor.isNull(35)) null else cursor.getDouble(35),
                                        servingQuantityUnit = cursor.getString(36)
                                            .takeIf(String::isNotBlank)
                                            ?.let { raw -> runCatching { FoodUnit.valueOf(raw) }.getOrNull() },
                                        servingLabel = cursor.getString(37),
                                        sourceType = when {
                                            source.contains("USDA Foundation Foods") -> FoodDataSourceType.USDA_FOUNDATION
                                            source.contains("USDA FNDDS") -> FoodDataSourceType.USDA_FNDDS
                                            source.contains("USDA SR Legacy") -> FoodDataSourceType.USDA_SR_LEGACY
                                            else -> FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE
                                        }
                                    )
                                )
                            }
                        }
                    }

                val fast = queryRows(prefix, prefix, requested.coerceAtLeast(12))
                if (fast.size >= minOf(requested, 6)) {
                    fast
                } else {
                    (fast + queryRows(contains, contains, requested.coerceAtLeast(16)))
                        .distinctBy { it.id }
                }
            }

            val localCanonical = raw.filter { it.id.startsWith("core:") }
            val canonicalFamilies = localCanonical.mapNotNull { food ->
                when {
                    food.id.startsWith("core:egg:") -> "egg"
                    food.id.startsWith("core:chicken:breast:") -> "chicken_breast"
                    else -> null
                }
            }.toSet()

            val visible = raw.filterNot { food ->
                if (food.id.startsWith("core:")) return@filterNot false
                val text = normalize(food.originalName.ifBlank { food.name })
                when {
                    "egg" in canonicalFamilies && text.contains("egg") -> true
                    "chicken_breast" in canonicalFamilies &&
                        text.contains("chicken breast") &&
                        (text.contains("grilled") || text.contains("roasted")) -> true
                    else -> false
                }
            }

            val result = visible
                .map { food ->
                    if (retrievalQuery == q) food else food.copy(searchText = food.searchText + " " + q)
                }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<NativeFood> { canonicalQueryRank(it, if (retrievalQuery == q) q else retrievalQuery) }
                        .thenBy { if (it.id.startsWith("core:")) 0 else 1 }
                        .thenByDescending { it.micronutrients.size }
                        .thenBy { it.name.length }
                )
                .take(requested)

            synchronized(localSearchCacheLock) { localSearchCache[cacheKey] = result }
            result
        }

    private fun canonicalQueryRank(food: NativeFood, normalizedQuery: String): Int {
        val name = normalize(food.name)
        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        return when {
            name == normalizedQuery -> 0
            name.startsWith(normalizedQuery) -> 1
            queryTokens.isNotEmpty() && queryTokens.all(name::contains) -> 2
            queryTokens.isNotEmpty() && queryTokens.all { token -> normalize(food.searchText).contains(token) } -> 3
            else -> 4
        }
    }

    private fun seedCanonicalLocalVariants(db: SQLiteDatabase) {
        fun findUsda(vararg patterns: String): NativeFood? {
            val where = patterns.joinToString(" AND ") { "normalized_name LIKE ?" }
            val args = patterns.map { "%" + normalize(it) + "%" }.toTypedArray()
            return db.rawQuery(
                """
                SELECT id, name, country, kcal, protein, carbs, fat, fibre, sugar,
                       protein_known, carbs_known, fat_known, fibre_known, sugar_known,
                       unit, source, search_text, brand, micronutrients_json,
                       saturated_fat, saturated_fat_known, sodium_mg, sodium_known,
                       salt_g, salt_known, source_record_id,
                       plant_food, plant_food_kind, plant_diversity_key,
                       plant_diversity_eligible, food_tags_json, taxonomy_version,
                       preparation_state, serving_quantity, serving_unit, serving_label
                FROM food_reference
                WHERE source LIKE 'USDA%' AND $where
                ORDER BY
                    CASE WHEN source LIKE 'USDA Foundation Foods%' THEN 0
                         WHEN source LIKE 'USDA FNDDS%' THEN 1
                         ELSE 2 END,
                    micronutrient_count DESC,
                    length(name)
                LIMIT 1
                """.trimIndent(),
                args
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val source = cursor.getString(15)
                val sourceId = cursor.getString(0)
                NativeFood(
                    id = sourceId,
                    name = cursor.getString(1),
                    originalName = cursor.getString(1),
                    country = cursor.getString(2),
                    kcal = cursor.getDouble(3),
                    protein = cursor.getDouble(4),
                    carbs = cursor.getDouble(5),
                    carbohydrateDefinition = CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE,
                    fat = cursor.getDouble(6),
                    fibre = cursor.getDouble(7),
                    sugar = cursor.getDouble(8),
                    proteinKnown = cursor.getInt(9) != 0,
                    carbsKnown = cursor.getInt(10) != 0,
                    fatKnown = cursor.getInt(11) != 0,
                    fibreKnown = cursor.getInt(12) != 0,
                    sugarKnown = cursor.getInt(13) != 0,
                    unit = cursor.getString(14),
                    source = source,
                    searchText = cursor.getString(16),
                    brand = cursor.getString(17),
                    micronutrients = decodeMicros(cursor.getString(18)),
                    saturatedFat = cursor.getDouble(19),
                    saturatedFatKnown = cursor.getInt(20) != 0,
                    sodiumMg = cursor.getDouble(21),
                    sodiumKnown = cursor.getInt(22) != 0,
                    salt = cursor.getDouble(23),
                    saltKnown = cursor.getInt(24) != 0,
                    sourceRecordId = cursor.getString(25).ifBlank { sourceId.removePrefix("usda:") },
                    isPlantFood = cursor.getInt(26) != 0,
                    plantFoodKind = cursor.getString(27)
                        .let { raw -> runCatching { PlantFoodKind.valueOf(raw) }.getOrDefault(PlantFoodKind.NONE) },
                    plantDiversityKey = cursor.getString(28),
                    plantDiversityEligible = cursor.getInt(29) != 0,
                    foodTags = decodeFoodTags(cursor.getString(30)),
                    foodTaxonomyVersion = cursor.getInt(31),
                    preparationState = cursor.getString(32)
                        .let { raw -> runCatching { FoodPreparationState.valueOf(raw) }.getOrDefault(FoodPreparationState.UNSPECIFIED) },
                    servingQuantity = if (cursor.isNull(33)) null else cursor.getDouble(33),
                    servingQuantityUnit = cursor.getString(34)
                        .takeIf(String::isNotBlank)
                        ?.let { raw -> runCatching { FoodUnit.valueOf(raw) }.getOrNull() },
                    servingLabel = cursor.getString(35),
                    sourceType = when {
                        source.startsWith("USDA Foundation Foods") -> FoodDataSourceType.USDA_FOUNDATION
                        source.startsWith("USDA FNDDS") -> FoodDataSourceType.USDA_FNDDS
                        source.startsWith("USDA SR Legacy") -> FoodDataSourceType.USDA_SR_LEGACY
                        else -> FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE
                    },
                    verificationState = FoodVerificationState.SOURCE_VALIDATED,
                    confidence = FoodDataConfidence.HIGH
                )
            }
        }

        fun saveVariant(
            base: NativeFood,
            id: String,
            name: String,
            searchText: String,
            servingGrams: Double?,
            servingLabel: String,
            preparation: FoodPreparationState
        ) {
            val variant = base.copy(
                id = "core:" + id,
                name = name,
                originalName = base.originalName.ifBlank { base.name },
                source = "Project Superhuman core · derived from " + base.source,
                searchText = searchText + " " + base.searchText,
                brand = "",
                sourceType = FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE,
                sourceRecordId = base.sourceRecordId,
                verificationState = FoodVerificationState.SOURCE_VALIDATED,
                confidence = FoodDataConfidence.HIGH,
                preparationState = preparation,
                servingQuantity = servingGrams,
                servingQuantityUnit = servingGrams?.let { FoodUnit.G },
                servingLabel = servingLabel
            )
            insertFood(db, variant, replace = true)
        }

        data class EggSize(val id: String, val label: String, val grams: Double)
        val eggSizes = listOf(
            EggSize("small", "Small", 38.0),
            EggSize("medium", "Medium", 44.0),
            EggSize("large", "Large", 50.0),
            EggSize("extra_large", "Extra-large", 56.0),
            EggSize("jumbo", "Jumbo", 63.0)
        )

        data class EggPrep(
            val id: String,
            val display: String,
            val patterns: List<String>,
            val prep: FoodPreparationState
        )
        val eggPreps = listOf(
            EggPrep("boiled", "Boiled egg", listOf("egg", "hard boiled"), FoodPreparationState.BOILED),
            EggPrep("fried", "Fried egg", listOf("egg", "fried"), FoodPreparationState.FRIED),
            EggPrep("poached", "Poached egg", listOf("egg", "poached"), FoodPreparationState.POACHED),
            EggPrep("scrambled", "Scrambled egg", listOf("egg", "scrambled"), FoodPreparationState.COOKED)
        )

        eggPreps.forEach { prep ->
            val base = findUsda(*prep.patterns.toTypedArray()) ?: return@forEach

            // Generic preparation record.
            saveVariant(
                base = base,
                id = "egg:" + prep.id,
                name = prep.display,
                searchText = "egg eggs " + prep.id + " generic",
                servingGrams = 50.0,
                servingLabel = "1 large egg",
                preparation = prep.prep
            )

            // Size-specific records are first-class local foods.
            eggSizes.forEach { size ->
                saveVariant(
                    base = base,
                    id = "egg:" + prep.id + ":" + size.id,
                    name = prep.display + ", " + size.label,
                    searchText = "egg eggs " + prep.id + " " + size.id.replace('_', ' ') + " " + size.label.lowercase(Locale.ROOT),
                    servingGrams = size.grams,
                    servingLabel = "1 " + size.label.lowercase(Locale.ROOT) + " egg",
                    preparation = prep.prep
                )
            }
        }

        findUsda("chicken breast", "grilled")?.let { base ->
            saveVariant(
                base = base,
                id = "chicken:breast:grilled",
                name = "Chicken breast, grilled",
                searchText = "chicken breast grilled plain skinless generic",
                servingGrams = null,
                servingLabel = "",
                preparation = FoodPreparationState.GRILLED
            )
        }

        findUsda("chicken breast", "roasted")?.let { base ->
            saveVariant(
                base = base,
                id = "chicken:breast:roasted",
                name = "Chicken breast, roasted",
                searchText = "chicken breast roasted plain skinless generic",
                servingGrams = null,
                servingLabel = "",
                preparation = FoodPreparationState.ROASTED
            )
        }
    }

    private fun humanizeReferenceName(rawName: String): String {
        val raw = rawName.trim()
        val n = normalize(raw)

        if (n.startsWith("egg whole cooked hard boiled")) return "Hard-boiled egg"
        if (n.startsWith("egg whole cooked poached")) return "Poached egg"
        if (n.startsWith("egg whole cooked fried")) return "Fried egg"
        if (n.startsWith("egg whole cooked scrambled")) return "Scrambled egg"

        if (n.startsWith("chicken breast")) {
            val method = when {
                n.contains("grilled") -> "Grilled"
                n.contains("roasted") -> "Roasted"
                n.contains("baked") -> "Baked"
                n.contains("fried") -> "Fried"
                n.contains("broiled") -> "Broiled"
                else -> null
            }
            if (method != null) {
                val qualifier = when {
                    n.contains("without skin") || n.contains("skinless") -> ", skinless"
                    n.contains("with skin") -> ", with skin"
                    n.contains("coated") || n.contains("breaded") -> ", coated"
                    else -> ""
                }
                return method + " chicken breast" + qualifier
            }
        }

        return raw
            .replace(Regex("\\s+"), " ")
            .replace(Regex(",\\s*,"), ",")
            .trim()
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
            backfillPlantClassificationIfNeeded(db)
            backfillFoodTaxonomyIfNeeded(db)
            if (sourcesComplete(db)) rebuildCoreFoodsIfNeeded(db)
            seedCanonicalLocalVariants(db)
            db.execSQL("UPDATE food_reference SET core_rank = 0 WHERE id LIKE 'core:%'")
            synchronized(localSearchCacheLock) { localSearchCache.clear() }
        }
    }

    /**
     * Plant classification is derived data, so it must be refreshable independently of the much
     * larger USDA nutrition import. Bumping PlantFoodClassifier.SCHEMA_VERSION reclassifies every
     * existing local/core row once. New rows are classified automatically by insertFood().
     */
    private fun backfillPlantClassificationIfNeeded(db: SQLiteDatabase) {
        val current = db.rawQuery(
            "SELECT value FROM food_reference_meta WHERE key = ? LIMIT 1",
            arrayOf(PLANT_CLASSIFIER_META)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() ?: 0 else 0
        }
        if (current >= PlantFoodClassifier.SCHEMA_VERSION) return

        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT id, name, search_text, brand FROM food_reference",
                null
            ).use { cursor ->
                val update = db.compileStatement(
                    """
                    UPDATE food_reference
                    SET plant_food = ?, plant_food_kind = ?, plant_diversity_key = ?,
                        plant_diversity_eligible = ?
                    WHERE id = ?
                    """.trimIndent()
                )
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    val searchText = buildString {
                        append(cursor.getString(2).orEmpty())
                        append(' ')
                        append(cursor.getString(3).orEmpty())
                    }
                    val identity = PlantFoodClassifier.classify(name, searchText)
                    update.clearBindings()
                    update.bindLong(1, if (identity.isPlantFood) 1L else 0L)
                    update.bindString(2, identity.kind.name)
                    update.bindString(3, identity.diversityKey)
                    update.bindLong(4, if (identity.diversityEligible) 1L else 0L)
                    update.bindString(5, id)
                    update.executeUpdateDelete()
                }
            }
            putMeta(db, PLANT_CLASSIFIER_META, PlantFoodClassifier.SCHEMA_VERSION.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun sourcesComplete(db: SQLiteDatabase): Boolean =
        listOf(FOUNDATION_META, FNDDS_META, SR_META).all { key ->
            db.rawQuery(
                "SELECT value FROM food_reference_meta WHERE key = ? LIMIT 1",
                arrayOf(key)
            ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }
        }

    private fun rebuildCoreFoodsIfNeeded(db: SQLiteDatabase) {
        val schema = db.rawQuery(
            "SELECT value FROM food_reference_meta WHERE key = ? LIMIT 1",
            arrayOf(CORE_SCHEMA_META)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() ?: 0 else 0
        }
        val count = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%'",
            null
        )
        if (schema >= CORE_SCHEMA_VERSION && count >= CORE_FOOD_TARGET) return
        rebuildCoreFoods(db)
    }

    /**
     * Food tags are derived metadata. A taxonomy schema bump reclassifies every existing local row
     * once, so old installs and future rule improvements stay consistent without manual row edits.
     */
    private fun backfillFoodTaxonomyIfNeeded(db: SQLiteDatabase) {
        val current = db.rawQuery(
            "SELECT value FROM food_reference_meta WHERE key = ? LIMIT 1",
            arrayOf(FOOD_TAXONOMY_META)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull() ?: 0 else 0
        }
        if (current >= FoodTaxonomyClassifier.SCHEMA_VERSION) return

        db.beginTransaction()
        try {
            db.rawQuery(
                """
                SELECT id, name, search_text, brand, plant_food, plant_food_kind,
                       plant_diversity_key, plant_diversity_eligible
                FROM food_reference
                """.trimIndent(),
                null
            ).use { cursor ->
                val update = db.compileStatement(
                    """
                    UPDATE food_reference
                    SET food_tags_json = ?, taxonomy_version = ?
                    WHERE id = ?
                    """.trimIndent()
                )
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    val searchText = buildString {
                        append(cursor.getString(2).orEmpty())
                        append(' ')
                        append(cursor.getString(3).orEmpty())
                    }
                    val plant = if (cursor.getInt(4) != 0 && cursor.getString(6).isNotBlank()) {
                        PlantFoodIdentity(
                            isPlantFood = true,
                            kind = runCatching { PlantFoodKind.valueOf(cursor.getString(5)) }.getOrDefault(PlantFoodKind.NONE),
                            diversityKey = cursor.getString(6),
                            diversityEligible = cursor.getInt(7) != 0
                        )
                    } else {
                        PlantFoodClassifier.classify(name, searchText)
                    }
                    val tags = FoodTaxonomyClassifier.classify(name, searchText, plantIdentity = plant)
                    update.clearBindings()
                    update.bindString(1, encodeFoodTags(tags))
                    update.bindLong(2, FoodTaxonomyClassifier.SCHEMA_VERSION.toLong())
                    update.bindString(3, id)
                    update.executeUpdateDelete()
                }
            }
            putMeta(db, FOOD_TAXONOMY_META, FoodTaxonomyClassifier.SCHEMA_VERSION.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
                        rebuildCoreFoods(db)
                        seedCanonicalLocalVariants(db)
                        db.execSQL("UPDATE food_reference SET core_rank = 0 WHERE id LIKE 'core:%'")
                        synchronized(localSearchCacheLock) { localSearchCache.clear() }
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
        var foodCategory = ""
        val nutrients = NutrientAccumulator()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "fdcId" -> fdcId = readLong(reader)
                "description" -> description = readString(reader)
                "foodCategory", "wweiaFoodCategory" -> {
                    val category = readFoodCategory(reader)
                    if (category.isNotBlank()) foodCategory = category
                }
                "wweiaFoodCategoryDescription" -> foodCategory = readString(reader)
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
        if (protein > 100.5 || carbs > 100.5 || fat > 100.5) return null
        if (nutrients.fibre != null && nutrients.fibre!! > 100.5) return null
        if (nutrients.sugar != null && nutrients.sugar!! > 100.5) return null
        if (nutrients.saturatedFat != null && nutrients.saturatedFat!! > 100.5) return null
        if (nutrients.sodiumMg != null && nutrients.sodiumMg!! > 50_000.0) return null
        if (nutrients.micros.size < 4) return null

        val microsWithEvidence = nutrients.micros.mapValues { (_, nutrient) ->
            nutrient.copy(
                evidenceKind = NutrientEvidenceKind.REFERENCE_DATABASE,
                source = sourceLabel,
                sourceRecordId = id.toString()
            )
        }

        return NativeFood(
            id = "usda:$id",
            name = description.trim(),
            country = "US",
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            carbohydrateDefinition = CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE,
            fat = fat,
            saturatedFat = nutrients.saturatedFat ?: 0.0,
            fibre = nutrients.fibre ?: 0.0,
            sugar = nutrients.sugar ?: 0.0,
            sodiumMg = nutrients.sodiumMg ?: 0.0,
            salt = nutrients.sodiumMg?.let(NutritionIntegrity::sodiumMgToSaltG) ?: 0.0,
            proteinKnown = true,
            carbsKnown = true,
            fatKnown = true,
            saturatedFatKnown = nutrients.saturatedFat != null,
            fibreKnown = nutrients.fibre != null,
            sugarKnown = nutrients.sugar != null,
            sodiumKnown = nutrients.sodiumMg != null,
            saltKnown = nutrients.sodiumMg != null,
            unit = "100 g",
            source = "$sourceLabel · FDC $id",
            searchText = buildString {
                append(description.lowercase(Locale.ROOT))
                if (foodCategory.isNotBlank()) {
                    append(" category ")
                    append(foodCategory.lowercase(Locale.ROOT))
                }
                append(" usda fooddata central fdc ")
                append(id)
            },
            micronutrients = microsWithEvidence,
            sourceType = when {
                sourceLabel.startsWith("USDA Foundation Foods") -> FoodDataSourceType.USDA_FOUNDATION
                sourceLabel.startsWith("USDA FNDDS") -> FoodDataSourceType.USDA_FNDDS
                sourceLabel.startsWith("USDA SR Legacy") -> FoodDataSourceType.USDA_SR_LEGACY
                else -> FoodDataSourceType.PROJECT_SUPERHUMAN_REFERENCE
            },
            sourceRecordId = id.toString(),
            verificationState = FoodVerificationState.SOURCE_VALIDATED
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
        var saturatedFat: Double? = null
        var sodiumMg: Double? = null
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
                id == 1258 || name.startsWith("fatty acids, total saturated") || name == "saturated fat" ->
                    saturatedFat = convertUnit(rawAmount, unit, "g")
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
                name == "sodium, na" -> {
                    sodiumMg = convertUnit(rawAmount, unit, "mg")
                    putMicro("sodium", "Sodium", "mg", rawAmount, unit)
                }
                name == "zinc, zn" -> putMicro("zinc", "Zinc", "mg", rawAmount, unit)
                name == "vitamin a, rae" -> putMicro("vitamin_a", "Vitamin A", "µg", rawAmount, unit)
                name == "thiamin" -> putMicro("vitamin_b1", "Vitamin B1", "mg", rawAmount, unit)
                name == "riboflavin" -> putMicro("vitamin_b2", "Vitamin B2", "mg", rawAmount, unit)
                name == "niacin" -> putMicro("niacin", "Niacin (B3)", "mg", rawAmount, unit)
                name == "pantothenic acid" -> putMicro("pantothenic_acid", "Pantothenic acid (B5)", "mg", rawAmount, unit)
                name == "biotin" -> putMicro("biotin", "Biotin (B7)", "µg", rawAmount, unit)
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
                id == 1009 || name == "starch" ->
                    putMicro("starch", "Starch", "g", rawAmount, unit)
                id == 1051 || name == "water" ->
                    putMicro("water", "Water", "g", rawAmount, unit)
                id == 1018 || name.startsWith("alcohol, ethyl") ->
                    putMicro("alcohol", "Alcohol", "g", rawAmount, unit)
                id == 1253 || name == "cholesterol" ->
                    putMicro("cholesterol", "Cholesterol", "mg", rawAmount, unit)
                id == 1292 || name.startsWith("fatty acids, total monounsaturated") ->
                    putMicro("monounsaturated_fat", "Monounsaturated fat", "g", rawAmount, unit)
                id == 1293 || name.startsWith("fatty acids, total polyunsaturated") ->
                    putMicro("polyunsaturated_fat", "Polyunsaturated fat", "g", rawAmount, unit)
                id == 1257 || name.startsWith("fatty acids, total trans") ->
                    putMicro("trans_fat", "Trans fat", "g", rawAmount, unit)
                name.startsWith("fatty acids, total n-3") || name.startsWith("omega-3") ->
                    putMicro("omega_3", "Omega-3 fatty acids", "g", rawAmount, unit)
                name.startsWith("fatty acids, total n-6") || name.startsWith("omega-6") ->
                    putMicro("omega_6", "Omega-6 fatty acids", "g", rawAmount, unit)
                name == "caffeine" ->
                    putMicro("caffeine", "Caffeine", "mg", rawAmount, unit)
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
        val plantIdentity = if (food.isPlantFood && food.plantDiversityKey.isNotBlank()) {
            PlantFoodIdentity(
                isPlantFood = true,
                kind = food.plantFoodKind,
                diversityKey = food.plantDiversityKey,
                diversityEligible = food.plantDiversityEligible
            )
        } else {
            PlantFoodClassifier.classify(food.name, food.searchText, food.ingredientsText)
        }
        val foodTags = (food.foodTags + FoodTaxonomyClassifier.classify(
            food.name,
            food.searchText,
            food.ingredientsText,
            plantIdentity
        )).toSet()
        val preparationState = if (food.preparationState == FoodPreparationState.UNSPECIFIED) {
            FoodEvidenceEngine.inferPreparationState(food.name + " " + food.searchText)
        } else {
            food.preparationState
        }
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
            put("saturated_fat", food.saturatedFat)
            put("sodium_mg", food.sodiumMg)
            put("salt_g", food.salt)
            put("protein_known", if (food.proteinKnown) 1 else 0)
            put("carbs_known", if (food.carbsKnown) 1 else 0)
            put("fat_known", if (food.fatKnown) 1 else 0)
            put("fibre_known", if (food.fibreKnown) 1 else 0)
            put("sugar_known", if (food.sugarKnown) 1 else 0)
            put("saturated_fat_known", if (food.saturatedFatKnown) 1 else 0)
            put("sodium_known", if (food.sodiumKnown) 1 else 0)
            put("salt_known", if (food.saltKnown) 1 else 0)
            put("unit", food.unit)
            put("source", food.source)
            put("search_text", normalize("${food.name} ${food.searchText} ${food.brand}"))
            put("brand", food.brand)
            put("micronutrients_json", encodeMicros(food.micronutrients))
            put("micronutrient_count", food.micronutrients.size)
            put("essential_micronutrient_count", food.micronutrients.keys.count(CORE_MICRONUTRIENTS::contains))
            put(
                "unknown_micronutrients_json",
                JSONArray((CORE_MICRONUTRIENTS - food.micronutrients.keys).sorted()).toString()
            )
            put(
                "source_record_id",
                food.sourceRecordId.ifBlank { food.id.removePrefix("usda:").removePrefix("core:usda:") }
            )
            putNull("core_rank")
            put("plant_food", if (plantIdentity.isPlantFood) 1 else 0)
            put("plant_food_kind", plantIdentity.kind.name)
            put("plant_diversity_key", plantIdentity.diversityKey)
            put("plant_diversity_eligible", if (plantIdentity.diversityEligible) 1 else 0)
            put("food_tags_json", encodeFoodTags(foodTags))
            put("taxonomy_version", FoodTaxonomyClassifier.SCHEMA_VERSION)
            put("preparation_state", preparationState.name)
            food.servingQuantity?.let { put("serving_quantity", it) } ?: putNull("serving_quantity")
            put("serving_unit", food.servingQuantityUnit?.name.orEmpty())
            put("serving_label", food.servingLabel)
        }
        db.insertWithOnConflict(
            "food_reference",
            null,
            values,
            if (replace) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private data class CoreCandidate(
        val id: String,
        val name: String,
        val source: String,
        val microCount: Int,
        val essentialCount: Int,
        val tags: Set<FoodTag>
    )

    /**
     * Builds the 10,000-food Project Superhuman core library inside the local reference DB.
     * Selection is deterministic and source-backed. FNDDS contributes common consumed foods,
     * Foundation Foods contributes analytical ingredients, and SR Legacy fills preparation variants.
     */
    private fun rebuildCoreFoods(db: SQLiteDatabase) {
        val candidates = mutableListOf<CoreCandidate>()
        db.rawQuery(
            """
            SELECT id, name, source, micronutrient_count, essential_micronutrient_count, food_tags_json
            FROM food_reference
            WHERE id LIKE 'usda:%'
              AND protein_known = 1
              AND carbs_known = 1
              AND fat_known = 1
              AND fibre_known = 1
              AND sugar_known = 1
              AND saturated_fat_known = 1
              AND sodium_known = 1
              AND protein BETWEEN 0.0 AND 100.5
              AND carbs BETWEEN 0.0 AND 100.5
              AND fat BETWEEN 0.0 AND 100.5
              AND fibre BETWEEN 0.0 AND 100.5
              AND sugar BETWEEN 0.0 AND 100.5
              AND saturated_fat BETWEEN 0.0 AND 100.5
              AND sodium_mg BETWEEN 0.0 AND 50000.0
              AND sugar <= carbs + MAX(0.5, carbs * 0.05)
              AND saturated_fat <= fat + MAX(0.2, fat * 0.03)
              AND (protein + carbs + fat) <= 105.0
              AND (protein * 4.0 + carbs * 4.0 + fat * 9.0) <= kcal + MAX(35.0, kcal * 0.30)
              AND essential_micronutrient_count >= $CORE_MIN_MICRONUTRIENTS
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                candidates += CoreCandidate(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    source = cursor.getString(2),
                    microCount = cursor.getInt(3),
                    essentialCount = cursor.getInt(4),
                    tags = decodeFoodTags(cursor.getString(5))
                )
            }
        }

        fun commonnessScore(candidate: CoreCandidate): Int {
            val text = candidate.name.lowercase(Locale.ROOT)
            var score = when {
                candidate.source.startsWith("USDA Foundation Foods") -> 310
                candidate.source.startsWith("USDA FNDDS") -> 300
                candidate.source.startsWith("USDA SR Legacy") -> 260
                else -> 180
            }
            score += candidate.essentialCount * 14
            score += candidate.microCount.coerceAtMost(40)
            if (candidate.essentialCount >= CORE_PREFERRED_MICRONUTRIENTS) score += 120

            // Keep the 10k core useful for ordinary supermarket logging rather than allowing
            // obscure analytical variants to dominate purely on nutrient count.
            if (candidate.tags.any { it in setOf(
                    FoodTag.FRUIT, FoodTag.VEGETABLE, FoodTag.LEGUME, FoodTag.GRAIN,
                    FoodTag.DAIRY, FoodTag.MEAT, FoodTag.POULTRY, FoodTag.FISH,
                    FoodTag.SEAFOOD, FoodTag.BREAD, FoodTag.BAKED_GOOD,
                    FoodTag.BAKING_INGREDIENT
                ) }) score += 45

            val commonTerms = listOf(
                "egg", "milk", "yogurt", "cheese", "chicken", "turkey", "beef", "pork",
                "salmon", "tuna", "cod", "shrimp", "rice", "pasta", "bread", "oat",
                "potato", "sweet potato", "bean", "lentil", "chickpea", "pea",
                "apple", "banana", "orange", "berry", "strawberry", "blueberry",
                "tomato", "onion", "garlic", "carrot", "broccoli", "spinach",
                "cabbage", "pepper", "cucumber", "mushroom", "avocado", "arugula", "rocket",
                "courgette", "zucchini", "aubergine", "eggplant", "leek", "asparagus",
                "almond", "walnut", "peanut", "seed", "olive oil", "butter", "kefir", "skyr",
                "chicken breast", "chicken leg", "chicken thigh", "drumstick", "chicken wing",
                "raw", "boiled", "cooked", "baked", "roasted", "fried", "grilled"
            )
            score += commonTerms.count(text::contains) * 16

            val nicheTerms = listOf(
                "babyfood", "infant", "school lunch", "restaurant", "fast food",
                "imitation", "commodity", "industrial", "formulated"
            )
            score -= nicheTerms.count(text::contains) * 80
            score -= (text.length / 45) * 4
            return score
        }

        val comparator = compareByDescending<CoreCandidate> { it.essentialCount >= CORE_PREFERRED_MICRONUTRIENTS }
            .thenByDescending { commonnessScore(it) }
            .thenByDescending { it.essentialCount }
            .thenByDescending { it.microCount }
            .thenBy { it.name.length }
            .thenBy { it.name }

        val ranked = candidates.sortedWith(comparator)

        // Pin representative supermarket staples and useful preparation variants whenever USDA
        // contains a qualifying record. The remaining slots are filled by evidence quality.
        val coverageQueries = listOf(
            listOf("arugula"), listOf("spinach"), listOf("kale"), listOf("watercress"),
            listOf("romaine"), listOf("iceberg lettuce"), listOf("lettuce"), listOf("swiss chard"),
            listOf("collard"), listOf("bok choy"), listOf("cabbage"), listOf("brussels sprouts"),
            listOf("broccoli"), listOf("cauliflower"), listOf("asparagus"), listOf("artichoke"),
            listOf("leek"), listOf("fennel"), listOf("celery"), listOf("celeriac"),
            listOf("cucumber"), listOf("zucchini"), listOf("eggplant"), listOf("okra"),
            listOf("tomato"), listOf("bell pepper"), listOf("jalapeno"), listOf("chili pepper"),
            listOf("carrot"), listOf("parsnip"), listOf("beet"), listOf("radish"),
            listOf("turnip"), listOf("rutabaga"), listOf("sweet potato"), listOf("potato"),
            listOf("pumpkin"), listOf("squash"), listOf("green bean"), listOf("sweet corn"),
            listOf("onion"), listOf("shallot"), listOf("spring onion"), listOf("garlic"),
            listOf("mushroom"), listOf("avocado"),
            listOf("apple"), listOf("banana"), listOf("orange"), listOf("grapefruit"),
            listOf("lemon"), listOf("lime"), listOf("pear"), listOf("peach"), listOf("plum"),
            listOf("apricot"), listOf("cherry"), listOf("strawberry"), listOf("blueberry"),
            listOf("raspberry"), listOf("blackberry"), listOf("cranberry"), listOf("grape"),
            listOf("kiwi"), listOf("pineapple"), listOf("mango"), listOf("papaya"),
            listOf("watermelon"), listOf("cantaloupe"), listOf("honeydew"),
            listOf("pomegranate"), listOf("passion fruit"), listOf("persimmon"),
            listOf("coconut"), listOf("fig"), listOf("date"),
            listOf("lentil"), listOf("chickpea"), listOf("kidney bean"), listOf("black bean"),
            listOf("pea"), listOf("soybean"), listOf("tofu"), listOf("tempeh"),
            listOf("oat"), listOf("rice"), listOf("quinoa"), listOf("barley"), listOf("rye"),
            listOf("buckwheat"), listOf("millet"), listOf("whole wheat"), listOf("pasta"),
            listOf("almond"), listOf("walnut"), listOf("cashew"), listOf("pistachio"),
            listOf("hazelnut"), listOf("peanut"), listOf("chia"), listOf("flax"),
            listOf("sesame"), listOf("sunflower seed"), listOf("pumpkin seed"),
            listOf("milk"), listOf("buttermilk"), listOf("yogurt"), listOf("kefir"),
            listOf("cheddar"), listOf("mozzarella"), listOf("parmesan"), listOf("cottage cheese"),
            listOf("feta"), listOf("brie"), listOf("gouda"), listOf("ricotta"), listOf("butter"),
            listOf("egg", "hard boiled"), listOf("egg", "poached"), listOf("egg", "fried"),
            listOf("chicken", "breast"), listOf("chicken", "breast", "grilled"),
            listOf("chicken", "breast", "roasted"), listOf("chicken", "leg"),
            listOf("chicken", "leg", "fried"), listOf("chicken", "thigh"),
            listOf("chicken", "drumstick"), listOf("chicken", "wing"),
            listOf("turkey", "breast"), listOf("beef", "ground"), listOf("beef", "sirloin"),
            listOf("beef", "rib"), listOf("beef", "steak"), listOf("beef", "roast"),
            listOf("pork", "chop"), listOf("pork", "loin"), listOf("pork", "belly"),
            listOf("pork", "ribs"), listOf("lamb", "leg"), listOf("lamb", "chop"),
            listOf("salmon"), listOf("tuna"), listOf("cod"), listOf("haddock"),
            listOf("mackerel"), listOf("sardine"), listOf("shrimp"), listOf("prawn"),
            listOf("bread"), listOf("sourdough"), listOf("whole wheat", "bread"),
            listOf("flour", "wheat"), listOf("flour", "whole wheat"), listOf("flour", "rye"),
            listOf("flour", "oat"), listOf("flour", "rice"), listOf("flour", "corn"),
            listOf("cornstarch"), listOf("cocoa powder"), listOf("baking powder"),
            listOf("baking soda"), listOf("yeast"), listOf("sugar"),
            listOf("olive oil"), listOf("canola oil"), listOf("sunflower oil"),
            listOf("cake"), listOf("muffin"), listOf("croissant"), listOf("cookie"),
            listOf("cracker"), listOf("pancake"), listOf("waffle")
        )
        val pinned = coverageQueries.mapNotNull { terms ->
            ranked.firstOrNull { candidate ->
                val text = candidate.name.lowercase(Locale.ROOT)
                terms.all(text::contains)
            }
        }.distinctBy { it.id }
        val selected = (pinned + ranked).distinctBy { it.id }.take(CORE_FOOD_TARGET)

        // Rebuild generated Project Superhuman snapshots deterministically.
        // Hand-curated core records (egg sizes, canonical chicken, etc.) use other core: prefixes.
        db.delete("food_reference", "id LIKE 'core:usda:%'", null)
        db.execSQL("UPDATE food_reference SET core_rank = NULL WHERE id NOT LIKE 'core:%'")

        selected.forEachIndexed { index, candidate ->
            val source = db.rawQuery(
                """
                SELECT name, country, kcal, protein, carbs, fat, fibre, sugar,
                       saturated_fat, sodium_mg,
                       protein_known, carbs_known, fat_known, fibre_known, sugar_known,
                       saturated_fat_known, sodium_known,
                       unit, source, search_text, brand, micronutrients_json,
                       micronutrient_count, essential_micronutrient_count,
                       salt_g, salt_known, unknown_micronutrients_json, source_record_id,
                       plant_food, plant_food_kind, plant_diversity_key,
                       plant_diversity_eligible, food_tags_json, taxonomy_version,
                       preparation_state, serving_quantity, serving_unit, serving_label
                FROM food_reference
                WHERE id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(candidate.id)
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else ContentValues().apply {
                    val sourceName = cursor.getString(18)
                    val originalRecordId = cursor.getString(27).ifBlank { candidate.id.removePrefix("usda:") }
                    put("id", "core:" + candidate.id)
                    put("name", humanizeReferenceName(cursor.getString(0)))
                    put("normalized_name", normalize(humanizeReferenceName(cursor.getString(0))))
                    put("country", cursor.getString(1))
                    put("kcal", cursor.getDouble(2))
                    put("protein", cursor.getDouble(3))
                    put("carbs", cursor.getDouble(4))
                    put("fat", cursor.getDouble(5))
                    put("fibre", cursor.getDouble(6))
                    put("sugar", cursor.getDouble(7))
                    put("saturated_fat", cursor.getDouble(8))
                    put("sodium_mg", cursor.getDouble(9))
                    put("protein_known", cursor.getInt(10))
                    put("carbs_known", cursor.getInt(11))
                    put("fat_known", cursor.getInt(12))
                    put("fibre_known", cursor.getInt(13))
                    put("sugar_known", cursor.getInt(14))
                    put("saturated_fat_known", cursor.getInt(15))
                    put("sodium_known", cursor.getInt(16))
                    put("unit", cursor.getString(17))
                    put("source", "Project Superhuman core · " + sourceName)
                    put(
                        "search_text",
                        normalize(cursor.getString(19) + " project superhuman core local offline")
                    )
                    put("brand", cursor.getString(20))
                    put("micronutrients_json", cursor.getString(21))
                    put("micronutrient_count", cursor.getInt(22))
                    put("essential_micronutrient_count", cursor.getInt(23))
                    put("salt_g", cursor.getDouble(24))
                    put("salt_known", cursor.getInt(25))
                    put("unknown_micronutrients_json", cursor.getString(26))
                    put("source_record_id", originalRecordId)
                    put("core_rank", index + 1)
                    put("plant_food", cursor.getInt(28))
                    put("plant_food_kind", cursor.getString(29))
                    put("plant_diversity_key", cursor.getString(30))
                    put("plant_diversity_eligible", cursor.getInt(31))
                    put("food_tags_json", cursor.getString(32))
                    put("taxonomy_version", cursor.getInt(33))
                    put("preparation_state", cursor.getString(34))
                    if (cursor.isNull(35)) putNull("serving_quantity") else put("serving_quantity", cursor.getDouble(35))
                    put("serving_unit", cursor.getString(36))
                    put("serving_label", cursor.getString(37))
                }
            }
            if (source != null) {
                db.insertWithOnConflict(
                    "food_reference",
                    null,
                    source,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }

        // Hand-curated Project Superhuman variants should outrank generated snapshots.
        db.execSQL("UPDATE food_reference SET core_rank = 0 WHERE id LIKE 'core:%' AND id NOT LIKE 'core:usda:%'")

        val materializedCount = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%'",
            null
        ).toInt()
        val strictCount = selected.count { it.essentialCount >= CORE_PREFERRED_MICRONUTRIENTS }
        val taggedCount = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%' AND food_tags_json <> '[]'",
            null
        ).toInt()
        val otherOnlyCount = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%' AND food_tags_json = '[\"OTHER\"]'",
            null
        ).toInt()
        val plantCount = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%' AND plant_food = 1",
            null
        ).toInt()

        putMeta(db, "project_superhuman_core_food_count", materializedCount.toString())
        putMeta(db, "project_superhuman_core_food_strict_count", strictCount.toString())
        putMeta(db, "project_superhuman_core_food_tagged_count", taggedCount.toString())
        putMeta(db, "project_superhuman_core_food_other_only_count", otherOnlyCount.toString())
        putMeta(db, "project_superhuman_core_food_plant_count", plantCount.toString())
        putMeta(
            db,
            "project_superhuman_core_food_plant_diversity_eligible_count",
            DatabaseUtils.longForQuery(
                db,
                "SELECT COUNT(*) FROM food_reference WHERE id LIKE 'core:usda:%' AND plant_diversity_eligible = 1",
                null
            ).toString()
        )
        putMeta(db, CORE_SCHEMA_META, CORE_SCHEMA_VERSION.toString())
        putMeta(db, "project_superhuman_core_food_storage", "materialized-local-snapshot")
        putMeta(db, "project_superhuman_core_food_min_essential", CORE_MIN_MICRONUTRIENTS.toString())
        putMeta(db, "project_superhuman_core_food_preferred_essential", CORE_PREFERRED_MICRONUTRIENTS.toString())
        putMeta(db, "project_superhuman_core_food_taxonomy_schema", FoodTaxonomyClassifier.SCHEMA_VERSION.toString())
        putMeta(db, "project_superhuman_core_food_essential_total", CORE_MICRONUTRIENTS.size.toString())
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

    private fun encodeFoodTags(tags: Set<FoodTag>): String =
        JSONArray(tags.map { it.name }.sorted()).toString()

    private fun decodeFoodTags(raw: String?): Set<FoodTag> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    runCatching { FoodTag.valueOf(array.optString(i)) }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun encodeMicros(micros: Map<String, NativeNutrient>): String {
        val root = JSONObject()
        micros.forEach { (id, nutrient) ->
            root.put(id, JSONObject().apply {
                put("label", nutrient.label)
                put("value", nutrient.valuePer100)
                put("unit", nutrient.unit)
                put("evidenceKind", nutrient.evidenceKind.name)
                put("source", nutrient.source)
                put("sourceRecordId", nutrient.sourceRecordId)
                put("derivedFrom", nutrient.derivedFrom)
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
                            unit = item.optString("unit").ifBlank { "mg" },
                            evidenceKind = item.optString("evidenceKind")
                                .let { raw -> runCatching { NutrientEvidenceKind.valueOf(raw) }.getOrDefault(NutrientEvidenceKind.UNSPECIFIED) },
                            source = item.optString("source"),
                            sourceRecordId = item.optString("sourceRecordId"),
                            derivedFrom = item.optString("derivedFrom")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun readFoodCategory(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> readString(reader)
        JsonToken.BEGIN_OBJECT -> {
            var description = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "description", "wweiaFoodCategoryDescription", "name" -> {
                        val value = readString(reader)
                        if (value.isNotBlank()) description = value
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            description
        }
        JsonToken.NULL -> {
            reader.nextNull()
            ""
        }
        else -> {
            reader.skipValue()
            ""
        }
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

internal class LargeFoodDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "superhuman_large_food_reference.db",
    null,
    13
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
                saturated_fat REAL NOT NULL DEFAULT 0,
                sodium_mg REAL NOT NULL DEFAULT 0,
                salt_g REAL NOT NULL DEFAULT 0,
                protein_known INTEGER NOT NULL,
                carbs_known INTEGER NOT NULL,
                fat_known INTEGER NOT NULL,
                fibre_known INTEGER NOT NULL,
                sugar_known INTEGER NOT NULL,
                saturated_fat_known INTEGER NOT NULL DEFAULT 0,
                sodium_known INTEGER NOT NULL DEFAULT 0,
                salt_known INTEGER NOT NULL DEFAULT 0,
                unit TEXT NOT NULL,
                source TEXT NOT NULL,
                search_text TEXT NOT NULL,
                brand TEXT NOT NULL,
                micronutrients_json TEXT NOT NULL,
                micronutrient_count INTEGER NOT NULL,
                essential_micronutrient_count INTEGER NOT NULL DEFAULT 0,
                unknown_micronutrients_json TEXT NOT NULL DEFAULT '[]',
                source_record_id TEXT NOT NULL DEFAULT '',
                core_rank INTEGER,
                plant_food INTEGER NOT NULL DEFAULT 0,
                plant_food_kind TEXT NOT NULL DEFAULT 'NONE',
                plant_diversity_key TEXT NOT NULL DEFAULT '',
                plant_diversity_eligible INTEGER NOT NULL DEFAULT 0,
                food_tags_json TEXT NOT NULL DEFAULT '[]',
                taxonomy_version INTEGER NOT NULL DEFAULT 1,
                preparation_state TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                serving_quantity REAL,
                serving_unit TEXT NOT NULL DEFAULT '',
                serving_label TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX food_reference_name_idx ON food_reference(normalized_name)")
        db.execSQL("CREATE INDEX food_reference_micro_idx ON food_reference(micronutrient_count DESC)")
        db.execSQL("CREATE INDEX food_reference_core_idx ON food_reference(core_rank)")
        db.execSQL("CREATE INDEX food_reference_plant_idx ON food_reference(plant_food, plant_diversity_key)")
        db.execSQL("CREATE INDEX food_reference_taxonomy_idx ON food_reference(taxonomy_version)")
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
        if (oldVersion >= 10) {
            // Preserve downloaded authoritative rows; only derived metadata is migrated in-place.
            if (oldVersion < 11) {
                db.execSQL("ALTER TABLE food_reference ADD COLUMN food_tags_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE food_reference ADD COLUMN taxonomy_version INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS food_reference_taxonomy_idx ON food_reference(taxonomy_version)")
            }
            if (oldVersion < 12) {
                db.execSQL("ALTER TABLE food_reference ADD COLUMN plant_diversity_eligible INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 13) {
                db.execSQL("ALTER TABLE food_reference ADD COLUMN preparation_state TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                db.execSQL("ALTER TABLE food_reference ADD COLUMN serving_quantity REAL")
                db.execSQL("ALTER TABLE food_reference ADD COLUMN serving_unit TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE food_reference ADD COLUMN serving_label TEXT NOT NULL DEFAULT ''")
            }
            db.delete("food_reference_meta", "key = ?", arrayOf("plant_classifier_schema"))
            db.delete("food_reference_meta", "key = ?", arrayOf("food_taxonomy_schema"))
            db.delete("food_reference_meta", "key = ?", arrayOf("project_superhuman_core_food_schema"))
            return
        }

        // Older schemas predate nutrition-integrity fixes; rebuild those from authoritative sources.
        db.execSQL("DROP TABLE IF EXISTS food_reference")
        db.execSQL("DROP TABLE IF EXISTS food_reference_meta")
        onCreate(db)
    }
}
