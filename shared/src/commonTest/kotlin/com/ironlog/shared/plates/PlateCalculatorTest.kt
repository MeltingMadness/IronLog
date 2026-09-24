package com.ironlog.shared.plates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlateCalculatorTest {
    @Test
    fun exactCombinationPrefersFewestPlatesAndOrdersDescending() {
        val result = SharedPlateCalculator.calculate(
            targetWeightKg = 100.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 20.0, 10.0, 5.0, 2.5, 1.25)
        )

        assertEquals(40.0, result.weightPerSideKg, 0.001)
        assertEquals(listOf(20.0, 20.0), result.platesPerSide)
        assertEquals(40.0, result.reachableWeightPerSideKg, 0.001)
        assertEquals(0.0, result.remainderKg, 0.001)
        assertTrue(result.isExact)
    }

    @Test
    fun nonGreedyExactCombinationBeatsLargestPlateFirst() {
        val result = SharedPlateCalculator.calculate(
            targetWeightKg = 80.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 15.0)
        )

        assertEquals(listOf(15.0, 15.0), result.platesPerSide)
        assertTrue(result.isExact)
    }

    @Test
    fun unreachableRequestReportsRemainderWithoutOverweightPlate() {
        val result = SharedPlateCalculator.calculate(
            targetWeightKg = 20.9999,
            barbellWeightKg = 20.0,
            availablePlates = listOf(0.5)
        )

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(0.49995, result.remainderKg, 0.00001)
        assertFalse(result.isExact)
    }

    @Test
    fun malformedInputsRemainFiniteAndSafe() {
        val result = SharedPlateCalculator.calculate(
            targetWeightKg = Double.POSITIVE_INFINITY,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 15.0)
        )

        assertTrue(result.targetWeightKg.isFinite())
        assertTrue(result.barbellWeightKg.isFinite())
        assertTrue(result.platesPerSide.isEmpty())
        assertFalse(result.isExact)
    }

    @Test
    fun jsonFacadeIncludesReachableWeight() {
        val json = PlateCalculatorFacade.calculateJson(
            targetWeightKg = 60.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(20.0)
        )

        assertTrue(json.contains("\"reachableWeightPerSideKg\":20.0"))
        assertTrue(json.contains("\"platesPerSide\":[20.0]"))
    }
}
