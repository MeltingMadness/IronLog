# Logged Set Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the `LoggedSetRow` to visually match the tabular/grid layout of `PendingSetRow` (input boxes) so the columns align perfectly, while slightly altering the aesthetic to indicate the set is completed (e.g., using a muted green background/border or just muted colors, and a delete button instead of a log button).

**Architecture:** 
1. Create a `LoggedSetBox` composable that mimics the exact visual footprint (padding, background, border, height) of `CompactTextField` but uses a standard `Text` element instead of an input field.
2. Update `LoggedSetRow` to use this new `LoggedSetBox` for the Weight, Reps, and Intensity values, assigning them the exact same `Modifier.weight` as in `PendingSetRow` (1.2f, 1f, 1f).
3. Align the Delete button to perfectly replace the "Log" checkmark button space (`size(40.dp)`).

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Create LoggedSetBox Composable

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Add `LoggedSetBox`**
Add this new composable at the bottom of the file or near `LoggedSetRow`. It perfectly mimics the dimensions of the text field.

```kotlin
@Composable
private fun LoggedSetBox(
    value: String,
    suffix: String,
    isWarmup: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isWarmup) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    }
    
    val borderColor = if (isWarmup) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .background(color = containerColor, shape = MaterialTheme.shapes.small)
            .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontStyle = if (isWarmup) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "feat(ui): add LoggedSetBox to mimic input fields for completed sets"
```

---

### Task 2: Refactor LoggedSetRow

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Rewrite LoggedSetRow**
Update the `LoggedSetRow` to use the `LoggedSetBox` elements with the exact same weights as `PendingSetRow`. We'll pass `intensitySystem` down so we know whether to render an empty/RPE box.

*Note for implementer: Since `LoggedSetRow` doesn't currently accept `intensitySystem: IntensitySystem`, you must add this parameter to `LoggedSetRow` and update all calls to `LoggedSetRow` in `ExerciseCard` to pass it!*

```kotlin
@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    intensitySystem: com.ironlog.app.domain.model.IntensitySystem,
    onDeleteSet: (Long) -> Unit,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != com.ironlog.app.domain.model.IntensitySystem.OFF
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = set.setNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        
        val weightText = if (set.weightKg % 1 == 0.0) set.weightKg.toInt().toString() else set.weightKg.toString()
        
        LoggedSetBox(
            value = weightText,
            suffix = stringResource(id = R.string.common_unit_kg),
            isWarmup = set.isWarmup,
            modifier = Modifier.weight(1.2f)
        )
        
        LoggedSetBox(
            value = set.reps.toString(),
            suffix = stringResource(id = R.string.common_reps_short),
            isWarmup = set.isWarmup,
            modifier = Modifier.weight(1f)
        )
        
        if (tracksIntensity) {
            val intensityText = set.rpe?.let {
                if (it % 1 == 0.0) it.toInt().toString() else it.toString()
            } ?: ""
            
            LoggedSetBox(
                value = intensityText,
                suffix = intensitySystem.displayName,
                isWarmup = set.isWarmup,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Delete button replacing the log button space
        IconButton(
            onClick = { haptic.reject(); onDeleteSet(set.id) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.workout_delete_set_cd),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

**Step 2: Update `ExerciseCard` calls**
In `ActiveWorkoutScreen.kt` inside `ExerciseCard`, find all instances of `LoggedSetRow(...)` and add `intensitySystem = intensitySystem` to them.

**Step 3: Compile and Test**
Run: `./gradlew.bat :feature:workout:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "style: align LoggedSetRow visually with PendingSetRow grid"
```
