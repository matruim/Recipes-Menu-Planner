package com.family.mealplanner.repository

import com.family.mealplanner.db.IngredientsTable
import com.family.mealplanner.db.PlannedMealsTable
import com.family.mealplanner.db.RecipeIngredientsTable
import com.family.mealplanner.db.RecipesTable
import com.family.mealplanner.db.ShoppingListItemsTable
import com.family.mealplanner.db.dbQuery
import com.family.mealplanner.db.toDbDecimal
import com.family.mealplanner.db.toQuantity
import com.family.mealplanner.db.unitFromDb
import com.family.mealplanner.domain.IngredientCategory
import com.family.mealplanner.domain.ShoppingItem
import com.family.mealplanner.domain.ShoppingList
import com.family.mealplanner.domain.parseIngredientLine
import com.family.mealplanner.service.IngredientDemand
import com.family.mealplanner.service.ShoppingListAggregator
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID

/** Shopping happens a week at a time, even though dinners are planned by the month. */
class ShoppingListRepository {

    suspend fun load(weekStart: LocalDate): ShoppingList = dbQuery { loadList(weekStart) }

    /**
     * Rebuilds the generated lines from the dinners planned that week.
     *
     * Hand-added lines are left alone, and anything already ticked off stays
     * ticked so regenerating mid-shop does not lose your place.
     */
    suspend fun regenerate(weekStart: LocalDate): ShoppingList = dbQuery {
        val previouslyChecked = ShoppingListItemsTable.selectAll()
            .where {
                (ShoppingListItemsTable.weekStart eq weekStart) and
                    (ShoppingListItemsTable.manual eq false)
            }
            .mapNotNull { row ->
                val ingredientId = row[ShoppingListItemsTable.ingredientId]?.value ?: return@mapNotNull null
                val family = unitFromDb(row[ShoppingListItemsTable.unit]).family
                if (row[ShoppingListItemsTable.checked]) (ingredientId to family) else null
            }
            .toSet()

        ShoppingListItemsTable.deleteWhere {
            (ShoppingListItemsTable.weekStart eq weekStart) and
                (ShoppingListItemsTable.manual eq false)
        }

        val manualCount = ShoppingListItemsTable.selectAll()
            .where { ShoppingListItemsTable.weekStart eq weekStart }
            .count()
            .toInt()

        ShoppingListAggregator.aggregate(demandsFor(weekStart)).forEachIndexed { index, line ->
            ShoppingListItemsTable.insert {
                it[ShoppingListItemsTable.weekStart] = weekStart
                it[ingredientId] = line.ingredientId
                it[label] = line.name
                it[quantity] = line.quantity.amount.toDbDecimal()
                it[unit] = line.quantity.unit.name
                it[category] = line.category.name
                it[checked] = (line.ingredientId to line.quantity.unit.family) in previouslyChecked
                it[manual] = false
                it[position] = manualCount + index
            }
        }

        loadList(weekStart)
    }

    suspend fun toggle(itemId: UUID): Boolean = dbQuery {
        val current = ShoppingListItemsTable.selectAll()
            .where { ShoppingListItemsTable.id eq itemId }
            .singleOrNull()
            ?.get(ShoppingListItemsTable.checked)
            ?: return@dbQuery false
        ShoppingListItemsTable.update({ ShoppingListItemsTable.id eq itemId }) {
            it[checked] = !current
        } > 0
    }

    suspend fun addManualItem(weekStart: LocalDate, rawText: String): Boolean = dbQuery {
        val parsed = parseIngredientLine(rawText) ?: return@dbQuery false
        val nextPosition = ShoppingListItemsTable.selectAll()
            .where { ShoppingListItemsTable.weekStart eq weekStart }
            .count()
            .toInt()

        ShoppingListItemsTable.insert {
            it[ShoppingListItemsTable.weekStart] = weekStart
            it[ingredientId] = null
            it[label] = if (parsed.note.isBlank()) parsed.name else "${parsed.name}, ${parsed.note}"
            it[quantity] = parsed.quantity.amount.toDbDecimal()
            it[unit] = parsed.quantity.unit.name
            it[category] = IngredientCategory.guessFrom(parsed.name).name
            it[checked] = false
            it[manual] = true
            it[position] = nextPosition
        }
        true
    }

    suspend fun removeItem(itemId: UUID): Boolean = dbQuery {
        ShoppingListItemsTable.deleteWhere { ShoppingListItemsTable.id eq itemId } > 0
    }

    suspend fun clearChecked(weekStart: LocalDate): Int = dbQuery {
        ShoppingListItemsTable.deleteWhere {
            (ShoppingListItemsTable.weekStart eq weekStart) and
                (ShoppingListItemsTable.checked eq true)
        }
    }

    suspend fun uncheckAll(weekStart: LocalDate): Int = dbQuery {
        ShoppingListItemsTable.update({ ShoppingListItemsTable.weekStart eq weekStart }) {
            it[checked] = false
        }
    }

    /** Every ingredient that week's dinners call for, scaled to the servings planned. */
    private fun JdbcTransaction.demandsFor(weekStart: LocalDate): List<IngredientDemand> {
        val weekEnd = weekStart.plusDays(6)
        return PlannedMealsTable
            .join(RecipesTable, JoinType.INNER, PlannedMealsTable.recipeId, RecipesTable.id)
            .join(RecipeIngredientsTable, JoinType.INNER, RecipesTable.id, RecipeIngredientsTable.recipeId)
            .join(IngredientsTable, JoinType.INNER, RecipeIngredientsTable.ingredientId, IngredientsTable.id)
            .selectAll()
            .where {
                (PlannedMealsTable.mealDate greaterEq weekStart) and
                    (PlannedMealsTable.mealDate lessEq weekEnd)
            }
            .map { row ->
                val recipeServings = row[RecipesTable.servings]
                val plannedServings = row[PlannedMealsTable.servings]
                val scale = if (recipeServings > 0) plannedServings.toDouble() / recipeServings else 1.0
                IngredientDemand(
                    ingredientId = row[IngredientsTable.id].value,
                    name = row[IngredientsTable.name],
                    category = IngredientCategory.fromDb(row[IngredientsTable.category]),
                    quantity = row.toQuantity(RecipeIngredientsTable.quantity, RecipeIngredientsTable.unit)
                        .scaledBy(scale),
                )
            }
    }

    private fun loadList(weekStart: LocalDate): ShoppingList {
        val items = ShoppingListItemsTable.selectAll()
            .where { ShoppingListItemsTable.weekStart eq weekStart }
            .orderBy(ShoppingListItemsTable.position to SortOrder.ASC)
            .map { row ->
                ShoppingItem(
                    id = row[ShoppingListItemsTable.id].value,
                    ingredientId = row[ShoppingListItemsTable.ingredientId]?.value,
                    label = row[ShoppingListItemsTable.label],
                    quantity = row.toQuantity(ShoppingListItemsTable.quantity, ShoppingListItemsTable.unit),
                    category = IngredientCategory.fromDb(row[ShoppingListItemsTable.category]),
                    checked = row[ShoppingListItemsTable.checked],
                    manual = row[ShoppingListItemsTable.manual],
                    position = row[ShoppingListItemsTable.position],
                )
            }
        return ShoppingList(weekStart, items)
    }
}
