package com.ironlog.shared.deload

import com.ironlog.shared.backup.BackupProgressionConfig
import com.ironlog.shared.backup.BackupProgressionTarget
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.model.DeloadMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DeloadTargetAdjustmentTest {
    @Test
    fun halveSetVolumeRoundsThreeSetsUpToTwo() {
        val adjusted = DeloadTargetAdjustment.adjust(
            sets = 3,
            reps = 8,
            weightKg = 60.0,
            mode = DeloadMode.HALVE_SET_VOLUME,
        )

        assertEquals(2, adjusted.sets)
        assertEquals(8, adjusted.reps)
        assertEquals(60.0, adjusted.weightKg, 0.001)
    }

    @Test
    fun halveSetVolumeKeepsAtLeastOneSet() {
        val adjusted = DeloadTargetAdjustment.adjust(
            sets = 1,
            reps = 8,
            weightKg = 60.0,
            mode = DeloadMode.HALVE_SET_VOLUME,
        )

        assertEquals(1, adjusted.sets)
    }

    @Test
    fun reducedIntensityAppliesFifteenPercentAndRoundsToOneDecimal() {
        val adjusted = DeloadTargetAdjustment.adjust(
            sets = 4,
            reps = 6,
            weightKg = 98.0,
            mode = DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT,
        )

        assertEquals(4, adjusted.sets)
        assertEquals(6, adjusted.reps)
        assertEquals(83.3, adjusted.weightKg, 0.001)
    }

    @Test
    fun applyingDeloadDoesNotMutateThePersistedOriginTarget() {
        val original = BackupWorkoutPlanTarget(
            id = 7L,
            sessionId = 8L,
            planId = 9L,
            exerciseId = 10L,
            orderIndex = 0,
            target = BackupProgressionTarget(sets = 3, reps = 8, weightKg = 98.0),
            progression = BackupProgressionConfig(),
        )
        val snapshotBeforeAdjustment = original

        val adjusted = DeloadTargetAdjustment.apply(original, DeloadMode.HALVE_SET_VOLUME)

        assertEquals(snapshotBeforeAdjustment, original)
        assertEquals(3, original.target.sets)
        assertEquals(2, adjusted.target.sets)
    }
}
