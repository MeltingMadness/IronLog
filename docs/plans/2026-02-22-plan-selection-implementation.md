# Plan Selection Bottom Sheet Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow users to choose between their saved training plans and a "Free Workout" via a bottom sheet when clicking "Start Workout" on the dashboard.

**Architecture:** 
- The `DashboardViewModel` will load available training plans.
- When the user presses "Start Workout", a `showPlanSelectionSheet` boolean toggles on.
- A `PlanSelectionSheet` composable renders the available plans and a fallback "Free Workout" item.
- The selection creates a new workout session (optionally linked to a plan) and navigates to the active workout screen.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines.

---

### Task 1: Update DashboardViewModel

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt`
- Modify: `app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `startNewWorkoutWithPlan creates session with plan name`() = runTest {
    val vm = createViewModel()
    var createdSessionId: Long? = null
    var planIdPass: Long? = null
    
    val testPlan = TrainingPlan(id = 99L, name = "My Test Plan", exercises = emptyList())
    
    vm.startNewWorkoutWithPlan(testPlan) { sessionId, planId ->
        createdSessionId = sessionId
        planIdPass = planId
    }
    
    testDispatcher.scheduler.advanceUntilIdle()
    
    assertTrue(createdSessionId != null)
    assertEquals(99L, planIdPass)
    val session = workoutRepo.getSessionById(createdSessionId!!)
    assertEquals("My Test Plan", session?.name)
}
```

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "*DashboardViewModelTest*"`
Expected: Compilation failure or test failure because `startNewWorkoutWithPlan` doesn't exist.

**Step 3: Write minimal implementation**
In `DashboardUiState`, add:
```kotlin
val trainingPlans: List<TrainingPlan> = emptyList(),
val showPlanSelectionSheet: Boolean = false
```

In `DashboardViewModel`:
- Add `private val trainingPlanRepository: TrainingPlanRepository` to the constructor.
- Add `showPlanSelectionSheet()` and `dismissPlanSelectionSheet()` methods.
- Inside `loadDashboard()`, observe `trainingPlanRepository.getAllPlans()` and update the UI state.
- Add `fun startNewWorkoutWithPlan(plan: TrainingPlan, onSessionCreated: (Long, Long?) -> Unit)` which calls `workoutRepository.startWorkout(plan.name)` and then `onSessionCreated(sessionId, plan.id)`.
- Update the existing `startNewWorkout` signature to `onSessionCreated: (Long, Long?) -> Unit` and pass `null` for `planId`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "*DashboardViewModelTest*"`
Expected: PASS

**Step 5: Commit**
```bash
git add feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt app/src/test/java/com/ironlog/app/presentation/dashboard/DashboardViewModelTest.kt
git commit -m "feat(dashboard): add plan selection logic to view model"
```

---

### Task 2: Create PlanSelectionSheet UI Component

**Files:**
- Create: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/PlanSelectionSheet.kt`

**Step 1: Write minimal implementation**
Create the composable:

```kotlin
package com.ironlog.app.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.presentation.theme.ironLogDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSelectionSheet(
    plans: List<TrainingPlan>,
    onDismiss: () -> Unit,
    onPlanSelected: (TrainingPlan) -> Unit,
    onFreeWorkoutSelected: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Training starten",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            LazyColumn {
                items(plans, key = { it.id }) { plan ->
                    ListItem(
                        headlineContent = { Text(plan.name) },
                        leadingContent = { Icon(Icons.Default.Assignment, contentDescription = null) },
                        modifier = Modifier.clickable { onPlanSelected(plan) }
                    )
                }
                
                if (plans.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Freies Training") },
                        supportingContent = { Text("Ohne Vorlage trainieren") },
                        leadingContent = { 
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        modifier = Modifier.clickable { onFreeWorkoutSelected() }
                    )
                }
            }
        }
    }
}
```

**Step 2: Run build to verify compilation**
Run: `./gradlew :feature:dashboard:compileDebugKotlin`
Expected: PASS

**Step 3: Commit**
```bash
git add feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/PlanSelectionSheet.kt
git commit -m "feat(ui): create PlanSelectionSheet composable"
```

---

### Task 3: Integrate Sheet into DashboardScreen

**Files:**
- Modify: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/ironlog/app/presentation/navigation/Screen.kt`
- Modify: `app/src/main/java/com/ironlog/app/presentation/navigation/NavHost.kt`
- Modify: `app/src/main/java/com/ironlog/app/di/AppModule.kt`

**Step 1: Write minimal implementation**
Update `DashboardScreen` signature to pass `planId`:
```kotlin
fun DashboardScreen(
    onStartWorkout: (Long, Long?) -> Unit, // sessionId, planId
    onContinueWorkout: (Long) -> Unit,
    // ...
)
```

In `DashboardScreen`, update the `CommandCenterCard` onStartWorkout lambda:
```kotlin
onStartWorkout = { viewModel.showPlanSelectionSheet() }
```

Add the bottom sheet conditionally at the end of the `Scaffold` body:
```kotlin
if (state.showPlanSelectionSheet) {
    PlanSelectionSheet(
        plans = state.trainingPlans,
        onDismiss = viewModel::dismissPlanSelectionSheet,
        onPlanSelected = { plan -> 
            viewModel.dismissPlanSelectionSheet()
            viewModel.startNewWorkoutWithPlan(plan, onStartWorkout) 
        },
        onFreeWorkoutSelected = { 
            viewModel.dismissPlanSelectionSheet()
            viewModel.startNewWorkout(onStartWorkout) 
        }
    )
}
```

Update `Screen.kt` and `NavHost.kt` so that `onStartWorkout` receives the `planId` and passes it to the `ActiveWorkout` screen as an argument (update route string `active_workout/{sessionId}?planId={planId}`).

Update `AppModule.kt` to inject `TrainingPlanRepository` into `DashboardViewModel`.

**Step 2: Check compilation**
Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS

**Step 3: Commit**
```bash
git add .
git commit -m "feat(dashboard): integrate plan selection bottom sheet"
```
