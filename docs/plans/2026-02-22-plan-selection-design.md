# Plan Selection Bottom Sheet Design

## Overview
Currently, clicking the "Start Workout" button on the dashboard immediately starts a blank "Free Workout". We want to introduce a selection step allowing users to choose from their saved training plans, with "Free Workout" as a fallback option at the bottom.

## Approach
We will use a **ModalBottomSheet** (Approach 1) triggered from the Dashboard.

## 1. Data & State (`feature:dashboard`)
- **ViewModel:** Inject `TrainingPlanRepository` into `DashboardViewModel`.
- **State:** 
  - Add `val showPlanSelection: Boolean = false` to `DashboardUiState`.
  - Add `val trainingPlans: List<TrainingPlan> = emptyList()` to `DashboardUiState`.
- **Logic:** 
  - Observe training plans in `loadDashboard()`.
  - Instead of immediately calling `startNewWorkout`, the "Start Workout" button will trigger `showPlanSelectionSheet()`.

## 2. UI Component (`feature:dashboard`)
- **New Composable:** Create `PlanSelectionSheet(plans, onDismiss, onPlanSelected, onFreeWorkoutSelected)` in `DashboardScreen.kt` or a separate file.
- **Layout:**
  - A bottom sheet.
  - A `LazyColumn` displaying all `TrainingPlan`s as selectable cards.
  - A final list item (or fixed bottom button) for "Freies Training" (Free Workout) styled slightly differently to distinguish it from saved plans.

## 3. Navigation & Integration
- **Free Workout:** The existing `startNewWorkout` logic remains unchanged. It creates a session named "Training <Date>".
- **Plan Selected:** A new method `startWorkoutFromPlan(plan: TrainingPlan, onSessionCreated: (Long) -> Unit)` will be added. It creates a session named after the plan (e.g., "Push Day") and navigates to the active workout screen, passing the `planId` so the `ActiveWorkoutViewModel` can load the predefined exercises.
