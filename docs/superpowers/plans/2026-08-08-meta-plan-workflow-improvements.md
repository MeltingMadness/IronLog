# Meta-Plan Workflow Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the dashboard streak, prioritize meta plans at workout start, persist safe meta-plan skips, separate weight history by context by default, and show whether the previous final work set met the current targets.

**Architecture:** Android remains the product surface. Room stores skip events, a shared domain resolver calculates rotation consistently, DataStore stores the history-sharing preference, and `PreviousSessionScope` makes each history query explicit. The active-workout ViewModel derives both suggestions and the target indicator from the same previous-session result.

**Tech Stack:** Kotlin 2.3.10, Jetpack Compose, Room, DataStore Preferences, Kotlin Coroutines/Flow, Koin, JUnit/MockK, Gradle/AGP 9.0.0.

## Global Constraints

- Android only; do not add iOS UI or shared workout-controller parity.
- The dashboard streak and its dedicated data path are deleted permanently.
- Meta plans appear before normal plans only in the “Training starten” sheet; ordering within each group stays unchanged.
- A skip is durable, survives backup/restore, advances rotation, and never creates a `WorkoutSession`.
- “Gewichte zwischen Einzel- und Meta-Plänen teilen” defaults to `false`; `true` restores the current plan-ID-only sharing behavior.
- The previous-set indicator requires current target weight and reps greater than zero and compares the last non-warmup set against both targets.
- Preserve unrelated work and do not redesign adjacent screens.
- Use only the smallest targeted tests for each task; the final local gate is the focused unit set plus `:app:assembleDebug`.

---

## File Map and Execution Order

Phase 1 can run in parallel after Task 1: Task 2 owns preferences/history files; Task 4 owns meta-skip persistence and rotation files. Task 3 follows Task 2, Task 5 follows Task 4, and Task 6 follows Tasks 1 and 4. Task 7 integrates everything.

- Dashboard cleanup and ordering: `DashboardScreen.kt`, `DashboardViewModel.kt`, `PlanSelectionSheet.kt`, dashboard tests and streak strings.
- History scope and setting: `PreviousSessionScope.kt`, preferences models/repositories/adapters, `WorkoutSetDao.kt`, `WorkoutRepository*`, settings UI and tests.
- Previous-target indicator: `ActiveWorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, one string and focused workout tests.
- Skip persistence and rotation: `MetaPlanSkipEntity.kt`, `MetaTrainingPlanDao.kt`, database migration, `MetaPlanRotation.kt`, meta repository and tests.
- Backup compatibility: shared backup payload/validator, backup snapshot/repository/counts and backup tests.
- Skip presentation: dashboard/meta-list consumers, start-sheet button, strings and ViewModel tests.
- Integration: generated Room schema, compilation fixes, focused tests and debug APK.

---

### Task 1: Remove the streak and put meta plans first

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt`
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/PlanSelectionSheet.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/repository/WorkoutRepository.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSessionDao.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt`
- Modify: `app/src/test/java/com/ironlog/app/fakes/FakeWorkoutRepository.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Existing dashboard state, repository contracts and `PlanSelectionSheet` callbacks.
- Produces: A dashboard with no streak state or query and the sheet order Meta → Normal → Free Workout.

- [ ] **Step 1: Remove obsolete streak tests and keep a dashboard load regression test**

Delete tests that call `calculateStreak()` and remove `currentStreak` assertions. Retain or add this assertion to an existing dashboard-load test so removal cannot accidentally break the remaining metrics:

```kotlin
assertEquals(1, viewModel.uiState.value.workoutsThisWeek)
assertEquals(1, viewModel.uiState.value.workoutsThisMonth)
assertFalse(viewModel.uiState.value.isLoading)
```

- [ ] **Step 2: Run the dashboard test before production deletion**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest"
```

Expected: PASS after test-only deletion; this establishes that remaining dashboard behavior is still covered.

- [ ] **Step 3: Delete the streak production path**

Remove `StreakCard`, `DEFAULT_WEEKLY_WORKOUT_GOAL`, `DashboardUiState.currentStreak`, `workoutDaysThisWeek`, `weekStart`, `calculateStreak()`, and their assignments. Keep the local `weekAnchor`, because weekly-volume grouping still uses `preferences.weekStart`.

Delete this contract everywhere:

```kotlin
suspend fun getCompletedWorkoutStartTimesDesc(): List<Long>
```

Delete its Room query, real implementation and fake implementation. Remove the exclusively streak-related strings:

```xml
dashboard_streak
dashboard_streak_value
dashboard_streak_label
dashboard_weekly_progress
```

- [ ] **Step 4: Reorder the selection sheet**

Move the existing meta header and meta option block before `items(plans, ...)`. Preserve callbacks and group-internal list order. The resulting `LazyColumn` structure must be:

```kotlin
item(key = "meta_header")
items(metaPlanOptions, key = { it.metaPlanId })
item(key = "meta_plan_divider")
items(plans, key = { it.plan.id })
item(key = "normal_plan_divider")
item(key = "free_workout")
```

This block specifies stable list order and keys. Keep the current card bodies and divider styling unchanged; no new wrapper composable is needed.

- [ ] **Step 5: Verify the dashboard change**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest"
```

Expected: PASS and no production reference found by:

```bash
rg -n "StreakCard|currentStreak|calculateStreak|getCompletedWorkoutStartTimesDesc|dashboard_streak|dashboard_weekly_progress" feature core data app/src/test
```

Expected: no matches.

- [ ] **Step 6: Commit Task 1**

```bash
git add feature/dashboard core/model/src/main/java/com/ironlog/app/domain/repository/WorkoutRepository.kt core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSessionDao.kt data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt app/src/test/java/com/ironlog/app/fakes/FakeWorkoutRepository.kt app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt core/designsystem/src/main/res/values/strings.xml
git commit -m "Remove dashboard streak and prioritize meta plans"
```

---

### Task 2: Add explicit previous-session scopes and the sharing setting

**Files:**
- Create: `core/model/src/main/java/com/ironlog/app/domain/model/PreviousSessionScope.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/model/AppPreferences.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/repository/AppPreferencesRepository.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/repository/WorkoutRepository.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSetDao.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/preferences/AppPreferencesDataStore.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/preferences/AppPreferencesRepositoryImpl.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/WorkoutRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/ironlog/shared/model/PlatformTypes.kt`
- Modify: `shared/src/commonMain/kotlin/com/ironlog/shared/settings/SettingsPreferencesController.kt`
- Modify: `feature/settings/src/main/java/com/ironlog/app/presentation/settings/SharedSettingsAdapters.kt`
- Modify: `feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsScreen.kt`
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt`
- Modify: `app/src/test/java/com/ironlog/app/fakes/FakeAppPreferencesRepository.kt`
- Modify: `app/src/test/java/com/ironlog/app/fakes/FakeWorkoutRepository.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/repository/WorkoutRepositoryImplTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/settings/SettingsViewModelTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt`
- Modify: `data/src/test/java/com/ironlog/app/data/preferences/AppPreferencesDataStoreTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/ironlog/shared/settings/SettingsPreferencesControllerTest.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SavedStateHandle` route values `planId` and `metaPlanId`; existing previous-session mapping.
- Produces: `PreviousSessionScope`, `AppPreferences.shareWeightHistoryAcrossContexts`, `updateShareWeightHistoryAcrossContexts(Boolean)`, and a scope-based repository query.

- [ ] **Step 1: Write failing scope and preference tests**

Add repository tests verifying exact DAO dispatch:

```kotlin
repository.getPreviousSessionDataForExercises(99L, listOf(7L), PreviousSessionScope.NormalPlan(3L))
coVerify { setDao.getMostRecentCompletedSetsForNormalPlanExercises(99L, listOf(7L), 3L) }

repository.getPreviousSessionDataForExercises(99L, listOf(7L), PreviousSessionScope.MetaPlan(3L, 8L))
coVerify { setDao.getMostRecentCompletedSetsForMetaPlanExercises(99L, listOf(7L), 3L, 8L) }

repository.getPreviousSessionDataForExercises(99L, listOf(7L), PreviousSessionScope.SharedPlan(3L))
coVerify { setDao.getMostRecentCompletedSetsForPlanExercises(99L, listOf(7L), 3L) }
```

Add DataStore/default tests:

```kotlin
assertFalse(repository.preferences.first().shareWeightHistoryAcrossContexts)
repository.updateShareWeightHistoryAcrossContexts(true)
assertTrue(repository.preferences.first().shareWeightHistoryAcrossContexts)
```

Add active-workout cases for Normal vs Meta A vs Meta B and shared mode. Use completed historical sessions with the same `planId` but distinct `metaPlanId` values, then assert the selected `previousSession.sessionId`.

- [ ] **Step 2: Run focused tests to verify the new contract is missing**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest" --tests "com.ironlog.app.presentation.settings.SettingsViewModelTest" --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest" :data:testDebugUnitTest --tests "com.ironlog.app.data.preferences.AppPreferencesDataStoreTest"
```

Expected: FAIL to compile because the new scope and preference APIs do not exist.

- [ ] **Step 3: Introduce the scope type and repository signature**

Create:

```kotlin
sealed interface PreviousSessionScope {
    data object Global : PreviousSessionScope
    data class NormalPlan(val planId: Long) : PreviousSessionScope
    data class MetaPlan(val planId: Long, val metaPlanId: Long) : PreviousSessionScope
    data class SharedPlan(val planId: Long) : PreviousSessionScope
}
```

Change `WorkoutRepository` to:

```kotlin
suspend fun getPreviousSessionDataForExercises(
    currentSessionId: Long,
    exerciseIds: List<Long>,
    scope: PreviousSessionScope = PreviousSessionScope.Global
): Map<Long, PreviousExerciseSession>
```

Do not change `SharedWorkoutRepository`; it is outside the Android path and iOS parity is excluded.

- [ ] **Step 4: Add the two context-specific Room queries**

Copy the existing plan-scoped query twice. In both the outer query and correlated subquery add the exact context predicate:

```sql
-- getMostRecentCompletedSetsForNormalPlanExercises
AND s.planId = :planId AND s.metaPlanId IS NULL
AND s2.planId = :planId AND s2.metaPlanId IS NULL

-- getMostRecentCompletedSetsForMetaPlanExercises
AND s.planId = :planId AND s.metaPlanId = :metaPlanId
AND s2.planId = :planId AND s2.metaPlanId = :metaPlanId
```

Keep ordering `s2.startTime DESC, ws2.sessionId DESC` and final `exerciseId, setNumber ASC` unchanged.

Dispatch in `WorkoutRepositoryImpl` with an exhaustive `when (scope)` and keep the mapping to `PreviousExerciseSession` unchanged.

- [ ] **Step 5: Persist the setting through app and shared settings adapters**

Add to both app and shared `AppPreferences`:

```kotlin
val shareWeightHistoryAcrossContexts: Boolean = false
```

Add the key:

```kotlin
val SHARE_WEIGHT_HISTORY_ACROSS_CONTEXTS =
    booleanPreferencesKey("share_weight_history_across_contexts")
```

Read with `?: false`, add `updateShareWeightHistoryAcrossContexts(enabled: Boolean)` through app repository, shared repository/controller, Android adapter, ViewModel and both fakes. Ensure `toShared()` and `toApp()` copy the property.

- [ ] **Step 6: Resolve one scope for both history consumers**

In `ActiveWorkoutViewModel`, read:

```kotlin
private val metaPlanId: Long = savedStateHandle["metaPlanId"] ?: 0L
```

Include `appPreferencesRepository.preferences` in the `exercisesWithSets` flow so a setting change re-evaluates history without maintaining a second truth source. Resolve:

```kotlin
internal fun previousSessionScope(
    planId: Long,
    metaPlanId: Long,
    shareAcrossContexts: Boolean
): PreviousSessionScope = when {
    planId <= 0L -> PreviousSessionScope.Global
    shareAcrossContexts -> PreviousSessionScope.SharedPlan(planId)
    metaPlanId > 0L -> PreviousSessionScope.MetaPlan(planId, metaPlanId)
    else -> PreviousSessionScope.NormalPlan(planId)
}
```

Pass that scope to `getPreviousSessionDataForExercises`. Update the fake with the same four filters; do not make tests depend on a looser fake than production.

- [ ] **Step 7: Add the settings toggle and copy**

Place a `ToggleRow` in the existing preferences card:

```kotlin
ToggleRow(
    title = stringResource(R.string.settings_share_weight_history_title),
    subtitle = stringResource(R.string.settings_share_weight_history_subtitle),
    checked = state.preferences.shareWeightHistoryAcrossContexts,
    onCheckedChange = viewModel::updateShareWeightHistoryAcrossContexts
)
```

Add:

```xml
<string name="settings_share_weight_history_title">Gewichte zwischen Einzel- und Meta-Plänen teilen</string>
<string name="settings_share_weight_history_subtitle">Wenn aus, verwendet jeder Einzel- und Meta-Plan eine eigene Gewichtshistorie.</string>
```

- [ ] **Step 8: Run scope, preference and workout tests**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest" --tests "com.ironlog.app.presentation.settings.SettingsViewModelTest" --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest" :data:testDebugUnitTest --tests "com.ironlog.app.data.preferences.AppPreferencesDataStoreTest" :shared:testAndroidHostTest --tests "com.ironlog.shared.settings.SettingsPreferencesControllerTest"
```

Expected: PASS. If `testAndroidHostTest` is not present, discover the exact shared host task with `./gradlew :shared:tasks --all | rg -i "test.*Host"` and run only the matching controller test.

- [ ] **Step 9: Commit Task 2**

```bash
git add core/model core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSetDao.kt data/src feature/settings feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt shared/src app/src/test core/designsystem/src/main/res/values/strings.xml
git commit -m "Separate workout weight history by plan context"
```

---

### Task 3: Derive and display the previous-final-set indicator

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt`
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: The context-correct `PreviousExerciseSession` produced by Task 2 and existing `PlanTarget`.
- Produces: `PreviousExerciseSessionUi.lastWorkSetReachedTarget: Boolean` and its success indicator.

- [ ] **Step 1: Write failing helper and ViewModel tests**

Cover reached, reps missed, weight missed, zero target, warmup-only, and “last non-warmup wins”. Define one local builder in the test:

```kotlin
fun previousSet(reps: Int, weightKg: Double, warmup: Boolean = false) = WorkoutSet(
    sessionId = 1L,
    exerciseId = 2L,
    setNumber = 1,
    reps = reps,
    weightKg = weightKg,
    isWarmup = warmup
)

assertTrue(lastWorkSetReachedTarget(PlanTarget(targetReps = 8, targetWeightKg = 80.0), listOf(previousSet(8, 80.0))))
assertFalse(lastWorkSetReachedTarget(PlanTarget(targetReps = 8, targetWeightKg = 80.0), listOf(previousSet(7, 90.0))))
assertFalse(lastWorkSetReachedTarget(PlanTarget(targetReps = 8, targetWeightKg = 0.0), listOf(previousSet(8, 90.0))))
assertFalse(lastWorkSetReachedTarget(PlanTarget(targetReps = 8, targetWeightKg = 80.0), listOf(previousSet(8, 80.0, warmup = true))))
```

Use the existing test builders or explicit `WorkoutSet` values rather than adding duplicate fixtures.

- [ ] **Step 2: Run the workout test and see the missing helper failure**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest"
```

Expected: FAIL because the helper/property does not exist.

- [ ] **Step 3: Implement the pure comparison**

```kotlin
internal fun lastWorkSetReachedTarget(
    planTarget: PlanTarget?,
    previousSets: List<WorkoutSet>
): Boolean {
    val target = planTarget ?: return false
    if (target.targetReps <= 0 || target.targetWeightKg <= 0.0) return false
    val lastWorkSet = previousSets.lastOrNull { !it.isWarmup } ?: return false
    return lastWorkSet.reps >= target.targetReps &&
        lastWorkSet.weightKg >= target.targetWeightKg
}
```

Add `lastWorkSetReachedTarget: Boolean = false` to `PreviousExerciseSessionUi` and compute it in the existing mapping with the current `targets[exercise.id]`. Do not issue a second repository query.

- [ ] **Step 4: Render the compact success line**

In the existing `planTarget != null` target block, before the current-session “Ziel erreicht” message:

```kotlin
if (previousSession?.lastWorkSetReachedTarget == true) {
    Text(
        text = stringResource(R.string.workout_previous_last_set_target_reached),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.semantic.success
    )
}
```

Add:

```xml
<string name="workout_previous_last_set_target_reached">Letzter Satz: Ziel erreicht</string>
```

- [ ] **Step 5: Verify and commit Task 3**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest"
git add feature/workout app/src/test/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModelTest.kt core/designsystem/src/main/res/values/strings.xml
git commit -m "Show previous final-set target indicator"
```

Expected: PASS before commit.

---

### Task 4: Persist skips and centralize meta-plan rotation

**Files:**
- Create: `core/model/src/main/java/com/ironlog/app/domain/model/MetaPlanRotationEvent.kt`
- Create: `core/common/src/main/java/com/ironlog/app/domain/util/MetaPlanRotation.kt`
- Create: `core/common/src/test/java/com/ironlog/app/domain/util/MetaPlanRotationTest.kt`
- Create: `core/database/src/main/java/com/ironlog/app/data/local/entity/MetaPlanSkipEntity.kt`
- Create: `core/database/src/main/java/com/ironlog/app/data/local/dao/LastMetaPlanRotationEventRow.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/repository/MetaTrainingPlanRepository.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/dao/MetaTrainingPlanDao.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/IronLogDatabase.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/MetaTrainingPlanRepositoryImpl.kt`
- Modify: `app/src/test/java/com/ironlog/app/fakes/FakeMetaTrainingPlanRepository.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/repository/MetaTrainingPlanRepositoryImplTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/plans/MetaPlanListViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/ironlog/app/data/local/IronLogDatabaseMigrationTest.kt`
- Create: `app/src/androidTest/java/com/ironlog/app/data/local/MetaPlanSkipDaoTest.kt`

**Interfaces:**
- Consumes: Ordered `MetaTrainingPlan.items`, completed workout sessions and current time.
- Produces: `observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>>`, `skipCurrentSubPlan(metaPlanId: Long, expectedTrainingPlanId: Long): Boolean`, and `resolveMetaPlanRotation(orderedPlanIds: List<Long>, lastEventAtByPlanId: Map<Long, Long>): List<Long>`.

- [ ] **Step 1: Write failing pure rotation tests**

Create `MetaPlanRotationTest.kt` and cover never-used first, oldest event first, tie by item order, skip event newer than session, and ignoring removed plans:

```kotlin
val result = resolveMetaPlanRotation(
    orderedPlanIds = listOf(10L, 20L, 30L),
    lastEventAtByPlanId = mapOf(10L to 300L, 20L to 100L, 99L to 0L)
)
assertEquals(listOf(30L, 20L, 10L), result)
```

Here `30L` has no event and therefore comes first; `99L` is ignored because it is not in the ordered list.

- [ ] **Step 2: Write failing repository/transaction tests**

Test these observable outcomes:

```kotlin
assertTrue(repository.skipCurrentSubPlan(metaPlanId = 5L, expectedTrainingPlanId = 10L))
coVerify(exactly = 1) { dao.skipCurrentSubPlanIfCurrent(5L, 10L, any()) }

assertFalse(repository.skipCurrentSubPlan(metaPlanId = 5L, expectedTrainingPlanId = 20L))
coVerify(exactly = 0) { dao.insertMetaPlanSkip(match { it.trainingPlanId == 20L }) }
```

Create `MetaPlanSkipDaoTest.kt` with an in-memory Room database. It must prove: one-item meta plans return `false`, missing membership returns `false`, a second call with the same stale expected plan returns `false` after the first call advances rotation, equal incoming millisecond timestamps are normalized to increasing anchors, and the workout-session table remains unchanged.

- [ ] **Step 3: Implement the domain event and resolver**

```kotlin
data class MetaPlanRotationEvent(
    val trainingPlanId: Long,
    val metaPlanId: Long,
    val lastEventAt: Long
)

fun resolveMetaPlanRotation(
    orderedPlanIds: List<Long>,
    lastEventAtByPlanId: Map<Long, Long>
): List<Long> = orderedPlanIds
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<Long>> { lastEventAtByPlanId[it.value] != null }
            .thenBy { lastEventAtByPlanId[it.value] ?: Long.MIN_VALUE }
            .thenBy { it.index }
    )
    .map { it.value }
```

The Boolean comparison deliberately places missing events first. Do not treat a skip as “last trained” in UI copy; it is only a rotation event.

- [ ] **Step 4: Add the Room entity and aggregate queries**

Create `MetaPlanSkipEntity(id = 0, metaPlanId, trainingPlanId, skippedAt)` with CASCADE foreign keys to `MetaTrainingPlanEntity` and `TrainingPlanEntity`, plus individual and combined indices.

Add an aggregate query that unions completed sessions and skips:

```sql
SELECT trainingPlanId, metaPlanId, MAX(eventAt) AS lastEventAt
FROM (
    SELECT planId AS trainingPlanId, metaPlanId, startTime AS eventAt
    FROM workout_sessions
    WHERE endTime IS NOT NULL AND planId IS NOT NULL AND metaPlanId IS NOT NULL
    UNION ALL
    SELECT trainingPlanId, metaPlanId, skippedAt AS eventAt
    FROM meta_plan_skips
)
GROUP BY trainingPlanId, metaPlanId
```

Expose both a `Flow<List<LastMetaPlanRotationEventRow>>` for consumers and a suspend query filtered by `metaPlanId` for the transaction.

- [ ] **Step 5: Make skip validation fully transactional**

Add a default `@Transaction` method on `MetaTrainingPlanDao`:

```kotlin
@Transaction
suspend fun skipCurrentSubPlanIfCurrent(
    metaPlanId: Long,
    expectedTrainingPlanId: Long,
    skippedAt: Long
): Boolean {
    val orderedIds = getItemsForMetaPlan(metaPlanId)
        .sortedBy { it.orderIndex }
        .map { it.trainingPlanId }
    if (orderedIds.size < 2) return false

    val anchors = getLastRotationEventsForMetaPlan(metaPlanId)
        .associate { it.trainingPlanId to it.lastEventAt }
    val current = resolveMetaPlanRotation(orderedIds, anchors).firstOrNull()
    if (current != expectedTrainingPlanId) return false

    val greatestAnchor = anchors.values.maxOrNull()
    val effectiveSkippedAt = if (greatestAnchor != null && skippedAt <= greatestAnchor) {
        greatestAnchor + 1L
    } else {
        skippedAt
    }

    insertMetaPlanSkip(
        MetaPlanSkipEntity(
            metaPlanId = metaPlanId,
            trainingPlanId = expectedTrainingPlanId,
            skippedAt = effectiveSkippedAt
        )
    )
    return true
}
```

Because membership, size, all anchors, expected-current comparison and insert are inside one Room transaction, stale UI and double taps cannot create a second effective event. Normalizing an equal clock value to `greatestAnchor + 1` also preserves the order of two legitimate consecutive skips that happen within the same millisecond; add a focused DAO-default-method test for this case.

- [ ] **Step 6: Migrate database 9 to 10**

Add the entity, set `version = 10`, create `MIGRATION_9_10`, expose `migration9To10ForTests()`, and register `MIGRATION_9_10` immediately after `MIGRATION_8_9` in the existing `.addMigrations` call. DDL must match the entity exactly:

```sql
CREATE TABLE IF NOT EXISTS `meta_plan_skips` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `metaPlanId` INTEGER NOT NULL,
    `trainingPlanId` INTEGER NOT NULL,
    `skippedAt` INTEGER NOT NULL,
    FOREIGN KEY(`metaPlanId`) REFERENCES `meta_training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`trainingPlanId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
)
```

Create indices matching the entity. Extend `IronLogDatabaseMigrationTest` to open a v9 database with existing plan/session rows, run 9→10, verify old rows remain and insert one valid skip.

The migration creates these indices explicitly:

```sql
CREATE INDEX IF NOT EXISTS `index_meta_plan_skips_metaPlanId` ON `meta_plan_skips` (`metaPlanId`);
CREATE INDEX IF NOT EXISTS `index_meta_plan_skips_trainingPlanId` ON `meta_plan_skips` (`trainingPlanId`);
CREATE INDEX IF NOT EXISTS `index_meta_plan_skips_metaPlanId_trainingPlanId` ON `meta_plan_skips` (`metaPlanId`, `trainingPlanId`);
```

- [ ] **Step 7: Move rotation observation to the meta repository**

Extend `MetaTrainingPlanRepository`:

```kotlin
fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>>
suspend fun skipCurrentSubPlan(metaPlanId: Long, expectedTrainingPlanId: Long): Boolean
```

Map DAO rows in `MetaTrainingPlanRepositoryImpl` and pass `EpochConverter.toLong(LocalDateTime.now())` to the transactional DAO method. Keep `WorkoutRepository.observeLastSessionPerMetaPlanSubPlan()` unchanged: dashboard labels still need a completed-session-only source so a skip is never displayed as “zuletzt trainiert”. Implement the new observation and skip behavior in `FakeMetaTrainingPlanRepository` with the same resolver semantics.

- [ ] **Step 8: Run rotation/repository tests and commit Task 4**

```bash
./gradlew --no-daemon :core:common:testDebugUnitTest --tests "com.ironlog.app.domain.util.MetaPlanRotationTest" :app:testDebugUnitTest --tests "com.ironlog.app.data.repository.MetaTrainingPlanRepositoryImplTest" --tests "com.ironlog.app.presentation.plans.MetaPlanListViewModelTest"
git add core/model core/common core/database data/src/main/java/com/ironlog/app/data/repository/MetaTrainingPlanRepositoryImpl.kt app/src/test/java/com/ironlog/app/fakes app/src/test/java/com/ironlog/app/data/repository/MetaTrainingPlanRepositoryImplTest.kt app/src/test/java/com/ironlog/app/presentation/plans/MetaPlanListViewModelTest.kt app/src/androidTest/java/com/ironlog/app/data/local/IronLogDatabaseMigrationTest.kt app/src/androidTest/java/com/ironlog/app/data/local/MetaPlanSkipDaoTest.kt
git commit -m "Persist meta-plan skip rotation events"
```

Expected: unit tests PASS. The DAO and migration instrumentation tests compile here but execute later in CI because local Android instrumentation is unavailable.

---

### Task 5: Include skip events in backup and restore

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ironlog/shared/backup/BackupPayloadV1.kt`
- Modify: `shared/src/commonMain/kotlin/com/ironlog/shared/backup/BackupPayloadValidator.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/backup/BackupPayloadV1.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/backup/BackupSnapshot.kt`
- Modify: `core/model/src/main/java/com/ironlog/app/domain/repository/BackupRepository.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/repository/BackupRepositoryImpl.kt`
- Modify: `data/src/test/java/com/ironlog/app/data/repository/BackupRepositoryImplTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/data/backup/BackupPayloadValidatorTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/ironlog/shared/backup/BackupPayloadValidatorTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/settings/SettingsViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/ironlog/app/data/backup/BackupLifecycleRoundTripTest.kt`

**Interfaces:**
- Consumes: `MetaPlanSkipEntity` and DAO list/delete/insert methods from Task 4.
- Produces: Backward-compatible `metaPlanSkips` payload, validation, counts and transactional import/export.

- [ ] **Step 1: Write failing backup compatibility tests**

Add a payload roundtrip with one skip, an old JSON document without `metaPlanSkips`, and invalid FK cases:

```kotlin
val legacyJson = Json { encodeDefaults = false }.encodeToString(
    validPayload().copy(metaPlanSkips = emptyList())
)
val legacy = json.decodeFromString<BackupPayloadV1>(legacyJson)
assertTrue(legacy.metaPlanSkips.isEmpty())

val invalid = validPayload().copy(
    metaPlanSkips = listOf(BackupMetaPlanSkip(1L, 999L, 3L, 1234L))
)
assertFalse(BackupPayloadValidator.validate(invalid, 10).isValid)
```

In `BackupRepositoryImplTest`, verify export reads skips, import inserts skips after plans exist, and deletion removes skips before meta/training plans.

- [ ] **Step 2: Run focused backup tests to verify failure**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.data.backup.BackupPayloadValidatorTest" :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.BackupRepositoryImplTest" :shared:testAndroidHostTest --tests "com.ironlog.shared.backup.BackupPayloadValidatorTest"
```

Expected: FAIL because payload and repository support are missing.

- [ ] **Step 3: Extend the payload without breaking old backups**

```kotlin
@Serializable
data class BackupMetaPlanSkip(
    val id: Long,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val skippedAt: Long
)

// Last property in BackupPayloadV1
val metaPlanSkips: List<BackupMetaPlanSkip> = emptyList()
```

Keep `formatVersion = 1`; the default empty list is the compatibility mechanism. Add the data-module typealias.

- [ ] **Step 4: Validate IDs and references**

Add duplicate-ID validation under label `meta plan skip`. For each skip, require `metaPlanId` in the payload meta-plan IDs and `trainingPlanId` in plan IDs. Do not require a matching current `metaPlanItem`; historical skips may remain after rotation editing and are ignored by the resolver.

- [ ] **Step 5: Wire snapshots, counts and transactional import order**

Add `metaPlanSkips` to `BackupSnapshot`, both payload builders, `BackupContentCounts`, `readSnapshotBlock`, `toCounts`, `toImportData`, and `ImportData`.

Update the `BackupRepository` contract comment from eight to nine workout-domain tables, and include `deleteAllMetaPlanSkips()` in both `resetUserData()` and `deleteAllInOrder()`.

Use this order:

```kotlin
// delete before referenced plans
metaTrainingPlanDao.deleteAllMetaPlanSkips()
metaTrainingPlanDao.deleteAllMetaPlanItems()

// insert after trainingPlans and metaTrainingPlans
metaTrainingPlanDao.replaceAllMetaPlanSkips(data.metaPlanSkips)
```

Set `BackupRepositoryImpl.SCHEMA_VERSION = 10` and add `MetaPlanSkipEntity.toBackup()` / `BackupMetaPlanSkip.toEntity()` mappers. Update count fixtures in Settings tests so compilation remains explicit.

- [ ] **Step 6: Update instrumentation parity without running it locally**

Change the lifecycle test callback/schema expectation from 9 to 10, seed a skip, and include `metaPlanSkips` in parity assertions. This test runs in GitHub Actions; do not claim local execution.

- [ ] **Step 7: Verify and commit Task 5**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.data.backup.BackupPayloadValidatorTest" :data:testDebugUnitTest --tests "com.ironlog.app.data.repository.BackupRepositoryImplTest" :shared:testAndroidHostTest --tests "com.ironlog.shared.backup.BackupPayloadValidatorTest"
git add shared/src/commonMain/kotlin/com/ironlog/shared/backup shared/src/commonTest data/src core/model/src/main/java/com/ironlog/app/domain/repository/BackupRepository.kt app/src/test app/src/androidTest/java/com/ironlog/app/data/backup/BackupLifecycleRoundTripTest.kt
git commit -m "Preserve meta-plan skips in backups"
```

Expected: focused host/unit tests PASS.

---

### Task 6: Wire skip rotation into the start flow

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt`
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/PlanSelectionSheet.kt`
- Modify: `feature/plans/src/main/java/com/ironlog/app/presentation/plans/MetaPlanListViewModel.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/plans/MetaPlanListViewModelTest.kt`
- Modify: `core/designsystem/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `MetaTrainingPlanRepository.observeLastRotationEventPerMetaPlanSubPlan()`, `skipCurrentSubPlan(metaPlanId: Long, expectedTrainingPlanId: Long)`, and `resolveMetaPlanRotation(orderedPlanIds: List<Long>, lastEventAtByPlanId: Map<Long, Long>)` from Task 4.
- Produces: A skip action only in `PlanSelectionSheet`; both dashboard and meta-plan list agree on the next plan.

- [ ] **Step 1: Write failing dashboard skip tests**

Cover these behaviors with the fake repository:

```kotlin
viewModel.skipCurrentMetaSubPlan(metaPlanId = 5L)
advanceUntilIdle()
assertEquals(20L, viewModel.uiState.value.metaPlanOptions.single().nextPlan?.id)
assertTrue(fakeWorkoutRepository.sessions.isEmpty())
```

Also assert: one-plan option exposes `canSkip = false`; a stale repository result refreshes without mutation; and an active workout blocks skip with the existing “anderes Training aktiv” error.

- [ ] **Step 2: Run dashboard/meta-list tests to verify missing behavior**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest" --tests "com.ironlog.app.presentation.plans.MetaPlanListViewModelTest"
```

Expected: FAIL because the new rotation source and skip method are not wired.

- [ ] **Step 3: Replace duplicated rotation math**

Change both ViewModels to observe training plans, completed meta-plan sessions, meta repository rotation events, and meta plans. Build the rotation index by `(trainingPlanId, metaPlanId)`, filter it to the current meta plan, and call:

```kotlin
val rotationIds = resolveMetaPlanRotation(
    orderedPlanIds = orderedSubPlans.map { it.id },
    lastEventAtByPlanId = eventIndexForMetaPlan
)
val rotatedPlans = rotationIds.mapNotNull(plansById::get)
```

Continue deriving `lastDoneDaysAgo` only from `workoutRepository.observeLastSessionPerMetaPlanSubPlan()`; a skip must not be displayed as a completed workout.

- [ ] **Step 4: Add an explicit skip UI state and action**

Extend `DashboardMetaPlanOption`:

```kotlin
val canSkip: Boolean
```

Add `skippingMetaPlanId: Long? = null` to `DashboardUiState`. Set it before the repository call and clear it in `finally`. Pass this ID through `DashboardScreen` to `PlanSelectionSheet`, which receives `skippingMetaPlanId: Long?`; this keeps transient write state out of the rotation model.

Implement:

```kotlin
fun skipCurrentMetaSubPlan(metaPlanId: Long) {
    viewModelScope.launch {
        val option = _uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaPlanId }
            ?: return@launch
        val expectedPlanId = option.nextPlan?.id ?: return@launch
        if (!option.canSkip) return@launch
        if (workoutRepository.getActiveSession() != null) {
            _uiState.update {
                it.copy(error = "Es ist bereits ein anderes Training aktiv. Bitte setze es fort oder beende es zuerst.")
            }
            return@launch
        }
        if (_uiState.value.skippingMetaPlanId != null) return@launch
        _uiState.update { it.copy(skippingMetaPlanId = metaPlanId) }
        try {
            val skipped = metaTrainingPlanRepository.skipCurrentSubPlan(metaPlanId, expectedPlanId)
            if (!skipped) {
                _uiState.update { it.copy(error = "Der vorgeschlagene Plan hat sich geändert. Bitte erneut versuchen.") }
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(error = "Teilplan konnte nicht übersprungen werden: ${error.message}") }
        } finally {
            _uiState.update { it.copy(skippingMetaPlanId = null) }
        }
    }
}
```

The repository flow refreshes the next option after success; do not create or navigate to a session.

- [ ] **Step 5: Add the button only to the start sheet**

Add `onSkipMetaPlan: (Long) -> Unit` to `PlanSelectionSheet` and pass it from `DashboardScreen`. Render a `TextButton` next to the “Weiter mit …” line:

```kotlin
TextButton(
    onClick = { onSkipMetaPlan(option.metaPlanId) },
    enabled = option.canSkip && skippingMetaPlanId != option.metaPlanId
) {
    Text(stringResource(R.string.plan_selection_meta_skip))
}
```

Add:

```xml
<string name="plan_selection_meta_skip">Überspringen</string>
<string name="plan_selection_meta_skip_stale">Der vorgeschlagene Plan hat sich geändert. Bitte erneut versuchen.</string>
```

Do not add a skip button to `MetaPlanListScreen`; the approved scope places the action only in “Training starten”.

- [ ] **Step 6: Verify and commit Task 6**

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest" --tests "com.ironlog.app.presentation.plans.MetaPlanListViewModelTest"
git add feature/dashboard feature/plans/src/main/java/com/ironlog/app/presentation/plans/MetaPlanListViewModel.kt app/src/test/java/com/ironlog/app/presentation/dashboard app/src/test/java/com/ironlog/app/presentation/plans core/designsystem/src/main/res/values/strings.xml
git commit -m "Add meta-plan skip action to workout start"
```

Expected: PASS; fake workout-session count remains unchanged in the skip test.

---

### Task 7: Generate schema and run the integration gate

**Files:**
- Create: `core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/10.json`
- Modify: Any already-owned fixture that fails compilation solely because an interface/data-class field added above is missing.

**Interfaces:**
- Consumes: All previous tasks.
- Produces: Compiling Android app, Room schema v10, focused green tests and debug APK.

- [ ] **Step 1: Generate and inspect the Room schema**

Run the smallest compile that invokes Room/KSP:

```bash
./gradlew --no-daemon :core:database:compileDebugKotlin
```

Expected: `10.json` is generated. Inspect it:

```bash
rg -n 'meta_plan_skips|metaPlanId|trainingPlanId|skippedAt' core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/10.json
git diff --check
```

Expected: table, foreign keys and indices match `MIGRATION_9_10`; no whitespace errors.

- [ ] **Step 2: Run the consolidated focused unit gate**

```bash
./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests "com.ironlog.app.presentation.dashboard.DashboardViewModelTest" \
  --tests "com.ironlog.app.presentation.plans.MetaPlanListViewModelTest" \
  --tests "com.ironlog.app.presentation.settings.SettingsViewModelTest" \
  --tests "com.ironlog.app.presentation.workout.ActiveWorkoutViewModelTest" \
  --tests "com.ironlog.app.data.repository.WorkoutRepositoryImplTest" \
  --tests "com.ironlog.app.data.repository.MetaTrainingPlanRepositoryImplTest" \
  --tests "com.ironlog.app.data.backup.BackupPayloadValidatorTest" \
  :data:testDebugUnitTest \
  --tests "com.ironlog.app.data.preferences.AppPreferencesDataStoreTest" \
  --tests "com.ironlog.app.data.repository.BackupRepositoryImplTest"
```

Expected: PASS. Fix only compilation fallout directly caused by changed interfaces or fields; do not broaden into unrelated failures.

- [ ] **Step 3: Build the Android debug APK**

```bash
./gradlew --no-daemon :app:assembleDebug
```

Expected: PASS with APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Perform the kill-criterion checks**

```bash
rg -n "StreakCard|currentStreak|calculateStreak|getCompletedWorkoutStartTimesDesc" feature core data app/src/test
rg -n "startWorkout\(" feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt
git status --short
```

Expected: no streak matches. Review every `startWorkout` match and confirm `skipCurrentMetaSubPlan` contains none. The worktree contains only planned files.

- [ ] **Step 5: Commit generated schema/integration fixes**

```bash
git add core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/10.json
git commit -m "Finalize meta-plan workflow integration"
```

If a fixture needed a direct compilation correction, amend the commit from the task that owns that fixture before this final commit. If the schema was already committed in Task 4 and no integration fixes exist, skip this empty commit.

- [ ] **Step 6: Push and let CI decide merge readiness**

Push `codex/meta-plan-workflow-improvements`, open or update the PR, and wait for `test`, `lintDebug`, `assembleDebug`, and `connectedDebugAndroidTest`. Do not merge if any required gate is missing or red; local unit/build success is not a substitute for the emulator gate.
