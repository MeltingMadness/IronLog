package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionContractTest {
    @Test
    fun `plan exercises opt in with manual revision one`() {
        val exercise = PlanExercise(exerciseId = 7, orderIndex = 0)

        assertEquals(ProgressionScheme.MANUAL, exercise.progressionConfig.scheme)
        assertEquals(1, exercise.progressionConfig.ruleRevision)
    }

    @Test
    fun `weight step preserves entered unit value and canonical kilograms`() {
        val step = WeightStep(
            originalValue = 5.0,
            originalUnit = UnitSystem.IMPERIAL,
            kilograms = 2.2679618509
        )
        val config = ProgressionConfig.Linear(step = step)

        assertEquals(UnitSystem.IMPERIAL, config.step.originalUnit)
        assertEquals(5.0, config.step.originalValue, 0.0)
        assertEquals(2.2679618509, config.step.kilograms, 0.0)
    }
}
