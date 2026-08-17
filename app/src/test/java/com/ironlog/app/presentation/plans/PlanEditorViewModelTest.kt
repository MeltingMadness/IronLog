package com.ironlog.app.presentation.plans

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePlanRepo: RecordingTrainingPlanRepository
    private lateinit var fakeExerciseRepo: FakeExerciseRepository
    private lateinit var preferencesRepository: FakeAppPreferencesRepository

    private val mockExercise1 = Exercise(
        id = 1L,
        name = "Bankdrücken",
        primaryMuscleGroup = MuscleGroup.BRUST,
        category = ExerciseCategory.LANGHANTEL
    )
    
    private val mockExercise2 = Exercise(
        id = 2L,
        name = "Kniebeuge",
        primaryMuscleGroup = MuscleGroup.BEINE,
        category = ExerciseCategory.LANGHANTEL
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePlanRepo = RecordingTrainingPlanRepository()
        fakeExerciseRepo = FakeExerciseRepository()
        preferencesRepository = FakeAppPreferencesRepository()
        fakeExerciseRepo.addExercise(mockExercise1)
        fakeExerciseRepo.addExercise(mockExercise2)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(planId: Long = 0L): PlanEditorViewModel {
        return PlanEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("planId" to planId)),
            planRepository = fakePlanRepo,
            exerciseRepository = fakeExerciseRepo,
            appPreferencesRepository = preferencesRepository
        )
    }

    private suspend fun seedPlanExercise(
        config: ProgressionConfig,
        targetWeightKg: Double = 100.0
    ): Long = fakePlanRepo.seed(
        TrainingPlan(
            name = "Bestehender Plan",
            exercises = listOf(
                PlanExercise(
                    exerciseId = mockExercise1.id,
                    exerciseName = mockExercise1.name,
                    orderIndex = 0,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = targetWeightKg,
                    progressionConfig = config
                )
            )
        )
    )

    @Test
    fun `updatePlanName correctly updates ui state`() = runTest {
        val viewModel = createViewModel()
        
        viewModel.updatePlanName("Neuer Trainingsplan")
        
        assertEquals("Neuer Trainingsplan", viewModel.uiState.value.planName)
    }

    @Test
    fun `addExercise adds exercise to the list`() = runTest {
        val viewModel = createViewModel()
        
        viewModel.addExercise(mockExercise1)
        
        val exercises = viewModel.uiState.value.exercises
        assertEquals(1, exercises.size)
        assertEquals(mockExercise1.id, exercises[0].exercise.id)
        assertEquals(3, exercises[0].planExercise.targetSets)
        assertEquals(10, exercises[0].planExercise.targetReps)
    }

    @Test
    fun `new exercises default to manual progression`() = runTest {
        val viewModel = createViewModel()

        viewModel.addExercise(mockExercise1)

        assertEquals(
            ProgressionConfig.Manual(),
            viewModel.uiState.value.exercises.single().planExercise.progressionConfig
        )
    }

    @Test
    fun `active scheme drafts use deterministic defaults from the exercise target`() = runTest {
        val activeSchemes = listOf(
            ProgressionScheme.LINEAR,
            ProgressionScheme.DOUBLE,
            ProgressionScheme.TOTAL_REPS,
            ProgressionScheme.RPE_RIR
        )

        activeSchemes.forEach { scheme ->
            val viewModel = createViewModel()
            viewModel.addExercise(mockExercise1)
            viewModel.openProgressionEditor(0)

            viewModel.selectProgressionScheme(scheme)

            val draft = requireNotNull(viewModel.uiState.value.progressionEditor)
            assertEquals(scheme, draft.scheme)
            assertEquals("2.5", draft.step)
            assertEquals("10", draft.minReps)
            assertEquals("12", draft.maxReps)
            assertEquals("30", draft.totalReps)
            assertEquals("8", draft.targetRpe)
            assertEquals("0.5", draft.rpeTolerance)
            assertEquals("2", draft.stallThreshold)
            assertEquals("10", draft.backoffPercent)
            assertEquals(UnitSystem.METRIC, draft.unitSystem)
            assertNull(draft.originalStep)
            assertTrue(draft.stepWasEdited)
        }
    }

    @Test
    fun `metric step accepts decimal comma and stores canonical kilograms`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.LINEAR)

        viewModel.updateProgressionField(ProgressionField.STEP, "2,75")
        viewModel.saveProgressionEditor()

        val config = viewModel.uiState.value.exercises.single()
            .planExercise.progressionConfig as ProgressionConfig.Linear
        assertEquals(2.75, config.step.originalValue, 0.0)
        assertEquals(UnitSystem.METRIC, config.step.originalUnit)
        assertEquals(2.75, config.step.kilograms, 0.0)
    }

    @Test
    fun `imperial step saves original pounds and canonical kilograms`() = runTest {
        preferencesRepository.updateUnitSystem(UnitSystem.IMPERIAL)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.LINEAR)

        val defaultDraft = requireNotNull(viewModel.uiState.value.progressionEditor)
        assertEquals("5", defaultDraft.step)
        assertEquals(UnitSystem.IMPERIAL, defaultDraft.unitSystem)
        assertNull(defaultDraft.originalStep)
        assertTrue(defaultDraft.stepWasEdited)

        viewModel.updateProgressionField(ProgressionField.STEP, "5")
        viewModel.saveProgressionEditor()

        val config = viewModel.uiState.value.exercises.single()
            .planExercise.progressionConfig as ProgressionConfig.Linear
        assertEquals(5.0, config.step.originalValue, 0.0)
        assertEquals(UnitSystem.IMPERIAL, config.step.originalUnit)
        assertEquals(
            WeightFormatting.convertToKg(5.0, UnitSystem.IMPERIAL),
            config.step.kilograms,
            0.000001
        )
    }

    @Test
    fun `opening and saving an existing step in another display unit is lossless`() = runTest {
        val original = WeightStep(
            originalValue = 5.0,
            originalUnit = UnitSystem.IMPERIAL,
            kilograms = WeightFormatting.convertToKg(5.0, UnitSystem.IMPERIAL)
        )
        val planId = seedPlanExercise(ProgressionConfig.Linear(step = original))
        val viewModel = createViewModel(planId)
        advanceUntilIdle()

        viewModel.openProgressionEditor(0)

        val draft = requireNotNull(viewModel.uiState.value.progressionEditor)
        assertEquals(UnitSystem.METRIC, draft.unitSystem)
        assertEquals(
            original.kilograms,
            draft.step.replace(',', '.').toDouble(),
            0.000001
        )
        assertSame(original, draft.originalStep)
        assertFalse(draft.stepWasEdited)

        viewModel.saveProgressionEditor()

        val saved = viewModel.uiState.value.exercises.single()
            .planExercise.progressionConfig as ProgressionConfig.Linear
        assertSame(original, saved.step)
    }

    @Test
    fun `preference change does not reinterpret a dirty progression draft`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.LINEAR)
        viewModel.updateProgressionField(ProgressionField.STEP, "3,5")

        preferencesRepository.updateUnitSystem(UnitSystem.IMPERIAL)
        advanceUntilIdle()

        val dirtyDraft = requireNotNull(viewModel.uiState.value.progressionEditor)
        assertEquals(UnitSystem.IMPERIAL, viewModel.uiState.value.unitSystem)
        assertEquals(UnitSystem.METRIC, dirtyDraft.unitSystem)
        assertEquals("3,5", dirtyDraft.step)

        viewModel.saveProgressionEditor()

        val saved = viewModel.uiState.value.exercises.single()
            .planExercise.progressionConfig as ProgressionConfig.Linear
        assertEquals(3.5, saved.step.originalValue, 0.0)
        assertEquals(UnitSystem.METRIC, saved.step.originalUnit)
        assertEquals(3.5, saved.step.kilograms, 0.0)

        viewModel.openProgressionEditor(0)
        val reopened = requireNotNull(viewModel.uiState.value.progressionEditor)
        assertEquals(UnitSystem.IMPERIAL, reopened.unitSystem)
        assertSame(saved.step, reopened.originalStep)
        assertFalse(reopened.stepWasEdited)
    }

    @Test
    fun `invalid rpe config stays in editor and maps validator paths to exact fields`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.RPE_RIR)
        viewModel.updateProgressionField(ProgressionField.TARGET_RPE, "11")
        viewModel.updateProgressionField(ProgressionField.RPE_TOLERANCE, "3")

        viewModel.saveProgressionEditor()

        val draft = requireNotNull(viewModel.uiState.value.progressionEditor)
        assertTrue(draft.errors.containsKey(ProgressionField.TARGET_RPE))
        assertTrue(draft.errors.containsKey(ProgressionField.RPE_TOLERANCE))
        assertTrue(
            viewModel.uiState.value.exercises.single()
                .planExercise.progressionConfig is ProgressionConfig.Manual
        )
    }

    @Test
    fun `double progression reports all simultaneous parse errors without changing plan state`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.DOUBLE)
        viewModel.updateProgressionField(ProgressionField.STEP, "")
        viewModel.updateProgressionField(ProgressionField.MIN_REPS, "")
        viewModel.updateProgressionField(ProgressionField.MAX_REPS, "")
        viewModel.updateProgressionField(ProgressionField.STALL_THRESHOLD, "")
        viewModel.updateProgressionField(ProgressionField.BACKOFF_PERCENT, "")

        viewModel.saveProgressionEditor()

        val draft = requireNotNull(viewModel.uiState.value.progressionEditor)
        assertEquals(
            setOf(
                ProgressionField.STEP,
                ProgressionField.MIN_REPS,
                ProgressionField.MAX_REPS,
                ProgressionField.STALL_THRESHOLD,
                ProgressionField.BACKOFF_PERCENT
            ),
            draft.errors.keys
        )
        assertEquals(
            ProgressionConfig.Manual(),
            viewModel.uiState.value.exercises.single().planExercise.progressionConfig
        )
    }

    @Test
    fun `cancel closes the editor without changing the exercise config`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.LINEAR)
        viewModel.updateProgressionField(ProgressionField.STEP, "4")

        viewModel.dismissProgressionEditor()

        assertNull(viewModel.uiState.value.progressionEditor)
        assertTrue(
            viewModel.uiState.value.exercises.single()
                .planExercise.progressionConfig is ProgressionConfig.Manual
        )
    }

    @Test
    fun `manual scheme changes an active config only after apply`() = runTest {
        val original = ProgressionConfig.Linear(
            step = WeightStep(2.5, UnitSystem.METRIC, 2.5),
            failurePolicy = FailurePolicy(stallThreshold = 3, backoffPercent = 12.0)
        )
        val planId = seedPlanExercise(original)
        val viewModel = createViewModel(planId)
        advanceUntilIdle()
        viewModel.openProgressionEditor(0)

        viewModel.selectProgressionScheme(ProgressionScheme.MANUAL)

        assertEquals(
            original,
            viewModel.uiState.value.exercises.single().planExercise.progressionConfig
        )

        viewModel.saveProgressionEditor()

        assertNull(viewModel.uiState.value.progressionEditor)
        assertEquals(
            ProgressionConfig.Manual(),
            viewModel.uiState.value.exercises.single().planExercise.progressionConfig
        )
    }

    @Test
    fun `savePlan revalidates configs against edited targets before repository save`() = runTest {
        val viewModel = createViewModel()
        viewModel.updatePlanName("Ungültiger Plan")
        viewModel.addExercise(mockExercise1)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.DOUBLE)
        viewModel.saveProgressionEditor()
        viewModel.updateTargetReps(0, 20)

        viewModel.savePlan()
        advanceUntilIdle()

        assertEquals(0, fakePlanRepo.saveCallCount)
        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(
            "Progression für ${mockExercise1.name} ist unvollständig",
            viewModel.uiState.value.error
        )
    }

    @Test
    fun `reorder preserves each exercises progression config`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)
        viewModel.openProgressionEditor(0)
        viewModel.selectProgressionScheme(ProgressionScheme.LINEAR)
        viewModel.saveProgressionEditor()
        viewModel.openProgressionEditor(1)
        viewModel.selectProgressionScheme(ProgressionScheme.TOTAL_REPS)
        viewModel.saveProgressionEditor()

        viewModel.moveDown(0)

        assertTrue(
            viewModel.uiState.value.exercises[0]
                .planExercise.progressionConfig is ProgressionConfig.TotalReps
        )
        assertTrue(
            viewModel.uiState.value.exercises[1]
                .planExercise.progressionConfig is ProgressionConfig.Linear
        )
    }

    @Test
    fun `target weight input uses display unit and stores canonical kilograms`() = runTest {
        preferencesRepository.updateUnitSystem(UnitSystem.IMPERIAL)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.addExercise(mockExercise1)

        viewModel.updateTargetWeightDisplay(0, 220.46226218)

        assertEquals(UnitSystem.IMPERIAL, viewModel.uiState.value.unitSystem)
        assertEquals(
            100.0,
            viewModel.uiState.value.exercises.single().planExercise.targetWeightKg,
            0.000001
        )
    }

    @Test
    fun `removeExercise removes the exercise at correct index`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)
        
        viewModel.removeExercise(0)
        
        val exercises = viewModel.uiState.value.exercises
        assertEquals(1, exercises.size)
        assertEquals(mockExercise2.id, exercises[0].exercise.id)
    }

    @Test
    fun `moveUp and moveDown swap exercise order correctly`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)
        
        // Move Kniebeuge up
        viewModel.moveUp(1)
        
        var exercises = viewModel.uiState.value.exercises
        assertEquals(mockExercise2.id, exercises[0].exercise.id)
        assertEquals(mockExercise1.id, exercises[1].exercise.id)
        
        // Move Kniebeuge down again
        viewModel.moveDown(0)
        
        exercises = viewModel.uiState.value.exercises
        assertEquals(mockExercise1.id, exercises[0].exercise.id)
        assertEquals(mockExercise2.id, exercises[1].exercise.id)
    }

    @Test
    fun `groupWithPrevious groups adjacent exercises into same superset`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)

        viewModel.groupWithPrevious(1)

        val exercises = viewModel.uiState.value.exercises
        val firstGroupId = exercises[0].planExercise.supersetGroupId
        assertNotNull(firstGroupId)
        assertEquals(firstGroupId, exercises[1].planExercise.supersetGroupId)
    }

    @Test
    fun `ungroup removes element and normalizes invalid supersets`() = runTest {
        val viewModel = createViewModel()
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)
        viewModel.addExercise(mockExercise1)

        viewModel.groupWithPrevious(1)
        viewModel.groupWithPrevious(2)
        viewModel.ungroup(1)

        val groups = viewModel.uiState.value.exercises.map { it.planExercise.supersetGroupId }
        assertEquals(listOf(null, null, null), groups)
    }

    @Test
    fun `savePlan persists compact superset group ids`() = runTest {
        val viewModel = createViewModel()
        viewModel.updatePlanName("Superset Plan")
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)
        viewModel.addExercise(mockExercise1)
        viewModel.addExercise(mockExercise2)

        viewModel.groupWithPrevious(1)
        viewModel.groupWithPrevious(3)
        viewModel.savePlan()
        advanceUntilIdle()

        val savedPlan = fakePlanRepo.getAllPlans().first().first()
        val savedGroupIds = savedPlan.exercises.map { it.supersetGroupId }
        assertEquals(listOf(1, 1, 2, 2), savedGroupIds)
    }

    @Test
    fun `savePlan stores the plan in repository and updates state`() = runTest {
        val viewModel = createViewModel()
        viewModel.updatePlanName("Mein Plan")
        viewModel.addExercise(mockExercise1)
        
        viewModel.savePlan()
        advanceUntilIdle() // Wait for coroutine to finish
        
        assertTrue(viewModel.uiState.value.isSaved)
        val savedPlans = fakePlanRepo.getAllPlans().first()
        // StateFlow initially is empty, after save it has 1
        assertEquals(1, savedPlans.size)
        assertEquals("Mein Plan", savedPlans[0].name)
        assertEquals(1, savedPlans[0].exercises.size)
        assertEquals(mockExercise1.id, savedPlans[0].exercises[0].exerciseId)
        // Check order indices
        assertEquals(0, savedPlans[0].exercises[0].orderIndex)
    }

    @Test
    fun `loading a missing plan sets notFound and clears isLoading instead of spinning forever`() = runTest {
        val viewModel = createViewModel(planId = 9999L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.notFound)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class RecordingTrainingPlanRepository : TrainingPlanRepository {
        private val delegate = FakeTrainingPlanRepository()
        var saveCallCount: Int = 0
            private set

        override fun getAllPlans(): Flow<List<TrainingPlan>> = delegate.getAllPlans()

        override suspend fun getPlanById(id: Long): TrainingPlan? = delegate.getPlanById(id)

        override suspend fun savePlan(plan: TrainingPlan): Long {
            saveCallCount++
            return delegate.savePlan(plan)
        }

        override suspend fun deletePlan(planId: Long) = delegate.deletePlan(planId)

        suspend fun seed(plan: TrainingPlan): Long = delegate.savePlan(plan)
    }
}
