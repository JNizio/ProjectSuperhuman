package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.ModuleParitySnapshot

/**
 * Small domain-scoped read facade for Android modules.
 *
 * Normal UI/module code should prefer this over metric-only NativeDataHub compatibility helpers.
 * It cannot accidentally cross into another HealthDomain and exposes no SQL/repository details.
 */
internal class NativeDomainData private constructor(
    val domain: HealthDomain
) {
    suspend fun latest(metric: String): HealthValue? = NativeDataHub.latest(domain, metric)

    /** Latest stored value for every metric in this domain. */
    suspend fun latestState(): List<HealthValue> = NativeDataHub.latestForDomain(domain)

    suspend fun between(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = NativeDataHub.between(domain, metric, fromEpochMs, toEpochMs)

    suspend fun boundedBetween(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long,
        limit: Int
    ): List<HealthValue> = NativeDataHub.boundedBetween(domain, metric, fromEpochMs, toEpochMs, limit)

    suspend fun history(
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = NativeDataHub.pageForDomain(domain, limit, offset)

    suspend fun metricHistory(
        metric: String,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = NativeDataHub.pageForMetric(domain, metric, limit, offset)

    /**
     * Complete domain history without an application-wide archive read.
     * Used only where preserving existing semantics genuinely requires all rows in one domain.
     */
    suspend fun allHistory(pageSize: Int = 500): List<HealthValue> {
        require(pageSize > 0)
        val out = mutableListOf<HealthValue>()
        var offset = 0
        while (true) {
            val page = history(limit = pageSize, offset = offset)
            out += page
            if (page.size < pageSize) break
            offset += page.size
        }
        return out
    }

    suspend fun count(): Long = NativeDataHub.storedValueCount(domain)

    suspend fun parity(
        historyLimit: Int = 250,
        historyOffset: Int = 0
    ): ModuleParitySnapshot = NativeModuleParity.snapshot(domain, historyLimit, historyOffset)

    companion object {
        fun forDomain(domain: HealthDomain): NativeDomainData = NativeDomainData(domain)
    }
}
