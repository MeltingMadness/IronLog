# WorkoutHistoryScreen Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the `WorkoutCard` in the `WorkoutHistoryScreen` to exactly match the visual structure and typography of the `PlanCard` in the `TrainingPlanListScreen`.

**Architecture:** 
- We will replace the current badge-based layout in `WorkoutCard` with the text-stack layout from `PlanCard`.
- The new structure will be:
  1. Title (`titleLarge`, `FontWeight.Bold`).
  2. Date (`bodyMedium`, `primary` color, `SemiBold` - matching the exercise count on PlanCard).
  3. Stats row (Duration, Exercises, Volume) as a dot-separated string (`bodyMedium`, `onSurfaceVariant` - matching the exercise list on PlanCard).
  4. A large Button at the bottom (acting as the "View Details" trigger, matching the "Start Workout" button on PlanCard).

**Tech Stack:** Kotlin, Jetpack Compose

---

### Task 1: Refactor WorkoutCard Composable

**Files:**
- Modify: `feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt`

**Step 1: Rewrite WorkoutCard to match PlanCard**
Replace the `WorkoutCard` function and remove the `BadgeStat` function. Use the exact spacing and font weights from `PlanCard`. 

```kotlin
@Composable
private fun WorkoutCard(
    item: com.ironlog.app.domain.model.WorkoutHistoryItem,
    unitSystem: com.ironlog.app.domain.model.UnitSystem,
    onClick: () -> Unit
) {
    val dims = com.ironlog.app.presentation.theme.ironLogDimens

    com.ironlog.app.presentation.common.IronLogSurfaceCard(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .androidx.compose.ui.draw.clip(androidx.compose.foundation.shape.RoundedCornerShape(dims.radiusLg))
            .androidx.compose.foundation.clickable(onClick = onClick),
        tone = com.ironlog.app.presentation.common.IronLogSurfaceTone.ELEVATED
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(dims.spacingLg)
        ) {
            val title = if (item.session.name.isNotBlank()) item.session.name else stringResource(id = R.string.history_title)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(dims.spacingXs))
            
            Text(
                text = item.session.startTime.format(com.ironlog.app.domain.model.DateFormatting.DATE_FULL),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(dims.spacingSm))
            
            val durationMin = item.session.durationSeconds / 60
            val statsList = mutableListOf<String>()
            statsList.add("$durationMin min")
            statsList.add("${item.exerciseCount} Übungen")
            if (item.totalVolume > 0) {
                statsList.add(com.ironlog.app.domain.model.WeightFormatting.formatVolume(item.totalVolume, unitSystem))
            }
            
            Text(
                text = statsList.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(dims.spacingLg))
            
            androidx.compose.material3.Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, contentDescription = null)
                Spacer(modifier = Modifier.width(dims.spacingXs))
                Text(
                    text = "Details", // Or a string resource if available, but "Details" is fine for now
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
```

**Step 2: Remove `BadgeStat`**
Delete the `private fun BadgeStat` composable from the bottom of the file since it is no longer used.

**Step 3: Update Imports**
Ensure `androidx.compose.material.icons.filled.ArrowForward` is imported if not already.
```kotlin
import androidx.compose.material.icons.filled.ArrowForward
```

**Step 4: Run compilation to verify**
Run: `./gradlew.bat :feature:history:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt
git commit -m "style: align WorkoutCard design strictly with PlanCard template"
```
