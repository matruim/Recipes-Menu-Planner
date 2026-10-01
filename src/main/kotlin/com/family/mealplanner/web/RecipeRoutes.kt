package com.family.mealplanner.web

import com.family.mealplanner.domain.RecipeDraft
import com.family.mealplanner.domain.parseIngredientLine
import com.family.mealplanner.repository.PlannedMealRepository
import com.family.mealplanner.repository.RecipeRepository
import com.family.mealplanner.service.ImportResult
import com.family.mealplanner.service.ImportedRecipe
import com.family.mealplanner.service.RecipePageParser
import com.family.mealplanner.service.ImageStore
import com.family.mealplanner.service.RecipeScraper
import com.family.mealplanner.web.views.NavItem
import com.family.mealplanner.web.views.appPage
import com.family.mealplanner.web.views.RecipeFormState
import com.family.mealplanner.web.views.imageFieldFragment
import com.family.mealplanner.web.views.ingredientLinesFragment
import com.family.mealplanner.web.views.ingredientPreviewFragment
import com.family.mealplanner.web.views.planFeedbackFragment
import com.family.mealplanner.web.views.recipeDetailPage
import com.family.mealplanner.web.views.recipeFormFragment
import com.family.mealplanner.web.views.recipeFormPage
import com.family.mealplanner.web.views.recipeListFragment
import com.family.mealplanner.web.views.recipesPage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.origin
import io.ktor.server.request.formFieldLimit
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.toByteArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Route.recipeRoutes(
    recipes: RecipeRepository,
    plans: PlannedMealRepository,
    scraper: RecipeScraper,
    images: ImageStore,
    publicBaseUrl: String?,
) {

    get("/recipes") {
        val query = call.request.queryParameters["q"].orEmpty()
        val results = recipes.list(query)
        call.respondHtml {
            appPage("Recipes", NavItem.RECIPES) { recipesPage(results, query) }
        }
    }

    get("/recipes/list") {
        val query = call.request.queryParameters["q"].orEmpty()
        call.respondFragment(recipeListFragment(recipes.list(query)))
    }

    get("/recipes/new") {
        val origin = call.appOrigin(publicBaseUrl)
        val newState = RecipeFormState(
            appOrigin = origin,
            awaitingPaste = call.request.queryParameters["paste"] == "1",
            reachableOrigins = call.reachableOrigins(origin),
        )
        call.respondHtml {
            appPage("New recipe", NavItem.RECIPES) { recipeFormPage(newState) }
        }
    }

    post("/recipes/ingredients-preview") {
        val text = call.receiveParameters()["ingredients"].orEmpty()
        call.respondFragment(ingredientPreviewFragment(text.lines().mapNotNull { parseIngredientLine(it) }))
    }

    post("/recipes") {
        val form = call.receiveParameters()
        val draft = form.toRecipeDraft()
        if (draft == null) {
            return@post call.respondHtml(HttpStatusCode.BadRequest) {
                appPage("New recipe", NavItem.RECIPES) {
                    recipeFormPage(form.toFormState(appOrigin = call.appOrigin(publicBaseUrl)), error = "A title is required.")
                }
            }
        }
        call.respondRedirect("/recipes/${recipes.create(draft)}")
    }

    get("/recipes/{id}") {
        val id = call.uuidParam("id") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val detail = recipes.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        // Arriving from a planned dinner carries that night's servings across.
        val servings = call.requestedServings(detail.recipe.servings)
        call.respondHtml {
            appPage(detail.recipe.title, NavItem.RECIPES) {
                recipeDetailPage(detail, LocalDate.now(), servings)
            }
        }
    }

    post("/recipes/import") {
        val form = call.receiveParameters()
        val current = form.toFormState(appOrigin = call.appOrigin(publicBaseUrl))
        call.respondFragment(
            when (val result = scraper.importFrom(current.sourceUrl)) {
                is ImportResult.Imported -> recipeFormFragment(
                    current.mergedWith(result.recipe, scraper.storeImage(result.recipe.imageUrl)),
                    notice = importedNotice(result.recipe),
                )
                // Partial data still beats an empty form, so fill what there was.
                is ImportResult.NothingFound -> recipeFormFragment(
                    current.mergedWith(result.partial),
                    error = result.message,
                )
                is ImportResult.Roundup -> recipeFormFragment(
                    current,
                    error = result.message,
                    roundup = result.itemNames,
                )
                is ImportResult.Failed -> recipeFormFragment(current, error = result.message)
            },
        )
    }

    post("/recipes/import-html") {
        // A pasted page is whole HTML, far past the default single-field limit.
        call.formFieldLimit = MAX_PASTED_PAGE_BYTES
        val form = call.receiveParameters()
        val current = form.toFormState(appOrigin = call.appOrigin(publicBaseUrl))
        val pageSource = form["pageSource"].orEmpty()

        call.respondFragment(
            if (pageSource.isBlank()) {
                recipeFormFragment(current, error = "Paste the page source first.")
            } else {
                // Same parser as a fetched page; the browser just did the fetching.
                when (val result = RecipePageParser.parse(pageSource, current.sourceUrl)) {
                    is ImportResult.Imported -> recipeFormFragment(
                        current.mergedWith(result.recipe, scraper.storeImage(result.recipe.imageUrl)),
                        notice = importedNotice(result.recipe),
                    )
                    is ImportResult.NothingFound -> recipeFormFragment(
                        current.mergedWith(result.partial),
                        error = result.message,
                    )
                    is ImportResult.Roundup -> recipeFormFragment(
                        current,
                        error = result.message,
                        roundup = result.itemNames,
                    )
                    is ImportResult.Failed -> recipeFormFragment(current, error = result.message)
                }
            },
        )
    }

    post("/recipes/images") {
        val upload = call.receiveMultipart(formFieldLimit = MAX_IMAGE_BYTES)
        var bytes: ByteArray? = null
        upload.forEachPart { part ->
            if (part is PartData.FileItem && bytes == null) {
                bytes = part.provider().toByteArray()
            }
            part.dispose()
        }

        val result = runCatching { bytes?.let { images.save(it) } }
        call.respondFragment(
            when {
                result.isFailure ->
                    imageFieldFragment(null, error = result.exceptionOrNull()?.message)
                result.getOrNull() == null ->
                    imageFieldFragment(null, error = "Choose an image file first.")
                else -> imageFieldFragment(result.getOrNull())
            },
        )
    }

    post("/recipes/images/clear") {
        call.respondFragment(imageFieldFragment(null))
    }

    get("/images/{name}") {
        val path = call.parameters["name"]?.let { images.resolve(it) }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondFile(path.toFile())
    }

    get("/recipes/{id}/ingredients") {
        val id = call.uuidParam("id") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val detail = recipes.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondFragment(
            ingredientLinesFragment(detail, call.requestedServings(detail.recipe.servings)),
        )
    }

    get("/recipes/{id}/edit") {
        val id = call.uuidParam("id") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val detail = recipes.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondHtml {
            appPage("Edit ${detail.recipe.title}", NavItem.RECIPES) {
                recipeFormPage(RecipeFormState.of(detail, call.appOrigin(publicBaseUrl)))
            }
        }
    }

    post("/recipes/{id}") {
        val id = call.uuidParam("id") ?: return@post call.respond(HttpStatusCode.BadRequest)
        val detail = recipes.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        val draft = form.toRecipeDraft()
        if (draft == null) {
            return@post call.respondHtml(HttpStatusCode.BadRequest) {
                appPage("Edit recipe", NavItem.RECIPES) {
                    recipeFormPage(form.toFormState(detail.recipe.id, call.appOrigin(publicBaseUrl)), error = "A title is required.")
                }
            }
        }
        recipes.update(id, draft)
        call.respondRedirect("/recipes/$id")
    }

    delete("/recipes/{id}") {
        val id = call.uuidParam("id") ?: return@delete call.respond(HttpStatusCode.BadRequest)
        recipes.delete(id)
        // HTMX honours this by navigating, which a body swap could not do here.
        call.response.headers.append("HX-Redirect", "/recipes")
        call.respond(HttpStatusCode.OK)
    }

    post("/recipes/{id}/plan") {
        val id = call.uuidParam("id") ?: return@post call.respond(HttpStatusCode.BadRequest)
        val form = call.receiveParameters()
        val date = form["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)

        val added = plans.addMeal(date, id, form.intOrNull("servings"))
        call.respondFragment(
            planFeedbackFragment(
                if (added) "Added to the calendar for ${date.format(FEEDBACK_DATE)}."
                else "Could not add that recipe to the calendar.",
            ),
        )
    }
}

private fun Parameters.toRecipeDraft(): RecipeDraft? {
    val title = this["title"]?.trim().orEmpty()
    if (title.isEmpty()) return null
    return RecipeDraft(
        title = title,
        description = this["description"]?.trim().orEmpty(),
        instructions = this["instructions"]?.trim().orEmpty(),
        servings = intOrNull("servings")?.coerceIn(1, 99) ?: 4,
        prepMinutes = intOrNull("prepMinutes")?.coerceIn(0, 10_000) ?: 0,
        cookMinutes = intOrNull("cookMinutes")?.coerceIn(0, 10_000) ?: 0,
        sourceUrl = this["sourceUrl"]?.trim()?.takeIf { it.isNotEmpty() },
        imageFile = this["imageFile"]?.trim()?.takeIf { it.isNotEmpty() },
        imageUrl = this["imageUrl"]?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) },
        ingredients = this["ingredients"].orEmpty().lines().mapNotNull { parseIngredientLine(it) },
    )
}

private val FEEDBACK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

/** Servings to display a recipe at, falling back to how it was written. */
private fun ApplicationCall.requestedServings(recipeServings: Int): Int =
    request.queryParameters["servings"]?.trim()?.toIntOrNull()?.coerceIn(1, 99) ?: recipeServings

/** Rebuilds form state from a submission so nothing typed is lost on a redisplay. */
private fun Parameters.toFormState(
    id: java.util.UUID? = null,
    appOrigin: String = "",
    awaitingPaste: Boolean = false,
) = RecipeFormState(
    id = id,
    appOrigin = appOrigin,
    awaitingPaste = awaitingPaste,
    title = this["title"].orEmpty(),
    description = this["description"].orEmpty(),
    instructions = this["instructions"].orEmpty(),
    servings = intOrNull("servings")?.coerceIn(1, 99) ?: 4,
    prepMinutes = intOrNull("prepMinutes")?.coerceIn(0, 10_000) ?: 0,
    cookMinutes = intOrNull("cookMinutes")?.coerceIn(0, 10_000) ?: 0,
    sourceUrl = this["sourceUrl"].orEmpty(),
    ingredientsText = this["ingredients"].orEmpty(),
    imageFile = this["imageFile"]?.takeIf { it.isNotBlank() },
    imageUrl = this["imageUrl"]?.takeIf { it.isNotBlank() },
)

private fun importedNotice(recipe: ImportedRecipe): String {
    val found = buildList {
        add("${recipe.ingredients.size} ingredients")
        if (recipe.instructions.isNotBlank()) add("instructions")
        if (recipe.servings != null) add("servings")
        if (recipe.prepMinutes != null || recipe.cookMinutes != null) add("times")
    }
    return "Imported ${found.joinToString(", ")}. Check it over before saving."
}

/** Recipe pages routinely run past a megabyte of markup. */
private const val MAX_PASTED_PAGE_BYTES = 8L * 1024 * 1024

/**
 * Where the bookmarklet should point. A configured base URL wins, since only it
 * knows about proxies and host names; otherwise it is however the browser got here.
 */
private fun ApplicationCall.appOrigin(publicBaseUrl: String? = null): String {
    publicBaseUrl?.let { return it }
    val point = request.origin
    val isDefaultPort = (point.scheme == "http" && point.serverPort == 80) ||
        (point.scheme == "https" && point.serverPort == 443)
    val port = if (isDefaultPort) "" else ":${point.serverPort}"
    return "${point.scheme}://${point.serverHost}$port"
}

/** Only worth offering when the planner was opened on the machine running it. */
private fun ApplicationCall.reachableOrigins(origin: String): List<String> =
    if (NetworkAddresses.isLoopback(origin)) {
        NetworkAddresses.reachableOrigins(request.local.localPort)
    } else {
        emptyList()
    }

/** Uploads are capped well below the store's own limit to fail fast. */
private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
