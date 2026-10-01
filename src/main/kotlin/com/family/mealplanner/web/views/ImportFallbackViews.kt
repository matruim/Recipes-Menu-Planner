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
