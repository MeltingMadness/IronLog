package com.ironlog.app.presentation.navigation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.domain.model.*
import com.ironlog.app.domain.repository.*
import com.ironlog.app.presentation.theme.IronLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.ironlog.app.data.local.IronLogDatabase
import com.ironlog.app.data.preferences.appPreferencesDataStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

@RunWith(AndroidJUnit4::class)
class ApprovedWorkoutFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun awaitText(text: String) = ui.waitUntil(20000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    /** Isolation wie in [NavigationSmokeTest]: keine aktive Session aus vorherigen Tests uebernehmen. */
    @Before fun clearAppDatabaseAndDataStore() {
        runBlocking {
            GlobalContext.get().get<IronLogDatabase>().clearAllTables()
            ApplicationProvider.getApplicationContext<Context>().appPreferencesDataStore.edit { it.clear() }
        }
    }
    @Test fun logPartialWorkoutReviewAndApplyOnlyPerformedSets() {
        val plans = GlobalContext.get().get<TrainingPlanRepository>()
        val workouts = GlobalContext.get().get<WorkoutRepository>()
        val exercises = GlobalContext.get().get<ExerciseRepository>()
        val planId = runBlocking {
            val ids = listOf("Review Bankdrücken", "Review Rudern", "Review Kniebeuge").map { name -> exercises.addCustomExercise(Exercise(name=name,primaryMuscleGroup=MuscleGroup.BRUST,category=ExerciseCategory.LANGHANTEL)) }
            plans.savePlan(TrainingPlan(name="Designprüfung 15.09.", exercises=ids.mapIndexed { index,id -> PlanExercise(exerciseId=id,orderIndex=index,targetWeightKg=60.0) }))
        }
        val sessionId = runBlocking { workouts.startWorkout("Designprüfung 15.09.", planId) }
        lateinit var nav: NavHostController
        ui.setContent { IronLogTheme { nav=rememberNavController(); IronLogNavHost(navController=nav, startDestination=Screen.ActiveWorkout.createRoute(sessionId,planId)) } }
        ui.waitUntil(20000) { ui.onAllNodes(hasText("Satz 1 loggen",substring=true)).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(hasText("Satz 1 loggen",substring=true))[0].performScrollTo().performClick()
        ui.waitUntil(20000) { runBlocking { workouts.getSetsForSessionList(sessionId).size == 1 } }
        ui.waitUntil(20000) { ui.onAllNodes(hasText("Satz 2 loggen",substring=true)).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodes(hasSetTextAction())[1].performTextReplacement("9")
        capture("android-logging")
        ui.onNode(hasText("Satz 2 loggen",substring=true)).performScrollTo().performClick()
        ui.waitUntil(20000) { runBlocking { workouts.getSetsForSessionList(sessionId).size == 2 } }
        ui.onNodeWithText("Beenden").performClick()
        awaitText("Training beenden?")
        ui.onNodeWithText("Noch 7 geplante Sätze offen. 2 von 9 absolviert.").assertExists()
        capture("android-partial-finish")
        ui.onNodeWithText("Weitertrainieren").performClick()
        ui.onNodeWithText("Beenden").performClick()
        ui.onNodeWithText("Trotzdem beenden").performClick()
        awaitText("Teiltraining gespeichert")
        capture("android-summary")
        runBlocking {
            assertNotNull(workouts.getSessionById(sessionId)?.endTime)
            assertEquals(listOf(10,9),workouts.getSetsForSessionList(sessionId).map { it.reps })
            assertEquals(1140.0,workouts.getSetsForSessionList(sessionId).sumOf { it.reps*it.weightKg },0.0)
            assertTrue(plans.getPlanById(planId)!!.exercises.all { it.setTargets.isEmpty() })
        }
        ui.onNodeWithText("Planänderungen prüfen").performScrollTo().performClick()
        awaitText("Plan unverändert lassen")
        capture("android-plan-changes")
        ui.onAllNodesWithText("Plan unverändert lassen").filterToOne(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        runBlocking { assertTrue(plans.getPlanById(planId)!!.exercises.all { it.setTargets.isEmpty() }) }
        ui.onNodeWithText("Planänderungen prüfen").performScrollTo().performClick()
        ui.onNodeWithText("Satzwerte übernehmen").performScrollTo().performClick()
        ui.onAllNodesWithText("Satzwerte übernehmen").filterToOne(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        awaitText("Satzwerte übernommen")
        runBlocking {
            val updated=plans.getPlanById(planId)!!.exercises
            assertEquals(listOf(10,9,10),updated[0].setTargets.map { it.reps })
            assertTrue(updated.drop(1).all { it.setTargets.isEmpty() })
        }
        ui.runOnIdle { nav.navigate(Screen.PlanEditor.createRoute(planId)) }
        awaitText("Einzelne Sätze")
        capture("android-plan-editor")
    }
}
