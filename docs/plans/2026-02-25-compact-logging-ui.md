# Refactor Workout Logging UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the workout logging UI to be significantly more compact, less clunky, and highly usable on mobile devices, adhering to high-end design principles.

**Architecture:** 
1. Redesign `SetInputRow` to use `BasicTextField` inside a custom, sleek, tight container instead of the bulky `OutlinedTextField`.
2. Refactor `PendingSetRow` and `ExtraSetInput` in `ActiveWorkoutScreen` to use this new compact input style.
3. Tighten the padding and typography in `ExerciseCard`, `LoggedSetRow`, and `PendingSetRow` to increase visual density (VISUAL_DENSITY: 8 - Cockpit Mode).

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Create a Sleek Compact TextField Component

**Files:**
- Create: `core/designsystem/src/main/java/com/ironlog/app/presentation/common/CompactTextField.kt`

**Step 1: Implement `CompactTextField`**
Create a new file containing a highly dense, borderless text field with a subtle background, designed to hold numbers like weight or reps without taking up much vertical space.

```kotlin
package com.ironlog.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = modifier
            .height(40.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(
                            text = "-",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
                if (suffix.isNotEmpty()) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    )
}
```

**Step 2: Commit**

```bash
git add core/designsystem/src/main/java/com/ironlog/app/presentation/common/CompactTextField.kt
git commit -m "feat(ui): add high-density CompactTextField for form inputs"
```

---

### Task 2: Refactor SetInputRow to use CompactTextField

**Files:**
- Modify: `core/designsystem/src/main/java/com/ironlog/app/presentation/common/SetInputRow.kt`

**Step 1: Rewrite SetInputRow**
Replace the heavy `OutlinedTextField` usage with the new `CompactTextField`. Make the log button smaller.

```kotlin
package com.ironlog.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.pressScale

@Composable
fun SetInputRow(
    reps: String,
    onRepsChange: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    intensity: String,
    onIntensityChange: (String) -> Unit,
    intensityLabel: String,
    showIntensityField: Boolean = true,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactTextField(
            value = weight,
            onValueChange = onWeightChange,
            suffix = stringResource(id = R.string.common_unit_kg),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.2f)
        )
        CompactTextField(
            value = reps,
            onValueChange = onRepsChange,
            suffix = stringResource(id = R.string.common_reps_short),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        if (showIntensityField) {
            CompactTextField(
                value = intensity,
                onValueChange = onIntensityChange,
                suffix = intensityLabel,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(
            onClick = {},
            modifier = Modifier
                .size(40.dp)
                .pressScale(onClick = onLog),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add core/designsystem/src/main/java/com/ironlog/app/presentation/common/SetInputRow.kt
git commit -m "refactor(ui): update SetInputRow to use CompactTextField"
```

---

### Task 3: Refactor ActiveWorkoutScreen set rows

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Rewrite PendingSetRow**
Find `private fun PendingSetRow` and update it to use `CompactTextField`.

```kotlin
@Composable
private fun PendingSetRow(
    setNumber: Int,
    defaultReps: String,
    defaultWeight: String,
    intensitySystem: IntensitySystem,
    onLog: (Int, Double, String) -> Unit
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    var repsInput by remember { mutableStateOf(defaultReps) }
    var weightInput by remember { mutableStateOf(defaultWeight) }
    var intensityInput by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        
        com.ironlog.app.presentation.common.CompactTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            suffix = stringResource(id = R.string.common_unit_kg),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.2f)
        )
        
        com.ironlog.app.presentation.common.CompactTextField(
            value = repsInput,
            onValueChange = { repsInput = it },
            suffix = stringResource(id = R.string.common_reps_short),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        
        if (tracksIntensity) {
            com.ironlog.app.presentation.common.CompactTextField(
                value = intensityInput,
                onValueChange = { intensityInput = it },
                suffix = intensitySystem.displayName,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        
        IconButton(
            onClick = {
                val reps = repsInput.toIntOrNull()
                val weight = weightInput.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLog(reps, weight, if (tracksIntensity) intensityInput else "")
                }
            },
            modifier = Modifier.size(40.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

**Step 2: Rewrite LoggedSetRow to be tighter**
Find `private fun LoggedSetRow` and reduce the vertical padding (`dims.spacing2` -> `dims.spacingXs`) and make the delete button smaller.

```kotlin
@Composable
private fun LoggedSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    onDeleteSet: (Long) -> Unit,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper
) {
    val dims = ironLogDimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dims.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = set.setNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = if (set.isWarmup) {
                    stringResource(id = R.string.workout_set_row_warmup, "", set.weightKg, set.reps).replace("^\s+".toRegex(), "")
                } else {
                    stringResource(id = R.string.workout_set_row_work, "", set.weightKg, set.reps).replace("^\s+".toRegex(), "")
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontStyle = if (set.isWarmup) FontStyle.Italic else FontStyle.Normal,
                color = if (set.isWarmup) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = { haptic.reject(); onDeleteSet(set.id) },
            modifier = Modifier.size(36.dp)
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

**Step 3: Modify ExtraSetInput slightly**
Find `private fun ExtraSetInput` and ensure it has tighter spacing as well. Also switch the FilterChip to be smaller or just tight. Ensure `SetInputRow` call is not broken.

```kotlin
@Composable
private fun ExtraSetInput(
    planTarget: com.ironlog.app.domain.model.PlanTarget?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    onLogSet: (Int, Double, Boolean, String) -> Unit,
    haptic: com.ironlog.app.presentation.common.HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    var repsInput by remember {
        mutableStateOf(
            planTarget?.let {
                if (it.targetReps > 0) it.targetReps.toString() else ""
            } ?: ""
        )
    }
    var weightInput by remember {
        mutableStateOf(
            planTarget?.let {
                if (it.targetWeightKg > 0) it.targetWeightKg.toString() else ""
            } ?: ""
        )
    }
    var intensityInput by remember { mutableStateOf("") }
    var isWarmup by remember { mutableStateOf(defaultWarmupFlag) }

    Column(modifier = Modifier.padding(top = dims.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = dims.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.FilterChip(
                selected = isWarmup,
                onClick = { isWarmup = !isWarmup },
                label = { Text(stringResource(id = R.string.workout_warmup_chip), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }

        com.ironlog.app.presentation.common.SetInputRow(
            reps = repsInput,
            onRepsChange = { repsInput = it },
            weight = weightInput,
            onWeightChange = { weightInput = it },
            intensity = intensityInput,
            onIntensityChange = { intensityInput = it },
            intensityLabel = intensitySystem.displayName,
            showIntensityField = tracksIntensity,
            onLog = {
                val reps = repsInput.toIntOrNull()
                val weight = weightInput.toDoubleOrNull()
                if (reps != null && reps > 0 && weight != null && weight >= 0) {
                    onLogSet(reps, weight, isWarmup, if (tracksIntensity) intensityInput else "")
                    haptic.confirm()
                    repsInput = ""
                    weightInput = ""
                    intensityInput = ""
                }
            }
        )
    }
}
```

**Step 4: Compile and test**
Run: `./gradlew.bat :feature:workout:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**
```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "style: dense and compact workout logging rows"
```
