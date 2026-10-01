package com.family.mealplanner.web.views

import com.family.mealplanner.domain.PlannedMeal
import com.family.mealplanner.domain.PlannedMonth
import com.family.mealplanner.domain.PlannedWeek
import kotlinx.html.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val RANGE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")
private val RANGE_LABEL_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

/** A month reads across the page, so the sheet is turned sideways. */
fun HEAD.landscapePage() = style { unsafe { +"@page { size: landscape; margin: 10mm; }" } }

fun HEAD.portraitPage() = style { unsafe { +"@page { size: portrait; margin: 14mm; }" } }

fun MAIN.printableMonth(month: PlannedMonth) {
    printToolbar(
        monthHref = "/plan/print?month=${month.month}",
        weekHref = "/plan/print?week=${LocalDate.now().let { today ->
            if (YearMonth.from(today) == month.month) today else month.month.atDay(1)
        }}",
        backHref = "/plan?month=${month.month}",
        active = "month",
    )

    h1("print-title") { +month.month.format(MONTH_TITLE) }

    div("print-month") {
        div("print-month-head") {
            DayOfWeek.entries.forEach { day ->
                div { +day.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            }
        }
        month.weeks.forEach { week ->
            div("print-month-week") {
                week.forEach { date ->
                    div(classes = if (month.isInMonth(date)) "print-day" else "print-day outside") {
                        div("print-day-num") { +date.dayOfMonth.toString() }
                        month.mealsOn(date).forEach { meal ->
                            div("print-meal") { +meal.recipeTitle }
                        }
                    }
                }
            }
        }
    }
}

fun MAIN.printableWeek(week: PlannedWeek) {
    printToolbar(
        monthHref = "/plan/print?month=${YearMonth.from(week.weekStart)}",
        weekHref = "/plan/print?week=${week.weekStart}",
        backHref = "/plan?month=${YearMonth.from(week.weekStart)}",
        active = "week",
    )

    h1("print-title") {
        +"${week.weekStart.format(RANGE_LABEL)} – ${week.weekEnd.format(RANGE_LABEL_WITH_YEAR)}"
    }

    // A week has room to breathe, so each day gets its own row.
    div("print-week") {
        week.days.forEach { date ->
            div("print-week-row") {
                div("print-week-day") {
                    div("name") {
                        +date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    }
                    div("date") { +date.format(RANGE_LABEL) }
                }
                div("print-week-meals") {
                    val meals = week.mealsOn(date)
                    if (meals.isEmpty()) {
                        span("print-empty") { +"—" }
                    } else {
                        meals.forEach { meal -> printedMeal(meal) }
                    }
                }
            }
        }
    }
}

/** Servings are a planning detail; the printed sheet is just what to cook. */
private fun FlowContent.printedMeal(meal: PlannedMeal) {
    div("print-meal") { +meal.recipeTitle }
}

/** On screen only: choose the range, then print. */
private fun FlowContent.printToolbar(
    monthHref: String,
    weekHref: String,
    backHref: String,
    active: String,
) {
    div("print-toolbar no-print") {
        div("toolbar") {
            a(href = monthHref, classes = if (active == "month") "btn btn-primary" else "btn") {
                +"Month"
            }
            a(href = weekHref, classes = if (active == "week") "btn btn-primary" else "btn") {
                +"Week"
            }
        }
        div("toolbar") {
            button(classes = "btn btn-primary") {
                onClick = "window.print()"
                +"Print"
            }
            a(href = backHref, classes = "btn btn-ghost") { +"Back to calendar" }
        }
    }
}
