# RPE / RIR Tracking Design

## Overview
Add the ability to track workout intensity using either RPE (Rate of Perceived Exertion) or RIR (Reps in Reserve).

## Approach
We will use a **Unified Approach**:
- The database and domain models will always store the intensity as `rpe` (Double, 1.0 to 10.0).
- The user can choose their preferred display and input scale (RPE or RIR) in the app settings.
- The UI layer will handle the conversion: `RIR = 10.0 - RPE` and `RPE = 10.0 - RIR`.

## 1. Data Model & Database (`core:model`, `core:database`)
- **Domain:** Update `WorkoutSet` to include `val rpe: Double? = null`.
- **Entity:** Update `WorkoutSetEntity` to include `val rpe: Double? = null`.
- **Migration:** Increment Room database version and provide a migration (or destructive fallback if early in dev) to add the `rpe` column to `workout_sets`.

## 2. Settings (`core:model`, `data:preferences`, `feature:settings`)
- **Domain:** Create `enum class IntensitySystem { RPE, RIR }`.
- **Preferences:** Update `AppPreferences` and `AppPreferencesDataStore` to include `intensitySystem` (default: RPE).
- **UI:** Add a selection toggle/dropdown in `SettingsScreen` to choose between RPE and RIR.

## 3. UI Input (`core:designsystem`, `feature:workout`)
- **Component:** Update `SetInputRow` to include a third input field for intensity.
- **Label:** The label/placeholder will dynamically show "RPE" or "RIR" based on the user's settings.
- **Logic:** `ActiveWorkoutViewModel` will handle the conversion. If the user inputs "1.5" while in RIR mode, the ViewModel saves `8.5` as the RPE.

## 4. UI History & Details (`feature:history`)
- **Component:** Update `WorkoutCard` and detail views.
- **Display:** When rendering a set, if `rpe` is not null, append the intensity.
  - If preference is RPE: "80kg x 10 @ RPE 8.5"
  - If preference is RIR: "80kg x 10 @ 1.5 RIR"
