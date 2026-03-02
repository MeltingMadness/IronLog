package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var planRepo: FakeTrainingPlanRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository

    private val testExercise = Exercise(
        id = 1L,
        name = "Bankdruecken",
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
        planRepo = FakeTrainingPlanRepository()
        prefsRepo = FakeAppPreferencesRepository()

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
        return ActiveWorkoutViewModel(savedStateHandle, workoutRepo, exerciseRepo, statsRepo, planRepo, prefsRepo)
    }

    // --- Log Set ---

    @Test
    fun `logSet speichert rpe korrekt wenn IntensitySystem ist RPE`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "8.5")

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(8.5, sets[0].rpe)
    }

    @Test
    fun `logSet rechnet rir um in rpe wenn IntensitySystem ist RIR`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RIR)
        val vm = createViewModel()

        // 1.5 RIR = 8.5 RPE
        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "1.5")

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(8.5, sets[0].rpe)
    }

    @Test
    fun `logSet ignoriert ungueltige intensitaet`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "abc")

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(null, sets[0].rpe)
    }

    @Test
    fun `logSet ignoriert intensitaet wenn IntensitySystem aus ist`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.OFF)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "8.5")

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(null, sets[0].rpe)
    }

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

    @Test
    fun `Setnummer wird nach Delete monoton fortgesetzt`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)
        vm.logSet(exerciseId = 1L, reps = 8, weightKg = 85.0)

        val beforeDelete = workoutRepo.getSetsForSessionList(sessionId)
        vm.deleteSet(beforeDelete.first { it.setNumber == 1 }.id)

        vm.logSet(exerciseId = 1L, reps = 6, weightKg = 90.0)

        val after = workoutRepo.getSetsForSessionList(sessionId).sortedBy { it.setNumber }
        assertEquals(listOf(2, 3), after.map { it.setNumber })
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

    @Test
    fun `init loads exercises from plan when planId is provided`() = runTest {
        // Setup a training plan
        val planId = 99L
        val exercise1 = Exercise(id = 10L, name = "Kniebeugen", primaryMuscleGroup = MuscleGroup.BEINE, category = ExerciseCategory.LANGHANTEL)
        val exercise2 = Exercise(id = 20L, name = "Beinpresse", primaryMuscleGroup = MuscleGroup.BEINE, category = ExerciseCategory.MASCHINE)
        exerciseRepo.addExercise(exercise1)
        exerciseRepo.addExercise(exercise2)

        val plan = com.ironlog.app.domain.model.TrainingPlan(
            id = planId,
            name = "Leg Day",
            exercises = listOf(
                com.ironlog.app.domain.model.PlanExercise(
                    exerciseId = 10L,
                    orderIndex = 0,
                    supersetGroupId = 1,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = 100.0
                ),
                com.ironlog.app.domain.model.PlanExercise(
                    exerciseId = 20L,
                    orderIndex = 1,
                    supersetGroupId = 1,
                    targetSets = 3,
                    targetReps = 12,
                    targetWeightKg = 150.0
                )
            )
        )
        planRepo.savePlan(plan)

        // Create ViewModel with planId
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId, "planId" to planId))
        val vm = ActiveWorkoutViewModel(savedStateHandle, workoutRepo, exerciseRepo, statsRepo, planRepo, prefsRepo)

        val collector = backgroundScope.launch { vm.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()

        // Check if exercises are loaded
        val state = vm.uiState.value
        assertEquals(2, state.exercisesWithSets.size)
        assertEquals("Kniebeugen", state.exercisesWithSets[0].exercise.name)
        assertEquals(3, state.exercisesWithSets[0].planTarget?.targetSets)
        assertEquals(1, state.exercisesWithSets[0].supersetGroupId)
        assertEquals("Beinpresse", state.exercisesWithSets[1].exercise.name)
        assertEquals(1, state.exercisesWithSets[1].supersetGroupId)
        
        collector.cancel()
    }

    @Test
    fun `uiState shows previous completed session data per exercise`() = runTest {
        val previousSessionId = 99L
        workoutRepo.addSession(
            WorkoutSession(
                id = previousSessionId,
                startTime = LocalDateTime.now().minusDays(2),
                endTime = LocalDateTime.now().minusDays(2).plusHours(1),
                durationSeconds = 3600
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 900L,
                sessionId = previousSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 75.0,
                isWarmup = true
            )
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 901L,
                sessionId = previousSessionId,
                exerciseId = testExercise.id,
                setNumber = 2,
                reps = 6,
                weightKg = 90.0,
                isWarmup = false
            )
        )

        val vm = createViewModel()
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val exerciseState = vm.uiState.value.exercisesWithSets.first()
        assertNotNull(exerciseState.previousSession)
        assertEquals(previousSessionId, exerciseState.previousSession!!.sessionId)
        assertEquals(90.0, exerciseState.previousSession!!.lastWorkSetWeightKg ?: 0.0, 0.01)
        assertEquals(2, exerciseState.previousSession!!.sets.size)

        collector.cancel()
    }

    @Test
    fun `previous session hint ignores active session and warmup-only history`() = runTest {
        val previousSessionId = 77L
        workoutRepo.addSession(
            WorkoutSession(
                id = previousSessionId,
                startTime = LocalDateTime.now().minusDays(3),
                endTime = LocalDateTime.now().minusDays(3).plusHours(1),
                durationSeconds = 3600
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 700L,
                sessionId = previousSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 40.0,
                isWarmup = true
            )
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 701L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 5,
                weightKg = 110.0,
                isWarmup = false
            )
        )

        val vm = createViewModel()
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertEquals(previousSessionId, previous!!.sessionId)
        assertNull(previous.lastWorkSetWeightKg)

        collector.cancel()
    }

    @Test
    fun `resuming active workout loads existing exercises from sets`() = runTest {
        // Setup existing set for the active session (simulating a resumed workout)
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 100L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 50.0,
                isWarmup = false
            )
        )

        val vm = createViewModel()
        
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.exercisesWithSets.size)
        assertEquals(testExercise.id, state.exercisesWithSets[0].exercise.id)
        assertEquals(1, state.exercisesWithSets[0].sets.size)
        
        collector.cancel()
    }
}
