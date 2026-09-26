package com.ironlog.app.presentation.history

import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySetInputTest {

    private fun parse(
        weight: String = "82,5",
        reps: String = "8",
        intensity: String = "",
        unit: UnitSystem = UnitSystem.METRIC,
        system: IntensitySystem = IntensitySystem.RPE,
        currentRpe: Double? = 7.0
    ) = parseHistorySetInput(weight, reps, intensity, unit, system, currentRpe)

    @Test
    fun `Komma als Dezimaltrenner und leere Intensitaet werden akzeptiert`() {
        assertEquals(
            HistorySetInputResult.Valid(HistorySetInput(reps = 8, weightKg = 82.5, rpe = null)),
            parse()
        )
    }

    @Test
    fun `RIR wird als RPE gespeichert`() {
        val result = parse(intensity = "2", system = IntensitySystem.RIR) as HistorySetInputResult.Valid
        assertEquals(8.0, result.input.rpe!!, 0.0)
    }

    @Test
    fun `ohne Intensitaets-Erfassung bleibt die gespeicherte RPE erhalten`() {
        val result = parse(intensity = "", system = IntensitySystem.OFF) as HistorySetInputResult.Valid
        assertEquals(7.0, result.input.rpe!!, 0.0)
    }

    @Test
    fun `Pfund werden in Kilogramm umgerechnet`() {
        val result = parse(weight = "100", unit = UnitSystem.IMPERIAL) as HistorySetInputResult.Valid
        assertEquals(45.36, result.input.weightKg, 0.01)
    }

    @Test
    fun `ungueltige Eingaben werden benannt`() {
        assertEquals(HistorySetInputResult.InvalidWeight, parse(weight = "-5"))
        assertEquals(HistorySetInputResult.InvalidWeight, parse(weight = "abc"))
        assertEquals(HistorySetInputResult.InvalidReps, parse(reps = "8,5"))
        assertEquals(HistorySetInputResult.InvalidIntensity, parse(intensity = "11"))
    }
}
