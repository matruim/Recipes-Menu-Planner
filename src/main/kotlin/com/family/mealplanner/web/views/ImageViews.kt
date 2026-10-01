package com.family.mealplanner.web.views

import kotlinx.html.*

fun imagePath(file: String): String = "/images/$file"

/**
 * Where a recipe's picture is served from: a local copy where one exists,
 * otherwise the source site's own address.
 */
fun imageSrc(imageFile: String?, imageUrl: String?): String? = when {
    imageFile != null -> imagePath(imageFile)
    // Only ever addresses read out of a page's markup, never arbitrary text.
    imageUrl != null && imageUrl.startsWith("http", ignoreCase = true) -> imageUrl
    else -> null
}

/** The picture field on the recipe form: preview, upload, remove. */
fun FlowContent.imageField(imageFile: String?, imageUrl: String? = null, error: String? = null) {
    div("field") {
        id = "image-field"
        imageFieldBody(imageFile, imageUrl, error)
    }
}

fun imageFieldFragment(
    imageFile: String?,
    imageUrl: String? = null,
    error: String? = null,
): String = fragment {
    div("field") {
        id = "image-field"
        imageFieldBody(imageFile, imageUrl, error)
    }
}

private fun FlowContent.imageFieldBody(imageFile: String?, imageUrl: String?, error: String?) {
    label {
        +"Picture "
        span("hint") { +"imported with the recipe, or choose your own" }
    }
    error?.let { div("flash flash-warn") { p { +it } } }

    val src = imageSrc(imageFile, imageUrl)
    div("image-picker") {
        if (src != null) {
            img(alt = "Recipe photo", src = src, classes = "image-preview")
        } else {
            div("image-placeholder") { +"No picture yet" }
        }
        div("image-controls") {
            // Uploading posts on its own so the form stays a plain urlencoded submit.
            input(type = InputType.file, name = "image") {
                accept = "image/*"
                hxPost = "/recipes/images"
                hxEncoding = "multipart/form-data"
                hxTarget = "#image-field"
                hxSwap = "outerHTML"
            }
            if (src != null) {
                if (imageFile == null) {
                    span("hint") { +"Linked from the recipe site; upload a copy to keep it for good." }
                }
                button(type = ButtonType.button, classes = "btn btn-danger btn-sm") {
                    hxPost = "/recipes/images/clear"
                    hxTarget = "#image-field"
                    hxSwap = "outerHTML"
                    +"Remove picture"
                }
            }
        }
        // Carried through the normal form submit.
        hiddenInput(name = "imageFile") { value = imageFile.orEmpty() }
        hiddenInput(name = "imageUrl") { value = imageUrl.orEmpty() }
    }
}

/** A recipe's picture wherever one is being chosen or read. */
fun FlowContent.recipeThumb(imageFile: String?, imageUrl: String?, title: String, classes: String) {
    val src = imageSrc(imageFile, imageUrl) ?: return
    img(alt = title, src = src, classes = classes) {
        attributes["loading"] = "lazy"
    }
}

/**
 * Keeps a grid of cards even when only some recipes have a photo — a card with
 * nothing where the picture goes reads as broken rather than as empty.
 */
fun FlowContent.recipeCardImage(imageFile: String?, imageUrl: String?, title: String) {
    val src = imageSrc(imageFile, imageUrl)
    if (src != null) {
        img(alt = title, src = src, classes = "card-image") {
            attributes["loading"] = "lazy"
        }
    } else {
        div("card-image card-image-empty") {
            attributes["aria-hidden"] = "true"
            +"\uD83C\uDF7D"
        }
    }
}
