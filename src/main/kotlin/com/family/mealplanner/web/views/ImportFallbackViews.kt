package com.family.mealplanner.web.views

import kotlinx.html.*

/**
 * Some recipe sites sit behind bot protection that refuses any request not made
 * by a real browser. The browser on this machine is not blocked, so it can do the
 * fetching: view source, paste it here, and the same parser runs over it.
 */
fun FlowContent.pasteSourceFallback(
    state: RecipeFormState,
    expanded: Boolean,
    appOrigin: String,
    awaitingPaste: Boolean,
) {
    details("paste-fallback") {
        if (expanded) open = true
        summary { +"Blocked, or nothing found? Paste the page source instead" }
        div("paste-body") {
            div("bookmarklet-tip") {
                p {
                    +"Easiest way: drag this button to your bookmarks bar once. Then on any "
                    +"recipe page, click it — this planner opens in a new tab and you just press "
                    +"paste."
                }
                // The address is baked into the bookmarklet when it is dragged, so a
                // localhost one works nowhere but this machine.
                if (state.reachableOrigins.isNotEmpty()) {
                    div("bookmarklet-warning") {
                        p {
                            +"You are viewing this at "
                            strong { +appOrigin }
                            +", so a bookmarklet made here will only work on this computer. "
                            +"To use it from a phone or another machine, open the planner at "
                            +"its network address first and drag the button from there:"
                        }
                        ul("origin-list") {
                            state.reachableOrigins.forEach { origin ->
                                li { a(href = "$origin/recipes/new") { +"$origin/recipes/new" } }
                            }
                        }
                    }
                }
                a(href = bookmarkletHref(appOrigin), classes = "btn btn-primary bookmarklet") {
                    // Stops a click here from navigating; it is meant to be dragged.
                    onClick = "alert('Drag this button to your bookmarks bar, " +
                        "then click it from a recipe page.'); return false;"
                    +"\uD83E\uDD58 Send to Meal Planner"
                }
            }
            p("hint") {
                +"Or do it by hand: open the recipe, press "
                kbd { +"Ctrl/Cmd" }
                +" + "
                kbd { +"U" }
                +" to view its source, select all, and paste it below."
            }
            textArea {
                name = "pageSource"
                id = "pageSource"
                rows = "4"
                placeholder = "Paste here - the recipe is read as soon as it lands"
                if (awaitingPaste) autoFocus = true
                // Importing on paste means the bookmarklet route is click, paste, done.
                hxPost = "/recipes/import-html"
                hxTrigger = "paste delay:300ms"
                hxTarget = "#recipe-form"
                hxSwap = "outerHTML"
                hxIndicator = "#import-status"
            }
            button(type = ButtonType.button, classes = "btn") {
                hxPost = "/recipes/import-html"
                hxTarget = "#recipe-form"
                hxSwap = "outerHTML"
                hxIndicator = "#import-status"
                +"Read pasted source"
            }
        }
    }
}

/**
 * Photograph a cookbook page or drop in a PDF, and the text is read off it.
 *
 * Offered only where the machine can actually do it, since a control that always
 * fails is worse than no control.
 */
fun FlowContent.scanPageField(available: Boolean) {
    if (!available) return
    div("field") {
        label {
            +"Scan a page "
            span("hint") { +"a photo of a cookbook page, a recipe card, or a PDF" }
        }
        div("import-row") {
            input(type = InputType.file, name = "page") {
                accept = "image/*,application/pdf"
                hxPost = "/recipes/scan"
                hxEncoding = "multipart/form-data"
                hxTarget = "#recipe-form"
                hxSwap = "outerHTML"
                hxIndicator = "#scan-status"
            }
        }
        span("htmx-indicator hint") {
            id = "scan-status"
            +"Reading the page..."
        }
    }
}
