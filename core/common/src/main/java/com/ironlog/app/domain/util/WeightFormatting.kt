package com.ironlog.app.domain.util

import com.ironlog.app.domain.model.UnitSystem
import java.util.Locale

object WeightFormatting {
    private const val KG_TO_LB = 2.2046226218

    fun formatWeight(weightKg: Double, unitSystem: UnitSystem): String {
        val value = convertToDisplay(weightKg, unitSystem)
        val formatted = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
        return "$formatted ${unitLabel(unitSystem)}"
    }

    fun formatVolume(volumeKg: Double, unitSystem: UnitSystem): String {
        val value = convertToDisplay(volumeKg, unitSystem)
        val formatted = if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.0f", value)
        }
        return "$formatted ${unitLabel(unitSystem)}"
    }

    /** Converts a value stored in kg into the unit the user should see (kg or lb). */
    fun convertToDisplay(valueKg: Double, unitSystem: UnitSystem): Double =
        if (unitSystem == UnitSystem.IMPERIAL) valueKg * KG_TO_LB else valueKg

    /** Converts a value typed by the user (in their preferred unit) back into kg for storage. */
    fun convertToKg(displayValue: Double, unitSystem: UnitSystem): Double =
        if (unitSystem == UnitSystem.IMPERIAL) displayValue / KG_TO_LB else displayValue

    fun unitLabel(unitSystem: UnitSystem): String =
        if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
}
