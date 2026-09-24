package com.ironlog.shared.deload

import com.ironlog.shared.backup.BackupProgressionTarget
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.model.DeloadMode
import kotlin.math.round

/**
 * Applies the active deload only to the target shown while a workout is performed.
 *
 * The stored plan target, workout target snapshot, completed sets, history, and progression
 * evidence remain untouched. Keeping this calculation in shared code prevents the iOS and Android
 * presentation layers from drifting on rounding or minimum-set behavior.
 */
object DeloadTargetAdjustment {
    fun adjust(
        sets: Int,
        reps: Int,
        weightKg: Double,
        mode: DeloadMode,
    ): BackupProgressionTarget = when (mode) {
        DeloadMode.NONE -> BackupProgressionTarget(sets = sets, reps = reps, weightKg = weightKg)
        DeloadMode.HALVE_SET_VOLUME -> BackupProgressionTarget(
            sets = maxOf(1, (sets + 1) / 2),
            reps = reps,
            weightKg = weightKg,
        )
        DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT -> BackupProgressionTarget(
            sets = sets,
            reps = reps,
            weightKg = roundToOneDecimal(weightKg * 0.85),
        )
    }

    /** Swift-friendly adapter for the raw names persisted by SettingsFormState. */
    fun adjustByName(
        sets: Int,
        reps: Int,
        weightKg: Double,
        modeName: String,
    ): BackupProgressionTarget = adjust(
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        mode = DeloadMode.entries.firstOrNull { it.name == modeName } ?: DeloadMode.NONE,
    )

    /** Applies the same display-only adjustment to a complete shared target snapshot. */
    fun apply(target: BackupWorkoutPlanTarget, mode: DeloadMode): BackupWorkoutPlanTarget =
        target.copy(
            target = adjust(
                sets = target.target.sets,
                reps = target.target.reps,
                weightKg = target.target.weightKg,
                mode = mode,
            ),
        )

    private fun roundToOneDecimal(value: Double): Double = round(value * 10.0) / 10.0
}
