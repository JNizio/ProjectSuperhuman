package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase

/** Android entry point into the shared SQLDelight data layer. */
internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
    }

    suspend fun latest(metric: String): HealthValue? = repository.latest(metric)

    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> =
        repository.between(metric, fromEpochMs, toEpochMs)

    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> =
        repository.latestForDomain(domain)

    suspend fun saveValues(values: List<HealthValue>) {
        repository.save(values)
    }

    fun allValues(): List<HealthValue> = repository.allValues()

    fun clearValues() {
        repository.clearValues()
    }

    suspend fun restoreValues(values: List<HealthValue>, replace: Boolean = false) {
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
    ) {
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

    suspend fun saveFood(food: NativeFood, grams: Double, meal: String = "Diary") {
        val factor = (grams.coerceAtLeast(1.0) / 100.0)
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

    fun storedValueCount(): Long = repository.count()
}
