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
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.presentation.progression.ProgressionReviewViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val progressionRepository = NavigationSmokeProgressionRepository()
    private val preferencesRepository = NavigationSmokePreferencesRepository()
    private val progressionReviewModule = module {
        single<ProgressionRepository> { progressionRepository }
        single<AppPreferencesRepository> { preferencesRepository }
        viewModelOf(::ProgressionReviewViewModel)
    }

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

        composeRule.onNodeWithContentDescription("Einstellungen").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Training starten").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Training fortsetzen").fetchSemanticsNodes().isNotEmpty()
        }

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

    @Test
    fun progression_review_route_shows_review_top_bar() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            KoinApplication(
                application = {
                    modules(progressionReviewModule)
                }
            ) {
                IronLogTheme {
                    navController = rememberNavController()
                    IronLogNavHost(
                        navController = navController,
                        startDestination = Screen.ProgressionReview.createRoute(0L)
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("progression_review_top_bar").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                Screen.ProgressionReview.route,
                navController.currentBackStackEntry?.destination?.route
            )
        }
    }
}

private class NavigationSmokeProgressionRepository : ProgressionRepository {
    override fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>> =
        MutableStateFlow(emptyList())

    override fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>> =
        MutableStateFlow(emptyList())

    override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)

    override suspend fun generateOutcomesForSession(sessionId: Long) =
        ProgressionGenerationResult(insertedCount = 0, reviewItemCount = 0, pendingCount = 0)

    override suspend fun generateMissingOutcomes(): Int = 0

    override suspend fun reconcileOutstandingSuggestions(): Set<Long> = emptySet()

    override suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult = ProgressionDecisionResult.Accepted(finalTargetsBySuggestionId.keys)

    override suspend fun rejectSuggestion(suggestionId: Long) = Unit
}

private class NavigationSmokePreferencesRepository : AppPreferencesRepository {
    private val state = MutableStateFlow(AppPreferences())
    override val preferences: Flow<AppPreferences> = state

    override suspend fun updateUnitSystem(unitSystem: UnitSystem) {
        state.value = state.value.copy(unitSystem = unitSystem)
    }

    override suspend fun updateWeekStart(weekStart: WeekStart) {
        state.value = state.value.copy(weekStart = weekStart)
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun updateThemeScheme(themeScheme: ThemeScheme) {
        state.value = state.value.copy(themeScheme = themeScheme)
    }

    override suspend fun updateUseDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColor = enabled)
    }

    override suspend fun updateReducedMotion(enabled: Boolean) {
        state.value = state.value.copy(reducedMotion = enabled)
    }

    override suspend fun updateDefaultWarmupFlag(enabled: Boolean) {
        state.value = state.value.copy(defaultWarmupFlag = enabled)
    }

    override suspend fun updateTimerKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(timerKeepScreenOn = enabled)
    }

    override suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        state.value = state.value.copy(betaDiagnosticsOptIn = enabled)
    }

    override suspend fun updateReminderConfig(config: ReminderConfig) {
        state.value = state.value.copy(reminderConfig = config)
    }

    override suspend fun updateIntensitySystem(intensitySystem: IntensitySystem) {
        state.value = state.value.copy(intensitySystem = intensitySystem)
    }

    override suspend fun updateShareWeightHistoryAcrossContexts(enabled: Boolean) {
        state.value = state.value.copy(shareWeightHistoryAcrossContexts = enabled)
    }
}

