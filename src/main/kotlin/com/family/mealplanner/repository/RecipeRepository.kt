package com.family.mealplanner.repository

import com.family.mealplanner.db.IngredientsTable
import com.family.mealplanner.db.RecipeIngredientsTable
import com.family.mealplanner.db.RecipesTable
import com.family.mealplanner.db.dbQuery
import com.family.mealplanner.db.toDbDecimal
import com.family.mealplanner.db.toQuantity
import com.family.mealplanner.domain.IngredientCategory
import com.family.mealplanner.domain.ParsedIngredientLine
import com.family.mealplanner.domain.Recipe
import com.family.mealplanner.domain.RecipeDetail
import com.family.mealplanner.domain.RecipeDraft
import com.family.mealplanner.domain.RecipeIngredient
import com.family.mealplanner.domain.normalizeIngredientName
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.util.UUID

class RecipeRepository {

    suspend fun list(search: String? = null): List<Recipe> = dbQuery {
        val query = if (search.isNullOrBlank()) {
            RecipesTable.selectAll()
        } else {
            val needle = "%${search.trim().lowercase()}%"
            RecipesTable.selectAll().where { RecipesTable.title.lowerCase() like needle }
        }
        query.orderBy(RecipesTable.title to SortOrder.ASC).map { it.toRecipe() }
    }

    suspend fun find(id: UUID): RecipeDetail? = dbQuery {
        val recipe = RecipesTable.selectAll()
            .where { RecipesTable.id eq id }
            .singleOrNull()
            ?.toRecipe()
            ?: return@dbQuery null
        RecipeDetail(recipe, loadIngredients(id))
    }

    suspend fun create(draft: RecipeDraft): UUID = dbQuery {
        val now = LocalDateTime.now()
        val id = RecipesTable.insertAndGetId {
            it[title] = draft.title
            it[description] = draft.description
            it[instructions] = draft.instructions
            it[servings] = draft.servings
            it[prepMinutes] = draft.prepMinutes
            it[cookMinutes] = draft.cookMinutes
            it[sourceUrl] = draft.sourceUrl
            it[imageFile] = draft.imageFile
            it[imageUrl] = draft.imageUrl
            it[createdAt] = now
            it[updatedAt] = now
        }.value
        writeIngredients(id, draft.ingredients)
        id
    }

    suspend fun update(id: UUID, draft: RecipeDraft): Boolean = dbQuery {
        val updated = RecipesTable.update({ RecipesTable.id eq id }) {
            it[title] = draft.title
            it[description] = draft.description
            it[instructions] = draft.instructions
            it[servings] = draft.servings
            it[prepMinutes] = draft.prepMinutes
            it[cookMinutes] = draft.cookMinutes
            it[sourceUrl] = draft.sourceUrl
            it[imageFile] = draft.imageFile
            it[imageUrl] = draft.imageUrl
            it[updatedAt] = LocalDateTime.now()
        }
        if (updated == 0) return@dbQuery false
        RecipeIngredientsTable.deleteWhere { RecipeIngredientsTable.recipeId eq id }
        writeIngredients(id, draft.ingredients)
        true
    }

    suspend fun delete(id: UUID): Boolean = dbQuery {
        RecipesTable.deleteWhere { RecipesTable.id eq id } > 0
    }

    private fun loadIngredients(recipeId: UUID): List<RecipeIngredient> =
        RecipeIngredientsTable
            .join(IngredientsTable, JoinType.INNER, RecipeIngredientsTable.ingredientId, IngredientsTable.id)
            .selectAll()
            .where { RecipeIngredientsTable.recipeId eq recipeId }
            .orderBy(RecipeIngredientsTable.position to SortOrder.ASC)
            .map { it.toRecipeIngredient() }

    private fun writeIngredients(recipeId: UUID, lines: List<ParsedIngredientLine>) {
        lines.forEachIndexed { index, line ->
            val ingredientId = findOrCreateIngredient(line.name)
            RecipeIngredientsTable.insert {
                it[RecipeIngredientsTable.recipeId] = recipeId
                it[RecipeIngredientsTable.ingredientId] = ingredientId
                it[quantity] = line.quantity.amount.toDbDecimal()
                it[unit] = line.quantity.unit.name
                it[note] = line.note
                it[position] = index
            }
        }
    }

    /** Ingredients are shared across recipes so the shopping list can merge them. */
    private fun findOrCreateIngredient(name: String): UUID {
        val normalized = normalizeIngredientName(name)
        val existing = IngredientsTable.selectAll()
            .where { IngredientsTable.normalizedName eq normalized }
            .singleOrNull()
        if (existing != null) return existing[IngredientsTable.id].value

        return IngredientsTable.insertAndGetId {
            it[IngredientsTable.name] = name
            it[normalizedName] = normalized
            it[category] = IngredientCategory.guessFrom(name).name
        }.value
    }
}

fun ResultRow.toRecipe(): Recipe = Recipe(
    id = this[RecipesTable.id].value,
    title = this[RecipesTable.title],
    description = this[RecipesTable.description],
    instructions = this[RecipesTable.instructions],
    servings = this[RecipesTable.servings],
    prepMinutes = this[RecipesTable.prepMinutes],
    cookMinutes = this[RecipesTable.cookMinutes],
    sourceUrl = this[RecipesTable.sourceUrl],
    imageFile = this[RecipesTable.imageFile],
    imageUrl = this[RecipesTable.imageUrl],
    createdAt = this[RecipesTable.createdAt],
    updatedAt = this[RecipesTable.updatedAt],
)

fun ResultRow.toRecipeIngredient(): RecipeIngredient = RecipeIngredient(
    id = this[RecipeIngredientsTable.id].value,
    ingredientId = this[IngredientsTable.id].value,
    name = this[IngredientsTable.name],
    category = IngredientCategory.fromDb(this[IngredientsTable.category]),
    quantity = toQuantity(RecipeIngredientsTable.quantity, RecipeIngredientsTable.unit),
    note = this[RecipeIngredientsTable.note],
    position = this[RecipeIngredientsTable.position],
)
