package com.ironlog.app.domain.progression

import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.PreviousProgressionOutcome
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.WeightFormatting
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {
    private val engine = ProgressionEngine()

    @Test
    fun `linear success adds exactly configured step`() {
        val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 8)))
        val change = result as ProgressionOutcome.ProposeChange
        assertEquals(102.5, change.proposedTarget.weightKg, 0.000001)
        assertEquals(ProgressionReasonCode.LOAD_ADVANCED, change.reasonCode)
        assertEquals(ProgressionStreakEffect.RESET, change.streakEffect)
        assertEquals(
            mapOf("targetReps" to 8.0, "actualReps" to 8.0, "stepOriginalValue" to 2.5),
            change.reasonArguments
        )
    }

    @Test
    fun `double progression advances repetitions before weight`() {
        val result = engine.evaluate(context(double(min = 8, max = 10), targetReps = 8, reps = listOf(8, 8, 8)))
        val change = result as ProgressionOutcome.ProposeChange
        assertEquals(9, change.proposedTarget.reps)
        assertEquals(100.0, change.proposedTarget.weightKg, 0.0)
    }

    @Test
    fun `double progression raises weight and resets repetitions at ceiling`() {
        val result = engine.evaluate(context(double(min = 8, max = 10), targetReps = 10, reps = listOf(10, 10, 10)))
        val change = result as ProgressionOutcome.ProposeChange
        assertEquals(8, change.proposedTarget.reps)
        assertEquals(102.5, change.proposedTarget.weightKg, 0.000001)
        assertEquals(ProgressionReasonCode.LOAD_ADVANCED, change.reasonCode)
    }

    @Test
    fun `total reps ignores distribution and extra sets`() {
        val first = engine.evaluate(context(totalReps(24), reps = listOf(8, 8, 8, 100)))
        val second = engine.evaluate(context(totalReps(24), reps = listOf(6, 7, 11, 1)))
        assertEquals(
            (first as ProgressionOutcome.ProposeChange).proposedTarget,
            (second as ProgressionOutcome.ProposeChange).proposedTarget
        )
        assertEquals(listOf(1L, 2L, 3L), first.countedSetIds)
        assertEquals(listOf(1L, 2L, 3L), second.countedSetIds)
    }

    @Test
    fun `rpe progression uses highest counted rpe`() {
        val result = engine.evaluate(context(rpe(target = 8.0, tolerance = 0.5), reps = listOf(8, 8, 8), rpes = listOf(7.5, 8.0, 8.5)))
        assertTrue(result is ProgressionOutcome.ProposeChange)
        assertEquals(ProgressionReasonCode.RPE_WITHIN_TARGET, result.reasonCode)
        assertEquals(
            mapOf("highestRpe" to 8.5, "targetRpe" to 8.0, "tolerance" to 0.5, "stepOriginalValue" to 2.5),
            result.reasonArguments
        )
    }

    @Test
    fun `warmups never satisfy missing planned sets`() {
        val result = engine.evaluate(context(linear(), reps = listOf(8, 8), warmupReps = listOf(20)))
        assertEquals(ProgressionReasonCode.TOO_FEW_WORK_SETS, result.reasonCode)
        assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
        assertEquals(mapOf("targetSets" to 3.0, "actualWorkSets" to 2.0), result.reasonArguments)
    }

    @Test
    fun `extra work sets never rescue a miss in the counted sets`() {
        val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 7), extraReps = listOf(20)))
        assertTrue(result is ProgressionOutcome.KeepTarget)
        assertEquals(ProgressionReasonCode.REPEAT_TARGET, result.reasonCode)
        assertEquals(listOf(1L, 2L, 3L), result.countedSetIds)
    }

    @Test
    fun `manual weight deviation is insufficient data`() {
        val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), weights = listOf(100.0, 100.02, 100.0)))
        assertEquals(ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION, result.reasonCode)
        assertEquals(
            mapOf("expectedWeightKg" to 100.0, "actualWeightKg" to 100.02),
            result.reasonArguments
        )
    }

    @Test
    fun `weight tolerance includes exactly one hundredth kilogram`() {
        val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), weights = listOf(100.0, 100.01, 99.99)))

        assertTrue(result is ProgressionOutcome.ProposeChange)
    }

    @Test
    fun `missing rpe ignores rather than resets failure streak`() {
        val result = engine.evaluate(context(rpe(8.0, 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, null, 8.0)))
        assertEquals(ProgressionReasonCode.RPE_MISSING, result.reasonCode)
        assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
    }

    @Test
    fun `high rpe after completed work resets failure streak without increasing`() {
        val result = engine.evaluate(context(rpe(8.0, 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, 9.0, 8.0)))
        assertTrue(result is ProgressionOutcome.KeepTarget)
        assertEquals(ProgressionStreakEffect.RESET, result.streakEffect)
        assertEquals(mapOf("highestRpe" to 9.0), result.reasonArguments)
    }

    @Test
    fun `only comparable increment outcomes reach stall threshold`() {
        val previous = listOf(
            previous(ProgressionStreakEffect.IGNORE),
            previous(ProgressionStreakEffect.INCREMENT)
        )
        val result = engine.evaluate(context(linear(stallThreshold = 2), reps = listOf(8, 8, 7), previous = previous))
        assertEquals(ProgressionReasonCode.STALL_BACKOFF, result.reasonCode)
        assertEquals(mapOf("backoffPercent" to 10.0), result.reasonArguments)
    }

    @Test
    fun `a reset outcome stops the older failure streak`() {
        val previous = listOf(
            previous(ProgressionStreakEffect.RESET),
            previous(ProgressionStreakEffect.INCREMENT)
        )
        val result = engine.evaluate(context(linear(stallThreshold = 2), reps = listOf(8, 8, 7), previous = previous))
        assertTrue(result is ProgressionOutcome.KeepTarget)
        assertEquals(ProgressionReasonCode.REPEAT_TARGET, result.reasonCode)
    }

    @Test
    fun `imperial backoff rounds ties downward and remains below current weight`() {
        val result = engine.evaluate(
            context(
                linearImperial(stepLb = 5.0, backoff = 10.0),
                targetWeightKg = WeightFormatting.convertToKg(100.0, UnitSystem.IMPERIAL),
                reps = listOf(8, 8, 7),
                previous = listOf(previous(ProgressionStreakEffect.INCREMENT))
            )
        )
        val change = result as ProgressionOutcome.ProposeChange
        assertEquals(90.0, WeightFormatting.convertToDisplay(change.proposedTarget.weightKg, UnitSystem.IMPERIAL), 0.000001)
    }

    @Test
    fun `backoff at zero keeps target instead of proposing an identical change`() {
        val result = engine.evaluate(
            context(
                linear(stallThreshold = 2),
                targetWeightKg = 0.0,
                weights = listOf(0.0, 0.0, 0.0),
                reps = listOf(8, 8, 7),
                previous = listOf(previous(ProgressionStreakEffect.INCREMENT))
            )
        )
        assertTrue(result is ProgressionOutcome.KeepTarget)
        assertEquals(ProgressionReasonCode.BACKOFF_FLOOR_REACHED, result.reasonCode)
        assertEquals(ProgressionStreakEffect.INCREMENT, result.streakEffect)
    }

    @Test
    fun `unsupported revision never falls through to revision one`() {
        val result = engine.evaluate(context(linear(ruleRevision = 99), reps = listOf(8, 8, 8)))
        assertEquals(ProgressionReasonCode.RULE_REVISION_UNSUPPORTED, result.reasonCode)
        assertTrue(result is ProgressionOutcome.InsufficientData)
    }

    @Test
    fun `manual scheme is not applicable`() {
        val result = engine.evaluate(context(ProgressionConfig.Manual(), reps = listOf(8, 8, 8)))

        assertTrue(result is ProgressionOutcome.NotApplicable)
        assertEquals(ProgressionReasonCode.MANUAL_SCHEME, result.reasonCode)
        assertEquals(emptyList<Long>(), result.countedSetIds)
    }

    @Test
    fun `invalid configurations fail closed without counting a failure`() {
        val invalidContexts = listOf(
            context(linear(stepKg = Double.NaN), reps = listOf(8, 8, 8)),
            context(linear(stepKg = Double.NEGATIVE_INFINITY), reps = listOf(8, 8, 8)),
            context(linear(stepKg = Double.POSITIVE_INFINITY), reps = listOf(8, 8, 8)),
            context(double(min = 10, max = 8), reps = listOf(8, 8, 8)),
            context(totalReps(0), reps = listOf(8, 8, 8)),
            context(rpe(target = 11.0, tolerance = 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, 8.0, 8.0)),
            context(rpe(target = 8.0, tolerance = 3.0), reps = listOf(8, 8, 8), rpes = listOf(8.0, 8.0, 8.0)),
            context(linear(stallThreshold = 0), reps = listOf(8, 8, 8)),
            context(linear(backoff = 31.0), reps = listOf(8, 8, 8)),
            context(linear(stepKg = Double.MIN_VALUE), targetWeightKg = 100.0, reps = listOf(8, 8, 8)),
            context(linear(), targetWeightKg = Double.MAX_VALUE, weights = listOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), reps = listOf(8, 8, 8)),
            context(linear(stepOriginalValue = 5.0, stepKg = 2.5), reps = listOf(8, 8, 8)),
            context(linear(), targetSets = 0, reps = listOf(8, 8, 8)),
            context(linear(), targetReps = 0, reps = listOf(8, 8, 8)),
            context(linear(), targetWeightKg = Double.NEGATIVE_INFINITY, weights = listOf(0.0, 0.0, 0.0), reps = listOf(8, 8, 8)),
            context(linear(), targetWeightKg = Double.POSITIVE_INFINITY, weights = listOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), reps = listOf(8, 8, 8))
        )

        invalidContexts.forEach { invalid ->
            val result = engine.evaluate(invalid)
            assertTrue(result is ProgressionOutcome.InsufficientData)
            assertEquals(ProgressionReasonCode.CONFIG_INVALID, result.reasonCode)
            assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
        }
    }

    @Test
    fun `configuration boundaries remain valid`() {
        val validContexts = listOf(
            context(rpe(target = 1.0, tolerance = 0.0), rpes = listOf(1.0, 1.0, 1.0)),
            context(rpe(target = 10.0, tolerance = 2.0), rpes = listOf(10.0, 10.0, 10.0)),
            context(linear(stallThreshold = 1, backoff = 1.0), reps = listOf(8, 8, 7)),
            context(
                linear(stallThreshold = 6, backoff = 30.0),
                reps = listOf(8, 8, 7),
                previous = List(5) { previous(ProgressionStreakEffect.INCREMENT) }
            )
        )

        validContexts.forEach { valid ->
            val result = engine.evaluate(valid)
            assertTrue(result !is ProgressionOutcome.InsufficientData)
            assertTrue(result.reasonArguments.values.all(Double::isFinite))
        }
    }

    @Test
    fun `counted set ids are deterministic and exclude warmups and extras`() {
        val result = engine.evaluate(
            context(
                linear(),
                reps = listOf(8, 8, 8),
                workSetIds = listOf(30, 10, 20),
                workSetNumbers = listOf(3, 1, 2),
                warmupSetIds = listOf(1),
                extraSetIds = listOf(40)
            )
        )

        assertEquals(listOf(10L, 20L, 30L), result.countedSetIds)
    }

    @Test
    fun `invalid set numbers preserve available evidence but do not count a failure`() {
        val duplicate = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), workSetNumbers = listOf(1, 1, 2)))
        val nonPositive = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), workSetNumbers = listOf(0, 1, 2)))

        listOf(duplicate, nonPositive).forEach { result ->
            assertTrue(result is ProgressionOutcome.InsufficientData)
            assertEquals(ProgressionReasonCode.SET_NUMBER_INVALID, result.reasonCode)
            assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
            assertEquals(listOf(1L, 2L, 3L), result.countedSetIds.sorted())
        }
    }

    @Test
    fun `invalid actual set values fail closed without advancing failure streak`() {
        val invalidReps = engine.evaluate(context(linear(), reps = listOf(8, -1, 8)))
        val invalidWeight = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), weights = listOf(100.0, Double.NaN, 100.0)))
        val negativeInfiniteWeight = engine.evaluate(context(linear(), weights = listOf(100.0, Double.NEGATIVE_INFINITY, 100.0)))
        val positiveInfiniteWeight = engine.evaluate(context(linear(), weights = listOf(100.0, Double.POSITIVE_INFINITY, 100.0)))
        val invalidRpe = engine.evaluate(context(rpe(8.0, 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, 0.0, 8.0)))
        val negativeInfiniteRpe = engine.evaluate(context(rpe(8.0, 0.5), rpes = listOf(8.0, Double.NEGATIVE_INFINITY, 8.0)))
        val positiveInfiniteRpe = engine.evaluate(context(rpe(8.0, 0.5), rpes = listOf(8.0, Double.POSITIVE_INFINITY, 8.0)))

        assertEquals(ProgressionReasonCode.SET_VALUE_INVALID, invalidReps.reasonCode)
        listOf(invalidWeight, negativeInfiniteWeight, positiveInfiniteWeight).forEach {
            assertEquals(ProgressionReasonCode.SET_VALUE_INVALID, it.reasonCode)
        }
        listOf(invalidRpe, negativeInfiniteRpe, positiveInfiniteRpe).forEach {
            assertEquals(ProgressionReasonCode.RPE_INVALID, it.reasonCode)
        }
        listOf(invalidReps, invalidWeight, negativeInfiniteWeight, positiveInfiniteWeight, invalidRpe, negativeInfiniteRpe, positiveInfiniteRpe).forEach {
            assertTrue(it is ProgressionOutcome.InsufficientData)
            assertEquals(ProgressionStreakEffect.IGNORE, it.streakEffect)
            assertTrue(it.reasonArguments.values.all { value -> value.isFinite() })
        }
    }

    @Test
    fun `invalid values in extra work sets do not change the counted outcome`() {
        val result = engine.evaluate(
            context(
                linear(),
                reps = listOf(8, 8, 8, -1),
                weights = listOf(100.0, 100.0, 100.0, Double.NaN)
            )
        )

        assertTrue(result is ProgressionOutcome.ProposeChange)
        assertEquals(listOf(1L, 2L, 3L), result.countedSetIds)
    }

    @Test
    fun `invalid set identity and ids fail closed with deterministic available evidence`() {
        val wrongIdentity = context(linear()).let { valid ->
            valid.copy(setsForTarget = valid.setsForTarget.mapIndexed { index, set ->
                if (index == 1) set.copy(sessionId = 999) else set
            })
        }
        val duplicateId = context(linear()).let { valid ->
            valid.copy(setsForTarget = valid.setsForTarget.mapIndexed { index, set ->
                if (index == 1) set.copy(id = 1) else set
            })
        }
        val nonPositiveId = context(linear()).let { valid ->
            valid.copy(setsForTarget = valid.setsForTarget.mapIndexed { index, set ->
                if (index == 1) set.copy(id = 0) else set
            })
        }

        listOf(wrongIdentity, duplicateId, nonPositiveId).forEach { invalid ->
            val result = engine.evaluate(invalid)
            assertTrue(result is ProgressionOutcome.InsufficientData)
            assertEquals(ProgressionReasonCode.SET_VALUE_INVALID, result.reasonCode)
            assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
            assertEquals(result.countedSetIds.distinct(), result.countedSetIds)
            assertTrue(result.countedSetIds.all { it > 0 })
        }
    }

    private fun context(
        config: ProgressionConfig,
        targetSets: Int = 3,
        targetReps: Int = 8,
        targetWeightKg: Double = 100.0,
        reps: List<Int> = listOf(8, 8, 8),
        weights: List<Double> = List(reps.size) { targetWeightKg },
        rpes: List<Double?> = List(reps.size) { null },
        warmupReps: List<Int> = emptyList(),
        extraReps: List<Int> = emptyList(),
        workSetIds: List<Long> = reps.indices.map { it + 1L },
        workSetNumbers: List<Int> = reps.indices.map { it + 1 },
        warmupSetIds: List<Long> = emptyList(),
        extraSetIds: List<Long> = emptyList(),
        previous: List<PreviousProgressionOutcome> = emptyList()
    ): ProgressionContext {
        val source = target(config, targetSets, targetReps, targetWeightKg)
        val workCount = maxOf(reps.size, workSetIds.size, workSetNumbers.size)
        val workSets = List(workCount) { index ->
            workoutSet(
                id = workSetIds.getOrElse(index) { index + 1L },
                setNumber = workSetNumbers.getOrElse(index) { index + 1 },
                reps = reps.getOrElse(index) { targetReps },
                weightKg = weights.getOrElse(index) { targetWeightKg },
                rpe = rpes.getOrElse(index) { null }
            )
        }
        val warmupCount = maxOf(warmupReps.size, warmupSetIds.size)
        val warmups = List(warmupCount) { index ->
            workoutSet(
                id = warmupSetIds.getOrElse(index) { workCount + index + 1L },
                setNumber = index + 1,
                reps = warmupReps.getOrElse(index) { targetReps },
                weightKg = targetWeightKg,
                isWarmup = true
            )
        }
        val extraCount = maxOf(extraReps.size, extraSetIds.size)
        val extras = List(extraCount) { index ->
            workoutSet(
                id = extraSetIds.getOrElse(index) { workCount + warmupCount + index + 1L },
                setNumber = workCount + index + 1,
                reps = extraReps.getOrElse(index) { targetReps },
                weightKg = targetWeightKg
            )
        }
        return ProgressionContext(source, workSets + warmups + extras, previous)
    }

    private fun workoutSet(
        id: Long,
        setNumber: Int,
        reps: Int,
        weightKg: Double,
        isWarmup: Boolean = false,
        rpe: Double? = null
    ) = WorkoutSet(
        id = id,
        sessionId = SESSION_ID,
        exerciseId = EXERCISE_ID,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        isWarmup = isWarmup,
        completedAt = LocalDateTime.of(2026, 8, 9, 12, 0).plusSeconds(id.coerceAtLeast(0)),
        rpe = rpe,
        planTargetSnapshotId = SNAPSHOT_ID
    )

    private fun target(
        config: ProgressionConfig,
        sets: Int = 3,
        reps: Int = 8,
        weightKg: Double = 100.0
    ) = WorkoutPlanTarget(
        id = SNAPSHOT_ID,
        sessionId = SESSION_ID,
        planId = 44,
        exerciseId = EXERCISE_ID,
        orderIndex = 0,
        supersetGroupId = null,
        target = ProgressionTarget(sets, reps, weightKg),
        config = config
    )

    private fun previous(effect: ProgressionStreakEffect) = PreviousProgressionOutcome(
        sourceTarget = target(ProgressionConfig.Manual()),
        streakEffect = effect
    )

    private fun linear(
        stepKg: Double = 2.5,
        stepOriginalValue: Double = stepKg,
        stallThreshold: Int = 2,
        backoff: Double = 10.0,
        ruleRevision: Int = 1
    ) = ProgressionConfig.Linear(
        step = WeightStep(stepOriginalValue, UnitSystem.METRIC, stepKg),
        failurePolicy = FailurePolicy(stallThreshold, backoff),
        ruleRevision = ruleRevision
    )

    private fun linearImperial(
        stepLb: Double,
        backoff: Double,
        stallThreshold: Int = 2
    ) = ProgressionConfig.Linear(
        step = WeightStep(stepLb, UnitSystem.IMPERIAL, WeightFormatting.convertToKg(stepLb, UnitSystem.IMPERIAL)),
        failurePolicy = FailurePolicy(stallThreshold, backoff)
    )

    private fun double(
        min: Int,
        max: Int,
        stepKg: Double = 2.5,
        stallThreshold: Int = 2,
        backoff: Double = 10.0
    ) = ProgressionConfig.DoubleProgression(
        minReps = min,
        maxReps = max,
        step = WeightStep(stepKg, UnitSystem.METRIC, stepKg),
        failurePolicy = FailurePolicy(stallThreshold, backoff)
    )

    private fun totalReps(
        target: Long,
        stepKg: Double = 2.5,
        stallThreshold: Int = 2,
        backoff: Double = 10.0
    ) = ProgressionConfig.TotalReps(
        targetTotalReps = target,
        step = WeightStep(stepKg, UnitSystem.METRIC, stepKg),
        failurePolicy = FailurePolicy(stallThreshold, backoff)
    )

    private fun rpe(
        target: Double,
        tolerance: Double,
        stepKg: Double = 2.5,
        stallThreshold: Int = 2,
        backoff: Double = 10.0
    ) = ProgressionConfig.RpeRir(
        targetRpe = target,
        tolerance = tolerance,
        step = WeightStep(stepKg, UnitSystem.METRIC, stepKg),
        failurePolicy = FailurePolicy(stallThreshold, backoff)
    )

    private companion object {
        const val SESSION_ID = 11L
        const val EXERCISE_ID = 22L
        const val SNAPSHOT_ID = 33L
    }
}
