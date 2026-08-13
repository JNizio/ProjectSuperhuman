package com.projectsuperhuman.next.environment

import org.json.JSONObject

internal object OpenMeteoJsonMapper {
    private val weatherSource = EnvironmentalProvenance("open-meteo", "forecast-api")
    private val airSource = EnvironmentalProvenance("open-meteo", "air-quality-api-cams")

    fun weather(body: String, contextId: String, retrievedAt: Long): EnvironmentalObservation {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val measuredAt = current.getLong("time") * 1_000L
        val values = mutableListOf<EnvironmentalMeasurement>()
        values.addIf(current, "temperature_2m", EnvironmentalMetricIds.TEMPERATURE_C, EnvironmentalUnit.CELSIUS, measuredAt, weatherSource)
        values.addIf(current, "apparent_temperature", EnvironmentalMetricIds.FEELS_LIKE_C, EnvironmentalUnit.CELSIUS, measuredAt, weatherSource)
        values.addIf(current, "relative_humidity_2m", EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, EnvironmentalUnit.PERCENT, measuredAt, weatherSource)
        values.addIf(current, "surface_pressure", EnvironmentalMetricIds.SURFACE_PRESSURE_HPA, EnvironmentalUnit.HECTOPASCAL, measuredAt, weatherSource)
        values.addIf(current, "precipitation", EnvironmentalMetricIds.PRECIPITATION_MM, EnvironmentalUnit.MILLIMETER, measuredAt, weatherSource)
        values.addIf(current, "wind_speed_10m", EnvironmentalMetricIds.WIND_SPEED_MPS, EnvironmentalUnit.METERS_PER_SECOND, measuredAt, weatherSource)
        values.addIf(current, "wind_direction_10m", EnvironmentalMetricIds.WIND_DIRECTION_DEG, EnvironmentalUnit.DEGREES, measuredAt, weatherSource)
        values.addIf(current, "wind_gusts_10m", EnvironmentalMetricIds.WIND_GUST_MPS, EnvironmentalUnit.METERS_PER_SECOND, measuredAt, weatherSource)
        values.addIf(current, "cloud_cover", EnvironmentalMetricIds.CLOUD_COVER_PCT, EnvironmentalUnit.PERCENT, measuredAt, weatherSource)
        values.addIf(current, "uv_index", EnvironmentalMetricIds.UV_INDEX, EnvironmentalUnit.INDEX, measuredAt, weatherSource)
        val code = if (current.has("weather_code") && !current.isNull("weather_code")) current.getInt("weather_code") else null
        return EnvironmentalObservation(
            retrievedAt,
            contextId,
            values,
            code?.let { EnvironmentalConditionObservation(it, EnvironmentalCondition.fromWmoCode(it), measuredAt, weatherSource) },
            sunCycle(root.optJSONObject("daily")),
            EnvironmentalFreshness(EnvironmentalFreshnessState.FRESH, retrievedAt, 0L, DEFAULT_ENVIRONMENT_FRESH_MS)
        )
    }

    fun airQuality(body: String): List<EnvironmentalMeasurement> {
        val current = JSONObject(body).getJSONObject("current")
        val measuredAt = current.getLong("time") * 1_000L
        return buildList {
            addIf(current, "european_aqi", EnvironmentalMetricIds.EUROPEAN_AQI, EnvironmentalUnit.INDEX, measuredAt, airSource)
            addIf(current, "pm2_5", EnvironmentalMetricIds.PM2_5_UG_M3, EnvironmentalUnit.MICROGRAMS_PER_CUBIC_METER, measuredAt, airSource)
            addIf(current, "pm10", EnvironmentalMetricIds.PM10_UG_M3, EnvironmentalUnit.MICROGRAMS_PER_CUBIC_METER, measuredAt, airSource)
        }
    }

    private fun sunCycle(daily: JSONObject?): EnvironmentalSunCycle? {
        daily ?: return null
        val sunrise = daily.optJSONArray("sunrise")?.optLong(0, Long.MIN_VALUE) ?: Long.MIN_VALUE
        val sunset = daily.optJSONArray("sunset")?.optLong(0, Long.MIN_VALUE) ?: Long.MIN_VALUE
        val daylight = daily.optJSONArray("daylight_duration")?.optDouble(0, Double.NaN) ?: Double.NaN
        if (sunrise == Long.MIN_VALUE || sunset == Long.MIN_VALUE || !daylight.isFinite()) return null
        return EnvironmentalSunCycle(sunrise * 1_000L, sunset * 1_000L, daylight, weatherSource)
    }

    private fun MutableList<EnvironmentalMeasurement>.addIf(
        json: JSONObject, responseName: String, metricId: String, unit: EnvironmentalUnit,
        measuredAt: Long, source: EnvironmentalProvenance
    ) {
        if (!json.has(responseName) || json.isNull(responseName)) return
        val value = json.optDouble(responseName, Double.NaN)
        if (!value.isFinite()) return
        val definition = EnvironmentalMetricCatalog.definition(metricId)
        if (definition?.minAccepted != null && value < definition.minAccepted) return
        if (definition?.maxAccepted != null && value > definition.maxAccepted) return
        add(EnvironmentalMeasurement(metricId, value, unit, measuredAt, source))
    }
}
