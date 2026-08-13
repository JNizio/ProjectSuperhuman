package com.projectsuperhuman.next.environment

enum class EnvironmentalCondition {
    CLEAR, MAINLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, FREEZING_DRIZZLE,
    RAIN, FREEZING_RAIN, SNOW, SNOW_GRAINS, RAIN_SHOWERS, SNOW_SHOWERS, THUNDERSTORM, UNKNOWN;

    companion object {
        fun fromWmoCode(code: Int) = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61, 63, 65 -> RAIN
            66, 67 -> FREEZING_RAIN
            71, 73, 75 -> SNOW
            77 -> SNOW_GRAINS
            80, 81, 82 -> RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

data class EnvironmentalConditionObservation(
    val wmoCode: Int,
    val condition: EnvironmentalCondition,
    val measurementTimeEpochMs: Long,
    val provenance: EnvironmentalProvenance
)
