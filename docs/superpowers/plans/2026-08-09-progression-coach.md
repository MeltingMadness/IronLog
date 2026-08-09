# Progression Coach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android-first, local progression coach that evaluates four deterministic progression schemes from immutable workout snapshots and changes plan targets only after an explicit, atomic user confirmation.

**Architecture:** Keep the rule engine pure in `core:common`, its typed contract in `core:model`, and all Room/backup mapping in `core:database` plus `data`. A session-owned target snapshot gives every planned exercise occurrence a stable identity from workout start through set logging, evaluation, review, retry, and backup; the current plan is consulted only during stale-safe acceptance.

**Tech Stack:** Kotlin 2.3.10, Android/Jetpack Compose, Room, coroutines/Flow, Koin, kotlinx.serialization, JUnit 4, MockK, Turbine, AndroidX instrumentation.

## Global Constraints

- Android is the only implementation target in this increment; do not add iOS, percentage blocks, periodized calendars, AI/cloud recommendations, wearables, readiness, nutrition, pain, injury, or medical scoring.
- Existing and new plan exercises default to `MANUAL`; migration and legacy backup import must never activate progression or change an existing target.
- Supported active schemes are exactly `LINEAR`, `DOUBLE`, `TOTAL_REPS`, and `RPE_RIR`, all at rule revision `1`.
- A workout session and all of its plan-target snapshots are created in one Room transaction; failure to copy every plan position means the workout did not start.
- Each planned set references its exact nullable `planTargetSnapshotId`; legacy, free-workout, and ad-hoc sets keep `null` and are never evaluated.
- Warm-ups never count; sort work sets by `setNumber`, then `completedAt`, then `id`, reject non-positive or duplicate set numbers as insufficient data, and evaluate only the first `targetSets` work sets.
- A work weight is comparable only within `0.01 kg` of the snapshot target; extra sets remain visible but never prove completion.
- Every target requires positive sets/repetitions and a finite non-negative canonical weight. Every active scheme requires a finite positive weight step; double progression requires `1 <= minReps <= target.reps <= maxReps`, and total-reps progression requires `targetTotalReps > 0`.
- `targetRpe` and evaluated RPE values must be finite and in `1.0..10.0`; tolerance must be finite and in `0.0..2.0`.
- Stall threshold is an integer in `1..6`, default `2`; backoff is finite in `1.0..30.0`, default `10.0`.
- The default increment is `2.5 kg` or `5 lb`; persist original value, original `UnitSystem`, and canonical kilograms so retries are independent of later preference changes.
- Backoff rounding happens in the originally configured unit to the nearest increment; an exact tie chooses the lower value, a non-decreasing result subtracts one increment, and the result never drops below zero.
- Workout completion and outcome generation are separate operations; generation failure leaves the workout completed and exposes `Retry` plus `Später`.
- Outcome insertion is idempotent on `(sourceTargetSnapshotId, ruleRevision)`; unsupported revisions fail closed with `INSUFFICIENT_DATA/RULE_REVISION_UNSUPPORTED`.
- Suggestions can change target sets, reps, or weight only through explicit single or batch acceptance; batch acceptance is all-or-nothing and directly updates matching plan-exercise rows.
- A changed target, scheme, configuration, revision, exercise identity, or order makes the suggestion `STALE`; no selected plan row may change if any selected suggestion fails validation.
- Database version and backup schema version become `11`; backup format remains `1`, and every new backup field has a legacy-safe default.
- Use the smallest targeted test named in each task. Local emulator execution is unavailable; instrumentation tests compile locally and run in the existing remote `connectedDebugAndroidTest` gate.
- Kill criterion: stop and revise the design if any suggestion cannot be reproduced from its stored snapshot plus linked sets, or if any code path can mutate a plan without a user-confirmed transaction.

---

## Locked File Structure and Interfaces

The following boundaries are fixed for the implementation so that workers do not invent competing representations:

- `core:model/.../Progression.kt` owns all typed values, outcomes, stored review items, and result types.
- `core:model/.../ProgressionRepository.kt` is the only feature-facing progression persistence contract.
- `core:common/.../progression/ProgressionEngine.kt` dispatches by `(ProgressionScheme, ruleRevision)`.
- `core:common/.../progression/v1/*RuleV1.kt` freezes revision-1 behavior; never edit V1 semantics after release, add a new revision instead.
- `core:database/.../entity/ProgressionStorageColumns.kt` is the single flat Room representation of typed config/target values.
- `core:database/.../entity/WorkoutPlanTargetEntity.kt` and `ProgressionSuggestionEntity.kt` are the two new tables.
- `core:database/.../dao/ProgressionDao.kt` owns snapshot/outcome queries; `TrainingPlanDao` owns direct plan-row updates.
- `data/.../repository/ProgressionRepositoryImpl.kt` owns generation, retry, stale reconciliation, and atomic decisions.
- `feature:workout` uses `WorkoutExerciseKey.Planned(snapshotId)` or `WorkoutExerciseKey.AdHoc(exerciseId)`; no planned UI or set operation may key by `exerciseId` alone.
- `feature:progression` owns the review screen and review ViewModel.

### Task 1: Freeze the typed progression contract

**Files:**
- Create: `core/model/src/main/java/com/ironlog/app/domain/model/Progression.kt`
- Create: `core/model/src/main/java/com/ironlog/app/domain/repository/ProgressionRepository.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/model/TrainingPlan.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/model/WorkoutSet.kt`
- Modify: `core/common/build.gradle.kts`
- Create: `core/common/src/test/java/com/ironlog/app/domain/progression/ProgressionContractTest.kt`

**Interfaces:**
- Consumes: existing `UnitSystem`, `WorkoutSet`, `PlanExercise`, and `Flow`.
- Produces: `ProgressionConfig`, `WorkoutPlanTarget`, `ProgressionContext`, `ProgressionOutcome`, `ProgressionSuggestion`, and `ProgressionRepository` used by every later task.

- [ ] **Step 1: Write the failing contract test**

```kotlin
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
```

- [ ] **Step 2: Run the contract test and observe the missing-type failure**

Run: `./gradlew --no-daemon :core:common:testDebugUnitTest --tests "com.ironlog.app.domain.progression.ProgressionContractTest"`

Expected: FAIL during Kotlin compilation because `ProgressionConfig` and `WeightStep` do not exist.

- [ ] **Step 3: Add the exact domain model**

Create `Progression.kt` with these public types and property names:

```kotlin
package com.ironlog.app.domain.model

const val CURRENT_PROGRESSION_RULE_REVISION = 1

enum class ProgressionScheme { MANUAL, LINEAR, DOUBLE, TOTAL_REPS, RPE_RIR }

data class WeightStep(
    val originalValue: Double,
    val originalUnit: UnitSystem,
    val kilograms: Double
)

data class FailurePolicy(
    val stallThreshold: Int = 2,
    val backoffPercent: Double = 10.0
)

sealed interface ProgressionConfig {
    val scheme: ProgressionScheme
    val ruleRevision: Int

    data class Manual(
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.MANUAL
    }

    data class Linear(
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.LINEAR
    }

    data class DoubleProgression(
        val minReps: Int,
        val maxReps: Int,
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.DOUBLE
    }

    data class TotalReps(
        val targetTotalReps: Long,
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.TOTAL_REPS
    }

    data class RpeRir(
        val targetRpe: Double,
        val tolerance: Double,
        val step: WeightStep,
        val failurePolicy: FailurePolicy = FailurePolicy(),
        override val ruleRevision: Int = CURRENT_PROGRESSION_RULE_REVISION
    ) : ProgressionConfig {
        override val scheme = ProgressionScheme.RPE_RIR
    }

    data class Invalid(
        override val scheme: ProgressionScheme,
        override val ruleRevision: Int,
        val storageReason: String,
        val rawScheme: String = scheme.name
    ) : ProgressionConfig
}

data class ProgressionTarget(val sets: Int, val reps: Int, val weightKg: Double)

data class WorkoutPlanTarget(
    val id: Long,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int?,
    val target: ProgressionTarget,
    val config: ProgressionConfig
)

enum class ProgressionOutcomeType { PROPOSE_CHANGE, KEEP_TARGET, INSUFFICIENT_DATA, NOT_APPLICABLE }
enum class ProgressionStreakEffect { INCREMENT, RESET, IGNORE }

enum class ProgressionReasonCode {
    REP_TARGET_ADVANCED,
    LOAD_ADVANCED,
    TOTAL_REPS_COMPLETED,
    RPE_WITHIN_TARGET,
    REPEAT_TARGET,
    STALL_BACKOFF,
    MANUAL_WEIGHT_DEVIATION,
    TOO_FEW_WORK_SETS,
    RPE_MISSING,
    RPE_INVALID,
    CONFIG_INVALID,
    RULE_REVISION_UNSUPPORTED,
    MANUAL_SCHEME,
    SET_NUMBER_INVALID,
    SET_VALUE_INVALID,
    BACKOFF_FLOOR_REACHED
}

data class PreviousProgressionOutcome(
    val sourceTarget: WorkoutPlanTarget,
    val streakEffect: ProgressionStreakEffect
)

data class ProgressionContext(
    val sourceTarget: WorkoutPlanTarget,
    val setsForTarget: List<WorkoutSet>,
    val previousComparableOutcomesNewestFirst: List<PreviousProgressionOutcome>
)

sealed interface ProgressionOutcome {
    val type: ProgressionOutcomeType
    val sourceTarget: ProgressionTarget
    val reasonCode: ProgressionReasonCode
    val reasonArguments: Map<String, Double>
    val streakEffect: ProgressionStreakEffect
    val countedSetIds: List<Long>

    data class ProposeChange(
        override val sourceTarget: ProgressionTarget,
        val proposedTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.PROPOSE_CHANGE
    }

    data class KeepTarget(
        override val sourceTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.KEEP_TARGET
    }

    data class InsufficientData(
        override val sourceTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect = ProgressionStreakEffect.IGNORE,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.INSUFFICIENT_DATA
    }

    data class NotApplicable(
        override val sourceTarget: ProgressionTarget,
        override val reasonCode: ProgressionReasonCode = ProgressionReasonCode.MANUAL_SCHEME,
        override val reasonArguments: Map<String, Double> = emptyMap(),
        override val streakEffect: ProgressionStreakEffect = ProgressionStreakEffect.IGNORE,
        override val countedSetIds: List<Long> = emptyList()
    ) : ProgressionOutcome {
        override val type = ProgressionOutcomeType.NOT_APPLICABLE
    }
}

enum class ProgressionSuggestionStatus { PENDING, ACCEPTED, REJECTED, STALE, INFORMATIONAL }

data class ProgressionSuggestion(
    val id: Long,
    val sourceTarget: WorkoutPlanTarget,
    val outcome: ProgressionOutcome,
    val countedSets: List<WorkoutSet>,
    val status: ProgressionSuggestionStatus,
    val wasEdited: Boolean,
    val finalTarget: ProgressionTarget?,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long?
)

data class ProgressionGenerationResult(
    val insertedCount: Int,
    val reviewItemCount: Int,
    val pendingCount: Int
)

sealed interface ProgressionDecisionResult {
    data class Accepted(val suggestionIds: Set<Long>) : ProgressionDecisionResult
    data class Stale(val suggestionIds: Set<Long>) : ProgressionDecisionResult
    data class Invalid(val message: String) : ProgressionDecisionResult
}
```

- [ ] **Step 4: Extend existing plan and set models without breaking legacy constructors**

Append `progressionConfig` to `PlanExercise` and `planTargetSnapshotId` to `WorkoutSet`:

```kotlin
data class PlanExercise(
    val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String = "",
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0,
    val progressionConfig: ProgressionConfig = ProgressionConfig.Manual()
)

data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val isWarmup: Boolean = false,
    val completedAt: LocalDateTime = LocalDateTime.now(),
    val rpe: Double? = null,
    val planTargetSnapshotId: Long? = null
)
```

- [ ] **Step 5: Add the repository contract**

```kotlin
package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WorkoutPlanTarget
import kotlinx.coroutines.flow.Flow

interface ProgressionRepository {
    fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>>
    fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>>
    fun observePendingCount(): Flow<Int>
    suspend fun generateOutcomesForSession(sessionId: Long): ProgressionGenerationResult
    suspend fun generateMissingOutcomes(): Int
    suspend fun reconcileOutstandingSuggestions(): Set<Long>
    suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult
    suspend fun rejectSuggestion(suggestionId: Long)
}
```

- [ ] **Step 6: Expose `core:model` as part of the public common API and run the contract test**

Change `core/common/build.gradle.kts` from `implementation(project(":core:model"))` to:

```kotlin
api(project(":core:model"))
```

Run: `./gradlew --no-daemon :core:common:testDebugUnitTest --tests "com.ironlog.app.domain.progression.ProgressionContractTest"`

Expected: PASS, 2 tests executed.

- [ ] **Step 7: Commit the contract**

```bash
git add core/model/src/main/java/com/ironlog/app/domain/model/Progression.kt core/model/src/main/java/com/ironlog/app/domain/model/TrainingPlan.kt core/model/src/main/java/com/ironlog/app/domain/model/WorkoutSet.kt core/model/src/main/java/com/ironlog/app/domain/repository/ProgressionRepository.kt core/common/build.gradle.kts core/common/src/test/java/com/ironlog/app/domain/progression/ProgressionContractTest.kt
git commit -m "feat: define progression coach domain contract"
```

### Task 2: Implement revision-1 progression rules

**Files:**
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionRule.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionEngine.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionConfigValidator.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionRuleSupport.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/v1/LinearProgressionRuleV1.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/v1/DoubleProgressionRuleV1.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/v1/TotalRepsProgressionRuleV1.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/progression/v1/RpeProgressionRuleV1.kt`
- Create: `core/common/src/test/java/com/ironlog/app/domain/progression/ProgressionEngineTest.kt`

**Interfaces:**
- Consumes: Task 1 domain types and existing `WeightFormatting.convertToDisplay/convertToKg`.
- Produces: side-effect-free `ProgressionEngine.evaluate(context: ProgressionContext): ProgressionOutcome` for Task 5.

- [ ] **Step 1: Write black-box tests for all load-increasing paths**

Use one `context(...)` builder that creates a valid snapshot and snapshot-linked work sets, then add these exact tests:

```kotlin
@Test fun `linear success adds exactly configured step`() {
    val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 8)))
    val change = result as ProgressionOutcome.ProposeChange
    assertEquals(102.5, change.proposedTarget.weightKg, 0.000001)
    assertEquals(ProgressionReasonCode.LOAD_ADVANCED, change.reasonCode)
    assertEquals(ProgressionStreakEffect.RESET, change.streakEffect)
}

@Test fun `double progression advances repetitions before weight`() {
    val result = engine.evaluate(context(double(min = 8, max = 10), targetReps = 8, reps = listOf(8, 8, 8)))
    val change = result as ProgressionOutcome.ProposeChange
    assertEquals(9, change.proposedTarget.reps)
    assertEquals(100.0, change.proposedTarget.weightKg, 0.0)
}

@Test fun `double progression raises weight and resets repetitions at ceiling`() {
    val result = engine.evaluate(context(double(min = 8, max = 10), targetReps = 10, reps = listOf(10, 10, 10)))
    val change = result as ProgressionOutcome.ProposeChange
    assertEquals(8, change.proposedTarget.reps)
    assertEquals(102.5, change.proposedTarget.weightKg, 0.000001)
    assertEquals(ProgressionReasonCode.LOAD_ADVANCED, change.reasonCode)
}

@Test fun `total reps ignores distribution and extra sets`() {
    val first = engine.evaluate(context(totalReps(24), reps = listOf(8, 8, 8, 100)))
    val second = engine.evaluate(context(totalReps(24), reps = listOf(6, 7, 11, 1)))
    assertEquals(
        (first as ProgressionOutcome.ProposeChange).proposedTarget,
        (second as ProgressionOutcome.ProposeChange).proposedTarget
    )
}

@Test fun `rpe progression uses highest counted rpe`() {
    val result = engine.evaluate(context(rpe(target = 8.0, tolerance = 0.5), reps = listOf(8, 8, 8), rpes = listOf(7.5, 8.0, 8.5)))
    assertTrue(result is ProgressionOutcome.ProposeChange)
    assertEquals(ProgressionReasonCode.RPE_WITHIN_TARGET, result.reasonCode)
}
```

- [ ] **Step 2: Write black-box tests for every fail-closed and streak path**

```kotlin
@Test fun `warmups never satisfy missing planned sets`() {
    val result = engine.evaluate(context(linear(), reps = listOf(8, 8), warmupReps = listOf(20)))
    assertEquals(ProgressionReasonCode.TOO_FEW_WORK_SETS, result.reasonCode)
    assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
}

@Test fun `extra work sets never rescue a miss in the counted sets`() {
    val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 7), extraReps = listOf(20)))
    assertTrue(result is ProgressionOutcome.KeepTarget)
    assertEquals(ProgressionReasonCode.REPEAT_TARGET, result.reasonCode)
    assertEquals(listOf(1L, 2L, 3L), result.countedSetIds)
}

@Test fun `manual weight deviation is insufficient data`() {
    val result = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), weights = listOf(100.0, 100.02, 100.0)))
    assertEquals(ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION, result.reasonCode)
}

@Test fun `missing rpe ignores rather than resets failure streak`() {
    val result = engine.evaluate(context(rpe(8.0, 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, null, 8.0)))
    assertEquals(ProgressionReasonCode.RPE_MISSING, result.reasonCode)
    assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
}

@Test fun `high rpe after completed work resets failure streak without increasing`() {
    val result = engine.evaluate(context(rpe(8.0, 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, 9.0, 8.0)))
    assertTrue(result is ProgressionOutcome.KeepTarget)
    assertEquals(ProgressionStreakEffect.RESET, result.streakEffect)
}

@Test fun `only comparable increment outcomes reach stall threshold`() {
    val previous = listOf(
        previous(ProgressionStreakEffect.IGNORE),
        previous(ProgressionStreakEffect.INCREMENT)
    )
    val result = engine.evaluate(context(linear(stallThreshold = 2), reps = listOf(8, 8, 7), previous = previous))
    assertEquals(ProgressionReasonCode.STALL_BACKOFF, result.reasonCode)
}

@Test fun `a reset outcome stops the older failure streak`() {
    val previous = listOf(
        previous(ProgressionStreakEffect.RESET),
        previous(ProgressionStreakEffect.INCREMENT)
    )
    val result = engine.evaluate(context(linear(stallThreshold = 2), reps = listOf(8, 8, 7), previous = previous))
    assertTrue(result is ProgressionOutcome.KeepTarget)
    assertEquals(ProgressionReasonCode.REPEAT_TARGET, result.reasonCode)
}

@Test fun `imperial backoff rounds ties downward and remains below current weight`() {
    val result = engine.evaluate(context(linearImperial(stepLb = 5.0, backoff = 10.0), targetWeightKg = WeightFormatting.convertToKg(100.0, UnitSystem.IMPERIAL), reps = listOf(8, 8, 7), previous = listOf(previous(ProgressionStreakEffect.INCREMENT))))
    val change = result as ProgressionOutcome.ProposeChange
    assertEquals(90.0, WeightFormatting.convertToDisplay(change.proposedTarget.weightKg, UnitSystem.IMPERIAL), 0.000001)
}

@Test fun `backoff at zero keeps target instead of proposing an identical change`() {
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

@Test fun `unsupported revision never falls through to revision one`() {
    val result = engine.evaluate(context(linear(ruleRevision = 99), reps = listOf(8, 8, 8)))
    assertEquals(ProgressionReasonCode.RULE_REVISION_UNSUPPORTED, result.reasonCode)
    assertTrue(result is ProgressionOutcome.InsufficientData)
}

@Test fun `invalid configurations fail closed without counting a failure`() {
    val invalidContexts = listOf(
        context(linear(stepKg = Double.NaN()), reps = listOf(8, 8, 8)),
        context(double(min = 10, max = 8), reps = listOf(8, 8, 8)),
        context(totalReps(0), reps = listOf(8, 8, 8)),
        context(rpe(target = 11.0, tolerance = 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, 8.0, 8.0)),
        context(rpe(target = 8.0, tolerance = 3.0), reps = listOf(8, 8, 8), rpes = listOf(8.0, 8.0, 8.0)),
        context(linear(stallThreshold = 0), reps = listOf(8, 8, 8)),
        context(linear(backoff = 31.0), reps = listOf(8, 8, 8)),
        context(linear(stepKg = Double.MIN_VALUE), targetWeightKg = 100.0, reps = listOf(8, 8, 8)),
        context(linear(), targetWeightKg = Double.MAX_VALUE, weights = listOf(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), reps = listOf(8, 8, 8))
    )

    invalidContexts.forEach { invalid ->
        val result = engine.evaluate(invalid)
        assertTrue(result is ProgressionOutcome.InsufficientData)
        assertEquals(ProgressionReasonCode.CONFIG_INVALID, result.reasonCode)
        assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
    }
}

@Test fun `counted set ids are deterministic and exclude warmups and extras`() {
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

@Test fun `invalid set numbers preserve available evidence but do not count a failure`() {
    val duplicate = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), workSetNumbers = listOf(1, 1, 2)))
    val nonPositive = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), workSetNumbers = listOf(0, 1, 2)))

    listOf(duplicate, nonPositive).forEach { result ->
        assertTrue(result is ProgressionOutcome.InsufficientData)
        assertEquals(ProgressionReasonCode.SET_NUMBER_INVALID, result.reasonCode)
        assertEquals(ProgressionStreakEffect.IGNORE, result.streakEffect)
        assertEquals(listOf(1L, 2L, 3L), result.countedSetIds.sorted())
    }
}


@Test fun `invalid actual set values fail closed without advancing failure streak`() {
    val invalidReps = engine.evaluate(context(linear(), reps = listOf(8, -1, 8)))
    val invalidWeight = engine.evaluate(context(linear(), reps = listOf(8, 8, 8), weights = listOf(100.0, Double.NaN, 100.0)))
    val invalidRpe = engine.evaluate(context(rpe(8.0, 0.5), reps = listOf(8, 8, 8), rpes = listOf(8.0, 0.0, 8.0)))

    assertEquals(ProgressionReasonCode.SET_VALUE_INVALID, invalidReps.reasonCode)
    assertEquals(ProgressionReasonCode.SET_VALUE_INVALID, invalidWeight.reasonCode)
    assertEquals(ProgressionReasonCode.RPE_INVALID, invalidRpe.reasonCode)
    listOf(invalidReps, invalidWeight, invalidRpe).forEach {
        assertTrue(it is ProgressionOutcome.InsufficientData)
        assertEquals(ProgressionStreakEffect.IGNORE, it.streakEffect)
        assertTrue(it.reasonArguments.values.all { value -> value.isFinite() })
    }
}

@Test fun `invalid values in extra work sets do not change the counted outcome`() {
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
```

The shared builders must expose the named arguments used above and assign stable IDs beginning at `1` unless explicit IDs are supplied. Add one case each for negative infinity and positive infinity alongside `NaN`, and boundary-pass assertions for RPE `1.0/10.0`, tolerance `0.0/2.0`, stall threshold `1/6`, and backoff `1.0/30.0`.

- [ ] **Step 3: Run the engine test and observe missing implementation failures**

Run: `./gradlew --no-daemon :core:common:testDebugUnitTest --tests "com.ironlog.app.domain.progression.ProgressionEngineTest"`

Expected: FAIL because `ProgressionEngine` and the rule implementations do not exist.

- [ ] **Step 4: Implement revision dispatch and the rule interface**

```kotlin
internal data class ProgressionRuleKey(val scheme: ProgressionScheme, val revision: Int)

internal fun interface ProgressionRule {
    fun evaluate(context: ProgressionContext): ProgressionOutcome
}

class ProgressionEngine private constructor(
    private val registry: Map<ProgressionRuleKey, ProgressionRule>
) {
    constructor() : this(listOf(
        ProgressionRuleKey(ProgressionScheme.LINEAR, 1) to LinearProgressionRuleV1,
        ProgressionRuleKey(ProgressionScheme.DOUBLE, 1) to DoubleProgressionRuleV1,
        ProgressionRuleKey(ProgressionScheme.TOTAL_REPS, 1) to TotalRepsProgressionRuleV1,
        ProgressionRuleKey(ProgressionScheme.RPE_RIR, 1) to RpeProgressionRuleV1
    ).toMap())

    internal constructor(rules: List<Pair<ProgressionRuleKey, ProgressionRule>>) : this(rules.toMap())

    fun evaluate(context: ProgressionContext): ProgressionOutcome {
        val config = context.sourceTarget.config
        val availableEvidenceIds = context.setsForTarget
            .filterNot(WorkoutSet::isWarmup)
            .sortedWith(compareBy(WorkoutSet::setNumber, WorkoutSet::completedAt, WorkoutSet::id))
            .map(WorkoutSet::id)
            .filter { it > 0 }
            .distinct()
        if (config is ProgressionConfig.Invalid) {
            return ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.CONFIG_INVALID,
                countedSetIds = availableEvidenceIds
            )
        }
        if (config.scheme == ProgressionScheme.MANUAL) {
            return ProgressionOutcome.NotApplicable(context.sourceTarget.target)
        }
        val rule = registry[ProgressionRuleKey(config.scheme, config.ruleRevision)]
            ?: return ProgressionOutcome.InsufficientData(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.RULE_REVISION_UNSUPPORTED,
                countedSetIds = availableEvidenceIds
            )
        return rule.evaluate(context)
    }
}
```

- [ ] **Step 5: Implement shared validation, counted-set selection, streak counting, and rounding**

`ProgressionRuleSupport` must expose these exact types and functions:

```kotlin
internal const val WEIGHT_TOLERANCE_KG = 0.01

internal sealed interface CountedWorkSetsResult {
    data class Valid(val sets: List<WorkoutSet>) : CountedWorkSetsResult
    data class Invalid(
        val reasonCode: ProgressionReasonCode,
        val availableSetIds: List<Long>,
        val reasonArguments: Map<String, Double> = emptyMap()
    ) : CountedWorkSetsResult
}

internal fun countedWorkSets(context: ProgressionContext): CountedWorkSetsResult
internal fun priorConsecutiveFailures(context: ProgressionContext): Int
internal fun increasedWeight(targetKg: Double, step: WeightStep): Double
internal fun backedOffWeight(targetKg: Double, step: WeightStep, backoffPercent: Double): Double
```

`countedWorkSets` must check that every supplied set has a positive unique database ID and exactly matches the source target's session, exercise, and snapshot IDs; any identity violation returns `SET_VALUE_INVALID/IGNORE`. It then filters warm-ups, sorts all work sets by `setNumber`, `completedAt`, and `id`, rejects non-positive or duplicate work-set numbers, returns `TOO_FEW_WORK_SETS` before any success/failure rule, and takes exactly `target.sets`. Only on those counted sets, reject negative repetitions and non-finite or negative weights as `SET_VALUE_INVALID`; never put a non-finite number in `reasonArguments`. This keeps extra-set performance values irrelevant while still requiring trustworthy identity and ordering for the full candidate sequence. A valid result's IDs are the exact evaluated evidence. An invalid result carries the unique positive available work-set IDs in deterministic order so the review can show why evaluation failed without persisting unusable links. `priorConsecutiveFailures` scans `previousComparableOutcomesNewestFirst`, increments on `INCREMENT`, skips `IGNORE`, and stops at the first `RESET`.

`ProgressionConfigValidator` must validate the common target first, then the selected config. In addition to the documented ranges, require `WeightStep.originalValue > 0`, `WeightStep.kilograms > 0`, both finite, and `abs(WeightFormatting.convertToKg(originalValue, originalUnit) - kilograms) <= 0.000001`; mismatched entered/canonical steps are invalid storage, not a new interpretation. For every active scheme also require the target converted to the step unit and `target.weightKg + step.kilograms` to be finite, with the sum strictly greater than the source weight. This rejects overflow and sub-ULP steps before they can create an infinite or no-op suggestion.

Implement backoff rounding with the existing conversion utility:

```kotlin
val currentDisplay = WeightFormatting.convertToDisplay(targetKg, step.originalUnit)
val rawDisplay = currentDisplay * (1.0 - backoffPercent / 100.0)
val lower = kotlin.math.floor(rawDisplay / step.originalValue) * step.originalValue
val upper = kotlin.math.ceil(rawDisplay / step.originalValue) * step.originalValue
val rounded = if (rawDisplay - lower <= upper - rawDisplay) lower else upper
val decreasing = if (rounded < currentDisplay) rounded else currentDisplay - step.originalValue
return WeightFormatting.convertToKg(decreasing.coerceAtLeast(0.0), step.originalUnit)
```

- [ ] **Step 6: Implement the four V1 strategies**

Each rule must first run shared config and set validation, then apply these exact V1 semantics:

1. Compare every counted work-set weight to `target.weightKg` within `WEIGHT_TOLERANCE_KG` before looking at repetitions or RPE. Any deviation returns `INSUFFICIENT_DATA/MANUAL_WEIGHT_DEVIATION` with `IGNORE`.
2. Linear succeeds only when every counted set has `reps >= target.reps`; double progression uses the same condition at the current target reps; total-reps succeeds when an overflow-safe `Long` sum is at least `targetTotalReps`. Compute that sum with checked addition; an overflow returns `INSUFFICIENT_DATA/SET_VALUE_INVALID/IGNORE` rather than throwing or wrapping.
3. RPE/RIR first requires the linear repetition condition, then requires every counted RPE. Missing values return `RPE_MISSING/IGNORE`; non-finite or out-of-range values return `RPE_INVALID/IGNORE`; the highest valid RPE decides the tolerance rule.
4. A valid repetition or total-reps miss computes `failuresIncludingCurrent = priorConsecutiveFailures(context) + 1`. Below the threshold it returns `KEEP_TARGET/REPEAT_TARGET/INCREMENT`; at or above the threshold it calculates the backoff. If the rounded result is strictly below the source weight, return the proposal with `INCREMENT`; if the source is already zero and no decrease is possible, return `KEEP_TARGET/BACKOFF_FLOOR_REACHED/INCREMENT` and never create a no-op proposal.
5. A success or completed repetitions with excessive RPE returns `RESET`. Extra work sets are excluded before performance-value validation and every rule calculation; only their identity and work-set numbering participate in the shared ordering preflight.

The resulting constructors are:

```kotlin
// Linear miss before threshold
ProgressionOutcome.KeepTarget(target, ProgressionReasonCode.REPEAT_TARGET, streakEffect = ProgressionStreakEffect.INCREMENT)

// Linear/total/RPE successful load increase
ProgressionOutcome.ProposeChange(target, target.copy(weightKg = increasedWeight(target.weightKg, config.step)), reasonCode, reasonArguments, ProgressionStreakEffect.RESET)

// Double below max
ProgressionOutcome.ProposeChange(target, target.copy(reps = target.reps + 1), ProgressionReasonCode.REP_TARGET_ADVANCED, emptyMap(), ProgressionStreakEffect.RESET)

// Double at max
ProgressionOutcome.ProposeChange(target, target.copy(reps = config.minReps, weightKg = increasedWeight(target.weightKg, config.step)), ProgressionReasonCode.LOAD_ADVANCED, reasonArguments, ProgressionStreakEffect.RESET)

// Any miss at threshold
ProgressionOutcome.ProposeChange(target, target.copy(weightKg = backedOffWeight(target.weightKg, config.step, config.failurePolicy.backoffPercent)), ProgressionReasonCode.STALL_BACKOFF, mapOf("backoffPercent" to config.failurePolicy.backoffPercent), ProgressionStreakEffect.INCREMENT)

// Completed repetitions but excessive RPE
ProgressionOutcome.KeepTarget(target, ProgressionReasonCode.REPEAT_TARGET, mapOf("highestRpe" to highestRpe), ProgressionStreakEffect.RESET)
```

Pass `countedSetIds = countedSets.map(WorkoutSet::id)` to every success, miss, and backoff outcome. For invalid data, pass `CountedWorkSetsResult.Invalid.availableSetIds`; unsupported revisions use all unique positive non-warm-up context-set IDs sorted by `setNumber`, `completedAt`, and `id`. Persist these exact numeric explanation keys when relevant: `expectedWeightKg`, `actualWeightKg`, `targetSets`, `actualWorkSets`, `targetReps`, `actualReps`, `achievedTotalReps`, `targetTotalReps`, `highestRpe`, `targetRpe`, `tolerance`, `stepOriginalValue`, and `backoffPercent`. `actualReps` means the minimum repetitions among the counted sets, and `actualWeightKg` means the first deviating weight in counted-set order, so explanations are deterministic. The review derives the step unit from the stored config, so no localized or string-valued argument enters the engine.

- [ ] **Step 7: Run the engine test**

Run: `./gradlew --no-daemon :core:common:testDebugUnitTest --tests "com.ironlog.app.domain.progression.ProgressionEngineTest"`

Expected: PASS with every named success, fail-closed, rounding, and streak test executed.

- [ ] **Step 8: Commit the pure engine**

```bash
git add core/common/src/main/java/com/ironlog/app/domain/progression core/common/src/test/java/com/ironlog/app/domain/progression/ProgressionEngineTest.kt
git commit -m "feat: implement deterministic progression rules"
```

### Task 3: Add Room schema 11 and migration 10 to 11

**Files:**
- Create: `core/database/src/main/java/com/ironlog/app/data/local/entity/ProgressionStorageColumns.kt`
- Create: `core/database/src/main/java/com/ironlog/app/data/local/entity/WorkoutPlanTargetEntity.kt`
- Create: `core/database/src/main/java/com/ironlog/app/data/local/entity/ProgressionSuggestionEntity.kt`
- Create: `core/database/src/main/java/com/ironlog/app/data/local/dao/ProgressionDao.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/entity/PlanExerciseEntity.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/entity/WorkoutSetEntity.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/dao/TrainingPlanDao.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSetDao.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/IronLogDatabase.kt`
- Create (generated by Room): `core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/11.json`
- Modify: `app/src/androidTest/java/com/ironlog/app/data/local/IronLogDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: Task 1 model types and Room's existing `TransactionRunner` integration.
- Produces: schema-11 entities, `ProgressionDao`, `TrainingPlanDao.getPlanExerciseAt(...)`, and `updatePlanExerciseTargetsById(...)` for Tasks 4–6 and 11.

- [ ] **Step 1: Write the failing migration test**

Add a raw version-10 fixture containing one plan, two duplicate exercise positions, one session, and one set, then open it with `IronLogDatabase.migration10To11ForTests()` and assert:

```kotlin
@Test
fun migration10To11_preservesRowsAndCreatesManualProgressionSchema() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dbName = "ironlog-migration-10-11-test.db"
    context.deleteDatabase(dbName)
    createLegacyV10Helper(context, dbName).use { helper ->
        helper.writableDatabase.execSQL("INSERT INTO exercises (id, name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom, notes, isArchived) VALUES (1, 'Squat', 'BEINE', '', 'LANGHANTEL', 0, '', 0)")
        helper.writableDatabase.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES (1, 'Lower', 1000)")
        helper.writableDatabase.execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, targetSets, targetReps, targetWeightKg, supersetGroupId) VALUES (1, 1, 1, 0, 3, 8, 100.0, NULL)")
        helper.writableDatabase.execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, targetSets, targetReps, targetWeightKg, supersetGroupId) VALUES (2, 1, 1, 1, 2, 12, 80.0, NULL)")
        helper.writableDatabase.execSQL("INSERT INTO workout_sessions (id, startTime, endTime, durationSeconds, name, notes, planId, metaPlanId) VALUES (1, 1000, 2000, 1, 'Lower', '', 1, NULL)")
        helper.writableDatabase.execSQL("INSERT INTO workout_sets (id, sessionId, exerciseId, setNumber, reps, weightKg, isWarmup, completedAt, rpe) VALUES (1, 1, 1, 1, 8, 100.0, 0, 1500, 8.0)")
    }

    val database = Room.databaseBuilder(context, IronLogDatabase::class.java, dbName)
        .addMigrations(IronLogDatabase.migration10To11ForTests())
        .build()
    runBlocking { database.exerciseDao().getCount() }
    database.close()

    openRawV11Connection(context, dbName).use { helper ->
        val db = helper.writableDatabase
        assertEquals("MANUAL", queryString(db, "SELECT progressionScheme FROM plan_exercises WHERE id = 1"))
        assertEquals("MANUAL", queryString(db, "SELECT progressionScheme FROM plan_exercises WHERE id = 2"))
        assertEquals(3, queryInt(db, "SELECT targetSets FROM plan_exercises WHERE id = 1"))
        assertEquals(8, queryInt(db, "SELECT targetReps FROM plan_exercises WHERE id = 1"))
        assertEquals(100.0, queryDouble(db, "SELECT targetWeightKg FROM plan_exercises WHERE id = 1"), 0.0)
        assertEquals(80.0, queryDouble(db, "SELECT targetWeightKg FROM plan_exercises WHERE id = 2"), 0.0)
        assertTrue(hasColumn(db, "workout_sets", "planTargetSnapshotId"))
        assertTrue(tableExists(db, "workout_plan_targets"))
        assertTrue(tableExists(db, "progression_suggestions"))
        assertTrue(hasIndex(db, "workout_plan_targets", "index_workout_plan_targets_sessionId_orderIndex"))
        assertTrue(hasIndex(db, "workout_plan_targets", "index_workout_plan_targets_planId_exerciseId_orderIndex"))
        assertTrue(hasIndex(db, "progression_suggestions", "index_progression_suggestions_sourceTargetSnapshotId_sourceProgressionRuleRevision"))
        assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM workout_plan_targets"))
        assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM progression_suggestions"))
        assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM workout_sets WHERE planTargetSnapshotId IS NOT NULL"))
        db.execSQL("PRAGMA foreign_keys = ON")
        db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }
    context.deleteDatabase(dbName)
}
```

`createLegacyV10Helper` must create the exact version-10 schema already represented by `core/database/schemas/.../10.json`; factor the existing V9 fixture so V10 additionally creates `meta_plan_skips` and its three indices. `openRawV11Connection` uses an `OpenHelper.Callback(11)` and performs no schema mutation.

- [ ] **Step 2: Compile the instrumentation test and observe the missing migration accessor**

Run: `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin`

Expected: FAIL because `migration10To11ForTests()` and schema-11 entities do not exist.

- [ ] **Step 3: Add reusable flat storage columns and lossless mappers**

```kotlin
data class ProgressionTargetColumns(
    @ColumnInfo(name = "Sets") val sets: Int,
    @ColumnInfo(name = "Reps") val reps: Int,
    @ColumnInfo(name = "WeightKg") val weightKg: Double
) {
    fun toDomain() = ProgressionTarget(sets, reps, weightKg)
    companion object {
        fun fromDomain(value: ProgressionTarget) = ProgressionTargetColumns(value.sets, value.reps, value.weightKg)
    }
}

data class ProgressionConfigColumns(
    @ColumnInfo(name = "Scheme", defaultValue = "'MANUAL'") val scheme: String = ProgressionScheme.MANUAL.name,
    @ColumnInfo(name = "IncrementValue") val incrementValue: Double? = null,
    @ColumnInfo(name = "IncrementUnit") val incrementUnit: String? = null,
    @ColumnInfo(name = "IncrementKg") val incrementKg: Double? = null,
    @ColumnInfo(name = "MinReps") val minReps: Int? = null,
    @ColumnInfo(name = "MaxReps") val maxReps: Int? = null,
    @ColumnInfo(name = "TargetTotalReps") val targetTotalReps: Long? = null,
    @ColumnInfo(name = "TargetRpe") val targetRpe: Double? = null,
    @ColumnInfo(name = "RpeTolerance") val rpeTolerance: Double? = null,
    @ColumnInfo(name = "StallThreshold", defaultValue = "2") val stallThreshold: Int = 2,
    @ColumnInfo(name = "BackoffPercent", defaultValue = "10.0") val backoffPercent: Double = 10.0,
    @ColumnInfo(name = "RuleRevision", defaultValue = "1") val ruleRevision: Int = 1
)
```

The leading-capital `@ColumnInfo` names are intentional: Room concatenates an embedded prefix verbatim. They make prefixes such as `progression`, `sourceProgression`, and `suggested` generate the locked column names `progressionScheme`, `sourceProgressionRuleRevision`, and `suggestedWeightKg` instead of lower-cased concatenations such as `progressionscheme`.

`toDomain()` must use `enumValueOf` through `runCatching`, return `ProgressionConfig.Invalid` for an unknown scheme or any missing required or non-null unused scheme-specific field, and otherwise preserve raw numeric values for the engine validator. For an unknown string, use `scheme = MANUAL` only as the typed fallback and preserve the actual database value in `rawScheme`; the engine checks `Invalid` before the manual branch, so corruption never becomes a silent opt-out. `fromDomain()` writes every valid config variant, including all three weight-step fields, but rejects `ProgressionConfig.Invalid` because that compact marker cannot losslessly represent every malformed nullable database column. Snapshot, suggestion, history, and backup paths copy or compare the original `ProgressionConfigColumns` directly; they must never round-trip corrupt storage through the domain marker.

- [ ] **Step 4: Define the two new entities and extend the existing entities**

Use these exact entity shapes:

```kotlin
@Entity(
    tableName = "workout_plan_targets",
    foreignKeys = [
        ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index("sessionId"), Index("planId"), Index("exerciseId"),
        Index(value = ["planId", "exerciseId", "orderIndex"]),
        Index(value = ["sessionId", "orderIndex"], unique = true)
    ]
)
data class WorkoutPlanTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int?,
    @Embedded(prefix = "target") val target: ProgressionTargetColumns,
    @Embedded(prefix = "progression") val progression: ProgressionConfigColumns
)

@Entity(
    tableName = "progression_suggestions",
    foreignKeys = [
        ForeignKey(entity = WorkoutPlanTargetEntity::class, parentColumns = ["id"], childColumns = ["sourceTargetSnapshotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sourceSessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index("sourceSessionId"), Index("planId"), Index("exerciseId"),
        Index(value = ["sourceTargetSnapshotId", "sourceProgressionRuleRevision"], unique = true),
        Index("status")
    ]
)
data class ProgressionSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceSessionId: Long,
    val sourceTargetSnapshotId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int?,
    @Embedded(prefix = "source") val sourceTarget: ProgressionTargetColumns,
    @Embedded(prefix = "sourceProgression") val sourceProgression: ProgressionConfigColumns,
    val outcomeType: String,
    val reasonCode: String,
    val reasonArgumentsJson: String,
    val countedSetIdsJson: String,
    val streakEffect: String,
    @Embedded(prefix = "suggested") val suggestedTarget: ProgressionTargetColumns?,
    val status: String,
    val wasEdited: Boolean,
    @Embedded(prefix = "final") val finalTarget: ProgressionTargetColumns?,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long?
)
```

Add `@Embedded(prefix = "progression") val progression: ProgressionConfigColumns = ProgressionConfigColumns()` to `PlanExerciseEntity` and map it to/from `PlanExercise.progressionConfig`. Add `planTargetSnapshotId: Long? = null`, its index, and a `WorkoutPlanTargetEntity` foreign key with `onDelete = SET_NULL` to `WorkoutSetEntity`; map it to/from `WorkoutSet.planTargetSnapshotId`.

- [ ] **Step 5: Add DAO operations with stable ordering and direct plan-row updates**

```kotlin
@Dao
interface ProgressionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTargets(targets: List<WorkoutPlanTargetEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSuggestion(suggestion: ProgressionSuggestionEntity): Long

    @Update suspend fun updateSuggestion(suggestion: ProgressionSuggestionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceAllTargets(values: List<WorkoutPlanTargetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceAllSuggestions(values: List<ProgressionSuggestionEntity>)

    @Query("SELECT * FROM workout_plan_targets WHERE sessionId = :sessionId ORDER BY orderIndex, id")
    fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTargetEntity>>

    @Query("SELECT * FROM workout_plan_targets WHERE sessionId = :sessionId ORDER BY orderIndex, id")
    suspend fun getTargetsForSession(sessionId: Long): List<WorkoutPlanTargetEntity>

    @Query("SELECT * FROM workout_plan_targets WHERE id = :id LIMIT 1")
    suspend fun getTargetById(id: Long): WorkoutPlanTargetEntity?

    @Query("SELECT * FROM progression_suggestions WHERE sourceSessionId = :sessionId ORDER BY orderIndex, id")
    fun observeSuggestionsForSession(sessionId: Long): Flow<List<ProgressionSuggestionEntity>>

    @Query("SELECT p.* FROM progression_suggestions p JOIN workout_sessions s ON s.id = p.sourceSessionId WHERE p.status = 'PENDING' ORDER BY s.endTime DESC, s.id DESC, p.orderIndex, p.id")
    fun observePendingSuggestions(): Flow<List<ProgressionSuggestionEntity>>

    @Query("SELECT COUNT(*) FROM progression_suggestions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM progression_suggestions WHERE id IN (:ids) ORDER BY id")
    suspend fun getSuggestionsByIds(ids: Set<Long>): List<ProgressionSuggestionEntity>

    @Query("SELECT * FROM progression_suggestions WHERE status = 'PENDING' ORDER BY id")
    suspend fun getPendingSuggestions(): List<ProgressionSuggestionEntity>

    @Query("""
        SELECT t.* FROM workout_plan_targets t
        JOIN workout_sessions s ON s.id = t.sessionId
        WHERE t.planId = :planId
          AND t.exerciseId = :exerciseId
          AND t.orderIndex = :orderIndex
          AND s.endTime IS NOT NULL
          AND (s.endTime < :sourceEndTime OR (s.endTime = :sourceEndTime AND s.id < :sourceSessionId))
        ORDER BY s.endTime DESC, s.id DESC, t.id DESC
    """)
    suspend fun getPreviousTargets(
        planId: Long,
        exerciseId: Long,
        orderIndex: Int,
        sourceEndTime: Long,
        sourceSessionId: Long
    ): List<WorkoutPlanTargetEntity>

    @Query("SELECT * FROM progression_suggestions WHERE sourceTargetSnapshotId IN (:targetIds) ORDER BY sourceTargetSnapshotId, id")
    suspend fun getSuggestionsForTargetIds(targetIds: List<Long>): List<ProgressionSuggestionEntity>

    @Query("""
        SELECT DISTINCT t.sessionId FROM workout_plan_targets t
        JOIN workout_sessions s ON s.id = t.sessionId
        WHERE s.endTime IS NOT NULL
          AND NOT (
              t.progressionScheme = 'MANUAL'
              AND t.progressionIncrementValue IS NULL
              AND t.progressionIncrementUnit IS NULL
              AND t.progressionIncrementKg IS NULL
              AND t.progressionMinReps IS NULL
              AND t.progressionMaxReps IS NULL
              AND t.progressionTargetTotalReps IS NULL
              AND t.progressionTargetRpe IS NULL
              AND t.progressionRpeTolerance IS NULL
          )
          AND NOT EXISTS (
              SELECT 1 FROM progression_suggestions p
              WHERE p.sourceTargetSnapshotId = t.id
                AND p.sourceProgressionRuleRevision = t.progressionRuleRevision
          )
        ORDER BY s.endTime, s.id
    """)
    suspend fun getCompletedSessionIdsWithMissingOutcomes(): List<Long>

    @Query("""
        SELECT DISTINCT t.sessionId FROM workout_plan_targets t
        JOIN workout_sessions s ON s.id = t.sessionId
        WHERE s.endTime IS NOT NULL
          AND (s.endTime < :sourceEndTime OR (s.endTime = :sourceEndTime AND s.id < :sourceSessionId))
          AND NOT (
              t.progressionScheme = 'MANUAL'
              AND t.progressionIncrementValue IS NULL
              AND t.progressionIncrementUnit IS NULL
              AND t.progressionIncrementKg IS NULL
              AND t.progressionMinReps IS NULL
              AND t.progressionMaxReps IS NULL
              AND t.progressionTargetTotalReps IS NULL
              AND t.progressionTargetRpe IS NULL
              AND t.progressionRpeTolerance IS NULL
          )
          AND NOT EXISTS (
              SELECT 1 FROM progression_suggestions p
              WHERE p.sourceTargetSnapshotId = t.id
                AND p.sourceProgressionRuleRevision = t.progressionRuleRevision
          )
        ORDER BY s.endTime, s.id
    """)
    suspend fun getCompletedSessionIdsWithMissingOutcomesBefore(sourceEndTime: Long, sourceSessionId: Long): List<Long>

    @Query("SELECT * FROM workout_plan_targets ORDER BY id") suspend fun getAllTargets(): List<WorkoutPlanTargetEntity>
    @Query("SELECT * FROM progression_suggestions ORDER BY id") suspend fun getAllSuggestions(): List<ProgressionSuggestionEntity>
    @Query("DELETE FROM progression_suggestions") suspend fun deleteAllSuggestions()
    @Query("DELETE FROM workout_plan_targets") suspend fun deleteAllTargets()
}
```

Add `WorkoutSetDao.getSetsByIds(ids: List<Long>): List<WorkoutSetEntity>` using `SELECT * FROM workout_sets WHERE id IN (:ids) ORDER BY setNumber, completedAt, id`; callers must bypass the query for an empty ID list.

Add to `TrainingPlanDao`:

```kotlin
@Query("SELECT * FROM plan_exercises WHERE planId = :planId AND exerciseId = :exerciseId AND orderIndex = :orderIndex LIMIT 1")
suspend fun getPlanExerciseAt(planId: Long, exerciseId: Long, orderIndex: Int): PlanExerciseEntity?

@Query("UPDATE plan_exercises SET targetSets = :sets, targetReps = :reps, targetWeightKg = :weightKg WHERE id = :id")
suspend fun updatePlanExerciseTargetsById(id: Long, sets: Int, reps: Int, weightKg: Double): Int
```

- [ ] **Step 6: Implement migration 10 to 11**

Set `@Database(version = 11)`, register both entities and `progressionDao()`, append `MIGRATION_10_11` after `MIGRATION_9_10`, and expose `migration10To11ForTests()`.

Add these columns in this order so migrated and freshly created schemas match:

```kotlin
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionScheme` TEXT NOT NULL DEFAULT 'MANUAL'")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionIncrementValue` REAL")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionIncrementUnit` TEXT")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionIncrementKg` REAL")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionMinReps` INTEGER")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionMaxReps` INTEGER")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionTargetTotalReps` INTEGER")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionTargetRpe` REAL")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionRpeTolerance` REAL")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionStallThreshold` INTEGER NOT NULL DEFAULT 2")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionBackoffPercent` REAL NOT NULL DEFAULT 10.0")
db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionRuleRevision` INTEGER NOT NULL DEFAULT 1")
```

Create `workout_plan_targets` before rebuilding `workout_sets`, then create `progression_suggestions` with these exact statements:

```kotlin
db.execSQL("""
    CREATE TABLE `workout_plan_targets` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `sessionId` INTEGER NOT NULL,
        `planId` INTEGER NOT NULL,
        `exerciseId` INTEGER NOT NULL,
        `orderIndex` INTEGER NOT NULL,
        `supersetGroupId` INTEGER,
        `targetSets` INTEGER NOT NULL,
        `targetReps` INTEGER NOT NULL,
        `targetWeightKg` REAL NOT NULL,
        `progressionScheme` TEXT NOT NULL DEFAULT 'MANUAL',
        `progressionIncrementValue` REAL,
        `progressionIncrementUnit` TEXT,
        `progressionIncrementKg` REAL,
        `progressionMinReps` INTEGER,
        `progressionMaxReps` INTEGER,
        `progressionTargetTotalReps` INTEGER,
        `progressionTargetRpe` REAL,
        `progressionRpeTolerance` REAL,
        `progressionStallThreshold` INTEGER NOT NULL DEFAULT 2,
        `progressionBackoffPercent` REAL NOT NULL DEFAULT 10.0,
        `progressionRuleRevision` INTEGER NOT NULL DEFAULT 1,
        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
    )
""".trimIndent())
db.execSQL("CREATE INDEX `index_workout_plan_targets_sessionId` ON `workout_plan_targets` (`sessionId`)")
db.execSQL("CREATE INDEX `index_workout_plan_targets_planId` ON `workout_plan_targets` (`planId`)")
db.execSQL("CREATE INDEX `index_workout_plan_targets_exerciseId` ON `workout_plan_targets` (`exerciseId`)")
db.execSQL("CREATE INDEX `index_workout_plan_targets_planId_exerciseId_orderIndex` ON `workout_plan_targets` (`planId`, `exerciseId`, `orderIndex`)")
db.execSQL("CREATE UNIQUE INDEX `index_workout_plan_targets_sessionId_orderIndex` ON `workout_plan_targets` (`sessionId`, `orderIndex`)")

db.execSQL("""
    CREATE TABLE `progression_suggestions` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `sourceSessionId` INTEGER NOT NULL,
        `sourceTargetSnapshotId` INTEGER NOT NULL,
        `planId` INTEGER NOT NULL,
        `exerciseId` INTEGER NOT NULL,
        `orderIndex` INTEGER NOT NULL,
        `supersetGroupId` INTEGER,
        `sourceSets` INTEGER NOT NULL,
        `sourceReps` INTEGER NOT NULL,
        `sourceWeightKg` REAL NOT NULL,
        `sourceProgressionScheme` TEXT NOT NULL DEFAULT 'MANUAL',
        `sourceProgressionIncrementValue` REAL,
        `sourceProgressionIncrementUnit` TEXT,
        `sourceProgressionIncrementKg` REAL,
        `sourceProgressionMinReps` INTEGER,
        `sourceProgressionMaxReps` INTEGER,
        `sourceProgressionTargetTotalReps` INTEGER,
        `sourceProgressionTargetRpe` REAL,
        `sourceProgressionRpeTolerance` REAL,
        `sourceProgressionStallThreshold` INTEGER NOT NULL DEFAULT 2,
        `sourceProgressionBackoffPercent` REAL NOT NULL DEFAULT 10.0,
        `sourceProgressionRuleRevision` INTEGER NOT NULL DEFAULT 1,
        `outcomeType` TEXT NOT NULL,
        `reasonCode` TEXT NOT NULL,
        `reasonArgumentsJson` TEXT NOT NULL,
        `countedSetIdsJson` TEXT NOT NULL,
        `streakEffect` TEXT NOT NULL,
        `suggestedSets` INTEGER,
        `suggestedReps` INTEGER,
        `suggestedWeightKg` REAL,
        `status` TEXT NOT NULL,
        `wasEdited` INTEGER NOT NULL,
        `finalSets` INTEGER,
        `finalReps` INTEGER,
        `finalWeightKg` REAL,
        `createdAtEpochMillis` INTEGER NOT NULL,
        `decidedAtEpochMillis` INTEGER,
        FOREIGN KEY(`sourceTargetSnapshotId`) REFERENCES `workout_plan_targets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`sourceSessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
    )
""".trimIndent())
db.execSQL("CREATE INDEX `index_progression_suggestions_sourceSessionId` ON `progression_suggestions` (`sourceSessionId`)")
db.execSQL("CREATE INDEX `index_progression_suggestions_planId` ON `progression_suggestions` (`planId`)")
db.execSQL("CREATE INDEX `index_progression_suggestions_exerciseId` ON `progression_suggestions` (`exerciseId`)")
db.execSQL("CREATE UNIQUE INDEX `index_progression_suggestions_sourceTargetSnapshotId_sourceProgressionRuleRevision` ON `progression_suggestions` (`sourceTargetSnapshotId`, `sourceProgressionRuleRevision`)")
db.execSQL("CREATE INDEX `index_progression_suggestions_status` ON `progression_suggestions` (`status`)")
```

Rebuild `workout_sets` so the new foreign key is real rather than a column-only annotation:

```kotlin
db.execSQL("""
    CREATE TABLE `workout_sets_new` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `sessionId` INTEGER NOT NULL,
        `exerciseId` INTEGER NOT NULL,
        `setNumber` INTEGER NOT NULL,
        `reps` INTEGER NOT NULL,
        `weightKg` REAL NOT NULL,
        `isWarmup` INTEGER NOT NULL,
        `completedAt` INTEGER NOT NULL,
        `rpe` REAL,
        `planTargetSnapshotId` INTEGER,
        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`planTargetSnapshotId`) REFERENCES `workout_plan_targets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
    )
""".trimIndent())
db.execSQL("INSERT INTO `workout_sets_new` (`id`,`sessionId`,`exerciseId`,`setNumber`,`reps`,`weightKg`,`isWarmup`,`completedAt`,`rpe`,`planTargetSnapshotId`) SELECT `id`,`sessionId`,`exerciseId`,`setNumber`,`reps`,`weightKg`,`isWarmup`,`completedAt`,`rpe`,NULL FROM `workout_sets`")
db.execSQL("DROP TABLE `workout_sets`")
db.execSQL("ALTER TABLE `workout_sets_new` RENAME TO `workout_sets`")
db.execSQL("CREATE INDEX `index_workout_sets_sessionId` ON `workout_sets` (`sessionId`)")
db.execSQL("CREATE INDEX `index_workout_sets_exerciseId` ON `workout_sets` (`exerciseId`)")
db.execSQL("CREATE INDEX `index_workout_sets_planTargetSnapshotId` ON `workout_sets` (`planTargetSnapshotId`)")
```

- [ ] **Step 7: Generate schema 11 and compile the migration test**

Run: `./gradlew --no-daemon :core:database:compileDebugKotlin :app:compileDebugAndroidTestKotlin`

Expected: PASS and Room creates `core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/11.json`. Inspect that JSON for database version `11`, the two unique indices, the composite target-history index, and the `SET NULL` set foreign key; do not hand-edit the schema JSON.

- [ ] **Step 8: Run migration 10 to 11 on an emulator-capable environment**

Run in CI/emulator: `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironlog.app.data.local.IronLogDatabaseMigrationTest#migration10To11_preservesRowsAndCreatesManualProgressionSchema`

Expected: PASS, 1 test executed. On this Mac, record `:app:compileDebugAndroidTestKotlin` as the local evidence and leave execution to the existing remote gate.

- [ ] **Step 9: Commit schema 11**

```bash
git add core/database/src/main/java/com/ironlog/app/data/local core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/11.json app/src/androidTest/java/com/ironlog/app/data/local/IronLogDatabaseMigrationTest.kt
git commit -m "feat: add progression persistence schema"
```

### Task 4: Snapshot every plan position when a workout starts

**Files:**
- Modify: `data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt`
- Modify: `app/src/main/java/com/ironlog/app/di/AppModule.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/repository/WorkoutRepositoryImplTest.kt`
- Create: `app/src/androidTest/java/com/ironlog/app/data/local/ProgressionSnapshotTransactionTest.kt`

**Interfaces:**
- Consumes: `TrainingPlanDao.getExercisesForPlan`, `ProgressionDao.insertTargets`, and `TransactionRunner` from Task 3.
- Produces: every successful planned `startWorkout(...)` has a complete immutable `workout_plan_targets` set; Task 7 can safely key the workout UI by snapshot ID.

- [ ] **Step 1: Add failing repository tests for duplicate exercise positions and immutable copies**

```kotlin
@Test
fun `planned workout snapshots every plan position including duplicate exercises`() = runTest {
    trainingPlanDao.planExercises = listOf(
        planExercise(id = 11, exerciseId = 7, orderIndex = 0, targetWeightKg = 100.0),
        planExercise(id = 12, exerciseId = 7, orderIndex = 1, targetWeightKg = 80.0)
    )

    val sessionId = repository.startWorkout("Two squats", planId = 3, metaPlanId = null)

    assertEquals(2, progressionDao.insertedTargets.size)
    assertEquals(listOf(0, 1), progressionDao.insertedTargets.map { it.orderIndex })
    assertEquals(listOf(100.0, 80.0), progressionDao.insertedTargets.map { it.target.weightKg })
    assertTrue(progressionDao.insertedTargets.all { it.sessionId == sessionId && it.planId == 3L })
}

@Test
fun `free workout creates no plan target snapshots`() = runTest {
    repository.startWorkout("Free", planId = null, metaPlanId = null)
    assertTrue(progressionDao.insertedTargets.isEmpty())
}
```

- [ ] **Step 2: Run the targeted repository tests and observe the constructor or assertion failure**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest"`

Expected: FAIL because `WorkoutRepositoryImpl` neither accepts plan/progression DAOs nor inserts snapshots.

- [ ] **Step 3: Move session insertion and snapshot copying into one transaction**

Extend the constructor with `trainingPlanDao: TrainingPlanDao` and `progressionDao: ProgressionDao`, then replace the insert tail of `startWorkout` with:

```kotlin
transactionRunner.runInTransaction {
    sessionDao.getActiveSession()?.let { return@runInTransaction it.id }
    if (planId != null) {
        require(trainingPlanDao.getPlanById(planId) != null) { "Training plan $planId does not exist" }
    }
    val sessionId = sessionDao.insert(
        WorkoutSessionEntity(
            startTime = EpochConverter.toLong(LocalDateTime.now()),
            name = name,
            planId = planId,
            metaPlanId = metaPlanId
        )
    )
    if (planId != null) {
        val planExercises = trainingPlanDao.getExercisesForPlan(planId).sortedBy { it.orderIndex }
        val targets = planExercises.map { planExercise ->
            WorkoutPlanTargetEntity(
                sessionId = sessionId,
                planId = planId,
                exerciseId = planExercise.exerciseId,
                orderIndex = planExercise.orderIndex,
                supersetGroupId = planExercise.supersetGroupId,
                target = ProgressionTargetColumns(
                    sets = planExercise.targetSets,
                    reps = planExercise.targetReps,
                    weightKg = planExercise.targetWeightKg
                ),
                progression = planExercise.progression
            )
        }
        val insertedIds = progressionDao.insertTargets(targets)
        check(insertedIds.size == targets.size && insertedIds.all { it > 0L }) {
            "Incomplete plan target snapshot"
        }
    }
    sessionId
}
```

Keep `startWorkoutMutex`, but run the active-session check again inside the transaction as shown so the database trigger remains the final concurrent-writer guard.

- [ ] **Step 4: Prove transaction rollback with a real Room test**

Seed two `PlanExerciseEntity` rows with the same `(planId, orderIndex)` through the DAO, call the production repository, catch the unique-index exception from `insertTargets`, then assert:

```kotlin
assertNull(database.workoutSessionDao().getActiveSession())
assertTrue(database.progressionDao().getTargetsForSession(1L).isEmpty())
```

This test must use `Room.inMemoryDatabaseBuilder`, the real `RoomTransactionRunner`, and the real DAOs; it must not fake rollback behavior.

- [ ] **Step 5: Wire the DAO and new repository constructor arguments**

```kotlin
single { get<IronLogDatabase>().progressionDao() }
single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get(), get(), get(), get(), get()) }
```

The constructor argument order is `WorkoutSessionDao`, `WorkoutSetDao`, `PersonalRecordDao`, `TrainingPlanDao`, `ProgressionDao`, `TransactionRunner`.

- [ ] **Step 6: Run the local tests and compile the instrumentation proof**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest" :app:compileDebugAndroidTestKotlin`

Expected: unit tests PASS; instrumentation sources compile. The remote test command is `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironlog.app.data.local.ProgressionSnapshotTransactionTest` and must report 1 executed test.

- [ ] **Step 7: Commit transactional snapshots**

```bash
git add data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt app/src/main/java/com/ironlog/app/di/AppModule.kt app/src/test/java/com/ironlog/app/data/repository/WorkoutRepositoryImplTest.kt app/src/androidTest/java/com/ironlog/app/data/local/ProgressionSnapshotTransactionTest.kt
git commit -m "feat: snapshot plan targets when workouts start"
```

### Task 5: Generate idempotent outcomes from stored snapshots

**Files:**
- Create: `data/src/main/java/com/ironlog/app/data/repository/ProgressionEntityMapper.kt`
- Create: `data/src/main/java/com/ironlog/app/data/repository/ProgressionRepositoryImpl.kt`
- Create: `data/src/test/java/com/ironlog/app/data/repository/ProgressionRepositoryImplTest.kt`
- Modify: `app/src/main/java/com/ironlog/app/di/AppModule.kt`

**Interfaces:**
- Consumes: Task 2 `ProgressionEngine`, Task 3 DAO/entity API, and snapshot-linked `WorkoutSetEntity` values.
- Produces: `generateOutcomesForSession`, retry generation, review flows, and pending count for Tasks 6, 8, 9, and 10.

- [ ] **Step 1: Write failing generation and retry tests**

```kotlin
@Test
fun `generation maps sets by snapshot id and keeps duplicate exercise positions separate`() = runTest {
    progressionDao.targets = listOf(target(id = 41, exerciseId = 7, orderIndex = 0), target(id = 42, exerciseId = 7, orderIndex = 1))
    setDao.sets = listOf(set(id = 1, snapshotId = 41, reps = 8), set(id = 2, snapshotId = 42, reps = 5))

    repository.generateOutcomesForSession(9)

    assertEquals(listOf(41L, 42L), progressionDao.suggestions.map { it.sourceTargetSnapshotId })
    assertEquals(2, engine.contexts.size)
    assertEquals(listOf(1L), engine.contexts[0].setsForTarget.map { it.id })
    assertEquals(listOf(2L), engine.contexts[1].setsForTarget.map { it.id })
}

@Test
fun `generation is idempotent per snapshot and revision`() = runTest {
    progressionDao.targets = listOf(target(id = 41))
    repository.generateOutcomesForSession(9)
    repository.generateOutcomesForSession(9)
    assertEquals(1, progressionDao.suggestions.size)
}

@Test
fun `manual targets are not evaluated or stored`() = runTest {
    progressionDao.targets = listOf(target(id = 41, config = ProgressionConfig.Manual()))
    val result = repository.generateOutcomesForSession(9)
    assertEquals(0, result.reviewItemCount)
    assertTrue(engine.contexts.isEmpty())
}

@Test
fun `retry processes only completed sessions with missing outcomes`() = runTest {
    progressionDao.missingSessionIds = listOf(9, 12)
    assertEquals(2, repository.generateMissingOutcomes())
    assertEquals(listOf(9L, 12L), progressionDao.requestedTargetSessionIds)
}

@Test
fun `current plan edits do not participate in retry generation`() = runTest {
    progressionDao.targets = listOf(target(id = 41, weightKg = 100.0))
    trainingPlanDao.currentWeightKg = 140.0
    repository.generateOutcomesForSession(9)
    assertEquals(100.0, engine.contexts.single().sourceTarget.target.weightKg, 0.0)
    verify(exactly = 0) { trainingPlanDao.getPlanExerciseAt(any(), any(), any()) }
}

@Test
fun `retry history follows workout completion order rather than generation time`() = runTest {
    sessionDao.sessions = listOf(completedSession(id = 9, endTime = 1_000), completedSession(id = 12, endTime = 2_000))
    progressionDao.targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
    progressionDao.targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
    progressionDao.missingSessionIds = listOf(9, 12)

    repository.generateMissingOutcomes()

    assertEquals(listOf(9L, 12L), progressionDao.requestedTargetSessionIds)
    assertEquals(1_000L to 9L, progressionDao.previousTargetBounds.single { it.second == 9L })
    assertEquals(2_000L to 12L, progressionDao.previousTargetBounds.single { it.second == 12L })
}

@Test
fun `an intervening target or config change cuts off older failure history`() = runTest {
    progressionDao.targets = listOf(target(id = 43, weightKg = 100.0))
    progressionDao.previousTargets = listOf(
        target(id = 42, weightKg = 100.0, config = ProgressionConfig.Manual()),
        target(id = 41, weightKg = 100.0)
    )
    progressionDao.suggestionsByTargetId[41] = suggestion(
        id = 1,
        sourceTargetSnapshotId = 41,
        sourceWeightKg = 100.0,
        streak = ProgressionStreakEffect.INCREMENT
    )

    repository.generateOutcomesForSession(12)

    assertTrue(engine.contexts.single().previousComparableOutcomesNewestFirst.isEmpty())
}

@Test
fun `direct generation catches up older missing sessions before current session`() = runTest {
    sessionDao.sessions = listOf(completedSession(id = 9, endTime = 1_000), completedSession(id = 12, endTime = 2_000))
    progressionDao.targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
    progressionDao.targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
    progressionDao.missingBefore[2_000L to 12L] = listOf(9)

    repository.generateOutcomesForSession(12)

    assertEquals(listOf(9L, 12L), progressionDao.requestedTargetSessionIds)
}

@Test
fun `review rows hydrate counted sets in stored evidence order`() = runTest {
    progressionDao.pendingRows.value = listOf(suggestion(id = 1, countedSetIds = listOf(30, 10, 20)))
    setDao.setsById = listOf(set(id = 10), set(id = 20), set(id = 30))

    val item = repository.observeReviewItems(null).first().single()

    assertEquals(listOf(30L, 10L, 20L), item.countedSets.map { it.id })
}

@Test(expected = IllegalStateException::class)
fun `review fails closed when stored evidence set is missing`() = runTest {
    progressionDao.pendingRows.value = listOf(suggestion(id = 1, countedSetIds = listOf(10, 20)))
    setDao.setsById = listOf(set(id = 10))
    repository.observeReviewItems(null).first()
}
```

- [ ] **Step 2: Run the data test and observe the missing repository failure**

Run: `./gradlew --no-daemon :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.ProgressionRepositoryImplTest"`

Expected: FAIL because `ProgressionRepositoryImpl` does not exist.

- [ ] **Step 3: Implement lossless entity mapping**

`ProgressionEntityMapper` must use a `Json` instance with `encodeDefaults = true`, encode `reasonArguments.toSortedMap()` as `Map<String, Double>` and ordered `countedSetIds` as `List<Long>`, and map outcome/status enums with `enumValueOf`. Sorting only argument keys makes database and backup bytes canonical; never sort the evidence IDs because their order records the evaluated sets. Expose `decodeCountedSetIds(row)`, `toSourceTarget(row)`, and `toPreviousOutcome(row)`. The last method constructs only `PreviousProgressionOutcome(toSourceTarget(row), enumValueOf(row.streakEffect))`, because history evaluation needs no review DTO or current-plan read. Require every decoded reason argument to be finite. Reconstruct a review outcome by `outcomeType`; `toDomain(row, countedSets)` receives exact hydrated evidence:

```kotlin
when (ProgressionOutcomeType.valueOf(outcomeType)) {
    ProgressionOutcomeType.PROPOSE_CHANGE -> ProgressionOutcome.ProposeChange(source, requireNotNull(suggested), reason, arguments, streak, countedSetIds)
    ProgressionOutcomeType.KEEP_TARGET -> ProgressionOutcome.KeepTarget(source, reason, arguments, streak, countedSetIds)
    ProgressionOutcomeType.INSUFFICIENT_DATA -> ProgressionOutcome.InsufficientData(source, reason, arguments, streak, countedSetIds)
    ProgressionOutcomeType.NOT_APPLICABLE -> ProgressionOutcome.NotApplicable(source, reason, arguments, streak, countedSetIds)
}
```

Mapping an entity must reconstruct its full `WorkoutPlanTarget` from duplicated source fields, not re-read the mutable current plan. Require unique, positive decoded IDs and require `countedSets.map { it.id } == countedSetIds`; unknown enum strings, malformed JSON, or missing evidence must throw. Backup validation and repository boundaries turn those failures into fail-closed UI errors.

- [ ] **Step 4: Implement generation from session-owned data only**

Use this constructor and method outline:

```kotlin
class ProgressionRepositoryImpl(
    private val progressionDao: ProgressionDao,
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val engine: ProgressionEngine,
    private val transactionRunner: TransactionRunner,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : ProgressionRepository
```

Every rule outcome stores the exact sorted/taken work-set IDs it evaluated; insufficient-data outcomes store the available sorted work-set IDs. Implement a private `generateSingleSession(sessionId)` whose reads, preflight checks, history lookup, and inserts all run inside one `transactionRunner.runInTransaction`. Public `generateOutcomesForSession(sessionId)` first loads the completed source bound, calls `getCompletedSessionIdsWithMissingOutcomesBefore(endTime, id)`, evaluates those sessions through `generateSingleSession` in returned order, and only then evaluates or re-reads the requested session. `generateMissingOutcomes()` similarly iterates the all-missing query and calls the private method directly. This prevents delayed retry order from changing a later failure streak; an older failure aborts before the newer session is evaluated.

1. Require a completed session; return zero counts for a missing, active, or free session.
2. Load targets ordered by plan position and all session sets once.
3. Skip `MANUAL`, but evaluate every other stored config including `Invalid` and unsupported revisions so it becomes an informational fail-closed result.
4. Build a map of the loaded session targets by ID. Before invoking the engine or inserting any row, require every session set with a non-null snapshot ID to resolve in that map and to match the target's session and exercise. Throw `IllegalStateException` on a dangling or cross-session/exercise link so the transaction stays unchanged and the completed-workout flow exposes retry instead of producing partial or misleading suggestions. Pass each engine call only the sets whose snapshot ID exactly equals that target ID; null/ad-hoc sets never enter a context.
5. Load previous target snapshots by plan/exercise/order and the source session's `(endTime, id)` bound. In DAO order use `takeWhile` while both `ProgressionTargetColumns` and the raw `ProgressionConfigColumns` equal the current entity, then map only that prefix to domain. Do not compare the compact `ProgressionConfig.Invalid` markers, query suggestions first, or use `filter`: a manual snapshot has no outcome row but must still cut the chain, and the first changed or malformed target/config prevents an older coincidentally equal target from reconnecting a failure streak. Never use suggestion creation time for history order because delayed retries must reproduce the same streak.
6. Load suggestions only for that comparable target-prefix, require exactly one row at the snapshot's stored rule revision for every ID, and require its duplicated source target to equal the target-table row before building `previousComparableOutcomesNewestFirst` in target order. Bypass the `IN ()` query when the prefix is empty. Missing or contradictory rows after completion-ordered catch-up are invariant failures, not permission to shorten the streak silently.
7. Insert `PENDING` for `ProposeChange`; insert `INFORMATIONAL` for `KeepTarget` and `InsufficientData`; do not store `NotApplicable`.
8. Use `OnConflictStrategy.IGNORE` and count only positive returned IDs.
9. Re-read the session's stored suggestions after insertion; return `reviewItemCount` as all `PENDING` plus `INFORMATIONAL` rows and `pendingCount` as only `PENDING`, so an idempotent retry still opens an already-created review.

The persisted row must set `sourceTargetSnapshotId`, duplicate `target` and raw `progression` columns directly from `WorkoutPlanTargetEntity`, store the exact rule revision, and use `createdAtEpochMillis = nowEpochMillis()`. Never rebuild source columns from a domain `Invalid` marker.

- [ ] **Step 5: Implement review flows and missing-session retry**

```kotlin
private suspend fun hydrate(rows: List<ProgressionSuggestionEntity>): List<ProgressionSuggestion> {
    val idsByRow = rows.associateWith(mapper::decodeCountedSetIds)
    val allIds = idsByRow.values.flatten().distinct()
    val setsById = if (allIds.isEmpty()) {
        emptyMap()
    } else {
        setDao.getSetsByIds(allIds).associateBy { it.id }
    }
    return rows.map { row ->
        val expectedIds = idsByRow.getValue(row)
        val countedSets = expectedIds.map { id ->
            checkNotNull(setsById[id]) { "Missing counted workout set $id for suggestion ${row.id}" }.toDomain()
        }
        mapper.toDomain(row, countedSets)
    }
}

override fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>> {
    val rows = if (sessionId == null) {
        progressionDao.observePendingSuggestions()
    } else {
        progressionDao.observeSuggestionsForSession(sessionId)
    }
    return rows.mapLatest(::hydrate)
}

override fun observePendingCount(): Flow<Int> = progressionDao.observePendingCount()

override suspend fun generateMissingOutcomes(): Int =
    progressionDao.getCompletedSessionIdsWithMissingOutcomes()
        .sumOf { sessionId -> generateSingleSession(sessionId).insertedCount }
```

- [ ] **Step 6: Bind engine and repository**

```kotlin
single { ProgressionEngine() }
single<ProgressionRepository> { ProgressionRepositoryImpl(get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 7: Run the targeted repository test**

Run: `./gradlew --no-daemon :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.ProgressionRepositoryImplTest"`

Expected: PASS with generation, duplicate-position separation, idempotency, completion-ordered catch-up, manual skip, retry, and current-plan-independence tests executed.

- [ ] **Step 8: Commit outcome generation**

```bash
git add data/src/main/java/com/ironlog/app/data/repository/ProgressionEntityMapper.kt data/src/main/java/com/ironlog/app/data/repository/ProgressionRepositoryImpl.kt data/src/test/java/com/ironlog/app/data/repository/ProgressionRepositoryImplTest.kt app/src/main/java/com/ironlog/app/di/AppModule.kt
git commit -m "feat: generate persisted progression outcomes"
```

### Task 6: Make acceptance stale-safe and atomic

**Files:**
- Modify: `core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionConfigValidator.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/ProgressionRepositoryImpl.kt`
- Modify: `data/src/test/java/com/ironlog/app/data/repository/ProgressionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: stored full snapshots, current `PlanExerciseEntity`, `TransactionRunner`, and Task 2 validation.
- Produces: atomic `acceptSuggestions`, `rejectSuggestion`, and stale reconciliation for the review and dashboard ViewModels.

- [ ] **Step 1: Add failing decision tests**

```kotlin
@Test
fun `single acceptance updates the exact plan position and records final target`() = runTest {
    seedPending(id = 1, planId = 3, exerciseId = 7, orderIndex = 1, sourceWeight = 100.0, proposedWeight = 102.5)
    trainingPlanDao.rows += matchingPlanExercise(planId = 3, exerciseId = 7, orderIndex = 1, weight = 100.0)

    val result = repository.acceptSuggestions(mapOf(1L to ProgressionTarget(3, 8, 102.5)))

    assertEquals(ProgressionDecisionResult.Accepted(setOf(1)), result)
    assertEquals(102.5, trainingPlanDao.rows.single().targetWeightKg, 0.0)
    assertEquals("ACCEPTED", progressionDao.suggestions.single().status)
}

@Test
fun `batch conflict changes no plan row and marks conflicting suggestions stale`() = runTest {
    seedPending(id = 1, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 102.5)
    seedPending(id = 2, orderIndex = 1, sourceWeight = 80.0, proposedWeight = 82.5)
    trainingPlanDao.rows += matchingPlanExercise(orderIndex = 0, weight = 100.0)
    trainingPlanDao.rows += matchingPlanExercise(orderIndex = 1, weight = 90.0)

    val result = repository.acceptSuggestions(
        mapOf(1L to ProgressionTarget(3, 8, 102.5), 2L to ProgressionTarget(3, 8, 82.5))
    )

    assertEquals(ProgressionDecisionResult.Stale(setOf(2)), result)
    assertEquals(listOf(100.0, 90.0), trainingPlanDao.rows.map { it.targetWeightKg })
    assertEquals(listOf("PENDING", "STALE"), progressionDao.suggestions.map { it.status })
}

@Test
fun `invalid edited target changes neither plan nor status`() = runTest {
    seedPending(id = 1, sourceWeight = 100.0, proposedWeight = 102.5)
    trainingPlanDao.rows += matchingPlanExercise(weight = 100.0)
    val result = repository.acceptSuggestions(mapOf(1L to ProgressionTarget(0, 8, Double.NaN)))
    assertTrue(result is ProgressionDecisionResult.Invalid)
    assertEquals(100.0, trainingPlanDao.rows.single().targetWeightKg, 0.0)
    assertEquals("PENDING", progressionDao.suggestions.single().status)
}

@Test
fun `batch rejects two suggestions for the same current plan position`() = runTest {
    seedPending(id = 1, sourceSessionId = 9, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 102.5)
    seedPending(id = 2, sourceSessionId = 12, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 105.0)
    trainingPlanDao.rows += matchingPlanExercise(orderIndex = 0, weight = 100.0)

    val result = repository.acceptSuggestions(
        mapOf(1L to ProgressionTarget(3, 8, 102.5), 2L to ProgressionTarget(3, 8, 105.0))
    )

    assertTrue(result is ProgressionDecisionResult.Invalid)
    assertEquals(100.0, trainingPlanDao.rows.single().targetWeightKg, 0.0)
    assertEquals(listOf("PENDING", "PENDING"), progressionDao.suggestions.map { it.status })
}

@Test
fun `mid batch write failure rolls back plan targets and decisions`() = runTest {
    seedPending(id = 1, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 102.5)
    seedPending(id = 2, orderIndex = 1, sourceWeight = 80.0, proposedWeight = 82.5)
    trainingPlanDao.rows += matchingPlanExercise(id = 11, orderIndex = 0, weight = 100.0)
    trainingPlanDao.rows += matchingPlanExercise(id = 12, orderIndex = 1, weight = 80.0)
    trainingPlanDao.failUpdateForId = 12

    assertFailsWith<IllegalStateException> {
        repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(3, 8, 102.5), 2L to ProgressionTarget(3, 8, 82.5))
        )
    }

    assertEquals(listOf(100.0, 80.0), trainingPlanDao.rows.map { it.targetWeightKg })
    assertEquals(listOf("PENDING", "PENDING"), progressionDao.suggestions.map { it.status })
}

@Test
fun `reconcile marks disabled or edited scheme stale`() = runTest {
    seedPending(id = 1, sourceWeight = 100.0)
    trainingPlanDao.rows += matchingPlanExercise(weight = 100.0, config = ProgressionConfig.Manual())
    assertEquals(setOf(1L), repository.reconcileOutstandingSuggestions())
    assertEquals("STALE", progressionDao.suggestions.single().status)
}
```

- [ ] **Step 2: Run the decision tests and observe unimplemented-method failures**

Run: `./gradlew --no-daemon :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.ProgressionRepositoryImplTest"`

Expected: FAIL because acceptance, rejection, and reconciliation are not implemented.

- [ ] **Step 3: Expose shared target/config validation to the data layer**

Make `ProgressionConfigValidator` public and provide:

```kotlin
object ProgressionConfigValidator {
    fun validationErrors(target: ProgressionTarget, config: ProgressionConfig): List<String>
}
```

The returned paths are stable machine-facing names such as `target.sets`, `config.step.originalValue`, `config.targetRpe`, and `config.failurePolicy.backoffPercent`. An empty list means valid; the same function must be called by the engine, plan editor, acceptance, and backup mapping boundaries.

- [ ] **Step 4: Implement exact stale comparison**

```kotlin
private fun PlanExerciseEntity.matches(source: ProgressionSuggestionEntity): Boolean =
    planId == source.planId &&
        exerciseId == source.exerciseId &&
        orderIndex == source.orderIndex &&
        targetSets == source.sourceTarget.sets &&
        targetReps == source.sourceTarget.reps &&
        targetWeightKg == source.sourceTarget.weightKg &&
        progression == source.sourceProgression
```

`reconcileOutstandingSuggestions` loads every pending row, compares it to `getPlanExerciseAt`, marks only mismatches `STALE` with `decidedAtEpochMillis = nowEpochMillis()`, and returns their IDs.

- [ ] **Step 5: Implement all-or-nothing acceptance**

Inside one `transactionRunner.runInTransaction`:

```kotlin
if (finalTargetsBySuggestionId.isEmpty()) {
    return@runInTransaction ProgressionDecisionResult.Invalid("Select at least one suggestion")
}
val rows = progressionDao.getSuggestionsByIds(finalTargetsBySuggestionId.keys)
if (rows.size != finalTargetsBySuggestionId.size || rows.any { it.status != ProgressionSuggestionStatus.PENDING.name }) {
    return@runInTransaction ProgressionDecisionResult.Invalid("Every selected suggestion must still be PENDING")
}
if (rows.any { it.outcomeType != ProgressionOutcomeType.PROPOSE_CHANGE.name || it.suggestedTarget == null }) {
    return@runInTransaction ProgressionDecisionResult.Invalid("Every selected suggestion must contain a proposed target")
}
if (rows.groupBy { Triple(it.planId, it.exerciseId, it.orderIndex) }.any { it.value.size > 1 }) {
    return@runInTransaction ProgressionDecisionResult.Invalid("Select only one suggestion per plan position")
}
val currentRows = rows.associateWith { row ->
    trainingPlanDao.getPlanExerciseAt(row.planId, row.exerciseId, row.orderIndex)
}
val staleIds = currentRows.filter { (suggestion, current) -> current == null || !current.matches(suggestion) }.keys.map { it.id }.toSet()
if (staleIds.isNotEmpty()) {
    rows.filter { it.id in staleIds }.forEach { row ->
        progressionDao.updateSuggestion(row.copy(status = ProgressionSuggestionStatus.STALE.name, decidedAtEpochMillis = nowEpochMillis()))
    }
    return@runInTransaction ProgressionDecisionResult.Stale(staleIds)
}
rows.forEach { row ->
    val finalTarget = requireNotNull(finalTargetsBySuggestionId[row.id])
    val config = row.sourceProgression.toDomain()
    val errors = ProgressionConfigValidator.validationErrors(finalTarget, config)
    if (errors.isNotEmpty()) return@runInTransaction ProgressionDecisionResult.Invalid(errors.joinToString())
}
rows.forEach { row ->
    val finalTarget = requireNotNull(finalTargetsBySuggestionId[row.id])
    val current = requireNotNull(currentRows[row])
    check(trainingPlanDao.updatePlanExerciseTargetsById(current.id, finalTarget.sets, finalTarget.reps, finalTarget.weightKg) == 1)
    val proposed = requireNotNull(row.suggestedTarget).toDomain()
    progressionDao.updateSuggestion(
        row.copy(
            status = ProgressionSuggestionStatus.ACCEPTED.name,
            wasEdited = finalTarget != proposed,
            finalTarget = ProgressionTargetColumns.fromDomain(finalTarget),
            decidedAtEpochMillis = nowEpochMillis()
        )
    )
}
ProgressionDecisionResult.Accepted(rows.map { it.id }.toSet())
```

Do not call `TrainingPlanRepository.savePlan` or `replacePlanAndExercises` anywhere in this path.

- [ ] **Step 6: Implement rejection**

`rejectSuggestion(id)` must load exactly one row, return without change unless it is `PENDING`, and then write `REJECTED` plus `decidedAtEpochMillis`; it never loads or updates a plan.

- [ ] **Step 7: Run the repository and engine tests**

Run: `./gradlew --no-daemon :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.ProgressionRepositoryImplTest" :core:common:testDebugUnitTest --tests "com.ironlog.app.domain.progression.ProgressionEngineTest"`

Expected: PASS; the fake transaction runner used by the batch-conflict test must snapshot and restore fake DAO state on exceptions so partial-update regressions are observable.

- [ ] **Step 8: Commit atomic decisions**

```bash
git add core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionConfigValidator.kt data/src/main/java/com/ironlog/app/data/repository/ProgressionRepositoryImpl.kt data/src/test/java/com/ironlog/app/data/repository/ProgressionRepositoryImplTest.kt
git commit -m "feat: apply progression suggestions atomically"
```

### Task 7: Carry snapshot identity through active workout logging

**Files:**
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSetDao.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt`
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt`
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ironlog/app/data/repository/WorkoutRepositoryImplTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/local/entity/WorkoutSetEntityTest.kt`

**Interfaces:**
- Consumes: `ProgressionRepository.observeTargetsForSession` and `WorkoutSet.planTargetSnapshotId`.
- Produces: duplicate-safe `WorkoutExerciseKey` and correctly linked sets for Task 5 generation.

- [ ] **Step 1: Add failing tests for duplicate positions, set numbering, and ad-hoc sets**

```kotlin
@Test
fun `duplicate planned exercise positions stay separate and receive only their own sets`() = runTest {
    progressionRepository.targets.value = listOf(
        target(id = 41, exerciseId = 7, orderIndex = 0, weightKg = 100.0),
        target(id = 42, exerciseId = 7, orderIndex = 1, weightKg = 80.0)
    )
    workoutRepository.sets.value = listOf(
        workoutSet(id = 1, exerciseId = 7, snapshotId = 41),
        workoutSet(id = 2, exerciseId = 7, snapshotId = 42)
    )

    val items = viewModel.uiState.first { it.exercisesWithSets.size == 2 }.exercisesWithSets
    assertEquals(listOf(WorkoutExerciseKey.Planned(41), WorkoutExerciseKey.Planned(42)), items.map { it.key })
    assertEquals(listOf(listOf(1L), listOf(2L)), items.map { item -> item.sets.map { it.id } })
}

@Test
fun `logging against a planned position stores snapshot id and numbers within that position`() = runTest {
    progressionRepository.targets.value = listOf(target(id = 41, exerciseId = 7, orderIndex = 0))
    workoutRepository.persistedSets += workoutSet(id = 1, exerciseId = 7, snapshotId = 41, setNumber = 1)
    viewModel.logSet(WorkoutExerciseKey.Planned(41), exerciseId = 7, reps = 8, weightKg = 100.0)
    advanceUntilIdle()
    assertEquals(41L, workoutRepository.addedSet.single().planTargetSnapshotId)
    assertEquals(2, workoutRepository.addedSet.single().setNumber)
}

@Test
fun `logging an added exercise keeps snapshot id null`() = runTest {
    viewModel.logSet(WorkoutExerciseKey.AdHoc(9), exerciseId = 9, reps = 10, weightKg = 20.0)
    advanceUntilIdle()
    assertNull(workoutRepository.addedSet.single().planTargetSnapshotId)
}

@Test
fun `rpe progression captures canonical rpe even when global intensity tracking is off`() = runTest {
    prefsRepo.updateIntensitySystem(IntensitySystem.OFF)
    progressionRepository.targets.value = listOf(target(id = 41, exerciseId = 7, config = rpeConfig()))
    viewModel.logSet(WorkoutExerciseKey.Planned(41), exerciseId = 7, reps = 8, weightKg = 100.0, intensity = "8")
    advanceUntilIdle()
    assertEquals(8.0, workoutRepository.addedSet.single().rpe ?: 0.0, 0.0)
}
```

Extend `WorkoutSetEntityTest` to assert the nullable snapshot ID survives both domain/entity mapping directions.

Add repository-boundary tests that use an active and a completed session to prove: a linked insert is rejected when its snapshot belongs to another session/exercise; an update cannot change `sessionId`, `exerciseId`, `setNumber`, `isWarmup`, `completedAt`, or `planTargetSnapshotId`; and add/update/delete all fail before DAO mutation once the owning session has an `endTime`. These tests protect evidence immutability even if a caller bypasses the ViewModel.

- [ ] **Step 2: Run the targeted ViewModel and mapper tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest" --tests "com.ironlog.app.data.local.entity.WorkoutSetEntityTest" --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest"`

Expected: FAIL because planned items are still grouped by `exerciseId` and `logSet` has no position key.

- [ ] **Step 3: Replace exercise-only identity with a stable UI key**

```kotlin
sealed interface WorkoutExerciseKey {
    data class Planned(val snapshotId: Long) : WorkoutExerciseKey
    data class AdHoc(val exerciseId: Long) : WorkoutExerciseKey
}

data class ExerciseWithSets(
    val key: WorkoutExerciseKey,
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val planTarget: WorkoutPlanTarget? = null,
    val previousSession: PreviousExerciseSessionUi? = null
)
```

Delete `_planTargets`, `_planSupersetGroups`, and `loadPlanExercises()`. Remove `TrainingPlanRepository` from the ViewModel constructor and inject `ProgressionRepository` instead.

- [ ] **Step 4: Build planned and ad-hoc rows without collapsing duplicate exercises**

Observe targets for the saved `sessionId`, load exercises by their IDs, and construct planned rows in `(orderIndex, id)` order. Associate sets as follows:

```kotlin
val plannedRows = targets.mapNotNull { target ->
    exercisesById[target.exerciseId]?.let { exercise ->
        ExerciseWithSets(
            key = WorkoutExerciseKey.Planned(target.id),
            exercise = exercise,
            sets = sets.filter { it.planTargetSnapshotId == target.id },
            planTarget = target,
            previousSession = previousByExerciseId[target.exerciseId]
        )
    }
}
val adHocRows = adHocExercises.map { exercise ->
    ExerciseWithSets(
        key = WorkoutExerciseKey.AdHoc(exercise.id),
        exercise = exercise,
        sets = sets.filter { it.planTargetSnapshotId == null && it.exerciseId == exercise.id },
        previousSession = previousByExerciseId[exercise.id]
    )
}
plannedRows + adHocRows
```

Exercise reconciliation may add only sets with `planTargetSnapshotId == null` to `adHocExercises`; otherwise a planned set would reappear as a duplicate ad-hoc row after process recreation.

- [ ] **Step 5: Link every new set to its selected row**

Change `logSet` and its retry descriptor to carry `WorkoutExerciseKey`. Determine existing sets with:

```kotlin
val persistedSets = workoutRepository.getSetsForSessionList(sessionId).filter { set ->
    when (key) {
        is WorkoutExerciseKey.Planned -> set.planTargetSnapshotId == key.snapshotId
        is WorkoutExerciseKey.AdHoc -> set.planTargetSnapshotId == null && set.exerciseId == exerciseId
    }
}
val snapshotId = (key as? WorkoutExerciseKey.Planned)?.snapshotId
val set = WorkoutSet(
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = (persistedSets.maxOfOrNull { it.setNumber } ?: 0) + 1,
    reps = reps,
    weightKg = weightKg,
    isWarmup = isWarmup,
    completedAt = completedAt,
    rpe = parsedRpe,
    planTargetSnapshotId = snapshotId
)
```

Change rest-timer and in-flight maps to `Map<WorkoutExerciseKey, ...>` so simultaneous duplicate positions cannot disable or stop each other's controls.

Add `WorkoutSetDao.getSetById(id)` and enforce the final persistence boundary inside the repository's existing set transactions. `addSet` must require an existing active session and, when the snapshot ID is non-null, a `ProgressionDao.getTargetById` row with the same session and exercise. `updateSet` must load the stored row, require its session is still active, and require all identity fields listed in Step 1 to remain equal; only repetitions, weight, and RPE may change. `deleteSet` must load the row and require its session is active before deletion. Throw `IllegalStateException` on violations before set or PR writes; the ViewModel guards provide normal UX, while these checks close finish/edit races.

- [ ] **Step 6: Update Compose call sites and target rendering**

Use `ExerciseWithSets.key` for `LazyColumn` keys and every callback. Read targets through `row.planTarget.target.sets/reps/weightKg` and show the active scheme from the typed snapshot config; render `ProgressionConfig.Invalid` as localized `Konfiguration ungültig` rather than its `MANUAL` fallback. Never read the mutable training plan on this screen.

For a row using `RPE_RIR`, always render an intensity field because that rule requires it. Reuse the global RPE/RIR display and conversion mode when enabled; when the global mode is `OFF`, render a row-local `RPE` field and parse it directly as canonical RPE without changing the preference. Other schemes retain the existing global intensity behavior.

- [ ] **Step 7: Run the targeted tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest" --tests "com.ironlog.app.data.local.entity.WorkoutSetEntityTest" --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest"`

Expected: PASS, including two separate UI rows for the same exercise ID, nullable mapper parity, snapshot-link validation, immutable set identity, and the completed-session mutation lock.

- [ ] **Step 8: Commit snapshot-linked logging**

```bash
git add core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSetDao.kt data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt core/designsystem/src/main/res/values/strings.xml app/src/test/java/com/ironlog/app/data/repository/WorkoutRepositoryImplTest.kt app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt app/src/test/java/com/ironlog/app/data/local/entity/WorkoutSetEntityTest.kt
git commit -m "feat: link workout sets to plan snapshots"
```

### Task 8: Add progression configuration to the plan editor

**Files:**
- Modify: `feature/plans/src/main/java/com/ironlog/app/presentation/plans/PlanEditorViewModel.kt`
- Modify: `feature/plans/src/main/java/com/ironlog/app/presentation/plans/PlanEditorScreen.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/ironlog/app/presentation/plans/PlanEditorViewModelTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/repository/TrainingPlanRepositoryImplTest.kt`

**Interfaces:**
- Consumes: typed configs, shared validator, `AppPreferencesRepository.preferences`, and existing plan save/reorder behavior.
- Produces: valid opt-in configuration persisted with each `PlanExercise`.

- [ ] **Step 1: Add failing editor tests for defaults, validation, units, and reorder persistence**

```kotlin
@Test
fun `new exercises default to manual progression`() {
    viewModel.addExercise(squat)
    assertEquals(ProgressionConfig.Manual(), viewModel.uiState.value.exercises.single().planExercise.progressionConfig)
}

@Test
fun `double progression draft uses current reps and two rep ceiling`() {
    viewModel.addExercise(squat)
    viewModel.openProgressionEditor(0)
    viewModel.selectProgressionScheme(ProgressionScheme.DOUBLE)
    val draft = requireNotNull(viewModel.uiState.value.progressionEditor)
    assertEquals("10", draft.minReps)
    assertEquals("12", draft.maxReps)
}

@Test
fun `imperial step saves original pounds and canonical kilograms`() = runTest {
    preferences.value = AppPreferences(unitSystem = UnitSystem.IMPERIAL)
    viewModel.addExercise(squat)
    viewModel.openProgressionEditor(0)
    viewModel.selectProgressionScheme(ProgressionScheme.LINEAR)
    viewModel.updateProgressionField(ProgressionField.STEP, "5")
    viewModel.saveProgressionEditor()
    val config = viewModel.uiState.value.exercises.single().planExercise.progressionConfig as ProgressionConfig.Linear
    assertEquals(5.0, config.step.originalValue, 0.0)
    assertEquals(UnitSystem.IMPERIAL, config.step.originalUnit)
    assertEquals(WeightFormatting.convertToKg(5.0, UnitSystem.IMPERIAL), config.step.kilograms, 0.000001)
}

@Test
fun `opening and saving an existing step in another display unit is lossless`() = runTest {
    preferences.value = AppPreferences(unitSystem = UnitSystem.METRIC)
    val original = WeightStep(5.0, UnitSystem.IMPERIAL, WeightFormatting.convertToKg(5.0, UnitSystem.IMPERIAL))
    seedExercise(config = ProgressionConfig.Linear(step = original))
    viewModel.openProgressionEditor(0)
    viewModel.saveProgressionEditor()
    val saved = viewModel.uiState.value.exercises.single().planExercise.progressionConfig as ProgressionConfig.Linear
    assertEquals(original, saved.step)
}

@Test
fun `invalid rpe config stays in editor and cannot reach plan state`() {
    openRpeDraft(targetRpe = "11", tolerance = "3")
    viewModel.saveProgressionEditor()
    assertNotNull(viewModel.uiState.value.progressionEditor)
    assertTrue(viewModel.uiState.value.progressionEditor!!.errors.containsKey(ProgressionField.TARGET_RPE))
    assertTrue(viewModel.uiState.value.exercises.single().planExercise.progressionConfig is ProgressionConfig.Manual)
}

@Test
fun `reorder preserves each exercises progression config`() {
    configureLinear(index = 0)
    configureTotalReps(index = 1)
    viewModel.moveDown(0)
    assertTrue(viewModel.uiState.value.exercises[0].planExercise.progressionConfig is ProgressionConfig.TotalReps)
    assertTrue(viewModel.uiState.value.exercises[1].planExercise.progressionConfig is ProgressionConfig.Linear)
}
```

Extend `TrainingPlanRepositoryImplTest` to round-trip every persistable `ProgressionConfig` variant through `PlanExerciseEntity`. Add a fail-closed test that `ProgressionConfig.Invalid` is rejected before repository mutation, because the compact domain marker intentionally cannot reconstruct malformed nullable storage columns losslessly.

- [ ] **Step 2: Run the editor and repository tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.plans.PlanEditorViewModelTest" --tests "com.ironlog.app.data.repository.TrainingPlanRepositoryImplTest"`

Expected: FAIL because the editor has no progression state and persistence does not map configs yet.

- [ ] **Step 3: Add explicit editor state and parse fields centrally**

```kotlin
enum class ProgressionField { STEP, MIN_REPS, MAX_REPS, TOTAL_REPS, TARGET_RPE, RPE_TOLERANCE, STALL_THRESHOLD, BACKOFF_PERCENT }

data class ProgressionEditorUi(
    val exerciseIndex: Int,
    val scheme: ProgressionScheme,
    val step: String,
    val minReps: String,
    val maxReps: String,
    val totalReps: String,
    val targetRpe: String,
    val rpeTolerance: String,
    val stallThreshold: String,
    val backoffPercent: String,
    val unitSystem: UnitSystem,
    val originalStep: WeightStep? = null,
    val stepWasEdited: Boolean = false,
    val errors: Map<ProgressionField, String> = emptyMap()
)
```

Add `unitSystem` and `progressionEditor` to `PlanEditorUiState`, inject `AppPreferencesRepository`, and collect `preferences.map { it.unitSystem }.distinctUntilChanged()`. Existing configured steps display `WeightFormatting.convertToDisplay(step.kilograms, currentUnit)`, but opening the sheet retains the exact stored `WeightStep` in `originalStep`. Saving without editing the step reuses that object byte-for-byte; the first step-field edit sets `stepWasEdited` and saving then creates a new `WeightStep` from the draft's captured visible value/unit plus its canonical kg conversion. A preference change while a dirty sheet is open affects the plan screen only and does not reinterpret the draft; reopening the sheet uses the new preference.

- [ ] **Step 4: Implement deterministic scheme defaults and validation**

When a manual exercise first selects a scheme, seed:

```kotlin
val defaultStep = if (unitSystem == UnitSystem.IMPERIAL) "5" else "2.5"
val minReps = planExercise.targetReps.toString()
val maxReps = (planExercise.targetReps + 2).toString()
val totalReps = planExercise.targetSets.toLong().times(planExercise.targetReps).toString()
val targetRpe = ""
val rpeTolerance = ""
val stallThreshold = "2"
val backoffPercent = "10"
```

For a newly activated scheme set `originalStep = null` and `stepWasEdited = true`, so the default becomes an explicit `WeightStep` in the captured display unit when the user taps `Übernehmen`.

`saveProgressionEditor` parses decimal commas with the existing `parseDecimal` behavior, creates the selected typed config, calls `ProgressionConfigValidator.validationErrors` with the exercise's current target, maps returned paths to exact fields, and updates only that `PlanExercise` when there are no errors. Selecting `MANUAL` creates a field-free valid draft; only `Übernehmen` updates the in-memory `PlanExercise`, while `Abbrechen` leaves it unchanged.

Before `savePlan`, validate every exercise target/config pair again; on any error, set `error = "Progression für <exercise name> ist unvollständig"` and do not call the repository.

- [ ] **Step 5: Make target-weight entry unit-aware**

Rename `updateTargetWeight` to `updateTargetWeightDisplay` and store `WeightFormatting.convertToKg(displayValue, uiState.unitSystem)`. Render existing canonical target weight with `convertToDisplay` and label the field with `WeightFormatting.unitLabel(unitSystem)`; this removes the current hard-coded kg assumption.

- [ ] **Step 6: Add the configuration sheet and localized copy**

Under each plan exercise's target fields render `Progression: Aus` or the localized scheme name. A tap opens a Material 3 `ModalBottomSheet` with:

- a single-choice row for all five schemes;
- only fields used by the selected scheme;
- `Schrittweite (<kg|lb>)`, `Fehlversuche bis Backoff`, and `Backoff (%)` for every active scheme;
- `Min. Wdh.`/`Max. Wdh.` for double progression;
- `Ziel-Gesamtwiederholungen` for total reps;
- `Ziel-RPE`/`Toleranz` for RPE/RIR;
- an inline error under each invalid field;
- a one-sentence preview generated from current draft values;
- `Abbrechen` and `Übernehmen` buttons.

Add all labels, scheme names, previews, and errors to `core/designsystem/.../strings.xml`; do not embed German literals in the new composables.

- [ ] **Step 7: Run targeted tests and compile the feature**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.plans.PlanEditorViewModelTest" --tests "com.ironlog.app.data.repository.TrainingPlanRepositoryImplTest" :feature:plans:compileDebugKotlin`

Expected: PASS, with plan configuration surviving reorder and repository mapping.

- [ ] **Step 8: Commit plan configuration**

```bash
git add feature/plans/src/main/java/com/ironlog/app/presentation/plans/PlanEditorViewModel.kt feature/plans/src/main/java/com/ironlog/app/presentation/plans/PlanEditorScreen.kt core/designsystem/src/main/res/values/strings.xml app/src/test/java/com/ironlog/app/presentation/plans/PlanEditorViewModelTest.kt app/src/test/java/com/ironlog/app/data/repository/TrainingPlanRepositoryImplTest.kt
git commit -m "feat: configure progression per plan exercise"
```

### Task 9: Build the progression review feature

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `feature/progression/build.gradle.kts`
- Create: `feature/progression/src/main/java/com/ironlog/app/presentation/progression/ProgressionReviewViewModel.kt`
- Create: `feature/progression/src/main/java/com/ironlog/app/presentation/progression/ProgressionReviewScreen.kt`
- Create: `feature/progression/src/main/java/com/ironlog/app/presentation/progression/ProgressionReasonText.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/ironlog/app/di/AppModule.kt`
- Create: `app/src/test/java/com/ironlog/app/presentation/progression/ProgressionReviewViewModelTest.kt`

**Interfaces:**
- Consumes: Task 5 review flow, Task 6 decisions, and current unit preferences.
- Produces: session-scoped or all-pending `ProgressionReviewScreen(onClose)` for navigation in Task 10.

- [ ] **Step 1: Write failing review-state tests**

```kotlin
@Test
fun `session review shows informational rows without decision actions`() = runTest {
    repository.reviewItems.value = listOf(information(id = 1), pendingChange(id = 2))
    val state = viewModel.uiState.first { it.items.size == 2 }
    assertFalse(state.items.first { it.id == 1L }.canDecide)
    assertTrue(state.items.first { it.id == 2L }.canDecide)
}

@Test
fun `accept all sends only pending proposed targets`() = runTest {
    repository.reviewItems.value = listOf(information(id = 1), pendingChange(id = 2), staleChange(id = 3))
    viewModel.acceptAllSafe()
    advanceUntilIdle()
    assertEquals(setOf(2L), repository.lastAccepted.keys)
}

@Test
fun `accept all selects only newest suggestion for one plan position`() = runTest {
    repository.reviewItems.value = listOf(
        pendingChange(id = 2, sourceSessionId = 12, planId = 3, orderIndex = 0),
        pendingChange(id = 1, sourceSessionId = 9, planId = 3, orderIndex = 0)
    )
    advanceUntilIdle()
    repository.reconcileCalls = 0
    viewModel.acceptAllSafe()
    advanceUntilIdle()
    assertEquals(setOf(2L), repository.lastAccepted.keys)
    assertEquals(1, repository.reconcileCalls)
}

@Test
fun `edited display weight is converted before acceptance`() = runTest {
    preferences.value = AppPreferences(unitSystem = UnitSystem.IMPERIAL)
    repository.reviewItems.value = listOf(pendingChange(id = 2, proposedWeightKg = 45.359237))
    viewModel.updateEdit(2, sets = "3", reps = "8", weight = "105")
    viewModel.acceptOne(2)
    advanceUntilIdle()
    assertEquals(WeightFormatting.convertToKg(105.0, UnitSystem.IMPERIAL), repository.lastAccepted.getValue(2).weightKg, 0.000001)
}

@Test
fun `stale acceptance result remains visible and refreshes rows`() = runTest {
    repository.acceptResult = ProgressionDecisionResult.Stale(setOf(2))
    advanceUntilIdle()
    repository.reconcileCalls = 0
    viewModel.acceptOne(2)
    advanceUntilIdle()
    assertEquals("Dieser Vorschlag passt nicht mehr zum Plan.", viewModel.uiState.value.message)
    assertEquals(1, repository.reconcileCalls)
}
```

- [ ] **Step 2: Add the module and run the missing-class test**

Add `include(":feature:progression")`, copy the Android/Compose configuration pattern from other feature modules, and depend only on `core:model`, `core:common`, and `core:designsystem`; the feature consumes the domain repository contract and must not depend on `data`. Add `implementation(project(":feature:progression"))` to `app`.

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.progression.ProgressionReviewViewModelTest"`

Expected: FAIL because the review ViewModel does not exist.

- [ ] **Step 3: Implement review state, edits, and decisions**

```kotlin
data class ProgressionReviewItemUi(
    val id: Long,
    val sourceSessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val scheme: ProgressionScheme,
    val source: ProgressionTarget,
    val proposed: ProgressionTarget?,
    val countedSets: List<WorkoutSet>,
    val reasonCode: ProgressionReasonCode,
    val reasonArguments: Map<String, Double>,
    val status: ProgressionSuggestionStatus,
    val canDecide: Boolean
)

enum class ProgressionEditField { SETS, REPS, WEIGHT }

data class ProgressionEditDraft(
    val sets: String,
    val reps: String,
    val weight: String,
    val unitSystem: UnitSystem,
    val dirtyFields: Set<ProgressionEditField> = emptySet(),
    val errors: Map<ProgressionEditField, String> = emptyMap()
)

data class ProgressionReviewUiState(
    val items: List<ProgressionReviewItemUi> = emptyList(),
    val edits: Map<Long, ProgressionEditDraft> = emptyMap(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isWorking: Boolean = false,
    val message: String? = null
)
```

Read `sessionId` from `SavedStateHandle`; values `<= 0` map to `null` and therefore all pending rows. On init call `reconcileOutstandingSuggestions()` once, then collect `observeReviewItems(scope)` and preferences. `acceptOne`, `acceptAllSafe`, and `reject` must set `isWorking`, call the repository, map invalid/stale results to user messages, and never optimistically mark a row accepted. An untouched field always reuses the exact stored proposed value; only dirty fields override it, with dirty weight converted from the draft's captured unit. The batch action receives pending rows ordered newest source session first, selects at most the first item per `(planId, exerciseId, orderIndex)`, sends exact stored proposals, and ignores any unconfirmed edit-sheet draft. After a successful acceptance it calls stale reconciliation once so older proposals for the changed rows leave the pending count; this prevents both conflicting writes and display rounding from changing canonical kilograms.

- [ ] **Step 4: Localize reason codes without putting strings in the engine**

`ProgressionReasonText` maps every `ProgressionReasonCode` exhaustively to a string resource plus formatted numeric arguments. Include the configured step/unit in load-change explanations, the highest RPE in RPE explanations, and the expected/actual weight in deviation explanations. A missing required argument uses a generic fail-closed explanation, never a fabricated number.

- [ ] **Step 5: Implement the review screen**

The screen contains a top app bar, an optional `Alle sicheren übernehmen` button enabled only when at least one item is `PENDING`, and one card per item. Every card shows scheme, counted work sets, localized reason, and all changed target dimensions as `alt → neu`. `PENDING` cards show `Übernehmen`, `Bearbeiten`, and `Verwerfen`; `INFORMATIONAL`, `ACCEPTED`, `REJECTED`, and `STALE` cards show status text only.

`Bearbeiten` opens a sheet with sets, reps, and weight in the captured current unit. The ViewModel parses and validates dirty fields before calling the repository and writes field errors back to the draft; the composable only renders those inline errors. Closing the sheet discards an unconfirmed draft, and closing the screen leaves pending rows untouched.

- [ ] **Step 6: Bind the ViewModel and run the targeted test**

```kotlin
viewModelOf(::ProgressionReviewViewModel)
```

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.progression.ProgressionReviewViewModelTest" :feature:progression:compileDebugKotlin`

Expected: PASS with informational action suppression, safe batch selection, unit conversion, and stale refresh tests.

- [ ] **Step 7: Commit the review feature**

```bash
git add settings.gradle.kts app/build.gradle.kts feature/progression core/designsystem/src/main/res/values/strings.xml app/src/main/java/com/ironlog/app/di/AppModule.kt app/src/test/java/com/ironlog/app/presentation/progression/ProgressionReviewViewModelTest.kt
git commit -m "feat: add progression review screen"
```

### Task 10: Separate workout completion from coach generation and navigation

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt`
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/ironlog/app/presentation/navigation/Screen.kt`
- Modify: `app/src/main/java/com/ironlog/app/presentation/navigation/NavHost.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/ironlog/app/presentation/navigation/NavigationSmokeTest.kt`

**Interfaces:**
- Consumes: Task 5 generation and Task 9 review screen.
- Produces: durable finish state, retry/skip behavior, and `progression_review` navigation.

- [ ] **Step 1: Add failing finish-order and retry tests**

```kotlin
@Test
fun `workout is finished before progression generation starts`() = runTest {
    progressionRepository.generateGate = CompletableDeferred()
    viewModel.finishWorkout()
    advanceUntilIdle()
    assertEquals(listOf("finish:9", "generate:9"), callLog.take(2))
    assertNotNull(workoutRepository.session.endTime)
}

@Test
fun `generation failure leaves workout completed and exposes retry plus later`() = runTest {
    progressionRepository.generateError = IOException("disk")
    viewModel.finishWorkout()
    advanceUntilIdle()
    assertNotNull(workoutRepository.session.endTime)
    assertTrue(viewModel.uiState.value.finishState is WorkoutFinishState.GenerationFailed)
}

@Test
fun `generation retry does not finish workout a second time`() = runTest {
    progressionRepository.generateError = IOException("disk")
    viewModel.finishWorkout()
    advanceUntilIdle()
    progressionRepository.generateError = null
    viewModel.retryProgressionGeneration()
    advanceUntilIdle()
    assertEquals(1, workoutRepository.finishCalls)
    assertEquals(2, progressionRepository.generateCalls)
}

@Test
fun `restored completed session locks source sets before recovery generation`() = runTest {
    workoutRepository.session = completedSession(id = 9)
    progressionRepository.generateGate = CompletableDeferred()
    recreateViewModel()
    advanceUntilIdle()

    viewModel.updateSet(setId = 1, reps = 12, weightKg = 110.0)
    viewModel.deleteSet(setId = 1)
    advanceUntilIdle()

    assertEquals(0, workoutRepository.updateCalls)
    assertEquals(0, workoutRepository.deleteCalls)
    assertTrue(viewModel.uiState.value.finishState is WorkoutFinishState.Generating)
}
```

- [ ] **Step 2: Run the targeted ViewModel test**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest"`

Expected: FAIL because `finishWorkout` currently treats persistence and navigation as one boolean state.

- [ ] **Step 3: Replace `workoutFinished` with an explicit finish state**

```kotlin
sealed interface WorkoutFinishState {
    data object Idle : WorkoutFinishState
    data object Completing : WorkoutFinishState
    data object Generating : WorkoutFinishState
    data class ReviewReady(val sessionId: Long) : WorkoutFinishState
    data object CompletedWithoutReview : WorkoutFinishState
    data class GenerationFailed(val sessionId: Long, val message: String) : WorkoutFinishState
}
```

For a non-empty workout, call `workoutRepository.finishWorkout(sessionId)` first, then set `Generating`, then call `generateOutcomesForSession`. Set `ReviewReady` when `reviewItemCount > 0`, otherwise `CompletedWithoutReview`. Catch generation separately and set `GenerationFailed`; never call `finishWorkout` again from `retryProgressionGeneration`. Empty sessions retain their existing delete behavior and finish as `CompletedWithoutReview`.

Treat a persisted non-null `session.endTime` as a hard mutation lock before starting recovery generation. `logSet`, `updateSet`, and `deleteSet` must re-read the session inside `mutationMutex` and return without repository mutation when it is missing or completed; the in-memory finish state alone is not sufficient after process recreation. Because this lock makes evaluated sets immutable and session deletion cascades both sets and suggestions, the stored counted-set IDs remain reproducible evidence.

- [ ] **Step 4: Replace auto-navigation from session end time**

Delete the `LaunchedEffect` condition `state.workoutFinished || session?.endTime != null`. Instead:

```kotlin
LaunchedEffect(state.finishState) {
    when (val finish = state.finishState) {
        is WorkoutFinishState.ReviewReady -> onProgressionReview(finish.sessionId)
        WorkoutFinishState.CompletedWithoutReview -> onWorkoutFinished()
        else -> Unit
    }
}
```

Show a non-cancelable error dialog for `GenerationFailed` with `Erneut versuchen` calling `retryProgressionGeneration()` and `Später` calling `onWorkoutFinished()`. A restored screen whose session already has `endTime` invokes generation once rather than navigating solely because the workout ended.

- [ ] **Step 5: Add the review route and graph destination**

```kotlin
data object ProgressionReview : Screen("progression_review?sessionId={sessionId}") {
    fun createRoute(sessionId: Long? = null): String = "progression_review?sessionId=${sessionId ?: 0L}"
}
```

Declare its `sessionId` argument as `LongType` with default `0L`. Change `ActiveWorkoutScreen` to expose `onProgressionReview: (Long) -> Unit`; navigate to the session route after generation and pop back to Dashboard when review closes. Dashboard navigation to `sessionId = null` is added in Task 11.

- [ ] **Step 6: Extend navigation smoke coverage**

Add a test that mounts `IronLogNavHost`, navigates to `Screen.ProgressionReview.createRoute(0)`, and asserts the review top-bar test tag. The fake Koin graph must bind `ProgressionRepository` and `ProgressionReviewViewModel` dependencies.

- [ ] **Step 7: Run unit tests and compile navigation instrumentation**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest" :app:compileDebugAndroidTestKotlin`

Expected: unit tests PASS and navigation instrumentation compiles. The remote command for the new smoke method must report 1 executed test.

- [ ] **Step 8: Commit finish and navigation flow**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt app/src/main/java/com/ironlog/app/presentation/navigation/Screen.kt app/src/main/java/com/ironlog/app/presentation/navigation/NavHost.kt app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt app/src/androidTest/java/com/ironlog/app/presentation/navigation/NavigationSmokeTest.kt
git commit -m "feat: review progression after workout completion"
```

### Task 11: Surface pending work and retry missing evaluations on Dashboard

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt`
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/ironlog/app/presentation/navigation/NavHost.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: pending count, stale reconciliation, missing generation, and all-pending review route.
- Produces: process-restart recovery and a persistent pending-suggestion entry point.

- [ ] **Step 1: Add failing Dashboard tests**

```kotlin
@Test
fun `pending suggestion count is exposed in dashboard state`() = runTest {
    progressionRepository.pendingCount.value = 3
    assertEquals(3, viewModel.uiState.first { it.pendingProgressionCount == 3 }.pendingProgressionCount)
}

@Test
fun `dashboard startup reconciles stale rows before retrying missing outcomes`() = runTest {
    advanceUntilIdle()
    assertEquals(listOf("reconcile", "generateMissing"), progressionRepository.callLog.take(2))
}

@Test
fun `coach startup failure does not hide ordinary dashboard data`() = runTest {
    progressionRepository.startupError = IOException("disk")
    val state = viewModel.uiState.first { !it.isLoading }
    assertNotNull(state.weeklyStats)
    assertEquals(0, state.pendingProgressionCount)
}
```

- [ ] **Step 2: Run the Dashboard test**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest"`

Expected: FAIL because Dashboard has no `ProgressionRepository` dependency or pending count.

- [ ] **Step 3: Reconcile and retry without blocking Dashboard content**

Inject `ProgressionRepository`, add `pendingProgressionCount: Int = 0` to `DashboardUiState`, combine `observePendingCount()` with existing state, and launch this independent startup job:

```kotlin
viewModelScope.launch {
    runCatching {
        progressionRepository.reconcileOutstandingSuggestions()
        progressionRepository.generateMissingOutcomes()
    }.onFailure { error ->
        AppLogger.w("DashboardVM", "Progression recovery failed: ${error.message}", error)
    }
}
```

Do not set the Dashboard's ordinary `isLoading` or full-screen error from this recovery job.

- [ ] **Step 4: Add the pending card and route**

When count is positive, show one compact card near the active-workout area: `N Progressionsvorschläge offen` with action `Prüfen`. Add `onOpenProgressionReview: () -> Unit` to `DashboardScreen` and navigate with `Screen.ProgressionReview.createRoute(null)`. Counts `0` render no card; informational, accepted, rejected, and stale rows are already excluded by the DAO query.

- [ ] **Step 5: Run the Dashboard test and compile UI**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest" :feature:dashboard:compileDebugKotlin`

Expected: PASS with ordered startup calls and non-blocking failure behavior.

- [ ] **Step 6: Commit Dashboard recovery**

```bash
git add feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt app/src/main/java/com/ironlog/app/presentation/navigation/NavHost.kt app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt core/designsystem/src/main/res/values/strings.xml
git commit -m "feat: surface pending progression reviews"
```

### Task 12: Make backup, import, recovery, and reset progression-complete

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ironlog/shared/backup/BackupPayloadV1.kt`
- Modify: `shared/src/commonMain/kotlin/com/ironlog/shared/backup/BackupPayloadValidator.kt`
- Modify: `shared/src/commonTest/kotlin/com/ironlog/shared/backup/BackupPayloadValidatorTest.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/backup/BackupPayloadV1.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/backup/BackupSnapshot.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/BackupRepositoryImpl.kt`
- Modify: `data/src/test/java/com/ironlog/app/data/repository/BackupRepositoryImplTest.kt`
- Modify: `data/src/test/java/com/ironlog/app/data/repository/BackupWorkoutSetRoundTripTest.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/repository/BackupRepository.kt`
- Modify: `app/src/main/java/com/ironlog/app/di/AppModule.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/backup/BackupPayloadValidatorTest.kt`
- Modify: `app/src/androidTest/java/com/ironlog/app/data/backup/BackupLifecycleRoundTripTest.kt`

**Interfaces:**
- Consumes: schema-11 targets and suggestions, shared V1 serialization, and the existing verified-hash/recovery transaction.
- Produces: backward-compatible V1 JSON that preserves all 11 domain tables, validates the full progression evidence graph, and restores it in foreign-key-safe order.

- [ ] **Step 1: Write failing shared compatibility and integrity tests**

Set both validator test constants to schema `11`, retain the existing raw schema-10 JSON fixture, and add these assertions:

```kotlin
@Test
fun `legacy schema ten json defaults progression to manual and empty evidence`() {
    val legacy = json.decodeFromString<BackupPayloadV1>(legacySchemaTenJson)
    assertEquals("MANUAL", legacy.planExercises.single().progression.scheme)
    assertNull(legacy.workoutSets.single().planTargetSnapshotId)
    assertTrue(legacy.workoutPlanTargets.isEmpty())
    assertTrue(legacy.progressionSuggestions.isEmpty())
    assertTrue(BackupPayloadValidator.validate(legacy, CURRENT_SCHEMA_VERSION).isValid)
}

@Test
fun `schema eleven progression payload survives json round trip`() {
    val source = validPayloadWithProgression()
    val decoded = json.decodeFromString<BackupPayloadV1>(json.encodeToString(source))
    assertEquals(source.workoutPlanTargets, decoded.workoutPlanTargets)
    assertEquals(source.progressionSuggestions, decoded.progressionSuggestions)
    assertEquals(source.workoutSets.single().planTargetSnapshotId, decoded.workoutSets.single().planTargetSnapshotId)
}

@Test
fun `validator rejects dangling or cross position progression evidence`() {
    val valid = validPayloadWithProgression()
    val brokenSetReference = valid.copy(
        workoutSets = valid.workoutSets.map { it.copy(planTargetSnapshotId = 999L) }
    )
    val brokenSuggestionEvidence = valid.copy(
        progressionSuggestions = valid.progressionSuggestions.map { it.copy(countedSetIds = listOf(999L)) }
    )
    val duplicatedRevision = valid.copy(
        progressionSuggestions = valid.progressionSuggestions + valid.progressionSuggestions.single().copy(id = 99L)
    )

    assertFalse(BackupPayloadValidator.validate(brokenSetReference, 11).isValid)
    assertFalse(BackupPayloadValidator.validate(brokenSuggestionEvidence, 11).isValid)
    assertFalse(BackupPayloadValidator.validate(duplicatedRevision, 11).isValid)
}

@Test
fun `validator rejects unknown non finite or inconsistent progression metadata`() {
    val valid = validPayloadWithProgression()
    val suggestion = valid.progressionSuggestions.single()
    val unknownReason = valid.copy(
        progressionSuggestions = listOf(suggestion.copy(reasonCode = "NOT_A_REASON"))
    )
    val nonFiniteArgument = valid.copy(
        progressionSuggestions = listOf(suggestion.copy(reasonArguments = mapOf("actualReps" to Double.NaN)))
    )
    val informationalPending = valid.copy(
        progressionSuggestions = listOf(
            suggestion.copy(outcomeType = "KEEP_TARGET", reasonCode = "REPEAT_TARGET", suggestedTarget = null, status = "PENDING")
        )
    )
    val incompleteEvidence = valid.copy(
        progressionSuggestions = listOf(suggestion.copy(countedSetIds = suggestion.countedSetIds.dropLast(1)))
    )

    assertFalse(BackupPayloadValidator.validate(unknownReason, 11).isValid)
    assertFalse(BackupPayloadValidator.validate(nonFiniteArgument, 11).isValid)
    assertFalse(BackupPayloadValidator.validate(informationalPending, 11).isValid)
    assertFalse(BackupPayloadValidator.validate(incompleteEvidence, 11).isValid)
}
```

- [ ] **Step 2: Run the shared validator test and observe missing fields**

Run: `./gradlew --no-daemon :shared:testAndroidHostTest --tests "com.ironlog.shared.backup.BackupPayloadValidatorTest"`

Expected: FAIL because the transport types do not yet contain progression data and the current schema constant is `10`.

- [ ] **Step 3: Extend the V1 transport with legacy-safe defaults**

Append these payload lists with defaults after the existing `metaPlanSkips` field and add the exact serializable types below:

```kotlin
val metaPlanSkips: List<BackupMetaPlanSkip> = emptyList(),
val workoutPlanTargets: List<BackupWorkoutPlanTarget> = emptyList(),
val progressionSuggestions: List<BackupProgressionSuggestion> = emptyList()

@Serializable
data class BackupProgressionConfig(
    val scheme: String = "MANUAL",
    val incrementValue: Double? = null,
    val incrementUnit: String? = null,
    val incrementKg: Double? = null,
    val minReps: Int? = null,
    val maxReps: Int? = null,
    val targetTotalReps: Long? = null,
    val targetRpe: Double? = null,
    val rpeTolerance: Double? = null,
    val stallThreshold: Int = 2,
    val backoffPercent: Double = 10.0,
    val ruleRevision: Int = 1
)

@Serializable
data class BackupProgressionTarget(val sets: Int, val reps: Int, val weightKg: Double)

@Serializable
data class BackupWorkoutPlanTarget(
    val id: Long,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val target: BackupProgressionTarget,
    val progression: BackupProgressionConfig
)

@Serializable
data class BackupProgressionSuggestion(
    val id: Long,
    val sourceSessionId: Long,
    val sourceTargetSnapshotId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val sourceTarget: BackupProgressionTarget,
    val sourceProgression: BackupProgressionConfig,
    val outcomeType: String,
    val reasonCode: String,
    val reasonArguments: Map<String, Double> = emptyMap(),
    val countedSetIds: List<Long> = emptyList(),
    val streakEffect: String,
    val suggestedTarget: BackupProgressionTarget? = null,
    val status: String,
    val wasEdited: Boolean = false,
    val finalTarget: BackupProgressionTarget? = null,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long? = null
)
```

Append `progression: BackupProgressionConfig = BackupProgressionConfig()` to `BackupPlanExercise` and `planTargetSnapshotId: Long? = null` to `BackupWorkoutSet`. Keep `formatVersion = 1`; only `schemaVersion` becomes `11`. Add Android-side typealiases for all four new transport types.

- [ ] **Step 4: Validate the complete progression graph in shared code**

Extend `BackupPayloadValidator` with these fail-closed checks, using literal enum-name sets because `shared` must not depend on Android `core:model`:

- IDs are positive and unique for targets and suggestions; target/suggestion order indices are non-negative, `(sessionId, orderIndex)` is unique for targets, and `(sourceTargetSnapshotId, sourceProgression.ruleRevision)` is unique for suggestions.
- Every target references an existing completed or active session, plan, and exercise; the session's non-null `planId` equals the target plan.
- Every non-null set snapshot exists and matches the set's session and exercise.
- Every suggestion references a completed source session plus a target, plan, and exercise that exactly match its duplicated identity, target, and progression values; active sessions may own snapshots but never outcomes.
- Counted set IDs are unique, exist, reference the suggestion's source snapshot, and match its session and exercise.
- Schemes are one of `MANUAL`, `LINEAR`, `DOUBLE`, `TOTAL_REPS`, `RPE_RIR`; active configs require all and only their scheme-specific nullable fields and satisfy the same finite/range rules as `ProgressionConfigValidator`. `MANUAL` requires every scheme-specific field to be null, while shared threshold/backoff/revision defaults remain present.
- Targets require positive sets/reps and finite non-negative weight; rule revisions are positive.
- Use these literal locked sets: outcomes `{PROPOSE_CHANGE, KEEP_TARGET, INSUFFICIENT_DATA}`; reasons `{REP_TARGET_ADVANCED, LOAD_ADVANCED, TOTAL_REPS_COMPLETED, RPE_WITHIN_TARGET, REPEAT_TARGET, STALL_BACKOFF, BACKOFF_FLOOR_REACHED, MANUAL_WEIGHT_DEVIATION, TOO_FEW_WORK_SETS, RPE_MISSING, RPE_INVALID, CONFIG_INVALID, RULE_REVISION_UNSUPPORTED, SET_NUMBER_INVALID, SET_VALUE_INVALID}`; streak effects `{INCREMENT, RESET, IGNORE}`; statuses `{PENDING, ACCEPTED, REJECTED, STALE, INFORMATIONAL}`. Reject stored `NOT_APPLICABLE`/`MANUAL_SCHEME` rows because manual targets are never persisted as outcomes.
- Lock reason-argument keys to `{expectedWeightKg, actualWeightKg, targetSets, actualWorkSets, targetReps, actualReps, achievedTotalReps, targetTotalReps, highestRpe, targetRpe, tolerance, stepOriginalValue, backoffPercent}` and require every value to be finite. Unknown keys fail validation; missing presentation arguments are allowed because the review already falls back to a generic explanation and the full result remains reproducible from its source snapshot plus evidence sets.
- Enforce the outcome/status matrix: `PROPOSE_CHANGE` requires a valid `suggestedTarget` and one of `PENDING/ACCEPTED/REJECTED/STALE`; `KEEP_TARGET` and `INSUFFICIENT_DATA` require `INFORMATIONAL` and no suggested target. Proposed and kept outcomes must link exactly `sourceTarget.sets` evidence sets; insufficient-data outcomes may link any deterministic subset or superset made available by their reason.
- `PENDING` and `INFORMATIONAL` have no decision time or final target. `ACCEPTED` requires a valid `finalTarget` and decision time, with `wasEdited == (finalTarget != suggestedTarget)`; `REJECTED` and `STALE` require a decision time and no final target. Every non-accepted row has `wasEdited = false`; all timestamps are non-negative and a decision time cannot precede creation.
- Schema versions below `11` must have empty progression lists and default manual/null additions; schema `11` may carry the new graph.

Run: `./gradlew --no-daemon :shared:testAndroidHostTest --tests "com.ironlog.shared.backup.BackupPayloadValidatorTest"`

Expected: PASS, including legacy schema-10 decoding, schema-11 round-trip, duplicate composite keys, dangling IDs, cross-session/exercise links, invalid configs, and status/target mismatches.

- [ ] **Step 5: Add the new tables to snapshots, counts, and lossless mapping**

Append `workoutPlanTargets` and `progressionSuggestions` to `BackupSnapshot`, both payload builders, and `ImportData`. Add defaulted fields to avoid breaking existing callers:

```kotlin
data class BackupContentCounts(
    val exercises: Int,
    val workoutSessions: Int,
    val workoutSets: Int,
    val trainingPlans: Int,
    val planExercises: Int,
    val personalRecords: Int,
    val metaTrainingPlans: Int,
    val metaPlanItems: Int,
    val metaPlanSkips: Int,
    val workoutPlanTargets: Int = 0,
    val progressionSuggestions: Int = 0
)
```

Set `BackupRepositoryImpl.SCHEMA_VERSION = 11`, inject `ProgressionDao`, and include `progressionDao.getAllTargets()` and `getAllSuggestions()` in `readSnapshotBlock`. Add explicit entity/transport mappers for flat targets, configs, and suggestions; sort reason-argument map keys before both transport serialization and `reasonArgumentsJson` encoding, while preserving `countedSetIds` order, and decode with the repository's strict `Json` instance. Update `WorkoutSetEntity` and `PlanExerciseEntity` mapping so snapshot ID and full progression config survive export/import.

The canonical payload must include both new lists, so recovery hashes change whenever a suggestion, its decision state, its evidence IDs, or a session target changes. `toCounts()` must return both new counts.

- [ ] **Step 6: Restore and reset in foreign-key-safe order**

Use this exact delete order in `deleteAllInOrder()` and `resetUserData()`:

```kotlin
progressionDao.deleteAllSuggestions()
personalRecordDao.deleteAll()
workoutSetDao.deleteAll()
progressionDao.deleteAllTargets()
metaTrainingPlanDao.deleteAllMetaPlanSkips()
metaTrainingPlanDao.deleteAllMetaPlanItems()
trainingPlanDao.deleteAllPlanExercises()
workoutSessionDao.deleteAll()
metaTrainingPlanDao.deleteAllMetaPlans()
trainingPlanDao.deleteAllPlans()
exerciseDao.deleteAll() // resetUserData keeps its existing deleteAllCustomExercises variant
```

Use this insert order after validation and the verified recovery snapshot:

```kotlin
exerciseDao.replaceAll(data.exercises.ifEmpty { ExerciseSeedData.getAll() })
trainingPlanDao.replaceAllPlans(data.trainingPlans)
metaTrainingPlanDao.replaceAllMetaPlans(data.metaTrainingPlans)
workoutSessionDao.replaceAll(data.workoutSessions)
trainingPlanDao.replaceAllExercises(data.planExercises)
progressionDao.replaceAllTargets(data.workoutPlanTargets)
metaTrainingPlanDao.replaceAllItems(data.metaPlanItems)
metaTrainingPlanDao.replaceAllMetaPlanSkips(data.metaPlanSkips)
workoutSetDao.replaceAll(data.workoutSets)
progressionDao.replaceAllSuggestions(data.progressionSuggestions)
personalRecordDao.replaceAll(data.personalRecords)
```

Keep each existing `isNotEmpty()` guard where DAO methods do not accept an empty list. Do not weaken the existing exact-byte hash re-read, concurrent-modification check, or one-transaction replacement behavior.

- [ ] **Step 7: Prove repository mapping and recovery parity**

Extend `BackupWorkoutSetRoundTripTest` to assert a non-null `planTargetSnapshotId` survives entity → JSON → entity. Extend `BackupRepositoryImplTest` with a source snapshot and one pending suggestion and assert:

```kotlin
assertEquals(1, preview.counts.workoutPlanTargets)
assertEquals(1, preview.counts.progressionSuggestions)
coVerifyOrder {
    progressionDao.deleteAllSuggestions()
    workoutSetDao.deleteAll()
    progressionDao.deleteAllTargets()
    progressionDao.replaceAllTargets(any())
    workoutSetDao.replaceAll(any())
    progressionDao.replaceAllSuggestions(any())
}
```

Also verify a validation or hash failure invokes none of the progression delete/replace methods, and `resetUserData()` removes suggestions before targets. Wire `ProgressionDao` into `BackupRepositoryImpl` in `AppModule` and all test harnesses.

Run: `./gradlew --no-daemon :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.BackupRepositoryImplTest" --tests "com.ironlog.app.data.repository.BackupWorkoutSetRoundTripTest"`

Expected: PASS with canonical export, preview counts, verified import, recovery, reset, and snapshot-ID round-trip covered.

- [ ] **Step 8: Extend the real Room lifecycle gate from nine to eleven tables**

Update `BackupLifecycleRoundTripTest` to seed one linear plan config, its session target, three linked work sets, and one pending suggestion with those exact counted IDs. Rename the main method to `exportMutateImport_restoresAllElevenTablesWithCanonicalParityAndFkIntegrity`, mutate/delete the new rows between export and import, and assert after import and recovery:

```kotlin
assertEquals(payload.workoutPlanTargets.size, preview.counts.workoutPlanTargets)
assertEquals(payload.progressionSuggestions.size, preview.counts.progressionSuggestions)
assertEquals(exportedPayload.workoutPlanTargets, restoredPayload.workoutPlanTargets)
assertEquals(exportedPayload.progressionSuggestions, restoredPayload.progressionSuggestions)
database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
```

Run locally: `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin`

Run in CI/emulator: `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironlog.app.data.backup.BackupLifecycleRoundTripTest`

Expected: local compilation PASS; remote execution PASS with the existing lifecycle methods and at least one executed test.

- [ ] **Step 9: Commit backup schema 11 support**

```bash
git add shared/src/commonMain/kotlin/com/ironlog/shared/backup shared/src/commonTest/kotlin/com/ironlog/shared/backup/BackupPayloadValidatorTest.kt data/src/main/java/com/ironlog/app/data/backup data/src/main/java/com/ironlog/app/data/repository/BackupRepositoryImpl.kt data/src/test/java/com/ironlog/app/data/repository/BackupRepositoryImplTest.kt data/src/test/java/com/ironlog/app/data/repository/BackupWorkoutSetRoundTripTest.kt core/model/src/main/java/com/ironlog/app/domain/repository/BackupRepository.kt app/src/main/java/com/ironlog/app/di/AppModule.kt app/src/test/java/com/ironlog/app/data/backup/BackupPayloadValidatorTest.kt app/src/androidTest/java/com/ironlog/app/data/backup/BackupLifecycleRoundTripTest.kt
git commit -m "feat: preserve progression data in backups"
```

### Task 13: Prove the complete coach lifecycle and build the Android app

**Files:**
- Create: `app/src/androidTest/java/com/ironlog/app/data/local/ProgressionCoachLifecycleTest.kt`

**Interfaces:**
- Consumes: the real schema-11 Room database, production workout and progression repositories, revision-1 engine, and atomic acceptance path.
- Produces: one deterministic integration proof for `plan → snapshot → linked sets → completed workout → review → confirmation → reopened plan` plus a final debug APK build.

- [ ] **Step 1: Write the failing lifecycle instrumentation test**

Use `Room.inMemoryDatabaseBuilder`, `RoomTransactionRunner`, `WorkoutRepositoryImpl`, `ProgressionRepositoryImpl`, and `TrainingPlanRepositoryImpl`; no DAO or engine fake is allowed. Seed one exercise and a plan whose only position is `3 × 8 @ 100 kg` with linear `2.5 kg`, then execute this exact flow:

```kotlin
@Test
fun acceptedLinearSuggestionUpdatesReopenedPlanButPreservesSourceSnapshot() = runTest {
    val planId = trainingPlanRepository.savePlan(linearPlan(weightKg = 100.0, stepKg = 2.5))
    val sessionId = workoutRepository.startWorkout("Progression lifecycle", planId, null)
    val source = database.progressionDao().getTargetsForSession(sessionId).single()

    val setIds = (1..3).map { setNumber ->
        workoutRepository.addSet(
            workoutSet(
                sessionId = sessionId,
                exerciseId = source.exerciseId,
                setNumber = setNumber,
                reps = 8,
                weightKg = 100.0,
                snapshotId = source.id
            )
        )
    }
    workoutRepository.finishWorkout(sessionId)

    val generated = progressionRepository.generateOutcomesForSession(sessionId)
    assertEquals(1, generated.reviewItemCount)
    assertEquals(1, generated.pendingCount)
    val repeated = progressionRepository.generateOutcomesForSession(sessionId)
    assertEquals(0, repeated.insertedCount)
    assertEquals(1, progressionRepository.observeReviewItems(sessionId).first().size)
    val suggestion = progressionRepository.observeReviewItems(sessionId).first().single()
    assertEquals(setIds, suggestion.countedSets.map { it.id })
    assertEquals(102.5, requireNotNull((suggestion.outcome as ProgressionOutcome.ProposeChange).proposedTarget).weightKg, 0.000001)

    val accepted = progressionRepository.acceptSuggestions(
        mapOf(suggestion.id to (suggestion.outcome as ProgressionOutcome.ProposeChange).proposedTarget)
    )
    assertEquals(ProgressionDecisionResult.Accepted(setOf(suggestion.id)), accepted)

    val reopened = requireNotNull(trainingPlanRepository.getPlanById(planId))
    assertEquals(102.5, reopened.exercises.single().targetWeightKg, 0.000001)
    assertEquals(100.0, database.progressionDao().getTargetsForSession(sessionId).single().target.weightKg, 0.0)
    assertEquals(ProgressionSuggestionStatus.ACCEPTED, progressionRepository.observeReviewItems(sessionId).first().single().status)
}
```

The fixture's `nowEpochMillis` is monotonic and fixed by the test, `completedAt` values increase with `setNumber`, and teardown closes the database. The repeated generation assertions prove lifecycle idempotency without adding another broad test.

- [ ] **Step 2: Compile instrumentation and observe the missing test helpers or integration defects**

Run: `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin`

Expected before the test is complete: FAIL on any missing production wiring or fixture helper; after implementing the exact harness: PASS.

- [ ] **Step 3: Run the lifecycle proof in the emulator gate**

Run in CI/emulator: `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironlog.app.data.local.ProgressionCoachLifecycleTest`

Expected: PASS, 1 test executed. A zero-test result, instrumentation acknowledgement, or compile-only result is not acceptance evidence.

- [ ] **Step 4: Build the final Android debug artifact once**

Run: `./gradlew --no-daemon :app:assembleDebug`

Expected: PASS and `app/build/outputs/apk/debug/app-debug.apk` exists. Do not rerun the targeted tests already recorded in Tasks 1–12 merely to duplicate evidence.

- [ ] **Step 5: Check the existing remote PR gates before merge readiness**

Confirm the branch's existing GitHub checks report success for `test`, `lintDebug`, `assembleDebug`, and `connectedDebugAndroidTest`. Inspect the connected-test log and require a non-zero executed-test count that includes the migration, backup lifecycle, navigation smoke, snapshot transaction, and progression lifecycle classes. Do not claim merge or release readiness from local compilation alone.

- [ ] **Step 6: Commit the lifecycle gate**

```bash
git add app/src/androidTest/java/com/ironlog/app/data/local/ProgressionCoachLifecycleTest.kt
git commit -m "test: cover progression coach lifecycle"
```

---

## Completion Evidence

Implementation is complete only when every task checkbox has direct evidence, the final APK exists, the lifecycle test has executed remotely, and all four required PR gates are green. Report any locally unavailable emulator checks separately from passing local unit/compile/build checks; do not collapse them into a generic success claim.
