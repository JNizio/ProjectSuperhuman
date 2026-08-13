package com.projectsuperhuman.next.data

import com.projectsuperhuman.next.core.CorrelationStrength
import com.projectsuperhuman.next.core.DailyAggregatePoint
import com.projectsuperhuman.next.core.DataVaultGateway
import com.projectsuperhuman.next.core.FindingDirection
import com.projectsuperhuman.next.core.FindingKind
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthQueryEngine
import com.projectsuperhuman.next.core.HealthValue
import com.projectsuperhuman.next.core.InterpretationDataPort
import com.projectsuperhuman.next.core.InterpretationEngine
import com.projectsuperhuman.next.core.MetricWindow
import com.projectsuperhuman.next.core.ModuleDataPort
import com.projectsuperhuman.next.core.TrendDirection
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmotionalDataVaultAdapterTest {
    @Test
    fun storesObservationWithProvenanceAndScaleSemantics() = runBlocking {
        val gateway = InMemoryGateway()
        val adapter = EmotionalDataVaultAdapter(gateway)

        val result = adapter.saveMetric(
            metricId = "anxiety_score",
            value = 7.0,
            unit = "0-10",
            timestampEpochMs = 1_720_000_000_000L,
            source = "manual-emotional-checkin",
            sourceEventId = "checkin-42",
            scaleMin = 0.0,
            scaleMax = 10.0,
            scaleLowMeaning = "low",
            scaleHighMeaning = "high"
        )

        assertEquals(1, result.accepted)
        val stored = assertNotNull(adapter.latest("anxiety_score"))
        assertEquals(HealthDomain.EMOTIONAL, stored.domain)
        assertEquals("anxiety_score", stored.metric)
        assertEquals(7.0, stored.value)
        assertEquals("0-10", stored.unit)
        assertEquals(1_720_000_000_000L, stored.timestampEpochMs)
        assertEquals("manual-emotional-checkin", stored.source)
        assertEquals("checkin-42", stored.metadata[EmotionalPersistenceMetadata.SOURCE_EVENT_ID])
        assertEquals("checkin-42:anxiety_score", stored.metadata[EmotionalPersistenceMetadata.SOURCE_RECORD_ID])
        assertEquals("0.0", stored.metadata[EmotionalPersistenceMetadata.SCALE_MIN])
        assertEquals("10.0", stored.metadata[EmotionalPersistenceMetadata.SCALE_MAX])
        assertEquals("low", stored.metadata[EmotionalPersistenceMetadata.SCALE_LOW_MEANING])
        assertEquals("high", stored.metadata[EmotionalPersistenceMetadata.SCALE_HIGH_MEANING])
    }

    @Test
    fun latestAndHistoryStayScopedToEmotionalDomain() = runBlocking {
        val gateway = InMemoryGateway()
        val adapter = EmotionalDataVaultAdapter(gateway)

        gateway.module(HealthDomain.MINDFULNESS).save(
            listOf(HealthValue(HealthDomain.MINDFULNESS, "mood_score", 3.0, "0-10", 100L, "legacy-mindfulness"))
        )
        adapter.saveMetric("mood_score", 6.0, "0-10", 200L, "emotional-checkin", scaleMin = 0.0, scaleMax = 10.0)
        adapter.saveMetric("mood_score", 8.0, "0-10", 300L, "emotional-checkin", scaleMin = 0.0, scaleMax = 10.0)

        assertEquals(8.0, adapter.latest("mood_score")?.value)
        assertEquals(listOf(6.0, 8.0), adapter.between("mood_score", 150L, 350L).map { it.value })
        assertEquals(2, adapter.history("mood_score").size)
        assertTrue(adapter.history().all { it.domain == HealthDomain.EMOTIONAL })
    }

    @Test
    fun genericAnalyticsCanTrendAndAssociateEmotionalEvidence() = runBlocking {
        val gateway = InMemoryGateway()
        val adapter = EmotionalDataVaultAdapter(gateway)
        val start = 1_720_000_000_000L
        val day = 86_400_000L

        repeat(5) { index ->
            val timestamp = start + index * day
            adapter.saveMetric(
                metricId = "anxiety_score",
                value = 2.0 + index,
                unit = "0-10",
                timestampEpochMs = timestamp,
                source = "emotional-checkin",
                sourceEventId = "day-$index",
                scaleMin = 0.0,
                scaleMax = 10.0,
                scaleLowMeaning = "low",
                scaleHighMeaning = "high"
            )
            gateway.module(HealthDomain.SLEEP).save(
                listOf(
                    HealthValue(
                        HealthDomain.SLEEP,
                        "sleep_score",
                        90.0 - index * 10.0,
                        "score",
                        timestamp,
                        "test-sleep"
                    )
                )
            )
        }

        val queries = HealthQueryEngine(gateway.interpretation)
        val anxietyWindow = MetricWindow(HealthDomain.EMOTIONAL, "anxiety_score", start, start + 4 * day)
        val sleepWindow = MetricWindow(HealthDomain.SLEEP, "sleep_score", start, start + 4 * day)

        val trend = queries.trend(anxietyWindow)
        assertEquals(TrendDirection.RISING, trend.direction)

        val correlation = queries.correlation(anxietyWindow, sleepWindow)
        assertEquals(5, correlation.sampleCount)
        assertEquals(CorrelationStrength.STRONG, correlation.strength)
        assertTrue((correlation.pearsonR ?: 0.0) < -0.99)

        val finding = InterpretationEngine(queries) { start + 5 * day }
            .relationship(anxietyWindow, sleepWindow)
        assertEquals(FindingKind.RELATIONSHIP, finding.kind)
        assertEquals(FindingDirection.NEGATIVE, finding.direction)
        assertEquals(listOf(HealthDomain.EMOTIONAL, HealthDomain.SLEEP), finding.evidence.map { it.domain })
        assertEquals(listOf("anxiety_score", "sleep_score"), finding.evidence.map { it.metric })
    }

    private class InMemoryGateway : DataVaultGateway {
        private val values = mutableMapOf<HealthDomain, MutableList<HealthValue>>()

        override fun module(domain: HealthDomain): ModuleDataPort = object : ModuleDataPort {
            override val domain: HealthDomain = domain

            override suspend fun save(valuesToSave: List<HealthValue>) {
                require(valuesToSave.all { it.domain == domain })
                values.getOrPut(domain) { mutableListOf() }.addAll(valuesToSave)
            }

            override suspend fun latest(metric: String): HealthValue? =
                rows(domain, metric).maxByOrNull { it.timestampEpochMs }

            override suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue> =
                rows(domain, metric)
                    .filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
                    .sortedBy { it.timestampEpochMs }

            override suspend fun page(metric: String?, limit: Int, offset: Int): List<HealthValue> =
                rows(domain, metric)
                    .sortedByDescending { it.timestampEpochMs }
                    .drop(offset.coerceAtLeast(0))
                    .take(limit.coerceAtLeast(0))

            override suspend fun count(): Long = values[domain]?.size?.toLong() ?: 0L
        }

        override val interpretation: InterpretationDataPort = object : InterpretationDataPort {
            override suspend fun latest(domain: HealthDomain, metric: String): HealthValue? =
                rows(domain, metric).maxByOrNull { it.timestampEpochMs }

            override suspend fun between(
                domain: HealthDomain,
                metric: String,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = rows(domain, metric)
                .filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
                .sortedBy { it.timestampEpochMs }

            override suspend fun domainBetween(
                domain: HealthDomain,
                fromEpochMs: Long,
                toEpochMs: Long
            ): List<HealthValue> = rows(domain, null)
                .filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
                .sortedBy { it.timestampEpochMs }

            override suspend fun dailyAggregates(
                domain: HealthDomain,
                metric: String,
                fromDayEpoch: Long,
                toDayEpoch: Long
            ): List<DailyAggregatePoint> = emptyList()
        }

        private fun rows(domain: HealthDomain, metric: String?): List<HealthValue> =
            values[domain].orEmpty().filter { metric == null || it.metric == metric }
    }
}
