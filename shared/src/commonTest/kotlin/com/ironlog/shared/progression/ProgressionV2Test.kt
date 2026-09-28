package com.ironlog.shared.progression

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionV2Test {
    private val engine = SharedProgressionEngine()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun failureMarkerCountsAsWorkAndDoesNotItselfMeanAMissedTarget() {
        val result = evaluate(sets = sets(kinds = listOf("NORMAL", "NORMAL", "FAILURE")))
        assertEquals(ProgressionOutcomeType.PROPOSE_CHANGE, result.outcomeType)
        assertEquals(102.5, result.proposedTarget?.weightKg)
        assertEquals(listOf(1L, 2L, 3L), result.countedSetIds)
        assertEquals(1.0, result.reasonArguments["failureMarkedSets"])
    }

    @Test
    fun missedRepsInAFailureSetPreventAnIncrease() {
        val result = evaluate(sets = sets(reps = listOf(8, 8, 7), kinds = listOf("NORMAL", "NORMAL", "FAILURE")))
        assertEquals(ProgressionReasonCode.REPEAT_TARGET, result.reasonCode)
        assertEquals(ProgressionStreakEffect.INCREMENT, result.streakEffect)
        assertNull(result.proposedTarget)
    }

    @Test
    fun warmupDropAndBackoffAreExcludedAndExtraWorkCannotRescueAMiss() {
        val evidence = sets(
            reps = listOf(20, 8, 8, 7, 20, 20, 20),
            kinds = listOf("WARMUP", "NORMAL", "NORMAL", "FAILURE", "DROP_SET", "BACKOFF", "NORMAL")
        )
        val result = evaluate(sets = evidence)
        assertEquals(listOf(2L, 3L, 4L), result.countedSetIds)
        assertEquals(ProgressionReasonCode.REPEAT_TARGET, result.reasonCode)
        assertEquals(1.0, result.reasonArguments["ignoredWarmupSets"])
        assertEquals(1.0, result.reasonArguments["ignoredDropSets"])
        assertEquals(1.0, result.reasonArguments["ignoredBackoffSets"])
        assertEquals(1.0, result.reasonArguments["ignoredExtraSets"])
    }

    @Test
    fun mixedLoadsOnlyConfirmThePlannedLoadWhenEveryWorkSetMeetsIt() {
        val result = evaluate(sets = sets(weights = listOf(100.0, 105.0, 102.5)))
        assertEquals(ProgressionOutcomeType.PROPOSE_CHANGE, result.outcomeType)
        assertEquals(102.5, result.proposedTarget?.weightKg)
        assertEquals(100.0, result.reasonArguments["actualWeightKg"])
        assertEquals(105.0, result.reasonArguments["maxWeightKg"])
    }

    @Test
    fun droppingBelowThePlanInMixedWorkKeepsThePlanWithoutAFailureStreak() {
        val result = evaluate(sets = sets(weights = listOf(100.0, 97.5, 95.0)))
        assertEquals(ProgressionOutcomeType.KEEP_TARGET, result.outcomeType)
        assertEquals("MIXED_LOADS", result.reasonCode.name)
        assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
        assertNull(result.proposedTarget)
    }

    @Test
    fun mixedLoadsWithMissedRepsCannotManufactureAComparableFailure() {
        val result = evaluate(sets = sets(reps = listOf(8, 8, 6), weights = listOf(100.0, 105.0, 102.5)))
        assertEquals("MIXED_LOADS", result.reasonCode.name)
        assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
    }

    @Test
    fun linearWaitsUntilTheConfiguredNumberOfComparableSuccesses() {
        val first = evaluate(config = linear(successes = 2))
        assertEquals("SUCCESS_CONFIRMATION_REQUIRED", first.reasonCode.name)
        assertEquals(ProgressionOutcomeType.KEEP_TARGET, first.outcomeType)
        assertEquals(1.0, first.reasonArguments["successfulSessions"])
        assertEquals(2.0, first.reasonArguments["requiredSuccesses"])
        val second = evaluate(config = linear(successes = 2), history = listOf(history(successful = true)))
        assertEquals(102.5, second.proposedTarget?.weightKg)
        assertEquals(2.0, second.reasonArguments["successfulSessions"])
    }

    @Test
    fun ANonSuccessBreaksTheSuccessChainEvenIfAnOlderWorkoutSucceeded() {
        val result = evaluate(
            config = linear(successes = 2),
            history = listOf(history(successful = false), history(successful = true))
        )
        assertEquals("SUCCESS_CONFIRMATION_REQUIRED", result.reasonCode.name)
        assertEquals(1.0, result.reasonArguments["successfulSessions"])
    }

    @Test
    fun doubleProgressionUsesCompletedRepsBelowTheCeiling() {
        val result = evaluate(
            config = linear().copy(scheme = "DOUBLE", minReps = 8, maxReps = 12),
            sets = sets(reps = listOf(10, 10, 9))
        )
        assertEquals(10, result.proposedTarget?.reps)
        assertEquals(100.0, result.proposedTarget?.weightKg)
    }

    @Test
    fun incompleteAndUnknownWorkCannotAdvanceOrIncrementFailures() {
        val incomplete = evaluate(sets = sets(reps = listOf(8, 8)))
        assertEquals(ProgressionReasonCode.TOO_FEW_WORK_SETS, incomplete.reasonCode)
        val unknown = evaluate(sets = sets(kinds = listOf("NORMAL", "FUTURE_KIND", "NORMAL")))
        assertEquals(ProgressionReasonCode.SET_VALUE_INVALID, unknown.reasonCode)
        assertEquals(ProgressionStreakEffect.IGNORE, unknown.streakEffect)
    }

    @Test
    fun unsupportedThresholdIsRejectedRatherThanSilentlyIgnored() {
        val result = evaluate(config = linear(successes = 0))
        assertEquals(ProgressionReasonCode.CONFIG_INVALID, result.reasonCode)
    }

    @Test
    fun revisionOneKeepsItsHistoricalSetAndMixedWeightRules() {
        val config = linear().copy(ruleRevision = 1)
        val failure = evaluate(config, sets(kinds = listOf("NORMAL", "NORMAL", "FAILURE")))
        assertEquals(ProgressionReasonCode.TOO_FEW_WORK_SETS, failure.reasonCode)
        val mixed = evaluate(config, sets(weights = listOf(100.0, 105.0, 102.5)))
        assertEquals(ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION, mixed.reasonCode)
    }

    private fun linear(successes: Int = 1): ProgressionConfigInput = json.decodeFromString(
        """{"scheme":"LINEAR","incrementValue":2.5,"incrementUnit":"METRIC","incrementKg":2.5,"ruleRevision":2,"successThreshold":$successes}"""
    )

    private fun history(successful: Boolean): ProgressionPreviousOutcomeInput = json.decodeFromString(
        """{"sourceTarget":{"sets":3,"reps":8,"weightKg":100.0},"streakEffect":"RESET","successful":$successful}"""
    )

    private fun evaluate(
        config: ProgressionConfigInput = linear(),
        sets: List<ProgressionSetInput> = sets(),
        history: List<ProgressionPreviousOutcomeInput> = emptyList()
    ) = engine.evaluate(ProgressionInput(
        sourceTarget = ProgressionPlanTargetInput(99L, 10L, 20L, 30L, 0, target = ProgressionTargetInput(3, 8, 100.0), config = config),
        setsForTarget = sets,
        previousComparableOutcomesNewestFirst = history
    ))

    private fun sets(
        reps: List<Int> = listOf(8, 8, 8),
        weights: List<Double> = List(reps.size) { 100.0 },
        kinds: List<String> = List(reps.size) { "NORMAL" }
    ) = reps.indices.map { index ->
        ProgressionSetInput(
            id = index + 1L, sessionId = 10L, exerciseId = 30L, setNumber = index + 1,
            reps = reps[index], weightKg = weights[index], setType = kinds[index], planTargetSnapshotId = 99L
        )
    }
}
