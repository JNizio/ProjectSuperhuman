package com.projectsuperhuman.next

import kotlin.math.abs
import kotlin.math.max

internal data class MacroIntegrityResult(
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val proteinKnown: Boolean,
    val carbsKnown: Boolean,
    val fatKnown: Boolean,
    val warning: String? = null
)

/**
 * Conservative nutrition-label sanity checks.
 *
 * We do not try to "correct" food labels from heuristics. We only suppress one macro when the
 * declared energy is impossible with all reported macros and removing exactly one field restores
 * strong energy consistency. This catches corrupt source records such as a drink with 37 kcal,
 * 9 g carbohydrate and an erroneous 9 g fat per 100 ml.
 *
 * Cases where declared energy is HIGHER than macro energy are left alone because alcohol,
 * polyols, fibre and organic acids can legitimately contribute energy not represented by P/C/F.
 */
internal object NutritionIntegrity {
    fun sanitizeMacros(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        proteinKnown: Boolean,
        carbsKnown: Boolean,
        fatKnown: Boolean,
        kcalKnown: Boolean = true
    ): MacroIntegrityResult {
        val p = protein.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val c = carbs.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val f = fat.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        var pKnown = proteinKnown && protein.isFinite() && protein >= 0.0
        var cKnown = carbsKnown && carbs.isFinite() && carbs >= 0.0
        var fKnown = fatKnown && fat.isFinite() && fat >= 0.0

        if (!kcalKnown || !kcal.isFinite() || kcal < 0.0) {
            return MacroIntegrityResult(p, c, f, pKnown, cKnown, fKnown)
        }

        val contributions = listOf(
            Triple("protein", if (pKnown) p * 4.0 else 0.0, pKnown),
            Triple("carbs", if (cKnown) c * 4.0 else 0.0, cKnown),
            Triple("fat", if (fKnown) f * 9.0 else 0.0, fKnown)
        )
        val implied = contributions.sumOf { it.second }

        // Only challenge records whose macros imply substantially MORE energy than declared.
        val impossibleTolerance = max(20.0, kcal * 0.25)
        if (implied <= kcal + impossibleTolerance) {
            return MacroIntegrityResult(p, c, f, pKnown, cKnown, fKnown)
        }

        val targetTolerance = max(8.0, kcal * 0.12)
        val currentError = abs(implied - kcal)
        val best = contributions
            .filter { it.third && it.second > 0.0 }
            .map { it.first to abs((implied - it.second) - kcal) }
            .minByOrNull { it.second }

        if (best == null || best.second > targetTolerance || best.second >= currentError * 0.45) {
            return MacroIntegrityResult(
                p, c, f, pKnown, cKnown, fKnown,
                warning = "Declared energy is inconsistent with reported macros"
            )
        }

        when (best.first) {
            "protein" -> pKnown = false
            "carbs" -> cKnown = false
            "fat" -> fKnown = false
        }

        return MacroIntegrityResult(
            protein = p,
            carbs = c,
            fat = f,
            proteinKnown = pKnown,
            carbsKnown = cKnown,
            fatKnown = fKnown,
            warning = "Suppressed inconsistent ${best.first} value from source data"
        )
    }
}
