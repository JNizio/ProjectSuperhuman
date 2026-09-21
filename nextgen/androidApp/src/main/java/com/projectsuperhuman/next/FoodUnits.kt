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
    FL_OZ("fl oz", FoodMeasureDimension.VOLUME, 29.5735295625),

    SERVING("serving", FoodMeasureDimension.DERIVED, 1.0),
    PIECE("piece", FoodMeasureDimension.DERIVED, 1.0),
    SLICE("slice", FoodMeasureDimension.DERIVED, 1.0),
    SCOOP("scoop", FoodMeasureDimension.DERIVED, 1.0),
    BAR("bar", FoodMeasureDimension.DERIVED, 1.0),
    PACKAGE("package", FoodMeasureDimension.DERIVED, 1.0),
    BOTTLE("bottle", FoodMeasureDimension.DERIVED, 1.0),
    CAN("can", FoodMeasureDimension.DERIVED, 1.0);

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
                "fl oz", "floz", "fluid ounce", "fluid ounces" -> FL_OZ
                "serving", "portion" -> SERVING
                "piece", "pc", "unit" -> PIECE
                "slice", "slices" -> SLICE
                "scoop", "scoops" -> SCOOP
                "bar", "bars" -> BAR
                "package", "pack", "packet" -> PACKAGE
                "bottle", "bottles" -> BOTTLE
                "can", "cans", "tin", "tins" -> CAN
                else -> null
            }
        }
    }
}

internal data class FoodConversion(
    val basisAmount: Double,
    val basisUnit: FoodUnit,
    val factor: Double,
    val grams: Double?,
    val millilitres: Double?
)

internal object FoodUnitSystem {
    private val massUnits = listOf(FoodUnit.MG, FoodUnit.G, FoodUnit.KG, FoodUnit.OZ, FoodUnit.LB)
    private val volumeUnits = listOf(
        FoodUnit.ML, FoodUnit.CL, FoodUnit.DL, FoodUnit.L,
        FoodUnit.TSP, FoodUnit.TBSP, FoodUnit.CUP, FoodUnit.FL_OZ
    )

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
            servingSpecificUnit(food)?.let(out::add)
        }
        inferredPiecePhysical(food)?.let { out += FoodUnit.PIECE }
        if (canResolveDerived(food.productQuantity, food.productQuantityUnit, food)) {
            out += FoodUnit.PACKAGE
            packageSpecificUnit(food)?.let(out::add)
        }
        return out.toList()
    }

    fun defaultUnit(food: NativeFood): FoodUnit {
        val units = availableUnits(food)
        val specific = servingSpecificUnit(food)
        return when {
            specific != null && specific in units -> specific
            inferredPiecePhysical(food) != null && FoodUnit.PIECE in units -> FoodUnit.PIECE
            FoodUnit.SERVING in units -> FoodUnit.SERVING
            else -> food.basisUnit
        }
    }

    fun defaultAmount(food: NativeFood): Double =
        if (defaultUnit(food).dimension == FoodMeasureDimension.DERIVED) 1.0 else basisAmount(food)

    fun amountForBasis(food: NativeFood, unit: FoodUnit): Double? {
        val one = convert(food, 1.0, unit) ?: return null
        if (one.factor <= 0.0) return null
        return 1.0 / one.factor
    }

    fun convert(food: NativeFood, amount: Double, unit: FoodUnit): FoodConversion? {
        if (!amount.isFinite() || amount <= 0.0) return null
        return when (unit) {
            FoodUnit.SERVING -> convertServing(food, amount)
            FoodUnit.PIECE -> convertPiece(food, amount)
            FoodUnit.SLICE,
            FoodUnit.SCOOP,
            FoodUnit.BAR -> {
                if (servingSpecificUnit(food) != unit) return null
                convertServing(food, amount)
            }
            FoodUnit.PACKAGE -> convertPackage(food, amount)
            FoodUnit.BOTTLE,
            FoodUnit.CAN -> {
                if (packageSpecificUnit(food) != unit) return null
                convertPackage(food, amount)
            }
            else -> convertPhysical(food, amount, unit)
        }
    }

    fun displayUnit(food: NativeFood, unit: FoodUnit): String = when {
        unit == FoodUnit.PIECE && isEggFood(food) -> "egg"
        else -> unit.symbol
    }

    fun unitContext(food: NativeFood, unit: FoodUnit): String? {
        if (unit == FoodUnit.PIECE) {
            inferredPiecePhysical(food)?.let { (quantity, physicalUnit) ->
                return "1 " + displayUnit(food, unit) + " ≈ " + formatAmount(quantity, physicalUnit)
            }
        }
        return null
    }

    fun formatAmount(amount: Double, unit: FoodUnit): String {
        val n = if (abs(amount - amount.toInt()) < 0.0001) amount.toInt().toString()
        else ((amount * 10.0).toInt() / 10.0).toString()
        return n + " " + unit.symbol
    }

    fun describeBasis(food: NativeFood): String =
        formatAmount(basisAmount(food), food.basisUnit) + " basis"

    private fun convertPiece(food: NativeFood, amount: Double): FoodConversion? {
        if (servingSpecificUnit(food) == FoodUnit.PIECE) {
            return convertServing(food, amount)
        }
        val inferred = inferredPiecePhysical(food) ?: return null
        return convertPhysical(food, amount * inferred.first, inferred.second)
    }

    private fun convertServing(food: NativeFood, amount: Double): FoodConversion? {
        val q = food.servingQuantity ?: return null
        val u = food.servingQuantityUnit ?: return null
        return convertPhysical(food, amount * q, u)
    }

    private fun convertPackage(food: NativeFood, amount: Double): FoodConversion? {
        val q = food.productQuantity ?: return null
        val u = food.productQuantityUnit ?: return null
        return convertPhysical(food, amount * q, u)
    }

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

    private fun inferredPiecePhysical(food: NativeFood): Pair<Double, FoodUnit>? {
        if (!isEggFood(food)) return null
        val text = (food.name + " " + food.searchText + " " + food.servingLabel).lowercase()
        val grams = when {
            Regex("""\b(jumbo)\b""").containsMatchIn(text) -> 63.0
            Regex("""\b(extra[- ]?large|x[- ]?large|xl)\b""").containsMatchIn(text) -> 56.0
            Regex("""\blarge\b""").containsMatchIn(text) -> 50.0
            Regex("""\bmedium\b""").containsMatchIn(text) -> 44.0
            Regex("""\bsmall\b""").containsMatchIn(text) -> 38.0
            Regex("""\b(peewee|pee wee)\b""").containsMatchIn(text) -> 35.0
            else -> 50.0
        }
        return grams to FoodUnit.G
    }

    private fun isEggFood(food: NativeFood): Boolean {
        val text = (food.name + " " + food.searchText + " " + food.servingLabel).lowercase()
        return Regex("""\beggs?\b""").containsMatchIn(text) &&
            !Regex("""\beggplant\b""").containsMatchIn(text)
    }

    private fun servingSpecificUnit(food: NativeFood): FoodUnit? {
        val normalized = food.servingLabel.lowercase()
        return when {
            Regex("""\b(slice|slices)\b""").containsMatchIn(normalized) -> FoodUnit.SLICE
            Regex("""\b(scoop|scoops)\b""").containsMatchIn(normalized) -> FoodUnit.SCOOP
            Regex("""\b(bar|bars)\b""").containsMatchIn(normalized) -> FoodUnit.BAR
            Regex("""\b(piece|pieces|pc|pcs|unit|units|egg|eggs|biscuit|biscuits|cookie|cookies)\b""")
                .containsMatchIn(normalized) -> FoodUnit.PIECE
            else -> null
        }
    }

    private fun packageSpecificUnit(food: NativeFood): FoodUnit? {
        val normalized = (food.quantity + " " + food.name + " " + food.searchText).lowercase()
        return when {
            Regex("""\b(bottle|bottles)\b""").containsMatchIn(normalized) -> FoodUnit.BOTTLE
            Regex("""\b(can|cans|tin|tins)\b""").containsMatchIn(normalized) -> FoodUnit.CAN
            else -> null
        }
    }

    fun parseBasis(raw: String): Pair<Double, FoodUnit>? {
        val matches = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]+(?:\s+oz)?)""")
            .findAll(raw.trim())
        for (match in matches) {
            val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val unit = FoodUnit.fromSymbol(match.groupValues[2]) ?: continue
            if (unit.dimension == FoodMeasureDimension.DERIVED) continue
            if (!amount.isFinite() || amount <= 0.0) continue
            return amount to unit
        }
        return null
    }
}
