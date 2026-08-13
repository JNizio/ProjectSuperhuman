package com.projectsuperhuman.next.environment

/** Stable, provider-independent Environmental metric vocabulary. */
object EnvironmentalMetricIds {
    const val TEMPERATURE_C = "environment_temperature_c"
    const val FEELS_LIKE_C = "environment_feels_like_c"
    const val RELATIVE_HUMIDITY_PCT = "environment_relative_humidity_pct"
    const val SURFACE_PRESSURE_HPA = "environment_surface_pressure_hpa"
    const val PRECIPITATION_MM = "environment_precipitation_mm"
    const val WIND_SPEED_MPS = "environment_wind_speed_mps"
    const val WIND_DIRECTION_DEG = "environment_wind_direction_deg"
    const val WIND_GUST_MPS = "environment_wind_gust_mps"
    const val CLOUD_COVER_PCT = "environment_cloud_cover_pct"
    const val UV_INDEX = "environment_uv_index"
    const val EUROPEAN_AQI = "environment_european_aqi"
    const val PM2_5_UG_M3 = "environment_pm2_5_ug_m3"
    const val PM10_UG_M3 = "environment_pm10_ug_m3"
}

enum class EnvironmentalUnit(val symbol: String) {
    CELSIUS("°C"), PERCENT("%"), HECTOPASCAL("hPa"), MILLIMETER("mm"),
    METERS_PER_SECOND("m/s"), DEGREES("°"), INDEX("index"),
    MICROGRAMS_PER_CUBIC_METER("µg/m³")
}

data class EnvironmentalMetricDefinition(
    val id: String,
    val unit: EnvironmentalUnit,
    val minAccepted: Double? = null,
    val maxAccepted: Double? = null
)

object EnvironmentalMetricCatalog {
    val all = listOf(
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.TEMPERATURE_C, EnvironmentalUnit.CELSIUS, -100.0, 70.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.FEELS_LIKE_C, EnvironmentalUnit.CELSIUS, -120.0, 80.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, EnvironmentalUnit.PERCENT, 0.0, 100.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.SURFACE_PRESSURE_HPA, EnvironmentalUnit.HECTOPASCAL, 300.0, 1_100.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.PRECIPITATION_MM, EnvironmentalUnit.MILLIMETER, 0.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.WIND_SPEED_MPS, EnvironmentalUnit.METERS_PER_SECOND, 0.0, 150.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.WIND_DIRECTION_DEG, EnvironmentalUnit.DEGREES, 0.0, 360.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.WIND_GUST_MPS, EnvironmentalUnit.METERS_PER_SECOND, 0.0, 200.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.CLOUD_COVER_PCT, EnvironmentalUnit.PERCENT, 0.0, 100.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.UV_INDEX, EnvironmentalUnit.INDEX, 0.0, 40.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.EUROPEAN_AQI, EnvironmentalUnit.INDEX, 0.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.PM2_5_UG_M3, EnvironmentalUnit.MICROGRAMS_PER_CUBIC_METER, 0.0),
        EnvironmentalMetricDefinition(EnvironmentalMetricIds.PM10_UG_M3, EnvironmentalUnit.MICROGRAMS_PER_CUBIC_METER, 0.0)
    )
    private val byId = all.associateBy { it.id }
    fun definition(id: String) = byId[id]
}
