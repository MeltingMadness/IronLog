package com.thrym.app.domain.util

import com.thrym.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WorkoutCalculationsTest {

    // --- Epley-Formel ---

    @Test
    fun `Epley berechnet bekannte Referenzwerte`() {
        assertEquals(132.0, WorkoutCalculations.calculateE1RM(120.0, 3), 0.01)
        assertEquals(116.6667, WorkoutCalculations.calculateE1RM(100.0, 5), 0.01)
        assertEquals(106.6667, WorkoutCalculations.calculateE1RM(80.0, 10), 0.01)
        assertEquals(200.0, WorkoutCalculations.calculateE1RM(100.0, 30), 0.01)
    }

    @Test
    fun `Epley gibt bei einem oder weniger Wiederholungen das Gewicht zurueck`() {
        assertEquals(100.0, WorkoutCalculations.calculateE1RM(100.0, 1), 0.0)
        assertEquals(100.0, WorkoutCalculations.calculateE1RM(100.0, 0), 0.0)
    }

    @Test
    fun `Epley steigt monoton mit den Wiederholungen`() {
        val low = WorkoutCalculations.calculateE1RM(100.0, 5)
        val high = WorkoutCalculations.calculateE1RM(100.0, 10)
        assertEquals(true, high > low)
    }

    // --- Brzycki-Formel ---

    @Test
    fun `Brzycki berechnet bekannte Referenzwerte`() {
        // 1RM = Gewicht / (1,0278 - 0,0278 * Wdh)
        assertEquals(112.5113, WorkoutCalculations.calculateBrzycki1RM(100.0, 5), 0.01)
        assertEquals(106.6951, WorkoutCalculations.calculateBrzycki1RM(80.0, 10), 0.01)
        assertEquals(93.1214, WorkoutCalculations.calculateBrzycki1RM(75.0, 8), 0.01)
        assertEquals(133.3689, WorkoutCalculations.calculateBrzycki1RM(100.0, 10), 0.01)
    }

    @Test
    fun `Brzycki gibt bei einem oder weniger Wiederholungen das Gewicht zurueck`() {
        assertEquals(150.0, WorkoutCalculations.calculateBrzycki1RM(150.0, 1), 0.0)
        assertEquals(150.0, WorkoutCalculations.calculateBrzycki1RM(150.0, 0), 0.0)
    }

    @Test
    fun `Brzycki ist ab 37 Wiederholungen undefiniert und gibt konservativ das Gewicht zurueck`() {
        assertEquals(100.0, WorkoutCalculations.calculateBrzycki1RM(100.0, 37), 0.0)
        assertEquals(100.0, WorkoutCalculations.calculateBrzycki1RM(100.0, 50), 0.0)
    }

    @Test
    fun `Brzycki steigt im Gueltigkeitsbereich monoton mit den Wiederholungen`() {
        val low = WorkoutCalculations.calculateBrzycki1RM(100.0, 3)
        val high = WorkoutCalculations.calculateBrzycki1RM(100.0, 10)
        assertEquals(true, high > low)
    }

    // --- Dispatcher ---

    @Test
    fun `calculate1RM dispatched auf die gewaehlte Formel`() {
        assertEquals(
            WorkoutCalculations.calculateE1RM(100.0, 5),
            WorkoutCalculations.calculate1RM(100.0, 5, OneRepMaxFormula.EPLEY),
            0.0
        )
        assertEquals(
            WorkoutCalculations.calculateBrzycki1RM(100.0, 5),
            WorkoutCalculations.calculate1RM(100.0, 5, OneRepMaxFormula.BRZYCKI),
            0.0
        )
    }

    // --- Delta-Formatierung (Locale explizit, unabhaengig von der JVM-Default-Locale) ---

    @Test
    fun `formatWeightDelta formatiert mit Vorzeichen in kg`() {
        assertEquals("+8,5 kg", WeightFormatting.formatWeightDelta(8.5, UnitSystem.METRIC, DE))
        assertEquals("-8,5 kg", WeightFormatting.formatWeightDelta(-8.5, UnitSystem.METRIC, DE))
        assertEquals("0 kg", WeightFormatting.formatWeightDelta(0.0, UnitSystem.METRIC, DE))
        assertEquals("+10 kg", WeightFormatting.formatWeightDelta(10.0, UnitSystem.METRIC, DE))
        assertEquals("+8.5 kg", WeightFormatting.formatWeightDelta(8.5, UnitSystem.METRIC, EN))
        assertEquals("-8.5 kg", WeightFormatting.formatWeightDelta(-8.5, UnitSystem.METRIC, EN))
    }

    @Test
    fun `formatWeightDelta rechnet in Pfund um`() {
        assertEquals("+2,2 lb", WeightFormatting.formatWeightDelta(1.0, UnitSystem.IMPERIAL, DE))
        assertEquals("-2,2 lb", WeightFormatting.formatWeightDelta(-1.0, UnitSystem.IMPERIAL, DE))
        assertEquals("+2.2 lb", WeightFormatting.formatWeightDelta(1.0, UnitSystem.IMPERIAL, EN))
    }

    // --- Zahlenformat je Locale ---

    @Test
    fun `formatWeight nutzt Dezimalkomma und laesst Nullen weg`() {
        assertEquals("82,5 kg", WeightFormatting.formatWeight(82.5, UnitSystem.METRIC, DE))
        assertEquals("80 kg", WeightFormatting.formatWeight(80.0, UnitSystem.METRIC, DE))
        assertEquals("1,25 kg", WeightFormatting.formatWeight(1.25, UnitSystem.METRIC, DE))
        assertEquals("176,4 lb", WeightFormatting.formatWeight(80.0, UnitSystem.IMPERIAL, DE))
        // Berechnete Werte (z. B. geschaetztes 1RM) mit einer Nachkommastelle.
        assertEquals("88,7 kg", WeightFormatting.formatWeight(88.666, UnitSystem.METRIC, DE))
        assertEquals("+15,8 kg", WeightFormatting.formatWeightDelta(15.833, UnitSystem.METRIC, DE))
        assertEquals("11,75 kg", WeightFormatting.formatWeight(11.75, UnitSystem.METRIC, DE))
    }

    @Test
    fun `formatWeight nutzt im Englischen den Dezimalpunkt`() {
        assertEquals("82.5 kg", WeightFormatting.formatWeight(82.5, UnitSystem.METRIC, EN))
        assertEquals("80 kg", WeightFormatting.formatWeight(80.0, UnitSystem.METRIC, EN))
        assertEquals("1.25 kg", WeightFormatting.formatWeight(1.25, UnitSystem.METRIC, EN))
        assertEquals("176.4 lb", WeightFormatting.formatWeight(80.0, UnitSystem.IMPERIAL, EN))
        assertEquals("88.7 kg", WeightFormatting.formatWeight(88.666, UnitSystem.METRIC, EN))
    }

    @Test
    fun `formatVolume gruppiert Tausender je Locale ohne Nachkommastellen`() {
        assertEquals("12.305 kg", WeightFormatting.formatVolume(12305.0, UnitSystem.METRIC, DE))
        assertEquals("1.280 kg", WeightFormatting.formatVolume(1280.0, UnitSystem.METRIC, DE))
        assertEquals("640 kg", WeightFormatting.formatVolume(640.4, UnitSystem.METRIC, DE))
        assertEquals("12,305 kg", WeightFormatting.formatVolume(12305.0, UnitSystem.METRIC, EN))
        assertEquals("640 kg", WeightFormatting.formatVolume(640.4, UnitSystem.METRIC, EN))
    }

    @Test
    fun `formatNumber und formatInputNumber`() {
        assertEquals("25.000", WeightFormatting.formatNumber(25000.0, 0, DE))
        assertEquals("8,5", WeightFormatting.formatNumber(8.5, locale = DE))
        assertEquals("0", WeightFormatting.formatNumber(-0.0, locale = DE))
        assertEquals("25,000", WeightFormatting.formatNumber(25000.0, 0, EN))
        assertEquals("8.5", WeightFormatting.formatNumber(8.5, locale = EN))
        // Eingabefelder ohne Tausendertrenner, damit der Parser "1250" nicht als 1,25 liest.
        assertEquals("1250", WeightFormatting.formatInputNumber(1250.0, locale = DE))
        assertEquals("82,5", WeightFormatting.formatInputNumber(82.5, locale = DE))
        assertEquals("1250", WeightFormatting.formatInputNumber(1250.0, locale = EN))
        assertEquals("1234.5", WeightFormatting.formatInputNumber(1234.5, locale = EN))
        assertEquals("1234,5", WeightFormatting.formatInputNumber(1234.5, locale = DE))
    }

    @Test
    fun `formatInputNumber ist in beiden Sprachen wieder einlesbar`() {
        for (locale in listOf(EN, DE)) {
            for (value in listOf(0.5, 82.5, 1234.5, 12305.25)) {
                val text = WeightFormatting.formatInputNumber(value, locale = locale)
                // Gleiche Regel wie parseDecimal in den Eingabefeldern: Komma oder Punkt als Dezimaltrenner.
                assertEquals(text, value, text.replace(',', '.').toDouble(), 1e-9)
            }
        }
    }

    private companion object {
        val EN: Locale = Locale.US
        val DE: Locale = Locale.GERMANY
    }
}
