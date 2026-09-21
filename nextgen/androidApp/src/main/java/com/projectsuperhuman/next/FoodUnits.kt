package com.projectsuperhuman.next

import kotlin.math.abs

internal enum class FoodMeasureDimension {
    MASS,
    VOLUME,
    DERIVED
}

internal enum class FoodUnit(
    val symbol: String,
    val dimension: FoodMeasureDimension,
    /** Multiplier to grams for mass or millilitres for volume. */
    val toBase: Double
) {
    MG("mg", FoodMeasureDimension.MASS, 0.001),
    G("g", FoodMeasureDimension.MASS, 1.0),
    KG("kg", FoodMeasureDimension.MASS, 1_000.0),
    OZ("oz", FoodMeasureDimension.MASS, 28.349523125),
    LB("lb", FoodMeasureDimension.MASS, 453.59237),

    ML("ml", FoodMeasureDimension.VOLUME, 1.0),
    CL("cl", FoodMeasureDimension.VOLUME, 10.0),
    DL("dl", FoodMeasureDimension.VOLUME, 100.0),
    L("l", FoodMeasureDimension.VOLUME, 1_000.0),
    TSP("tsp", FoodMeasureDimension.VOLUME, 4.92892159375),
    TBSP("tbsp", FoodMeasureDimension.VOLUME, 14.78676478125),
    CUP("cup", FoodMeasureDimension.VOLUME, 236.5882365),

    SERVING("serving", FoodMeasureDimension.DERIVED, 1.0),
    PACKAGE("package", FoodMeasureDimension.DERIVED, 1.0),
    PIECE("piece", FoodMeasureDimension.DERIVED, 1.0);

    companion object {
        fun fromSymbol(raw: String?): FoodUnit? {
            val normalized = raw?.trim()?.lowercase()?.replace("ℓ", "l") ?: return null
            return when (normalized) {
                "mg", "milligram", "milligrams" -> MG
                "g", "gr", "gram", "grams" -> G
                "kg", "kilogram", "kilograms" -> KG
                "oz", "ounce", "ounces" -> OZ
                "lb", "lbs", "pound", "pounds" -> LB
                "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> ML
                "cl" -> CL
                "dl" -> DL
                "l", "lt", "liter", "liters", "litre", "litres" -> L
                "tsp", "teaspoon", "teaspoons" -> TSP
                "tbsp", "tablespoon", "tablespoons" -> TBSP
                "cup", "cups" -> CUP
                "serving", "portion" -> SERVING
                "package", "pack" -> PACKAGE
                "piece", "pc", "unit" -> PIECE
                else -> null
            }
        }
    }
}

internal data class FoodConversion(
    /** Amount expressed in the food's nutrition basis unit (usually g or ml). */
    val basisAmount: Double,
    val basisUnit: FoodUnit,
    /** Multiplier applied to per-basis nutrition. */
    val factor: Double,
    /** When physically resolvable, canonical mass in grams. */
    val grams: Double?,
    /** When physically resolvable, canonical volume in millilitres. */
    val millilitres: Double?
)

internal object FoodUnitSystem {
    private val massUnits = listOf(FoodUnit.G, FoodUnit.KG, FoodUnit.OZ, FoodUnit.LB)
    private val volumeUnits = listOf(FoodUnit.ML, FoodUnit.L, FoodUnit.CL, FoodUnit.DL, FoodUnit.TSP, FoodUnit.TBSP, FoodUnit.CUP)

    fun basisUnit(food: NativeFood): FoodUnit = food.basisUnit
    fun basisAmount(food: NativeFood): Double = food.basisAmount.takeIf { it > 0.0 } ?: 100.0

    fun availableUnits(food: NativeFood): List<FoodUnit> {
        val out = linkedSetOf<FoodUnit>()
        when (food.basisUnit.dimension) {
            FoodMeasureDimension.MASS -> {
                out += massUnits
                if (food.densityGPerMl != null) out += volumeUnits
            }
            FoodMeasureDimension.VOLUME -> {
                out += volumeUnits
                if (food.densityGPerMl != null) out += massUnits
            }
            FoodMeasureDimension.DERIVED -> Unit
        }

        if (canResolveDerived(food.servingQuantity, food.servingQuantityUnit, food)) {
            out += FoodUnit.SERVING
            if (looksLikeSinglePiece(food.servingLabel)) out += FoodUnit.PIECE
        }
        if (canResolveDerived(food.productQuantity, food.productQuantityUnit, food)) {
            out += FoodUnit.PACKAGE
        }
        return out.toList()
    }

    fun defaultUnit(food: NativeFood): FoodUnit =
        if (FoodUnit.SERVING in availableUnits(food)) FoodUnit.SERVING else food.basisUnit

    fun defaultAmount(food: NativeFood): Double =
        if (defaultUnit(food) == FoodUnit.SERVING) 1.0 else basisAmount(food)

    /**
     * Amount in [unit] that corresponds to exactly one nutrition basis quantity.
     * Useful when the user changes units: 100 g olive oil becomes ~109.9 ml, not an arbitrary 100 ml.
     */
    fun amountForBasis(food: NativeFood, unit: FoodUnit): Double? {
        val one = convert(food, 1.0, unit) ?: return null
        if (one.factor <= 0.0) return null
        return 1.0 / one.factor
    }

    fun convert(food: NativeFood, amount: Double, unit: FoodUnit): FoodConversion? {
        if (!amount.isFinite() || amount <= 0.0) return null

        return when (unit) {
            FoodUnit.SERVING -> {
                val q = food.servingQuantity ?: return null
                val u = food.servingQuantityUnit ?: return null
                convert(food, amount * q, u)
            }
            FoodUnit.PACKAGE -> {
                val q = food.productQuantity ?: return null
                val u = food.productQuantityUnit ?: return null
                convert(food, amount * q, u)
            }
            FoodUnit.PIECE -> {
                if (!looksLikeSinglePiece(food.servingLabel)) return null
                val q = food.servingQuantity ?: return null
                val u = food.servingQuantityUnit ?: return null
                convert(food, amount * q, u)
            }
            else -> convertPhysical(food, amount, unit)
        }
    }

    fun formatAmount(amount: Double, unit: FoodUnit): String {
        val n = if (abs(amount - amount.toInt()) < 0.0001) amount.toInt().toString()
        else ((amount * 10.0).toInt() / 10.0).toString()
        return "$n ${unit.symbol}"
    }

    fun describeBasis(food: NativeFood): String =
        "${formatAmount(basisAmount(food), food.basisUnit)} basis"

    private fun convertPhysical(food: NativeFood, amount: Double, unit: FoodUnit): FoodConversion? {
        val basis = food.basisUnit
        if (basis.dimension == FoodMeasureDimension.DERIVED || unit.dimension == FoodMeasureDimension.DERIVED) return null

        val inputGrams: Double?
        val inputMl: Double?
        when (unit.dimension) {
            FoodMeasureDimension.MASS -> {
                inputGrams = amount * unit.toBase
                inputMl = food.densityGPerMl?.takeIf { it > 0.0 }?.let { inputGrams / it }
            }
            FoodMeasureDimension.VOLUME -> {
                inputMl = amount * unit.toBase
                inputGrams = food.densityGPerMl?.takeIf { it > 0.0 }?.let { inputMl * it }
            }
            FoodMeasureDimension.DERIVED -> return null
        }

        val basisPhysicalAmount = when (basis.dimension) {
            FoodMeasureDimension.MASS -> inputGrams
            FoodMeasureDimension.VOLUME -> inputMl
            FoodMeasureDimension.DERIVED -> null
        } ?: return null

        val factor = basisPhysicalAmount / basisAmount(food)
        if (!factor.isFinite() || factor <= 0.0) return null

        return FoodConversion(
            basisAmount = basisPhysicalAmount,
            basisUnit = basis,
            factor = factor,
            grams = inputGrams,
            millilitres = inputMl
        )
    }

    private fun canResolveDerived(quantity: Double?, unit: FoodUnit?, food: NativeFood): Boolean {
        if (quantity == null || quantity <= 0.0 || unit == null || unit.dimension == FoodMeasureDimension.DERIVED) return false
        return convertPhysical(food, quantity, unit) != null
    }

    private fun looksLikeSinglePiece(label: String): Boolean {
        if (label.isBlank()) return false
        val normalized = label.lowercase()
        return Regex("""\b(1|one)\s*(piece|pc|unit|egg|bar|slice|biscuit|cookie|can|bottle|sachet)\b""")
            .containsMatchIn(normalized)
    }

    fun parseBasis(raw: String): Pair<Double, FoodUnit>? {
        val match = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]+)""").find(raw.trim()) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = FoodUnit.fromSymbol(match.groupValues[2]) ?: return null
        if (unit.dimension == FoodMeasureDimension.DERIVED) return null
        return amount to unit
    }
}
