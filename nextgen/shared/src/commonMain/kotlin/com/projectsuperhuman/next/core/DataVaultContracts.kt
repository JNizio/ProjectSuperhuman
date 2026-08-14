package com.projectsuperhuman.next.core

/**
 * Read contract exposed to a single module.
 *
 * A module receives a port already scoped to its domain, so Nutrition cannot
 * accidentally become coupled to Sleep storage details and vice versa.
 */
interface ModuleDataPort {
    val domain: HealthDomain

    suspend fun save(values: List<HealthValue>)
    suspend fun latest(metric: String): HealthValue?
    /**
     * Latest row for every stored metric in this domain.
     *
     * SQL-backed ports override this with one grouped/indexed query. The default keeps existing
     * test/fake ports source-compatible while still bounding fallback work.
     */
    suspend fun latestForDomain(limit: Int = 250): List<HealthValue> =
        page(metric = null, limit = limit.coerceIn(1, 5_000), offset = 0)
            .sortedByDescending { it.timestampEpochMs }
            .distinctBy { it.metric }
    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue>
    /**
     * Metric-qualified time-window read with a hard result cap. High-frequency streams such as
     * heart rate must use this boundary instead of materialising every sample in a long window.
     */
    suspend fun boundedBetween(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long,
        limit: Int = 250
    ): List<HealthValue> = between(metric, fromEpochMs, toEpochMs)
        .takeLast(limit.coerceIn(1, 5_000))
    suspend fun page(metric: String? = null, limit: Int = 250, offset: Int = 0): List<HealthValue>
    suspend fun count(): Long
}

data class DailyAggregatePoint(
    val dayEpoch: Long,
    val domain: HealthDomain,
    val metric: String,
    val count: Long,
    val min: Double?,
    val max: Double?,
    val average: Double?,
    val sum: Double?,
    val first: Double?,
    val last: Double?
)

/**
 * Read-only cross-domain contract intended for the Interpretation Engine.
 *
 * Interpretation should request a bounded raw window or precomputed aggregates rather
 * than loading the complete archive. This is the key rule that keeps analysis viable
 * when the vault contains millions of observations.
 */
interface InterpretationDataPort {
    suspend fun latest(domain: HealthDomain, metric: String): HealthValue?
    suspend fun between(
        domain: HealthDomain,
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue>

    suspend fun domainBetween(
        domain: HealthDomain,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue>

    suspend fun dailyAggregates(
        domain: HealthDomain,
        metric: String,
        fromDayEpoch: Long,
        toDayEpoch: Long
    ): List<DailyAggregatePoint>
}

/**
 * Stable write/read gateway factory. New modules should depend on this boundary
 * instead of directly depending on SQLite/SQLDelight or another module.
 */
interface DataVaultGateway {
    fun module(domain: HealthDomain): ModuleDataPort
    val interpretation: InterpretationDataPort
}
