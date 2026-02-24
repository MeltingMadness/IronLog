package com.ironlog.app.presentation.plans

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePlanRepo: FakeTrainingPlanRepository
    private lateinit var fakeExerciseRepo: FakeExerciseRepository

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
        fakePlanRepo = FakeTrainingPlanRepository()
        fakeExerciseRepo = FakeExerciseRepository()
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
            exerciseRepository = fakeExerciseRepo
        )
    }

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
}
