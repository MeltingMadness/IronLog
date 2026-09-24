package com.ironlog.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    @Test
    fun calculate_withDefaultPlates_computesExactBreakdown() {
        // 100 kg total with 20 kg bar = 40 kg per side -> 25 kg + 15 kg
        val result = PlateCalculator.calculate(
            targetWeightKg = 100.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        )

        assertEquals(40.0, result.weightPerSideKg, 0.001)
        assertEquals(listOf(25.0, 15.0), result.platesPerSide)
        assertEquals(0.0, result.remainderKg, 0.001)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_prefersExactNonGreedyCombination() {
        // A greedy 25 kg choice leaves 5 kg, but two 15 kg plates reach the
        // exact 30 kg required on each side.
        val result = PlateCalculator.calculate(
            targetWeightKg = 80.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 15.0)
        )

        assertEquals(listOf(15.0, 15.0), result.platesPerSide)
        assertEquals(30.0, result.reachableWeightPerSideKg, 0.001)
        assertEquals(0.0, result.remainderKg, 0.001)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_without15KgPlates_fallsBackToNextAvailablePlates() {
        // User specific scenario: user has no 15 kg plates!
        // 100 kg total with 20 kg bar = 40 kg per side
        // The minimum-count exact combination is two 20 kg plates.
        val result = PlateCalculator.calculate(
            targetWeightKg = 100.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 20.0, 10.0, 5.0, 2.5, 1.25)
        )

        assertEquals(40.0, result.weightPerSideKg, 0.001)
        assertEquals(listOf(20.0, 20.0), result.platesPerSide)
        assertEquals(0.0, result.remainderKg, 0.001)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_with60KgTotal_usesSingle20KgPlatePerSide() {
        val result = PlateCalculator.calculate(
            targetWeightKg = 60.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0)
        )

        assertEquals(20.0, result.weightPerSideKg, 0.001)
        assertEquals(listOf(20.0), result.platesPerSide)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_withFractions_handles1_25KgPlates() {
        // 82.5 kg total with 20 kg bar = 31.25 kg per side -> 25 + 5 + 1.25
        val result = PlateCalculator.calculate(
            targetWeightKg = 82.5,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        )

        assertEquals(31.25, result.weightPerSideKg, 0.001)
        assertEquals(listOf(25.0, 5.0, 1.25), result.platesPerSide)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_whenTargetCannotBeMatchedExactly_recordsRemainder() {
        // 83.0 kg total with 20 kg bar = 31.5 kg per side. Available plates smallest is 1.25.
        // 31.5 -> 25 + 5 + 1.25 = 31.25 kg, remainder = 0.25 kg
        val result = PlateCalculator.calculate(
            targetWeightKg = 83.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 10.0, 5.0, 1.25)
        )

        assertEquals(listOf(25.0, 5.0, 1.25), result.platesPerSide)
        assertEquals(0.25, result.remainderKg, 0.001)
        assertFalse(result.isExact)
    }

    @Test
    fun calculate_whenRequestedDenominationIsUnavailable_usesAvailablePlatesOnly() {
        val result = PlateCalculator.calculate(
            targetWeightKg = 80.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(25.0, 10.0)
        )

        // 15 kg is unavailable, but three 10 kg plates still form the exact
        // 30 kg load on each side.
        assertEquals(listOf(10.0, 10.0, 10.0), result.platesPerSide)
        assertEquals(30.0, result.reachableWeightPerSideKg, 0.001)
        assertEquals(0.0, result.remainderKg, 0.001)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_ignoresNonFiniteAndUnusableDenominations() {
        val result = PlateCalculator.calculate(
            targetWeightKg = 80.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE)
        )

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(30.0, result.remainderKg, 0.001)
        assertFalse(result.isExact)
    }

    @Test
    fun calculate_ignoresFiniteDenominationThatCannotFit() {
        val result = PlateCalculator.calculate(
            targetWeightKg = 80.0,
            barbellWeightKg = 20.0,
            availablePlates = listOf(1_000_000_000_000.0, 15.0)
        )

        assertEquals(listOf(15.0, 15.0), result.platesPerSide)
        assertTrue(result.isExact)
    }

    @Test
    fun calculate_doesNotRoundPlateAboveRequestedLoad() {
        // 20.9999 kg total needs 0.49995 kg per side.  A 0.5 kg plate is
        // close, but still heavier than the requested load and must not be
        // reported as an exact fit.
        val result = PlateCalculator.calculate(
            targetWeightKg = 20.9999,
            barbellWeightKg = 20.0,
            availablePlates = listOf(0.5)
        )

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(0.49995, result.remainderKg, 0.00001)
        assertFalse(result.isExact)
    }

    @Test
    fun calculate_withNonFiniteInput_returnsFiniteEmptyResult() {
        val result = PlateCalculator.calculate(
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
    fun calculate_whenWeightIsBarbellWeightOrLess_returnsEmptyPlates() {
        val resultAtBar = PlateCalculator.calculate(20.0, 20.0)
        assertTrue(resultAtBar.platesPerSide.isEmpty())
        assertTrue(resultAtBar.isExact)

        val resultBelowBar = PlateCalculator.calculate(15.0, 20.0)
        assertTrue(resultBelowBar.platesPerSide.isEmpty())
        assertFalse(resultBelowBar.isExact)
    }
}
