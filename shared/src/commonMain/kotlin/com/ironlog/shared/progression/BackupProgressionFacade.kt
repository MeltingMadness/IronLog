package com.ironlog.shared.progression

import com.ironlog.shared.backup.BackupProgressionConfig
import com.ironlog.shared.backup.BackupProgressionTarget
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.backup.BackupWorkoutSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** History entry accepted by the JSON-friendly backup façade. */
@Serializable
data class BackupProgressionHistory(
    val streakEffect: ProgressionStreakEffect,
    val sourceTarget: BackupProgressionTarget? = null
)

@Serializable
data class BackupProgressionResult(
    val outcomeType: ProgressionOutcomeType,
    val sourceTarget: BackupProgressionTarget,
    val proposedTarget: BackupProgressionTarget? = null,
    val reasonCode: ProgressionReasonCode,
    val reasonArguments: Map<String, Double> = emptyMap(),
    val streakEffect: ProgressionStreakEffect = ProgressionStreakEffect.IGNORE,
    val countedSetIds: List<Long> = emptyList()
)

/**
 * iOS-facing façade. It consumes the existing backup DTOs directly, so Swift
 * does not need a duplicate progression model or a Java/Kotlin date adapter.
 */
object BackupProgressionFacade {
    private val engine = SharedProgressionEngine()
    private val json = Json { encodeDefaults = true }

    fun evaluate(
        target: BackupWorkoutPlanTarget,
        sets: List<BackupWorkoutSet>,
        previousOutcomesNewestFirst: List<BackupProgressionHistory> = emptyList()
    ): BackupProgressionResult {
        val portableTarget = target.toPortable()
        val portableInput = ProgressionInput(
            sourceTarget = portableTarget,
            setsForTarget = sets.map { it.toPortable() },
            previousComparableOutcomesNewestFirst = previousOutcomesNewestFirst.map { previous ->
                ProgressionPreviousOutcomeInput(
                    sourceTarget = previous.sourceTarget?.toPortable() ?: portableTarget.target,
                    streakEffect = previous.streakEffect
                )
            }
        )
        return engine.evaluate(portableInput).toBackupResult()
    }

    /** Convenience overload when only streak effects are available. */
    fun evaluateWithStreakEffects(
        target: BackupWorkoutPlanTarget,
        sets: List<BackupWorkoutSet>,
        previousStreakEffectsNewestFirst: List<ProgressionStreakEffect>
    ): BackupProgressionResult = evaluate(
        target = target,
        sets = sets,
        previousOutcomesNewestFirst = previousStreakEffectsNewestFirst.map {
            BackupProgressionHistory(streakEffect = it)
        }
    )

    /** Stable JSON output for a thin Swift bridge or snapshot tests. */
    fun evaluateJson(
        target: BackupWorkoutPlanTarget,
        sets: List<BackupWorkoutSet>,
        previousOutcomesNewestFirst: List<BackupProgressionHistory> = emptyList()
    ): String = json.encodeToString(evaluate(target, sets, previousOutcomesNewestFirst))
}

private fun BackupWorkoutPlanTarget.toPortable() = ProgressionPlanTargetInput(
    id = id,
    sessionId = sessionId,
    planId = planId,
    exerciseId = exerciseId,
    orderIndex = orderIndex,
    supersetGroupId = supersetGroupId,
    target = target.toPortable(),
    config = progression.toPortable()
)

private fun BackupProgressionTarget.toPortable() = ProgressionTargetInput(sets, reps, weightKg)

private fun BackupProgressionConfig.toPortable() = ProgressionConfigInput(
    scheme = scheme,
    incrementValue = incrementValue,
    incrementUnit = incrementUnit,
    incrementKg = incrementKg,
    minReps = minReps,
    maxReps = maxReps,
    targetTotalReps = targetTotalReps,
    targetRpe = targetRpe,
    rpeTolerance = rpeTolerance,
    stallThreshold = stallThreshold,
    backoffPercent = backoffPercent,
    ruleRevision = ruleRevision
)

private fun BackupWorkoutSet.toPortable() = ProgressionSetInput(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    setType = resolvedSetType(),
    completedAtEpochMillis = completedAt,
    orderingToken = id,
    rpe = rpe,
    planTargetSnapshotId = planTargetSnapshotId
)

private fun ProgressionResult.toBackupResult() = BackupProgressionResult(
    outcomeType = outcomeType,
    sourceTarget = sourceTarget.toBackup(),
    proposedTarget = proposedTarget?.toBackup(),
    reasonCode = reasonCode,
    reasonArguments = reasonArguments,
    streakEffect = streakEffect,
    countedSetIds = countedSetIds
)

private fun ProgressionTargetInput.toBackup() = BackupProgressionTarget(sets, reps, weightKg)
