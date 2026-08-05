# Global Background Design Alignment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Apply the beautiful primary-tinted gradient background from the Dashboard to the entire application so that all screens (Plans, History, Exercises) look cohesive and have the same premium aesthetic.

**Architecture:** 
1. The `DashboardScreen` currently implements its own hardcoded background gradient `Brush.verticalGradient(primary.copy(alpha = 0.15f), background, background)`.
2. `MainActivity` implements a global gradient `Brush.verticalGradient(background, muted, surfaceVariant, background)`. This is why the other screens look dull and inconsistent.
3. We will move the primary-tinted gradient into `MainActivity` to make it global, and remove the explicit background `Box` wrapper from `DashboardScreen` so it inherits the global one seamlessly.

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Update Global Gradient in MainActivity

**Files:**
- Modify: `app/src/main/java/com/ironlog/app/MainActivity.kt`

**Step 1: Replace the global background brush**
Find the `Brush.verticalGradient` inside the global `Box` wrapper in `MainActivity.kt` and replace its color list with the primary-tinted one.

```kotlin
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/ironlog/app/MainActivity.kt
git commit -m "style: apply home primary-tinted gradient globally"
```

---

### Task 2: Remove Local Gradient from Dashboard

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`

**Step 1: Remove the Box wrapper**
Find the `Box` wrapper that applies the `background` modifier right after `IronLogScreenScaffold` content lambda opens, and remove it. The `LazyColumn` and loading states should sit directly inside the Scaffold's padding block. Ensure the `padding` is properly applied to the `LazyColumn` (which it already is).

**Step 2: Commit**

```bash
git add feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt
git commit -m "refactor: remove redundant local background gradient from dashboard"
```

---

### Task 3: Verify and Deploy

**Step 1: Compile the app**
Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Deploy to device**
Run: `.\deploy.ps1 -Launch`
