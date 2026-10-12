package com.thrym.app.domain.util

import com.thrym.app.domain.error.IntensityRangeException
import com.thrym.app.domain.model.IntensitySystem
import com.thrym.app.domain.model.WorkoutSet

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
            if (!(it.isFinite() && it in MIN_RPE..MAX_RPE)) {
                throw IntensityRangeException(
                    IntensityRangeException.Reason.RPE_OUT_OF_RANGE,
                    "RPE must be between $MIN_RPE and $MAX_RPE"
                )
            }
        }
        IntensitySystem.RIR -> {
            if (!isValidRir(rawValue)) {
                throw IntensityRangeException(
                    IntensityRangeException.Reason.RIR_OUT_OF_RANGE,
                    "RIR must be between $MIN_RIR and $MAX_RIR"
                )
            }
            (10.0 - rawValue).also {
                // Keep the conversion guarded as well so an unusual future
                // scale cannot emit an invalid canonical value.
                require(isValidRpe(it)) { "RIR does not convert to a valid RPE" }
            }
        }
    }

    /** Throws before a repository mutation can persist malformed set values. */
    fun requireValidWorkoutSet(set: WorkoutSet) {
        require(set.setNumber > 0) { "Set number must be greater than 0" }
        require(isValidReps(set.reps)) { "Reps must be greater than 0" }
        require(isValidWeightKg(set.weightKg)) { "Weight must be finite and not negative" }
        require(isValidRpe(set.rpe)) { "RPE must be between $MIN_RPE and $MAX_RPE" }
    }
}
