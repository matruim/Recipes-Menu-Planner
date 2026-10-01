package com.family.mealplanner.web.views

import com.family.mealplanner.domain.ParsedIngredientLine
import com.family.mealplanner.domain.Recipe
import com.family.mealplanner.domain.RecipeDetail
import kotlinx.html.*
import java.time.LocalDate

fun MAIN.recipesPage(recipes: List<Recipe>, query: String) {
    div("page-head") {
        div {
            h1 { +"Recipes" }
            p("subtitle") { +"${recipes.size} saved" }
        }
        div("toolbar") {
            a(href = "/recipes/new", classes = "btn btn-primary") { +"New recipe" }
        }
    }
    div("field") {
        input(type = InputType.search, name = "q") {
            placeholder = "Search recipes by name..."
            value = query
            hxGet = "/recipes/list"
            hxTrigger = "input changed delay:250ms, search"
            hxTarget = "#recipe-list"
            hxSwap = "outerHTML"
        }
    }
    recipeList(recipes)
}

fun FlowContent.recipeList(recipes: List<Recipe>) {
    div("recipe-grid") {
        id = "recipe-list"
        recipeCards(recipes)
    }
}

fun recipeListFragment(recipes: List<Recipe>): String = fragment {
    div("recipe-grid") {
        id = "recipe-list"
        recipeCards(recipes)
    }
}

private fun FlowContent.recipeCards(recipes: List<Recipe>) {
    if (recipes.isEmpty()) {
        emptyState("No recipes yet.", "Add one to start planning meals.")
        return
    }
    recipes.forEach { recipe ->
        a(href = "/recipes/${recipe.id}", classes = "card recipe-card") {
            recipeCardImage(recipe.imageFile, recipe.imageUrl, recipe.title)
            h3 { +recipe.title }
            if (recipe.description.isNotBlank()) {
                div("desc") { +recipe.description }
            }
            div("recipe-meta") {
                span("badge") { +"Serves ${recipe.servings}" }
                if (recipe.totalMinutes > 0) span("badge") { +"${recipe.totalMinutes} min" }
            }
        }
    }
}

fun MAIN.recipeDetailPage(detail: RecipeDetail, today: LocalDate, servings: Int) {
    val recipe = detail.recipe
    div("page-head") {
        div {
            h1 { +recipe.title }
            p("subtitle") {
                +"Serves ${recipe.servings}"
                if (recipe.prepMinutes > 0) +" · ${recipe.prepMinutes} min prep"
                if (recipe.cookMinutes > 0) +" · ${recipe.cookMinutes} min cook"
            }
        }
        div("toolbar") {
            a(href = "/recipes/${recipe.id}/edit", classes = "btn") { +"Edit" }
            button(classes = "btn btn-danger") {
                hxDelete = "/recipes/${recipe.id}"
                hxConfirm = "Delete \"${recipe.title}\"? This also removes it from any meal plans."
                +"Delete"
            }
        }
    }

    div("recipe-layout") {
        div("stack") {
            imageSrc(recipe.imageFile, recipe.imageUrl)?.let {
                img(alt = recipe.title, src = it, classes = "recipe-hero")
            }
            if (recipe.description.isNotBlank()) {
                p { +recipe.description }
            }
            div("card") {
                h2 { +"Instructions" }
                if (recipe.instructions.isBlank()) {
                    p("subtitle") { +"No instructions recorded." }
                } else {
                    div("instructions") { +recipe.instructions }
                }
            }
            recipe.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
                p("subtitle") {
                    +"Source: "
                    a(href = url) { +url }
                }
            }
        }

        div("stack") {
            ingredientsCard(detail, servings)
            addToPlanCard(detail, today)
        }
    }
}

/**
 * Ingredients for [servings] people. The recipe's own wording is preserved at its
 * written serving count; only a genuine rescale re-expresses the units, so
 * "4 tbsp butter" does not silently become "1/4 cup" when nothing was changed.
 */
fun FlowContent.ingredientsCard(detail: RecipeDetail, servings: Int) {
    div("card") {
        div("ingredients-head") {
            h2 { +"Ingredients" }
            label("scale-control") {
                +"for "
                input(type = InputType.number, name = "servings") {
                    id = "servings"
                    value = servings.toString()
                    min = "1"
                    max = "99"
                    title = "Scale the ingredients"
                    hxGet = "/recipes/${detail.recipe.id}/ingredients"
                    hxTrigger = "input changed delay:250ms"
                    hxTarget = "#ingredient-lines"
                    hxSwap = "outerHTML"
                }
                +" servings"
            }
        }
        ingredientLines(detail, servings)
    }
}

fun ingredientLinesFragment(detail: RecipeDetail, servings: Int): String = fragment {
    div {
        id = "ingredient-lines"
        ingredientLinesBody(detail, servings)
    }
}

private fun FlowContent.ingredientLines(detail: RecipeDetail, servings: Int) {
    div {
        id = "ingredient-lines"
        ingredientLinesBody(detail, servings)
    }
}

private fun FlowContent.ingredientLinesBody(detail: RecipeDetail, servings: Int) {
    if (detail.ingredients.isEmpty()) {
        p("subtitle") { +"No ingredients recorded." }
        return
    }

    val base = detail.recipe.servings
    val scale = if (base > 0) servings.toDouble() / base else 1.0
    val rescaled = servings != base

    if (rescaled) {
        p("scale-note") { +"Scaled from the recipe's $base servings" }
    }

    ul("ingredient-list") {
        detail.ingredients.forEach { ingredient ->
            val quantity =
                if (rescaled) ingredient.quantity.scaledBy(scale).humanized() else ingredient.quantity
            li {
                val hasAmount = quantity.amount != null
                if (hasAmount) {
                    span("amount") { +quantity.format() }
                    +" "
                }
                +ingredient.name
                if (ingredient.note.isNotBlank()) {
                    span("note") { +", ${ingredient.note}" }
                }
                if (!hasAmount && !quantity.unit.isAggregatable) {
                    span("note") { +", ${quantity.unit.label}" }
                }
            }
        }
    }
}

private fun FlowContent.addToPlanCard(detail: RecipeDetail, today: LocalDate) {
    div("card") {
        h2 { +"Add to the calendar" }
        p("subtitle") { +"Pick any night; dinners are planned a month at a time." }
        form {
            hxPost = "/recipes/${detail.recipe.id}/plan"
            hxTarget = "#plan-feedback"
            hxSwap = "outerHTML"
            // Servings live on the ingredients card so one number drives both the
            // quantities on screen and what gets planned.
            hxInclude = "#servings"
            div("field") {
                label { +"Date" }
                input(type = InputType.date, name = "date") {
                    value = today.toString()
                    required = true
                }
            }
            button(type = ButtonType.submit, classes = "btn btn-primary") { +"Add dinner" }
        }
        div { id = "plan-feedback" }
    }
}

fun planFeedbackFragment(message: String): String = fragment {
    div {
        id = "plan-feedback"
        div("flash") { +message }
    }
}

fun MAIN.recipeFormPage(state: RecipeFormState, error: String? = null, notice: String? = null) {
    div("page-head") {
        h1 { +if (state.isEdit) "Edit recipe" else "New recipe" }
    }
    recipeForm(state, error, notice)
}

fun recipeFormFragment(
    state: RecipeFormState,
    error: String? = null,
    notice: String? = null,
    roundup: List<String> = emptyList(),
): String = fragment {
    form(
        action = if (state.isEdit) "/recipes/${state.id}" else "/recipes",
        method = FormMethod.post,
        classes = "card stack",
    ) {
        id = "recipe-form"
        recipeFormBody(state, error, notice, roundup)
    }
}

private fun FlowContent.recipeForm(state: RecipeFormState, error: String?, notice: String?) {
    form(
        action = if (state.isEdit) "/recipes/${state.id}" else "/recipes",
        method = FormMethod.post,
        classes = "card stack",
    ) {
        id = "recipe-form"
        recipeFormBody(state, error, notice)
    }
}

private fun FlowContent.recipeFormBody(
    state: RecipeFormState,
    error: String?,
    notice: String?,
    roundup: List<String> = emptyList(),
) {
    error?.let { message ->
        div("flash flash-warn") {
            p { +message }
            if (roundup.isNotEmpty()) {
                ul("roundup-list") { roundup.forEach { li { +it } } }
            }
        }
    }
    notice?.let { div("flash") { +it } }

    // The link comes first: most recipes can be filled in from it.
    div("field") {
        label {
            +"Source URL "
            span("hint") { +"paste a recipe link and let it fill the form in" }
        }
        div("import-row") {
            input(type = InputType.url, name = "sourceUrl") {
                id = "sourceUrl"
                value = state.sourceUrl
                placeholder = "https://..."
            }
            button(type = ButtonType.button, classes = "btn btn-primary") {
                hxPost = "/recipes/import"
                hxTarget = "#recipe-form"
                hxSwap = "outerHTML"
                hxIndicator = "#import-status"
                +"Import"
            }
        }
        span("htmx-indicator hint") {
            id = "import-status"
            +"Reading that page..."
        }
        pasteSourceFallback(
            state,
            expanded = error != null || state.awaitingPaste,
            appOrigin = state.appOrigin,
            awaitingPaste = state.awaitingPaste,
        )
    }

    scanPageField(state.canScanPages)

    div("field") {
        label { +"Title" }
        input(type = InputType.text, name = "title") {
            required = true
            value = state.title
            placeholder = "Sheet pan chicken and vegetables"
        }
    }
    imageField(state.imageFile, state.imageUrl)

    div("field") {
        label { +"Description" }
        input(type = InputType.text, name = "description") {
            value = state.description
            placeholder = "A one-line summary"
        }
    }
    div("field-row") {
        div("field") {
            label { +"Servings" }
            input(type = InputType.number, name = "servings") {
                value = state.servings.toString()
                min = "1"
                max = "99"
                required = true
            }
        }
        div("field") {
            label { +"Prep minutes" }
            input(type = InputType.number, name = "prepMinutes") {
                value = state.prepMinutes.toString()
                min = "0"
            }
        }
        div("field") {
            label { +"Cook minutes" }
            input(type = InputType.number, name = "cookMinutes") {
                value = state.cookMinutes.toString()
                min = "0"
            }
        }
    }
    div("field") {
        label {
            +"Ingredients "
            span("hint") { +"one per line, e.g. \"1 1/2 cups flour, sifted\"" }
        }
        textArea {
            name = "ingredients"
            rows = "10"
            placeholder = "2 tbsp olive oil\n1 lb chicken thighs\n3 cloves garlic, minced\nsalt, to taste"
            hxPost = "/recipes/ingredients-preview"
            hxTrigger = "input changed delay:400ms"
            hxTarget = "#ingredient-preview"
            hxSwap = "outerHTML"
            +state.ingredientsText
        }
        ingredientPreview(state.parsedIngredients)
    }
    div("field") {
        label { +"Instructions" }
        textArea {
            name = "instructions"
            rows = "10"
            placeholder = "Step by step, one per line."
            +state.instructions
        }
    }
    div("toolbar") {
        button(type = ButtonType.submit, classes = "btn btn-primary") {
            +if (state.isEdit) "Save changes" else "Create recipe"
        }
        a(
            href = if (state.isEdit) "/recipes/${state.id}" else "/recipes",
            classes = "btn btn-ghost",
        ) { +"Cancel" }
    }
}

fun FlowContent.ingredientPreview(lines: List<ParsedIngredientLine>) {
    ul("preview-list") {
        id = "ingredient-preview"
        previewItems(lines)
    }
}

fun ingredientPreviewFragment(lines: List<ParsedIngredientLine>): String = fragment {
    ul("preview-list") {
        id = "ingredient-preview"
        previewItems(lines)
    }
}

/** Shows how each typed line was understood, so surprises surface before saving. */
private fun UL.previewItems(lines: List<ParsedIngredientLine>) {
    if (lines.isEmpty()) {
        li { span("unparsed") { +"Parsed ingredients will appear here as you type." } }
        return
    }
    lines.forEach { line ->
        li {
            val amount = line.quantity.format()
            span("amount") { +if (amount.isBlank()) "—" else amount }
            span {
                +line.name
                if (line.note.isNotBlank()) span("note") { +", ${line.note}" }
            }
        }
    }
}
