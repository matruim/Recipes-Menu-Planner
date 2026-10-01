package com.family.mealplanner.repository

import com.family.mealplanner.db.PlannedMealsTable
import com.family.mealplanner.db.RecipesTable
import com.family.mealplanner.db.dbQuery
import com.family.mealplanner.domain.PlannedMeal
import com.family.mealplanner.domain.PlannedMonth
import com.family.mealplanner.domain.PlannedWeek
import com.family.mealplanner.domain.repeatedInto
import com.family.mealplanner.domain.weekStart
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class PlannedMealRepository {

    /** The month plus the spill-over days that complete its first and last weeks. */
    suspend fun month(month: YearMonth): PlannedMonth = dbQuery {
        val from = month.atDay(1).weekStart()
        val to = month.atEndOfMonth().weekStart().plusDays(6)
        PlannedMonth(month, loadBetween(from, to))
    }

    suspend fun week(weekStart: LocalDate): PlannedWeek = dbQuery {
        PlannedWeek(weekStart, loadBetween(weekStart, weekStart.plusDays(6)))
    }

    suspend fun addMeal(date: LocalDate, recipeId: UUID, servings: Int?): Boolean = dbQuery {
        val recipeServings = RecipesTable.selectAll()
            .where { RecipesTable.id eq recipeId }
            .singleOrNull()
            ?.get(RecipesTable.servings)
            ?: return@dbQuery false

        val alreadyPlanned = PlannedMealsTable.selectAll().where {
            (PlannedMealsTable.mealDate eq date) and (PlannedMealsTable.recipeId eq recipeId)
        }.any()
        if (alreadyPlanned) return@dbQuery true

        val nextPosition = PlannedMealsTable.selectAll()
            .where { PlannedMealsTable.mealDate eq date }
            .count()
            .toInt()

        PlannedMealsTable.insert {
            it[mealDate] = date
            it[PlannedMealsTable.recipeId] = recipeId
            it[PlannedMealsTable.servings] = servings ?: recipeServings
            it[position] = nextPosition
        }
        true
    }

    suspend fun removeMeal(mealId: UUID): Boolean = dbQuery {
        PlannedMealsTable.deleteWhere { PlannedMealsTable.id eq mealId } > 0
    }

    suspend fun setServings(mealId: UUID, servings: Int): Boolean = dbQuery {
        PlannedMealsTable.update({ PlannedMealsTable.id eq mealId }) {
            it[PlannedMealsTable.servings] = servings.coerceIn(1, 99)
        } > 0
    }

    /** Looked up before a mutation so the right calendar can be re-rendered. */
    suspend fun dateOf(mealId: UUID): LocalDate? = dbQuery {
        PlannedMealsTable.selectAll()
            .where { PlannedMealsTable.id eq mealId }
            .singleOrNull()
            ?.get(PlannedMealsTable.mealDate)
    }

    suspend fun clearMonth(month: YearMonth): Int = dbQuery {
        PlannedMealsTable.deleteWhere {
            (mealDate greaterEq month.atDay(1)) and (mealDate lessEq month.atEndOfMonth())
        }
    }

    suspend fun clearWeek(weekStart: LocalDate): Int = dbQuery {
        PlannedMealsTable.deleteWhere {
            (mealDate greaterEq weekStart) and (mealDate lessEq weekStart.plusDays(6))
        }
    }

    /**
     * Repeats the previous month's dinners into this one.
     *
     * Meals are matched by which calendar row they sat in and which weekday, not
     * by day of the month, so a Friday pizza stays on a Friday. Anything that
     * would land outside the target month is dropped rather than bleeding into
     * the month after.
     */
    suspend fun copyPreviousMonth(month: YearMonth): Int = dbQuery {
        val previous = month.minusMonths(1)
        val source = loadBetween(previous.atDay(1), previous.atEndOfMonth())
        if (source.isEmpty()) return@dbQuery 0

        val existing = loadBetween(month.atDay(1), month.atEndOfMonth())
            .map { it.date to it.recipeId }
            .toSet()

        var copied = 0
        source.forEach { meal ->
            val target = repeatedInto(meal.date, previous, month) ?: return@forEach
            if ((target to meal.recipeId) in existing) return@forEach

            PlannedMealsTable.insert {
                it[mealDate] = target
                it[recipeId] = meal.recipeId
                it[servings] = meal.servings
                it[position] = 0
            }
            copied++
        }
        copied
    }

    private fun JdbcTransaction.loadBetween(from: LocalDate, to: LocalDate): List<PlannedMeal> =
        PlannedMealsTable
            .join(RecipesTable, JoinType.INNER, PlannedMealsTable.recipeId, RecipesTable.id)
            .selectAll()
            .where { (PlannedMealsTable.mealDate greaterEq from) and (PlannedMealsTable.mealDate lessEq to) }
            .orderBy(
                PlannedMealsTable.mealDate to SortOrder.ASC,
                PlannedMealsTable.position to SortOrder.ASC,
                RecipesTable.title to SortOrder.ASC,
            )
            .map { it.toPlannedMeal() }
}

fun ResultRow.toPlannedMeal(): PlannedMeal = PlannedMeal(
    id = this[PlannedMealsTable.id].value,
    date = this[PlannedMealsTable.mealDate],
    recipeId = this[PlannedMealsTable.recipeId].value,
    recipeTitle = this[RecipesTable.title],
    recipeServings = this[RecipesTable.servings],
    servings = this[PlannedMealsTable.servings],
)
