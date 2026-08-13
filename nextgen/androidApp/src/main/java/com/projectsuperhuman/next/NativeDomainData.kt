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

    suspend fun between(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = NativeDataHub.between(domain, metric, fromEpochMs, toEpochMs)

    suspend fun history(
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = NativeDataHub.pageForDomain(domain, limit, offset)

    suspend fun metricHistory(
        metric: String,
        limit: Int = 250,
        offset: Int = 0
    ): List<HealthValue> = NativeDataHub.pageForMetric(domain, metric, limit, offset)

    suspend fun count(): Long = NativeDataHub.storedValueCount(domain)

    suspend fun parity(
        historyLimit: Int = 250,
        historyOffset: Int = 0
    ): ModuleParitySnapshot = NativeModuleParity.snapshot(domain, historyLimit, historyOffset)

    companion object {
        fun forDomain(domain: HealthDomain): NativeDomainData = NativeDomainData(domain)
    }
}
