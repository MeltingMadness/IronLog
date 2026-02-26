# High-Density UI Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Shrink the global UI components, paddings, and typography to create a "Cockpit Mode" aesthetic (high visual density), as the current layouts are perceived as too large and lack overview.

**Architecture:** 
- Reduce global spacing and radius tokens in `DesignTokens.kt`.
- Scale down Material typography sizes in `Type.kt`.
- Decrease hardcoded heights (like `56.dp` buttons to `40.dp` or `44.dp`) in major cards (`PlanCard`, `WorkoutCard`, etc.).

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Shrink Global Design Tokens

**Files:**
- Modify: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/DesignTokens.kt`

**Step 1: Reduce all spacing and radius values**
Decrease the padding and corner radii to create a tighter "cockpit" feel.

```kotlin
@Immutable
data class IronLogDimens(
    val spacing2: Dp = 2.dp,
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 12.dp,
    val spacingLg: Dp = 16.dp,
    val spacingXl: Dp = 24.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 16.dp,
    val radiusXl: Dp = 24.dp
)
```

**Step 2: Commit**
```bash
git add core/designsystem/src/main/java/com/ironlog/app/presentation/theme/DesignTokens.kt
git commit -m "style: reduce global spacing and radius tokens for higher density"
```

---

### Task 2: Scale Down Global Typography

**Files:**
- Modify: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Type.kt`

**Step 1: Reduce font sizes by 2-4sp**
Find the `Typography` object and shrink the sizes. Keep `FontWeight` unchanged.

- `titleLarge`: fontSize = 18.sp, lineHeight = 24.sp
- `titleMedium`: fontSize = 14.sp, lineHeight = 20.sp
- `bodyLarge`: fontSize = 14.sp, lineHeight = 20.sp
- `bodyMedium`: fontSize = 12.sp, lineHeight = 16.sp
- `labelLarge`: fontSize = 12.sp, lineHeight = 16.sp
- `labelMedium`: fontSize = 10.sp, lineHeight = 14.sp

**Step 2: Commit**
```bash
git add core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Type.kt
git commit -m "style: scale down typography to improve visual overview"
```

---

### Task 3: Reduce Hardcoded Element Sizes

**Files:**
- Modify: `feature/plans/src/main/java/com/ironlog/app/presentation/plans/TrainingPlanListScreen.kt`
- Modify: `feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt`
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`

**Step 1: Shrink PlanCard Button**
In `TrainingPlanListScreen.kt`, find `height(56.dp)` in `PlanCard` and change it to `height(40.dp)`.

**Step 2: Shrink WorkoutCard Button**
In `WorkoutHistoryScreen.kt`, find `height(56.dp)` in `WorkoutCard` and change it to `height(40.dp)`.

**Step 3: Shrink Dashboard Buttons**
In `DashboardScreen.kt`, find `height(dims.spacingXl + dims.spacingLg)` (which used to be 32+24 = 56dp) on the Start Workout button, and just change it to `height(44.dp)`.

**Step 4: Commit**
```bash
git add feature/plans/src/main/java/com/ironlog/app/presentation/plans/TrainingPlanListScreen.kt feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt
git commit -m "style: shrink buttons and hardcoded sizes for compact ui"
```
