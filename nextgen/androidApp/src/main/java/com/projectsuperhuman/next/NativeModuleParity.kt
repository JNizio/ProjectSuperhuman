package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.DailyAggregatePoint
import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.InterpretationDataPort
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.core.ModuleParityService
import com.projectsuperhuman.next.core.ModuleParitySnapshot

/**
 * Android bridge for the shared five-surface module API.
 *
 * Existing screens and module write paths are intentionally untouched. This adapter delegates to
 * NativeDataHub, which already routes normal module traffic into the SQL Data Vault. That means
 * Clinical, Blood Pressure, Body, Sleep, Nutrition, Hydration, Exercise and Mindfulness all expose
 * the same Trudy-ready contract without introducing a second source of truth.
 */
internal object NativeModuleParity {
    private val gateway = object : DataVaultGateway {
        override fun module(domain: HealthDomain): ModuleDataPort = NativeDataHub.module(domain)

        override val interpretation: InterpretationDataPort = object : InterpretationDataPort {
            override suspend fun latest(domain: HealthDomain, metric: String): HealthValue? =
                NativeDataHub.latest(domain, metric)

            override suspend fun between(
                domain: HealthDomain,
                metric: String,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = NativeDataHub.between(domain, metric, fromEpochMs, toEpochMs)

            override suspend fun domainBetween(
                domain: HealthDomain,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = NativeDataHub.domainBetween(domain, fromEpochMs, toEpochMs)

            override suspend fun dailyAggregates(
                domain: HealthDomain,
                metric: String,
                fromDayEpoch: Long,
                toDayEpoch: Long
            ): List<DailyAggregatePoint> = NativeDataHub.queryEngine()
                .dailyAggregates(domain, metric, fromDayEpoch, toDayEpoch)
        }
    }

    private val service by lazy {
        ModuleParityService(gateway) { System.currentTimeMillis() }
    }

    suspend fun snapshot(
        domain: HealthDomain,
        historyLimit: Int = 250,
        historyOffset: Int = 0
    ): ModuleParitySnapshot = service.snapshot(domain, historyLimit, historyOffset)

    suspend fun allModules(): Map<HealthDomain, ModuleParitySnapshot> = service.allModules()
}
