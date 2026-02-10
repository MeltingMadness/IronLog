package com.ironlog.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object ActiveWorkout : Screen("active_workout/{sessionId}?planId={planId}") {
        fun createRoute(sessionId: Long, planId: Long = 0L): String {
            return if (planId > 0L) {
                "active_workout/$sessionId?planId=$planId"
            } else {
                "active_workout/$sessionId?planId=0"
            }
        }
    }
    data object ExerciseLibrary : Screen("exercises")
    data object WorkoutHistory : Screen("history")
    data object WorkoutDetail : Screen("workout_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "workout_detail/$sessionId"
    }
    data object ExerciseStats : Screen("exercise_stats/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_stats/$exerciseId"
    }
    data object TrainingPlanList : Screen("plans")
    data object PlanEditorNew : Screen("plans/new")
    data object PlanEditor : Screen("plans/edit/{planId}") {
        fun createRoute(planId: Long) = "plans/edit/$planId"
    }
}

enum class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
) {
    HOME(Screen.Dashboard, "Home", Icons.Default.Home),
    PLANS(Screen.TrainingPlanList, "Pläne", Icons.AutoMirrored.Filled.List),
    HISTORY(Screen.WorkoutHistory, "Verlauf", Icons.Default.History),
    EXERCISES(Screen.ExerciseLibrary, "Übungen", Icons.Default.FitnessCenter),
}
