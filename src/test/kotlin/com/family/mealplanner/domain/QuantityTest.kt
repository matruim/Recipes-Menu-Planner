package com.family.mealplanner.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class QuantityTest {

    @Test
    fun `renders cooking fractions rather than decimals`() {
        assertEquals("1 1/2", formatCookingAmount(1.5))
        assertEquals("3/4", formatCookingAmount(0.75))
        assertEquals("1/3", formatCookingAmount(1.0 / 3))
        assertEquals("2 2/3", formatCookingAmount(2.0 + 2.0 / 3))
        assertEquals("2", formatCookingAmount(2.0))
    }

    @Test
    fun `rounds away floating point noise`() {
        assertEquals("2", formatCookingAmount(1.995))
        assertEquals("3", formatCookingAmount(3.001))
    }

    @Test
    fun `falls back to a decimal when no fraction is close`() {
        assertEquals("0.05", formatCookingAmount(0.05))
    }

    @Test
    fun `picks the unit a cook would write`() {
        // 12 tbsp is 3/4 cup, and reads better that way.
        val twelveTablespoons = Quantity(12.0, MeasurementUnit.TABLESPOON).humanized()
        assertEquals(MeasurementUnit.CUP, twelveTablespoons.unit)
        assertEquals("3/4 cup", twelveTablespoons.format())

        // Small amounts stay in teaspoons.
        assertEquals("2 tsp", Quantity(2.0, MeasurementUnit.TEASPOON).humanized().format())
    }

    @Test
    fun `avoids fractions no measuring cup has`() {
        // 1/4 cup scaled by 1.5 is 3/8 cup by arithmetic, but 6 tbsp in a kitchen.
        val scaled = Quantity(0.25, MeasurementUnit.CUP).scaledBy(1.5).humanized()
        assertEquals(MeasurementUnit.TABLESPOON, scaled.unit)
        assertEquals("6 tbsp", scaled.format())
    }

    @Test
    fun `still prefers the larger unit when it reads cleanly`() {
        assertEquals("3/4 cup", Quantity(12.0, MeasurementUnit.TABLESPOON).humanized().format())
        assertEquals("3 cups", Quantity(3.0, MeasurementUnit.CUP).humanized().format())
        assertEquals("1 1/2 lbs", Quantity(24.0, MeasurementUnit.OUNCE).humanized().format())
    }

    @Test
    fun `does not demote to an absurd number of small units`() {
        // ~1.09 cups has no clean fraction, but 17 1/2 tbsp is not the answer.
        val awkward = Quantity(17.5, MeasurementUnit.TABLESPOON).humanized()
        assertEquals(MeasurementUnit.CUP, awkward.unit)
        assertEquals("1 1/8 cups", awkward.format())
    }

    @Test
    fun `counts round up because half an onion cannot be bought`() {
        assertEquals("5", Quantity(3.0, MeasurementUnit.COUNT).scaledBy(1.5).humanized().format())
        assertEquals("2", Quantity(1.0, MeasurementUnit.COUNT).scaledBy(1.5).humanized().format())
        // Whole results are left alone.
        assertEquals("6", Quantity(3.0, MeasurementUnit.COUNT).scaledBy(2.0).humanized().format())
    }

    @Test
    fun `keeps metric amounts metric`() {
        assertEquals("500 ml", Quantity(500.0, MeasurementUnit.MILLILITER).humanized().format())
        assertEquals("1 l", Quantity(1000.0, MeasurementUnit.MILLILITER).humanized().format())
        assertEquals("800 g", Quantity(800.0, MeasurementUnit.GRAM).humanized().format())
        assertEquals("1 kg", Quantity(1000.0, MeasurementUnit.GRAM).humanized().format())
    }

    @Test
    fun `promotes ounces to pounds`() {
        assertEquals("2 lbs", Quantity(32.0, MeasurementUnit.OUNCE).humanized().format())
    }

    @Test
    fun `scales by a servings ratio`() {
        val doubled = Quantity(1.5, MeasurementUnit.CUP).scaledBy(2.0)
        assertEquals(3.0, doubled.amount)
    }

    @Test
    fun `formats counts without a unit label`() {
        assertEquals("3", Quantity(3.0, MeasurementUnit.COUNT).format())
    }

    @Test
    fun `formats inexact units with and without a number`() {
        assertEquals("to taste", Quantity(null, MeasurementUnit.TO_TASTE).format())
        assertEquals("2 pinches", Quantity(2.0, MeasurementUnit.PINCH).format())
        assertEquals("1 pinch", Quantity(1.0, MeasurementUnit.PINCH).format())
    }

    @Test
    fun `describes an unmeasured amount as needed`() {
        assertEquals("as needed", Quantity(null, MeasurementUnit.CUP).format())
    }
}
