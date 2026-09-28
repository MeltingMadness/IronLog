package com.ironlog.shared.progression

import kotlin.math.abs
import kotlin.math.floor

/** Revision 1 remains available for immutable historical snapshots. */
const val CURRENT_PROGRESSION_RULE_REVISION: Int = 2

fun isProgressionWorkSet(setType: String, ruleRevision: Int): Boolean =
    setType == "NORMAL" || (ruleRevision == 2 && setType == "FAILURE")

/** Same tolerance used by the Android evidence/provenance checks. */
const val PROGRESSION_WEIGHT_TOLERANCE_KG: Double = 0.1

private const val KG_TO_LB = 2.2046226218
private const val STEP_STORAGE_TOLERANCE_KG = 0.000001
private const val WEIGHT_COMPARISON_EPSILON_KG = 0.000000001

/**
 * Platform-independent progression evaluator.
 *
 * All rule decisions are made here.  The evaluator deliberately accepts a
 * flat, backup-shaped configuration so unknown fields and malformed values can
 * become an explicit CONFIG_INVALID result instead of being guessed.
 */
class SharedProgressionEngine {
    fun evaluate(input: ProgressionInput): ProgressionResult {
        val result = evaluateRules(input)
        if (input.sourceTarget.config.ruleRevision != 2) return result
        val counted = input.setsForTarget.filter { it.id in result.countedSetIds }
        val workCount = input.setsForTarget.count { isProgressionWorkSet(it.setType, 2) }
        return result.copy(reasonArguments = result.reasonArguments + mapOf(
            "countedWorkSets" to counted.size.toDouble(),
            "ignoredWarmupSets" to input.setsForTarget.count { it.setType == "WARMUP" }.toDouble(),
            "ignoredDropSets" to input.setsForTarget.count { it.setType == "DROP_SET" }.toDouble(),
            "ignoredBackoffSets" to input.setsForTarget.count { it.setType == "BACKOFF" }.toDouble(),
            "ignoredExtraSets" to (workCount - counted.size).coerceAtLeast(0).toDouble(),
            "failureMarkedSets" to counted.count { it.setType == "FAILURE" }.toDouble()
        ))
    }

    private fun evaluateRules(input: ProgressionInput): ProgressionResult {
        val source = input.sourceTarget
        val target = source.target
        val availableEvidenceIds = availableWorkSetIds(input)
        if (!isValidTarget(target) || !isValidConfig(target, source.config)) {
            return insufficient(
                target = target,
                reasonCode = ProgressionReasonCode.CONFIG_INVALID,
                countedSetIds = availableEvidenceIds
            )
        }

        if (source.config.scheme == "MANUAL") {
            return ProgressionResult(
                outcomeType = ProgressionOutcomeType.NOT_APPLICABLE,
                sourceTarget = target,
                reasonCode = ProgressionReasonCode.MANUAL_SCHEME
            )
        }
        if (source.config.ruleRevision !in 1..CURRENT_PROGRESSION_RULE_REVISION) {
            return insufficient(
                target = target,
                reasonCode = ProgressionReasonCode.RULE_REVISION_UNSUPPORTED,
                countedSetIds = availableEvidenceIds
            )
        }

        val prepared = prepare(input)
        if (prepared is Prepared.Invalid) return prepared.result
        val countedSets = (prepared as Prepared.Valid).sets
        val uniformWeight = evaluationWeightKg(countedSets)
        val mixed = uniformWeight == null
        val config = source.config
        if (mixed && config.ruleRevision == 1) return mixedWeightOutcome(target, countedSets)
        val actualWeightKg = uniformWeight ?: countedSets.minOf { it.weightKg }
        val actualReps = countedSets.minOf { it.reps }
        if (mixed && actualWeightKg + PROGRESSION_WEIGHT_TOLERANCE_KG < target.weightKg) {
            return mixedLoadRepeat(target, countedSets)
        }
        if (config.ruleRevision == 2 && (!increasedWeight(actualWeightKg, config).isFinite() ||
                increasedWeight(actualWeightKg, config) <= actualWeightKg)) {
            return insufficient(target, ProgressionReasonCode.SET_VALUE_INVALID, countedSetIds = availableEvidenceIds)
        }

        val result = when (config.scheme) {
            "LINEAR" -> evaluateLinear(input, countedSets, actualWeightKg, actualReps)
            "DOUBLE" -> evaluateDouble(input, countedSets, actualWeightKg, actualReps)
            "TOTAL_REPS" -> evaluateTotalReps(input, countedSets, actualWeightKg)
            "RPE_RIR" -> evaluateRpe(input, countedSets, actualWeightKg, actualReps)
            // isValidConfig() rejects unknown schemes. Keep this branch for
            // exhaustiveness if a future scheme is added without a rule.
            else -> insufficient(
                target = target,
                reasonCode = ProgressionReasonCode.RULE_REVISION_UNSUPPORTED,
                countedSetIds = countedSets.map { it.id }
            )
        }
        // Different loads cannot establish a comparable failed attempt. Successful
        // work confirms only the lowest load completed by every counted work set.
        if (mixed && result.streakEffect == ProgressionStreakEffect.INCREMENT) {
            return mixedLoadRepeat(target, countedSets)
        }
        return if (config.ruleRevision == 2) result.copy(reasonArguments = result.reasonArguments + mapOf(
            "minWeightKg" to countedSets.minOf { it.weightKg },
            "maxWeightKg" to countedSets.maxOf { it.weightKg }
        )) else result
    }

    private fun evaluateLinear(
        input: ProgressionInput,
        countedSets: List<ProgressionSetInput>,
        actualWeightKg: Double,
        actualReps: Int
    ): ProgressionResult {
        val target = input.sourceTarget.target
        val config = input.sourceTarget.config
        var arguments = mapOf(
            "targetReps" to target.reps.toDouble(),
            "actualReps" to actualReps.toDouble()
        )
        if (actualReps < target.reps) {
            return repetitionMissOutcome(input, countedSets, arguments, actualWeightKg)
        }
        if (config.ruleRevision == 2) {
            val successes = (1 + input.previousComparableOutcomesNewestFirst
                .takeWhile { it.sourceTarget == target && it.successful }.size).coerceAtMost(config.successThreshold)
            arguments = arguments + mapOf(
                "successfulSessions" to successes.toDouble(),
                "requiredSuccesses" to config.successThreshold.toDouble(),
                "actualWeightKg" to actualWeightKg
            )
            if (successes < config.successThreshold) {
                return keep(target, ProgressionReasonCode.SUCCESS_CONFIRMATION_REQUIRED, arguments,
                    ProgressionStreakEffect.RESET, countedSets.map { it.id })
            }
        }
        return propose(
            target = target,
            proposedTarget = target.copy(weightKg = increasedWeight(actualWeightKg, config)),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = arguments +
                ("stepOriginalValue" to requireNotNull(config.incrementValue)) +
                ("actualWeightKg" to actualWeightKg),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map { it.id }
        )
    }

    private fun evaluateDouble(
        input: ProgressionInput,
        countedSets: List<ProgressionSetInput>,
        actualWeightKg: Double,
        actualReps: Int
    ): ProgressionResult {
        val target = input.sourceTarget.target
        val config = input.sourceTarget.config
        if (actualReps < target.reps) {
            return repetitionMissOutcome(
                input,
                countedSets,
                mapOf(
                    "targetReps" to target.reps.toDouble(),
                    "actualReps" to actualReps.toDouble()
                ),
                actualWeightKg
            )
        }
        // Completed evidence, rather than a stale stored target, determines
        // whether the upper repetition bound has been reached.
        val maxReps = requireNotNull(config.maxReps)
        if (actualReps < maxReps) {
            return propose(
                target = target,
                proposedTarget = target.copy(
                    reps = if (config.ruleRevision == 2) actualReps + 1 else target.reps + 1,
                    weightKg = actualWeightKg
                ),
                reasonCode = ProgressionReasonCode.REP_TARGET_ADVANCED,
                reasonArguments = mapOf("actualWeightKg" to actualWeightKg) +
                    (if (config.ruleRevision == 2) mapOf("actualReps" to actualReps.toDouble()) else emptyMap()),
                streakEffect = ProgressionStreakEffect.RESET,
                countedSetIds = countedSets.map { it.id }
            )
        }
        return propose(
            target = target,
            proposedTarget = target.copy(
                reps = requireNotNull(config.minReps),
                weightKg = increasedWeight(actualWeightKg, config)
            ),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = mapOf(
                "targetReps" to target.reps.toDouble(),
                "actualReps" to actualReps.toDouble(),
                "stepOriginalValue" to requireNotNull(config.incrementValue),
                "actualWeightKg" to actualWeightKg
            ),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map { it.id }
        )
    }

    private fun evaluateTotalReps(
        input: ProgressionInput,
        countedSets: List<ProgressionSetInput>,
        actualWeightKg: Double
    ): ProgressionResult {
        val target = input.sourceTarget.target
        val config = input.sourceTarget.config
        val achievedTotalReps = safeRepsSum(countedSets)
            ?: return insufficient(
                target = target,
                reasonCode = ProgressionReasonCode.SET_VALUE_INVALID,
                countedSetIds = countedSets.map { it.id }
            )
        val totalArguments = mapOf(
            "achievedTotalReps" to achievedTotalReps.toDouble(),
            "targetTotalReps" to requireNotNull(config.targetTotalReps).toDouble()
        )
        if (achievedTotalReps < requireNotNull(config.targetTotalReps)) {
            return repetitionMissOutcome(input, countedSets, totalArguments, actualWeightKg)
        }
        return propose(
            target = target,
            proposedTarget = target.copy(weightKg = increasedWeight(actualWeightKg, config)),
            reasonCode = ProgressionReasonCode.TOTAL_REPS_COMPLETED,
            reasonArguments = totalArguments +
                ("stepOriginalValue" to requireNotNull(config.incrementValue)) +
                ("actualWeightKg" to actualWeightKg),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map { it.id }
        )
    }

    private fun evaluateRpe(
        input: ProgressionInput,
        countedSets: List<ProgressionSetInput>,
        actualWeightKg: Double,
        actualReps: Int
    ): ProgressionResult {
        val target = input.sourceTarget.target
        val config = input.sourceTarget.config
        if (actualReps < target.reps) {
            return repetitionMissOutcome(
                input,
                countedSets,
                mapOf(
                    "targetReps" to target.reps.toDouble(),
                    "actualReps" to actualReps.toDouble()
                ),
                actualWeightKg
            )
        }
        if (countedSets.any { it.rpe == null }) {
            return insufficient(
                target = target,
                reasonCode = ProgressionReasonCode.RPE_MISSING,
                countedSetIds = countedSets.map { it.id }
            )
        }
        val rpes = countedSets.map { requireNotNull(it.rpe) }
        if (rpes.any { !it.isFinite() || it !in 1.0..10.0 }) {
            return insufficient(
                target = target,
                reasonCode = ProgressionReasonCode.RPE_INVALID,
                countedSetIds = countedSets.map { it.id }
            )
        }
        val highestRpe = rpes.maxOrNull() ?: return insufficient(
            target = target,
            reasonCode = ProgressionReasonCode.RPE_MISSING,
            countedSetIds = countedSets.map { it.id }
        )
        val targetRpe = requireNotNull(config.targetRpe)
        val tolerance = requireNotNull(config.rpeTolerance)
        if (highestRpe > targetRpe + tolerance) {
            return repetitionMissOutcome(
                input = input,
                countedSets = countedSets,
                repeatReasonArguments = mapOf("highestRpe" to highestRpe),
                baseWeightKg = actualWeightKg,
                additionalBackoffReasonArguments = mapOf("highestRpe" to highestRpe)
            )
        }
        return propose(
            target = target,
            proposedTarget = target.copy(weightKg = increasedWeight(actualWeightKg, config)),
            reasonCode = ProgressionReasonCode.RPE_WITHIN_TARGET,
            reasonArguments = mapOf(
                "highestRpe" to highestRpe,
                "targetRpe" to targetRpe,
                "tolerance" to tolerance,
                "stepOriginalValue" to requireNotNull(config.incrementValue),
                "actualWeightKg" to actualWeightKg
            ),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = countedSets.map { it.id }
        )
    }

    private fun prepare(input: ProgressionInput): Prepared {
        val source = input.sourceTarget
        val availableSetIds = availableWorkSetIds(input)
        val allIds = input.setsForTarget.map { it.id }
        val identityIsValid = allIds.all { it > 0L } &&
            allIds.distinct().size == allIds.size &&
            input.setsForTarget.all { set ->
                set.sessionId == source.sessionId &&
                    set.exerciseId == source.exerciseId &&
                    set.planTargetSnapshotId == source.id
            }
        if (!identityIsValid || (source.config.ruleRevision == 2 && input.setsForTarget.any {
                it.setType !in setOf("NORMAL", "FAILURE", "WARMUP", "DROP_SET", "BACKOFF")
            })) {
            return Prepared.Invalid(
                insufficient(
                    target = source.target,
                    reasonCode = ProgressionReasonCode.SET_VALUE_INVALID,
                    countedSetIds = availableSetIds
                )
            )
        }

        val workSets = input.setsForTarget
            .filter { isProgressionWorkSet(it.setType, source.config.ruleRevision) }
            .sortedWith(setComparator)
        val workSetNumbers = workSets.map { it.setNumber }
        if (workSetNumbers.any { it <= 0 } || workSetNumbers.distinct().size != workSetNumbers.size) {
            return Prepared.Invalid(
                insufficient(
                    target = source.target,
                    reasonCode = ProgressionReasonCode.SET_NUMBER_INVALID,
                    countedSetIds = availableSetIds
                )
            )
        }

        val targetSets = source.target.sets
        if (workSets.size < targetSets) {
            return Prepared.Invalid(
                insufficient(
                    target = source.target,
                    reasonCode = ProgressionReasonCode.TOO_FEW_WORK_SETS,
                    reasonArguments = mapOf(
                        "targetSets" to targetSets.toDouble(),
                        "actualWorkSets" to workSets.size.toDouble()
                    ),
                    countedSetIds = availableSetIds
                )
            )
        }
        val countedSets = workSets.take(targetSets)
        if (countedSets.any { it.reps < 0 || !it.weightKg.isFinite() || it.weightKg < 0.0 }) {
            return Prepared.Invalid(
                insufficient(
                    target = source.target,
                    reasonCode = ProgressionReasonCode.SET_VALUE_INVALID,
                    countedSetIds = availableSetIds
                )
            )
        }
        return Prepared.Valid(countedSets)
    }

    private fun repetitionMissOutcome(
        input: ProgressionInput,
        countedSets: List<ProgressionSetInput>,
        repeatReasonArguments: Map<String, Double>,
        baseWeightKg: Double,
        additionalBackoffReasonArguments: Map<String, Double> = emptyMap()
    ): ProgressionResult {
        val target = input.sourceTarget.target
        val config = input.sourceTarget.config
        val failurePolicy = FailurePolicyValues(
            stallThreshold = requireNotNull(config.stallThreshold),
            backoffPercent = config.backoffPercent
        )
        val failuresIncludingCurrent = priorConsecutiveFailures(input) + 1
        val streakArguments = if (config.ruleRevision == 2) mapOf(
            "failedSessions" to failuresIncludingCurrent.toDouble(),
            "failureThreshold" to failurePolicy.stallThreshold.toDouble()
        ) else emptyMap()
        val repeatArguments = repeatReasonArguments + ("actualWeightKg" to baseWeightKg) + streakArguments
        if (failuresIncludingCurrent < failurePolicy.stallThreshold) {
            return if (weightsDiffer(target.weightKg, baseWeightKg)) {
                propose(
                    target = target,
                    proposedTarget = target.copy(weightKg = baseWeightKg),
                    reasonCode = ProgressionReasonCode.REPEAT_TARGET,
                    reasonArguments = repeatArguments,
                    streakEffect = ProgressionStreakEffect.INCREMENT,
                    countedSetIds = countedSets.map { it.id }
                )
            } else {
                keep(
                    target = target,
                    reasonCode = ProgressionReasonCode.REPEAT_TARGET,
                    reasonArguments = repeatArguments,
                    streakEffect = ProgressionStreakEffect.INCREMENT,
                    countedSetIds = countedSets.map { it.id }
                )
            }
        }

        val backedOff = backedOffWeight(baseWeightKg, config, failurePolicy.backoffPercent)
        return if (backedOff < baseWeightKg) {
            propose(
                target = target,
                proposedTarget = target.copy(weightKg = backedOff),
                reasonCode = ProgressionReasonCode.STALL_BACKOFF,
                reasonArguments = additionalBackoffReasonArguments + streakArguments + mapOf(
                    "backoffPercent" to failurePolicy.backoffPercent,
                    "actualWeightKg" to baseWeightKg
                ),
                streakEffect = ProgressionStreakEffect.INCREMENT,
                countedSetIds = countedSets.map { it.id }
            )
        } else {
            keep(
                target = target,
                reasonCode = ProgressionReasonCode.BACKOFF_FLOOR_REACHED,
                reasonArguments = additionalBackoffReasonArguments + streakArguments + mapOf(
                    "backoffPercent" to failurePolicy.backoffPercent,
                    "actualWeightKg" to baseWeightKg
                ),
                streakEffect = ProgressionStreakEffect.INCREMENT,
                countedSetIds = countedSets.map { it.id }
            )
        }
    }

    private fun priorConsecutiveFailures(input: ProgressionInput): Int {
        var failures = 0
        for (outcome in input.previousComparableOutcomesNewestFirst) {
            when (outcome.streakEffect) {
                ProgressionStreakEffect.INCREMENT -> failures += 1
                ProgressionStreakEffect.IGNORE -> Unit
                ProgressionStreakEffect.RESET -> return failures
            }
        }
        return failures
    }

    private fun evaluationWeightKg(sets: List<ProgressionSetInput>): Double? {
        val first = sets.firstOrNull() ?: return null
        return if (sets.drop(1).all { weightsDiffer(it.weightKg, first.weightKg).not() }) {
            first.weightKg
        } else {
            null
        }
    }

    private fun mixedWeightOutcome(
        target: ProgressionTargetInput,
        countedSets: List<ProgressionSetInput>
    ): ProgressionResult {
        val expected = countedSets.first().weightKg
        val actual = countedSets.drop(1).firstOrNull {
            weightsDiffer(it.weightKg, expected)
        }?.weightKg ?: expected
        return insufficient(
            target = target,
            reasonCode = ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION,
            reasonArguments = mapOf(
                "expectedWeightKg" to expected,
                "actualWeightKg" to actual
            ),
            countedSetIds = countedSets.map { it.id }
        )
    }

    private fun mixedLoadRepeat(target: ProgressionTargetInput, sets: List<ProgressionSetInput>) = keep(
        target, ProgressionReasonCode.MIXED_LOADS,
        mapOf("minWeightKg" to sets.minOf { it.weightKg }, "maxWeightKg" to sets.maxOf { it.weightKg }),
        ProgressionStreakEffect.IGNORE, sets.map { it.id }
    )

    private fun increasedWeight(actualWeightKg: Double, config: ProgressionConfigInput): Double {
        val display = toDisplay(actualWeightKg, requireNotNull(config.incrementUnit))
        return toKg(display + requireNotNull(config.incrementValue), config.incrementUnit)
    }

    private fun backedOffWeight(
        actualWeightKg: Double,
        config: ProgressionConfigInput,
        backoffPercent: Double
    ): Double {
        val display = toDisplay(actualWeightKg, requireNotNull(config.incrementUnit))
        val desiredReduction = display * backoffPercent / 100.0
        val step = requireNotNull(config.incrementValue)
        // Round the number of downward steps, never the absolute load. This
        // preserves offset dumbbell series such as 4 / 5.5 / 7 / 8.5 kg.
        val stepsDown = floor(desiredReduction / step + 0.5).coerceAtLeast(1.0)
        return toKg((display - stepsDown * step).coerceAtLeast(0.0), config.incrementUnit)
    }

    private fun isValidTarget(target: ProgressionTargetInput): Boolean =
        target.sets > 0 && target.reps > 0 && target.weightKg.isFinite() && target.weightKg >= 0.0

    private fun isValidConfig(
        target: ProgressionTargetInput,
        config: ProgressionConfigInput
    ): Boolean {
        if (config.storageReason != null) return false
        if (!validSuccessThreshold(config)) return false
        val schemeFieldsValid = when (config.scheme) {
            "MANUAL" -> config.incrementValue == null && config.incrementUnit == null &&
                config.incrementKg == null && config.minReps == null && config.maxReps == null &&
                config.targetTotalReps == null && config.targetRpe == null && config.rpeTolerance == null

            "LINEAR" -> activeFieldsValid(config) && config.minReps == null && config.maxReps == null &&
                config.targetTotalReps == null && config.targetRpe == null && config.rpeTolerance == null

            "DOUBLE" -> activeFieldsValid(config) && config.minReps != null && config.maxReps != null &&
                config.targetTotalReps == null && config.targetRpe == null && config.rpeTolerance == null

            "TOTAL_REPS" -> activeFieldsValid(config) && config.minReps == null && config.maxReps == null &&
                config.targetTotalReps != null && config.targetRpe == null && config.rpeTolerance == null

            "RPE_RIR" -> activeFieldsValid(config) && config.minReps == null && config.maxReps == null &&
                config.targetTotalReps == null && config.targetRpe != null && config.rpeTolerance != null

            else -> false
        }
        if (!schemeFieldsValid) return false
        if (config.scheme == "MANUAL") return true
        val incrementValue = config.incrementValue ?: return false
        val incrementUnit = config.incrementUnit ?: return false
        val incrementKg = config.incrementKg ?: return false
        if (!incrementValue.isFinite() || incrementValue <= 0.0 ||
            !incrementKg.isFinite() || incrementKg <= 0.0 ||
            incrementUnit !in setOf("METRIC", "IMPERIAL")
        ) return false
        if (!toDisplay(target.weightKg, incrementUnit).isFinite()) return false
        val converted = toKg(incrementValue, incrementUnit)
        if (!converted.isFinite() || abs(converted - incrementKg) > STEP_STORAGE_TOLERANCE_KG) return false
        if (config.stallThreshold !in 1..6 ||
            !config.backoffPercent.isFinite() || config.backoffPercent !in 1.0..30.0
        ) return false
        val increased = target.weightKg + incrementKg
        if (!increased.isFinite() || increased <= target.weightKg) return false
        return when (config.scheme) {
            "DOUBLE" -> {
                val minReps = requireNotNull(config.minReps)
                val maxReps = requireNotNull(config.maxReps)
                minReps >= 1 && minReps <= target.reps && maxReps >= target.reps && maxReps >= minReps
            }

            "TOTAL_REPS" -> requireNotNull(config.targetTotalReps) > 0L
            "RPE_RIR" -> {
                val targetRpe = requireNotNull(config.targetRpe)
                val tolerance = requireNotNull(config.rpeTolerance)
                targetRpe.isFinite() && targetRpe in 1.0..10.0 &&
                    tolerance.isFinite() && tolerance in 0.0..2.0
            }

            "LINEAR" -> true
            else -> false
        }
    }

    private fun activeFieldsValid(config: ProgressionConfigInput): Boolean =
        config.incrementValue != null && config.incrementUnit != null && config.incrementKg != null

    private fun availableWorkSetIds(input: ProgressionInput): List<Long> = input.setsForTarget
        .filter { isProgressionWorkSet(it.setType, input.sourceTarget.config.ruleRevision) }
        .sortedWith(setComparator)
        .map { it.id }
        .filter { it > 0L }
        .distinct()

    private fun safeRepsSum(sets: List<ProgressionSetInput>): Long? {
        var total = 0L
        for (set in sets) {
            val reps = set.reps.toLong()
            // Reps are validated non-negative before this function is called.
            if (reps > Long.MAX_VALUE - total) return null
            total += reps
        }
        return total
    }

    private fun toDisplay(weightKg: Double, unit: String): Double =
        if (unit == "IMPERIAL") weightKg * KG_TO_LB else weightKg

    private fun toKg(displayWeight: Double, unit: String): Double =
        if (unit == "IMPERIAL") displayWeight / KG_TO_LB else displayWeight

    private fun weightsDiffer(expected: Double, actual: Double): Boolean =
        abs(expected - actual) > PROGRESSION_WEIGHT_TOLERANCE_KG + WEIGHT_COMPARISON_EPSILON_KG

    private fun insufficient(
        target: ProgressionTargetInput,
        reasonCode: ProgressionReasonCode,
        reasonArguments: Map<String, Double> = emptyMap(),
        countedSetIds: List<Long> = emptyList()
    ) = ProgressionResult(
        outcomeType = ProgressionOutcomeType.INSUFFICIENT_DATA,
        sourceTarget = target,
        reasonCode = reasonCode,
        reasonArguments = reasonArguments,
        countedSetIds = countedSetIds
    )

    private fun propose(
        target: ProgressionTargetInput,
        proposedTarget: ProgressionTargetInput,
        reasonCode: ProgressionReasonCode,
        reasonArguments: Map<String, Double>,
        streakEffect: ProgressionStreakEffect,
        countedSetIds: List<Long>
    ) = ProgressionResult(
        outcomeType = ProgressionOutcomeType.PROPOSE_CHANGE,
        sourceTarget = target,
        proposedTarget = proposedTarget,
        reasonCode = reasonCode,
        reasonArguments = reasonArguments,
        streakEffect = streakEffect,
        countedSetIds = countedSetIds
    )

    private fun keep(
        target: ProgressionTargetInput,
        reasonCode: ProgressionReasonCode,
        reasonArguments: Map<String, Double>,
        streakEffect: ProgressionStreakEffect,
        countedSetIds: List<Long>
    ) = ProgressionResult(
        outcomeType = ProgressionOutcomeType.KEEP_TARGET,
        sourceTarget = target,
        reasonCode = reasonCode,
        reasonArguments = reasonArguments,
        streakEffect = streakEffect,
        countedSetIds = countedSetIds
    )

    private sealed interface Prepared {
        data class Valid(val sets: List<ProgressionSetInput>) : Prepared
        data class Invalid(val result: ProgressionResult) : Prepared
    }

    private data class FailurePolicyValues(
        val stallThreshold: Int,
        val backoffPercent: Double
    )

    private companion object {
        val setComparator: Comparator<ProgressionSetInput> = compareBy(
            { it.setNumber },
            { it.completedAtEpochMillis },
            { it.orderingToken },
            { it.id }
        )
    }
}

/** Public common validator used by platform adapters before persistence. */
object SharedProgressionConfigValidator {
    fun validationErrors(
        target: ProgressionTargetInput,
        config: ProgressionConfigInput
    ): List<String> {
        val errors = linkedSetOf<String>()
        if (!validSuccessThreshold(config)) errors += "config.successThreshold"
        if (target.sets <= 0) errors += "target.sets"
        if (target.reps <= 0) errors += "target.reps"
        if (!target.weightKg.isFinite() || target.weightKg < 0.0) errors += "target.weightKg"
        if (config.storageReason != null || config.scheme !in SUPPORTED_SCHEMES) {
            errors += "config"
            return errors.toList()
        }

        when (config.scheme) {
            "MANUAL" -> {
                // The Android model has no active fields on Manual. Keep its
                // historical validator contract: manual is valid regardless
                // of the stored revision/policy values.
            }

            "LINEAR" -> {
                addActiveConfigErrors(errors, target, config)
                if (config.minReps != null || config.maxReps != null ||
                    config.targetTotalReps != null || config.targetRpe != null || config.rpeTolerance != null
                ) errors += "config"
            }

            "DOUBLE" -> {
                addActiveConfigErrors(errors, target, config)
                val minReps = config.minReps
                val maxReps = config.maxReps
                if (minReps == null || minReps < 1 || minReps > target.reps) errors += "config.minReps"
                if (maxReps == null || maxReps < target.reps || (minReps != null && maxReps < minReps)) {
                    errors += "config.maxReps"
                }
                if (config.targetTotalReps != null || config.targetRpe != null || config.rpeTolerance != null) {
                    errors += "config"
                }
            }

            "TOTAL_REPS" -> {
                addActiveConfigErrors(errors, target, config)
                if (config.targetTotalReps == null || config.targetTotalReps <= 0L) {
                    errors += "config.targetTotalReps"
                }
                if (config.minReps != null || config.maxReps != null ||
                    config.targetRpe != null || config.rpeTolerance != null
                ) errors += "config"
            }

            "RPE_RIR" -> {
                addActiveConfigErrors(errors, target, config)
                if (config.targetRpe == null || !config.targetRpe.isFinite() || config.targetRpe !in 1.0..10.0) {
                    errors += "config.targetRpe"
                }
                if (config.rpeTolerance == null || !config.rpeTolerance.isFinite() || config.rpeTolerance !in 0.0..2.0) {
                    errors += "config.tolerance"
                }
                if (config.minReps != null || config.maxReps != null || config.targetTotalReps != null) {
                    errors += "config"
                }
            }
        }
        return errors.toList()
    }

    private fun addActiveConfigErrors(
        errors: MutableSet<String>,
        target: ProgressionTargetInput,
        config: ProgressionConfigInput
    ) {
        val value = config.incrementValue
        val unit = config.incrementUnit
        val kilograms = config.incrementKg
        if (value == null || !value.isFinite() || value <= 0.0) errors += "config.step.originalValue"
        if (unit == null || !SUPPORTED_UNITS.contains(unit)) errors += "config.step.originalUnit"
        if (kilograms == null || !kilograms.isFinite() || kilograms <= 0.0) {
            errors += "config.step.kilograms"
        }
        if (value != null && kilograms != null && unit != null && SUPPORTED_UNITS.contains(unit)) {
            val converted = if (unit == "IMPERIAL") value / KG_TO_LB else value
            if (!converted.isFinite() || abs(converted - kilograms) > STEP_STORAGE_TOLERANCE_KG) {
                errors += "config.step.kilograms"
            }
        }
        if (config.stallThreshold !in 1..6) errors += "config.failurePolicy.stallThreshold"
        if (!config.backoffPercent.isFinite() || config.backoffPercent !in 1.0..30.0) {
            errors += "config.failurePolicy.backoffPercent"
        }
        if (kilograms != null) {
            if (unit != null && !toDisplayForValidation(target.weightKg, unit).isFinite()) {
                errors += "target.weightKg"
            }
            val increased = target.weightKg + kilograms
            if (!increased.isFinite() || increased <= target.weightKg) {
                errors += "config.step.kilograms"
            }
        }
    }

    private fun toDisplayForValidation(weightKg: Double, unit: String): Double =
        if (unit == "IMPERIAL") weightKg * KG_TO_LB else weightKg
}

private val SUPPORTED_SCHEMES = setOf("MANUAL", "LINEAR", "DOUBLE", "TOTAL_REPS", "RPE_RIR")
private val SUPPORTED_UNITS = setOf("METRIC", "IMPERIAL")

private fun validSuccessThreshold(config: ProgressionConfigInput): Boolean =
    config.successThreshold in 1..6 &&
        (config.successThreshold == 1 || (config.scheme == "LINEAR" && config.ruleRevision == 2))

/** A reset alone is not evidence of success (e.g. imported or informational outcomes). */
fun isSuccessfulProgression(reasonCode: String, streakEffect: String): Boolean =
    streakEffect == "RESET" && reasonCode in setOf(
        "LOAD_ADVANCED", "REP_TARGET_ADVANCED", "TOTAL_REPS_COMPLETED", "RPE_WITHIN_TARGET", "SUCCESS_CONFIRMATION_REQUIRED"
    )
