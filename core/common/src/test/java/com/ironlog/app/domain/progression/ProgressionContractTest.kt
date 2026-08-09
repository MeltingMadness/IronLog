package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutPlanTarget
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

    @Test(expected = IllegalArgumentException::class)
    fun `suggestion rejects an outcome from another source target`() {
        val sourceTarget = WorkoutPlanTarget(
            id = 1,
            sessionId = 2,
            planId = 3,
            exerciseId = 4,
            orderIndex = 0,
            supersetGroupId = null,
            target = ProgressionTarget(sets = 3, reps = 8, weightKg = 100.0),
            config = ProgressionConfig.Manual()
        )

        ProgressionSuggestion(
            id = 5,
            sourceTarget = sourceTarget,
            outcome = ProgressionOutcome.KeepTarget(
                sourceTarget = ProgressionTarget(sets = 3, reps = 8, weightKg = 102.5),
                reasonCode = ProgressionReasonCode.REPEAT_TARGET,
                streakEffect = ProgressionStreakEffect.IGNORE
            ),
            countedSets = emptyList(),
            status = ProgressionSuggestionStatus.INFORMATIONAL,
            wasEdited = false,
            finalTarget = null,
            createdAtEpochMillis = 0,
            decidedAtEpochMillis = null
        )
    }
}
