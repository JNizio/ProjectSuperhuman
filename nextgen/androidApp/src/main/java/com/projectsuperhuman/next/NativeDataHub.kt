package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

/** Android entry point into the shared SQLDelight data layer. */
internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository

    private data class MetricRangeKey(val metric: String, val fromEpochMs: Long, val toEpochMs: Long)
    private data class DomainRangeKey(val domain: HealthDomain, val fromEpochMs: Long, val toEpochMs: Long)
    private data class RecentMetricKey(val metric: String, val limit: Int)

    private class BoundedCache<K, V>(private val maxEntries: Int) : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
    }

    private val cacheLock = Any()
    private val metricRangeCache = BoundedCache<MetricRangeKey, List<HealthValue>>(24)
    private val domainRangeCache = BoundedCache<DomainRangeKey, List<HealthValue>>(12)
    private val recentMetricCache = BoundedCache<RecentMetricKey, List<HealthValue>>(24)
    private val latestDomainCache = BoundedCache<HealthDomain, List<HealthValue>>(12)

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
    }

    private fun invalidateReadCaches() {
        synchronized(cacheLock) {
            metricRangeCache.clear()
            domainRangeCache.clear()
            recentMetricCache.clear()
            latestDomainCache.clear()
        }
    }

    suspend fun latest(metric: String): HealthValue? = withContext(Dispatchers.IO) {
        repository.latest(metric)
    }

    /**
     * Indexed metric-range read with a small bounded in-memory cache. Date/day/month screens
     * frequently ask for the same range again during recomposition or navigation, so keeping
     * only the most recent ranges avoids duplicate SQL work without retaining the full database.
     */
    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = withContext(Dispatchers.IO) {
        val key = MetricRangeKey(metric, fromEpochMs, toEpochMs)
        synchronized(cacheLock) { metricRangeCache[key] }?.let { return@withContext it }
        repository.between(metric, fromEpochMs, toEpochMs).also { result ->
            synchronized(cacheLock) { metricRangeCache[key] = result }
        }
    }

    /** Efficient domain-scoped range read for modules that need several related metrics at once. */
    suspend fun betweenForDomain(domain: HealthDomain, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = withContext(Dispatchers.IO) {
        val key = DomainRangeKey(domain, fromEpochMs, toEpochMs)
        synchronized(cacheLock) { domainRangeCache[key] }?.let { return@withContext it }
        repository.betweenForDomain(domain, fromEpochMs, toEpochMs).also { result ->
            synchronized(cacheLock) { domainRangeCache[key] = result }
        }
    }

    /** Fetch only the newest N rows for a metric instead of loading its entire history. */
    suspend fun recent(metric: String, limit: Int): List<HealthValue> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceAtLeast(1)
        val key = RecentMetricKey(metric, safeLimit)
        synchronized(cacheLock) { recentMetricCache[key] }?.let { return@withContext it }
        repository.recent(metric, safeLimit).also { result ->
            synchronized(cacheLock) { recentMetricCache[key] = result }
        }
    }

    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> = withContext(Dispatchers.IO) {
        synchronized(cacheLock) { latestDomainCache[domain] }?.let { return@withContext it }
        repository.latestForDomain(domain).also { result ->
            synchronized(cacheLock) { latestDomainCache[domain] = result }
        }
    }

    suspend fun saveValues(values: List<HealthValue>) = withContext(Dispatchers.IO) {
        repository.save(values)
        if (values.isNotEmpty()) invalidateReadCaches()
    }

    /** Compatibility helpers kept for older call sites. Prefer the suspend variants below in UI code. */
    fun allValues(): List<HealthValue> = repository.allValues()
    fun clearValues() {
        repository.clearValues()
        invalidateReadCaches()
    }
    fun storedValueCount(): Long = repository.count()

    suspend fun allValuesAsync(): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.allValues()
    }

    suspend fun clearValuesAsync() = withContext(Dispatchers.IO) {
        repository.clearValues()
        invalidateReadCaches()
    }

    suspend fun storedValueCountAsync(): Long = withContext(Dispatchers.IO) {
        repository.count()
    }

    suspend fun restoreValues(values: List<HealthValue>, replace: Boolean = false) = withContext(Dispatchers.IO) {
        if (replace) repository.clearValues()
        repository.save(values)
        invalidateReadCaches()
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
        invalidateReadCaches()
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
        invalidateReadCaches()
    }
}
