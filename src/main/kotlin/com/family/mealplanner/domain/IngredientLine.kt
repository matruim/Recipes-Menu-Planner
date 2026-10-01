package com.family.mealplanner.domain

/** One parsed line from the recipe ingredients textarea. */
data class ParsedIngredientLine(
    val name: String,
    val quantity: Quantity,
    val note: String,
) {
    /** Key used to merge the same ingredient across recipes. */
    val normalizedName: String get() = normalizeIngredientName(name)

    /**
     * Rebuilds the line the way it was typed, so editing a recipe round-trips
     * through the textarea without drift: "salt, to taste" stays put rather than
     * becoming "to taste salt".
     */
    fun display(): String = buildString {
        val hasAmount = quantity.amount != null
        if (hasAmount) append(quantity.format()).append(' ')
        append(name)
        if (note.isNotBlank()) append(", ").append(note)
        if (!hasAmount && !quantity.unit.isAggregatable) append(", ").append(quantity.unit.label)
    }
}

private val UNICODE_FRACTIONS = mapOf(
    '¼' to "1/4", '½' to "1/2", '¾' to "3/4",
    '⅐' to "1/7", '⅑' to "1/9", '⅒' to "1/10",
    '⅓' to "1/3", '⅔' to "2/3", '⅕' to "1/5",
    '⅖' to "2/5", '⅗' to "3/5", '⅘' to "4/5",
    '⅙' to "1/6", '⅚' to "5/6", '⅛' to "1/8",
    '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
)

private val LEADING_NOISE = Regex("""^[\-*•–—\s]+""")
private val MIXED_OR_SIMPLE_NUMBER = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)|^(\d+)\s*/\s*(\d+)|^(\d+(?:\.\d+)?)""")
private val RANGE_TAIL = Regex("""^\s*(?:-|–|to)\s*\d+(?:\.\d+)?(?:\s*/\s*\d+)?""")
private val TRAILING_INEXACT = Regex("""[,\s]+(to taste|as needed)\s*\z""", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("""\s+""")

/**
 * Parses a free-text ingredient line such as "1 1/2 cups flour, sifted" or
 * "2 cloves garlic, minced". Anything it cannot identify as a unit stays part of
 * the ingredient name, so "2 cloves garlic" keeps its "cloves".
 */
fun parseIngredientLine(raw: String): ParsedIngredientLine? {
    var text = raw.map { UNICODE_FRACTIONS[it]?.let { f -> " $f " } ?: it.toString() }.joinToString("")
    text = text.replace(LEADING_NOISE, "").replace(WHITESPACE, " ").trim()
    if (text.isEmpty()) return null

    // "salt, to taste" -> the trailing phrase becomes the unit, not part of the name.
    var inexact: MeasurementUnit? = null
    TRAILING_INEXACT.find(text)?.let { match ->
        inexact = MeasurementUnit.fromToken(match.groupValues[1])
        text = text.removeRange(match.range).trim().trimEnd(',')
    }

    val amount = MIXED_OR_SIMPLE_NUMBER.find(text)?.let { match ->
        text = text.removeRange(match.range).trim()
        RANGE_TAIL.find(text)?.let { text = text.removeRange(it.range).trim() }
        match.toAmount()
    }

    val unit = if (amount == null) {
        inexact ?: MeasurementUnit.COUNT
    } else {
        val (consumed, found) = takeLeadingUnit(text)
        if (found != null) text = consumed
        found ?: MeasurementUnit.COUNT
    }

    // A comma splits preparation notes off the name: "onion, finely diced".
    val commaAt = text.indexOf(',')
    val name = (if (commaAt >= 0) text.substring(0, commaAt) else text).trim().trim('.')
    val note = if (commaAt >= 0) text.substring(commaAt + 1).trim().trim('.') else ""

    if (name.isEmpty()) return null
    return ParsedIngredientLine(name = name, quantity = Quantity(amount, unit), note = note)
}

/** Tries the longest multi-word unit alias first so "fl oz" wins over "fl". */
private fun takeLeadingUnit(text: String): Pair<String, MeasurementUnit?> {
    val words = text.split(" ")
    for (size in minOf(MeasurementUnit.maxTokenWords, words.size) downTo 1) {
        val candidate = words.take(size).joinToString(" ")
        val unit = MeasurementUnit.fromToken(candidate.trim('.'))
        if (unit != null) return words.drop(size).joinToString(" ").trim() to unit
    }
    return text to null
}

/**
 * Reads a bare fraction, recovering a mixed one that lost its space.
 *
 * Text lifted off a printed page reliably drops the gap in "1 1/2", leaving
 * "11/2". Taken literally that is five and a half - a silent wrong answer, and a
 * dangerous one for salt. Cooking fractions are always proper, so a numerator
 * that is both multi-digit and no smaller than its denominator did not come from
 * a person, and its leading digits are the whole number.
 */
private fun fractionOrSquashedMixed(numerator: String, denominator: String): Double? {
    val n = numerator.toDoubleOrNull() ?: return null
    val d = denominator.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
    if (numerator.length < 2 || n < d) return n / d

    val fraction = numerator.last().digitToIntOrNull() ?: return n / d
    val whole = numerator.dropLast(1).toDoubleOrNull() ?: return n / d
    return if (fraction < d) whole + fraction / d else n / d
}

private fun MatchResult.toAmount(): Double? {
    val g = groupValues
    return when {
        g[1].isNotEmpty() -> g[1].toDouble() + g[2].toDouble() / g[3].toDouble()
        g[4].isNotEmpty() -> fractionOrSquashedMixed(g[4], g[5])
        g[6].isNotEmpty() -> g[6].toDouble()
        else -> null
    }?.takeIf { it.isFinite() }
}

/**
 * Collapses plurals and casing so "Tomatoes" and "tomato" land on one shopping
 * line. Deliberately conservative: words ending in -ss, -us or -is are left alone
 * so "molasses" and "hummus" survive intact.
 */
fun normalizeIngredientName(name: String): String {
    val cleaned = name.lowercase().replace(WHITESPACE, " ").trim().trim('.', ',')
    return cleaned.split(" ").joinToString(" ") { singularize(it) }
}

/**
 * Ingredients that already end in -s in their singular form. Suffix rules alone
 * cannot tell "molasses" from "glasses", so these are listed explicitly.
 */
private val ALWAYS_SINGULAR = setOf(
    "molasses", "hummus", "couscous", "asparagus", "watercress", "swiss",
    "bass", "grits", "oats", "greens",
)

private fun singularize(word: String): String = when {
    word in ALWAYS_SINGULAR -> word
    word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
    word.length > 4 && word.endsWith("oes") -> word.dropLast(2)
    word.length > 4 && (word.endsWith("ches") || word.endsWith("shes") ||
        word.endsWith("xes") || word.endsWith("sses")) -> word.dropLast(2)
    word.length > 3 && word.endsWith("s") &&
        !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is") -> word.dropLast(1)
    else -> word
}
