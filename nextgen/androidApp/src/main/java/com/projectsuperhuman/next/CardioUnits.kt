package com.projectsuperhuman.next

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

internal object CardioUnits {
    private const val KM_PER_MILE = 1.609344
    private const val FEET_PER_METRE = 3.280839895013123

    fun kmToMiles(km: Double): Double = km / KM_PER_MILE
    fun milesToKm(miles: Double): Double = miles * KM_PER_MILE
    fun kmhToMph(kmh: Double): Double = kmh / KM_PER_MILE
    fun mphToKmh(mph: Double): Double = mph * KM_PER_MILE
    fun metresToFeet(metres: Double): Double = metres * FEET_PER_METRE
    fun feetToMetres(feet: Double): Double = feet / FEET_PER_METRE
    fun secondsPerKmToSecondsPerMile(secondsPerKm: Double): Double = secondsPerKm * KM_PER_MILE
    fun secondsPerMileToSecondsPerKm(secondsPerMile: Double): Double = secondsPerMile / KM_PER_MILE

    fun displayDistance(km: Double, units: CardioUnitSystem): Pair<Double, String> =
        if (units == CardioUnitSystem.METRIC) km to "km" else kmToMiles(km) to "mi"

    fun displaySpeed(kmh: Double, units: CardioUnitSystem): Pair<Double, String> =
        if (units == CardioUnitSystem.METRIC) kmh to "km/h" else kmhToMph(kmh) to "mph"

    fun displayElevation(metres: Double, units: CardioUnitSystem): Pair<Double, String> =
        if (units == CardioUnitSystem.METRIC) metres to "m" else metresToFeet(metres) to "ft"

    fun displayPace(secondsPerKm: Double, units: CardioUnitSystem): Pair<Int, String> {
        val seconds = if (units == CardioUnitSystem.METRIC) {
            secondsPerKm
        } else {
            secondsPerKmToSecondsPerMile(secondsPerKm)
        }
        return seconds.roundToInt() to if (units == CardioUnitSystem.METRIC) "min/km" else "min/mi"
    }

    fun formatPace(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return (safe / 60).toString() + ":" + (safe % 60).toString().padStart(2, '0')
    }

    /**
     * Parses a user-entered decimal using the current locale while still accepting '.'
     * as a fallback for data copied from devices/apps.
     */
    fun parseLocalizedDecimal(input: String, locale: Locale = Locale.getDefault()): Double? {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val decimal = symbols.decimalSeparator
        val grouping = symbols.groupingSeparator
        val raw = input.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
        val normalized = if (decimal != '.' && raw.contains(decimal)) {
            raw.replace(grouping.toString(), "").replace(decimal, '.')
        } else {
            val withoutGrouping = if (grouping != '.') raw.replace(grouping.toString(), "") else raw
            withoutGrouping.replace(decimal, '.')
        }
        if (normalized.count { it == '.' } > 1) return null
        return normalized.toDoubleOrNull()
    }

    fun sanitizeDecimalInput(input: String, locale: Locale = Locale.getDefault(), maxLength: Int = 10): String {
        val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        var usedDecimal = false
        val out = buildString {
            input.forEach { ch ->
                when {
                    ch.isDigit() -> append(ch)
                    (ch == decimal || ch == '.') && !usedDecimal -> {
                        append(decimal)
                        usedDecimal = true
                    }
                }
            }
        }
        return out.take(maxLength)
    }
}
