# Glassmorphism UI Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the broken, blurry glassmorphism effect and add a pulsating animation to the "Start Workout" button for new users.

**Architecture:** 
- The `Glassmorphism.kt` modifier currently uses `Modifier.blur()` on the entire component, which cascades down to text and icons, making them unreadable. We will replace this with a sharp, semi-transparent background combined with a border gradient to simulate a frosted glass edge.
- In `DashboardScreen.kt`, we will update `CommandCenterCard` to conditionally use an `infiniteRepeatable` scale animation if the user has no active session and no past workouts/records.

**Tech Stack:** Kotlin, Jetpack Compose.

---

### Task 1: Fix Glassmorphism Modifier

**Files:**
- Modify: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Glassmorphism.kt`

**Step 1: Write minimal implementation**
We don't need a UI test for this visual change. Open `Glassmorphism.kt` and rewrite the `glassmorphism` function. Remove `blurRadius: Dp = 16.dp` from the signature and remove the `.blur(...)` call entirely. Optionally, increase the default `backgroundColor` alpha from `0.1f` to `0.3f` to compensate for the lost blur opacity.

```kotlin
fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.3f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    borderWidth: Dp = 1.dp
): Modifier = this.then(
    Modifier
        .clip(shape)
        .background(backgroundColor, shape)
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    Color.Transparent,
                    borderColor.copy(alpha = 0.05f)
                )
            ),
            shape = shape
        )
)
```

**Step 2: Run build to verify compilation**
Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: PASS

**Step 3: Commit**
```bash
git add core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Glassmorphism.kt
git commit -m "style(ui): fix glassmorphism blur issue"
```

---

### Task 2: Implement Pulsating Button in Dashboard

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`

**Step 1: Update CommandCenterCard Signature**
Pass a new `isFirstTimeUser` boolean to `CommandCenterCard`.
```kotlin
@Composable
private fun CommandCenterCard(
    hasActiveSession: Boolean,
    isFirstTimeUser: Boolean,
    onStartWorkout: () -> Unit,
    onContinueWorkout: () -> Unit
)
```

**Step 2: Add Pulse Animation**
Inside `CommandCenterCard`, add the infinite transition logic:
```kotlin
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isFirstTimeUser && !hasActiveSession) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
```
Apply `Modifier.scale(scale)` to the `Button`.

**Step 3: Update DashboardScreen Caller**
Determine `isFirstTimeUser` in `DashboardScreen`:
```kotlin
    val isFirstTimeUser = state.lastWorkout == null && state.recentRecords.isEmpty()
```
Pass this down to `CommandCenterCard` in the LazyColumn `item`.

**Step 4: Fix Imports and Build**
Make sure to add `androidx.compose.animation.core.*` and `androidx.compose.ui.draw.scale` imports.
Run: `./gradlew :feature:dashboard:compileDebugKotlin`
Expected: PASS

**Step 5: Commit**
```bash
git add feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt
git commit -m "feat(dashboard): add pulsing animation to start button for new users"
```
