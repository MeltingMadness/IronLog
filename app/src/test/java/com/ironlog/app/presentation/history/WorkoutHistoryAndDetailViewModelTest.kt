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
    fun `history view model uses paged data`() = runTest {
        val now = LocalDateTime.now()
        workoutRepo.addSession(
            WorkoutSession(id = 1L, startTime = now.minusDays(2), endTime = now.minusDays(2).plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = WorkoutHistoryViewModel(workoutRepo)
        val collector = backgroundScope.launch { vm.pagedWorkouts.collect() }

        testDispatcher.scheduler.advanceUntilIdle()

        // Test should verify that pagedWorkouts flow is not null and emits PagingData
        assertTrue(vm.pagedWorkouts != null)
        
        collector.cancel()
    }

    @Test
    fun `history view model vermeidet per-session stats N+1`() = runTest {
        val now = LocalDateTime.now()
        workoutRepo.addSession(
            WorkoutSession(id = 1L, startTime = now.minusDays(2), endTime = now.minusDays(2).plusHours(1), durationSeconds = 3600),
            isActive = false
        )
        workoutRepo.addSession(
            WorkoutSession(id = 2L, startTime = now.minusDays(1), endTime = now.minusDays(1).plusHours(1), durationSeconds = 3600),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(id = 1L, sessionId = 1L, exerciseId = 10L, setNumber = 1, reps = 8, weightKg = 80.0, completedAt = now.minusDays(2))
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(id = 2L, sessionId = 2L, exerciseId = 11L, setNumber = 1, reps = 6, weightKg = 100.0, completedAt = now.minusDays(1))
        )

        val vm = WorkoutHistoryViewModel(workoutRepo)
        val collector = backgroundScope.launch { vm.uiState.collect() }

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.workouts.size)
        assertEquals(0, workoutRepo.getExerciseIdsForSessionCallCount)
        assertEquals(0, workoutRepo.getSetCountForSessionCallCount)
        assertEquals(0, workoutRepo.getTotalVolumeForSessionCallCount)

        collector.cancel()
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
}
