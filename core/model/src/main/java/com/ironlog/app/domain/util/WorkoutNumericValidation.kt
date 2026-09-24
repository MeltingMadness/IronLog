package com.ironlog.app.domain.util

import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.WorkoutSet

/**
 * Numeric boundaries shared by workout input and persistence paths.
 *
 * Workout records are stored as canonical kilograms and RPE values. Keeping these
 * checks in the model module prevents the UI, repository and test fakes from
 * quietly choosing different interpretations for malformed values.
 */
object WorkoutNumericValidation {
    const val MIN_RPE = 1.0
    const val MAX_RPE = 10.0
    const val MIN_RIR = 0.0
    const val MAX_RIR = 9.0

    fun isValidReps(reps: Int): Boolean = reps > 0

    fun isValidWeightKg(weightKg: Double): Boolean =
        weightKg.isFinite() && weightKg >= 0.0

    fun isValidRpe(rpe: Double?): Boolean =
        rpe == null || (rpe.isFinite() && rpe in MIN_RPE..MAX_RPE)

    fun isValidRir(rir: Double): Boolean =
        rir.isFinite() && rir in MIN_RIR..MAX_RIR

    /**
     * Converts the value entered on the active-workout intensity scale to the
     * canonical stored RPE. A blank value is represented by null; a non-blank
     * value must already have been parsed and satisfy its scale's bounds.
     */
    fun storedRpe(rawValue: Double, intensitySystem: IntensitySystem): Double? = when (intensitySystem) {
        IntensitySystem.OFF -> null
        IntensitySystem.RPE -> rawValue.also {
            require(it.isFinite() && it in MIN_RPE..MAX_RPE) {
                "RPE muss zwischen $MIN_RPE und $MAX_RPE liegen"
            }
        }
        IntensitySystem.RIR -> {
            require(isValidRir(rawValue)) {
                "RIR muss zwischen $MIN_RIR und $MAX_RIR liegen"
            }
            (10.0 - rawValue).also {
                // Keep the conversion guarded as well so an unusual future
                // scale cannot emit an invalid canonical value.
                require(isValidRpe(it)) { "RIR ergibt kein gültiges RPE" }
            }
        }
    }

    /** Throws before a repository mutation can persist malformed set values. */
    fun requireValidWorkoutSet(set: WorkoutSet) {
        require(set.setNumber > 0) { "Satznummer muss größer als 0 sein" }
        require(isValidReps(set.reps)) { "Wiederholungen müssen größer als 0 sein" }
        require(isValidWeightKg(set.weightKg)) { "Gewicht muss endlich und nicht negativ sein" }
        require(isValidRpe(set.rpe)) { "RPE muss zwischen $MIN_RPE und $MAX_RPE liegen" }
    }
}
