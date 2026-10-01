package com.family.mealplanner.service

import com.family.mealplanner.domain.parseIngredientLine
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Finds ingredient lists by their shape when the structured data is incomplete.
 *
 * Some sites publish only part of their recipe: recipes.heart.org lists a soup's
 * six salsa ingredients in JSON-LD and leaves the ten soup ingredients out
 * entirely, which loses half the recipe without saying so.
 *
 * An ingredient list is recognisable without knowing the site: most of its items
 * begin with a quantity. That is checked with the same parser the form uses, so
 * "1 1/2 cups" and "1/8 teaspoon" count and "Preheat the oven" does not.
 */
object HtmlIngredientFinder {

    /** Below this share of measured items, a list is something else. */
    private const val MEASURED_SHARE = 0.5

    /** Shorter lists are too easy to confuse with navigation. */
    private const val MIN_ITEMS = 3

    /** A list needs a couple of genuine quantities, not one lucky number. */
    private const val MIN_MEASURED = 2

    /**
     * Ingredients to use, given what the structured data provided.
     *
     * The page is only trusted over the markup when it offers strictly more and
     * still accounts for everything the markup named - that way a page listing
     * the same ingredients twice, or an unrelated list that happens to look
     * measured, cannot quietly replace good data.
     */
    fun merge(structured: List<String>, document: Document): List<String> {
        val found = candidateLists(document).flatten().distinctBy { it.comparable() }
        if (found.size <= structured.size) return structured

        val accounted = structured.all { line ->
            found.any { it.comparable() == line.comparable() }
        }
        return if (accounted) found else structured
    }

    /** Each distinct list on the page that reads like ingredients, in document order. */
    fun candidateLists(document: Document): List<List<String>> =
        document.select("ul")
            .mapNotNull { list -> list.ingredientItems() }
            // Pages often render the same list twice, once for a print view.
            .distinctBy { items -> items.joinToString("\u0000") { it.comparable() } }

    private fun Element.ingredientItems(): List<String>? {
        // Only the list's own items; a nested list is considered on its own.
        val items = children()
            .filter { it.tagName() == "li" }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (items.size < MIN_ITEMS) return null

        val measured = items.count { it.looksMeasured() }
        if (measured < MIN_MEASURED) return null
        if (measured.toDouble() / items.size < MEASURED_SHARE) return null
        return items
    }

    /** Uses the form's own parser, so whatever it understands counts here too. */
    private fun String.looksMeasured(): Boolean =
        parseIngredientLine(this)?.quantity?.amount != null

    private fun String.comparable(): String = lowercase().replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("""\s+""")
}
