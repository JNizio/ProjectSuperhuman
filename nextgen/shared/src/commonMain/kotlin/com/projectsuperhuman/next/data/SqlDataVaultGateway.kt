package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.InterpretationDataPort
import com.projectsuperhuman.next.core.ModuleDataPort

/**
 * SQLDelight implementation of the stable Data Vault boundary.
 *
 * Modules are handed a domain-scoped [ModuleDataPort]. They never need to know
 * which SQL tables, indexes or database technology sit underneath the Vault.
 * This makes storage replaceable and prevents accidental cross-module reads.
 */
class SqlDataVaultGateway(
    private val repository: SqlHealthRepository
) : DataVaultGateway {

    private val modulePorts: Map<HealthDomain, ModuleDataPort> =
        HealthDomain.entries.associateWith { domain -> SqlModuleDataPort(domain, repository) }

    override fun module(domain: HealthDomain): ModuleDataPort =
        modulePorts.getValue(domain)

    override val interpretation: InterpretationDataPort = SqlInterpretationDataPort(repository)
}

private class SqlModuleDataPort(
    override val domain: HealthDomain,
    private val repository: SqlHealthRepository
) : ModuleDataPort {

    override suspend fun save(values: List<HealthValue>) {
        if (values.isEmpty()) return
        require(values.all { it.domain == domain }) {
            "A $domain ModuleDataPort may only save $domain values"
        }
        repository.save(values)
    }

    override suspend fun latest(metric: String): HealthValue? =
        repository.latest(domain, metric)

    override suspend fun between(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> {
        require(fromEpochMs <= toEpochMs) { "fromEpochMs must be <= toEpochMs" }
        return repository.between(domain, metric, fromEpochMs, toEpochMs)
    }

    override suspend fun page(
        metric: String?,
        limit: Int,
        offset: Int
    ): List<HealthValue> {
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE).toLong()
        val safeOffset = offset.coerceAtLeast(0).toLong()
        return if (metric == null) {
            repository.pageForDomain(domain, safeLimit, safeOffset)
        } else {
            repository.pageForMetric(domain, metric, safeLimit, safeOffset)
        }
    }

    override suspend fun count(): Long = repository.count(domain)

    private companion object {
        const val MAX_PAGE_SIZE = 5_000
    }
}

private class SqlInterpretationDataPort(
    private val repository: SqlHealthRepository
) : InterpretationDataPort {

    override suspend fun latest(domain: HealthDomain, metric: String): HealthValue? =
        repository.latest(domain, metric)

    override suspend fun between(
        domain: HealthDomain,
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> {
        require(fromEpochMs <= toEpochMs) { "fromEpochMs must be <= toEpochMs" }
        return repository.between(domain, metric, fromEpochMs, toEpochMs)
    }

    override suspend fun domainBetween(
        domain: HealthDomain,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> {
        require(fromEpochMs <= toEpochMs) { "fromEpochMs must be <= toEpochMs" }
        return repository.domainBetween(domain, fromEpochMs, toEpochMs)
    }
}
