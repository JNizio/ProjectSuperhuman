package com.projectsuperhuman.next.environment

enum class EnvironmentalFreshnessState { FRESH, STALE }

data class EnvironmentalFreshness(
    val state: EnvironmentalFreshnessState,
    val evaluatedAtEpochMs: Long,
    val ageSinceRetrievalMs: Long,
    val freshForMs: Long
) {
    init {
        require(ageSinceRetrievalMs >= 0L)
        require(freshForMs > 0L)
    }
}
