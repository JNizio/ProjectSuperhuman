package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TrudyEvidenceCacheTest {
    @Test
    fun identicalWindowAndQualityReadsAreReusedOnlyInsideShortTtl() = runTest {
        var now = 1_000L
        var windowReads = 0
        var qualityReads = 0
        val range = TrudyTimeRange(100L, 900L)
        val row = TrudyMetricEvidence(
            domain = HealthDomain.SLEEP,
            metricId = "sleep_score",
            value = 80.0,
            unit = "score",
            timestampEpochMs = 800L,
            source = "test"
        )
        val delegate = object : TrudyPersonalEvidenceSource {
            override suspend fun metricHistory(domain: HealthDomain, metricId: String, limit: Int) = listOf(row)
            override suspend fun metricWindow(domain: HealthDomain, metricId: String, range: TrudyTimeRange, limit: Int): List<TrudyMetricEvidence> {
                windowReads++
                return listOf(row)
            }
            override suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence {
                qualityReads++
                return TrudyDataQualityEvidence(domain, 90, 10, 1, 800L, 0.1, false, emptyList())
            }
        }
        val cached = TrudyCachedPersonalEvidenceSource(
            delegate = delegate,
            ttlMs = 1_000L,
            maxEntries = 8,
            nowEpochMs = { now }
        )

        cached.metricWindow(HealthDomain.SLEEP, "sleep_score", range, 100)
        cached.metricWindow(HealthDomain.SLEEP, "sleep_score", range, 100)
        cached.dataQuality(HealthDomain.SLEEP)
        cached.dataQuality(HealthDomain.SLEEP)

        assertEquals(1, windowReads)
        assertEquals(1, qualityReads)

        now += 1_001L
        cached.metricWindow(HealthDomain.SLEEP, "sleep_score", range, 100)
        cached.dataQuality(HealthDomain.SLEEP)

        assertEquals(2, windowReads)
        assertEquals(2, qualityReads)
    }
}
