package com.thrym.app.domain.util

import com.thrym.app.domain.model.UnitSystem
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object WeightFormatting {
    private const val KG_TO_LB = 2.2046226218

    /**
     * Weight in the user's unit, formatted for [locale]: "82.5 kg" (en), "82,5 kg" (de),
     * "80 kg", "1.25 kg". Metric shows up to two decimals (small plates), pounds one.
     */
    fun formatWeight(weightKg: Double, unitSystem: UnitSystem, locale: Locale = Locale.getDefault()): String =
        "${formatWeightNumber(weightKg, unitSystem, locale)} ${unitLabel(unitSystem)}"

    /** Like [formatWeight] but without the unit, e.g. for "80 kg × 8" compositions. */
    fun formatWeightNumber(weightKg: Double, unitSystem: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val value = convertToDisplay(weightKg, unitSystem)
        return formatNumber(value, weightDecimals(value, unitSystem), locale)
    }

    /** Volume without decimals, with grouping: "12,305 kg" (en), "12.305 kg" (de). */
    fun formatVolume(volumeKg: Double, unitSystem: UnitSystem, locale: Locale = Locale.getDefault()): String =
        "${formatNumber(convertToDisplay(volumeKg, unitSystem), 0, locale)} ${unitLabel(unitSystem)}"

    /**
     * Signed weight difference (e.g. 1RM progress) in the user's unit,
     * e.g. "+8.5 kg" / "-2.2 lb" (en), "+8,5 kg" / "-2,2 lb" (de).
     */
    fun formatWeightDelta(deltaKg: Double, unitSystem: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val formatted = formatWeightNumber(deltaKg, unitSystem, locale)
        val sign = if (deltaKg > 0) "+" else ""
        return "$sign$formatted ${unitLabel(unitSystem)}"
    }

    /**
     * Number with the [locale]'s grouping and decimal separator and no trailing zeros:
     * 12305.0 -> "12,305" (en) / "12.305" (de), 82.5 -> "82.5" / "82,5", 8.0 -> "8".
     */
    fun formatNumber(value: Double, maxFractionDigits: Int = 1, locale: Locale = Locale.getDefault()): String =
        decimalFormat(maxFractionDigits, grouping = true, locale).format(normalizeZero(value))

    /**
     * Like [formatNumber] but without grouping: "1234.5" (en) / "1234,5" (de). For prefilled
     * input fields, whose parser reads both comma and dot as the decimal separator and would
     * misread a grouping separator ("1,234" would become 1.234).
     */
    fun formatInputNumber(value: Double, maxFractionDigits: Int = 2, locale: Locale = Locale.getDefault()): String =
        decimalFormat(maxFractionDigits, grouping = false, locale).format(normalizeZero(value))

    /**
     * Two decimals only for real quarter steps in kg (1.25 kg plates), otherwise one:
     * computed values such as an estimated 1RM show as "88.7 kg".
     */
    fun weightDecimals(displayValue: Double, unitSystem: UnitSystem): Int {
        if (unitSystem == UnitSystem.IMPERIAL) return 1
        val quarters = displayValue * 4.0
        return if (abs(quarters - quarters.roundToLong()) < 1e-6) 2 else 1
    }

    /** Avoids "-0" for tiny negative values that round to zero. */
    private fun normalizeZero(value: Double): Double = if (value == 0.0) 0.0 else value

    private fun decimalFormat(maxFractionDigits: Int, grouping: Boolean, locale: Locale): DecimalFormat =
        DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale)).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = maxFractionDigits
            isGroupingUsed = grouping
            roundingMode = RoundingMode.HALF_UP
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
