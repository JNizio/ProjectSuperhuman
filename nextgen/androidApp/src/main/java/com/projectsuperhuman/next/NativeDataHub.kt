package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android gateway into the Project Superhuman Data Vault.
 *
 * New module code should prefer domain-scoped and paged methods below. Full-
 * archive reads are kept only for backup/export and migration compatibility.
 */
internal data class DataVaultDiagnostics(
    val totalRecords: Long,
    val recordsByDomain: Map<HealthDomain, Long>,
    val latestTimestampByDomain: Map<HealthDomain, Long?>
)

internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
        // Safe, non-destructive index upgrade for existing installs.
        repository.ensureLargeHistoryIndexes()
    }

    suspend fun latest(metric: String): HealthValue? = withContext(Dispatchers.IO) {
        repository.latest(metric)
    }

    suspend fun latest(domain: HealthDomain, metric: String): HealthValue? = withContext(Dispatchers.IO) {
        repository.latest(domain, metric)
    }

    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.between(metric, fromEpochMs, toEpochMs)
    }

    suspend fun between(
        domain: HealthDomain,
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.between(domain, metric, fromEpochMs, toEpochMs)
    }

    suspend fun domainBetween(
        domain: HealthDomain,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.domainBetween(domain, fromEpochMs, toEpochMs)
    }

    suspend fun pageForDomain(
        domain: HealthDomain,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.pageForDomain(
            domain,
            limit.coerceIn(1, 5_000).toLong(),
            offset.coerceAtLeast(0).toLong()
        )
    }

    suspend fun pageForMetric(
        domain: HealthDomain,
        metric: String,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.pageForMetric(
            domain,
            metric,
            limit.coerceIn(1, 5_000).toLong(),
            offset.coerceAtLeast(0).toLong()
        )
    }

    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.latestForDomain(domain)
    }

    suspend fun saveValues(values: List<HealthValue>) = withContext(Dispatchers.IO) {
        repository.save(values)
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
        repository.count(domain)
    }

    /** Cheap indexed health check; never materialises stored HealthValue rows. */
    suspend fun diagnostics(): DataVaultDiagnostics = withContext(Dispatchers.IO) {
        val counts = HealthDomain.entries.associateWith { repository.count(it) }
        val latest = HealthDomain.entries.associateWith { repository.latestTimestamp(it) }
        DataVaultDiagnostics(
            totalRecords = repository.count(),
            recordsByDomain = counts,
            latestTimestampByDomain = latest
        )
    }

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
        repository.save(
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
        repository.save(
            listOf(
                HealthValue(HealthDomain.NUTRITION, "food_kcal", food.kcal * factor, "kcal", now, "native-nutrition", common),
                HealthValue(HealthDomain.NUTRITION, "food_protein", food.protein * factor, "g", now, "native-nutrition", common)
            )
        )
    }
}
