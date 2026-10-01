package com.family.mealplanner.domain

import java.time.LocalDateTime
import java.util.UUID

/** Shopping list groupings, ordered roughly the way a store is walked. */
enum class IngredientCategory(val label: String) {
    PRODUCE("Produce"),
    MEAT_SEAFOOD("Meat & Seafood"),
    DAIRY_EGGS("Dairy & Eggs"),
    BAKERY("Bakery"),
    FROZEN("Frozen"),
    PANTRY("Pantry"),
    SPICES("Spices & Seasoning"),
    BEVERAGES("Beverages"),
    OTHER("Other"),
    ;

    companion object {
        fun fromDb(value: String): IngredientCategory =
            entries.firstOrNull { it.name == value } ?: OTHER

        /**
         * Best-effort aisle guess from the ingredient name so a new recipe lands in
         * sensible groups without anyone tagging ingredients by hand. Always
         * overridable later; OTHER is a fine answer.
         */
        fun guessFrom(name: String): IngredientCategory {
            val n = normalizeIngredientName(name)
            return KEYWORDS.firstOrNull { (words, _) -> words.any { n.contains(it) } }?.second ?: OTHER
        }

        /**
         * Order matters: the first match wins. Preserved and packaged goods are
         * tested before fresh ones so "canned tomatoes" is pantry, not produce,
         * and "vegetable broth" is pantry, not a beverage.
         */
        private val KEYWORDS: List<Pair<List<String>, IngredientCategory>> = listOf(
            listOf("frozen", "ice cream") to FROZEN,
            listOf("canned", "can of", "jarred", "broth", "stock", "tomato paste",
                "tomato sauce", "coconut milk", "dried") to PANTRY,
            listOf("chicken", "beef", "pork", "bacon", "sausage", "turkey", "lamb", "salmon",
                "shrimp", "fish", "tuna", "steak", "ground ") to MEAT_SEAFOOD,
            listOf("milk", "cheese", "butter", "yogurt", "cream", "egg", "parmesan",
                "mozzarella", "cheddar", "feta") to DAIRY_EGGS,
            listOf("onion", "garlic", "tomato", "lettuce", "spinach", "carrot", "celery",
                "bell pepper", "potato", "broccoli", "cucumber", "lemon",
                "lime", "apple", "banana", "avocado", "mushroom", "zucchini", "kale",
                "cilantro", "parsley", "basil", "ginger", "scallion", "green onion",
                "berry", "grape", "pear", "peach", "orange", "pepper flake") to PRODUCE,
            // No bare "roll": it would also claim "rolled oats" and "paper towels".
            listOf("bread", "tortilla", "bun", "bagel", "baguette", "pita",
                "dinner roll", "croissant") to BAKERY,
            listOf("salt", "pepper", "cumin", "paprika", "oregano", "thyme", "cinnamon",
                "chili powder", "curry", "turmeric", "bay leaf", "nutmeg", "cayenne") to SPICES,
            listOf("water", "juice", "wine", "beer", "soda", "coffee", "tea") to BEVERAGES,
            listOf("flour", "sugar", "rice", "pasta", "spaghetti", "noodle", "penne",
                "macaroni", "oil", "vinegar", "bean", "lentil", "sauce", "oat", "honey",
                "syrup", "yeast", "baking powder", "baking soda", "cornstarch", "quinoa") to PANTRY,
        )
    }
}

data class Recipe(
    val id: UUID,
    val title: String,
    val description: String,
    val instructions: String,
    val servings: Int,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val sourceUrl: String?,
    val imageFile: String?,
    /** Linked instead of copied when the source CDN refuses this server. */
    val imageUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes
}

data class RecipeIngredient(
    val id: UUID,
    val ingredientId: UUID,
    val name: String,
    val category: IngredientCategory,
    val quantity: Quantity,
    val note: String,
    val position: Int,
) {
    fun display(): String = ParsedIngredientLine(name, quantity, note).display()
}

data class RecipeDetail(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient>,
)

/** Everything needed to create or update a recipe, already parsed. */
data class RecipeDraft(
    val title: String,
    val description: String,
    val instructions: String,
    val servings: Int,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val sourceUrl: String?,
    val imageFile: String?,
    val imageUrl: String?,
    val ingredients: List<ParsedIngredientLine>,
)
