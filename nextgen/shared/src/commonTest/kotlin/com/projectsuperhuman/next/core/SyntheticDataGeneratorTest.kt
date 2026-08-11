package com.projectsuperhuman.next.core

import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticDataGeneratorTest {
    @Test
    fun generatesTaggedCrossModuleHistoryThroughIngestionPipeline() = runBlocking {
        val gateway = RecordingGateway()
        val generator = SyntheticDataGenerator(
            ingestion = DataIngestionPipeline(gateway),
            nowEpochMs = { 1_786_425_600_000L }
        )

        val result = generator.generate(SyntheticGenerationConfig(days = 60, seed = 42, batchSize = 250))
        val values = gateway.saved.values.flatten()

        assertEquals(result.generated, values.size)
        assertEquals(result.generated, result.accepted)
        assertEquals(0, result.rejected)
        assertTrue(values.isNotEmpty())
        assertTrue(values.all { it.source == SYNTHETIC_DATA_SOURCE })
        assertTrue(values.all { it.metadata["synthetic"] == "true" })
        assertTrue(values.all { it.metadata["ingestionPipeline"] == "ingestion-v1" })
        assertTrue(values.all { !it.metadata["sourceRecordId"].isNullOrBlank() })
        assertTrue(values.any { it.metric == "body_weight_kg" })
        assertTrue(values.any { it.metric == "sleep_score" })
        assertTrue(values.any { it.metric == "food_kcal" })
        assertTrue(values.any { it.metric == "steps" })

        val expectedDomains = setOf(
            HealthDomain.SLEEP,
            HealthDomain.NUTRITION,
            HealthDomain.BODY,
            HealthDomain.EXERCISE,
            HealthDomain.MINDFULNESS,
            HealthDomain.HYDRATION,
            HealthDomain.CLINICAL
        )
        assertTrue(expectedDomains.all { it in gateway.saved.keys })
    }

    @Test
    fun scenarioContainsUsefulCorrelationsAndSparseClinicalData() = runBlocking {
        val gateway = RecordingGateway()
        SyntheticDataGenerator(DataIngestionPipeline(gateway)) { 1_786_425_600_000L }
            .generate(SyntheticGenerationConfig(days = 120, seed = 7, batchSize = 400))

        val exercise = gateway.saved.getValue(HealthDomain.EXERCISE)
        val steps = exercise.filter { it.metric == "steps" }.sortedBy { it.timestampEpochMs }.map { it.value }
        val activeCalories = exercise.filter { it.metric == "active_calories_kcal" }.sortedBy { it.timestampEpochMs }.map { it.value }
        assertEquals(steps.size, activeCalories.size)
        assertTrue(pearson(steps, activeCalories) > 0.55, "Steps and active calories should be positively correlated")

        val mindfulness = gateway.saved.getValue(HealthDomain.MINDFULNESS)
        val before = mindfulness.filter { it.metric == "stress_before" }.map { it.value }
        val after = mindfulness.filter { it.metric == "stress_after" }.map { it.value }
        assertEquals(before.size, after.size)
        assertTrue(before.zip(after).map { (a, b) -> a - b }.average() > 0.7, "Synthetic mindfulness should usually reduce same-session stress")

        val clinical = gateway.saved.getValue(HealthDomain.CLINICAL)
        assertTrue(clinical.size in 12..28, "Clinical snapshots should stay sparse rather than being generated daily")
    }

    private fun pearson(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size && a.size >= 2)
        val meanA = a.average()
        val meanB = b.average()
        var numerator = 0.0
        var sumA = 0.0
        var sumB = 0.0
        a.indices.forEach { i ->
            val da = a[i] - meanA
            val db = b[i] - meanB
            numerator += da * db
            sumA += da * da
            sumB += db * db
        }
        return numerator / sqrt(sumA * sumB)
    }

    private class RecordingGateway : DataVaultGateway {
        val saved = mutableMapOf<HealthDomain, MutableList<HealthValue>>()

        override fun module(domain: HealthDomain): ModuleDataPort = object : ModuleDataPort {
            override val domain: HealthDomain = domain

            override suspend fun save(values: List<HealthValue>) {
                require(values.all { it.domain == domain })
                saved.getOrPut(domain) { mutableListOf() }.addAll(values)
            }

            override suspend fun latest(metric: String): HealthValue? = null
            override suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = emptyList()
            override suspend fun page(metric: String?, limit: Int, offset: Int): List<HealthValue> = emptyList()
            override suspend fun count(): Long = saved[domain]?.size?.toLong() ?: 0L
        }

        override val interpretation: InterpretationDataPort = object : InterpretationDataPort {
            override suspend fun latest(domain: HealthDomain, metric: String): HealthValue? = null
            override suspend fun between(domain: HealthDomain, metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = emptyList()
            override suspend fun domainBetween(domain: HealthDomain, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> = emptyList()
            override suspend fun dailyAggregates(
                domain: HealthDomain,
                metric: String,
                fromDayEpoch: Long,
                toDayEpoch: Long
            ): List<DailyAggregatePoint> = emptyList()
        }
    }
}
