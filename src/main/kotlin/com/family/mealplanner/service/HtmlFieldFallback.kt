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
 * harm than good, and they already come from the structured data.
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
            servings = withImage.servings ?: servingsIn(text),
            prepMinutes = withImage.prepMinutes ?: minutesLabelled(text, PREP_LABEL),
            cookMinutes = withImage.cookMinutes ?: minutesLabelled(text, COOK_LABEL),
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

    private fun servingsIn(text: String): Int? =
        SERVINGS_LABEL.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..99 }

    /** Reads "40 min", "1 hr", "1 hr 15 min" sitting just after a label. */
    private fun minutesLabelled(text: String, label: Regex): Int? {
        val match = label.find(text) ?: return null
        val after = DURATION.matchAt(text, match.range.last + 1) ?: return null
        val hours = after.groupValues[1].toIntOrNull() ?: 0
        val minutes = after.groupValues[2].toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private val PREP_LABEL = Regex("""prep(?:aration)?\s*time""", RegexOption.IGNORE_CASE)
    private val COOK_LABEL = Regex("""cook(?:ing)?\s*time""", RegexOption.IGNORE_CASE)

    // "Servings 4" must match while "Serving Size: 2 cups" must not.
    private val SERVINGS_LABEL = Regex(
        """(?:servings|serves|yields?)\b\s*:?\s*(\d+)""",
        RegexOption.IGNORE_CASE,
    )

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

    private val DURATION = Regex(
        """\s*:?\s*(?:(\d+)\s*(?:hours?|hrs?|h)\b)?\s*(?:(\d+)\s*(?:minutes?|mins?|m)\b)?""",
        RegexOption.IGNORE_CASE,
    )
}
