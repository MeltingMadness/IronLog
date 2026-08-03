# Glassmorphism UI Redesign

## Overview
Fix the currently broken glassmorphism effect in the IronLog app which makes text and icons blurry. Replace it with a clean, sharp frosted-glass effect using gradients and borders. Add a pulsating attention animation to the "Start Workout" button for first-time users to improve onboarding.

## Approach
- **Remove blur from content:** The current `Modifier.blur` in `Glassmorphism.kt` applies to the whole composable, blurring text. We will replace this with a pure gradient/alpha-based frosted glass illusion.
- **Pulsating Button:** Use Compose's `rememberInfiniteTransition` to scale the primary call-to-action button slightly up and down to draw attention, but *only* if the user has no active or past workouts.
- **Contrast Check:** Ensure the new semi-transparent backgrounds provide sufficient contrast for the text on top of them in both light and dark modes.

## 1. Update Glassmorphism Modifier (`core:designsystem`)
- **File:** `Glassmorphism.kt`
- **Change:** Remove `Modifier.blur`. Use a combination of `Modifier.background` with a semi-transparent color, and a `Modifier.border` with a subtle linear gradient to simulate the edge of glass. Keep the `glow` modifier as is, since it's meant for background ambient light.

## 2. Implement Pulsating Onboarding Button (`feature:dashboard`)
- **File:** `DashboardScreen.kt`
- **Change:** Create a wrapper or modify the `CommandCenterCard` button to use an infinite scale animation.
- **Condition:** The animation should only run when `hasActiveSession` is false AND the user has no recent workouts/records (indicating they are a new user).

## 3. Review Contrast and Colors (`core:designsystem`)
- **File:** `Color.kt` / `ThemeTokens.kt`
- **Change:** If the removed blur makes the background too transparent, slightly increase the alpha of the default `backgroundColor` in the `glassmorphism` modifier to ensure text remains legible against complex backgrounds.
