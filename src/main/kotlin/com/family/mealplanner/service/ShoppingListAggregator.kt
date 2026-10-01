package com.family.mealplanner.service

import com.family.mealplanner.domain.IngredientCategory
import com.family.mealplanner.domain.MeasurementUnit
import com.family.mealplanner.domain.Quantity
import com.family.mealplanner.domain.UnitFamily
import com.family.mealplanner.domain.UnitSystem
import java.util.UUID

/** One recipe's call for an ingredient, already scaled to the planned servings. */
data class IngredientDemand(
    val ingredientId: UUID,
    val name: String,
    val category: IngredientCategory,
    val quantity: Quantity,
)

/** One line on the finished shopping list. */
data class AggregatedLine(
    val ingredientId: UUID,
    val name: String,
    val category: IngredientCategory,
    val quantity: Quantity,
)

/**
 * Rolls every planned recipe's ingredients into one line per ingredient.
 *
 * Quantities only merge inside a unit family, so "1 cup flour" and "120 g flour"
 * stay as two lines rather than being silently combined with a guessed density.
 */
object ShoppingListAggregator {

    fun aggregate(demands: List<IngredientDemand>): List<AggregatedLine> =
        demands
            .groupBy { groupKey(it) }
            .map { (_, group) -> combine(group) }
            .sortedWith(compareBy({ it.category.ordinal }, { it.name.lowercase() }))

    /**
     * Inexact units are not interchangeable with each other, so pinches merge only
     * with pinches and "to taste" only with "to taste".
     */
    private fun groupKey(demand: IngredientDemand): Triple<UUID, UnitFamily, String> {
        val unit = demand.quantity.unit
        val inexactDiscriminator = if (unit.family == UnitFamily.INEXACT) unit.name else ""
        return Triple(demand.ingredientId, unit.family, inexactDiscriminator)
    }

    private fun combine(group: List<IngredientDemand>): AggregatedLine {
        val first = group.first()
        val family = first.quantity.unit.family
        val quantity =
            if (family == UnitFamily.INEXACT) sumInexact(group) else sumOf(family, group)
        return AggregatedLine(first.ingredientId, first.name, first.category, quantity)
    }

    /** Keeps the unit as-is; "to taste" stays numberless, pinches add up. */
    private fun sumInexact(group: List<IngredientDemand>): Quantity {
        val amounts = group.mapNotNull { it.quantity.amount }
        return Quantity(amounts.takeIf { it.isNotEmpty() }?.sum(), group.first().quantity.unit)
    }

    private fun sumOf(family: UnitFamily, group: List<IngredientDemand>): Quantity {
        val measured = group.mapNotNull { demand ->
            demand.quantity.baseAmount?.let { it to demand.quantity.unit.system }
        }
        // Every mention was unmeasured, e.g. "salt" with no amount given.
        if (measured.isEmpty()) return Quantity(null, group.first().quantity.unit)

        val total = measured.sumOf { it.first }
        return Quantity.fromBase(total, family, dominantSystem(measured))
    }

    /**
     * If a week's recipes mix metric and US measures for one ingredient, the list is
     * written in whichever system accounts for most of the volume or weight.
     */
    private fun dominantSystem(measured: List<Pair<Double, UnitSystem>>): UnitSystem =
        measured
            .groupBy { it.second }
            .mapValues { (_, values) -> values.sumOf { it.first } }
            .maxByOrNull { it.value }
            ?.key
            ?: UnitSystem.US
}
