package com.family.mealplanner.web

import com.family.mealplanner.domain.weekStart
import com.family.mealplanner.repository.PlannedMealRepository
import com.family.mealplanner.repository.ShoppingListRepository
import com.family.mealplanner.web.views.NavItem
import com.family.mealplanner.web.views.appPage
import com.family.mealplanner.web.views.shoppingListFragment
import com.family.mealplanner.web.views.shoppingListPage
import com.family.mealplanner.web.views.weekOrCurrent
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.LocalDate

fun Route.shoppingListRoutes(lists: ShoppingListRepository, plans: PlannedMealRepository) {

    get("/shopping-list") {
        val weekStart = weekOrCurrent(call.request.queryParameters["week"], LocalDate.now())
        val week = plans.week(weekStart)

        // The first visit to a week builds the list; later visits keep whatever
        // has been ticked or hand-added since.
        val existing = lists.load(weekStart)
        val list = if (existing.isEmpty && !week.isEmpty) lists.regenerate(weekStart) else existing

        call.respondHtml {
            appPage("Shopping list", NavItem.SHOPPING) { shoppingListPage(week, list) }
        }
    }

    post("/shopping-list/{week}/regenerate") {
        val week = call.weekParam() ?: return@post call.respond(HttpStatusCode.BadRequest)
        call.respondFragment(shoppingListFragment(lists.regenerate(week)))
    }

    post("/shopping-list/{week}/items") {
        val week = call.weekParam() ?: return@post call.respond(HttpStatusCode.BadRequest)
        call.receiveParameters()["text"]?.takeIf { it.isNotBlank() }?.let {
            lists.addManualItem(week, it)
        }
        call.respondFragment(shoppingListFragment(lists.load(week)))
    }

    post("/shopping-list/{week}/items/{itemId}/toggle") {
        val week = call.weekParam() ?: return@post call.respond(HttpStatusCode.BadRequest)
        val itemId = call.uuidParam("itemId") ?: return@post call.respond(HttpStatusCode.BadRequest)
        lists.toggle(itemId)
        call.respondFragment(shoppingListFragment(lists.load(week)))
    }

    delete("/shopping-list/{week}/items/{itemId}") {
        val week = call.weekParam() ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val itemId = call.uuidParam("itemId") ?: return@delete call.respond(HttpStatusCode.BadRequest)
        lists.removeItem(itemId)
        call.respondFragment(shoppingListFragment(lists.load(week)))
    }

    post("/shopping-list/{week}/uncheck-all") {
        val week = call.weekParam() ?: return@post call.respond(HttpStatusCode.BadRequest)
        lists.uncheckAll(week)
        call.respondFragment(shoppingListFragment(lists.load(week)))
    }

    post("/shopping-list/{week}/clear-checked") {
        val week = call.weekParam() ?: return@post call.respond(HttpStatusCode.BadRequest)
        lists.clearChecked(week)
        call.respondFragment(shoppingListFragment(lists.load(week)))
    }
}

/** Snapped to a Monday so a stray date in the URL still finds its week. */
private fun ApplicationCall.weekParam(): LocalDate? =
    parameters["week"]
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.weekStart()
