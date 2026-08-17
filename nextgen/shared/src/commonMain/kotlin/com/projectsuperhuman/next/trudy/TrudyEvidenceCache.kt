package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Very short-lived, bounded request-neighbourhood cache for deterministic personal-evidence reads.
 *
 * Trudy's investigation path may ask for the same primary metric/window more than once while it
 * verifies a target and compares related signals. Re-reading SQLite for identical windows adds
 * latency without adding information. This decorator only reuses exact typed reads for a few
 * seconds; it does not cache interpretations, model answers, diagnoses, or mutable conclusions.
 */
class TrudyCachedPersonalEvidenceSource(
    private val delegate: TrudyPersonalEvidenceSource,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) : TrudyPersonalEvidenceSource {
    init {
        require(ttlMs in 250L..60_000L)
        require(maxEntries in 8..256)
    }

    private data class HistoryKey(
        val domain: HealthDomain,
        val metricId: String,
        val limit: Int
    )

    private data class WindowKey(
        val domain: HealthDomain,
        val metricId: String,
        val range: TrudyTimeRange,
        val limit: Int
    )

    private data class Cached<T>(val storedAtEpochMs: Long, val value: T)

    private val mutex = Mutex()
    private val history = LinkedHashMap<HistoryKey, Cached<List<TrudyMetricEvidence>>>()
    private val windows = LinkedHashMap<WindowKey, Cached<List<TrudyMetricEvidence>>>()
    private val quality = LinkedHashMap<HealthDomain, Cached<TrudyDataQualityEvidence>>()

    override suspend fun metricHistory(
        domain: HealthDomain,
        metricId: String,
        limit: Int
    ): List<TrudyMetricEvidence> {
        val key = HistoryKey(domain, metricId, limit)
        cached(history, key)?.let { return it }
        val value = delegate.metricHistory(domain, metricId, limit)
        store(history, key, value)
        return value
    }

    override suspend fun metricWindow(
        domain: HealthDomain,
        metricId: String,
        range: TrudyTimeRange,
        limit: Int
    ): List<TrudyMetricEvidence> {
        val key = WindowKey(domain, metricId, range, limit)
        cached(windows, key)?.let { return it }
        val value = delegate.metricWindow(domain, metricId, range, limit)
        store(windows, key, value)
        return value
    }

    override suspend fun dataQuality(domain: HealthDomain): TrudyDataQualityEvidence {
        cached(quality, domain)?.let { return it }
        val value = delegate.dataQuality(domain)
        store(quality, domain, value)
        return value
    }

    suspend fun clear() {
        mutex.withLock {
            history.clear()
            windows.clear()
            quality.clear()
        }
    }

    private suspend fun <K, V> cached(map: LinkedHashMap<K, Cached<V>>, key: K): V? {
        val now = nowEpochMs()
        return mutex.withLock {
            evictExpired(map, now)
            map[key]?.value
        }
    }

    private suspend fun <K, V> store(map: LinkedHashMap<K, Cached<V>>, key: K, value: V) {
        val now = nowEpochMs()
        mutex.withLock {
            evictExpired(map, now)
            map.remove(key)
            while (map.size >= maxEntries) {
                val oldest = map.keys.firstOrNull() ?: break
                map.remove(oldest)
            }
            map[key] = Cached(now, value)
        }
    }

    private fun <K, V> evictExpired(map: LinkedHashMap<K, Cached<V>>, now: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.storedAtEpochMs > ttlMs) iterator.remove()
        }
    }

    private companion object {
        const val DEFAULT_TTL_MS = 5_000L
        const val DEFAULT_MAX_ENTRIES = 64
    }
}
