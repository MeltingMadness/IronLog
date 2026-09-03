package com.ironlog.app.presentation.workout

import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WorkoutPlanTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pending-set weight placeholder in ActiveWorkoutScreen:
 * the current plan target weight must take priority over the previous
 * session's last work-set weight; the previous weight is only the fallback
 * when the plan carries no weight target.
 */
class ActiveWorkoutWeightHintTest {

    private fun planTarget(weightKg: Double): WorkoutPlanTarget = WorkoutPlanTarget(
        id = 1L,
        sessionId = 1L,
        planId = 1L,
        exerciseId = 1L,
        orderIndex = 0,
        supersetGroupId = null,
        target = ProgressionTarget(sets = 3, reps = 8, weightKg = weightKg),
        config = ProgressionConfig.Manual()
    )

    @Test
    fun `plan target weight wins over previous session weight`() {
        val hint = targetWeightHint(
            planTarget = planTarget(weightKg = 102.5),
            unitSystem = UnitSystem.METRIC,
            previousWeightHint = "100"
        )

        assertEquals("102.5", hint)
    }

    @Test
    fun `plan target weight is converted into the display unit`() {
        val hint = targetWeightHint(
            planTarget = planTarget(weightKg = 50.0),
            unitSystem = UnitSystem.IMPERIAL,
            previousWeightHint = "100"
        )

        assertEquals("110.2", hint)
    }

    @Test
    fun `zero plan weight falls back to the previous session weight`() {
        val hint = targetWeightHint(
            planTarget = planTarget(weightKg = 0.0),
            unitSystem = UnitSystem.METRIC,
            previousWeightHint = "100"
        )

        assertEquals("100", hint)
    }

    @Test
    fun `missing plan target falls back to the previous session weight`() {
        val hint = targetWeightHint(
            planTarget = null,
            unitSystem = UnitSystem.METRIC,
            previousWeightHint = "100"
        )

        assertEquals("100", hint)
    }

    @Test
    fun `no plan weight and no previous weight yields no hint`() {
        val hint = targetWeightHint(
            planTarget = planTarget(weightKg = 0.0),
            unitSystem = UnitSystem.METRIC,
            previousWeightHint = null
        )

        assertNull(hint)
    }

    @Test
    fun `plan weight without previous session is still shown`() {
        val hint = targetWeightHint(
            planTarget = planTarget(weightKg = 60.0),
            unitSystem = UnitSystem.METRIC,
            previousWeightHint = null
        )

        assertEquals("60", hint)
    }
}
