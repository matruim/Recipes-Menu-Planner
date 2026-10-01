package com.family.mealplanner.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonLdRecipeParserTest {

    private fun page(jsonLd: String, head: String = "") = """
        <html><head><title>Fallback Title</title>$head
        <script type="application/ld+json">$jsonLd</script>
        </head><body><h1>ignored</h1></body></html>
    """.trimIndent()

    private fun imported(jsonLd: String, head: String = ""): ImportedRecipe {
        val result = JsonLdRecipeParser.parse(page(jsonLd, head), "https://example.com/r")
        assertIs<ImportResult.Imported>(result, "expected a usable recipe, got $result")
        return result.recipe
    }

    @Test
    fun `reads a plain schema org recipe`() {
        val recipe = imported(
            """
            {"@context":"https://schema.org","@type":"Recipe",
             "name":"Sheet Pan Chicken","description":"Everything on one tray",
             "recipeYield":"4 servings","prepTime":"PT15M","cookTime":"PT40M",
             "recipeIngredient":["2 lbs chicken thighs","1/4 cup olive oil"],
             "recipeInstructions":[{"@type":"HowToStep","text":"Heat the oven."},
                                   {"@type":"HowToStep","text":"Roast 40 minutes."}]}
            """,
        )
        assertEquals("Sheet Pan Chicken", recipe.title)
        assertEquals("Everything on one tray", recipe.description)
        assertEquals(4, recipe.servings)
        assertEquals(15, recipe.prepMinutes)
        assertEquals(40, recipe.cookMinutes)
        assertEquals(listOf("2 lbs chicken thighs", "1/4 cup olive oil"), recipe.ingredients)
        assertEquals("Heat the oven.\nRoast 40 minutes.", recipe.instructions)
    }

    @Test
    fun `finds the recipe inside an at-graph wrapper`() {
        // The shape Yoast and most WordPress food blogs emit.
        val recipe = imported(
            """
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebSite","name":"A Blog"},
              {"@type":"WebPage","name":"A Page"},
              {"@type":"Recipe","name":"Blog Pasta","recipeIngredient":["1 lb spaghetti"]}]}
            """,
        )
        assertEquals("Blog Pasta", recipe.title)
        assertEquals(listOf("1 lb spaghetti"), recipe.ingredients)
    }

    @Test
    fun `handles a top level array of nodes`() {
        val recipe = imported(
            """
            [{"@type":"Organization","name":"Site"},
             {"@type":"Recipe","name":"Array Soup","recipeIngredient":["2 carrots"]}]
            """,
        )
        assertEquals("Array Soup", recipe.title)
    }

    @Test
    fun `accepts a type given as an array`() {
        val recipe = imported(
            """{"@type":["Recipe","NewsArticle"],"name":"Dual Typed","recipeIngredient":["1 egg"]}""",
        )
        assertEquals("Dual Typed", recipe.title)
    }

    @Test
    fun `splits instructions delivered as one HTML blob`() {
        val recipe = imported(
            """
            {"@type":"Recipe","name":"HTML Steps","recipeIngredient":["1 onion"],
             "recipeInstructions":"<ol><li>Chop the onion.</li><li>Fry it gently.</li></ol>"}
            """,
        )
        assertEquals("Chop the onion.\nFry it gently.", recipe.instructions)
    }

    @Test
    fun `splits instructions delivered as newline separated prose`() {
        val recipe = imported(
            """
            {"@type":"Recipe","name":"Plain Steps","recipeIngredient":["1 onion"],
             "recipeInstructions":"Chop the onion.\nFry it gently.\n\n"}
            """,
        )
        assertEquals("Chop the onion.\nFry it gently.", recipe.instructions)
    }

    @Test
    fun `flattens grouped HowToSection steps`() {
        val recipe = imported(
            """
            {"@type":"Recipe","name":"Sectioned","recipeIngredient":["1 cup flour"],
             "recipeInstructions":[
               {"@type":"HowToSection","name":"Dough","itemListElement":[
                  {"@type":"HowToStep","text":"Mix the flour."},
                  {"@type":"HowToStep","text":"Rest the dough."}]},
               {"@type":"HowToSection","name":"Bake","itemListElement":[
                  {"@type":"HowToStep","text":"Bake 20 minutes."}]}]}
            """,
        )
        assertEquals("Mix the flour.\nRest the dough.\nBake 20 minutes.", recipe.instructions)
    }

    @Test
    fun `unwraps steps nested in an ItemList`() {
        // The shape recipes.heart.org publishes: a list holding an ItemList.
        val recipe = imported(
            """
            {"@type":"Recipe","name":"Quiche Cups","recipeIngredient":["4 large eggs"],
             "recipeInstructions":[{"@type":"ItemList","itemListElement":[
                {"@type":"HowToStep","text":"Preheat the oven to 350F."},
                {"@type":"HowToStep","text":"Whisk the eggs and yogurt."},
                {"@type":"HowToStep","text":"Bake for 25 minutes."}]}]}
            """,
        )
        assertEquals(
            "Preheat the oven to 350F.\nWhisk the eggs and yogurt.\nBake for 25 minutes.",
            recipe.instructions,
        )
    }

    @Test
    fun `collapses a title the page repeated`() {
        // Real markup hands back the name twice, padded with newlines.
        val recipe = imported(
            """
            {"@type":"Recipe",
             "name":"\nSouthwestern Quiche Cups       \n\nSouthwestern Quiche Cups     ",
             "recipeIngredient":["4 large eggs"]}
            """,
        )
        assertEquals("Southwestern Quiche Cups", recipe.title)
    }

    @Test
    fun `keeps genuinely different lines in a description`() {
        val recipe = imported(
            """
            {"@type":"Recipe","name":"Two Liner","recipeIngredient":["1 egg"],
             "description":"Quick to make.\nGood cold."}
            """,
        )
        assertEquals("Quick to make. Good cold.", recipe.description)
    }

    @Test
    fun `reads servings from the many shapes sites use`() {
        fun yieldOf(raw: String) = imported(
            """{"@type":"Recipe","name":"Y","recipeIngredient":["x"],"recipeYield":$raw}""",
        ).servings

        assertEquals(4, yieldOf(""""4 servings""""))
        assertEquals(4, yieldOf("4"))
        assertEquals(6, yieldOf(""""Serves 6 to 8""""))
        assertEquals(12, yieldOf("""["12 cookies","12"]"""))
    }

    @Test
    fun `reads ISO durations including hours`() {
        fun timesOf(prep: String, cook: String) = imported(
            """{"@type":"Recipe","name":"T","recipeIngredient":["x"],
                "prepTime":$prep,"cookTime":$cook}""",
        )

        val hourAndQuarter = timesOf(""""PT1H15M"""", """"PT30M"""")
        assertEquals(75, hourAndQuarter.prepMinutes)
        assertEquals(30, hourAndQuarter.cookMinutes)

        // Some sites emit a bare number of minutes instead of a duration.
        assertEquals(20, timesOf(""""20"""", """"PT0M"""").prepMinutes)
        assertNull(timesOf(""""PT0M"""", """"PT0M"""").prepMinutes)
    }

    @Test
    fun `reads the picture in each shape sites publish it`() {
        fun imageOf(raw: String) = imported(
            """{"@type":"Recipe","name":"P","recipeIngredient":["x"],"image":$raw}""",
        ).imageUrl

        assertEquals("https://cdn.test/a.jpg", imageOf(""""https://cdn.test/a.jpg""""))
        assertEquals("https://cdn.test/a.jpg", imageOf("""["https://cdn.test/a.jpg","https://cdn.test/b.jpg"]"""))
        assertEquals(
            "https://cdn.test/c.jpg",
            imageOf("""{"@type":"ImageObject","url":"https://cdn.test/c.jpg"}"""),
        )
        // A bare path is resolved against the page it came from.
        assertEquals("https://example.com/img/d.jpg", imageOf(""""/img/d.jpg""""))
    }

    @Test
    fun `has no picture when the page gives none`() {
        assertNull(imported("""{"@type":"Recipe","name":"P","recipeIngredient":["x"]}""").imageUrl)
    }

    @Test
    fun `strips markup and entities out of text fields`() {
        val recipe = imported(
            """
            {"@type":"Recipe","name":"Caf&eacute; Loaf",
             "description":"<p>Soft &amp; sweet</p>",
             "recipeIngredient":["1 cup <b>bread</b> flour"]}
            """,
        )
        assertEquals("Café Loaf", recipe.title)
        assertEquals("Soft & sweet", recipe.description)
        assertEquals(listOf("1 cup bread flour"), recipe.ingredients)
    }

    @Test
    fun `skips a malformed block and uses a later valid one`() {
        val html = """
            <html><head><title>T</title>
            <script type="application/ld+json">{ this is not json }</script>
            <script type="application/ld+json">
              {"@type":"Recipe","name":"Survivor","recipeIngredient":["1 tsp salt"]}
            </script></head><body></body></html>
        """.trimIndent()
        val result = JsonLdRecipeParser.parse(html, "https://example.com/r")
        assertIs<ImportResult.Imported>(result)
        assertEquals("Survivor", result.recipe.title)
    }

    @Test
    fun `recognises a roundup article as a list, not a recipe`() {
        // AllRecipes' "10 Zucchini Boat Recipes" shape: a NewsArticle plus an
        // ItemList of names, with no Recipe node anywhere.
        val html = page(
            """
            [{"@type":"NewsArticle","name":"10 Zucchini Boat Recipes"},
             {"@type":"ItemList","itemListElement":[
               {"@type":"ListItem","position":1,"name":"Vegetarian Zucchini Boats"},
               {"@type":"ListItem","position":2,"name":"Zucchini Boats on the Grill"},
               {"@type":"ListItem","position":3,"name":"Zucchini Pizza Boats"}]}]
            """,
        )
        val result = JsonLdRecipeParser.parse(html, "https://example.com/roundup")
        assertIs<ImportResult.Roundup>(result)
        assertEquals(3, result.itemNames.size)
        assertEquals("Vegetarian Zucchini Boats", result.itemNames.first())
        assertTrue(result.message.contains("roundup"))
    }

    @Test
    fun `reads names nested under item`() {
        val html = page(
            """
            {"@type":"ItemList","itemListElement":[
              {"@type":"ListItem","item":{"@type":"Thing","name":"First Dish"}},
              {"@type":"ListItem","item":{"@type":"Thing","name":"Second Dish"}}]}
            """,
        )
        val result = JsonLdRecipeParser.parse(html, "https://example.com/roundup")
        assertIs<ImportResult.Roundup>(result)
        assertEquals(listOf("First Dish", "Second Dish"), result.itemNames)
    }

    @Test
    fun `a real recipe still wins over an ItemList on the same page`() {
        // Recipe pages carry a breadcrumb ItemList; it must not hide the recipe.
        val recipe = imported(
            """
            [{"@type":"BreadcrumbList","itemListElement":[
               {"@type":"ListItem","name":"Recipes"},{"@type":"ListItem","name":"Dinner"}]},
             {"@type":"Recipe","name":"Real Dinner","recipeIngredient":["1 lb beef"]}]
            """,
        )
        assertEquals("Real Dinner", recipe.title)
    }

    @Test
    fun `reports pages with no recipe markup but keeps the title`() {
        val html = """
            <html><head><title>Just A Blog Post</title>
            <meta property="og:title" content="Just A Blog Post"></head>
            <body><p>No markup here.</p></body></html>
        """.trimIndent()
        val result = JsonLdRecipeParser.parse(html, "https://example.com/r")
        assertIs<ImportResult.NothingFound>(result)
        assertEquals("Just A Blog Post", result.partial?.title)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `reports recipe markup that carries no ingredients`() {
        val html = page("""{"@type":"Recipe","name":"Ingredient-less"}""")
        val result = JsonLdRecipeParser.parse(html, "https://example.com/r")
        assertIs<ImportResult.NothingFound>(result)
        assertEquals("Ingredient-less", result.partial?.title)
    }

    @Test
    fun `parsed ingredients feed straight into the existing line parser`() {
        val recipe = imported(
            """
            {"@type":"Recipe","name":"Round Trip","recipeIngredient":
              ["1 1/2 cups flour, sifted","2 cloves garlic, minced","salt, to taste"]}
            """,
        )
        val parsed = recipe.ingredients.mapNotNull {
            com.family.mealplanner.domain.parseIngredientLine(it)
        }
        assertEquals(3, parsed.size)
        assertEquals("flour", parsed[0].name)
        assertEquals(1.5, parsed[0].quantity.amount)
        assertEquals("cloves garlic", parsed[1].name)
        assertEquals("salt", parsed[2].name)
    }
}
