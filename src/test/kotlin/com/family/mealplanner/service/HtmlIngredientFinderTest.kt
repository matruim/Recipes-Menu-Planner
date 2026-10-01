package com.family.mealplanner.service

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlIngredientFinderTest {

    private fun doc(body: String) = Jsoup.parse("<html><body>$body</body></html>")

    private fun ul(vararg items: String) =
        "<ul>" + items.joinToString("") { "<li>$it</li>" } + "</ul>"

    @Test
    fun `recovers a list the markup left out entirely`() {
        // The recipes.heart.org case: the salsa is published, the soup is not.
        val salsa = listOf("1 medium cucumber, diced", "2 medium tomatoes, diced", "1/2 cup cilantro")
        val soup = listOf(
            "1 medium onion, chopped",
            "3 medium zucchini, chopped",
            "1 1/2 cups vegetable broth",
            "1/8 teaspoon salt",
        )
        val page = doc(ul(*salsa.toTypedArray()) + ul(*soup.toTypedArray()))

        val merged = HtmlIngredientFinder.merge(salsa, page)
        assertEquals(7, merged.size)
        assertTrue(merged.containsAll(salsa))
        assertTrue(merged.containsAll(soup))
    }

    @Test
    fun `leaves good markup alone`() {
        val published = listOf("2 tbsp olive oil", "1 lb chicken thighs", "3 cloves garlic")
        val page = doc(ul(*published.toTypedArray()))
        // The page offers nothing extra, so there is no reason to second-guess it.
        assertEquals(published, HtmlIngredientFinder.merge(published, page))
    }

    @Test
    fun `ignores a list of prose`() {
        val page = doc(
            ul(
                "Preheat the oven to 375F.",
                "Toss everything on a sheet pan.",
                "Roast for 40 minutes, turning once.",
            ),
        )
        assertTrue(HtmlIngredientFinder.candidateLists(page).isEmpty())
    }

    @Test
    fun `ignores navigation and other short lists`() {
        val page = doc(
            ul("Dinner Tonight", "5-Ingredient Dinners", "One-Pot Meals", "Quick & Easy") +
                ul("1 cup flour", "2 eggs"),
        )
        // Neither is ingredient-shaped: one has no quantities, the other is too short.
        assertTrue(HtmlIngredientFinder.candidateLists(page).isEmpty())
    }

    @Test
    fun `counts a list that mixes measured and unmeasured items`() {
        val page = doc(
            ul("2 tbsp olive oil", "1 lb chicken thighs", "salt, to taste", "Cooking spray"),
        )
        // Half carry a quantity, which is enough to recognise the list.
        assertEquals(1, HtmlIngredientFinder.candidateLists(page).size)
    }

    @Test
    fun `does not count the same list twice when a page renders it for print`() {
        val items = listOf("2 tbsp olive oil", "1 lb chicken thighs", "3 cloves garlic")
        val page = doc(ul(*items.toTypedArray()) + ul(*items.toTypedArray()))
        assertEquals(1, HtmlIngredientFinder.candidateLists(page).size)
        assertEquals(items, HtmlIngredientFinder.merge(items, page))
    }

    @Test
    fun `will not replace markup it cannot account for`() {
        // A longer list that omits what the markup named is a different list.
        val published = listOf("400 g tinned tomatoes", "1 tsp smoked paprika")
        val unrelated = ul("1 cup flour", "2 cups sugar", "3 eggs", "1 tsp vanilla")
        assertEquals(published, HtmlIngredientFinder.merge(published, doc(unrelated)))
    }
}
