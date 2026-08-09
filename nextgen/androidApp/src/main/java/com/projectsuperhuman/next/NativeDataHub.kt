package com.projectsuperhuman.next

import android.content.Context
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.data.DatabaseDriverFactory
import com.projectsuperhuman.next.data.SqlHealthRepository
import com.projectsuperhuman.next.data.createSuperhumanDatabase

/**
 * Android entry point into the shared SQLDelight data layer.
 * Native Compose screens use this instead of talking to WebView/localStorage directly.
 */
internal object NativeDataHub {
    private lateinit var repository: SqlHealthRepository

    fun initialize(context: Context) {
        if (::repository.isInitialized) return
        val database = createSuperhumanDatabase(DatabaseDriverFactory(context.applicationContext))
        repository = SqlHealthRepository(database) { System.currentTimeMillis() }
    }

    suspend fun latest(metric: String): HealthValue? = repository.latest(metric)

    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue> =
        repository.latestForDomain(domain)

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

    fun storedValueCount(): Long = repository.count()
}
