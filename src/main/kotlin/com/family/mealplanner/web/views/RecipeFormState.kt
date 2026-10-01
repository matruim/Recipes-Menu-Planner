package com.family.mealplanner.web.views

import com.family.mealplanner.domain.ParsedIngredientLine
import com.family.mealplanner.domain.RecipeDetail
import com.family.mealplanner.domain.parseIngredientLine
import com.family.mealplanner.service.ImportedRecipe
import com.family.mealplanner.service.StoredImage
import java.util.UUID

/**
 * Everything the recipe form shows. Typing, editing and importing all produce one
 * of these, so a half-filled form survives an import that only found some fields.
 */
data class RecipeFormState(
    val id: UUID? = null,
    val title: String = "",
    val description: String = "",
    val instructions: String = "",
    val servings: Int = 4,
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val sourceUrl: String = "",
    val ingredientsText: String = "",
    val imageFile: String? = null,
    val imageUrl: String? = null,
    /** This server as the browser sees it, so the bookmarklet can point back. */
    val appOrigin: String = "",
    /** Arrived from the bookmarklet, with the page already on the clipboard. */
    val awaitingPaste: Boolean = false,
) {
    val isEdit: Boolean get() = id != null

    val parsedIngredients: List<ParsedIngredientLine>
        get() = ingredientsText.lines().mapNotNull { parseIngredientLine(it) }

    /**
     * Overlays what a page gave us. Anything the page left out keeps whatever was
     * already typed, so importing never wipes work already done by hand.
     */
    fun mergedWith(imported: ImportedRecipe?, importedImage: StoredImage? = null): RecipeFormState {
        if (imported == null) return this
        return copy(
            title = imported.title.ifBlank { title },
            description = imported.description.ifBlank { description },
            instructions = imported.instructions.ifBlank { instructions },
            servings = imported.servings ?: servings,
            prepMinutes = imported.prepMinutes ?: prepMinutes,
            cookMinutes = imported.cookMinutes ?: cookMinutes,
            sourceUrl = imported.sourceUrl.ifBlank { sourceUrl },
            ingredientsText = imported.ingredients
                .joinToString("\n")
                .ifBlank { ingredientsText },
            imageFile = importedImage?.file ?: imageFile,
            imageUrl = importedImage?.remoteUrl ?: imageUrl,
        )
    }

    companion object {
        fun of(detail: RecipeDetail, appOrigin: String = "") = RecipeFormState(
            appOrigin = appOrigin,
            id = detail.recipe.id,
            title = detail.recipe.title,
            description = detail.recipe.description,
            instructions = detail.recipe.instructions,
            servings = detail.recipe.servings,
            prepMinutes = detail.recipe.prepMinutes,
            cookMinutes = detail.recipe.cookMinutes,
            sourceUrl = detail.recipe.sourceUrl.orEmpty(),
            ingredientsText = detail.ingredients.joinToString("\n") { it.display() },
            imageFile = detail.recipe.imageFile,
            imageUrl = detail.recipe.imageUrl,
        )
    }
}
