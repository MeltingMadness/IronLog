package com.ironlog.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryAndDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: FakeWorkoutRepository
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var statisticsRepo: FakeStatisticsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
        exerciseRepo = FakeExerciseRepository()
        statisticsRepo = FakeStatisticsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `workout detail nutzt records nicht ueber globales Limit abgeschnitten`() = runTest {
        val now = LocalDateTime.now()

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
                name = "Klimmzug",
                primaryMuscleGroup = MuscleGroup.RUECKEN,
                category = ExerciseCategory.EIGENGEWICHT
            )
        )

        workoutRepo.addSession(
            WorkoutSession(id = 7L, startTime = now.minusDays(1), endTime = now, durationSeconds = 3600),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(id = 101L, sessionId = 7L, exerciseId = 1L, setNumber = 1, reps = 8, weightKg = 80.0, completedAt = now.minusDays(1))
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(id = 102L, sessionId = 7L, exerciseId = 2L, setNumber = 1, reps = 10, weightKg = 0.0, completedAt = now.minusDays(1))
        )

        // 5 neuere Records fuer Exercise 1
        repeat(5) { index ->
            statisticsRepo.addRecord(
                PersonalRecord(
                    id = (index + 1).toLong(),
                    exerciseId = 1L,
                    type = RecordType.MAX_WEIGHT,
                    value = 100.0 + index,
                    achievedAt = now.minusHours(index.toLong())
                )
            )
        }
        // Aelterer, aber relevanter Record fuer Exercise 2
        statisticsRepo.addRecord(
            PersonalRecord(
                id = 99L,
                exerciseId = 2L,
                type = RecordType.MAX_REPS,
                value = 12.0,
                achievedAt = now.minusDays(10)
            )
        )

        val vm = WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 7L)),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val exercise2Detail = vm.uiState.value.exercises.firstOrNull { it.exercise.id == 2L }
        assertTrue(exercise2Detail != null)
        assertTrue(exercise2Detail!!.records.isNotEmpty())
    }

    @Test
    fun `missing session sets notFound and clears isLoading instead of an empty scaffold`() = runTest {
        val vm = WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 12345L)),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
        assertEquals(false, vm.uiState.value.isLoading)
    }
}
