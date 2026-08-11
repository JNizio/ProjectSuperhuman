package com.projectsuperhuman.next.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngestionPipelineTest {
    @Test
    fun convertsKnownUnitsAndRoutesToOwningDomain() = runBlocking {
        val gateway = RecordingGateway()
        val pipeline = DataIngestionPipeline(gateway)
        val result = pipeline.ingestValues(
            listOf(
                HealthValue(
                    domain = HealthDomain.SLEEP,
                    metric = "sleep_total_minutes",
                    value = 8.0,
                    unit = "hours",
                    timestampEpochMs = 1_720_000_000_000,
                    source = "test"
                )
            )
        )

        assertEquals(1, result.accepted)
        assertEquals(0, result.rejected)
        val saved = gateway.saved.getValue(HealthDomain.SLEEP).single()
        assertEquals(480.0, saved.value)
        assertEquals("min", saved.unit)
        assertEquals("8.0", saved.metadata["originalValue"])
        assertEquals("hours", saved.metadata["originalUnit"])
    }

    @Test
    fun rejectsInvalidKnownUnitsAndBounds() = runBlocking {
        val gateway = RecordingGateway()
        val pipeline = DataIngestionPipeline(gateway)
        val result = pipeline.ingestValues(
            listOf(
                HealthValue(HealthDomain.SLEEP, "sleep_score", 140.0, "score", 1, "test"),
                HealthValue(HealthDomain.SLEEP, "sleep_total_minutes", 7.0, "kg", 2, "test")
            )
        )

        assertEquals(0, result.accepted)
        assertEquals(2, result.rejected)
        assertTrue(gateway.saved.values.flatten().isEmpty())
    }

    @Test
    fun deduplicatesSameSourceRecordWithinBatch() = runBlocking {
        val gateway = RecordingGateway()
        val pipeline = DataIngestionPipeline(gateway)
        val value = HealthValue(
            HealthDomain.SLEEP,
            "sleep_score",
            80.0,
            "score",
            100,
            "health-connect",
            mapOf("sourceRecordId" to "night-1:score")
        )
        val result = pipeline.ingestValues(listOf(value, value.copy(value = 81.0)))

        assertEquals(1, result.accepted)
        assertEquals(1, result.deduplicated)
        assertEquals(80.0, gateway.saved.getValue(HealthDomain.SLEEP).single().value)
    }

    @Test
    fun preservesUnknownClinicalMetricsWithoutRelabelling() = runBlocking {
        val gateway = RecordingGateway()
        val pipeline = DataIngestionPipeline(gateway)
        val result = pipeline.ingestValues(
            listOf(
                HealthValue(
                    domain = HealthDomain.CLINICAL,
                    metric = "future_nhs_marker",
                    value = 4.2,
                    unit = "weird-unit",
                    timestampEpochMs = 500,
                    source = "nhs-ocr"
                )
            )
        )

        assertEquals(1, result.accepted)
        val saved = gateway.saved.getValue(HealthDomain.CLINICAL).single()
        assertEquals("future_nhs_marker", saved.metric)
        assertEquals("weird-unit", saved.unit)
        assertEquals("unregistered", saved.metadata["metricRegistryStatus"])
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
        }
    }
}
