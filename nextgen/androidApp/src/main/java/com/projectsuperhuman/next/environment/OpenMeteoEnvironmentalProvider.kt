package com.projectsuperhuman.next.environment

import kotlinx.coroutines.CancellationException

class OpenMeteoEnvironmentalProvider(
    private val httpClient: EnvironmentalHttpClient = AndroidEnvironmentalHttpClient(),
    private val includeAirQuality: Boolean = true
) : EnvironmentalProvider {
    override val providerId = "open-meteo"

    override suspend fun current(
        coordinates: EnvironmentalCoordinates,
        contextId: String,
        retrievedAtEpochMs: Long
    ): EnvironmentalProviderResult {
        if (!coordinates.isValid() || contextId.isBlank()) {
            return EnvironmentalProviderResult.Failure(EnvironmentalProviderError(EnvironmentalProviderErrorKind.HTTP, false))
        }
        val weatherResponse = request(OpenMeteoRequestBuilder.weather(coordinates))
            ?: return EnvironmentalProviderResult.Failure(error(EnvironmentalProviderErrorKind.NETWORK))
        if (weatherResponse.statusCode !in 200..299) return EnvironmentalProviderResult.Failure(httpError(weatherResponse.statusCode))
        val weather = runCatching { OpenMeteoJsonMapper.weather(weatherResponse.body, contextId, retrievedAtEpochMs) }.getOrNull()
            ?: return EnvironmentalProviderResult.Failure(error(EnvironmentalProviderErrorKind.PARSING))
        if (!includeAirQuality) return EnvironmentalProviderResult.Success(weather)

        val airResponse = request(OpenMeteoRequestBuilder.airQuality(coordinates))
            ?: return EnvironmentalProviderResult.Success(weather, setOf(EnvironmentalAvailabilityWarning.AIR_QUALITY_UNAVAILABLE))
        if (airResponse.statusCode !in 200..299) {
            return EnvironmentalProviderResult.Success(weather, setOf(EnvironmentalAvailabilityWarning.AIR_QUALITY_UNAVAILABLE))
        }
        val air = runCatching { OpenMeteoJsonMapper.airQuality(airResponse.body) }.getOrNull()
            ?: return EnvironmentalProviderResult.Success(weather, setOf(EnvironmentalAvailabilityWarning.AIR_QUALITY_UNAVAILABLE))
        val warning = if (air.size < 3) setOf(EnvironmentalAvailabilityWarning.AIR_QUALITY_INCOMPLETE) else emptySet()
        return EnvironmentalProviderResult.Success(weather.copy(measurements = weather.measurements + air), warning)
    }

    private suspend fun request(request: EnvironmentalHttpRequest): EnvironmentalHttpResponse? = try {
        httpClient.get(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun error(kind: EnvironmentalProviderErrorKind) = EnvironmentalProviderError(kind, recoverable = true)
    private fun httpError(status: Int) = EnvironmentalProviderError(
        if (status in 500..599) EnvironmentalProviderErrorKind.PROVIDER_UNAVAILABLE else EnvironmentalProviderErrorKind.HTTP,
        status == 408 || status == 429 || status in 500..599,
        status
    )
}
