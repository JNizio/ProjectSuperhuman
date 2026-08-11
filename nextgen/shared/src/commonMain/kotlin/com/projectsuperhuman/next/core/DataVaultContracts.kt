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
    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue>
    suspend fun page(metric: String? = null, limit: Int = 250, offset: Int = 0): List<HealthValue>
    suspend fun count(): Long
}

/**
 * Read-only cross-domain contract intended for the Interpretation Engine.
 *
 * Interpretation should request a bounded window or aggregates rather than
 * loading the complete raw archive. This is the key rule that keeps analysis
 * viable when the vault contains millions of observations.
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
}

/**
 * Stable write/read gateway factory. New modules should depend on this boundary
 * instead of directly depending on SQLite/SQLDelight or another module.
 */
interface DataVaultGateway {
    fun module(domain: HealthDomain): ModuleDataPort
    val interpretation: InterpretationDataPort
}
