package com.projectsuperhuman.next.environment

data class EnvironmentalFreshnessPolicy(
    val freshForMs: Long = DEFAULT_ENVIRONMENT_FRESH_MS,
    val maxStaleOnErrorMs: Long = DEFAULT_ENVIRONMENT_MAX_STALE_MS
) {
    init {
        require(freshForMs > 0L)
        require(maxStaleOnErrorMs >= freshForMs)
    }
}

interface EnvironmentalCache {
    fun get(coordinates: EnvironmentalCoordinates): EnvironmentalObservation?
    fun put(coordinates: EnvironmentalCoordinates, observation: EnvironmentalObservation)
    fun clear()
}

class InMemoryEnvironmentalCache : EnvironmentalCache {
    private val values = mutableMapOf<String, EnvironmentalObservation>()
    override fun get(coordinates: EnvironmentalCoordinates) = values[key(coordinates)]
    override fun put(coordinates: EnvironmentalCoordinates, observation: EnvironmentalObservation) {
        values[key(coordinates)] = observation
    }
    override fun clear() = values.clear()
    private fun key(coordinates: EnvironmentalCoordinates): String {
        val c = coordinates.coarsened(2)
        return "${c.latitude},${c.longitude}"
    }
}
