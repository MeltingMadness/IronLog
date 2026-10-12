package com.thrym.app.presentation.common

import com.thrym.app.domain.error.ExerciseValidationException
import com.thrym.app.domain.error.IntensityRangeException
import com.thrym.app.domain.error.PlanApplyException
import com.thrym.app.domain.util.WorkoutNumericValidation
import com.thrym.core.designsystem.R

/**
 * A failure whose message is already localized UI text, thrown by presentation code
 * (e.g. input validation in a ViewModel) and shown as-is by [userMessageOrNull].
 */
class UserMessageException(message: String) : IllegalArgumentException(message)

/**
 * Localized text for failures the user can act on, or null when the failure has no
 * user-facing copy. Exception messages of other failures are English developer
 * diagnostics and must not reach the UI.
 */
fun Throwable.userMessageOrNull(strings: UiStrings): String? = when (this) {
    is UserMessageException -> message
    is ExerciseValidationException -> when (reason) {
        ExerciseValidationException.Reason.NOT_SAVED -> strings.get(R.string.error_exercise_not_saved)
        ExerciseValidationException.Reason.NOT_FOUND -> strings.get(R.string.error_exercise_not_found)
        ExerciseValidationException.Reason.NOT_CUSTOM_EDIT -> strings.get(R.string.error_exercise_not_custom_edit)
        ExerciseValidationException.Reason.NOT_CUSTOM_DELETE -> strings.get(R.string.error_exercise_not_custom_delete)
        ExerciseValidationException.Reason.DUPLICATE_NAME -> strings.get(R.string.error_exercise_duplicate_name)
        ExerciseValidationException.Reason.NAME_LENGTH -> strings.get(
            R.string.error_exercise_name_length,
            ExerciseValidationException.NAME_MIN_LENGTH,
            ExerciseValidationException.NAME_MAX_LENGTH
        )
        ExerciseValidationException.Reason.NOTES_TOO_LONG ->
            strings.get(R.string.error_exercise_notes_too_long, ExerciseValidationException.NOTES_MAX_LENGTH)
        ExerciseValidationException.Reason.TOO_MANY_SECONDARY_GROUPS -> strings.get(R.string.error_exercise_too_many_secondary)
        ExerciseValidationException.Reason.PRIMARY_ALSO_SECONDARY -> strings.get(R.string.error_exercise_primary_also_secondary)
    }
    is IntensityRangeException -> when (reason) {
        IntensityRangeException.Reason.RPE_OUT_OF_RANGE -> strings.get(
            R.string.error_intensity_rpe_range,
            WorkoutNumericValidation.MIN_RPE.toInt(),
            WorkoutNumericValidation.MAX_RPE.toInt()
        )
        IntensityRangeException.Reason.RIR_OUT_OF_RANGE -> strings.get(
            R.string.error_intensity_rir_range,
            WorkoutNumericValidation.MIN_RIR.toInt(),
            WorkoutNumericValidation.MAX_RIR.toInt()
        )
    }
    is PlanApplyException -> strings.get(
        when (reason) {
            PlanApplyException.Reason.SESSION_NOT_FOUND -> R.string.error_plan_apply_session_not_found
            PlanApplyException.Reason.SESSION_NOT_FINISHED -> R.string.error_plan_apply_session_not_finished
            PlanApplyException.Reason.NO_PLAN -> R.string.error_plan_apply_no_plan
            PlanApplyException.Reason.PLAN_EXERCISE_MISSING -> R.string.error_plan_apply_plan_exercise_missing
            PlanApplyException.Reason.PLAN_CHANGED -> R.string.error_plan_apply_plan_changed
        }
    )
    else -> null
}

/**
 * Detail for "Couldn't … : %1$s" messages: the typed copy from [userMessageOrNull], or a
 * generic localized text instead of the raw exception message.
 */
fun Throwable.toUserDetail(strings: UiStrings): String =
    userMessageOrNull(strings) ?: strings.get(R.string.common_error_unknown)
