package com.family.mealplanner.service

import org.jsoup.nodes.Document

/**
 * Picks up the handful of fields some sites leave out of their JSON-LD.
 *
 * recipes.heart.org is the worked example: it publishes ingredients and steps as
 * structured data, but shows "Prep Time 10 min" only as text and puts the photo
 * solely in its `og:image` tag, with no `image` key on the recipe at all.
 *
 * Deliberately limited to servings, times and the picture. Those have a single
 * unambiguous home on the page; guessing at ingredients from prose would do more
 * harm than good, and the page's own lists are read separately.
 */
object HtmlFieldFallback {

    /** Fills only what the structured data left empty. */
    fun fill(recipe: ImportedRecipe, document: Document): ImportedRecipe {
        val withImage = recipe.copy(
            imageUrl = recipe.imageUrl ?: socialImageIn(document, recipe.sourceUrl),
        )
        if (withImage.servings != null && withImage.prepMinutes != null && withImage.cookMinutes != null) {
            return withImage
        }
        val text = document.body()?.text() ?: return withImage
        return withImage.copy(
            // Strict labels only: the body text includes the method, where
            // "Cook the onion for 5 minutes" would otherwise read as a cook time.
            servings = withImage.servings ?: RecipeTextSignals.servingsIn(text),
            prepMinutes = withImage.prepMinutes ?: RecipeTextSignals.prepMinutesIn(text),
            cookMinutes = withImage.cookMinutes ?: RecipeTextSignals.cookMinutesIn(text),
        )
    }

    /**
     * The picture a page offers for sharing. Sites maintain these tags carefully
     * because they decide how a link looks on social media, which makes them a
     * dependable stand-in when the recipe markup has no image of its own.
     */
    private fun socialImageIn(document: Document, sourceUrl: String): String? {
        val raw = SOCIAL_IMAGE_TAGS
            .firstNotNullOfOrNull { selector ->
                document.select(selector).firstOrNull()
                    ?.let { it.attr("content").ifBlank { it.attr("href") } }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            ?: return null

        // Some pages give a path rather than a full address.
        return runCatching { java.net.URI(sourceUrl).resolve(raw).toString() }
            .getOrDefault(raw)
            .takeIf { it.startsWith("http", ignoreCase = true) }
    }

    /** In preference order; og:image is the most widely maintained. */
    private val SOCIAL_IMAGE_TAGS = listOf(
        "meta[property=og:image]",
        "meta[name=og:image]",
        "meta[property=og:image:url]",
        "meta[name=twitter:image]",
        "meta[property=twitter:image]",
        "meta[name=twitter:image:src]",
        "link[rel=image_src]",
    )
}
