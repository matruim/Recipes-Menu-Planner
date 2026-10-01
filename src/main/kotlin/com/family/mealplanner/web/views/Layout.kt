package com.family.mealplanner.web.views

import kotlinx.html.*

enum class NavItem(val label: String, val href: String) {
    PLAN("Plan", "/plan"),
    RECIPES("Recipes", "/recipes"),
    SHOPPING("Shopping", "/shopping-list"),
}

fun HTML.appPage(
    pageTitle: String,
    active: NavItem?,
    extraHead: HEAD.() -> Unit = {},
    content: MAIN.() -> Unit,
) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"$pageTitle - Meal Planner" }
        link(href = "/static/css/app.css", rel = "stylesheet")
        script(src = "/static/js/htmx.min.js") { defer = true }
        extraHead()
    }
    body {
        header("site-header") {
            div("inner") {
                a(href = "/plan", classes = "brand") { +"🍲 Meal Planner" }
                nav("site-nav") {
                    NavItem.entries.forEach { item ->
                        a(
                            href = item.href,
                            classes = if (item == active) "active" else null,
                        ) { +item.label }
                    }
                }
            }
        }
        main("container") { content() }
        // Mount point for pickers and confirmations; cleared by an out-of-band swap.
        div { id = "dialog" }
    }
}

fun FlowContent.emptyState(message: String, detail: String? = null) {
    div("empty") {
        p { +message }
        detail?.let { p("subtitle") { +it } }
    }
}
