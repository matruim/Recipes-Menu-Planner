package com.family.mealplanner.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * These mirror src/main/resources/db/migration by hand. Flyway owns the schema;
 * changing a column there means changing it here too.
 */

object RecipesTable : UUIDTable("recipes") {
    val title = text("title")
    val description = text("description")
    val instructions = text("instructions")
    val servings = integer("servings")
    val prepMinutes = integer("prep_minutes")
    val cookMinutes = integer("cook_minutes")
    val sourceUrl = text("source_url").nullable()
    val imageFile = text("image_file").nullable()
    val imageUrl = text("image_url").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object IngredientsTable : UUIDTable("ingredients") {
    val name = text("name")
    val normalizedName = text("normalized_name").uniqueIndex()
    val category = text("category")
}

object RecipeIngredientsTable : UUIDTable("recipe_ingredients") {
    val recipeId = reference("recipe_id", RecipesTable, onDelete = ReferenceOption.CASCADE)
    val ingredientId = reference("ingredient_id", IngredientsTable, onDelete = ReferenceOption.RESTRICT)
    val quantity = decimal("quantity", 10, 3).nullable()
    val unit = text("unit")
    val note = text("note")
    val position = integer("position")
}

object PlannedMealsTable : UUIDTable("planned_meals") {
    val mealDate = date("meal_date")
    val recipeId = reference("recipe_id", RecipesTable, onDelete = ReferenceOption.CASCADE)
    val servings = integer("servings")
    val position = integer("position")
}

object ShoppingListItemsTable : UUIDTable("shopping_list_items") {
    val weekStart = date("week_start")
    val ingredientId = reference("ingredient_id", IngredientsTable, onDelete = ReferenceOption.CASCADE).nullable()
    val label = text("label")
    val quantity = decimal("quantity", 10, 3).nullable()
    val unit = text("unit")
    val category = text("category")
    val checked = bool("checked")
    val manual = bool("manual")
    val position = integer("position")
}
