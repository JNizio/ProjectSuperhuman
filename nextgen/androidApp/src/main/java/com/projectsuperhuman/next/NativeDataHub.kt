package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.DataIngestionPipeline
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.IngestionResult
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlDataVaultGateway
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
        repository.ensureLargeHistoryIndexes()
        gateway = SqlDataVaultGateway(repository)
        ingestion = DataIngestionPipeline(gateway)
    }

    /** Preferred dependency for a module engine. The returned port is domain-scoped. */
    fun module(domain: HealthDomain): ModuleDataPort = gateway.module(domain)

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

    suspend fun saveFood(food: NativeFood, grams: Double, meal: String = "Diary") = withContext(Dispatchers.IO) {
        val factor = grams.coerceAtLeast(1.0) / 100.0
        val now = System.currentTimeMillis()
        val common = mapOf(
            "foodId" to food.id,
            "name" to food.name,
            "grams" to grams.toString(),
            "meal" to meal,
            "sourceName" to food.source,
            "barcode" to (food.barcode ?: ""),
            "carbs" to (food.carbs * factor).toString(),
            "fat" to (food.fat * factor).toString(),
            "fibre" to (food.fibre * factor).toString(),
            "sugar" to (food.sugar * factor).toString()
        )
        ingestion.ingestValues(
            listOf(
                HealthValue(HealthDomain.NUTRITION, "food_kcal", food.kcal * factor, "kcal", now, "native-nutrition", common),
                HealthValue(HealthDomain.NUTRITION, "food_protein", food.protein * factor, "g", now, "native-nutrition", common)
            )
        )
        Unit
    }
}
