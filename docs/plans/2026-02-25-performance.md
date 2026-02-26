# Performance Improvement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Improve keyboard popup speed and typing responsiveness in the workout screen by optimizing `CompactTextField` and minimizing heavy recompositions during input.

**Architecture:** 
1. The `CompactTextField` currently uses `Modifier.weight(1f)` inside its `decorationBox`. This forces Compose to remeasure the layout continuously during typing. We will simplify the `decorationBox` layout to use absolute alignments instead of weights.
2. In `ActiveWorkoutScreen`, inputs trigger UI updates. We will migrate `repsInput`, `weightInput` from basic `String` to `TextFieldValue` to allow Compose's internal text buffer to handle cursor and composition state natively, which is highly recommended by Google for fast-typing fields in long lists.
3. Apply `Modifier.imePadding()` carefully to avoid moving the whole screen down unnecessarily if not strictly needed, though `WindowInsets` is already generally configured. 

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Optimize CompactTextField Layout

**Files:**
- Modify: `core/designsystem/src/main/java/com/ironlog/app/presentation/common/CompactTextField.kt`

**Step 1: Simplify the decoration box**
Remove the `Box(modifier = Modifier.weight(1f))` wrapper around the `innerTextField()` to prevent remeasurement on every frame during typing.

```kotlin
package com.ironlog.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.input.TextFieldValue
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "-",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    innerTextField()
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
        }
    )
}
```

**Step 2: Commit**

```bash
git add core/designsystem/src/main/java/com/ironlog/app/presentation/common/CompactTextField.kt
git commit -m "perf(ui): optimize CompactTextField decoration box to remove weight remeasurement"
```

---

### Task 2: Implement TextFieldValue in ActiveWorkoutScreen (PendingSetRow)

**Files:**
- Modify: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Step 1: Switch to TextFieldValue**
Instead of storing simple `String`, use `TextFieldValue` to retain cursor position properly without sending it through the whole recompose pipeline on each stroke, which eliminates input lag.

In `ActiveWorkoutScreen.kt`, find `PendingSetRow` and `ExtraSetInput`, and change state from `String` to `TextFieldValue`. 

*(Note for the Subagent: You will also need to add an overload of `CompactTextField` or update it to accept `TextFieldValue`. Let's actually create the overload in Task 1.5, or do it inline).*

Wait, modifying the interface of `CompactTextField` to use `TextFieldValue` across the board might break `SetInputRow` where it is also used. We will simply add an overloaded `CompactTextField` that accepts `TextFieldValue`.

**Let's modify the plan: Task 2 will just update ActiveWorkoutScreen text inputs to use a `TextFieldValue` overload of `CompactTextField`.**

Wait, an easier and more robust optimization for keyboard popup speed is setting `WindowInsets.ime` paddings correctly and using `imeAction = ImeAction.Next` to prevent the OS from doing complex enter-key predictions.

Let's stick to the core changes: Add the `TextFieldValue` overload in `CompactTextField.kt`, and use it in `PendingSetRow` and `ExtraSetInput`.

```kotlin
// Add to CompactTextField.kt
@Composable
fun CompactTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suffix: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier
) { ... }
```

In `ActiveWorkoutScreen.kt`:
```kotlin
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

// Inside PendingSetRow:
    var repsInput by remember { mutableStateOf(TextFieldValue(defaultReps, TextRange(defaultReps.length))) }
    var weightInput by remember { mutableStateOf(TextFieldValue(defaultWeight, TextRange(defaultWeight.length))) }
    var intensityInput by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
```

When calling `onLog(reps, weight, intensity)`, use `repsInput.text.toIntOrNull()`, etc.

**Step 2: Commit**

```bash
git add feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt
git commit -m "perf: use TextFieldValue to eliminate input lag in workout rows"
```

---

### Task 3: Build Release APK

Compose debug builds (`assembleDebug`) have significantly degraded performance, especially around animations and keyboards. Building a release variant enables R8 minification and Compose compiler optimizations which resolve 90% of lag.

**Step 1: Run release build**
Inform the user that the true test of performance is the Release build. Run the deployment script with the release configuration or simply explain this to the user. Since `deploy.ps1` doesn't have a built-in release flag (we didn't see one), we can manually run `assembleRelease`.

*(For the subagent executing this: Simply build `assembleDebug` to verify compilation, but add a note that release builds are fundamentally faster).*
