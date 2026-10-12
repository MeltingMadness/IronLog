package com.thrym.app.domain.error

/*
 * Failures the user can act on. Each carries a stable reason that the presentation layer
 * maps to localized copy (core/designsystem ErrorText.kt). The exception message is English
 * developer text for logs and must not be shown in the UI. The supertypes are kept from the
 * previous require()/check() calls, so toAppError() and existing callers behave the same.
 */

/** Rejected custom-exercise edit (validation in ExerciseRepository). */
class ExerciseValidationException(
    val reason: Reason,
    message: String
) : IllegalArgumentException(message) {
    enum class Reason {
        NOT_SAVED,
        NOT_FOUND,
        NOT_CUSTOM_EDIT,
        NOT_CUSTOM_DELETE,
        DUPLICATE_NAME,
        NAME_LENGTH,
        NOTES_TOO_LONG,
        TOO_MANY_SECONDARY_GROUPS,
        PRIMARY_ALSO_SECONDARY
    }

    companion object {
        const val NAME_MIN_LENGTH = 3
        const val NAME_MAX_LENGTH = 60
        const val NOTES_MAX_LENGTH = 240
        const val MAX_SECONDARY_GROUPS = 3
    }
}

/** Entered intensity outside its scale (WorkoutNumericValidation.storedRpe). */
class IntensityRangeException(
    val reason: Reason,
    message: String
) : IllegalArgumentException(message) {
    enum class Reason { RPE_OUT_OF_RANGE, RIR_OUT_OF_RANGE }
}

/** Performed sets could not be written back to the plan (TrainingPlanDao.applyPerformedSetTargets). */
class PlanApplyException(
    val reason: Reason,
    message: String
) : IllegalStateException(message) {
    enum class Reason {
        SESSION_NOT_FOUND,
        SESSION_NOT_FINISHED,
        NO_PLAN,
        PLAN_EXERCISE_MISSING,
        PLAN_CHANGED
    }
}
