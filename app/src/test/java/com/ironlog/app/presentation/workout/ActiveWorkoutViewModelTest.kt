package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ActiveWorkoutViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var workoutRepo: FakeWorkoutRepository
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var statsRepo: FakeStatisticsRepository

    private val testExercise = Exercise(
        id = 1L,
        name = "Bankdrücken",
        primaryMuscleGroup = MuscleGroup.BRUST,
        category = ExerciseCategory.LANGHANTEL
    )

    private val sessionId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
        exerciseRepo = FakeExerciseRepository()
        statsRepo = FakeStatisticsRepository()

        exerciseRepo.addExercise(testExercise)
        workoutRepo.addSession(
            WorkoutSession(id = sessionId, startTime = LocalDateTime.now()),
            isActive = true
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ActiveWorkoutViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId))
        return ActiveWorkoutViewModel(savedStateHandle, workoutRepo, exerciseRepo, statsRepo)
    }

    // --- Log Set ---

    @Test
    fun `logSet erstellt Satz korrekt`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(1, sets.size)
        assertEquals(10, sets[0].reps)
        assertEquals(80.0, sets[0].weightKg, 0.01)
        assertEquals(1, sets[0].setNumber)
    }

    @Test
    fun `logSet prueft auf PR`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 100.0)

        // Should have checked MAX_WEIGHT, MAX_REPS, MAX_E1RM, MAX_VOLUME
        assertTrue(statsRepo.updatedRecords.any { it.first == 1L && it.second == RecordType.MAX_WEIGHT })
        assertTrue(statsRepo.updatedRecords.any { it.first == 1L && it.second == RecordType.MAX_REPS })
        assertTrue(statsRepo.updatedRecords.any { it.first == 1L && it.second == RecordType.MAX_E1RM })
    }

    @Test
    fun `E1RM Epley Berechnung korrekt`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 100.0)

        // E1RM = 100 * (1 + 10/30) = 100 * 1.333... = 133.33...
        val e1rmRecord = statsRepo.updatedRecords.find { it.second == RecordType.MAX_E1RM }
        assertTrue(e1rmRecord != null)
        assertEquals(133.33, e1rmRecord!!.third, 0.01)
    }

    @Test
    fun `E1RM nicht berechnet bei 1 Wiederholung`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 1, weightKg = 100.0)

        val e1rmRecords = statsRepo.updatedRecords.filter { it.second == RecordType.MAX_E1RM }
        assertTrue(e1rmRecords.isEmpty())
    }

    @Test
    fun `Warmup-Satz keine PR-Pruefung`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 50.0, isWarmup = true)

        // No PR checks for warmup
        assertTrue(statsRepo.updatedRecords.isEmpty())
    }

    // --- Finish Workout ---

    @Test
    fun `finishWorkout beendet Session`() = runTest {
        val vm = createViewModel()

        vm.finishWorkout()

        val activeSession = workoutRepo.getActiveSession()
        assertTrue(activeSession == null)
    }

    // --- Delete Set ---

    @Test
    fun `deleteSet entfernt Satz`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(1, sets.size)

        vm.deleteSet(sets[0].id)

        val setsAfter = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(0, setsAfter.size)
    }
}
