package com.projectsuperhuman.next.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyntheticCardioDataGeneratorTest {
    @Test
    fun generatesCardioSessionsWithCoherentSummaryAndSensorEvidence() = runBlocking {
        val gateway = RecordingGateway()
        SyntheticDataGenerator(
            ingestion = DataIngestionPipeline(gateway),
            nowEpochMs = { 1_786_425_600_000L }
        ).generate(
            SyntheticGenerationConfig(days = 35, seed = 20260920, batchSize = 350)
        )

        val exercise = gateway.saved[HealthDomain.EXERCISE].orEmpty()
        val sessions = exercise.filter { it.metric == "cardio_session" }
        val hrSamples = exercise.filter { it.metric == "cardio_hr_sample_bpm" }
        val rrSamples = exercise.filter { it.metric == "cardio_rr_interval_ms" }

        assertTrue(sessions.size >= 12, "Expected regular synthetic cardio sessions across five weeks")
        assertTrue(hrSamples.size >= sessions.size * 18, "Every synthetic cardio session should expose raw HR evidence")
        assertTrue(rrSamples.size >= sessions.size * 18, "Every synthetic cardio session should expose RR evidence")
        assertTrue(sessions.any { it.metadata["activityType"] == "RUNNING" })
        assertTrue(sessions.any { it.metadata["activityType"] == "CYCLING" })

        sessions.forEach { session ->
            val sessionId = session.metadata["sessionId"]
            assertNotNull(sessionId)
            assertTrue(sessionId.isNotBlank())
            assertEquals("true", session.metadata["synthetic"])
            assertNotNull(session.metadata["activityType"])
            assertNotNull(session.metadata["workoutType"])
            assertNotNull(session.metadata["avgHeartRate"])
            assertNotNull(session.metadata["maxHeartRate"])
            assertNotNull(session.metadata["distanceKm"])

            val duration = session.metadata.getValue("durationSeconds").toInt()
            val zoneSeconds = (1..5).sumOf { zone ->
                session.metadata["zone${zone}Seconds"]?.toIntOrNull() ?: 0
            }
            assertEquals(duration, zoneSeconds, "Synthetic HR-zone time should cover the full session")

            val linkedHr = hrSamples.count { it.metadata["sessionId"] == sessionId }
            val linkedRr = rrSamples.count { it.metadata["sessionId"] == sessionId }
            assertEquals(18, linkedHr, "Each synthetic session should have a stable lightweight HR stream")
            assertEquals(18, linkedRr, "Each synthetic session should have a stable lightweight RR stream")

            when (session.metadata["activityType"]) {
                "RUNNING", "WALKING" -> assertNotNull(session.metadata["avgPaceSecPerKm"])
                "CYCLING" -> assertNotNull(session.metadata["avgSpeedKmh"])
            }
        }
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

            override suspend fun between(
                metric: String,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = emptyList()

            override suspend fun page(
                metric: String?,
                limit: Int,
                offset: Int
            ): List<HealthValue> = emptyList()

            override suspend fun count(): Long = saved[domain]?.size?.toLong() ?: 0L
        }

        override val interpretation: InterpretationDataPort = object : InterpretationDataPort {
            override suspend fun latest(domain: HealthDomain, metric: String): HealthValue? = null

            override suspend fun between(
                domain: HealthDomain,
                metric: String,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = emptyList()

            override suspend fun domainBetween(
                domain: HealthDomain,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = emptyList()

            override suspend fun dailyAggregates(
                domain: HealthDomain,
                metric: String,
                fromDayEpoch: Long,
                toDayEpoch: Long
            ): List<DailyAggregatePoint> = emptyList()
        }
    }
}
