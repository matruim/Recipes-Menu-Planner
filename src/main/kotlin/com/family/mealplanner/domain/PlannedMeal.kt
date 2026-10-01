package com.family.mealplanner.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One dinner on one date. Several recipes may share a date. */
data class PlannedMeal(
    val id: UUID,
    val date: LocalDate,
    val recipeId: UUID,
    val recipeTitle: String,
    val recipeServings: Int,
    val servings: Int,
) {
    /** How far the recipe stretches to feed the number planned for. */
    val scaleFactor: Double get() = if (recipeServings > 0) servings.toDouble() / recipeServings else 1.0
}

/** The Monday starting the week a date falls in. Weeks are the shopping window. */
fun LocalDate.weekStart(): LocalDate = minusDays(dayOfWeek.ordinal.toLong())

/**
 * Where a dinner on [date] lands when month [from] is repeated into month [to].
 *
 * Matched by which calendar row it sat in and which weekday, not by day of the
 * month, so a Friday pizza stays on a Friday. Returns null when it would fall
 * outside [to] — months do not line up evenly, and bleeding into the following
 * month is worse than dropping the meal.
 */
fun repeatedInto(date: LocalDate, from: YearMonth, to: YearMonth): LocalDate? {
    val weekIndex = ChronoUnit.WEEKS.between(from.atDay(1).weekStart(), date.weekStart())
    val target = to.atDay(1).weekStart()
        .plusWeeks(weekIndex)
        .plusDays(date.dayOfWeek.ordinal.toLong())
    return target.takeIf { YearMonth.from(it) == to }
}

/** A month of dinners, laid out as whole Monday-to-Sunday calendar rows. */
data class PlannedMonth(
    val month: YearMonth,
    val meals: List<PlannedMeal>,
) {
    private val byDate: Map<LocalDate, List<PlannedMeal>> = meals.groupBy { it.date }

    fun mealsOn(date: LocalDate): List<PlannedMeal> = byDate[date].orEmpty()

    /**
     * Calendar rows covering the month. The first and last rows spill into the
     * neighbouring months so every row is a full shoppable week.
     */
    val weeks: List<List<LocalDate>>
        get() {
            val firstRow = month.atDay(1).weekStart()
            val lastRow = month.atEndOfMonth().weekStart()
            return generateSequence(firstRow) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(lastRow) }
                .map { start -> (0L..6L).map { start.plusDays(it) } }
                .toList()
        }

    /** Counts the whole row, spill-over days included, because you shop for the week. */
    fun mealCountIn(week: List<LocalDate>): Int = week.sumOf { mealsOn(it).size }

    /**
     * Only the dinners actually in this month. The spill-over days belong to the
     * neighbouring months' totals, and to their own "clear month".
     */
    val mealCount: Int get() = meals.count { isInMonth(it.date) }
    val isEmpty: Boolean get() = mealCount == 0

    fun isInMonth(date: LocalDate): Boolean = YearMonth.from(date) == month
}

/** One week's dinners, which is what a shopping list is built from. */
data class PlannedWeek(
    val weekStart: LocalDate,
    val meals: List<PlannedMeal>,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
    val isEmpty: Boolean get() = meals.isEmpty()
    val mealCount: Int get() = meals.size

    val days: List<LocalDate> get() = (0L..6L).map { weekStart.plusDays(it) }

    private val byDate: Map<LocalDate, List<PlannedMeal>> get() = meals.groupBy { it.date }

    fun mealsOn(date: LocalDate): List<PlannedMeal> = byDate[date].orEmpty()
}
