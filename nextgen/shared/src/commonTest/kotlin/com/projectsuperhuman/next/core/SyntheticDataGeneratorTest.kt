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
        assertTrue(values.any { it.metric == "cardio_session" })
        assertTrue(values.any { it.metric == "cardio_hr_sample_bpm" })
        assertTrue(values.any { it.metric == "cardio_rr_interval_ms" })

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
    fun oneYearPresetProducesCompleteDailyHistoryAcrossPrimaryModules() = runBlocking {
        val gateway = RecordingGateway()
        val result = SyntheticDataGenerator(DataIngestionPipeline(gateway)) { 1_786_425_600_000L }
            .generate(SyntheticGenerationConfig(days = 365, seed = 20260811, batchSize = 750))

        assertEquals(365, result.requestedDays)
        assertEquals(0, result.rejected)

        fun metric(domain: HealthDomain, name: String): List<HealthValue> =
            gateway.saved.getValue(domain).filter { it.metric == name }

        val sleepNights = metric(HealthDomain.SLEEP, "sleep_total_minutes")
        assertEquals(365, sleepNights.size)
        assertEquals(365, sleepNights.mapNotNull { it.metadata["nightEnd"] }.distinct().size)
        assertTrue(sleepNights.all { !it.metadata["nightStart"].isNullOrBlank() })
        assertEquals(365, metric(HealthDomain.SLEEP, "sleep_start_epoch_ms").size)
        assertEquals(365, metric(HealthDomain.SLEEP, "sleep_end_epoch_ms").size)
        assertEquals(365, metric(HealthDomain.SLEEP, "sleep_stage_timeline").size)

        val dailyBodyMetrics = listOf(
            "body_weight_kg",
            "body_impedance_ohm",
            "body_fat_pct",
            "body_fat_mass_kg",
            "body_fat_free_mass_kg",
            "body_water_pct",
            "body_water_l",
            "body_muscle_pct",
            "body_muscle_mass_kg",
            "body_skeletal_muscle_pct",
            "body_skeletal_muscle_mass_kg",
            "body_visceral_fat_estimate",
            "body_bmi",
            "body_ffmi",
            "body_fmi"
        )
        dailyBodyMetrics.forEach { name ->
            val rows = metric(HealthDomain.BODY, name)
            assertEquals(365, rows.size, "$name should cover every generated day")
            assertTrue(rows.none { it.metadata["metricRegistryStatus"] == "unregistered" }, "$name should be a registered app metric")
        }

        assertEquals(365, metric(HealthDomain.EXERCISE, "steps").size)
        assertEquals(365, metric(HealthDomain.EXERCISE, "active_calories_kcal").size)
        val cardioSessions = metric(HealthDomain.EXERCISE, "cardio_session")
        assertTrue(cardioSessions.size >= 120, "One-year synthetic history should include regular Cardio sessions")
        assertTrue(cardioSessions.all { !it.metadata["sessionId"].isNullOrBlank() })
        assertTrue(cardioSessions.all { !it.metadata["activityType"].isNullOrBlank() })
        assertTrue(cardioSessions.all { it.metadata["avgHeartRate"]?.toIntOrNull() != null })
        assertTrue(cardioSessions.count { it.metadata["avgPaceSecPerKm"] != null } >= 60)
        assertTrue(metric(HealthDomain.EXERCISE, "cardio_hr_sample_bpm").size >= cardioSessions.size * 12)
        assertTrue(metric(HealthDomain.EXERCISE, "cardio_rr_interval_ms").size >= cardioSessions.size * 12)
        assertEquals(365 * 3, metric(HealthDomain.NUTRITION, "food_kcal").size)
        assertEquals(365, metric(HealthDomain.HYDRATION, "water_total_l").size)
        assertEquals(365, metric(HealthDomain.MINDFULNESS, "mood_score").size)
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
