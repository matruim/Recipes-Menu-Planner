package com.family.mealplanner.db

import com.family.mealplanner.domain.MeasurementUnit
import com.family.mealplanner.domain.Quantity
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import java.math.BigDecimal

/** Units are stored as enum names; anything unrecognised degrades to a bare count. */
fun unitFromDb(value: String): MeasurementUnit =
    MeasurementUnit.entries.firstOrNull { it.name == value } ?: MeasurementUnit.COUNT

fun ResultRow.toQuantity(
    quantityColumn: Column<BigDecimal?>,
    unitColumn: Column<String>,
): Quantity = Quantity(this[quantityColumn]?.toDouble(), unitFromDb(this[unitColumn]))

fun Double?.toDbDecimal(): BigDecimal? = this?.let { BigDecimal.valueOf(it).setScale(3, java.math.RoundingMode.HALF_UP) }
