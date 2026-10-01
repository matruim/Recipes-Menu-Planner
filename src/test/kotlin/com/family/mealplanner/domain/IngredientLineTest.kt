package com.family.mealplanner.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngredientLineTest {

    private fun parse(line: String) = requireNotNull(parseIngredientLine(line)) { "failed to parse: $line" }

    @Test
    fun `parses a simple amount and unit`() {
        val result = parse("2 tbsp olive oil")
        assertEquals("olive oil", result.name)
        assertEquals(2.0, result.quantity.amount)
        assertEquals(MeasurementUnit.TABLESPOON, result.quantity.unit)
    }

    @Test
    fun `parses mixed numbers`() {
        val result = parse("1 1/2 cups flour")
        assertEquals("flour", result.name)
        assertEquals(1.5, result.quantity.amount)
        assertEquals(MeasurementUnit.CUP, result.quantity.unit)
    }

    @Test
    fun `parses a bare fraction`() {
        val result = parse("3/4 cup sugar")
        assertEquals(0.75, result.quantity.amount)
        assertEquals(MeasurementUnit.CUP, result.quantity.unit)
    }

    @Test
    fun `parses unicode fractions, including when glued to a whole number`() {
        assertEquals(0.5, parse("½ cup milk").quantity.amount)
        assertEquals(1.5, parse("1½ cups milk").quantity.amount)
    }

    @Test
    fun `splits preparation notes off the name`() {
        val result = parse("3 cloves garlic, minced")
        assertEquals("cloves garlic", result.name)
        assertEquals("minced", result.note)
        assertEquals(3.0, result.quantity.amount)
    }

    @Test
    fun `keeps unrecognised measure words as part of the name`() {
        // "cloves" is not a unit, so it must not be swallowed.
        val result = parse("6 cloves garlic")
        assertEquals("cloves garlic", result.name)
        assertEquals(MeasurementUnit.COUNT, result.quantity.unit)
    }

    @Test
    fun `reads a trailing to-taste phrase as the unit`() {
        val result = parse("salt, to taste")
        assertEquals("salt", result.name)
        assertNull(result.quantity.amount)
        assertEquals(MeasurementUnit.TO_TASTE, result.quantity.unit)
    }

    @Test
    fun `takes the low end of a range`() {
        assertEquals(1.0, parse("1-2 tbsp chili oil").quantity.amount)
        assertEquals(2.0, parse("2 to 3 cups stock").quantity.amount)
    }

    @Test
    fun `strips list bullets`() {
        assertEquals("butter", parse("- 4 tbsp butter").name)
    }

    @Test
    fun `ignores blank lines`() {
        assertNull(parseIngredientLine("   "))
        assertNull(parseIngredientLine(""))
    }

    @Test
    fun `handles metric units`() {
        val grams = parse("800 g canned tomatoes")
        assertEquals(800.0, grams.quantity.amount)
        assertEquals(MeasurementUnit.GRAM, grams.quantity.unit)

        val millilitres = parse("500 ml vegetable broth")
        assertEquals(MeasurementUnit.MILLILITER, millilitres.quantity.unit)
    }

    @Test
    fun `round-trips through display so editing does not corrupt a recipe`() {
        val lines = listOf(
            "2 tbsp olive oil",
            "1 1/2 cups flour",
            "3 cloves garlic, minced",
            "salt, to taste",
            "800 g canned tomatoes",
            "1 onion, diced",
            "spaghetti",
        )
        lines.forEach { original ->
            assertEquals(original, parse(original).display(), "round-trip changed \"$original\"")
        }
    }

    @Test
    fun `normalises plurals conservatively`() {
        assertEquals("tomato", normalizeIngredientName("Tomatoes"))
        assertEquals("onion", normalizeIngredientName("onions"))
        assertEquals("berry", normalizeIngredientName("berries"))
        // Words that merely end in -ss or -us are left intact.
        assertEquals("molasses", normalizeIngredientName("molasses"))
        assertEquals("hummus", normalizeIngredientName("hummus"))
    }
}
