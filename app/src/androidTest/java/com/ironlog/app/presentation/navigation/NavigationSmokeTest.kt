package com.ironlog.app.presentation.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.data.local.IronLogDatabase
import com.ironlog.app.data.preferences.appPreferencesDataStore
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.MuscleGroup
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
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.presentation.progression.ProgressionReviewViewModel
import com.ironlog.app.presentation.theme.IronLogTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinIsolatedContext
import org.koin.core.context.GlobalContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

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

    /**
     * Isolation: Vor JEDEM Test App-DB und DataStore leeren. Der Workout-E2E-Test
     * schreibt ueber die echten Repositories in die echte Datenbank; ohne das
     * Leeren wuerde er die anderen Smoke-Tests vergiften (z.B. eine uebrige
     * aktive Session wuerde aus "Training starten" ein "Training fortsetzen"
     * machen). Die DB wird nur einmal pro Prozess erzeugt (Koin-Single), daher
     * reicht clearAllTables(); der Seed-Callback laeuft nur bei der Erzeugung.
     */
    @Before
    fun clearAppDatabaseAndDataStore() {
        runBlocking {
            GlobalContext.get().get<IronLogDatabase>().clearAllTables()
            ApplicationProvider.getApplicationContext<Context>()
                .appPreferencesDataStore.edit { it.clear() }
        }
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

        // Die Isolation (DB + DataStore werden vor jedem Test geleert) garantiert:
        // es gibt keine aktive Session, also MUSS "Training starten" erscheinen.
        // Ein schreibender Test darf hier kein "Training fortsetzen" hinterlassen.
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Training starten").fetchSemanticsNodes().isNotEmpty()
        }

        val hasContinue =
            composeRule.onAllNodesWithText("Training fortsetzen").fetchSemanticsNodes().isNotEmpty()
        assertFalse("Nach dem Leeren der App-DB darf keine aktive Session existieren", hasContinue)
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
    fun workout_flow_starts_logs_set_finishes_and_appears_in_history() {
        // Echte App-Pfade: DB + DataStore wurden im @Before geleert, daher gibt es
        // keine Uebungen mehr (der Seed laeuft nur bei DB-Erzeugung). Eine eigene
        // Uebung ueber das echte Repository anlegen, damit der Picker etwas zeigt.
        val exerciseId = runBlocking {
            GlobalContext.get().get<ExerciseRepository>().addCustomExercise(
                Exercise(
                    name = "Kniebeuge",
                    primaryMuscleGroup = MuscleGroup.BEINE,
                    category = ExerciseCategory.LANGHANTEL
                )
            )
        }
        assertTrue(exerciseId > 0L)

        lateinit var navController: NavHostController

        composeRule.setContent {
            IronLogTheme {
                navController = rememberNavController()
                IronLogNavHost(navController = navController)
            }
        }

        // 1) Dashboard: Training starten (dank Isolation garantiert "starten",
        //    nicht "fortsetzen").
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Training starten").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Training starten").performClick()
        composeRule.waitForIdle()

        // 2) Plan-Auswahl-Sheet: Freies Training waehlen -> Session startet.
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Freies Training").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Freies Training").performClick()
        composeRule.waitForIdle()

        // 3) Workout-Screen: Uebung hinzufuegen -> Picker oeffnet.
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Übung hinzufügen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Übung hinzufügen").performClick()
        composeRule.waitForIdle()

        // 4) Picker: angelegte Uebung waehlen.
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Kniebeuge").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kniebeuge").performClick()
        composeRule.waitForIdle()

        // 5) Satz loggen: exakt 3 Eingabefelder (Gewicht, Wdh, Intensitaet) -
        //    der Picker muss zu (kein Suchfeld mehr), sonst waere die Reihenfolge
        //    nicht determiniert.
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 3
        }
        val setInputs = composeRule.onAllNodes(hasSetTextAction())
        setInputs[0].performTextInput("100") // Gewicht in kg
        setInputs[1].performTextInput("10") // Wiederholungen
        composeRule.onNodeWithContentDescription("Loggen").performClick()
        composeRule.waitForIdle()

        // 6) Beenden: Top-Bar-Aktion, dann Dialog bestaetigen. Der Dialog
        //    (eigenes Fenster) kommt in der Traversierung nach dem Hauptinhalt,
        //    daher ist der zweite "Beenden"-Knoten der Bestaetigen-Button.
        composeRule.onNodeWithText("Beenden").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Training beenden?").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "Beenden muss genau in Top-Bar und Dialog-Bestaetigen erscheinen",
            2,
            composeRule.onAllNodesWithText("Beenden").fetchSemanticsNodes().size
        )
        composeRule.onAllNodesWithText("Beenden")[1].performClick()
        composeRule.waitForIdle()

        // 7) Zurueck auf dem Dashboard (Finish poppt zum Dashboard zurueck).
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Training starten").fetchSemanticsNodes().isNotEmpty()
        }

        // 8) Verlauf pruefen: Session mit heutigem Datum muss erscheinen.
        composeRule.onNodeWithTag("bottom_nav_history", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Trainingsverlauf").fetchSemanticsNodes().isNotEmpty()
        }
        val expectedDate = LocalDateTime.now().format(DateFormatting.DATE_FULL)
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText(expectedDate).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "Der Verlauf darf nach dem Workout nicht leer sein",
            0,
            composeRule.onAllNodesWithText("Noch keine Trainings").fetchSemanticsNodes().size
        )
    }

    @Test
    fun progression_review_route_shows_review_top_bar_without_stopping_application_koin() {
        lateinit var navController: NavHostController
        val showReview = mutableStateOf(true)
        val applicationKoin = GlobalContext.get()
        val progressionReviewKoin = koinApplication {
            modules(progressionReviewModule)
        }

        composeRule.setContent {
            if (showReview.value) {
                KoinIsolatedContext(context = progressionReviewKoin) {
                    IronLogTheme {
                        navController = rememberNavController()
                        IronLogNavHost(
                            navController = navController,
                            startDestination = Screen.ProgressionReview.createRoute(0L)
                        )
                    }
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

        composeRule.runOnIdle {
            showReview.value = false
        }
        composeRule.waitForIdle()

        assertSame(applicationKoin, GlobalContext.get())
        progressionReviewKoin.close()
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

