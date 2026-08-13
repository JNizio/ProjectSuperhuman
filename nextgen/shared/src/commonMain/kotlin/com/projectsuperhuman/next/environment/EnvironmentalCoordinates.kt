package com.projectsuperhuman.next.environment

import kotlin.math.pow
import kotlin.math.round

data class EnvironmentalCoordinates(
    val latitude: Double,
    val longitude: Double
) {
    fun isValid(): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun coarsened(decimals: Int = 2): EnvironmentalCoordinates {
        require(decimals in 0..6)
        val factor = 10.0.pow(decimals)
        return EnvironmentalCoordinates(
            latitude = round(latitude * factor) / factor,
            longitude = round(longitude * factor) / factor
        )
    }
}
