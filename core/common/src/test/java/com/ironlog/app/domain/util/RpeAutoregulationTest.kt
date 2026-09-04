package com.ironlog.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpeAutoregulationTest {

    // --- Ziel-RPE aktiv: Delta-basierte Anpassung (~2,5 % je 1,0 RPE) ---

    @Test
    fun `RPE gleich Ziel laesst Gewicht unveraendert`() {
        assertEquals(80.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 8.0, 8.0)!!, 0.01)
    }

    @Test
    fun `RPE ueber Ziel senkt Gewicht um 2,5 Prozent je 1,0 RPE`() {
        // 8.5 RPE bei Ziel 8.0: Delta -0.5 -> -1.25 % -> 79.0
        assertEquals(79.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 8.5, 8.0)!!, 0.01)
        // 9.5 RPE bei Ziel 8.0: Delta -1.5 -> -3.75 % -> 77.0
        assertEquals(77.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 9.5, 8.0)!!, 0.01)
        // 10.0 RPE bei Ziel 8.0: Delta -2.0 -> -5.0 % -> 76.0
        assertEquals(76.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 10.0, 8.0)!!, 0.01)
    }

    @Test
    fun `RPE unter Ziel erhoeht Gewicht um 2,5 Prozent je 1,0 RPE`() {
        // 8.0 RPE bei Ziel 8.5: Delta +0.5 -> +1.25 % -> 81.0
        assertEquals(81.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 8.0, 8.5)!!, 0.01)
        // 7.0 RPE bei Ziel 9.5: Delta +2.5 -> +6.25 % -> 85.0
        assertEquals(85.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 7.0, 9.5)!!, 0.01)
    }

    @Test
    fun `Anpassung ist auf zehn Prozent pro Schritt begrenzt`() {
        // Delta -4.0 RPE -> -10 % (Cap greift nicht)
        assertEquals(72.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 10.0, 6.0)!!, 0.01)
        // Delta -5.0 RPE -> -12,5 % gekappt auf -10 %
        assertEquals(72.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 10.0, 5.0)!!, 0.01)
        // Delta +4.0 RPE -> +10 %
        assertEquals(88.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 5.0, 9.0)!!, 0.01)
        // Delta +5.0 RPE -> +12,5 % gekappt auf +10 %
        assertEquals(88.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 5.0, 10.0)!!, 0.01)
    }

    @Test
    fun `Ergebnisse werden auf eine Dezimalstelle gerundet`() {
        // 100.0 bei Delta -0.3 RPE -> 99.25 -> 99.3
        assertEquals(99.3, RpeAutoregulation.recommendNextSetWeightKg(100.0, 8.3, 8.0)!!, 0.01)
    }

    // --- Ohne Ziel-RPE: nur bei Overshoot (>= 9.0) ---

    @Test
    fun `ohne Ziel gibt es keine Empfehlung unterhalb des Overshoot`() {
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(80.0, 8.5, null))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(80.0, 8.9, null))
    }

    @Test
    fun `ohne Ziel wird Overshoot gegen RPE 9 als Referenz gerechnet`() {
        assertEquals(80.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 9.0, null)!!, 0.01)
        // 9.5 RPE ohne Ziel: Delta -0.5 -> -1.25 % -> 79.0
        assertEquals(79.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 9.5, null)!!, 0.01)
        // 10.0 RPE ohne Ziel: Delta -1.0 -> -2.5 % -> 78.0
        assertEquals(78.0, RpeAutoregulation.recommendNextSetWeightKg(80.0, 10.0, null)!!, 0.01)
    }

    @Test
    fun `ungueltige Eingaben ergeben keine Empfehlung`() {
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(0.0, 8.0, 8.0))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(-5.0, 8.0, 8.0))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(Double.NaN, 8.0, 8.0))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(80.0, 0.5, 8.0))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(80.0, 10.5, 8.0))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(80.0, Double.NaN, 8.0))
        assertNull(RpeAutoregulation.recommendNextSetWeightKg(Double.POSITIVE_INFINITY, 8.0, 8.0))
    }

    // --- Backoff-Satz ---

    @Test
    fun `Backoff-Satz berechnet festen prozentualen Abfall`() {
        assertEquals(90.0, RpeAutoregulation.backoffSetWeightKg(100.0)!!, 0.01)
        assertEquals(80.0, RpeAutoregulation.backoffSetWeightKg(80.0, 0.0)!!, 0.01)
        assertEquals(70.0, RpeAutoregulation.backoffSetWeightKg(80.0, 12.5)!!, 0.01)
        assertEquals(0.5, RpeAutoregulation.backoffSetWeightKg(1.0, 50.0)!!, 0.01)
        assertEquals(0.0, RpeAutoregulation.backoffSetWeightKg(0.05, 10.0)!!, 0.01)
    }

    @Test
    fun `Backoff mit ungueltigen Eingaben ist null`() {
        assertNull(RpeAutoregulation.backoffSetWeightKg(0.0))
        assertNull(RpeAutoregulation.backoffSetWeightKg(-10.0))
        assertNull(RpeAutoregulation.backoffSetWeightKg(Double.NaN))
        assertNull(RpeAutoregulation.backoffSetWeightKg(100.0, -1.0))
        assertNull(RpeAutoregulation.backoffSetWeightKg(100.0, 60.0))
        assertNull(RpeAutoregulation.backoffSetWeightKg(100.0, Double.NaN))
    }
}