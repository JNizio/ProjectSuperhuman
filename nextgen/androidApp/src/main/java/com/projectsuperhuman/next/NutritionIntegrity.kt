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

internal data class NutritionIntegrityReport(
    val warnings: List<String> = emptyList(),
    val conflicted: Boolean = false,
    val derivedSodiumMg: Double? = null,
    val derivedSaltG: Double? = null
) {
    val warningText: String? get() = warnings.distinct().joinToString(" · ").ifBlank { null }
}

/**
 * Conservative nutrition-evidence validation.
 *
 * Source values are preserved unless there is a narrowly defensible reason to suppress one corrupt
 * macro. Wider inconsistencies lower confidence and stay visible as evidence warnings; they are not
 * silently "fixed" into invented nutrition.
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
        // Higher declared energy can legitimately include alcohol, fibre, polyols and organic acids.
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
            warning = "Suppressed inconsistent " + best.first + " value from source data"
        )
    }

    fun validateFoodValues(
        basisAmount: Double,
        basisUnit: FoodUnit,
        kcal: Double,
        kcalKnown: Boolean,
        protein: Double,
        proteinKnown: Boolean,
        carbs: Double,
        carbsKnown: Boolean,
        carbohydrateDefinition: CarbohydrateDefinition = CarbohydrateDefinition.UNKNOWN,
        fat: Double,
        fatKnown: Boolean,
        saturatedFat: Double,
        saturatedFatKnown: Boolean,
        fibre: Double,
        fibreKnown: Boolean,
        sugar: Double,
        sugarKnown: Boolean,
        saltG: Double,
        saltKnown: Boolean,
        sodiumMg: Double,
        sodiumKnown: Boolean,
        servingQuantity: Double? = null,
        productQuantity: Double? = null
    ): NutritionIntegrityReport {
        val warnings = mutableListOf<String>()
        var conflicted = false

        fun invalidKnown(value: Double, known: Boolean, label: String) {
            if (known && (!value.isFinite() || value < 0.0)) {
                warnings += "$label is invalid"
                conflicted = true
            }
        }

        invalidKnown(kcal, kcalKnown, "Energy")
        invalidKnown(protein, proteinKnown, "Protein")
        invalidKnown(carbs, carbsKnown, "Carbohydrate")
        invalidKnown(fat, fatKnown, "Fat")
        invalidKnown(saturatedFat, saturatedFatKnown, "Saturated fat")
        invalidKnown(fibre, fibreKnown, "Fibre")
        invalidKnown(sugar, sugarKnown, "Sugars")
        invalidKnown(saltG, saltKnown, "Salt")
        invalidKnown(sodiumMg, sodiumKnown, "Sodium")

        val per100PhysicalBasis =
            basisAmount in 99.0..101.0 &&
                (basisUnit.dimension == FoodMeasureDimension.MASS || basisUnit.dimension == FoodMeasureDimension.VOLUME)

        if (per100PhysicalBasis) {
            listOf(
                "protein" to (protein to proteinKnown),
                "carbohydrate" to (carbs to carbsKnown),
                "fat" to (fat to fatKnown),
                "saturated fat" to (saturatedFat to saturatedFatKnown),
                "fibre" to (fibre to fibreKnown),
                "sugars" to (sugar to sugarKnown)
            ).forEach { (label, pair) ->
                if (pair.second && pair.first > 100.5) {
                    warnings += "$label exceeds a plausible per-100 basis"
                    conflicted = true
                }
            }
        }

        if (sugarKnown && carbsKnown) {
            val tolerance = max(0.5, carbs * 0.05)
            if (sugar > carbs + tolerance) {
                warnings += "Sugars exceed reported carbohydrate"
                conflicted = true
            }
        }

        if (saturatedFatKnown && fatKnown) {
            val tolerance = max(0.2, fat * 0.03)
            if (saturatedFat > fat + tolerance) {
                warnings += "Saturated fat exceeds total fat"
                conflicted = true
            }
        }

        if (per100PhysicalBasis && proteinKnown && carbsKnown && fatKnown) {
            val carbohydrateMass = when (carbohydrateDefinition) {
                CarbohydrateDefinition.AVAILABLE_EXCLUDING_FIBRE ->
                    carbs + if (fibreKnown) fibre else 0.0
                CarbohydrateDefinition.TOTAL_INCLUDING_FIBRE,
                CarbohydrateDefinition.UNKNOWN -> carbs
            }
            val majorMass = protein + carbohydrateMass + fat
            if (majorMass > 105.0) {
                warnings += "Major nutrients exceed a plausible per-100 mass"
                conflicted = true
            }
        }

        var derivedSodium: Double? = null
        var derivedSalt: Double? = null
        when {
            saltKnown && sodiumKnown -> {
                val expectedSalt = sodiumMgToSaltG(sodiumMg)
                val tolerance = max(0.15, expectedSalt * 0.18)
                if (abs(saltG - expectedSalt) > tolerance) {
                    warnings += "Salt and sodium values disagree"
                    conflicted = true
                }
            }
            saltKnown && saltG.isFinite() && saltG >= 0.0 -> {
                derivedSodium = saltGToSodiumMg(saltG)
            }
            sodiumKnown && sodiumMg.isFinite() && sodiumMg >= 0.0 -> {
                derivedSalt = sodiumMgToSaltG(sodiumMg)
            }
        }

        servingQuantity?.let {
            if (!it.isFinite() || it <= 0.0 || it > 100_000.0) {
                warnings += "Serving quantity is implausible"
                conflicted = true
            }
        }
        productQuantity?.let {
            if (!it.isFinite() || it <= 0.0 || it > 1_000_000.0) {
                warnings += "Package quantity is implausible"
                conflicted = true
            }
        }

        // Energy disagreement is checked conservatively. We do not expect P/C/F alone to explain
        // all calories, but substantially more macro energy than label energy is suspicious.
        val macroResult = sanitizeMacros(
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
            proteinKnown = proteinKnown,
            carbsKnown = carbsKnown,
            fatKnown = fatKnown,
            kcalKnown = kcalKnown
        )
        macroResult.warning?.let {
            if (!it.startsWith("Suppressed inconsistent")) warnings += it
            if ("inconsistent" in it.lowercase()) conflicted = true
        }

        return NutritionIntegrityReport(
            warnings = warnings.distinct(),
            conflicted = conflicted,
            derivedSodiumMg = derivedSodium,
            derivedSaltG = derivedSalt
        )
    }

    fun saltGToSodiumMg(saltG: Double): Double = saltG * 1_000.0 / 2.5

    fun sodiumMgToSaltG(sodiumMg: Double): Double = sodiumMg / 1_000.0 * 2.5
}
