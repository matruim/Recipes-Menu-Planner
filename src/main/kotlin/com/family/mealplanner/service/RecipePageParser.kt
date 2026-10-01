package com.family.mealplanner.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Duration

/**
 * Reads schema.org Recipe markup out of a page.
 *
 * Recipe sites overwhelmingly publish this as JSON-LD, which is far steadier than
 * scraping their HTML: the fields are named, and they survive redesigns. The
 * markup in the wild is still untidy — a field can arrive as a string, a number,
 * an array, or an object — so everything here reads defensively.
 */
object RecipePageParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(html: String, sourceUrl: String): ImportResult {
        val document = Jsoup.parse(html, sourceUrl)
        val recipeNode = jsonLdBlocks(document).firstNotNullOfOrNull { findRecipeNode(it) }

        if (recipeNode == null) {
            // Some sites annotate their HTML rather than publishing JSON.
            MicrodataRecipeParser.parse(document, sourceUrl)?.let { fromMicrodata ->
                return ImportResult.Imported(
                    HtmlFieldFallback.fill(fromMicrodata, document).withIngredientsFrom(document),
                )
            }
            // A roundup lists recipes without being one, which is worth saying plainly.
            val listed = collectionItemNames(document)
            if (listed.size >= 2) {
                return ImportResult.Roundup(
                    listed,
                    "That page is a roundup of ${listed.size} recipes rather than a single " +
                        "recipe, so there is nothing to import. Open the one you want and " +
                        "import that instead.",
                )
            }
            val title = fallbackTitle(document)
            val partial = title?.let { emptyImport(sourceUrl).copy(title = it) }
            return ImportResult.NothingFound(
                partial,
                "That page has no recipe markup, so nothing could be read from it automatically.",
            )
        }

        val recipe = ImportedRecipe(
            title = recipeNode.text("name") ?: fallbackTitle(document).orEmpty(),
            description = recipeNode.text("description").orEmpty(),
            instructions = instructionsFrom(recipeNode["recipeInstructions"]).joinToString("\n"),
            servings = recipeNode["recipeYield"]?.let { firstIntIn(it) },
            prepMinutes = recipeNode["prepTime"]?.let { minutesIn(it) },
            cookMinutes = recipeNode["cookTime"]?.let { minutesIn(it) },
            ingredients = stringsIn(recipeNode["recipeIngredient"] ?: recipeNode["ingredients"])
                .map { cleanText(it) }
                .filter { it.isNotBlank() },
            imageUrl = imageUrlIn(recipeNode["image"], sourceUrl),
            sourceUrl = sourceUrl,
        )

        // Some sites print servings and times on the page but omit them from JSON-LD.
        val completed = HtmlFieldFallback.fill(recipe, document)
            .withIngredientsFrom(document)

        return if (completed.isUsable) {
            ImportResult.Imported(completed)
        } else {
            ImportResult.NothingFound(
                completed,
                "That page has recipe markup but no ingredient list, so some fields are still blank.",
            )
        }
    }

    /** `image` may be a URL, a list of them, or an ImageObject wrapping one. */
    private fun imageUrlIn(element: JsonElement?, sourceUrl: String): String? {
        val candidate = when (element) {
            null -> null
            is JsonPrimitive -> element.content
            is JsonArray -> element.firstNotNullOfOrNull { imageUrlIn(it, sourceUrl) }
            is JsonObject -> (element["url"] ?: element["contentUrl"])?.let { imageUrlIn(it, sourceUrl) }
        }?.trim()?.takeIf { it.isNotBlank() } ?: return null

        // Pages sometimes give a path rather than a full address.
        return runCatching { java.net.URI(sourceUrl).resolve(candidate).toString() }
            .getOrDefault(candidate)
            .takeIf { it.startsWith("http", ignoreCase = true) }
    }

    private fun jsonLdBlocks(document: Document): List<JsonElement> =
        document.select("script[type=application/ld+json]")
            .mapNotNull { runCatching { json.parseToJsonElement(it.data()) }.getOrNull() }

    /** Names of the entries in an ItemList, which is how roundups list their recipes. */
    private fun collectionItemNames(document: Document): List<String> {
        val list = jsonLdBlocks(document).firstNotNullOfOrNull { findItemList(it) } ?: return emptyList()
        val elements = list["itemListElement"] as? JsonArray ?: return emptyList()
        return elements
            .filterIsInstance<JsonObject>()
            .mapNotNull { entry ->
                entry.text("name") ?: (entry["item"] as? JsonObject)?.text("name")
            }
            .filter { it.isNotBlank() }
    }

    private fun findItemList(element: JsonElement): JsonObject? = when (element) {
        is JsonObject ->
            if (element.hasType("ItemList") && element["itemListElement"] != null) element
            else element.values.firstNotNullOfOrNull { findItemList(it) }
        is JsonArray -> element.firstNotNullOfOrNull { findItemList(it) }
        else -> null
    }

    fun emptyImport(sourceUrl: String) = ImportedRecipe(
        title = "",
        description = "",
        instructions = "",
        servings = null,
        prepMinutes = null,
        cookMinutes = null,
        ingredients = emptyList(),
        imageUrl = null,
        sourceUrl = sourceUrl,
    )

    /** Recipe markup hides in @graph, in arrays, and inside other nodes. */
    private fun findRecipeNode(element: JsonElement): JsonObject? = when (element) {
        is JsonObject ->
            if (element.hasType("Recipe")) element
            else element.values.firstNotNullOfOrNull { findRecipeNode(it) }
        is JsonArray -> element.firstNotNullOfOrNull { findRecipeNode(it) }
        else -> null
    }

    private fun JsonObject.hasType(type: String): Boolean =
        stringsIn(this["@type"]).any { it.equals(type, ignoreCase = true) }

    private fun JsonObject.text(key: String): String? =
        this[key]?.let { stringsIn(it).firstOrNull() }?.let { cleanText(it) }?.takeIf { it.isNotBlank() }

    private fun stringsIn(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonPrimitive -> listOf(element.content)
        is JsonArray -> element.flatMap { stringsIn(it) }
        // A nested node such as {"@type":"HowToStep","text":"..."}
        is JsonObject -> listOfNotNull(
            (element["text"] ?: element["name"] ?: element["value"])?.let { stringsIn(it).firstOrNull() },
        )
    }

    private fun instructionsFrom(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonPrimitive -> htmlToLines(element.content)
        is JsonArray -> element.flatMap { instructionsFrom(it) }
        is JsonObject -> {
            // HowToSection and ItemList both just wrap the real steps, and some
            // sites nest them. Anything carrying itemListElement is a container.
            val nested = element["itemListElement"]
            if (nested != null) instructionsFrom(nested)
            else listOfNotNull(element.text("text") ?: element.text("name"))
        }
    }

    /** Instructions arrive as prose, as HTML, or as newline-separated steps. */
    private fun htmlToLines(raw: String): List<String> {
        if (!raw.contains('<')) {
            return raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        }
        val body = Jsoup.parseBodyFragment(raw)
        val blocks = body.select("li, p")
        val lines = if (blocks.isNotEmpty()) blocks.map { it.text() } else listOf(body.text())
        return lines.map { it.trim() }.filter { it.isNotBlank() }
    }

    /**
     * Normalises a text field out of the markup.
     *
     * `wholeText` is used rather than `text` so line breaks survive long enough to
     * split on: real pages hand back values like "\nTitle   \n\nTitle   ", where
     * the title is simply repeated, and collapsing first would fuse the two.
     */
    private fun cleanText(raw: String): String {
        val plain = if (raw.contains('<') || raw.contains('&')) {
            Jsoup.parseBodyFragment(raw).wholeText()
        } else {
            raw
        }
        return plain.split('\n')
            .map { it.replace(NON_BREAKING_SPACE, ' ').replace(WHITESPACE, " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
    }

    /** "4 servings", "Serves 4-6" and 4 all mean four. */
    private fun firstIntIn(element: JsonElement): Int? =
        stringsIn(element).firstNotNullOfOrNull { value ->
            DIGITS.find(value)?.value?.toIntOrNull()?.takeIf { it in 1..99 }
        }

    /** ISO-8601 durations such as PT1H15M, with a bare-number fallback. */
    private fun minutesIn(element: JsonElement): Int? {
        val raw = stringsIn(element).firstOrNull()?.trim() ?: return null
        runCatching { Duration.parse(raw) }.getOrNull()?.let { duration ->
            return duration.toMinutes().toInt().takeIf { it > 0 }
        }
        return DIGITS.find(raw)?.value?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun fallbackTitle(document: Document): String? =
        (document.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
            ?: document.title().takeIf { it.isNotBlank() })
            ?.let { cleanText(it) }

    private val DIGITS = Regex("""\d+""")
    private val WHITESPACE = Regex("""\s+""")
    private const val NON_BREAKING_SPACE = ' '
}

/**
 * Takes the page's own ingredient lists where the markup gave fewer, which is
 * how a recipe published only in part is recovered in full.
 */
private fun ImportedRecipe.withIngredientsFrom(document: Document): ImportedRecipe =
    copy(ingredients = HtmlIngredientFinder.merge(ingredients, document))
