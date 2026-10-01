package com.family.mealplanner.web

import com.family.mealplanner.repository.PlannedMealRepository
import com.family.mealplanner.repository.RecipeRepository
import com.family.mealplanner.web.views.NavItem
import com.family.mealplanner.web.views.appPage
import com.family.mealplanner.web.views.calendarPage
import com.family.mealplanner.web.views.closeDialogFragment
import com.family.mealplanner.web.views.monthGridFragment
import com.family.mealplanner.web.views.monthOrCurrent
import com.family.mealplanner.web.views.pickerDialogFragment
import com.family.mealplanner.web.views.pickerResultsFragment
import com.family.mealplanner.web.views.landscapePage
import com.family.mealplanner.web.views.portraitPage
import com.family.mealplanner.web.views.printableMonth
import com.family.mealplanner.web.views.printableWeek
import com.family.mealplanner.web.views.weekOrCurrent
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

fun Route.calendarRoutes(plans: PlannedMealRepository, recipes: RecipeRepository) {

    get("/") { call.respondRedirect("/plan") }

    get("/plan") {
        val month = monthOrCurrent(call.request.queryParameters["month"], LocalDate.now())
        val planned = plans.month(month)
        call.respondHtml {
            appPage("Plan", NavItem.PLAN) { calendarPage(planned, LocalDate.now()) }
        }
    }

    get("/plan/print") {
        val today = LocalDate.now()
        val weekParam = call.request.queryParameters["week"]

        if (weekParam != null) {
            val week = plans.week(weekOrCurrent(weekParam, today))
            call.respondHtml {
                appPage("Print week", NavItem.PLAN, extraHead = { portraitPage() }) {
                    printableWeek(week)
                }
            }
        } else {
            val month = plans.month(monthOrCurrent(call.request.queryParameters["month"], today))
            call.respondHtml {
                appPage("Print month", NavItem.PLAN, extraHead = { landscapePage() }) {
                    printableMonth(month)
                }
            }
        }
    }

    get("/plan/picker") {
        val date = call.queryLocalDate("date") ?: return@get call.respond(HttpStatusCode.BadRequest)
        call.respondFragment(pickerDialogFragment(date, recipes.list(), query = ""))
    }

    get("/plan/picker/results") {
        val date = call.queryLocalDate("date") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val query = call.request.queryParameters["q"].orEmpty()
        call.respondFragment(pickerResultsFragment(date, recipes.list(query)))
    }

    post("/plan/meals") {
        val form = call.receiveParameters()
        val date = form["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val recipeId = form["recipeId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)

        plans.addMeal(date, recipeId, form.intOrNull("servings"))
        call.respondCalendar(plans, YearMonth.from(date), closeDialog = true)
    }

    delete("/plan/meals/{mealId}") {
        val mealId = call.uuidParam("mealId") ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val date = plans.dateOf(mealId) ?: return@delete call.respond(HttpStatusCode.NotFound)
        plans.removeMeal(mealId)
        call.respondCalendar(plans, YearMonth.from(date))
    }

    put("/plan/meals/{mealId}/servings") {
        val mealId = call.uuidParam("mealId") ?: return@put call.respond(HttpStatusCode.BadRequest)
        val date = plans.dateOf(mealId) ?: return@put call.respond(HttpStatusCode.NotFound)
        call.receiveParameters().intOrNull("servings")?.let { plans.setServings(mealId, it) }
        call.respondCalendar(plans, YearMonth.from(date))
    }

    post("/plan/{month}/copy-previous") {
        val month = call.monthParam() ?: return@post call.respond(HttpStatusCode.BadRequest)
        plans.copyPreviousMonth(month)
        call.respondCalendar(plans, month)
    }

    delete("/plan/{month}/meals") {
        val month = call.monthParam() ?: return@delete call.respond(HttpStatusCode.BadRequest)
        plans.clearMonth(month)
        call.respondCalendar(plans, month)
    }
}

private fun ApplicationCall.monthParam(): YearMonth? =
    parameters["month"]?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

private fun ApplicationCall.queryLocalDate(name: String): LocalDate? =
    request.queryParameters[name]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * A meal can be added from a day that spills over from a neighbouring month, so
 * the calendar is always re-rendered for the month the user is looking at.
 */
private suspend fun ApplicationCall.respondCalendar(
    plans: PlannedMealRepository,
    fallback: YearMonth,
    closeDialog: Boolean = false,
) {
    val month = request.queryParameters["month"]
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: fallback
    val grid = monthGridFragment(plans.month(month), LocalDate.now())
    respondFragment(if (closeDialog) grid + closeDialogFragment() else grid)
}
