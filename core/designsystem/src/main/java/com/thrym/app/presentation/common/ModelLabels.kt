package com.thrym.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.thrym.app.domain.model.ExerciseCategory
import com.thrym.app.domain.model.IntensitySystem
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.domain.model.RecordType
import com.thrym.core.designsystem.R

/*
 * Localized labels for persisted enum codes. The enum constants (BRUST, LANGHANTEL,
 * MAX_WEIGHT, ...) are stored in Room and in backups and must never be renamed; only
 * their display text lives in resources (model_strings.xml).
 */

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.BRUST -> R.string.muscle_group_brust
    MuscleGroup.RUECKEN -> R.string.muscle_group_ruecken
    MuscleGroup.BEINE -> R.string.muscle_group_beine
    MuscleGroup.SCHULTERN -> R.string.muscle_group_schultern
    MuscleGroup.BIZEPS -> R.string.muscle_group_bizeps
    MuscleGroup.TRIZEPS -> R.string.muscle_group_trizeps
    MuscleGroup.GESAESS -> R.string.muscle_group_gesaess
    MuscleGroup.CORE -> R.string.muscle_group_core
    MuscleGroup.UNTERARME -> R.string.muscle_group_unterarme
    MuscleGroup.WADEN -> R.string.muscle_group_waden
}

@StringRes
fun ExerciseCategory.labelRes(): Int = when (this) {
    ExerciseCategory.LANGHANTEL -> R.string.exercise_category_langhantel
    ExerciseCategory.KURZHANTEL -> R.string.exercise_category_kurzhantel
    ExerciseCategory.MASCHINE -> R.string.exercise_category_maschine
    ExerciseCategory.KABEL -> R.string.exercise_category_kabel
    ExerciseCategory.EIGENGEWICHT -> R.string.exercise_category_eigengewicht
}

@StringRes
fun RecordType.labelRes(): Int = when (this) {
    RecordType.MAX_WEIGHT -> R.string.record_type_max_weight
    RecordType.MAX_REPS -> R.string.record_type_max_reps
    RecordType.MAX_VOLUME -> R.string.record_type_max_volume
    RecordType.MAX_E1RM -> R.string.record_type_max_e1rm
}

@StringRes
fun IntensitySystem.labelRes(): Int = when (this) {
    IntensitySystem.OFF -> R.string.settings_intensity_off
    IntensitySystem.RPE -> R.string.intensity_system_rpe
    IntensitySystem.RIR -> R.string.intensity_system_rir
}

@Composable
fun MuscleGroup.label(): String = stringResource(labelRes())

@Composable
fun ExerciseCategory.label(): String = stringResource(labelRes())

@Composable
fun RecordType.label(): String = stringResource(labelRes())

@Composable
fun IntensitySystem.label(): String = stringResource(labelRes())
