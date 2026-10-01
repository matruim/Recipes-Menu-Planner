package com.family.mealplanner.web.views

import kotlinx.html.CommonAttributeGroupFacade
import kotlinx.html.TagConsumer
import kotlinx.html.stream.createHTML

/**
 * Typed accessors for the HTMX attributes used across the views, so the templates
 * read as Kotlin rather than as a pile of raw attribute strings.
 */
var CommonAttributeGroupFacade.hxGet: String
    get() = attributes["hx-get"].orEmpty()
    set(value) { attributes["hx-get"] = value }

var CommonAttributeGroupFacade.hxPost: String
    get() = attributes["hx-post"].orEmpty()
    set(value) { attributes["hx-post"] = value }

var CommonAttributeGroupFacade.hxPut: String
    get() = attributes["hx-put"].orEmpty()
    set(value) { attributes["hx-put"] = value }

var CommonAttributeGroupFacade.hxDelete: String
    get() = attributes["hx-delete"].orEmpty()
    set(value) { attributes["hx-delete"] = value }

var CommonAttributeGroupFacade.hxTarget: String
    get() = attributes["hx-target"].orEmpty()
    set(value) { attributes["hx-target"] = value }

var CommonAttributeGroupFacade.hxSwap: String
    get() = attributes["hx-swap"].orEmpty()
    set(value) { attributes["hx-swap"] = value }

var CommonAttributeGroupFacade.hxTrigger: String
    get() = attributes["hx-trigger"].orEmpty()
    set(value) { attributes["hx-trigger"] = value }

var CommonAttributeGroupFacade.hxVals: String
    get() = attributes["hx-vals"].orEmpty()
    set(value) { attributes["hx-vals"] = value }

var CommonAttributeGroupFacade.hxConfirm: String
    get() = attributes["hx-confirm"].orEmpty()
    set(value) { attributes["hx-confirm"] = value }

var CommonAttributeGroupFacade.hxInclude: String
    get() = attributes["hx-include"].orEmpty()
    set(value) { attributes["hx-include"] = value }

var CommonAttributeGroupFacade.hxPushUrl: String
    get() = attributes["hx-push-url"].orEmpty()
    set(value) { attributes["hx-push-url"] = value }

var CommonAttributeGroupFacade.hxSwapOob: String
    get() = attributes["hx-swap-oob"].orEmpty()
    set(value) { attributes["hx-swap-oob"] = value }

var CommonAttributeGroupFacade.hxOnAfterRequest: String
    get() = attributes["hx-on::after-request"].orEmpty()
    set(value) { attributes["hx-on::after-request"] = value }

/** Renders a standalone chunk of markup for an HTMX swap. */
fun fragment(block: TagConsumer<String>.() -> String): String = createHTML(prettyPrint = false).block()

var CommonAttributeGroupFacade.hxIndicator: String
    get() = attributes["hx-indicator"].orEmpty()
    set(value) { attributes["hx-indicator"] = value }

var CommonAttributeGroupFacade.hxEncoding: String
    get() = attributes["hx-encoding"].orEmpty()
    set(value) { attributes["hx-encoding"] = value }
