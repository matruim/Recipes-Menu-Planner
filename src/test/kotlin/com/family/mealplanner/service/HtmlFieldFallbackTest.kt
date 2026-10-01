package com.family.mealplanner.service

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlFieldFallbackTest {

    private val blank = ImportedRecipe(
        title = "T", description = "", instructions = "",
        servings = null, prepMinutes = null, cookMinutes = null,
        ingredients = listOf("1 egg"), imageUrl = null, sourceUrl = "https://example.com",
    )

    private fun fill(bodyHtml: String, base: ImportedRecipe = blank) =
        HtmlFieldFallback.fill(base, Jsoup.parse("<html><body>$bodyHtml</body></html>"))

    private fun fillHead(headHtml: String, base: ImportedRecipe = blank) =
        HtmlFieldFallback.fill(base, Jsoup.parse("<html><head>$headHtml</head><body></body></html>"))

    @Test
    fun `reads times and servings printed as labelled text`() {
        // The layout recipes.heart.org uses: a table of labels and values.
        val filled = fill(
            """
            <table><tr><td>Prep Time</td><td>10 min</td></tr>
                   <tr><td>Cook Time</td><td>40 min</td></tr>
                   <tr><td>Total Time</td><td>50 min</td></tr>
                   <tr><td>Servings</td><td>4</td></tr></table>
            """,
        )
        assertEquals(10, filled.prepMinutes)
        assertEquals(40, filled.cookMinutes)
        assertEquals(4, filled.servings)
    }

    @Test
    fun `falls back to the page's social image`() {
        // recipes.heart.org publishes no image on the recipe itself, only og:image.
        val filled = fillHead(
            """<meta property="og:image" content="https://cdn.test/soup.jpg?w=1024">""",
        )
        assertEquals("https://cdn.test/soup.jpg?w=1024", filled.imageUrl)
    }

    @Test
    fun `accepts twitter image when og is absent`() {
        assertEquals(
            "https://cdn.test/tw.jpg",
            fillHead("""<meta name="twitter:image" content="https://cdn.test/tw.jpg">""").imageUrl,
        )
        assertEquals(
            "https://cdn.test/src.jpg",
            fillHead("""<link rel="image_src" href="https://cdn.test/src.jpg">""").imageUrl,
        )
    }

    @Test
    fun `resolves a relative social image against the page`() {
        val filled = fillHead("""<meta property="og:image" content="/img/soup.jpg">""")
        assertEquals("https://example.com/img/soup.jpg", filled.imageUrl)
    }

    @Test
    fun `keeps the image the recipe markup already gave`() {
        val fromJsonLd = blank.copy(imageUrl = "https://cdn.test/structured.jpg")
        val filled = fillHead(
            """<meta property="og:image" content="https://cdn.test/social.jpg">""",
            fromJsonLd,
        )
        assertEquals("https://cdn.test/structured.jpg", filled.imageUrl)
    }

    @Test
    fun `has no image when the page offers none`() {
        assertNull(fillHead("<title>No pictures here</title>").imageUrl)
    }

    @Test
    fun `does not mistake serving size for servings`() {
        val filled = fill("<p>Servings 6</p><p>Serving Size: 2 quiche cups</p>")
        assertEquals(6, filled.servings)
    }

    @Test
    fun `reads hours and combined durations`() {
        assertEquals(90, fill("<p>Prep Time: 1 hr 30 min</p>").prepMinutes)
        assertEquals(60, fill("<p>Prep time 1 hour</p>").prepMinutes)
        assertEquals(75, fill("<p>Cooking Time 1 hr 15 mins</p>").cookMinutes)
    }

    @Test
    fun `leaves structured data alone when it already supplied a value`() {
        val fromJsonLd = blank.copy(servings = 8, prepMinutes = 5, cookMinutes = 20)
        val filled = fill("<p>Prep Time 99 min</p><p>Servings 2</p>", fromJsonLd)
        assertEquals(8, filled.servings)
        assertEquals(5, filled.prepMinutes)
        assertEquals(20, filled.cookMinutes)
    }

    @Test
    fun `fills only the gaps`() {
        val partial = blank.copy(servings = 6)
        val filled = fill("<p>Prep Time 10 min</p><p>Servings 2</p>", partial)
        assertEquals(6, filled.servings, "should not overwrite a known yield")
        assertEquals(10, filled.prepMinutes)
    }

    @Test
    fun `reports nothing when the page says nothing`() {
        val filled = fill("<p>Just a story about the recipe.</p>")
        assertNull(filled.servings)
        assertNull(filled.prepMinutes)
        assertNull(filled.cookMinutes)
    }

    @Test
    fun `ignores a zero duration`() {
        assertNull(fill("<p>Prep Time 0 min</p>").prepMinutes)
    }
}
