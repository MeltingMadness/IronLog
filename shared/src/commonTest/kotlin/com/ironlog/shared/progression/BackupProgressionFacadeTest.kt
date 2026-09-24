package com.ironlog.shared.progression

import com.ironlog.shared.backup.BackupProgressionConfig
import com.ironlog.shared.backup.BackupProgressionTarget
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.backup.BackupWorkoutSet
import com.ironlog.shared.backup.NORMAL_SET_TYPE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.Json

class BackupProgressionFacadeTest {
    @Test
    fun linearSuccessUsesActualWeightAsRelativeIncrementFromZeroTarget() {
        val target = target(
            target = BackupProgressionTarget(sets = 3, reps = 10, weightKg = 0.0),
            config = linear(step = 2.0)
        )
        val result = BackupProgressionFacade.evaluate(target, sets(target, weight = 23.0))

        assertEquals(ProgressionOutcomeType.PROPOSE_CHANGE, result.outcomeType)
        assertEquals(25.0, result.proposedTarget?.weightKg)
        assertEquals(ProgressionReasonCode.LOAD_ADVANCED, result.reasonCode)
    }

    @Test
    fun doubleProgressionUsesCompletedUpperBoundBeforeStoredTarget() {
        val target = target(
            target = BackupProgressionTarget(sets = 3, reps = 8, weightKg = 100.0),
            config = linear(step = 2.5).copy(
                scheme = "DOUBLE",
                minReps = 8,
                maxReps = 10
            )
        )
        val result = BackupProgressionFacade.evaluate(target, sets(target, reps = 10))

        assertEquals(ProgressionReasonCode.LOAD_ADVANCED, result.reasonCode)
        assertEquals(102.5, result.proposedTarget?.weightKg)
        assertEquals(8, result.proposedTarget?.reps)
    }

    @Test
    fun rpeStallBackoffUsesActualWeightAndHistory() {
        val target = target(
            target = BackupProgressionTarget(sets = 3, reps = 8, weightKg = 0.0),
            config = linear(step = 2.5).copy(
                scheme = "RPE_RIR",
                targetRpe = 8.0,
                rpeTolerance = 0.5
            )
        )
        val result = BackupProgressionFacade.evaluate(
            target = target,
            sets = sets(target, reps = 8, rpe = listOf(8.0, 9.0, 8.0), weight = 50.0),
            previousOutcomesNewestFirst = listOf(
                BackupProgressionHistory(ProgressionStreakEffect.INCREMENT)
            )
        )

        assertEquals(ProgressionReasonCode.STALL_BACKOFF, result.reasonCode)
        assertEquals(45.0, result.proposedTarget?.weightKg)
        assertEquals(50.0, result.reasonArguments["actualWeightKg"])
    }

    @Test
    fun mixedWeightsRemainInsufficientData() {
        val target = target(config = linear(step = 2.5))
        val result = BackupProgressionFacade.evaluate(
            target,
            sets(target, weights = listOf(100.0, 102.5, 100.0))
        )

        assertEquals(ProgressionOutcomeType.INSUFFICIENT_DATA, result.outcomeType)
        assertEquals(ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION, result.reasonCode)
        assertEquals(listOf(1L, 2L, 3L), result.countedSetIds)
    }

    @Test
    fun resultIsJsonSerializableForSwiftBridge() {
        val target = target(config = linear(step = 2.5))
        val json = BackupProgressionFacade.evaluateJson(target, sets(target))
        val result = Json.decodeFromString<BackupProgressionResult>(json)

        assertIs<BackupProgressionResult>(result)
        assertEquals(ProgressionReasonCode.LOAD_ADVANCED, result.reasonCode)
    }

    private fun target(
        target: BackupProgressionTarget = BackupProgressionTarget(3, 10, 100.0),
        config: BackupProgressionConfig
    ) = BackupWorkoutPlanTarget(
        id = 99L,
        sessionId = 10L,
        planId = 20L,
        exerciseId = 30L,
        orderIndex = 0,
        target = target,
        progression = config
    )

    private fun linear(step: Double) = BackupProgressionConfig(
        scheme = "LINEAR",
        incrementValue = step,
        incrementUnit = "METRIC",
        incrementKg = step
    )

    private fun sets(
        target: BackupWorkoutPlanTarget,
        reps: Int = target.target.reps,
        weight: Double = target.target.weightKg,
        weights: List<Double> = List(target.target.sets) { weight },
        rpe: List<Double?> = List(target.target.sets) { null }
    ) = weights.mapIndexed { index, actualWeight ->
        BackupWorkoutSet(
            id = (index + 1).toLong(),
            sessionId = target.sessionId,
            exerciseId = target.exerciseId,
            setNumber = index + 1,
            reps = reps,
            weightKg = actualWeight,
            setType = NORMAL_SET_TYPE,
            completedAt = 1_000L + index,
            rpe = rpe[index],
            planTargetSnapshotId = target.id
        )
    }
}
