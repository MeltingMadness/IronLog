# Remove Quick Log Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the "Quick Log" feature from the active workout screen as it has been deemed impractical.

**Architecture:** We will surgically remove the `QuickLogComposer` and its associated state from the `ActiveWorkoutScreen`. No ViewModel changes are necessary because the backend data and logic (like `logSet`) are still used by the inline per-exercise logging components.

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Remove Quick Log UI State

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Remove state variables for Quick Log**
Find and remove the lines managing `quickSelectedExerciseId` and the `LaunchedEffect` that updates it. 

Lines to remove (around lines 93-102):
```kotlin
    var quickSelectedExerciseId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.exercisesWithSets) {
        val currentIds = state.exercisesWithSets.map { it.exercise.id }
        if (quickSelectedExerciseId == null || quickSelectedExerciseId !in currentIds) {
            quickSelectedExerciseId = state.exercisesWithSets.firstOrNull()?.exercise?.id
        }
    }
```

**Step 2: Commit**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "refactor: remove Quick Log state variables"
```

---

### Task 2: Remove QuickLogComposer call

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Remove the composer call from the UI tree**
Remove the block of code calling `QuickLogComposer` near the bottom of the main Scaffold content area (around line 221).

Lines to remove:
```kotlin
            QuickLogComposer(
                exercisesWithSets = state.exercisesWithSets,
                selectedExerciseId = quickSelectedExerciseId,
                onSelectExercise = { quickSelectedExerciseId = it },
                defaultWarmupFlag = preferences.defaultWarmupFlag,
                intensitySystem = preferences.intensitySystem,
                onLogSet = { exerciseId, reps, weight, isWarmup, intensity ->
                    viewModel.logSet(exerciseId, reps, weight, isWarmup, intensity)
                },
                haptic = haptic
            )
```

**Step 2: Commit**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "refactor: remove QuickLogComposer instantiation"
```

---

### Task 3: Remove QuickLogComposer function

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Delete the `QuickLogComposer` composable function definition**
Find the `private fun QuickLogComposer` definition (around line 345) and remove the entire function.

**Step 2: Clean up unused imports (optional but recommended)**
If removing the Quick Log left any imports unused (e.g., related purely to styling the quick log box), they should be cleaned up.

**Step 3: Test compilation**
Run the compiler to ensure no references were missed.
Run: `./gradlew.bat :feature:workout:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "refactor: delete QuickLogComposer function definition"
```
