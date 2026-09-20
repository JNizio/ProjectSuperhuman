package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.core.ModuleParityService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrudyHealthContextServiceTest {
    private val now = 2_000_000_000_000L

    @Test
    fun singleDomainIsolation() = runTest {
        val service = service(
            HealthDomain.BODY to listOf(value(HealthDomain.BODY, "body_weight_kg", 80.0)),
            HealthDomain.SLEEP to listOf(value(HealthDomain.SLEEP, "sleep_score", 88.0))
        )

        val body = service.currentState(HealthDomain.BODY)

        assertEquals(listOf(HealthDomain.BODY), body.map { it.domain }.distinct())
        assertEquals(listOf("body_weight_kg"), body.map { it.metricId })
    }

    @Test
    fun identicalMetricIdsInTwoDomainsDoNotLeak() = runTest {
        val metric = "collision"
        val service = service(
            HealthDomain.BODY to listOf(value(HealthDomain.BODY, metric, 11.0)),
            HealthDomain.EXERCISE to listOf(value(HealthDomain.EXERCISE, metric, 99.0))
        )

        val body = service.metricHistory(HealthDomain.BODY, metric)
        val exercise = service.metricHistory(HealthDomain.EXERCISE, metric)

        assertEquals(listOf(11.0), body.map { it.value })
        assertEquals(listOf(99.0), exercise.map { it.value })
        assertTrue(body.all { it.domain == HealthDomain.BODY })
        assertTrue(exercise.all { it.domain == HealthDomain.EXERCISE })
    }

    @Test
    fun emptyDomainProducesValidContext() = runTest {
        val service = service(HealthDomain.SLEEP to emptyList())

        val context = service.context(TrudyContextRequest(listOf(HealthDomain.SLEEP)))
        val sleep = context.domains.single()

        assertEquals(listOf(HealthDomain.SLEEP), context.requestedDomains)
        assertTrue(sleep.currentState.isEmpty())
        assertTrue(sleep.history.isEmpty())
        assertTrue(sleep.derivedFeatures.isEmpty())
        assertEquals(TrudyEvidenceKind.UNCERTAINTY_OR_DATA_GAP, sleep.insights.single().evidenceKind)
        assertEquals(0, sleep.dataQuality?.score)
    }

    @Test
    fun dataQualityIsPreservedOnEvidence() = runTest {
        val rows = listOf(
            value(HealthDomain.BODY, "body_weight_kg", 80.0, now - 1_000L),
            value(HealthDomain.BODY, "body_fat_pct", 18.0, now)
        )
        val service = service(HealthDomain.BODY to rows)

        val quality = service.dataQuality(HealthDomain.BODY)
        val current = service.currentState(HealthDomain.BODY)

        assertEquals(2L, quality.recordCount)
        assertEquals(2, quality.distinctMetricCount)
        assertTrue(current.all { it.dataQuality == quality })
    }

    @Test
    fun aliasBehaviorFlowsThroughExistingCanonicalization() = runTest {
        val service = service(
            HealthDomain.BODY to listOf(value(HealthDomain.BODY, "weight_kg", 81.2))
        )

        val current = service.currentState(HealthDomain.BODY).single()
        val byAlias = service.metricHistory(HealthDomain.BODY, "weight_kg").single()

        assertEquals("body_weight_kg", current.metricId)
        assertEquals("weight_kg", current.metadata["parityOriginalMetric"])
        assertEquals("body_weight_kg", byAlias.metricId)
    }

    @Test
    fun crossDomainContextContainsOnlyExplicitlyRequestedDomains() = runTest {
        val service = service(
            HealthDomain.BODY to listOf(value(HealthDomain.BODY, "body_weight_kg", 80.0)),
            HealthDomain.SLEEP to listOf(value(HealthDomain.SLEEP, "sleep_score", 88.0)),
            HealthDomain.HYDRATION to listOf(value(HealthDomain.HYDRATION, "water_intake_ml", 500.0))
        )

        val context = service.context(
            TrudyContextRequest(listOf(HealthDomain.SLEEP, HealthDomain.BODY), historyLimitPerDomain = 10)
        )

        assertEquals(listOf(HealthDomain.SLEEP, HealthDomain.BODY), context.requestedDomains)
        assertEquals(listOf(HealthDomain.SLEEP, HealthDomain.BODY), context.domains.map { it.domain })
        assertTrue(context.domains.none { it.domain == HealthDomain.HYDRATION })
    }

    @Test
    fun trudyCoreUsesSharedContractsWithoutRepositoryImplementation() = runTest {
        val ports = mapOf(HealthDomain.MINDFULNESS to FakeModulePort(HealthDomain.MINDFULNESS, emptyList()))
        val parity = ModuleParityService(
            modulePort = { domain -> ports.getValue(domain) },
            nowEpochMs = { now }
        )
        val trudy = TrudyHealthContextService(parity)

        val quality = trudy.dataQuality(HealthDomain.MINDFULNESS)

        assertEquals(HealthDomain.MINDFULNESS, quality.domain)
        assertEquals(0L, quality.recordCount)
    }

    private fun service(vararg domainRows: Pair<HealthDomain, List<HealthValue>>): TrudyHealthContextService {
        val ports = domainRows.associate { (domain, rows) -> domain to FakeModulePort(domain, rows) }
        val parity = ModuleParityService(
            modulePort = { domain -> ports.getValue(domain) },
            nowEpochMs = { now }
        )
        return TrudyHealthContextService(parity)
    }

    private fun value(
        domain: HealthDomain,
        metric: String,
        measurement: Double,
        timestamp: Long = now
    ) = HealthValue(
        domain = domain,
        metric = metric,
        value = measurement,
        unit = "unit",
        timestampEpochMs = timestamp,
        source = "test"
    )
}

private class FakeModulePort(
    override val domain: HealthDomain,
    rows: List<HealthValue>
) : ModuleDataPort {
    private val rows = rows.toMutableList()

    override suspend fun save(values: List<HealthValue>) {
        require(values.all { it.domain == domain })
        rows += values
    }

    override suspend fun latest(metric: String): HealthValue? =
        rows.filter { it.metric == metric }.maxByOrNull { it.timestampEpochMs }

    override suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> =
        rows.filter { it.metric == metric && it.timestampEpochMs in fromEpochMs..toEpochMs }
            .sortedBy { it.timestampEpochMs }

    override suspend fun page(metric: String?, limit: Int, offset: Int): List<HealthValue> =
        rows.asSequence()
            .filter { metric == null || it.metric == metric }
            .sortedByDescending { it.timestampEpochMs }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(0))
            .toList()

    override suspend fun count(): Long = rows.size.toLong()
}
