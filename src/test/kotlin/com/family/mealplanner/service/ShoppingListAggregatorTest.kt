package com.family.mealplanner.service

import com.family.mealplanner.domain.IngredientCategory
import com.family.mealplanner.domain.MeasurementUnit
import com.family.mealplanner.domain.Quantity
import com.family.mealplanner.domain.UnitSystem
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingListAggregatorTest {

    private val oliveOil = UUID.randomUUID()
    private val flour = UUID.randomUUID()
    private val salt = UUID.randomUUID()

    private fun demand(
        id: UUID,
        name: String,
        amount: Double?,
        unit: MeasurementUnit,
        category: IngredientCategory = IngredientCategory.PANTRY,
    ) = IngredientDemand(id, name, category, Quantity(amount, unit))

    @Test
    fun `merges the same ingredient across different volume units`() {
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(oliveOil, "olive oil", 2.0, MeasurementUnit.TABLESPOON),
                demand(oliveOil, "olive oil", 8.0, MeasurementUnit.TABLESPOON),
                demand(oliveOil, "olive oil", 2.0, MeasurementUnit.TABLESPOON),
            ),
        )
        assertEquals(1, lines.size)
        assertEquals("3/4 cup", lines.single().quantity.format())
    }

    @Test
    fun `keeps weight and volume of one ingredient on separate lines`() {
        // Without a density there is no honest way to add a cup to 120 grams.
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(flour, "flour", 1.0, MeasurementUnit.CUP),
                demand(flour, "flour", 120.0, MeasurementUnit.GRAM),
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(setOf("1 cup", "120 g"), lines.map { it.quantity.format() }.toSet())
    }

    @Test
    fun `writes the total in whichever system contributed most`() {
        val metricHeavy = ShoppingListAggregator.aggregate(
            listOf(
                demand(oliveOil, "olive oil", 900.0, MeasurementUnit.MILLILITER),
                demand(oliveOil, "olive oil", 1.0, MeasurementUnit.TABLESPOON),
            ),
        ).single()
        assertEquals(MeasurementUnit.MILLILITER, metricHeavy.quantity.unit)

        val usHeavy = ShoppingListAggregator.aggregate(
            listOf(
                demand(oliveOil, "olive oil", 4.0, MeasurementUnit.CUP),
                demand(oliveOil, "olive oil", 10.0, MeasurementUnit.MILLILITER),
            ),
        ).single()
        assertEquals(UnitSystem.US, usHeavy.quantity.unit.system)
    }

    @Test
    fun `adds up counts`() {
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(flour, "cloves garlic", 6.0, MeasurementUnit.COUNT),
                demand(flour, "cloves garlic", 6.0, MeasurementUnit.COUNT),
                demand(flour, "cloves garlic", 4.0, MeasurementUnit.COUNT),
            ),
        )
        assertEquals("16", lines.single().quantity.format())
    }

    @Test
    fun `leaves to-taste ingredients numberless`() {
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(salt, "salt", null, MeasurementUnit.TO_TASTE, IngredientCategory.SPICES),
                demand(salt, "salt", null, MeasurementUnit.TO_TASTE, IngredientCategory.SPICES),
            ),
        )
        assertEquals("to taste", lines.single().quantity.format())
    }

    @Test
    fun `does not mix pinches with to-taste`() {
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(salt, "salt", null, MeasurementUnit.TO_TASTE, IngredientCategory.SPICES),
                demand(salt, "salt", 2.0, MeasurementUnit.PINCH, IngredientCategory.SPICES),
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(setOf("to taste", "2 pinches"), lines.map { it.quantity.format() }.toSet())
    }

    @Test
    fun `rounds an awkward total to a fraction a cook can measure`() {
        // 2 tbsp + 1/2 cup + 2 tbsp + 1 tbsp + 1/2 cup works out at 1.3124 cups.
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(oliveOil, "olive oil", 2.0, MeasurementUnit.TABLESPOON),
                demand(oliveOil, "olive oil", 0.5, MeasurementUnit.CUP),
                demand(oliveOil, "olive oil", 2.0, MeasurementUnit.TABLESPOON),
                demand(oliveOil, "olive oil", 1.0, MeasurementUnit.TABLESPOON),
                demand(oliveOil, "olive oil", 0.5, MeasurementUnit.CUP),
            ),
        )
        assertEquals("1 1/3 cups", lines.single().quantity.format())
    }

    @Test
    fun `orders lines by aisle then name`() {
        val lines = ShoppingListAggregator.aggregate(
            listOf(
                demand(salt, "salt", null, MeasurementUnit.TO_TASTE, IngredientCategory.SPICES),
                demand(flour, "flour", 1.0, MeasurementUnit.CUP, IngredientCategory.PANTRY),
                demand(oliveOil, "apples", 3.0, MeasurementUnit.COUNT, IngredientCategory.PRODUCE),
            ),
        )
        assertEquals(listOf("apples", "flour", "salt"), lines.map { it.name })
    }
}
