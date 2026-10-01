package com.family.mealplanner.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextRecipeParserTest {

    /** Verbatim output of the on-device scanner reading a printed page. */
    private val scannedPage = """
        BRAISED SHORT RIBS WITH ROOT VEGETABLES
        A long, slow Sunday braise that rewards patience.
        Serves 6 • Prep 25 minutes • Cook 3 hours 15 minutes
        INGREDIENTS
        • 3 lbs bone-in beef short ribs
        • 2 tbsp olive oil
        • 11/2 tsp kosher salt
        • 1/2 tsp freshly ground black pepper
        • 1 large onion, roughly chopped
        • 3 medium carrots, cut into chunks
        • 2 parsnips, peeled and sliced
        • 4 cloves garlic, smashed
        • 2 tbsp tomato paste
        • 11/4 cups dry red wine
        • 3 cups beef stock
        • 2 sprigs fresh thyme
        • 1 bay leaf
        • 400 g tinned tomatoes
        METHOD
        1. Heat the oven to 325°F. Pat the ribs dry and season all over with the salt and pepper.
        2. Brown the ribs in the oil in a heavy casserole, about 4 minutes a side. Set aside.
        3. Soften the onion, carrots and parsnips in the same pot for 8 minutes, then stir in the garlic and tomato paste.
        4. Pour in the wine and reduce by half, scraping the base of the pot clean.
        5. Return the ribs, add the stock, tomatoes, thyme and bay leaf. Cover and braise for 3 hours.
        6. Skim the fat, season to taste and serve with the vegetables spooned over.
    """.trimIndent()

    @Test
    fun `reads a whole recipe off a scanned page`() {
        val recipe = requireNotNull(TextRecipeParser.parse(scannedPage))

        assertEquals("Braised Short Ribs with Root Vegetables", recipe.title)
        assertEquals(6, recipe.servings)
        assertEquals(25, recipe.prepMinutes)
        assertEquals(195, recipe.cookMinutes, "3 hours 15 minutes")
        assertEquals(14, recipe.ingredients.size)
        assertEquals("3 lbs bone-in beef short ribs", recipe.ingredients.first())
        assertEquals("400 g tinned tomatoes", recipe.ingredients.last())
        assertEquals(6, recipe.instructions.lines().count { it.isNotBlank() })
        assertTrue(recipe.instructions.startsWith("Heat the oven to 325"))
    }

    @Test
    fun `strips the bullets and step numbers`() {
        val recipe = requireNotNull(TextRecipeParser.parse(scannedPage))
        assertTrue(recipe.ingredients.none { it.startsWith("•") }, recipe.ingredients.toString())
        assertTrue(
            recipe.instructions.lines().none { it.matches(Regex("""^\d+\..*""")) },
            recipe.instructions,
        )
    }

    @Test
    fun `does not mistake a cooking step for a cook time`() {
        // "Cook the onion for 5 minutes" sits in the method, below the heading
        // block, so it must not be read as the recipe's cook time.
        val recipe = requireNotNull(
            TextRecipeParser.parse(
                """
                SOUP
                Serves 4
                INGREDIENTS
                1 onion, chopped
                2 cups stock
                METHOD
                Cook the onion for 5 minutes, then add the stock.
                """.trimIndent(),
            ),
        )
        assertEquals(4, recipe.servings)
        assertNull(recipe.cookMinutes)
    }

    @Test
    fun `sorts the lines out when the page has no headings`() {
        val recipe = requireNotNull(
            TextRecipeParser.parse(
                """
                Grandma's Pancakes
                2 cups plain flour
                1 tbsp sugar
                2 eggs
                1 1/2 cups milk
                Whisk the dry ingredients together in a large bowl before adding the eggs.
                Rest the batter for twenty minutes, then cook on a hot griddle until golden.
                """.trimIndent(),
            ),
        )
        assertEquals("Grandma's Pancakes", recipe.title)
        assertEquals(4, recipe.ingredients.size)
        assertEquals(2, recipe.instructions.lines().count { it.isNotBlank() })
    }

    @Test
    fun `leaves a title that is already cased alone`() {
        val recipe = TextRecipeParser.parse(
            "Chef John's Taco-Stuffed Zucchini\nINGREDIENTS\n2 zucchini\n1 lb beef\n1 tsp cumin",
        )
        assertEquals("Chef John's Taco-Stuffed Zucchini", recipe?.title)
    }

    @Test
    fun `gives up on a page with no ingredients`() {
        assertNull(TextRecipeParser.parse("A LETTER\nDear Jane,\nHow are you keeping?"))
        assertNull(TextRecipeParser.parse("   "))
    }
}
