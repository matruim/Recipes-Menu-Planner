package com.family.mealplanner.service

import com.family.mealplanner.domain.parseIngredientLine

/**
 * Turns the text read off a scanned page into a recipe.
 *
 * Printed recipes are laid out conventionally enough to read without knowing the
 * book: a title, a line of servings and times, a list of ingredients under a
 * heading, then numbered steps. Headings are used where they exist; where they
 * do not, the lines sort themselves out by shape, since an ingredient starts
 * with a quantity and a step does not.
 */
object TextRecipeParser {

    fun parse(text: String, sourceLabel: String = ""): ImportedRecipe? {
        val lines = text.lines()
            .map { it.trim().removeBullet() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val ingredientsAt = lines.indexOfFirst { it.isHeading(INGREDIENTS_HEADING) }
        val methodAt = lines.indexOfFirst { it.isHeading(METHOD_HEADING) }

        val sections = if (ingredientsAt >= 0) {
            sectioned(lines, ingredientsAt, methodAt)
        } else {
            byShape(lines)
        }

        if (sections.ingredients.isEmpty()) return null

        // Servings and times are read only from the lines above the ingredients,
        // so a step like "Cook the onion for 5 minutes" cannot be mistaken for one.
        val heading = lines.take(if (ingredientsAt >= 0) ingredientsAt else 1).joinToString(" ")

        return ImportedRecipe(
            title = sections.title,
            description = "",
            instructions = sections.instructions.joinToString("\n"),
            servings = RecipeTextSignals.servingsIn(heading),
            prepMinutes = RecipeTextSignals.prepMinutesIn(heading, looseLabel = true),
            cookMinutes = RecipeTextSignals.cookMinutesIn(heading, looseLabel = true),
            ingredients = sections.ingredients,
            imageUrl = null,
            sourceUrl = sourceLabel,
        )
    }

    private data class Sections(
        val title: String,
        val ingredients: List<String>,
        val instructions: List<String>,
    )

    /** The straightforward case: the page labels its own sections. */
    private fun sectioned(lines: List<String>, ingredientsAt: Int, methodAt: Int): Sections {
        val ingredientsEnd = if (methodAt > ingredientsAt) methodAt else lines.size
        return Sections(
            title = lines.first().asTitle(),
            ingredients = lines.subList(ingredientsAt + 1, ingredientsEnd)
                .filterNot { it.isAnyHeading() }
                .filter { it.looksLikeIngredient() },
            instructions = if (methodAt >= 0) {
                lines.subList(methodAt + 1, lines.size).filterNot { it.isAnyHeading() }
            } else {
                emptyList()
            },
        )
    }

    /**
     * No headings, so the lines are sorted by what they look like. A measured
     * line is an ingredient; anything long and unmeasured is a step.
     */
    private fun byShape(lines: List<String>): Sections {
        val ingredients = lines.filter { it.isMeasured() }
        val instructions = lines
            .filterNot { it.isMeasured() }
            .drop(1) // the title
            .filter { it.length >= PROSE_LENGTH }
        return Sections(lines.first().asTitle(), ingredients, instructions)
    }

    /** Within an ingredients block, keep measured lines and short unmeasured ones. */
    private fun String.looksLikeIngredient(): Boolean =
        isMeasured() || length < PROSE_LENGTH

    private fun String.isMeasured(): Boolean =
        parseIngredientLine(this)?.quantity?.amount != null

    private fun String.isHeading(pattern: Regex): Boolean =
        length <= HEADING_LENGTH && pattern.containsMatchIn(this)

    private fun String.isAnyHeading(): Boolean =
        isHeading(INGREDIENTS_HEADING) || isHeading(METHOD_HEADING)

    /** Scanned titles often come back shouting, which reads badly in a list. */
    private fun String.asTitle(): String {
        val cleaned = trim().trimEnd(':')
        if (cleaned.any { it.isLowerCase() }) return cleaned
        return cleaned.split(" ").mapIndexed { index, word ->
            when {
                word.isEmpty() -> word
                index > 0 && word.lowercase() in MINOR_WORDS -> word.lowercase()
                else -> word.first().uppercase() + word.drop(1).lowercase()
            }
        }.joinToString(" ")
    }

    private fun String.removeBullet(): String = replace(BULLET, "").trim()

    private const val HEADING_LENGTH = 28
    private const val PROSE_LENGTH = 60

    private val INGREDIENTS_HEADING = Regex("""^ingredients?\b""", RegexOption.IGNORE_CASE)
    private val METHOD_HEADING =
        Regex("""^(?:method|directions?|instructions?|preparation|steps?)\b""", RegexOption.IGNORE_CASE)

    // Leading bullets and step numbers, which carry no meaning once split out.
    private val BULLET = Regex("""^(?:[•·*•●▪\-–—]\s*|\d{1,2}[.)]\s+)""")

    private val MINOR_WORDS = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "of",
        "on", "or", "the", "to", "with", "over", "into",
    )
}
