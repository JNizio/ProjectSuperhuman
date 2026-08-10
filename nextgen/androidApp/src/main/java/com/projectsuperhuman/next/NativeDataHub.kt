package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android entry point into the shared SQLDelight data layer. */
internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
    }

    suspend fun latest(metric: String): HealthValue? = withContext(Dispatchers.IO) {
        repository.latest(metric)
    }

    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.between(metric, fromEpochMs, toEpochMs)
    }

    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.latestForDomain(domain)
    }

    suspend fun saveValues(values: List<HealthValue>) = withContext(Dispatchers.IO) {
        repository.save(values)
    }

    /** Compatibility helpers kept for older call sites. Prefer the suspend variants below in UI code. */
    fun allValues(): List<HealthValue> = repository.allValues()
    fun clearValues() = repository.clearValues()
    fun storedValueCount(): Long = repository.count()

    suspend fun allValuesAsync(): List<HealthValue> = withContext(Dispatchers.IO) {
        repository.allValues()
    }

    suspend fun clearValuesAsync() = withContext(Dispatchers.IO) {
        repository.clearValues()
    }

    suspend fun deleteValue(target: HealthValue) = withContext(Dispatchers.IO) {
        val remaining = repository.allValues().filterNot { value ->
            value.domain == target.domain &&
                value.metric == target.metric &&
                value.timestampEpochMs == target.timestampEpochMs &&
                value.source == target.source &&
                value.value == target.value &&
                value.unit == target.unit
        }
        repository.clearValues()
        repository.save(remaining)
    }

    suspend fun storedValueCountAsync(): Long = withContext(Dispatchers.IO) {
        repository.count()
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
