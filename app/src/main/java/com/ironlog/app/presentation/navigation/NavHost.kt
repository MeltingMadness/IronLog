package com.ironlog.app.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.ironlog.app.presentation.plans.MetaPlanEditorScreen
import com.ironlog.app.presentation.plans.MetaPlanListScreen
import com.ironlog.app.presentation.plans.PlanEditorScreen
import com.ironlog.app.presentation.plans.TrainingPlanListScreen
import com.ironlog.app.presentation.settings.SettingsScreen
import com.ironlog.app.presentation.statistics.ExerciseStatsScreen
import com.ironlog.app.presentation.theme.ironLogMotion
import com.ironlog.app.presentation.workout.ActiveWorkoutScreen

@Composable
fun IronLogNavHost(navController: NavHostController) {
    val motion = ironLogMotion

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        enterTransition = {
            if (motion.reduced) {
                fadeIn(animationSpec = snap())
            } else {
                fadeIn(animationSpec = tween(motion.normalMillis)) +
                    slideInHorizontally(
                        animationSpec = tween(motion.normalMillis),
                        initialOffsetX = { fullWidth -> fullWidth / 4 }
                    )
            }
        },
        exitTransition = {
            if (motion.reduced) {
                fadeOut(animationSpec = snap())
            } else {
                fadeOut(animationSpec = tween(motion.fastMillis))
            }
        },
        popEnterTransition = {
            if (motion.reduced) {
                fadeIn(animationSpec = snap())
            } else {
                fadeIn(animationSpec = tween(motion.normalMillis)) +
                    slideInHorizontally(
                        animationSpec = tween(motion.normalMillis),
                        initialOffsetX = { fullWidth -> -fullWidth / 4 }
                    )
            }
        },
        popExitTransition = {
            if (motion.reduced) {
                fadeOut(animationSpec = snap())
            } else {
                fadeOut(animationSpec = tween(motion.fastMillis)) +
                    slideOutHorizontally(
                        animationSpec = tween(motion.fastMillis),
                        targetOffsetX = { fullWidth -> fullWidth / 4 }
                    )
            }
        }
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onStartWorkout = { sessionId, planId, metaPlanId ->
                    navController.navigate(
                        Screen.ActiveWorkout.createRoute(
                            sessionId = sessionId,
                            planId = planId ?: 0L,
                            metaPlanId = metaPlanId ?: 0L
                        )
                    )
                },
                onContinueWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId))
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ActiveWorkout.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("planId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("metaPlanId") { type = NavType.LongType; defaultValue = 0L }
            )
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

        composable(Screen.TrainingPlanList.route) {
            TrainingPlanListScreen(
                onCreatePlan = {
                    navController.navigate(Screen.PlanEditorNew.route)
                },
                onEditPlan = { planId ->
                    navController.navigate(Screen.PlanEditor.createRoute(planId))
                },
                onStartWorkout = { sessionId, planId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId, planId))
                },
                onOpenMetaPlans = {
                    navController.navigate(Screen.MetaPlanList.route)
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

        composable(Screen.MetaPlanList.route) {
            MetaPlanListScreen(
                onBack = { navController.popBackStack() },
                onCreateMetaPlan = { navController.navigate(Screen.MetaPlanEditorNew.route) },
                onEditMetaPlan = { metaPlanId ->
                    navController.navigate(Screen.MetaPlanEditor.createRoute(metaPlanId))
                }
            )
        }

        composable(Screen.MetaPlanEditorNew.route) {
            MetaPlanEditorScreen(
                metaPlanId = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MetaPlanEditor.route,
            arguments = listOf(navArgument("metaPlanId") { type = NavType.LongType })
        ) { backStackEntry ->
            MetaPlanEditorScreen(
                metaPlanId = backStackEntry.arguments?.getLong("metaPlanId"),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
