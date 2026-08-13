package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.ModuleCurrentState
import com.projectsuperhuman.next.core.ModuleDataQuality
import com.projectsuperhuman.next.core.ModuleDerivedFeatures
import com.projectsuperhuman.next.core.ModuleHistory
import com.projectsuperhuman.next.core.ModuleParityInsight
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
    private val service by lazy {
        ModuleParityService(
            modulePort = { domain -> NativeDataHub.module(domain) },
            nowEpochMs = { System.currentTimeMillis() }
        )
    }

    suspend fun currentState(domain: HealthDomain): ModuleCurrentState =
        service.currentState(domain)

    suspend fun history(
        domain: HealthDomain,
        limit: Int = 250,
        offset: Int = 0
    ): ModuleHistory = service.history(domain, limit, offset)

    suspend fun derivedFeatures(domain: HealthDomain): ModuleDerivedFeatures =
        service.derivedFeatures(domain)

    suspend fun insights(domain: HealthDomain): List<ModuleParityInsight> =
        service.insights(domain)

    suspend fun dataQuality(domain: HealthDomain): ModuleDataQuality =
        service.dataQuality(domain)

    suspend fun snapshot(
        domain: HealthDomain,
        historyLimit: Int = 250,
        historyOffset: Int = 0
    ): ModuleParitySnapshot = service.snapshot(domain, historyLimit, historyOffset)

    suspend fun allModules(): Map<HealthDomain, ModuleParitySnapshot> = service.allModules()
}
