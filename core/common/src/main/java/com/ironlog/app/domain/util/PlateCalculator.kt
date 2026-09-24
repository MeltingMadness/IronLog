package com.ironlog.app.domain.util

import com.ironlog.shared.plates.PlateCalculationResult as SharedPlateCalculationResult
import com.ironlog.shared.plates.SharedPlateCalculator

/** Android-facing result kept source-compatible with the existing UI. */
data class PlateCalculationResult(
    val targetWeightKg: Double,
    val barbellWeightKg: Double,
    /** The requested load on one side, excluding the barbell. */
    val weightPerSideKg: Double,
    /** The plates to put on one side, in descending weight order. */
    val platesPerSide: List<Double>,
    /** The amount still missing on one side after loading [platesPerSide]. */
    val remainderKg: Double,
    val isExact: Boolean
) {
    /** The load that can actually be assembled from [platesPerSide]. */
    val reachableWeightPerSideKg: Double
        get() = platesPerSide.sum()
}

/**
 * Compatibility adapter for the Android presentation layer. The algorithm
 * lives in shared KMP code so the iOS workout UI can render the same result.
 */
object PlateCalculator {
    val STANDARD_AVAILABLE_PLATES = SharedPlateCalculator.STANDARD_AVAILABLE_PLATES
    val DEFAULT_USER_PLATES = SharedPlateCalculator.DEFAULT_USER_PLATES
    const val DEFAULT_BARBELL_KG = SharedPlateCalculator.DEFAULT_BARBELL_KG

    fun calculate(
        targetWeightKg: Double,
        barbellWeightKg: Double = DEFAULT_BARBELL_KG,
        availablePlates: List<Double> = DEFAULT_USER_PLATES
    ): PlateCalculationResult = SharedPlateCalculator.calculate(
        targetWeightKg = targetWeightKg,
        barbellWeightKg = barbellWeightKg,
        availablePlates = availablePlates
    ).toAndroidResult()
}

private fun SharedPlateCalculationResult.toAndroidResult() = PlateCalculationResult(
    targetWeightKg = targetWeightKg,
    barbellWeightKg = barbellWeightKg,
    weightPerSideKg = weightPerSideKg,
    platesPerSide = platesPerSide,
    remainderKg = remainderKg,
    isExact = isExact
)
