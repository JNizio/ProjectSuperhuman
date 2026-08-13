package com.projectsuperhuman.next.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleParityTest {
    private val now = 2_000_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun allHealthDomainsExposeTheSameFiveSurfaces() = runTest {
        val ports = HealthDomain.entries.associateWith { domain -> FakeModulePort(domain, emptyList()) }
        val service = ModuleParityService(
            modulePort = { domain -> ports.getValue(domain) },
            nowEpochMs = { now }
        )

        val all = service.allModules()

        assertEquals(HealthDomain.entries.toSet(), all.keys)
        HealthDomain.entries.forEach { domain ->
            val snapshot = all.getValue(domain)
            assertEquals(domain, snapshot.domain)
            assertEquals(domain, snapshot.currentState.domain)
            assertEquals(domain, snapshot.history.domain)
            assertEquals(domain, snapshot.derivedFeatures.domain)
            assertEquals(domain, snapshot.dataQuality.domain)
            assertTrue(snapshot.insights.all { it.domain == domain })
        }
    }

    @Test
    fun emptyDomainProducesDataGapAndZeroQuality() = runTest {
        val service = serviceFor(HealthDomain.SLEEP, emptyList())
        val snapshot = service.snapshot(HealthDomain.SLEEP)

        assertTrue(snapshot.currentState.latestByMetric.isEmpty())
        assertTrue(snapshot.history.values.isEmpty())
        assertTrue(snapshot.derivedFeatures.metrics.isEmpty())
        assertEquals(ModuleInsightKind.DATA_GAP, snapshot.insights.single().kind)
        assertEquals(0, snapshot.dataQuality.score)
        assertEquals(0L, snapshot.dataQuality.recordCount)
        assertTrue(snapshot.dataQuality.isStale)
    }

    @Test
    fun multiMetricCurrentStateAndDerivedFeaturesStayDomainScoped() = runTest {
        val rows = listOf(
            hv(HealthDomain.BODY, "body_weight_kg", 80.0, now - 3 * hour, "kg"),
            hv(HealthDomain.BODY, "body_weight_kg", 79.0, now - hour, "kg"),
            hv(HealthDomain.BODY, "body_fat_pct", 18.5, now - 2 * hour, "%"),
            hv(HealthDomain.BODY, "body_fat_pct", 18.0, now, "%")
        )
        val service = serviceFor(HealthDomain.BODY, rows)
        val snapshot = service.snapshot(HealthDomain.BODY)

        assertEquals(2, snapshot.currentState.latestByMetric.size)
        assertEquals(79.0, snapshot.currentState.latestByMetric.single { it.metric == "body_weight_kg" }.value)
        assertEquals(18.0, snapshot.currentState.latestByMetric.single { it.metric == "body_fat_pct" }.value)

        val weight = snapshot.derivedFeatures.metrics.single { it.metric == "body_weight_kg" }
        assertEquals(2, weight.sampleCount)
        assertEquals(79.5, weight.mean)
        assertEquals(-1.0, weight.change)
        assertEquals(2, snapshot.dataQuality.distinctMetricCount)
        assertFalse(snapshot.dataQuality.isStale)
    }

    @Test
    fun historyPaginationIsStableAndDescending() = runTest {
        val rows = (1..8).map { i ->
            hv(HealthDomain.HYDRATION, "water_intake_ml", i * 100.0, now - i * 1_000L, "ml")
        }
        val service = serviceFor(HealthDomain.HYDRATION, rows)

        val first = service.history(HealthDomain.HYDRATION, limit = 3, offset = 0)
        val second = service.history(HealthDomain.HYDRATION, limit = 3, offset = 3)

        assertEquals(3, first.values.size)
        assertEquals(3, second.values.size)
        assertTrue(first.values.zipWithNext().all { (a, b) -> a.timestampEpochMs >= b.timestampEpochMs })
        assertTrue(second.values.zipWithNext().all { (a, b) -> a.timestampEpochMs >= b.timestampEpochMs })
        assertTrue(first.values.none { it in second.values })
        assertEquals(0, first.offset)
        assertEquals(3, second.offset)
    }

    @Test
    fun hydrationSignedCorrectionsRemainVisibleInScopedMetricHistory() = runTest {
        val port = FakeModulePort(
            HealthDomain.HYDRATION,
            listOf(
                hv(HealthDomain.HYDRATION, "water_intake_ml", 500.0, now - 3_000L, "ml"),
                hv(HealthDomain.HYDRATION, "water_intake_ml", -200.0, now - 2_000L, "ml"),
                hv(HealthDomain.HYDRATION, "water_intake_ml", 350.0, now - 1_000L, "ml")
            )
        )

        val rows = port.between("water_intake_ml", now - 10_000L, now)

        assertEquals(listOf(500.0, -200.0, 350.0), rows.map { it.value })
        assertEquals(650.0, rows.sumOf { it.value })
        assertTrue(rows.all { it.domain == HealthDomain.HYDRATION })
    }

    @Test
    fun exerciseHistoryPreservesSetMetadataThroughScopedPort() = runTest {
        val set = hv(
            HealthDomain.EXERCISE,
            "exercise_set",
            800.0,
            now,
            "kg-reps",
            mapOf("exerciseId" to "bench-press", "reps" to "10", "loadKg" to "80")
        )
        val port = FakeModulePort(HealthDomain.EXERCISE, listOf(set))

        val history = port.between("exercise_set", now - hour, now)

        assertEquals(1, history.size)
        assertEquals("bench-press", history.single().metadata["exerciseId"])
        assertEquals("80", history.single().metadata["loadKg"])
        assertEquals(HealthDomain.EXERCISE, history.single().domain)
    }

    @Test
    fun nutritionHistoryPreservesLinkedDiaryEntryRows() = runTest {
        val common = mapOf("diaryEntryId" to "nutrition-entry-1", "foodId" to "oats", "meal" to "Breakfast")
        val port = FakeModulePort(
            HealthDomain.NUTRITION,
            listOf(
                hv(HealthDomain.NUTRITION, "food_kcal", 380.0, now, "kcal", common),
                hv(HealthDomain.NUTRITION, "food_protein", 13.0, now, "g", common)
            )
        )

        val page = port.page(metric = null, limit = 10, offset = 0)

        assertEquals(2, page.size)
        assertTrue(page.all { it.metadata["diaryEntryId"] == "nutrition-entry-1" })
        assertTrue(page.all { it.domain == HealthDomain.NUTRITION })
        assertEquals(setOf("food_kcal", "food_protein"), page.map { it.metric }.toSet())
    }

    @Test
    fun meaningfulRepeatedChangeProducesCautiousTrendInsight() = runTest {
        val rows = listOf(60.0, 62.0, 70.0, 78.0, 84.0).mapIndexed { index, value ->
            hv(HealthDomain.SLEEP, "sleep_score", value, now - (5 - index) * hour, "score")
        }
        val service = serviceFor(HealthDomain.SLEEP, rows)

        val insight = service.insights(HealthDomain.SLEEP).single()

        assertEquals(ModuleInsightKind.TREND_SIGNAL, insight.kind)
        assertTrue(insight.title.contains("higher"))
        assertEquals(listOf("sleep_score"), insight.evidenceMetricIds)
        assertTrue(insight.confidence in 0.0..1.0)
        assertTrue(insight.explanation.contains("not evidence that another factor caused it"))
    }

    @Test
    fun oldDataIsMarkedStale() = runTest {
        val rows = listOf(
            hv(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", 10.0, now - 30L * 24L * hour, "min")
        )
        val quality = serviceFor(HealthDomain.MINDFULNESS, rows).dataQuality(HealthDomain.MINDFULNESS)

        assertTrue(quality.isStale)
        assertNotNull(quality.ageHours)
        assertTrue(quality.ageHours!! > 14.0 * 24.0)
        assertTrue(quality.notes.any { it.contains("stale", ignoreCase = true) })
    }

    @Test
    fun legacyAliasesAreCanonicalizedOnReadWithoutMutatingStoredRow() = runTest {
        val legacy = hv(HealthDomain.BODY, "weight_kg", 81.2, now, "kg")
        val port = FakeModulePort(HealthDomain.BODY, listOf(legacy))
        val service = ModuleParityService(
            modulePort = { port },
            nowEpochMs = { now }
        )

        val current = service.currentState(HealthDomain.BODY).latestByMetric.single()
        val history = service.history(HealthDomain.BODY).values.single()

        assertEquals("body_weight_kg", current.metric)
        assertEquals("weight_kg", current.metadata["parityOriginalMetric"])
        assertEquals("body_weight_kg", history.metric)
        assertEquals("weight_kg", port.storedRows.single().metric)
    }

    @Test
    fun identicalMetricNamesInDifferentDomainsNeverLeakAcrossModulePorts() = runTest {
        val sharedMetric = "shared_collision_metric"
        val bodyPort = FakeModulePort(
            HealthDomain.BODY,
            listOf(hv(HealthDomain.BODY, sharedMetric, 11.0, now - hour, "unit"))
        )
        val exercisePort = FakeModulePort(
            HealthDomain.EXERCISE,
            listOf(hv(HealthDomain.EXERCISE, sharedMetric, 99.0, now, "unit"))
        )
        val ports = mapOf(
            HealthDomain.BODY to bodyPort,
            HealthDomain.EXERCISE to exercisePort
        )
        val service = ModuleParityService(
            modulePort = { domain -> ports.getValue(domain) },
            nowEpochMs = { now }
        )

        val body = service.currentState(HealthDomain.BODY)
        val exercise = service.currentState(HealthDomain.EXERCISE)

        assertEquals(11.0, body.latestByMetric.single { it.metric == sharedMetric }.value)
        assertEquals(99.0, exercise.latestByMetric.single { it.metric == sharedMetric }.value)
        assertTrue(body.latestByMetric.none { it.domain != HealthDomain.BODY })
        assertTrue(exercise.latestByMetric.none { it.domain != HealthDomain.EXERCISE })
        assertEquals(11.0, bodyPort.latest(sharedMetric)?.value)
        assertEquals(99.0, exercisePort.latest(sharedMetric)?.value)
        assertNull(bodyPort.latest("exercise_only_metric"))
    }

    private fun serviceFor(domain: HealthDomain, rows: List<HealthValue>): ModuleParityService {
        val port = FakeModulePort(domain, rows)
        return ModuleParityService(
            modulePort = { requested ->
                require(requested == domain)
                port
            },
            nowEpochMs = { now }
        )
    }

    private fun hv(
        domain: HealthDomain,
        metric: String,
        value: Double,
        timestamp: Long,
        unit: String,
        metadata: Map<String, String> = emptyMap()
    ) = HealthValue(
        domain = domain,
        metric = metric,
        value = value,
        unit = unit,
        timestampEpochMs = timestamp,
        source = "test",
        metadata = metadata
    )
}

private class FakeModulePort(
    override val domain: HealthDomain,
    rows: List<HealthValue>
) : ModuleDataPort {
    val storedRows: MutableList<HealthValue> = rows.toMutableList()

    override suspend fun save(values: List<HealthValue>) {
        require(values.all { it.domain == domain })
        storedRows += values
    }

    override suspend fun latest(metric: String): HealthValue? =
        storedRows.filter { it.metric == metric }.maxByOrNull { it.timestampEpochMs }

    override suspend fun between(
        metric: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<HealthValue> = storedRows.filter {
        it.metric == metric && it.timestampEpochMs in fromEpochMs..toEpochMs
    }.sortedBy { it.timestampEpochMs }

    override suspend fun page(metric: String?, limit: Int, offset: Int): List<HealthValue> =
        storedRows.asSequence()
            .filter { metric == null || it.metric == metric }
            .sortedByDescending { it.timestampEpochMs }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(0))
            .toList()

    override suspend fun count(): Long = storedRows.size.toLong()
}
