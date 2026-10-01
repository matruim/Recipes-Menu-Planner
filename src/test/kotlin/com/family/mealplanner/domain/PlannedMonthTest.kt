package com.family.mealplanner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlannedMonthTest {

    private fun meal(date: String, title: String = "Dinner", servings: Int = 4) = PlannedMeal(
        id = UUID.randomUUID(),
        date = LocalDate.parse(date),
        recipeId = UUID.randomUUID(),
        recipeTitle = title,
        recipeServings = 4,
        servings = servings,
    )

    private fun monthOf(month: String, meals: List<PlannedMeal> = emptyList()) =
        PlannedMonth(YearMonth.parse(month), meals)

    @Test
    fun `weeks are whole Monday to Sunday rows`() {
        monthOf("2026-09").weeks.forEach { week ->
            assertEquals(7, week.size)
            assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        }
    }

    @Test
    fun `calendar rows cover every day of the month`() {
        val month = YearMonth.parse("2026-09")
        val covered = monthOf("2026-09").weeks.flatten().toSet()
        (1..month.lengthOfMonth()).forEach { day ->
            assertTrue(month.atDay(day) in covered, "day $day missing from the calendar")
        }
    }

    @Test
    fun `rows spill into neighbouring months so every row is a full week`() {
        // September 2026 starts on a Tuesday, so the first row reaches back to Aug 31.
        val weeks = monthOf("2026-09").weeks
        assertEquals(LocalDate.parse("2026-08-31"), weeks.first().first())
        assertEquals(LocalDate.parse("2026-10-04"), weeks.last().last())
    }

    @Test
    fun `a month starting on Monday needs no leading spill`() {
        val weeks = monthOf("2026-06").weeks
        assertEquals(LocalDate.parse("2026-06-01"), weeks.first().first())
        assertEquals(DayOfWeek.MONDAY, LocalDate.parse("2026-06-01").dayOfWeek)
    }

    @Test
    fun `days outside the month are flagged`() {
        val month = monthOf("2026-09")
        assertFalse(month.isInMonth(LocalDate.parse("2026-08-31")))
        assertTrue(month.isInMonth(LocalDate.parse("2026-09-01")))
        assertFalse(month.isInMonth(LocalDate.parse("2026-10-01")))
    }

    @Test
    fun `meals are grouped by date and several may share a night`() {
        val month = monthOf(
            "2026-09",
            listOf(
                meal("2026-09-30", "Tomato Soup"),
                meal("2026-09-30", "Greek Salad"),
                meal("2026-09-28", "Pasta"),
            ),
        )
        assertEquals(
            listOf("Tomato Soup", "Greek Salad"),
            month.mealsOn(LocalDate.parse("2026-09-30")).map { it.recipeTitle },
        )
        assertEquals(1, month.mealsOn(LocalDate.parse("2026-09-28")).size)
        assertTrue(month.mealsOn(LocalDate.parse("2026-09-29")).isEmpty())
    }

    @Test
    fun `the month total ignores spill-over days`() {
        // Sep 28-30 show in October's first calendar row but are September's meals.
        val month = monthOf(
            "2026-10",
            listOf(meal("2026-09-28"), meal("2026-09-30"), meal("2026-10-26")),
        )
        assertEquals(1, month.mealCount)
        assertFalse(month.isEmpty)

        val spillOnly = monthOf("2026-10", listOf(meal("2026-09-28")))
        assertEquals(0, spillOnly.mealCount)
        assertTrue(spillOnly.isEmpty)
        // The row still shows it, because that week is shopped as a whole.
        assertEquals(1, spillOnly.mealCountIn(spillOnly.weeks.first()))
    }

    @Test
    fun `a week row counts the spill-over days it contains`() {
        // Oct 1 falls in September's last calendar row, and you shop for it that week.
        val month = monthOf(
            "2026-09",
            listOf(meal("2026-09-28"), meal("2026-10-01")),
        )
        val lastRow = month.weeks.last()
        assertEquals(2, month.mealCountIn(lastRow))
    }

    @Test
    fun `a week lists all seven days even when empty`() {
        val week = PlannedWeek(LocalDate.parse("2026-10-05"), emptyList())
        assertEquals(7, week.days.size)
        assertEquals(LocalDate.parse("2026-10-05"), week.days.first())
        assertEquals(LocalDate.parse("2026-10-11"), week.days.last())
        assertEquals(week.weekEnd, week.days.last())
        // The printed sheet still shows a row per day.
        assertTrue(week.days.all { week.mealsOn(it).isEmpty() })
    }

    @Test
    fun `a week groups its dinners by date`() {
        val week = PlannedWeek(
            LocalDate.parse("2026-10-05"),
            listOf(
                meal("2026-10-05", "Greek Salad"),
                meal("2026-10-07", "Tomato Soup"),
                meal("2026-10-07", "Garlic Bread"),
            ),
        )
        assertEquals(listOf("Greek Salad"), week.mealsOn(LocalDate.parse("2026-10-05")).map { it.recipeTitle })
        assertEquals(2, week.mealsOn(LocalDate.parse("2026-10-07")).size)
        assertTrue(week.mealsOn(LocalDate.parse("2026-10-06")).isEmpty())
        assertEquals(3, week.mealCount)
    }

    @Test
    fun `week start snaps back to Monday`() {
        assertEquals(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-09-30").weekStart())
        assertEquals(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-09-28").weekStart())
        assertEquals(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04").weekStart())
    }

    @Test
    fun `repeating a month keeps each dinner on its weekday`() {
        val sep = YearMonth.parse("2026-09")
        val oct = YearMonth.parse("2026-10")
        // Sep 28 is a Monday; it must land on a Monday in October, not Oct 28.
        listOf("2026-09-28", "2026-09-29", "2026-09-30").forEach { raw ->
            val source = LocalDate.parse(raw)
            val target = requireNotNull(repeatedInto(source, sep, oct))
            assertEquals(source.dayOfWeek, target.dayOfWeek, "weekday moved for $raw")
            assertEquals(oct, YearMonth.from(target))
        }
        assertEquals(LocalDate.parse("2026-10-26"), repeatedInto(LocalDate.parse("2026-09-28"), sep, oct))
    }

    @Test
    fun `repeating keeps the position of a dinner within the month`() {
        val sep = YearMonth.parse("2026-09")
        val oct = YearMonth.parse("2026-10")
        // First calendar row of September maps to the first row of October.
        assertEquals(LocalDate.parse("2026-10-01"), repeatedInto(LocalDate.parse("2026-09-03"), sep, oct))
    }

    @Test
    fun `a dinner that would fall outside the target month is dropped`() {
        // Months do not line up evenly; bleeding into November would be worse.
        val sep = YearMonth.parse("2026-09")
        val nov = YearMonth.parse("2026-11")
        val overflow = (1..30)
            .map { LocalDate.parse("2026-09-%02d".format(it)) }
            .mapNotNull { repeatedInto(it, sep, nov) }
        assertTrue(overflow.all { YearMonth.from(it) == nov })
    }

    @Test
    fun `scale factor stretches a recipe to the servings planned`() {
        assertEquals(2.0, meal("2026-09-01", servings = 8).scaleFactor)
        assertEquals(1.0, meal("2026-09-01", servings = 4).scaleFactor)
    }
}
