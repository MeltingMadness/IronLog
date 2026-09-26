package com.ironlog.app.presentation.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.shared.readinessdata.SetIntention
import com.ironlog.app.presentation.theme.semantic
import java.util.Locale

/** Formats a weight stored in kg as a plain number in the user's preferred unit system. */
internal fun formatWeightValue(weightKg: Double, unitSystem: UnitSystem): String {
    val displayValue = WeightFormatting.convertToDisplay(weightKg, unitSystem)
    return if (displayValue % 1.0 == 0.0) {
        displayValue.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", displayValue)
    }
}

internal fun formatRpeValue(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }

/**
 * Weight hint for plan-based workout rows: the current plan target weight
 * first (the plan is the source of truth after a progression step), falling
 * back to the last trained weight when the plan carries no weight target.
 * With actual-weight-based progression both sources yield valid suggestions.
 */
internal fun targetWeightHint(
    planTarget: WorkoutPlanTarget?,
    unitSystem: UnitSystem,
    previousWeightHint: String?
): String? = planTarget
    ?.takeIf { it.target.weightKg > 0 }
    ?.let { formatWeightValue(it.target.weightKg, unitSystem) }
    ?: previousWeightHint

/** Formats a plan target weight stored in kg for display in the user's preferred unit system, e.g. "100.0 kg" or "220.5 lb". */
fun formatTargetWeight(weightKg: Double, unitSystem: UnitSystem): String {
    val displayValue = WeightFormatting.convertToDisplay(weightKg, unitSystem)
    return String.format(Locale.ROOT, "%.1f %s", displayValue, WeightFormatting.unitLabel(unitSystem))
}

internal fun setTypeLabel(setNumber: Int, setType: SetType): String = when (setType) {
    SetType.NORMAL -> setNumber.toString()
    SetType.WARMUP -> "W$setNumber"
    SetType.DROP_SET -> "D$setNumber"
    SetType.FAILURE -> "F$setNumber"
}

internal fun SetType.labelRes(): Int = when (this) {
    SetType.NORMAL -> R.string.workout_set_type_normal
    SetType.WARMUP -> R.string.workout_warmup_chip
    SetType.DROP_SET -> R.string.workout_set_type_drop_set
    SetType.FAILURE -> R.string.workout_set_type_failure
}

internal fun SetIntention.labelRes(): Int = when (this) {
    SetIntention.UNKNOWN -> R.string.workout_set_intention_unknown
    SetIntention.PLANNED_FAILURE -> R.string.workout_set_intention_planned_failure
    SetIntention.UNEXPECTED_TARGET_MISS -> R.string.workout_set_intention_unexpected_miss
}

@Composable
internal fun progressionSchemeLabel(config: ProgressionConfig): String = when (config) {
    is ProgressionConfig.Manual -> stringResource(R.string.workout_progression_manual)
    is ProgressionConfig.Linear -> stringResource(R.string.workout_progression_linear)
    is ProgressionConfig.DoubleProgression -> stringResource(R.string.workout_progression_double)
    is ProgressionConfig.TotalReps -> stringResource(R.string.workout_progression_total_reps)
    is ProgressionConfig.RpeRir -> stringResource(R.string.workout_progression_rpe_rir)
    is ProgressionConfig.Invalid -> stringResource(R.string.workout_progression_invalid)
}

internal fun formatIntensity(rpe: Double?, intensitySystem: IntensitySystem): String {
    if (rpe == null || intensitySystem == IntensitySystem.OFF) return ""
    val displayValue = if (intensitySystem == IntensitySystem.RIR) {
        10.0 - rpe
    } else {
        rpe
    }
    return if (displayValue % 1.0 == 0.0) displayValue.toInt().toString() else displayValue.toString()
}

@Composable
internal fun rpeColor(rpe: Double?): Color? {
    if (rpe == null) return null
    return when {
        rpe <= 7.0 -> MaterialTheme.semantic.success   // Grün
        rpe <= 8.0 -> MaterialTheme.semantic.warning    // Amber
        rpe <= 9.0 -> MaterialTheme.semantic.rose.copy(alpha = 0.85f) // Orange-Rose
        else       -> MaterialTheme.semantic.danger     // Rot für RPE 10
    }
}
