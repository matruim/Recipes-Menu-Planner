package com.family.mealplanner.service

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MicrodataRecipeParserTest {

    private fun parse(body: String) = MicrodataRecipeParser.parse(
        Jsoup.parse("<html><body>$body</body></html>", "https://example.com/r"),
        "https://example.com/r",
    )

    @Test
    fun `reads a recipe annotated on the HTML itself`() {
        val recipe = parse(
            """
            <div itemscope itemtype="http://schema.org/Recipe">
              <h1 itemprop="name">Blog Chilli</h1>
              <p itemprop="description">Before JSON-LD existed</p>
              <span itemprop="recipeYield">Serves 8</span>
              <time itemprop="prepTime" datetime="PT20M">20 mins</time>
              <time itemprop="cookTime" datetime="PT1H10M">1 hr 10 mins</time>
              <img itemprop="image" src="/img/chilli.jpg">
              <ul>
                <li itemprop="recipeIngredient">1 lb ground beef</li>
                <li itemprop="recipeIngredient">2 cans kidney beans</li>
              </ul>
              <ol itemprop="recipeInstructions">
                <li>Brown the beef.</li>
                <li>Simmer an hour.</li>
              </ol>
            </div>
            """,
        )
        requireNotNull(recipe)
        assertEquals("Blog Chilli", recipe.title)
        assertEquals("Before JSON-LD existed", recipe.description)
        assertEquals(8, recipe.servings)
        assertEquals(20, recipe.prepMinutes)
        assertEquals(70, recipe.cookMinutes)
        assertEquals(listOf("1 lb ground beef", "2 cans kidney beans"), recipe.ingredients)
        assertEquals("Brown the beef.\nSimmer an hour.", recipe.instructions)
        assertEquals("https://example.com/img/chilli.jpg", recipe.imageUrl)
    }

    @Test
    fun `accepts the older ingredients property name`() {
        val recipe = parse(
            """
            <div itemscope itemtype="https://schema.org/Recipe">
              <h1 itemprop="name">Legacy Soup</h1>
              <span itemprop="ingredients">2 carrots</span>
              <span itemprop="ingredients">1 onion</span>
            </div>
            """,
        )
        assertEquals(listOf("2 carrots", "1 onion"), recipe?.ingredients)
    }

    @Test
    fun `reads steps given as one block of prose`() {
        val recipe = parse(
            """
            <div itemscope itemtype="http://schema.org/Recipe">
              <h1 itemprop="name">One Block</h1>
              <span itemprop="recipeIngredient">1 egg</span>
              <div itemprop="recipeInstructions">Beat the egg.
              Fry it gently.</div>
            </div>
            """,
        )
        assertEquals("Beat the egg.\nFry it gently.", recipe?.instructions)
    }

    @Test
    fun `is absent when there is no recipe markup`() {
        assertNull(parse("<div><h1>Just a blog post</h1><p>No markup.</p></div>"))
    }

    @Test
    fun `is absent when the markup names no ingredients`() {
        // Without ingredients there is nothing worth filling the form with.
        assertNull(
            parse(
                """<div itemscope itemtype="http://schema.org/Recipe">
                     <h1 itemprop="name">Nothing To Cook</h1></div>""",
            ),
        )
    }
}
