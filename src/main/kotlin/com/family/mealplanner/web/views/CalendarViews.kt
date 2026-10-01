package com.family.mealplanner.web.views

import com.family.mealplanner.domain.PlannedMeal
import com.family.mealplanner.domain.PlannedMonth
import com.family.mealplanner.domain.Recipe
import com.family.mealplanner.domain.weekStart
import kotlinx.html.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

fun MAIN.calendarPage(month: PlannedMonth, today: LocalDate) {
    div("page-head") {
        div {
            h1 { +month.month.format(MONTH_TITLE) }
            p("subtitle") {
                +if (month.isEmpty) {
                    "No dinners planned yet"
                } else {
                    "${month.mealCount} dinners planned - shop a week at a time"
                }
            }
        }
        div("toolbar") {
            a(href = "/plan?month=${month.month.minusMonths(1)}", classes = "btn") { +"← ${
                month.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }" }
            a(href = "/plan", classes = "btn") { +"This month" }
            a(href = "/plan?month=${month.month.plusMonths(1)}", classes = "btn") { +"${
                month.month.plusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } →" }
            button(classes = "btn") {
                hxPost = "/plan/${month.month}/copy-previous"
                hxTarget = "#month-grid"
                hxSwap = "outerHTML"
                +"Repeat last month"
            }
            a(href = "/plan/print?month=${month.month}", classes = "btn") { +"Print" }
            if (!month.isEmpty) {
                button(classes = "btn btn-danger") {
                    hxDelete = "/plan/${month.month}/meals"
                    hxTarget = "#month-grid"
                    hxSwap = "outerHTML"
                    hxConfirm = "Remove every dinner planned in ${month.month.format(MONTH_TITLE)}?"
                    +"Clear month"
                }
            }
        }
    }
    monthGrid(month, today)
}

fun FlowContent.monthGrid(month: PlannedMonth, today: LocalDate) {
    div {
        id = "month-grid"
        monthGridBody(month, today)
    }
}

fun monthGridFragment(month: PlannedMonth, today: LocalDate): String = fragment {
    div {
        id = "month-grid"
        monthGridBody(month, today)
    }
}

/** Clears the picker as a side effect of any calendar update. */
fun closeDialogFragment(): String = fragment {
    div {
        id = "dialog"
        hxSwapOob = "true"
    }
}

private fun FlowContent.monthGridBody(month: PlannedMonth, today: LocalDate) {
    div("month-head") {
        DayOfWeek.entries.forEach { day ->
            div { +day.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        div { +"" }
    }
    month.weeks.forEach { week ->
        div("month-week") {
            week.forEach { date -> dayCell(month, date, today) }
            weekShopCell(month, week)
        }
    }
}

private fun FlowContent.dayCell(month: PlannedMonth, date: LocalDate, today: LocalDate) {
    val classes = buildList {
        add("day-cell")
        if (!month.isInMonth(date)) add("outside")
        if (date == today) add("today")
    }.joinToString(" ")

    div(classes) {
        div("day-num") {
            span { +date.dayOfMonth.toString() }
            span("day-dow") {
                +date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        }
        month.mealsOn(date).forEach { mealChip(it) }
        button(classes = "add-meal") {
            hxGet = "/plan/picker?date=$date"
            hxTarget = "#dialog"
            title = "Add a dinner for ${date.format(DAY_TITLE)}"
            +"+"
        }
    }
}

private fun FlowContent.mealChip(meal: PlannedMeal) {
    div("meal-chip") {
        div("title") {
            // Carry the planned servings through so the recipe opens showing the
            // quantities for this night, not the recipe's written yield.
            a(href = "/recipes/${meal.recipeId}?servings=${meal.servings}") { +meal.recipeTitle }
        }
        div("meta") {
            input(type = InputType.number, name = "servings") {
                value = meal.servings.toString()
                min = "1"
                max = "99"
                title = "Servings"
                hxPut = "/plan/meals/${meal.id}/servings"
                hxTrigger = "change"
                hxTarget = "#month-grid"
                hxSwap = "outerHTML"
            }
            button(classes = "btn btn-danger btn-sm") {
                hxDelete = "/plan/meals/${meal.id}"
                hxTarget = "#month-grid"
                hxSwap = "outerHTML"
                title = "Remove ${meal.recipeTitle}"
                +"✕"
            }
        }
    }
}

/** Each calendar row is a whole week, which is also the shopping window. */
private fun FlowContent.weekShopCell(month: PlannedMonth, week: List<LocalDate>) {
    val count = month.mealCountIn(week)
    div("week-shop") {
        a(href = "/shopping-list?week=${week.first()}", classes = "btn btn-sm") { +"Shop" }
        span("count") { +if (count == 1) "1 dinner" else "$count dinners" }
    }
}

fun pickerDialogFragment(date: LocalDate, recipes: List<Recipe>, query: String): String = fragment {
    div("dialog-backdrop") {
        onClick = "if (event.target === this) this.remove()"
        div("dialog") {
            div("dialog-head") {
                div {
                    h2 { +"Add a dinner" }
                    p("subtitle") { +date.format(DAY_TITLE) }
                }
                button(classes = "btn btn-ghost") {
                    onClick = "this.closest('.dialog-backdrop').remove()"
                    +"✕"
                }
            }
            div("dialog-body") {
                input(type = InputType.search, name = "q") {
                    placeholder = "Search recipes..."
                    value = query
                    autoFocus = true
                    hxGet = "/plan/picker/results?date=$date"
                    hxTrigger = "input changed delay:250ms, search"
                    hxTarget = "#picker-results"
                    hxSwap = "outerHTML"
                }
                ul("picker-list") {
                    id = "picker-results"
                    pickerItems(date, recipes)
                }
            }
        }
    }
}

fun pickerResultsFragment(date: LocalDate, recipes: List<Recipe>): String = fragment {
    ul("picker-list") {
        id = "picker-results"
        pickerItems(date, recipes)
    }
}

private fun UL.pickerItems(date: LocalDate, recipes: List<Recipe>) {
    if (recipes.isEmpty()) {
        li {
            p("subtitle") { +"No recipes match. " }
            a(href = "/recipes/new") { +"Add a recipe" }
        }
        return
    }
    recipes.forEach { recipe ->
        li {
            button {
                hxPost = "/plan/meals"
                hxVals = """{"recipeId":"${recipe.id}","date":"$date"}"""
                hxTarget = "#month-grid"
                hxSwap = "outerHTML"
                recipeThumb(recipe.imageFile, recipe.imageUrl, recipe.title, "picker-thumb")
                div("title") { +recipe.title }
                div("meta") {
                    +"Serves ${recipe.servings}"
                    if (recipe.totalMinutes > 0) +" · ${recipe.totalMinutes} min"
                }
            }
        }
    }
}

/** Month a calendar URL refers to, falling back to the current one. */
fun monthOrCurrent(raw: String?, today: LocalDate): YearMonth =
    raw?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(today)

/** Monday of the week a shopping URL refers to. */
fun weekOrCurrent(raw: String?, today: LocalDate): LocalDate =
    (raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today).weekStart()
