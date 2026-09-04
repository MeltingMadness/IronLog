package com.ironlog.app.presentation.workout

import com.ironlog.app.domain.model.DeloadMode
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WorkoutPlanTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class DeloadTargetAdjustmentTest {

    private fun planTarget(
        sets: Int = 4,
        reps: Int = 8,
        weightKg: Double = 100.0
    ): WorkoutPlanTarget = WorkoutPlanTarget(
        id = 1L,
        sessionId = 1L,
        planId = 1L,
        exerciseId = 1L,
        orderIndex = 0,
        supersetGroupId = null,
        target = ProgressionTarget(sets = sets, reps = reps, weightKg = weightKg),
        config = ProgressionConfig.Linear(
            step = com.ironlog.app.domain.model.WeightStep(
                originalValue = 2.5,
                originalUnit = com.ironlog.app.domain.model.UnitSystem.METRIC,
                kilograms = 2.5
            ),
            failurePolicy = com.ironlog.app.domain.model.FailurePolicy(stallThreshold = 2, backoffPercent = 10.0),
            ruleRevision = 1
        )
    )

    @Test
    fun `without an active deload mode the target stays unchanged`() {
        val target = planTarget()
        assertEquals(target, applyDeloadToTarget(target, null))
    }

    @Test
    fun `halve set volume rounds up and keeps a minimum of one set`() {
        assertEquals(2, applyDeloadToTarget(planTarget(sets = 4), DeloadMode.HALVE_SET_VOLUME).target.sets)
        assertEquals(2, applyDeloadToTarget(planTarget(sets = 3), DeloadMode.HALVE_SET_VOLUME).target.sets)
        assertEquals(1, applyDeloadToTarget(planTarget(sets = 1), DeloadMode.HALVE_SET_VOLUME).target.sets)
        assertEquals(100.0, applyDeloadToTarget(planTarget(sets = 4), DeloadMode.HALVE_SET_VOLUME).target.weightKg, 0.0)
    }

    @Test
    fun `reduce intensity applies fifteen percent and rounds to one decimal`() {
        assertEquals(85.0, applyDeloadToTarget(planTarget(weightKg = 100.0), DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT).target.weightKg, 0.0)
        assertEquals(83.3, applyDeloadToTarget(planTarget(weightKg = 98.0), DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT).target.weightKg, 0.001)
        assertEquals(4, applyDeloadToTarget(planTarget(weightKg = 100.0), DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT).target.sets)
    }

    @Test
    fun `deload keeps reps and progression config untouched`() {
        val adjusted = applyDeloadToTarget(planTarget(), DeloadMode.HALVE_SET_VOLUME)
        assertEquals(8, adjusted.target.reps)
        assertEquals(planTarget().config, adjusted.config)
    }
}