package com.family.mealplanner.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Units only combine within a family. A cup of flour and 120 g of flour cannot be
 * added together without knowing the density, so they stay separate shopping lines.
 */
enum class UnitFamily { VOLUME, WEIGHT, COUNT, INEXACT }

enum class UnitSystem { US, METRIC, NEUTRAL }

/**
 * [inBaseUnits] is expressed in millilitres for volume, grams for weight and
 * whole items for count, which is what lets quantities be summed.
 */
enum class MeasurementUnit(
    val label: String,
    val pluralLabel: String,
    val family: UnitFamily,
    val system: UnitSystem,
    val inBaseUnits: Double,
    vararg val aliases: String,
) {
    TEASPOON("tsp", "tsp", UnitFamily.VOLUME, UnitSystem.US, 4.92892, "tsps", "teaspoon", "teaspoons"),
    TABLESPOON("tbsp", "tbsp", UnitFamily.VOLUME, UnitSystem.US, 14.78676, "tbsps", "tbs", "tablespoon", "tablespoons"),
    FLUID_OUNCE("fl oz", "fl oz", UnitFamily.VOLUME, UnitSystem.US, 29.5735, "floz", "fluid ounce", "fluid ounces"),
    CUP("cup", "cups", UnitFamily.VOLUME, UnitSystem.US, 236.588, "c", "cups"),
    PINT("pint", "pints", UnitFamily.VOLUME, UnitSystem.US, 473.176, "pt", "pints"),
    QUART("quart", "quarts", UnitFamily.VOLUME, UnitSystem.US, 946.353, "qt", "quarts"),
    GALLON("gallon", "gallons", UnitFamily.VOLUME, UnitSystem.US, 3785.41, "gal", "gallons"),

    MILLILITER("ml", "ml", UnitFamily.VOLUME, UnitSystem.METRIC, 1.0, "milliliter", "milliliters", "millilitre", "millilitres"),
    LITER("l", "l", UnitFamily.VOLUME, UnitSystem.METRIC, 1000.0, "liter", "liters", "litre", "litres"),

    OUNCE("oz", "oz", UnitFamily.WEIGHT, UnitSystem.US, 28.3495, "ounce", "ounces", "0z"),
    // "ibs" and "1bs" are not typos a person makes; they are how "lbs" comes back
    // from text read off a printed page, where l, I and 1 are easily confused.
    POUND("lb", "lbs", UnitFamily.WEIGHT, UnitSystem.US, 453.592, "lbs", "pound", "pounds", "ibs", "1bs"),

    GRAM("g", "g", UnitFamily.WEIGHT, UnitSystem.METRIC, 1.0, "gram", "grams"),
    KILOGRAM("kg", "kg", UnitFamily.WEIGHT, UnitSystem.METRIC, 1000.0, "kilogram", "kilograms"),

    COUNT("", "", UnitFamily.COUNT, UnitSystem.NEUTRAL, 1.0, "each", "ea", "whole"),
    DOZEN("dozen", "dozen", UnitFamily.COUNT, UnitSystem.NEUTRAL, 12.0, "doz"),

    PINCH("pinch", "pinches", UnitFamily.INEXACT, UnitSystem.NEUTRAL, 1.0, "pinches"),
    DASH("dash", "dashes", UnitFamily.INEXACT, UnitSystem.NEUTRAL, 1.0, "dashes"),
    TO_TASTE("to taste", "to taste", UnitFamily.INEXACT, UnitSystem.NEUTRAL, 1.0, "as needed"),
    ;

    /** Inexact units carry no arithmetic meaning, so they are never summed. */
    val isAggregatable: Boolean get() = family != UnitFamily.INEXACT

    companion object {
        private val byToken: Map<String, MeasurementUnit> = buildMap {
            MeasurementUnit.entries.forEach { unit ->
                if (unit.label.isNotEmpty()) put(unit.label.lowercase(), unit)
                unit.aliases.forEach { put(it.lowercase(), unit) }
            }
        }

        /** Longest alias measured in words, so the parser knows how far to look ahead. */
        val maxTokenWords: Int = byToken.keys.maxOf { it.split(" ").size }

        fun fromToken(token: String): MeasurementUnit? = byToken[token.trim().lowercase()]

        /**
         * Ladders are ordered smallest first and paired with the exclusive upper bound
         * (in base units) at which the next unit up takes over. They are tuned for how
         * a shopping list actually reads: "3/4 cup" beats "12 tbsp".
         */
        private fun ladderFor(family: UnitFamily, system: UnitSystem): List<Pair<MeasurementUnit, Double>> =
            when (family) {
                UnitFamily.VOLUME -> if (system == UnitSystem.METRIC) {
                    listOf(MILLILITER to 1000.0, LITER to Double.MAX_VALUE)
                } else {
                    listOf(
                        TEASPOON to 3 * TEASPOON.inBaseUnits,
                        TABLESPOON to 4 * TABLESPOON.inBaseUnits,
                        CUP to 4 * CUP.inBaseUnits,
                        QUART to 4 * QUART.inBaseUnits,
                        GALLON to Double.MAX_VALUE,
                    )
                }

                UnitFamily.WEIGHT -> if (system == UnitSystem.METRIC) {
                    listOf(GRAM to 1000.0, KILOGRAM to Double.MAX_VALUE)
                } else {
                    listOf(OUNCE to POUND.inBaseUnits, POUND to Double.MAX_VALUE)
                }

                UnitFamily.COUNT -> listOf(COUNT to Double.MAX_VALUE)
                UnitFamily.INEXACT -> listOf(TO_TASTE to Double.MAX_VALUE)
            }

        /**
         * Picks the unit a cook would reach for when writing down [baseAmount].
         *
         * The ladder chooses by magnitude, but magnitude alone can land on a
         * measure nobody owns: 88 ml is "3/8 cup" by size and "6 tbsp" in a real
         * kitchen. So when the ladder's pick misses the fractions people can
         * actually measure, the next unit down is used instead.
         */
        fun bestFit(baseAmount: Double, family: UnitFamily, system: UnitSystem): MeasurementUnit {
            val ladder = ladderFor(family, system)
            val index = ladder.indexOfFirst { baseAmount < it.second }.coerceAtLeast(0)
            val chosen = ladder[index].first
            if (readsCleanly(baseAmount / chosen.inBaseUnits)) return chosen

            val smaller = ladder.getOrNull(index - 1)?.first ?: return chosen
            val steppedDown = baseAmount / smaller.inBaseUnits
            // Only worth it while the smaller unit stays countable: 6 tbsp beats
            // 3/8 cup, but 17 1/2 tbsp is far worse than the cup it replaced.
            if (steppedDown > MAX_STEP_DOWN) return chosen
            return if (readsCleanly(steppedDown)) smaller else chosen
        }

        /** Whole numbers, and the fractions a measuring cup actually has. */
        private fun readsCleanly(value: Double): Boolean {
            val fraction = value - floor(value)
            if (fraction < FRACTION_TOLERANCE || fraction > 1 - FRACTION_TOLERANCE) return true
            return MEASURABLE_FRACTIONS.any { abs(it - fraction) < FRACTION_TOLERANCE }
        }
    }
}

/**
 * An amount paired with its unit. A null [amount] means the recipe never gave one
 * ("salt, to taste"), which survives all the way to the shopping list.
 */
data class Quantity(val amount: Double?, val unit: MeasurementUnit) {

    val baseAmount: Double? get() = amount?.let { it * unit.inBaseUnits }

    fun scaledBy(factor: Double): Quantity = copy(amount = amount?.times(factor))

    /** Re-expresses this quantity in whichever unit of its family reads most naturally. */
    fun humanized(): Quantity {
        val base = baseAmount ?: return this
        if (!unit.isAggregatable) return this
        return fromBase(base, unit.family, unit.system)
    }

    fun format(): String {
        val value = amount
        // "to taste" has no number; "1 pinch" does, and both are inexact.
        if (!unit.isAggregatable) {
            return if (value == null) unit.label else "${formatCookingAmount(value)} ${labelFor(value)}"
        }
        if (value == null) return "as needed"
        val number = formatCookingAmount(value)
        val label = labelFor(value)
        return if (label.isEmpty()) number else "$number $label"
    }

    private fun labelFor(value: Double): String =
        if (value > 1.0 + FRACTION_TOLERANCE) unit.pluralLabel else unit.label

    companion object {
        /**
         * Expresses [baseAmount] in the unit of its family that reads best.
         *
         * Counts round up: half an onion is not something you can buy, and one
         * spare is a smaller problem than one short.
         */
        fun fromBase(baseAmount: Double, family: UnitFamily, system: UnitSystem): Quantity {
            val unit = MeasurementUnit.bestFit(baseAmount, family, system)
            val amount = baseAmount / unit.inBaseUnits
            return Quantity(if (family == UnitFamily.COUNT) ceil(amount) else amount, unit)
        }
    }
}

/**
 * How far from a nice fraction a value may sit and still be written as one.
 *
 * Amounts typed into a recipe land on a fraction exactly, so this only bites on
 * summed shopping-list totals - where 1.31 cups is better read as "1 1/3 cups"
 * than reported to a precision nobody can measure.
 */
private const val FRACTION_TOLERANCE = 0.04

/** Past this many of the smaller unit, the bigger one reads better even as a decimal. */
private const val MAX_STEP_DOWN = 8.0

/** Fractions a measuring cup or spoon actually offers. */
private val MEASURABLE_FRACTIONS = listOf(1.0 / 4, 1.0 / 3, 1.0 / 2, 2.0 / 3, 3.0 / 4)

/** Fractions a cook would actually write, including thirds and sixths. */
private val COOKING_FRACTIONS: List<Pair<Double, String>> = listOf(
    1.0 / 8 to "1/8",
    1.0 / 6 to "1/6",
    1.0 / 4 to "1/4",
    1.0 / 3 to "1/3",
    3.0 / 8 to "3/8",
    1.0 / 2 to "1/2",
    5.0 / 8 to "5/8",
    2.0 / 3 to "2/3",
    3.0 / 4 to "3/4",
    5.0 / 6 to "5/6",
    7.0 / 8 to "7/8",
)

/**
 * Renders 1.5 as "1 1/2" and 0.333 as "1/3", falling back to a decimal when the
 * value does not land near any fraction worth reading.
 */
fun formatCookingAmount(value: Double): String {
    if (value < 0) return trimDecimal(value)

    var whole = floor(value).toInt()
    var fraction = value - whole

    // Something like 1.995 should read as "2", not "1 199/200".
    if (fraction > 1 - FRACTION_TOLERANCE) {
        whole += 1
        fraction = 0.0
    }
    if (fraction < FRACTION_TOLERANCE) return whole.toString()

    val match = COOKING_FRACTIONS.minByOrNull { abs(it.first - fraction) }
        ?.takeIf { abs(it.first - fraction) < FRACTION_TOLERANCE }
        ?: return trimDecimal(value)

    return if (whole == 0) match.second else "$whole ${match.second}"
}

private fun trimDecimal(value: Double): String {
    val rounded = round(value * 100) / 100
    return if (rounded == floor(rounded)) rounded.toInt().toString() else rounded.toString()
}
