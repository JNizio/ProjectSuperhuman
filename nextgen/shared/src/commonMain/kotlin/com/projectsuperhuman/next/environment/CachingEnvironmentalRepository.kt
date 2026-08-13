package com.projectsuperhuman.next.environment

class CachingEnvironmentalRepository(
    private val provider: EnvironmentalProvider,
    private val cache: EnvironmentalCache = InMemoryEnvironmentalCache(),
    private val policy: EnvironmentalFreshnessPolicy = EnvironmentalFreshnessPolicy()
) : EnvironmentalRepository {
    override suspend fun current(coordinates: EnvironmentalCoordinates, contextId: String, nowEpochMs: Long, forceRefresh: Boolean): EnvironmentalFetchResult {
        if (!coordinates.isValid()) return EnvironmentalFetchResult.Failure(EnvironmentalProviderError(EnvironmentalProviderErrorKind.HTTP, false))
        val cached = cache.get(coordinates)
        val age = cached?.let { (nowEpochMs - it.retrievedAtEpochMs).coerceAtLeast(0L) }
        if (!forceRefresh && cached != null && age != null && age <= policy.freshForMs) {
            return EnvironmentalFetchResult.Success(cached.mark(nowEpochMs, age, false), EnvironmentalFetchSource.CACHE_FRESH)
        }
        return when (val result = provider.current(coordinates.coarsened(2), contextId, nowEpochMs)) {
            is EnvironmentalProviderResult.Success -> {
                val fresh = result.observation.mark(nowEpochMs, 0L, false)
                cache.put(coordinates, fresh)
                EnvironmentalFetchResult.Success(fresh, EnvironmentalFetchSource.NETWORK, result.warnings)
            }
            is EnvironmentalProviderResult.Failure -> if (cached != null && age != null && age <= policy.maxStaleOnErrorMs) {
                EnvironmentalFetchResult.Success(cached.mark(nowEpochMs, age, true), EnvironmentalFetchSource.CACHE_STALE, refreshError = result.error)
            } else EnvironmentalFetchResult.Failure(result.error)
        }
    }

    private fun EnvironmentalObservation.mark(now: Long, age: Long, stale: Boolean) = copy(
        freshness = EnvironmentalFreshness(if (stale) EnvironmentalFreshnessState.STALE else EnvironmentalFreshnessState.FRESH, now, age, policy.freshForMs)
    )
}
