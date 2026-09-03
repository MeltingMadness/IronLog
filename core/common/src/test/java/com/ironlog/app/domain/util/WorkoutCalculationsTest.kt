package com.ironlog.app.domain.util

import com.ironlog.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

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

    // --- Delta-Formatierung ---

    @Test
    fun `formatWeightDelta formatiert mit Vorzeichen in kg`() {
        assertEquals("+8.5 kg", WeightFormatting.formatWeightDelta(8.5, UnitSystem.METRIC))
        assertEquals("-8.5 kg", WeightFormatting.formatWeightDelta(-8.5, UnitSystem.METRIC))
        assertEquals("0 kg", WeightFormatting.formatWeightDelta(0.0, UnitSystem.METRIC))
        assertEquals("+10 kg", WeightFormatting.formatWeightDelta(10.0, UnitSystem.METRIC))
    }

    @Test
    fun `formatWeightDelta rechnet in Pfund um`() {
        assertEquals("+2.2 lb", WeightFormatting.formatWeightDelta(1.0, UnitSystem.IMPERIAL))
        assertEquals("-2.2 lb", WeightFormatting.formatWeightDelta(-1.0, UnitSystem.IMPERIAL))
    }
}