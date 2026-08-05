# Align History Design with Plans Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ensure the `WorkoutHistoryScreen` cards are pixel-perfect matches to the `TrainingPlanListScreen` cards.

**Architecture:** 
1. Remove the explicit `.clip()` modifier from `WorkoutCard` that is breaking the drop shadow and border of `IronLogSurfaceCard`.
2. Ensure the "Details" Button has identical styling to the "Start" Button.

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Fix WorkoutCard Modifier

**Files:**
- Modify: `feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt`

**Step 1: Update the Modifier**
Find `private fun WorkoutCard` and remove the `.clip(RoundedCornerShape(dims.radiusLg))` call from its modifier chain so it matches the `PlanCard` implementation exactly.

```kotlin
    com.ironlog.app.presentation.common.IronLogSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tone = com.ironlog.app.presentation.common.IronLogSurfaceTone.ELEVATED
    ) {
```

**Step 2: Commit**

```bash
git add feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt
git commit -m "style: remove clip modifier from WorkoutCard to match PlanCard borders"
```
