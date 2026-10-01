package com.family.mealplanner.service

/**
 * The labelled numbers a recipe states in prose - "Serves 6", "Prep Time 15 min".
 *
 * Shared between pages and scanned sheets, which print them the same way. The
 * time labels can be matched loosely, without the word "time", but only where
 * the caller knows it is looking at a heading block: loose matching over a whole
 * recipe would read "Cook the onion for 5 minutes" as a five-minute cook time.
 */
object RecipeTextSignals {

    fun servingsIn(text: String): Int? =
        SERVINGS.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..99 }

    fun prepMinutesIn(text: String, looseLabel: Boolean = false): Int? =
        minutesAfter(text, if (looseLabel) PREP_LOOSE else PREP_STRICT)

    fun cookMinutesIn(text: String, looseLabel: Boolean = false): Int? =
        minutesAfter(text, if (looseLabel) COOK_LOOSE else COOK_STRICT)

    /** Reads "40 min", "1 hr", "3 hours 15 minutes" sitting just after a label. */
    private fun minutesAfter(text: String, label: Regex): Int? {
        val match = label.find(text) ?: return null
        val after = DURATION.matchAt(text, match.range.last + 1) ?: return null
        val hours = after.groupValues[1].toIntOrNull() ?: 0
        val minutes = after.groupValues[2].toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    // "Servings 4" must match while "Serving Size: 2 cups" must not.
    private val SERVINGS = Regex("""(?:servings|serves|yields?)\b\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE)

    private val PREP_STRICT = Regex("""prep(?:aration)?\s*time""", RegexOption.IGNORE_CASE)
    private val COOK_STRICT = Regex("""cook(?:ing)?\s*time""", RegexOption.IGNORE_CASE)
    private val PREP_LOOSE = Regex("""prep(?:aration)?(?:\s*time)?""", RegexOption.IGNORE_CASE)
    private val COOK_LOOSE = Regex("""cook(?:ing)?(?:\s*time)?""", RegexOption.IGNORE_CASE)

    private val DURATION = Regex(
        """\s*:?\s*(?:(\d+)\s*(?:hours?|hrs?|h)\b)?\s*(?:(\d+)\s*(?:minutes?|mins?|m)\b)?""",
        RegexOption.IGNORE_CASE,
    )
}
