package com.ironlog.app.domain.util

import com.ironlog.app.domain.model.UnitSystem
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object WeightFormatting {
    private const val KG_TO_LB = 2.2046226218

    /**
     * Gewicht in der Nutzer-Einheit, deutsch formatiert: "82,5 kg", "80 kg", "1,25 kg".
     * Metrisch bis zwei Nachkommastellen (kleine Scheiben), Pfund eine.
     */
    fun formatWeight(weightKg: Double, unitSystem: UnitSystem): String =
        "${formatWeightNumber(weightKg, unitSystem)} ${unitLabel(unitSystem)}"

    /** Wie [formatWeight], aber ohne Einheit, z. B. fuer "80 kg × 8"-Zusammensetzungen. */
    fun formatWeightNumber(weightKg: Double, unitSystem: UnitSystem): String {
        val value = convertToDisplay(weightKg, unitSystem)
        return formatNumber(value, weightDecimals(value, unitSystem))
    }

    /** Volumen ohne Nachkommastellen mit Tausenderpunkt: "12.305 kg". */
    fun formatVolume(volumeKg: Double, unitSystem: UnitSystem): String =
        "${formatNumber(convertToDisplay(volumeKg, unitSystem), 0)} ${unitLabel(unitSystem)}"

    /**
     * Formatiert eine Gewichts-Differenz (z. B. 1RM-Fortschritt) mit Vorzeichen
     * in der Nutzer-Einheit, z. B. "+8,5 kg" bzw. "-2,2 lb".
     */
    fun formatWeightDelta(deltaKg: Double, unitSystem: UnitSystem): String {
        val formatted = formatWeightNumber(deltaKg, unitSystem)
        val sign = if (deltaKg > 0) "+" else ""
        return "$sign$formatted ${unitLabel(unitSystem)}"
    }

    /**
     * Deutsche Zahl mit Tausenderpunkt, Dezimalkomma und ohne ueberfluessige Nullen:
     * 12305.0 -> "12.305", 82.5 -> "82,5", 8.0 -> "8".
     */
    fun formatNumber(value: Double, maxFractionDigits: Int = 1): String =
        germanFormat(maxFractionDigits, grouping = true).format(normalizeZero(value))

    /**
     * Wie [formatNumber], aber ohne Tausenderpunkt. Fuer vorbelegte Eingabefelder, deren
     * Parser Komma und Punkt als Dezimaltrenner liest und einen Tausenderpunkt
     * falsch verstehen wuerde.
     */
    fun formatInputNumber(value: Double, maxFractionDigits: Int = 2): String =
        germanFormat(maxFractionDigits, grouping = false).format(normalizeZero(value))

    /**
     * Zwei Nachkommastellen nur fuer echte Viertel-Schritte in kg (1,25-kg-Scheiben),
     * sonst eine: berechnete Werte wie ein geschaetztes 1RM erscheinen als "88,7 kg".
     */
    fun weightDecimals(displayValue: Double, unitSystem: UnitSystem): Int {
        if (unitSystem == UnitSystem.IMPERIAL) return 1
        val quarters = displayValue * 4.0
        return if (abs(quarters - quarters.roundToLong()) < 1e-6) 2 else 1
    }

    /** Avoids "-0" for tiny negative values that round to zero. */
    private fun normalizeZero(value: Double): Double = if (value == 0.0) 0.0 else value

    private fun germanFormat(maxFractionDigits: Int, grouping: Boolean): DecimalFormat =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.GERMANY)).apply {
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
