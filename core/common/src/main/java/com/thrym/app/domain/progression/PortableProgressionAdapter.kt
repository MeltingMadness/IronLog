package com.thrym.app.domain.progression

import com.thrym.app.domain.model.FailurePolicy
import com.thrym.app.domain.model.ProgressionConfig
import com.thrym.app.domain.model.ProgressionContext
import com.thrym.app.domain.model.ProgressionOutcome
import com.thrym.app.domain.model.ProgressionReasonCode
import com.thrym.app.domain.model.ProgressionStreakEffect
import com.thrym.app.domain.model.ProgressionTarget
import com.thrym.app.domain.model.WeightStep
import com.thrym.app.domain.model.WorkoutPlanTarget
import com.thrym.app.domain.model.WorkoutSet
import com.thrym.shared.progression.ProgressionConfigInput
import com.thrym.shared.progression.ProgressionInput
import com.thrym.shared.progression.ProgressionPlanTargetInput
import com.thrym.shared.progression.ProgressionPreviousOutcomeInput
import com.thrym.shared.progression.ProgressionResult
import com.thrym.shared.progression.ProgressionSetInput
import com.thrym.shared.progression.ProgressionOutcomeType as SharedOutcomeType
import com.thrym.shared.progression.ProgressionStreakEffect as SharedStreakEffect
import com.thrym.shared.progression.ProgressionTargetInput
import com.thrym.shared.progression.SharedProgressionConfigValidator
import com.thrym.shared.progression.SharedProgressionEngine
import java.time.ZoneOffset

internal object PortableProgressionAdapter {
    private val engine = SharedProgressionEngine()

    fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val input = context.toPortable()
        return engine.evaluate(input).toAndroidOutcome(context.sourceTarget.target)
    }

    fun validationErrors(target: ProgressionTarget, config: ProgressionConfig): List<String> =
        SharedProgressionConfigValidator.validationErrors(
            target = target.toPortable(),
            config = config.toPortable()
        )

    private fun ProgressionContext.toPortable() = ProgressionInput(
        sourceTarget = sourceTarget.toPortable(),
        setsForTarget = setsForTarget.map { it.toPortable() },
        previousComparableOutcomesNewestFirst = previousComparableOutcomesNewestFirst.map {
            ProgressionPreviousOutcomeInput(
                sourceTarget = it.sourceTarget.target.toPortable(),
                streakEffect = it.streakEffect.toShared(),
                successful = it.successful
            )
        }
    )

    private fun WorkoutPlanTarget.toPortable() = ProgressionPlanTargetInput(
        id = id,
        sessionId = sessionId,
        planId = planId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        supersetGroupId = supersetGroupId,
        target = target.toPortable(),
        config = config.toPortable()
    )

    private fun ProgressionTarget.toPortable() = ProgressionTargetInput(sets, reps, weightKg)

    private fun ProgressionConfig.toPortable(): ProgressionConfigInput = when (this) {
        is ProgressionConfig.Manual -> ProgressionConfigInput(
            scheme = scheme.name,
            ruleRevision = ruleRevision
        )

        is ProgressionConfig.Linear -> activeConfig(
            successThreshold = successThreshold,
            scheme = scheme.name,
            step = step,
            failurePolicy = failurePolicy,
            ruleRevision = ruleRevision
        )

        is ProgressionConfig.DoubleProgression -> activeConfig(
            scheme = scheme.name,
            step = step,
            failurePolicy = failurePolicy,
            ruleRevision = ruleRevision,
            minReps = minReps,
            maxReps = maxReps
        )

        is ProgressionConfig.TotalReps -> activeConfig(
            scheme = scheme.name,
            step = step,
            failurePolicy = failurePolicy,
            ruleRevision = ruleRevision,
            targetTotalReps = targetTotalReps
        )

        is ProgressionConfig.RpeRir -> activeConfig(
            scheme = scheme.name,
            step = step,
            failurePolicy = failurePolicy,
            ruleRevision = ruleRevision,
            targetRpe = targetRpe,
            rpeTolerance = tolerance
        )

        is ProgressionConfig.Invalid -> ProgressionConfigInput(
            scheme = rawScheme,
            ruleRevision = ruleRevision,
            storageReason = storageReason,
            rawScheme = rawScheme
        )
    }

    private fun activeConfig(
        scheme: String,
        step: WeightStep,
        failurePolicy: FailurePolicy,
        ruleRevision: Int,
        minReps: Int? = null,
        maxReps: Int? = null,
        targetTotalReps: Long? = null,
        targetRpe: Double? = null,
        rpeTolerance: Double? = null,
        successThreshold: Int = 1
    ) = ProgressionConfigInput(
        scheme = scheme,
        incrementValue = step.originalValue,
        incrementUnit = step.originalUnit.name,
        incrementKg = step.kilograms,
        minReps = minReps,
        maxReps = maxReps,
        targetTotalReps = targetTotalReps,
        targetRpe = targetRpe,
        rpeTolerance = rpeTolerance,
        stallThreshold = failurePolicy.stallThreshold,
        backoffPercent = failurePolicy.backoffPercent,
        successThreshold = successThreshold,
        ruleRevision = ruleRevision
    )

    private fun WorkoutSet.toPortable() = ProgressionSetInput(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        setType = setType.name,
        completedAtEpochMillis = completedAt.toEpochSecond(ZoneOffset.UTC) * 1_000L +
            completedAt.nano / 1_000_000L,
        orderingToken = completedAt.nano.toLong(),
        rpe = rpe,
        planTargetSnapshotId = planTargetSnapshotId
    )

    private fun ProgressionStreakEffect.toShared() = when (this) {
        ProgressionStreakEffect.INCREMENT -> SharedStreakEffect.INCREMENT
        ProgressionStreakEffect.RESET -> SharedStreakEffect.RESET
        ProgressionStreakEffect.IGNORE -> SharedStreakEffect.IGNORE
    }

    private fun ProgressionResult.toAndroidOutcome(source: ProgressionTarget): ProgressionOutcome {
        require(this.sourceTarget.toAndroid() == source) {
            "Shared progression outcome source does not match Android source target"
        }
        val reason = ProgressionReasonCode.valueOf(reasonCode.name)
        val streak = ProgressionStreakEffect.valueOf(streakEffect.name)
        return when (outcomeType) {
            SharedOutcomeType.PROPOSE_CHANGE -> ProgressionOutcome.ProposeChange(
                sourceTarget = source,
                proposedTarget = requireNotNull(proposedTarget).toAndroid(),
                reasonCode = reason,
                reasonArguments = reasonArguments,
                streakEffect = streak,
                countedSetIds = countedSetIds
            )

            SharedOutcomeType.KEEP_TARGET -> ProgressionOutcome.KeepTarget(
                sourceTarget = source,
                reasonCode = reason,
                reasonArguments = reasonArguments,
                streakEffect = streak,
                countedSetIds = countedSetIds
            )

            SharedOutcomeType.INSUFFICIENT_DATA -> ProgressionOutcome.InsufficientData(
                sourceTarget = source,
                reasonCode = reason,
                reasonArguments = reasonArguments,
                streakEffect = streak,
                countedSetIds = countedSetIds
            )

            SharedOutcomeType.NOT_APPLICABLE -> ProgressionOutcome.NotApplicable(
                sourceTarget = source,
                reasonCode = reason,
                reasonArguments = reasonArguments,
                streakEffect = streak,
                countedSetIds = countedSetIds
            )
        }
    }

    private fun ProgressionTargetInput.toAndroid() = ProgressionTarget(sets, reps, weightKg)
}
