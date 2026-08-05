# RPE / RIR Tracking Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add RPE/RIR tracking to the app, unified under an `rpe` field in the database, with a toggle in settings to view/input as RIR or RPE.

**Architecture:** 
- Add `rpe` column to `workout_sets` using Room migration.
- Add `intensitySystem` enum to `AppPreferences` with DataStore.
- Update `SetInputRow` and `ActiveWorkoutViewModel` to support RPE/RIR input/display.
- Update history detail cards to show the logged intensity.

**Tech Stack:** Kotlin, Room, DataStore, Jetpack Compose.

---

### Task 1: Update Domain and Database Models

**Files:**
- Modify: `core/model/src/main/java/com/ironlog/app/domain/model/WorkoutSet.kt`
- Modify: `core/database/src/main/java/com/ironlog/app/data/local/entity/WorkoutSetEntity.kt`
- Test: `app/src/test/java/com/ironlog/app/data/local/entity/WorkoutSetEntityTest.kt` (Create this file if needed, or just run DAO tests to ensure it compiles)

**Step 1: Write the failing test**
Create/Modify a test verifying that converting between `WorkoutSet` and `WorkoutSetEntity` preserves the `rpe` field.

```kotlin
@Test
fun `WorkoutSet and WorkoutSetEntity conversion preserves rpe`() {
    val domainSet = WorkoutSet(id = 1L, sessionId = 2L, exerciseId = 3L, setNumber = 1, reps = 10, weightKg = 100.0, rpe = 8.5)
    val entity = WorkoutSetEntity.fromDomain(domainSet)
    assertEquals(8.5, entity.rpe)
    val convertedBack = entity.toDomain()
    assertEquals(8.5, convertedBack.rpe)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutSetEntityTest*"`
Expected: FAIL due to missing `rpe` property.

**Step 3: Write minimal implementation**
Add `val rpe: Double? = null` to both `WorkoutSet` and `WorkoutSetEntity`, and update `toDomain` and `fromDomain`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutSetEntityTest*"`
Expected: PASS

**Step 5: Update Room Database Version**
In `core/database/src/main/java/com/ironlog/app/data/local/IronLogDatabase.kt`, increment `version` and provide an `AutoMigration` (or fallback to destructive migration if that is already configured). Make sure the project builds:
`./gradlew :core:database:compileDebugKotlin`

**Step 6: Commit**
```bash
git add core/model/src/main/java/com/ironlog/app/domain/model/WorkoutSet.kt core/database/src/main/java/com/ironlog/app/data/local/entity/WorkoutSetEntity.kt core/database/src/main/java/com/ironlog/app/data/local/IronLogDatabase.kt app/src/test/java/com/ironlog/app/data/local/entity/WorkoutSetEntityTest.kt
git commit -m "feat(database): add rpe to workout sets"
```

---

### Task 2: Add IntensitySystem to Settings

**Files:**
- Modify: `core/model/src/main/java/com/ironlog/app/domain/model/AppPreferences.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/preferences/AppPreferencesDataStore.kt`
- Modify: `data/src/main/java/com/ironlog/app/data/preferences/AppPreferencesRepositoryImpl.kt`
- Modify: `feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsViewModel.kt`

**Step 1: Define IntensitySystem Enum**
In `core/model/src/main/java/com/ironlog/app/domain/model/IntensitySystem.kt`:
```kotlin
package com.ironlog.app.domain.model

enum class IntensitySystem(val displayName: String) {
    RPE("RPE"),
    RIR("RIR")
}
```

**Step 2: Update AppPreferences**
Add `val intensitySystem: IntensitySystem = IntensitySystem.RPE` to `AppPreferences`. Update DataStore keys to save this preference as a string.

**Step 3: Update SettingsViewModel**
Add a method `fun updateIntensitySystem(system: IntensitySystem)` to update the preference.

**Step 4: Update SettingsScreen**
Add a dropdown/segment button to allow the user to select their preferred `IntensitySystem`.

**Step 5: Run UI Tests or Build**
Run: `./gradlew :app:compileDebugKotlin`

**Step 6: Commit**
```bash
git add .
git commit -m "feat(settings): add intensity system preference"
```

---

### Task 3: Update SetInputRow for Intensity

**Files:**
- Modify: `core/designsystem/src/main/java/com/ironlog/app/presentation/common/SetInputRow.kt`

**Step 1: Update UI signature**
Update `SetInputRow` to accept `intensity: String`, `onIntensityChange: (String) -> Unit`, and `intensityLabel: String`. 

```kotlin
@Composable
fun SetInputRow(
    reps: String,
    onRepsChange: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    intensity: String,
    onIntensityChange: (String) -> Unit,
    intensityLabel: String,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
  // Add a third OutlinedTextField for intensity
}
```

**Step 2: Check usages and fix compilation**
Any view calling `SetInputRow` (like `ActiveWorkoutScreen`) will now fail. Temporarily pass empty strings to fix compilation.

**Step 3: Build & Commit**
```bash
./gradlew :core:designsystem:compileDebugKotlin
git add core/designsystem/src/main/java/com/ironlog/app/presentation/common/SetInputRow.kt
git commit -m "feat(ui): add intensity input to SetInputRow"
```

---

### Task 4: Integrate Intensity into Workout Tracker

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt`
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Write the failing test**
In `ActiveWorkoutViewModelTest.kt`, test that logging a set stores the RPE correctly based on the current preference (e.g. if preference is RIR and user inputs 2.0, RPE stored is 8.0).

**Step 2: Update ActiveWorkoutViewModel**
- Read `intensitySystem` from `AppPreferences`.
- Add `intensityInput` flow.
- When `logSet` is called, parse the intensity input.
  - If RPE: `rpe = input.toDoubleOrNull()`
  - If RIR: `rpe = input.toDoubleOrNull()?.let { 10.0 - it }`
- Store the set with the calculated `rpe`.

**Step 3: Update ActiveWorkoutScreen**
Hook up `SetInputRow` to the new `intensityInput` state from the ViewModel. Pass "RPE" or "RIR" as the `intensityLabel` depending on the current preference.

**Step 4: Verify tests**
```bash
./gradlew :app:testDebugUnitTest --tests "*ActiveWorkoutViewModelTest*"
```

**Step 5: Commit**
```bash
git add .
git commit -m "feat(workout): support rpe/rir logging"
```

---

### Task 5: Display Intensity in History

**Files:**
- Modify: `feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutDetailScreen.kt`

**Step 1: Update UI rendering**
In `WorkoutDetailScreen`, where individual sets are listed:
Read the user's `IntensitySystem` preference.
For each set, if `set.rpe != null`:
- If preference is RPE: Append `@ RPE ${set.rpe}`
- If preference is RIR: Append `@ ${10.0 - set.rpe} RIR`

**Step 2: Build and visually verify**
```bash
./gradlew :app:compileDebugKotlin
```

**Step 3: Commit**
```bash
git add feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutDetailScreen.kt
git commit -m "feat(history): display rpe/rir on past sets"
```