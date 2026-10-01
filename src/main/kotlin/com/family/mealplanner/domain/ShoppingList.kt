package com.family.mealplanner.domain

import java.time.LocalDate
import java.util.UUID

data class ShoppingItem(
    val id: UUID,
    val ingredientId: UUID?,
    val label: String,
    val quantity: Quantity,
    val category: IngredientCategory,
    val checked: Boolean,
    val manual: Boolean,
    val position: Int,
) {
    fun displayQuantity(): String = quantity.format()
}

data class ShoppingList(
    val weekStart: LocalDate,
    val items: List<ShoppingItem>,
) {
    /** Grouped by aisle and ordered the way the store is walked. */
    val byCategory: List<Pair<IngredientCategory, List<ShoppingItem>>>
        get() = items
            .groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> category.ordinal }
            .map { (category, group) -> category to group.sortedBy { it.label.lowercase() } }

    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    val remainingCount: Int get() = items.count { !it.checked }
    val isEmpty: Boolean get() = items.isEmpty()
}
