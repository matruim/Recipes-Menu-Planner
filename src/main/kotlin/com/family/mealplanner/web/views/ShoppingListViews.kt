package com.family.mealplanner.web.views

import com.family.mealplanner.domain.PlannedWeek
import com.family.mealplanner.domain.ShoppingItem
import com.family.mealplanner.domain.ShoppingList
import kotlinx.html.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")

fun MAIN.shoppingListPage(week: PlannedWeek, list: ShoppingList) {
    val start = week.weekStart
    div("page-head") {
        div {
            h1 { +"Shopping for ${start.format(WEEK_LABEL)} – ${week.weekEnd.format(WEEK_LABEL)}" }
            p("subtitle") {
                +if (week.isEmpty) {
                    "No dinners planned this week"
                } else {
                    "${week.mealCount} dinners planned"
                }
            }
        }
        div("toolbar") {
            a(href = "/shopping-list?week=${start.minusWeeks(1)}", classes = "btn") { +"← Previous week" }
            a(href = "/shopping-list", classes = "btn") { +"This week" }
            a(href = "/shopping-list?week=${start.plusWeeks(1)}", classes = "btn") { +"Next week →" }
            a(href = "/plan?month=${YearMonth.from(start)}", classes = "btn") { +"Calendar" }
        }
    }

    weekMealsCard(week)

    div("card stack") {
        div("toolbar") {
            button(classes = "btn btn-primary") {
                hxPost = "/shopping-list/$start/regenerate"
                hxTarget = "#shopping-list"
                hxSwap = "outerHTML"
                hxConfirm = "Rebuild the list from this week's dinners? Ticked items stay ticked."
                +"Rebuild from plan"
            }
            button(classes = "btn") {
                hxPost = "/shopping-list/$start/uncheck-all"
                hxTarget = "#shopping-list"
                hxSwap = "outerHTML"
                +"Uncheck all"
            }
            button(classes = "btn btn-danger") {
                hxPost = "/shopping-list/$start/clear-checked"
                hxTarget = "#shopping-list"
                hxSwap = "outerHTML"
                hxConfirm = "Remove every ticked item from the list?"
                +"Clear ticked"
            }
        }
        form(classes = "inline-form") {
            hxPost = "/shopping-list/$start/items"
            hxTarget = "#shopping-list"
            hxSwap = "outerHTML"
            hxOnAfterRequest = "this.reset()"
            div("field") {
                style = "flex:1;margin-bottom:0"
                label {
                    +"Add something else "
                    span("hint") { +"quantities are understood, e.g. \"2 lbs coffee\"" }
                }
                input(type = InputType.text, name = "text") {
                    placeholder = "Paper towels"
                    required = true
                }
            }
            button(type = ButtonType.submit, classes = "btn btn-primary") { +"Add" }
        }
    }

    shoppingListBlock(list)
}

/** A reminder of what the list is actually for, so the week can be sanity-checked. */
private fun FlowContent.weekMealsCard(week: PlannedWeek) {
    if (week.isEmpty) return
    div("card") {
        style = "margin-bottom:1rem"
        h2 { +"This week's dinners" }
        ul("week-meals") {
            week.meals.forEach { meal ->
                li {
                    span("day") { +meal.date.format(DAY_LABEL) }
                    a(href = "/recipes/${meal.recipeId}?servings=${meal.servings}") { +meal.recipeTitle }
                    span("badge") { +"${meal.servings} servings" }
                }
            }
        }
    }
}

fun FlowContent.shoppingListBlock(list: ShoppingList) {
    div("card") {
        id = "shopping-list"
        style = "margin-top:1rem"
        shoppingListBody(list)
    }
}

fun shoppingListFragment(list: ShoppingList): String = fragment {
    div("card") {
        id = "shopping-list"
        style = "margin-top:1rem"
        shoppingListBody(list)
    }
}

private fun FlowContent.shoppingListBody(list: ShoppingList) {
    if (list.isEmpty) {
        emptyState(
            "Nothing on the list yet.",
            "Plan some dinners for this week, then rebuild the list from the plan.",
        )
        return
    }

    p("progress") { +"${list.remainingCount} of ${list.items.size} still to buy" }

    list.byCategory.forEach { (category, items) ->
        div("shop-group") {
            h3 { +category.label }
            items.forEach { shoppingItemRow(list.weekStart, it) }
        }
    }
}

private fun FlowContent.shoppingItemRow(weekStart: LocalDate, item: ShoppingItem) {
    div(classes = if (item.checked) "shop-item checked" else "shop-item") {
        // The whole row toggles, not just the box. This is the one screen used
        // one-handed in a shop, where an 18px target is no use at all.
        label("shop-item-main") {
            input(type = InputType.checkBox) {
                this.checked = item.checked
                title = if (item.checked) "Mark as still needed" else "Mark as bought"
                hxPost = "/shopping-list/$weekStart/items/${item.id}/toggle"
                hxTarget = "#shopping-list"
                hxSwap = "outerHTML"
            }
            val amount = item.displayQuantity()
            span("amount") { +if (amount.isBlank() || amount == "as needed") "\u2014" else amount }
            span("label") {
                +item.label
                if (item.manual) {
                    +" "
                    span("manual-tag") { +"(added)" }
                }
            }
        }
        button(classes = "btn btn-danger btn-sm") {
            hxDelete = "/shopping-list/$weekStart/items/${item.id}"
            hxTarget = "#shopping-list"
            hxSwap = "outerHTML"
            title = "Remove ${item.label}"
            +"\u2715"
        }
    }
}
