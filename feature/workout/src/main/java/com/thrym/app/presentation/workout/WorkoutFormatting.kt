package com.thrym.app.presentation.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.thrym.core.designsystem.R
import com.thrym.app.domain.model.IntensitySystem
import com.thrym.app.domain.model.ProgressionConfig
import com.thrym.app.domain.model.SetType
import com.thrym.app.domain.model.UnitSystem
import com.thrym.app.domain.model.WorkoutPlanTarget
import com.thrym.app.domain.util.WeightFormatting
import com.thrym.shared.readinessdata.SetIntention
import com.thrym.app.presentation.theme.semantic
import com.thrym.app.presentation.theme.semanticText
import com.thrym.feature.workout.R as WorkoutR
import java.util.Locale

/**
 * Formats a weight stored in kg as a plain number in the user's preferred unit system,
 * e.g. "82.5" (en) / "82,5" (de). No thousands separator, because the value also prefills
 * input fields.
 */
internal fun formatWeightValue(weightKg: Double, unitSystem: UnitSystem, locale: Locale = Locale.getDefault()): String {
    val displayValue = WeightFormatting.convertToDisplay(weightKg, unitSystem)
    return WeightFormatting.formatInputNumber(
        displayValue,
        maxFractionDigits = WeightFormatting.weightDecimals(displayValue, unitSystem),
        locale = locale
    )
}

internal fun formatRpeValue(value: Double, locale: Locale = Locale.getDefault()): String =
    WeightFormatting.formatNumber(value, locale = locale)

/**
 * Weight hint for plan-based workout rows: the current plan target weight
 * first (the plan is the source of truth after a progression step), falling
 * back to the last trained weight when the plan carries no weight target.
 * With actual-weight-based progression both sources yield valid suggestions.
 */
internal fun targetWeightHint(
    planTarget: WorkoutPlanTarget?,
    unitSystem: UnitSystem,
    previousWeightHint: String?,
    locale: Locale = Locale.getDefault()
): String? = planTarget
    ?.takeIf { it.target.weightKg > 0 }
    ?.let { formatWeightValue(it.target.weightKg, unitSystem, locale) }
    ?: previousWeightHint

/**
 * Formats a plan target weight stored in kg for display in the user's preferred unit system,
 * e.g. "100 kg" or "220.5 lb" (en) / "220,5 lb" (de).
 */
fun formatTargetWeight(weightKg: Double, unitSystem: UnitSystem, locale: Locale = Locale.getDefault()): String =
    WeightFormatting.formatWeight(weightKg, unitSystem, locale)

/** Compact set badge: the number for normal sets, a type letter plus number otherwise (e.g. "W1"). */
@Composable
internal fun setTypeLabel(setNumber: Int, setType: SetType): String = when (setType) {
    SetType.NORMAL -> setNumber.toString()
    SetType.WARMUP -> stringResource(WorkoutR.string.workout_set_badge_warmup, setNumber)
    SetType.DROP_SET -> stringResource(WorkoutR.string.workout_set_badge_drop_set, setNumber)
    SetType.FAILURE -> stringResource(WorkoutR.string.workout_set_badge_failure, setNumber)
    SetType.BACKOFF -> stringResource(WorkoutR.string.workout_set_badge_backoff, setNumber)
}

internal fun SetType.labelRes(): Int = when (this) {
    SetType.NORMAL -> R.string.workout_set_type_normal
    SetType.WARMUP -> R.string.workout_warmup_chip
    SetType.DROP_SET -> R.string.workout_set_type_drop_set
    SetType.FAILURE -> R.string.workout_set_type_failure
    SetType.BACKOFF -> R.string.workout_set_type_backoff
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

internal fun formatIntensity(rpe: Double?, intensitySystem: IntensitySystem, locale: Locale = Locale.getDefault()): String {
    if (rpe == null || intensitySystem == IntensitySystem.OFF) return ""
    val displayValue = if (intensitySystem == IntensitySystem.RIR) {
        10.0 - rpe
    } else {
        rpe
    }
    return WeightFormatting.formatNumber(displayValue, locale = locale)
}

/** RPE labels use opaque ink; [rpeColor] is reserved for fills and borders. */
@Composable
internal fun rpeTextColor(rpe: Double?): Color? {
    if (rpe == null) return null
    return when {
        rpe <= 7.0 -> MaterialTheme.semanticText.success
        rpe <= 8.0 -> MaterialTheme.semanticText.warning
        rpe <= 9.0 -> MaterialTheme.semanticText.rose
        else -> MaterialTheme.semanticText.danger
    }
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
