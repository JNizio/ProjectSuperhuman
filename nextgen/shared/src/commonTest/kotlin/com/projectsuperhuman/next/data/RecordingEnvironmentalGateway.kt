package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.DailyAggregatePoint
import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.InterpretationDataPort
import com.projectsuperhuman.next.core.ModuleDataPort

internal class RecordingEnvironmentalGateway : DataVaultGateway {
    val rows = mutableListOf<HealthValue>()

    override fun module(domain: HealthDomain) = object : ModuleDataPort {
        override val domain = domain
        override suspend fun save(values: List<HealthValue>) { rows += values }
        override suspend fun latest(metric: String) = rowsFor(domain, metric).maxByOrNull { it.timestampEpochMs }
        override suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long) =
            rowsFor(domain, metric).filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
        override suspend fun page(metric: String?, limit: Int, offset: Int) = rows
            .filter { it.domain == domain && (metric == null || it.metric == metric) }
            .sortedByDescending { it.timestampEpochMs }
            .drop(offset)
            .take(limit)
        override suspend fun count() = rows.count { it.domain == domain }.toLong()
    }

    override val interpretation = object : InterpretationDataPort {
        override suspend fun latest(domain: HealthDomain, metric: String) =
            rowsFor(domain, metric).maxByOrNull { it.timestampEpochMs }
        override suspend fun between(domain: HealthDomain, metric: String, fromEpochMs: Long, toEpochMs: Long) =
            rowsFor(domain, metric).filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
        override suspend fun domainBetween(domain: HealthDomain, fromEpochMs: Long, toEpochMs: Long) =
            rows.filter { it.domain == domain && it.timestampEpochMs in fromEpochMs..toEpochMs }
        override suspend fun dailyAggregates(
            domain: HealthDomain,
            metric: String,
            fromDayEpoch: Long,
            toDayEpoch: Long
        ): List<DailyAggregatePoint> = emptyList()
    }

    private fun rowsFor(domain: HealthDomain, metric: String) =
        rows.filter { it.domain == domain && it.metric == metric }
}
