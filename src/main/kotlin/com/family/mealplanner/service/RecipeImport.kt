package com.family.mealplanner.service

/** What could be lifted off a recipe page. Every field may be missing. */
data class ImportedRecipe(
    val title: String,
    val description: String,
    val instructions: String,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<String>,
    val imageUrl: String?,
    val sourceUrl: String,
) {
    /** True when the page gave enough to be worth filling the form with. */
    val isUsable: Boolean get() = title.isNotBlank() && ingredients.isNotEmpty()
}

sealed interface ImportResult {
    data class Imported(val recipe: ImportedRecipe) : ImportResult

    /**
     * A roundup article ("10 Zucchini Boat Recipes") rather than a recipe. There
     * is nothing to import, but naming what the page holds beats a bare failure.
     */
    data class Roundup(val itemNames: List<String>, val message: String) : ImportResult

    /** The page loaded but had no recipe markup; [partial] may still hold a title. */
    data class NothingFound(val partial: ImportedRecipe?, val message: String) : ImportResult

    data class Failed(val message: String) : ImportResult
}
