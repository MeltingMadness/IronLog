package com.ironlog.app.presentation.plans

import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingPlanListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var planRepo: FakeTrainingPlanRepository
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var workoutRepo: FakeWorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        planRepo = FakeTrainingPlanRepository()
        exerciseRepo = FakeExerciseRepository()
        workoutRepo = FakeWorkoutRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPlans erzeugt keine Exercise N+1 Lookups pro Plan`() = runTest {
        exerciseRepo.addExercise(
            Exercise(
                id = 1L,
                name = "Bankdruecken",
                primaryMuscleGroup = MuscleGroup.BRUST,
                category = ExerciseCategory.LANGHANTEL
            )
        )
        exerciseRepo.addExercise(
            Exercise(
                id = 2L,
                name = "Rudern",
                primaryMuscleGroup = MuscleGroup.RUECKEN,
                category = ExerciseCategory.LANGHANTEL
            )
        )

        val sharedExercises = listOf(
            PlanExercise(exerciseId = 1L, orderIndex = 0),
            PlanExercise(exerciseId = 2L, orderIndex = 1)
        )
        planRepo.savePlan(TrainingPlan(name = "Plan A", exercises = sharedExercises))
        planRepo.savePlan(TrainingPlan(name = "Plan B", exercises = sharedExercises))

        val vm = TrainingPlanListViewModel(planRepo, exerciseRepo, workoutRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.plans.size)
        assertEquals(0, exerciseRepo.getExerciseByIdCallCount)
    }
}
