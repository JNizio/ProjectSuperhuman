package com.projectsuperhuman.next.environment

object OpenMeteoRequestBuilder {
    const val WEATHER_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    const val AIR_QUALITY_ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality"

    fun weather(coordinates: EnvironmentalCoordinates): EnvironmentalHttpRequest {
        val c = checked(coordinates)
        return EnvironmentalHttpRequest(WEATHER_ENDPOINT, base(c) + mapOf(
            "current" to "temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index",
            "daily" to "sunrise,sunset,daylight_duration",
            "temperature_unit" to "celsius",
            "wind_speed_unit" to "ms",
            "precipitation_unit" to "mm",
            "timeformat" to "unixtime",
            "timezone" to "auto",
            "forecast_days" to "1"
        ))
    }

    fun airQuality(coordinates: EnvironmentalCoordinates): EnvironmentalHttpRequest {
        val c = checked(coordinates)
        return EnvironmentalHttpRequest(AIR_QUALITY_ENDPOINT, base(c) + mapOf(
            "current" to "european_aqi,pm2_5,pm10",
            "timeformat" to "unixtime"
        ))
    }

    private fun checked(coordinates: EnvironmentalCoordinates): EnvironmentalCoordinates {
        require(coordinates.isValid())
        return coordinates.coarsened(2)
    }

    private fun base(c: EnvironmentalCoordinates) = mapOf(
        "latitude" to c.latitude.toString(),
        "longitude" to c.longitude.toString()
    )
}
