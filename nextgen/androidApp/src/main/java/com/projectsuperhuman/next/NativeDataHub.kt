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
    suspend fun saveFood(food: NativeFood, grams: Double, meal: String = "Snack") = withContext(Dispatchers.IO) {
        val safeGrams = grams.coerceIn(1.0, 5_000.0)
        val factor = safeGrams / 100.0
        val now = System.currentTimeMillis()
        val foodHash = abs(food.id.hashCode().toLong())
        val entryId = "nutrition-$now-$foodHash-${(safeGrams * 10.0).roundToInt()}"
        val protein = food.protein * factor
        val carbs = food.carbs * factor
        val fat = food.fat * factor
        val fibre = food.fibre * factor
        val sugar = food.sugar * factor

        val common = mapOf(
            "diaryEntryId" to entryId,
            "foodId" to food.id,
            "name" to food.name,
            "grams" to safeGrams.toString(),
            "meal" to meal,
            "sourceName" to food.source,
            "barcode" to (food.barcode ?: ""),
            "brand" to food.brand,
            "quantity" to food.quantity,
            "servingSize" to food.servingSize,
            "protein" to protein.toString(),
            "carbs" to carbs.toString(),
            "fat" to fat.toString(),
            "fibre" to fibre.toString(),
            "sugar" to sugar.toString(),
            "micronutrientCount" to food.micronutrients.size.toString()
        )

        fun row(metric: String, value: Double, unit: String, extra: Map<String, String> = emptyMap()): HealthValue =
            HealthValue(
                domain = HealthDomain.NUTRITION,
                metric = metric,
                value = value,
                unit = unit,
                timestampEpochMs = now,
                source = "native-nutrition",
                metadata = common + extra + ("sourceRecordId" to "nutrition:$entryId:$metric")
            )

        val values = buildList {
            add(row("food_kcal", food.kcal * factor, "kcal"))
            add(row("food_protein", protein, "g"))
            add(row("food_carbs", carbs, "g"))
            add(row("food_fat", fat, "g"))
            add(row("food_fibre", fibre, "g"))
            add(row("food_sugar", sugar, "g"))

            food.micronutrients.values.forEach { nutrient ->
                val suffix = when (nutrient.unit) {
                    "µg", "μg", "mcg" -> "ug"
                    else -> nutrient.unit.lowercase().replace("%", "pct")
                }
                val metricId = nutrient.id.lowercase().replace('-', '_').replace(' ', '_')
                add(
                    row(
                        metric = "food_${metricId}_$suffix",
                        value = nutrient.valuePer100 * factor,
                        unit = nutrient.unit,
                        extra = mapOf(
                            "nutrientId" to nutrient.id,
                            "nutrientLabel" to nutrient.label,
                            "per100" to nutrient.valuePer100.toString()
                        )
                    )
                )
            }
        }

        ingestion.ingestValues(values)
        Unit
    }
}
