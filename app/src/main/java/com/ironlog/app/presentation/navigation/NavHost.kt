package com.ironlog.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ironlog.app.presentation.dashboard.DashboardScreen
import com.ironlog.app.presentation.exercises.ExerciseLibraryScreen
import com.ironlog.app.presentation.history.WorkoutDetailScreen
import com.ironlog.app.presentation.history.WorkoutHistoryScreen
import com.ironlog.app.presentation.plans.PlanEditorScreen
import com.ironlog.app.presentation.plans.TrainingPlanListScreen
import com.ironlog.app.presentation.statistics.ExerciseStatsScreen
import com.ironlog.app.presentation.workout.ActiveWorkoutScreen

@Composable
fun IronLogNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId))
                },
                onContinueWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.ActiveWorkout.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            ActiveWorkoutScreen(
                onWorkoutFinished = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
            )
        }

        composable(Screen.ExerciseLibrary.route) {
            ExerciseLibraryScreen(
                onExerciseClick = { exerciseId ->
                    navController.navigate(Screen.ExerciseStats.createRoute(exerciseId))
                }
            )
        }

        composable(Screen.WorkoutHistory.route) {
            WorkoutHistoryScreen(
                onWorkoutClick = { sessionId ->
                    navController.navigate(Screen.WorkoutDetail.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.WorkoutDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            WorkoutDetailScreen(
                onBack = { navController.popBackStack() },
                onExerciseClick = { exerciseId ->
                    navController.navigate(Screen.ExerciseStats.createRoute(exerciseId))
                }
            )
        }

        composable(
            route = Screen.ExerciseStats.route,
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
        ) {
            ExerciseStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Training Plans
        composable(Screen.TrainingPlanList.route) {
            TrainingPlanListScreen(
                onCreatePlan = {
                    navController.navigate(Screen.PlanEditorNew.route)
                },
                onEditPlan = { planId ->
                    navController.navigate(Screen.PlanEditor.createRoute(planId))
                },
                onStartWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId))
                }
            )
        }

        composable(Screen.PlanEditorNew.route) {
            PlanEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PlanEditor.route,
            arguments = listOf(navArgument("planId") { type = NavType.LongType })
        ) {
            PlanEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
