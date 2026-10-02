package com.family.mealplanner

import com.family.mealplanner.config.databaseConfig
import com.family.mealplanner.config.imageConfig
import com.family.mealplanner.config.publicBaseUrl
import com.family.mealplanner.config.scraperConfig
import com.family.mealplanner.db.DatabaseFactory
import com.family.mealplanner.repository.PlannedMealRepository
import com.family.mealplanner.repository.RecipeRepository
import com.family.mealplanner.repository.ShoppingListRepository
import com.family.mealplanner.service.ImageStore
import com.family.mealplanner.service.PageScanner
import com.family.mealplanner.service.RecipeScraper
import com.family.mealplanner.service.UrlFetcher
import com.family.mealplanner.web.calendarRoutes
import com.family.mealplanner.web.recipeRoutes
import com.family.mealplanner.web.shoppingListRoutes
import com.family.mealplanner.web.views.NavItem
import com.family.mealplanner.web.views.appPage
import com.family.mealplanner.web.views.emptyState
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import kotlinx.html.a
import kotlinx.html.p
import org.slf4j.event.Level

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.connect(environment.config.databaseConfig())

    install(DefaultHeaders)
    install(Compression)
    install(CallLogging) { level = Level.INFO }
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondHtml(HttpStatusCode.NotFound) {
                appPage("Not found", null) {
                    emptyState("That page does not exist.")
                    p { a(href = "/plan") { +"Back to the plan" } }
                }
            }
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled failure on ${call.request.local.uri}", cause)
            call.respondHtml(HttpStatusCode.InternalServerError) {
                appPage("Something went wrong", null) {
                    emptyState("Something went wrong.", cause.message)
                    p { a(href = "/plan") { +"Back to the plan" } }
                }
            }
        }
    }

    val recipes = RecipeRepository()
    val plans = PlannedMealRepository()
    val lists = ShoppingListRepository()
    val imageDirectory = java.nio.file.Path.of(environment.config.imageConfig().directory)
    val images = ImageStore(imageDirectory)
    // The compiled text-recognition helper is cached beside the pictures.
    val scanner = PageScanner(imageDirectory.resolveSibling("scan"))
    val scraper = RecipeScraper(
        UrlFetcher(allowPrivateHosts = environment.config.scraperConfig().allowPrivateHosts),
        images,
    )

    routing {
        staticResources("/static", "static")
        calendarRoutes(plans, recipes)
        recipeRoutes(recipes, plans, scraper, images, scanner, environment.config.publicBaseUrl())
        shoppingListRoutes(lists, plans)
    }
}
