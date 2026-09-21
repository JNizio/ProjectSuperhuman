package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.DataIngestionPipeline
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthQueryEngine
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.IngestionResult
import com.projectsuperhuman.next.core.InsightsEngine
import com.projectsuperhuman.next.core.InterpretationEngine
import com.projectsuperhuman.next.core.InterventionEngine
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.core.SYNTHETIC_DATA_SOURCE
import com.projectsuperhuman.next.core.SyntheticDataGenerator
import com.projectsuperhuman.next.core.SyntheticGenerationConfig
import com.projectsuperhuman.next.core.SyntheticGenerationResult
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlDataVaultGateway
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android access point into the Project Superhuman Data Vault.
 *
 * Normal module traffic routes through [SqlDataVaultGateway] and the shared
 * ingestion pipeline. The repository is retained only for administrative /
 * compatibility operations such as full backup/export, restore and diagnostics.
 */
internal data class DataVaultDiagnostics(
    val totalRecords: Long,
    val recordsByDomain: Map<HealthDomain, Long>,
    val latestTimestampByDomain: Map<HealthDomain, Long?>
)

internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository
    private lateinit var gateway: SqlDataVaultGateway
    private lateinit var ingestion: DataIngestionPipeline
    private lateinit var syntheticGenerator: SyntheticDataGenerator
    private lateinit var queries: HealthQueryEngine
    private lateinit var interpretations: InterpretationEngine
    private lateinit var interventions: InterventionEngine
    private lateinit var insights: InsightsEngine

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
        repository.ensureLargeHistoryIndexes()
        gateway = SqlDataVaultGateway(repository)
        ingestion = DataIngestionPipeline(gateway)
        syntheticGenerator = SyntheticDataGenerator(ingestion) { System.currentTimeMillis() }
        queries = HealthQueryEngine(gateway.interpretation)
        interpretations = InterpretationEngine(queries) { System.currentTimeMillis() }
        interventions = InterventionEngine(queries) { System.currentTimeMillis() }
        insights = InsightsEngine()
    }

    /** Preferred dependency for a module engine. The returned port is domain-scoped. */
    fun module(domain: HealthDomain): ModuleDataPort = gateway.module(domain)

    /** Read-only Step 4 API for Interpretation/Insights code. */
    fun queryEngine(): HealthQueryEngine = queries

    /** Step 5 derived-evidence engines. None of these receive direct SQL access. */
    fun interpretationEngine(): InterpretationEngine = interpretations
    fun interventionEngine(): InterventionEngine = interventions
    fun insightsEngine(): InsightsEngine = insights

    /** Compatibility helper for old callers that do not yet supply a domain. */
    suspend fun latest(metric: String): HealthValue? = withContext(Dispatchers.IO) {
        repository.latest(metric)
    }

    suspend fun latest(domain: HealthDomain, metric: String): HealthValue? = withContext(Dispatchers.IO) {
        gateway.module(domain).latest(metric)
    }

    /** Compatibility helper for old callers that do not yet supply a domain. */
    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.between(metric, fromEpochMs, toEpochMs)
    }

    suspend fun between(
        domain: HealthDomain,
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        gateway.module(domain).between(metric, fromEpochMs, toEpochMs)
    }

    /** Indexed newest-first range read with a hard row cap for UI/history hot paths. */
    suspend fun boundedBetween(
        domain: HealthDomain,
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long,
        limit: Int
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        require(limit > 0) { "limit must be positive" }
        repository.boundedBetween(domain, metric, fromEpochMs, toEpochMs, limit.toLong())
    }

    suspend fun domainBetween(
        domain: HealthDomain,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        gateway.interpretation.domainBetween(domain, fromEpochMs, toEpochMs)
    }

    suspend fun pageForDomain(
        domain: HealthDomain,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        gateway.module(domain).page(metric = null, limit = limit, offset = offset)
    }

    suspend fun pageForMetric(
        domain: HealthDomain,
        metric: String,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        gateway.module(domain).page(metric = metric, limit = limit, offset = offset)
    }

    suspend fun valueBySourceRecordId(source: String, sourceRecordId: String): HealthValue? =
        withContext(Dispatchers.IO) {
            repository.valueBySourceRecordId(source, sourceRecordId)
        }

    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.latestForDomain(domain)
    }

    /**
     * Standardised write entry point for native modules and importers.
     * Validation, canonical units, in-batch deduplication and domain routing happen here.
     */
    suspend fun ingestValues(values: List<HealthValue>): IngestionResult = withContext(Dispatchers.IO) {
        ingestion.ingestValues(values)
    }

    /** Legacy call shape retained while modules migrate; now uses the same safe pipeline. */
    suspend fun saveValues(values: List<HealthValue>) = withContext(Dispatchers.IO) {
        ingestion.ingestValues(values)
        Unit
    }

    /**
     * Developer-only test history. Generation deliberately uses [DataIngestionPipeline], so
     * synthetic traffic exercises validation, normalisation, deduplication, module routing and
     * incremental daily aggregate maintenance exactly like normal app data.
     */
    suspend fun generateSyntheticTestData(days: Int): SyntheticGenerationResult = withContext(Dispatchers.IO) {
        syntheticGenerator.generate(SyntheticGenerationConfig(days = days))
    }

    /** Cheap indexed count for Settings diagnostics; no synthetic rows are materialised. */
    suspend fun syntheticTestDataCount(): Long = withContext(Dispatchers.IO) {
        repository.countSource(SYNTHETIC_DATA_SOURCE)
    }

    /**
     * Administrative source-scoped cleanup. The exact synthetic source is deleted and affected
     * aggregates are rebuilt; genuine rows from every other source are left untouched.
     */
    suspend fun clearSyntheticTestData(): Long = withContext(Dispatchers.IO) {
        repository.deleteSource(SYNTHETIC_DATA_SOURCE)
    }

    /** Compatibility/export helpers. Do not use for normal screens or analytics. */
    fun allValues(): List<HealthValue> = repository.allValues()
    fun clearValues() = repository.clearValues()
    fun storedValueCount(): Long = repository.count()

    suspend fun allValuesAsync(): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.allValues()
    }

    suspend fun clearValuesAsync() = withContext(Dispatchers.IO) {
        repository.clearValues()
    }

    /** Indexed single-row delete; never rewrites the archive. */
    suspend fun deleteValue(target: HealthValue) = withContext(Dispatchers.IO) {
        repository.delete(target)
    }

    /**
     * Small batch delete for one logical diary item. This intentionally stays targeted rather than
     * loading/re-writing the nutrition archive; each row still goes through repository aggregate repair.
     */
    suspend fun deleteValues(targets: List<HealthValue>) = withContext(Dispatchers.IO) {
        targets.forEach(repository::delete)
    }

    /**
     * Duplicate one stored nutrition diary item without depending on the original catalogue source.
     * This preserves the exact ingredient/product evidence, including micronutrients and provenance.
     */
    suspend fun duplicateNutritionEntry(
        rows: List<HealthValue>,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): IngestionResult = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext IngestionResult(accepted = 0, rejected = 0, deduplicated = 0, issues = emptyList())
        val originalId = rows.firstNotNullOfOrNull { it.metadata["diaryEntryId"] }.orEmpty()
        val foodId = rows.firstNotNullOfOrNull { it.metadata["foodId"] }.orEmpty()
        val newEntryId = "nutrition-$timestampEpochMs-${abs((originalId + foodId).hashCode().toLong())}"
        val copied = rows.mapIndexed { index, row ->
            val metricRecordId = "nutrition:$newEntryId:${row.metric}:$index"
            row.copy(
                timestampEpochMs = timestampEpochMs + index,
                source = if (row.source == SYNTHETIC_DATA_SOURCE) "native-nutrition-repeat" else row.source,
                metadata = row.metadata +
                    ("diaryEntryId" to newEntryId) +
                    ("sourceRecordId" to metricRecordId) +
                    ("repeatedFromEntryId" to originalId)
            )
        }
        ingestion.ingestValues(copied)
    }

    /**
     * Rename a grouped meal while preserving each ingredient entry and its nutrition evidence.
     * Used by the diary's inline meal-title editor.
     */
    suspend fun renameNutritionMealGroup(
        rows: List<HealthValue>,
        newName: String
    ): IngestionResult = withContext(Dispatchers.IO) {
        val safeName = newName.trim().take(80)
        if (rows.isEmpty() || safeName.isBlank()) {
            return@withContext IngestionResult(accepted = 0, rejected = 0, deduplicated = 0, issues = emptyList())
        }
        rows.forEach(repository::delete)
        val renamed = rows.map { row ->
            row.copy(metadata = row.metadata + ("mealGroupName" to safeName))
        }
        ingestion.ingestValues(renamed)
    }

    suspend fun clearDomain(domain: HealthDomain) = withContext(Dispatchers.IO) {
        repository.clearDomain(domain)
    }

    suspend fun storedValueCountAsync(): Long = withContext(Dispatchers.IO) {
        repository.count()
    }

    suspend fun storedValueCount(domain: HealthDomain): Long = withContext(Dispatchers.IO) {
        gateway.module(domain).count()
    }

    /** Cheap indexed health check; never materialises stored HealthValue rows. */
    suspend fun diagnostics(): DataVaultDiagnostics = withContext(Dispatchers.IO) {
        val counts = HealthDomain.entries.associateWith { gateway.module(it).count() }
        val latest = HealthDomain.entries.associateWith { repository.latestTimestamp(it) }
        DataVaultDiagnostics(
            totalRecords = repository.count(),
            recordsByDomain = counts,
            latestTimestampByDomain = latest
        )
    }

    /** Restore deliberately bypasses normalisation so a backup is restored byte-for-byte semantically. */
    suspend fun restoreValues(values: List<HealthValue>, replace: Boolean = false) = withContext(Dispatchers.IO) {
        if (replace) repository.clearValues()
        repository.save(values)
    }

    suspend fun saveMetric(
        domain: HealthDomain,
        metric: String,
        value: Double,
        unit: String,
        source: String = "native-compose",
        metadata: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        ingestion.ingestValues(
            listOf(
                HealthValue(
                    domain = domain,
                    metric = metric,
                    value = value,
                    unit = unit,
                    timestampEpochMs = System.currentTimeMillis(),
                    source = source,
                    metadata = metadata
                )
            )
        )
        Unit
    }

    /**
     * Persist one food diary item as a linked set of nutrition observations.
     *
     * All rows share `diaryEntryId`, so a meal can be removed cleanly. Macros and any verified
     * Open Food Facts micronutrients become first-class time-series metrics ready for future
     * correlations (for example magnesium vs. sleep), instead of being trapped in UI metadata.
     */
    /** Legacy mass-entry overload kept for older callers. */
    suspend fun saveFood(
        food: NativeFood,
        grams: Double,
        meal: String = "Snack",
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        saveFood(
            food = food,
            amount = grams,
            inputUnit = FoodUnit.G,
            meal = meal,
            timestampEpochMs = timestampEpochMs
        )
    }

    suspend fun saveFood(
        food: NativeFood,
        amount: Double,
        inputUnit: FoodUnit,
        meal: String = "Snack",
        timestampEpochMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val evidenceFood = FoodEvidenceEngine.enrich(food)
        val safeAmount = amount.coerceIn(0.001, 100_000.0)
        val conversion = FoodUnitSystem.convert(evidenceFood, safeAmount, inputUnit)
            ?: return@withContext

        val now = timestampEpochMs
        val foodHash = abs(evidenceFood.id.hashCode().toLong())
        val entryId = "nutrition-" + now + "-" + foodHash + "-" +
            (safeAmount * 100.0).roundToInt() + "-" + inputUnit.name.lowercase()
        val factor = conversion.factor
        val protein = evidenceFood.protein * factor
        val carbs = evidenceFood.carbs * factor
        val fat = evidenceFood.fat * factor
        val saturatedFat = evidenceFood.saturatedFat * factor
        val fibre = evidenceFood.fibre * factor
        val sugar = evidenceFood.sugar * factor
        val salt = evidenceFood.salt * factor
        val sodiumMg = evidenceFood.sodiumMg * factor

        val common = buildMap {
            put("diaryEntryId", entryId)
            put("foodId", evidenceFood.id)
            put("name", evidenceFood.name)
            put("amount", safeAmount.toString())
            put("amountUnit", inputUnit.symbol)
            put("basisAmount", conversion.basisAmount.toString())
            put("basisUnit", conversion.basisUnit.symbol)
            conversion.grams?.let { put("grams", it.toString()) }
            conversion.millilitres?.let { put("millilitres", it.toString()) }
            put("meal", meal)
            put("sourceName", evidenceFood.source)
            put("barcode", evidenceFood.barcode ?: "")
            put("brand", evidenceFood.brand)
            put("quantity", evidenceFood.quantity)
            put("servingSize", evidenceFood.servingSize)
            put("kcalKnown", evidenceFood.kcalKnown.toString())
            put("protein", if (evidenceFood.proteinKnown) protein.toString() else "")
            put("carbs", if (evidenceFood.carbsKnown) carbs.toString() else "")
            put("carbohydrateDefinition", evidenceFood.carbohydrateDefinition.name)
            put("fat", if (evidenceFood.fatKnown) fat.toString() else "")
            put("saturatedFat", if (evidenceFood.saturatedFatKnown) saturatedFat.toString() else "")
            put("proteinKnown", evidenceFood.proteinKnown.toString())
            put("carbsKnown", evidenceFood.carbsKnown.toString())
            put("fatKnown", evidenceFood.fatKnown.toString())
            put("saturatedFatKnown", evidenceFood.saturatedFatKnown.toString())
            put("fibre", if (evidenceFood.fibreKnown) fibre.toString() else "")
            put("sugar", if (evidenceFood.sugarKnown) sugar.toString() else "")
            put("salt", if (evidenceFood.saltKnown) salt.toString() else "")
            put("sodiumMg", if (evidenceFood.sodiumKnown) sodiumMg.toString() else "")
            put("fibreKnown", evidenceFood.fibreKnown.toString())
            put("sugarKnown", evidenceFood.sugarKnown.toString())
            put("saltKnown", evidenceFood.saltKnown.toString())
            put("sodiumKnown", evidenceFood.sodiumKnown.toString())
            put("micronutrientCount", evidenceFood.micronutrients.size.toString())
            put("nutritionIntegrityWarning", evidenceFood.nutritionIntegrityWarning.orEmpty())
            put("nutritionApproximate", evidenceFood.nutritionApproximate.toString())
            put("unitSystemVersion", "4")
            putAll(FoodEvidenceEngine.snapshotMetadata(evidenceFood, safeAmount, inputUnit, conversion))
        }

        fun row(
            metric: String,
            value: Double,
            unit: String,
            extra: Map<String, String> = emptyMap()
        ): HealthValue = HealthValue(
            domain = HealthDomain.NUTRITION,
            metric = metric,
            value = value,
            unit = unit,
            timestampEpochMs = now,
            source = "native-nutrition",
            metadata = common + extra + ("sourceRecordId" to ("nutrition:" + entryId + ":" + metric))
        )

        val values = buildList {
            // Always persist a neutral entry anchor. Unknown nutrition remains absent rather than 0.
            add(row("food_entry", 1.0, "count"))
            if (evidenceFood.kcalKnown) {
                add(row("food_kcal", evidenceFood.kcal * factor, "kcal", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "energy_kcal")))
            }
            if (evidenceFood.proteinKnown) {
                add(row("food_protein", protein, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "protein")))
            }
            if (evidenceFood.carbsKnown) {
                add(row("food_carbs", carbs, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "carbohydrate")))
            }
            if (evidenceFood.fatKnown) {
                add(row("food_fat", fat, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "fat")))
            }
            if (evidenceFood.saturatedFatKnown) {
                add(row("food_saturated_fat", saturatedFat, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "saturated_fat")))
            }
            if (evidenceFood.fibreKnown) {
                add(row("food_fibre", fibre, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "fibre")))
            }
            if (evidenceFood.sugarKnown) {
                add(row("food_sugar", sugar, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "sugars")))
            }
            if (evidenceFood.saltKnown) {
                add(row("food_salt", salt, "g", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "salt")))
            }
            if (evidenceFood.sodiumKnown) {
                add(row("food_sodium", sodiumMg, "mg", FoodEvidenceEngine.nutrientMetadata(evidenceFood, "sodium")))
            }

            evidenceFood.micronutrients.values
                .filterNot { it.id.equals("sodium", ignoreCase = true) && evidenceFood.sodiumKnown }
                .forEach { nutrient ->
                    val suffix = when (nutrient.unit) {
                        "µg", "μg", "mcg" -> "ug"
                        else -> nutrient.unit.lowercase().replace("%", "pct")
                    }
                    val metricId = nutrient.id.lowercase().replace('-', '_').replace(' ', '_')
                    val evidenceKind = nutrient.evidenceKind.takeUnless { it == NutrientEvidenceKind.UNSPECIFIED }
                        ?: FoodEvidenceEngine.evidenceKindForSource(
                            evidenceFood.sourceType,
                            evidenceFood.nutritionApproximate
                        )
                    add(
                        row(
                            metric = "food_" + metricId + "_" + suffix,
                            value = nutrient.valuePer100 * factor,
                            unit = nutrient.unit,
                            extra = mapOf(
                                "nutrientId" to nutrient.id,
                                "nutrientLabel" to nutrient.label,
                                "perBasis" to nutrient.valuePer100.toString(),
                                "perBasisAmount" to evidenceFood.basisAmount.toString(),
                                "perBasisUnit" to evidenceFood.basisUnit.symbol,
                                "nutrientEvidenceKind" to evidenceKind.name,
                                "nutrientEvidenceSource" to nutrient.source.ifBlank { evidenceFood.source },
                                "nutrientSourceRecordId" to nutrient.sourceRecordId.ifBlank { evidenceFood.sourceRecordId },
                                "nutrientDerivedFrom" to nutrient.derivedFrom
                            )
                        )
                    )
                }
        }

        ingestion.ingestValues(values)
        Unit
    }

}
