package com.family.mealplanner.service

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Duration

/**
 * Reads schema.org Recipe markup expressed as microdata rather than JSON-LD.
 *
 * Older sites and hand-rolled blog templates annotate their existing HTML with
 * `itemprop` attributes instead of publishing a JSON block. The fields are the
 * same ones, so this is only a different way of reading them.
 */
object MicrodataRecipeParser {

    fun parse(document: Document, sourceUrl: String): ImportedRecipe? {
        val scope = document.selectFirst("[itemtype~=(?i)schema\\.org/Recipe]") ?: return null

        val ingredients = scope.values("[itemprop=recipeIngredient]", "[itemprop=ingredients]")
        val name = scope.value("[itemprop=name]") ?: return null
        if (ingredients.isEmpty()) return null

        return ImportedRecipe(
            title = name,
            description = scope.value("[itemprop=description]").orEmpty(),
            instructions = instructionsIn(scope).joinToString("\n"),
            servings = scope.value("[itemprop=recipeYield]")?.let { DIGITS.find(it)?.value?.toIntOrNull() }
                ?.takeIf { it in 1..99 },
            prepMinutes = scope.minutes("[itemprop=prepTime]"),
            cookMinutes = scope.minutes("[itemprop=cookTime]"),
            ingredients = ingredients,
            imageUrl = scope.selectFirst("[itemprop=image]")
                ?.let { it.absUrl("src").ifBlank { it.absUrl("content") } }
                ?.takeIf { it.isNotBlank() },
            sourceUrl = sourceUrl,
        )
    }

    /** Steps may be marked up one per element, or as a single block of prose. */
    private fun instructionsIn(scope: Element): List<String> {
        val marked = scope.select("[itemprop=recipeInstructions]")
        if (marked.isEmpty()) return emptyList()

        if (marked.size > 1) {
            return marked.map { it.text().trim() }.filter { it.isNotBlank() }
        }
        val single = marked.first()!!
        val steps = single.select("li, p").map { it.text().trim() }.filter { it.isNotBlank() }
        return steps.ifEmpty {
            // wholeText keeps the line breaks that separate the steps; text() would
            // collapse them into one run-on paragraph.
            single.wholeText().lines().map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    private fun Element.value(selector: String): String? =
        selectFirst(selector)
            ?.let { it.attr("content").ifBlank { it.text() } }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun Element.values(vararg selectors: String): List<String> =
        selectors.flatMap { selector -> select(selector).map { it.text().trim() } }
            .filter { it.isNotBlank() }
            .distinct()

    /** Durations live in `datetime` or `content`, since the text is for people. */
    private fun Element.minutes(selector: String): Int? {
        val element = selectFirst(selector) ?: return null
        val raw = listOf(element.attr("datetime"), element.attr("content"), element.text())
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return null

        runCatching { Duration.parse(raw) }.getOrNull()?.let { duration ->
            return duration.toMinutes().toInt().takeIf { it > 0 }
        }
        return DIGITS.find(raw)?.value?.toIntOrNull()?.takeIf { it > 0 }
    }

    private val DIGITS = Regex("""\d+""")
}
