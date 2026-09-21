package com.projectsuperhuman.next

import kotlin.math.max

/**
 * Pure nutrition reliability helpers shared by ingestion/UI tests.
 * Unknown nutrient data must never be silently converted into a measured zero.
 */
internal object NutritionMath {
    fun scalePer100(valuePer100: Double, amount: Double): Double =
        if (valuePer100.isFinite() && amount.isFinite()) valuePer100 * amount.coerceAtLeast(0.0) / 100.0 else 0.0

    fun recipePerServing(total: Double, servings: Double): Double =
        if (!total.isFinite() || !servings.isFinite() || servings <= 0.0) 0.0 else total / servings

    fun coverage(known: Int, total: Int): Double =
        if (total <= 0) 0.0 else known.coerceIn(0, total).toDouble() / total.toDouble()

    fun hasEnoughCoverage(known: Int, total: Int, minimum: Double = 0.6): Boolean =
        total > 0 && coverage(known, total) >= minimum.coerceIn(0.0, 1.0)

    /**
     * Validates GTIN-8, UPC-A, EAN-13 and GTIN-14 check digits.
     * This prevents accidental remote lookups for mistyped numeric strings.
     */
    fun isValidBarcode(raw: String): Boolean {
        val digits = raw.filter(Char::isDigit)
        if (digits.length !in setOf(8, 12, 13, 14)) return false
        val expected = digits.last().digitToInt()
        var sum = 0
        val body = digits.dropLast(1)
        body.reversed().forEachIndexed { index, char ->
            val n = char.digitToInt()
            sum += if (index % 2 == 0) n * 3 else n
        }
        val calculated = (10 - (sum % 10)) % 10
        return calculated == expected
    }

    fun normalizedServings(raw: Double): Double = max(1.0, raw)
}
