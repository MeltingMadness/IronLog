package com.ironlog.app.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.presentation.theme.IronLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bottom_nav_clicks_switch_top_level_destinations() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            IronLogTheme {
                navController = rememberNavController()
                val navBackStackEntry = navController.currentBackStackEntryAsState().value
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute in listOf(
                    Screen.Dashboard.route,
                    Screen.WorkoutHistory.route,
                    Screen.ExerciseLibrary.route,
                    Screen.TrainingPlanList.route
                )

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        IronLogNavHost(navController = navController)
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(Screen.Dashboard.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.onNodeWithTag("bottom_nav_plans", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Screen.TrainingPlanList.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.onNodeWithTag("bottom_nav_history", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Screen.WorkoutHistory.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.onNodeWithTag("bottom_nav_exercises", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Screen.ExerciseLibrary.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.onNodeWithTag("bottom_nav_home", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Screen.Dashboard.route, navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun dashboard_to_history_detail_stats_smoke_with_shared_transition_path() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            IronLogTheme {
                navController = rememberNavController()
                IronLogNavHost(navController = navController)
            }
        }

        composeRule.runOnIdle {
            assertEquals(Screen.Dashboard.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.runOnIdle {
            navController.navigate(Screen.WorkoutHistory.route)
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText("Trainingsverlauf").fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.runOnIdle {
            assertEquals(Screen.WorkoutHistory.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.runOnIdle {
            navController.navigate(Screen.WorkoutDetail.createRoute(sessionId = 1L))
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText("Trainingsdetails").fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.runOnIdle {
            assertEquals(Screen.WorkoutDetail.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.runOnIdle {
            navController.navigate(Screen.ExerciseStats.createRoute(exerciseId = 1L))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Screen.ExerciseStats.route, navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun dashboard_to_settings_smoke() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            IronLogTheme {
                navController = rememberNavController()
                IronLogNavHost(navController = navController)
            }
        }

        composeRule.runOnIdle {
            assertEquals(Screen.Dashboard.route, navController.currentBackStackEntry?.destination?.route)
        }

        composeRule.runOnIdle {
            navController.navigate(Screen.Settings.route)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Screen.Settings.route, navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun dashboard_has_primary_action_and_settings_accessibility_label() {
        composeRule.setContent {
            IronLogTheme {
                val navController = rememberNavController()
                IronLogNavHost(navController = navController)
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Einstellungen").assertIsDisplayed()

        val hasStart = composeRule.onAllNodesWithText("Training starten").fetchSemanticsNodes().isNotEmpty()
        val hasContinue = composeRule.onAllNodesWithText("Training fortsetzen").fetchSemanticsNodes().isNotEmpty()
        assertTrue(hasStart || hasContinue)
    }

    @Test
    fun settings_shows_appearance_controls() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            IronLogTheme {
                navController = rememberNavController()
                IronLogNavHost(navController = navController)
            }
        }

        composeRule.runOnIdle {
            navController.navigate(Screen.Settings.route)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Theme-Modus").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic Color").assertIsDisplayed()
        composeRule.onNodeWithText("Reduzierte Animationen").assertIsDisplayed()
    }
}

