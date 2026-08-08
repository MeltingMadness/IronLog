package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.util.AppLogger
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
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

    private fun withMockedAppLoggerWarnings(block: () -> Unit) {
        mockkObject(AppLogger)
        try {
            every { AppLogger.w(any(), any(), any()) } returns Unit
            block()
        } finally {
            unmockkObject(AppLogger)
        }
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
    fun `logSet akzeptiert Komma als Dezimaltrennzeichen fuer Intensitaet`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "8,5")

        val sets = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(8.5, sets[0].rpe)
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
    fun `logSet vergibt bei schnellen Folgeaufrufen fortlaufende Setnummern`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)
        vm.logSet(exerciseId = 1L, reps = 8, weightKg = 85.0)
        testDispatcher.scheduler.advanceUntilIdle()

        val sets = workoutRepo.getSetsForSessionList(sessionId).sortedBy { it.setNumber }
        assertEquals(listOf(1, 2), sets.map { it.setNumber })
    }

    @Test
    fun `logSet emittiert NewRecord aus Vergleich und schreibt keine PRs`() = runTest {
        val stats = mockk<StatisticsRepository>(relaxed = true)
        val now = LocalDateTime.now()
        val before = listOf(
            com.ironlog.app.domain.model.PersonalRecord(
                id = 1L, exerciseId = testExercise.id, type = RecordType.MAX_WEIGHT, value = 80.0, achievedAt = now
            ),
            com.ironlog.app.domain.model.PersonalRecord(
                id = 2L, exerciseId = testExercise.id, type = RecordType.MAX_REPS, value = 6.0, achievedAt = now
            ),
            com.ironlog.app.domain.model.PersonalRecord(
                id = 3L, exerciseId = testExercise.id, type = RecordType.MAX_E1RM, value = 100.0, achievedAt = now
            ),
            com.ironlog.app.domain.model.PersonalRecord(
                id = 4L, exerciseId = testExercise.id, type = RecordType.MAX_VOLUME, value = 500.0, achievedAt = now
            )
        )
        val after = listOf(
            before[0].copy(value = 100.0),
            before[1].copy(value = 8.0),
            before[2].copy(value = 133.33),
            before[3].copy(value = 800.0)
        )
        coEvery { stats.getRecordsForExercisesList(listOf(testExercise.id)) } returnsMany listOf(before, after)

        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { emitted += it } }

        vm.logSet(exerciseId = testExercise.id, reps = 10, weightKg = 100.0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            setOf(RecordType.MAX_WEIGHT, RecordType.MAX_REPS, RecordType.MAX_E1RM, RecordType.MAX_VOLUME),
            emitted.filterIsInstance<WorkoutEvent.NewRecord>().map { it.type }.toSet()
        )
        coVerify(exactly = 0) { stats.checkAndUpdateRecord(any(), any(), any()) }

        eventCollector.cancel()
    }

    @Test
    fun `logSet emittiert kein NewRecord wenn kein Record verbessert wurde`() = runTest {
        val stats = mockk<StatisticsRepository>(relaxed = true)
        val existing = listOf(
            com.ironlog.app.domain.model.PersonalRecord(
                id = 1L,
                exerciseId = testExercise.id,
                type = RecordType.MAX_WEIGHT,
                value = 120.0,
                achievedAt = LocalDateTime.now()
            )
        )
        coEvery { stats.getRecordsForExercisesList(listOf(testExercise.id)) } returnsMany listOf(existing, existing)

        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch { vm.events.collect { emitted += it } }

        vm.logSet(exerciseId = testExercise.id, reps = 10, weightKg = 100.0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(emitted.isEmpty())
        coVerify(exactly = 0) { stats.checkAndUpdateRecord(any(), any(), any()) }

        eventCollector.cancel()
    }

    @Test
    fun `logSet fuehrt Mutation aus wenn PR-Snapshot fehlschlaegt`() = runTest {
        val stats = mockk<StatisticsRepository>(relaxed = true)
        coEvery { stats.getRecordsForExercisesList(listOf(testExercise.id)) } throws
            IllegalStateException("stats boom")
        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { emitted += it } }

        withMockedAppLoggerWarnings {
            vm.logSet(exerciseId = testExercise.id, reps = 10, weightKg = 100.0)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        assertEquals(1, workoutRepo.addSetCallCount)
        assertEquals(1, workoutRepo.getSetsForSessionList(sessionId).size)
        assertTrue(emitted.isEmpty())
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 0) { stats.checkAndUpdateRecord(any(), any(), any()) }

        eventCollector.cancel()
    }

    @Test
    fun `Warmup-Satz no PR comparison and no PR write`() = runTest {
        val stats = mockk<StatisticsRepository>(relaxed = true)
        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch { vm.events.collect { emitted += it } }

        vm.logSet(exerciseId = testExercise.id, reps = 10, weightKg = 50.0, isWarmup = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(emitted.isEmpty())
        coVerify(exactly = 0) { stats.getRecordsForExercisesList(any()) }
        coVerify(exactly = 0) { stats.checkAndUpdateRecord(any(), any(), any()) }

        eventCollector.cancel()
    }

    @Test
    fun `logSet haelt mutationMutex bis der Record-Vergleich abgeschlossen ist`() = runTest {
        val stats = mockk<StatisticsRepository>(relaxed = true)
        val comparisonGate = CompletableDeferred<Unit>()
        var statsCallCount = 0
        coEvery { stats.getRecordsForExercisesList(listOf(testExercise.id)) } coAnswers {
            statsCallCount++
            if (statsCallCount == 1) {
                emptyList()
            } else {
                comparisonGate.await()
                emptyList()
            }
        }
        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch { vm.events.collect { emitted += it } }

        vm.logSet(exerciseId = testExercise.id, reps = 10, weightKg = 100.0)
        testDispatcher.scheduler.advanceUntilIdle()
        val addedSetId = workoutRepo.getSetsForSessionList(sessionId).first().id

        // The record comparison is still blocked inside the mutex, so a queued delete
        // must not run before it completes.
        vm.deleteSet(addedSetId)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, workoutRepo.deleteSetCallCount)

        comparisonGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.deleteSetCallCount)
        assertTrue(emitted.isEmpty())

        eventCollector.cancel()
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

    // --- Update Set ---

    @Test
    fun `updateSet behaelt vorhandenes RPE wenn IntensitySystem aus ist`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "8.5")
        val logged = workoutRepo.getSetsForSessionList(sessionId).first()

        // User switches intensity tracking off before editing the set; the (hidden)
        // intensity UI now sends a blank string, which must not wipe out the RPE.
        prefsRepo.updateIntensitySystem(IntensitySystem.OFF)
        vm.updateSet(setId = logged.id, reps = 12, weightKg = 82.5, intensity = "")

        val updated = workoutRepo.getSetsForSessionList(sessionId).first { it.id == logged.id }
        assertEquals(12, updated.reps)
        assertEquals(82.5, updated.weightKg, 0.01)
        assertEquals(8.5, updated.rpe)
    }

    @Test
    fun `updateSet loescht RPE wenn Intensitaetsfeld absichtlich geleert wird`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, isWarmup = false, intensity = "8.5")
        val logged = workoutRepo.getSetsForSessionList(sessionId).first()

        // Intensity tracking stays on, user clears the field intentionally.
        vm.updateSet(setId = logged.id, reps = 10, weightKg = 80.0, intensity = "")

        val updated = workoutRepo.getSetsForSessionList(sessionId).first { it.id == logged.id }
        assertNull(updated.rpe)
    }

    @Test
    fun `updateSet akzeptiert Komma als Dezimaltrennzeichen fuer Intensitaet`() = runTest {
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)
        val logged = workoutRepo.getSetsForSessionList(sessionId).first()

        vm.updateSet(setId = logged.id, reps = 10, weightKg = 80.0, intensity = "9,0")

        val updated = workoutRepo.getSetsForSessionList(sessionId).first { it.id == logged.id }
        assertEquals(9.0, updated.rpe)
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
    fun `previous session hint prefers last completed session of same plan even if another plan is newer`() = runTest {
        val targetPlanId = 55L
        val otherPlanId = 77L
        val samePlanSessionId = 201L
        val otherPlanSessionId = 202L

        workoutRepo.addSession(
            WorkoutSession(
                id = samePlanSessionId,
                startTime = LocalDateTime.now().minusDays(3),
                endTime = LocalDateTime.now().minusDays(3).plusHours(1),
                durationSeconds = 3600,
                planId = targetPlanId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 801L,
                sessionId = samePlanSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 82.5,
                isWarmup = false
            )
        )

        workoutRepo.addSession(
            WorkoutSession(
                id = otherPlanSessionId,
                startTime = LocalDateTime.now().minusDays(1),
                endTime = LocalDateTime.now().minusDays(1).plusHours(1),
                durationSeconds = 3600,
                planId = otherPlanId
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 802L,
                sessionId = otherPlanSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 5,
                weightKg = 95.0,
                isWarmup = false
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to sessionId,
                "planId" to targetPlanId,
                "metaPlanId" to 500L
            )
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertEquals(samePlanSessionId, previous!!.sessionId)
        assertEquals(82.5, previous.lastWorkSetWeightKg ?: 0.0, 0.01)

        collector.cancel()
    }

    @Test
    fun `previous session hint separates normal plan from meta plan history by default`() = runTest {
        val planId = 55L
        val normalSessionId = 301L
        val metaSessionId = 302L

        workoutRepo.addSession(
            WorkoutSession(
                id = normalSessionId,
                startTime = LocalDateTime.now().minusDays(3),
                endTime = LocalDateTime.now().minusDays(3).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = null
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 901L,
                sessionId = normalSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 80.0,
                isWarmup = false
            )
        )

        workoutRepo.addSession(
            WorkoutSession(
                id = metaSessionId,
                startTime = LocalDateTime.now().minusDays(1),
                endTime = LocalDateTime.now().minusDays(1).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 902L,
                sessionId = metaSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 6,
                weightKg = 95.0,
                isWarmup = false
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf("sessionId" to sessionId, "planId" to planId)
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertEquals(normalSessionId, previous!!.sessionId)
        assertEquals(80.0, previous.lastWorkSetWeightKg ?: 0.0, 0.01)

        collector.cancel()
    }

    @Test
    fun `previous session hint picks matching meta plan subcontext by default`() = runTest {
        val planId = 55L
        val metaASessionId = 401L
        val metaBSessionId = 402L

        workoutRepo.addSession(
            WorkoutSession(
                id = metaASessionId,
                startTime = LocalDateTime.now().minusDays(3),
                endTime = LocalDateTime.now().minusDays(3).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 911L,
                sessionId = metaASessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 82.5,
                isWarmup = false
            )
        )

        workoutRepo.addSession(
            WorkoutSession(
                id = metaBSessionId,
                startTime = LocalDateTime.now().minusDays(1),
                endTime = LocalDateTime.now().minusDays(1).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 501L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 912L,
                sessionId = metaBSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 6,
                weightKg = 95.0,
                isWarmup = false
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to sessionId,
                "planId" to planId,
                "metaPlanId" to 500L
            )
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertEquals(metaASessionId, previous!!.sessionId)
        assertEquals(82.5, previous.lastWorkSetWeightKg ?: 0.0, 0.01)

        collector.cancel()
    }

    @Test
    fun `previous session hint shares history across contexts when setting enabled`() = runTest {
        prefsRepo.updateShareWeightHistoryAcrossContexts(true)
        val planId = 55L
        val normalSessionId = 501L
        val metaSessionId = 502L

        workoutRepo.addSession(
            WorkoutSession(
                id = normalSessionId,
                startTime = LocalDateTime.now().minusDays(3),
                endTime = LocalDateTime.now().minusDays(3).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = null
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 921L,
                sessionId = normalSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 80.0,
                isWarmup = false
            )
        )

        workoutRepo.addSession(
            WorkoutSession(
                id = metaSessionId,
                startTime = LocalDateTime.now().minusDays(1),
                endTime = LocalDateTime.now().minusDays(1).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 501L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 922L,
                sessionId = metaSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 6,
                weightKg = 95.0,
                isWarmup = false
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to sessionId,
                "planId" to planId,
                "metaPlanId" to 500L
            )
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertEquals(metaSessionId, previous!!.sessionId)
        assertEquals(95.0, previous.lastWorkSetWeightKg ?: 0.0, 0.01)

        collector.cancel()
    }

    @Test
    fun `other preference changes do not reload previous session history`() = runTest {
        val vm = createViewModel()
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val callsBefore = workoutRepo.getPreviousSessionDataForExercisesCallCount
        prefsRepo.updateIntensitySystem(IntensitySystem.RPE)
        prefsRepo.updateUnitSystem(com.ironlog.app.domain.model.UnitSystem.IMPERIAL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(callsBefore, workoutRepo.getPreviousSessionDataForExercisesCallCount)
        collector.cancel()
    }

    @Test
    fun `previous session hint ties equal start times to higher session id`() = runTest {
        val planId = 55L
        val olderSessionId = 601L
        val newerSessionId = 602L
        val sameStartTime = LocalDateTime.now().minusDays(2)

        workoutRepo.addSession(
            WorkoutSession(
                id = olderSessionId,
                startTime = sameStartTime,
                endTime = sameStartTime.plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 931L,
                sessionId = olderSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 80.0,
                isWarmup = false
            )
        )

        workoutRepo.addSession(
            WorkoutSession(
                id = newerSessionId,
                startTime = sameStartTime,
                endTime = sameStartTime.plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 932L,
                sessionId = newerSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 6,
                weightKg = 95.0,
                isWarmup = false
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to sessionId,
                "planId" to planId,
                "metaPlanId" to 500L
            )
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        vm.addExercise(testExercise)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertEquals(newerSessionId, previous!!.sessionId)
        assertEquals(95.0, previous.lastWorkSetWeightKg ?: 0.0, 0.01)

        collector.cancel()
    }

    // --- Previous final-set target indicator ---

    @Test
    fun `lastWorkSetReachedTarget returns true when target was reached`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertTrue(lastWorkSetReachedTarget(target, listOf(previousSet(8, 80.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns true when target was exceeded`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertTrue(lastWorkSetReachedTarget(target, listOf(previousSet(9, 85.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false when reps are below target`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertFalse(lastWorkSetReachedTarget(target, listOf(previousSet(7, 90.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false when weight is below target`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertFalse(lastWorkSetReachedTarget(target, listOf(previousSet(8, 79.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false when target is missing`() {
        assertFalse(lastWorkSetReachedTarget(null, listOf(previousSet(8, 80.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false when weight target is zero`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 0.0)

        assertFalse(lastWorkSetReachedTarget(target, listOf(previousSet(8, 90.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false when reps target is zero`() {
        val target = PlanTarget(targetReps = 0, targetWeightKg = 80.0)

        assertFalse(lastWorkSetReachedTarget(target, listOf(previousSet(8, 90.0))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false for warmup-only history`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertFalse(lastWorkSetReachedTarget(target, listOf(previousSet(8, 80.0, warmup = true))))
    }

    @Test
    fun `lastWorkSetReachedTarget returns false without previous sets`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertFalse(lastWorkSetReachedTarget(target, emptyList()))
    }

    @Test
    fun `lastWorkSetReachedTarget evaluates last non-warmup set even when warmup comes last`() {
        val target = PlanTarget(targetReps = 8, targetWeightKg = 80.0)

        assertTrue(
            lastWorkSetReachedTarget(
                target,
                listOf(previousSet(8, 80.0), previousSet(5, 60.0, warmup = true))
            )
        )
    }

    @Test
    fun `previous session indicator is true when last work set reached current targets`() = runTest {
        val planId = 55L
        val previousSessionId = 701L
        workoutRepo.addSession(
            WorkoutSession(
                id = previousSessionId,
                startTime = LocalDateTime.now().minusDays(2),
                endTime = LocalDateTime.now().minusDays(2).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(
                id = 951L,
                sessionId = previousSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 80.0,
                isWarmup = false
            )
        )
        planRepo.savePlan(
            com.ironlog.app.domain.model.TrainingPlan(
                id = planId,
                name = "Push Day",
                exercises = listOf(
                    com.ironlog.app.domain.model.PlanExercise(
                        exerciseId = testExercise.id,
                        orderIndex = 0,
                        targetSets = 3,
                        targetReps = 8,
                        targetWeightKg = 80.0
                    )
                )
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to sessionId,
                "planId" to planId,
                "metaPlanId" to 500L
            )
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertTrue(previous!!.lastWorkSetReachedTarget)

        collector.cancel()
    }

    @Test
    fun `previous session indicator is false when last work set missed current target`() = runTest {
        val planId = 55L
        val previousSessionId = 702L
        workoutRepo.addSession(
            WorkoutSession(
                id = previousSessionId,
                startTime = LocalDateTime.now().minusDays(2),
                endTime = LocalDateTime.now().minusDays(2).plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 500L
            ),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(
                id = 952L,
                sessionId = previousSessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 6,
                weightKg = 90.0,
                isWarmup = false
            )
        )
        planRepo.savePlan(
            com.ironlog.app.domain.model.TrainingPlan(
                id = planId,
                name = "Push Day",
                exercises = listOf(
                    com.ironlog.app.domain.model.PlanExercise(
                        exerciseId = testExercise.id,
                        orderIndex = 0,
                        targetSets = 3,
                        targetReps = 8,
                        targetWeightKg = 80.0
                    )
                )
            )
        )

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "sessionId" to sessionId,
                "planId" to planId,
                "metaPlanId" to 500L
            )
        )
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val previous = vm.uiState.value.exercisesWithSets.first().previousSession
        assertNotNull(previous)
        assertFalse(previous!!.lastWorkSetReachedTarget)

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

    @Test
    fun `resuming active workout reconciles exercises from sets without duplicates`() = runTest {
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 101L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 8,
                weightKg = 70.0,
                isWarmup = false
            )
        )

        val vm = createViewModel()
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val firstIds = vm.uiState.value.exercisesWithSets.map { it.exercise.id }
        testDispatcher.scheduler.advanceUntilIdle()
        val secondIds = vm.uiState.value.exercisesWithSets.map { it.exercise.id }

        assertEquals(listOf(testExercise.id), firstIds)
        assertEquals(firstIds, secondIds)
        assertEquals(secondIds.distinct().size, secondIds.size)

        collector.cancel()
    }

    @Test
    fun `rest timer disappears automatically after logging last planned work set`() = runTest {
        val planId = 88L
        val plan = com.ironlog.app.domain.model.TrainingPlan(
            id = planId,
            name = "Push Day",
            exercises = listOf(
                com.ironlog.app.domain.model.PlanExercise(
                    exerciseId = testExercise.id,
                    orderIndex = 0,
                    targetSets = 2,
                    targetReps = 8,
                    targetWeightKg = 80.0
                )
            )
        )
        planRepo.savePlan(plan)

        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId, "planId" to planId))
        val vm = ActiveWorkoutViewModel(savedStateHandle, workoutRepo, exerciseRepo, statsRepo, planRepo, prefsRepo)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.logSet(exerciseId = testExercise.id, reps = 8, weightKg = 80.0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(testExercise.id in vm.uiState.value.restTimers)

        vm.logSet(exerciseId = testExercise.id, reps = 8, weightKg = 80.0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(testExercise.id !in vm.uiState.value.restTimers)

        collector.cancel()
    }

    @Test
    fun `hinzugefuegte Uebungen ueberleben Prozesstod ueber SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val vm = ActiveWorkoutViewModel(savedStateHandle, workoutRepo, exerciseRepo, statsRepo, planRepo, prefsRepo)
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addExercise(testExercise)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(testExercise.id), vm.uiState.value.exercisesWithSets.map { it.exercise.id })
        collector.cancel()

        // Simulate process death + recreation: a new ViewModel instance is created
        // with the same (restored) SavedStateHandle, before any sets exist.
        val restoredVm = ActiveWorkoutViewModel(savedStateHandle, workoutRepo, exerciseRepo, statsRepo, planRepo, prefsRepo)
        val restoredCollector = backgroundScope.launch { restoredVm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(testExercise.id),
            restoredVm.uiState.value.exercisesWithSets.map { it.exercise.id }
        )

        restoredCollector.cancel()
    }

    @Test
    fun `rest timer state uses instant and dismisses per exercise independently`() = runTest {
        val secondExercise = Exercise(
            id = 2L,
            name = "Schraegbankdruecken",
            primaryMuscleGroup = MuscleGroup.BRUST,
            category = ExerciseCategory.KURZHANTEL
        )
        exerciseRepo.addExercise(secondExercise)
        val vm = createViewModel()
        val collector = backgroundScope.launch { vm.uiState.collect { } }

        vm.logSet(exerciseId = testExercise.id, reps = 10, weightKg = 80.0)
        vm.logSet(exerciseId = secondExercise.id, reps = 12, weightKg = 32.5)
        testDispatcher.scheduler.advanceUntilIdle()

        val restTimers = vm.uiState.value.restTimers
        assertEquals(setOf(testExercise.id, secondExercise.id), restTimers.keys)
        val firstTimer: Instant = restTimers.getValue(testExercise.id)
        val secondTimer: Instant = restTimers.getValue(secondExercise.id)
        assertTrue(firstTimer.epochSecond > 0)
        assertTrue(secondTimer.epochSecond > 0)

        vm.dismissRestTimer(testExercise.id)

        val afterDismiss = vm.uiState.value.restTimers
        assertTrue(testExercise.id !in afterDismiss)
        assertTrue(secondExercise.id in afterDismiss)

        collector.cancel()
    }

    @Test
    fun `missing session sets phase Missing and clears Loading`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to 98765L))
        val vm = ActiveWorkoutViewModel(
            savedStateHandle,
            workoutRepo,
            exerciseRepo,
            statsRepo,
            planRepo,
            prefsRepo
        )
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveWorkoutSessionPhase.Missing, vm.uiState.value.sessionPhase)

        collector.cancel()
    }

    @Test
    fun `logSet failure releases lock and retry persists exactly one set`() = runTest {
        workoutRepo.failAddSet = true
        val vm = createViewModel()
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, submissionId = 321L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.addSetCallCount)
        assertTrue(workoutRepo.getSetsForSessionList(sessionId).isEmpty())
        assertNull(vm.uiState.value.logInFlightByExercise[1L])
        val error = vm.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.retry is WorkoutRetryDescriptor.LogSet)
        assertEquals(321L, (error.retry as WorkoutRetryDescriptor.LogSet).submissionId)

        workoutRepo.failAddSet = false
        vm.retryLastError()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, workoutRepo.addSetCallCount)
        val persisted = workoutRepo.getSetsForSessionList(sessionId)
        assertEquals(1, persisted.size)
        assertEquals(10, persisted[0].reps)
        assertEquals(80.0, persisted[0].weightKg, 0.01)
        assertNull(vm.uiState.value.error)
        assertTrue(321L in vm.uiState.value.logSuccessSubmissions)

        vm.retryLastError()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, workoutRepo.addSetCallCount)

        collector.cancel()
    }

    @Test
    fun `updateSet ignores reps kleiner gleich null without persisting`() = runTest {
        val vm = createViewModel()
        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)
        testDispatcher.scheduler.advanceUntilIdle()
        val logged = workoutRepo.getSetsForSessionList(sessionId).first()

        vm.updateSet(setId = logged.id, reps = 0, weightKg = 90.0, intensity = "")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, workoutRepo.updateSetCallCount)
        val unchanged = workoutRepo.getSetsForSessionList(sessionId).first()
        assertEquals(10, unchanged.reps)
        assertEquals(80.0, unchanged.weightKg, 0.01)
    }

    @Test
    fun `finishWorkout with zero sets discards session via deleteSession`() = runTest {
        val vm = createViewModel()
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.showFinishDialog()
        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.deleteSessionCallCount)
        assertEquals(0, workoutRepo.finishWorkoutCallCount)
        assertNull(workoutRepo.getSessionById(sessionId))
        assertFalse(vm.uiState.value.showFinishDialog)
        assertTrue(vm.uiState.value.workoutFinished)

        collector.cancel()
    }

    @Test
    fun `finishWorkout with sets calls finishWorkout instead of deleteSession`() = runTest {
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 400L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 80.0,
                isWarmup = false
            )
        )
        val vm = createViewModel()

        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.finishWorkoutCallCount)
        assertEquals(0, workoutRepo.deleteSessionCallCount)
        assertNotNull(workoutRepo.getSessionById(sessionId)?.endTime)
    }

    @Test
    fun `finishWorkout failure keeps dialog and session and retry finishes exactly once`() = runTest {
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 500L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 80.0,
                isWarmup = false
            )
        )
        workoutRepo.failFinishWorkout = true
        val vm = createViewModel()
        val collector = backgroundScope.launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.showFinishDialog()
        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.finishWorkoutCallCount)
        assertTrue(vm.uiState.value.showFinishDialog)
        assertNotNull(workoutRepo.getSessionById(sessionId))
        assertFalse(vm.uiState.value.workoutFinished)
        val error = vm.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.retry is WorkoutRetryDescriptor.FinishWorkout)

        workoutRepo.failFinishWorkout = false
        vm.retryLastError()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, workoutRepo.finishWorkoutCallCount)
        assertFalse(vm.uiState.value.showFinishDialog)
        assertTrue(vm.uiState.value.workoutFinished)
        assertNotNull(workoutRepo.getSessionById(sessionId)?.endTime)

        collector.cancel()
    }

    @Test
    fun `finishWorkout vor logSet verhindert nachtraeglichen Insert`() = runTest {
        val vm = createViewModel()

        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.deleteSessionCallCount)
        assertEquals(0, workoutRepo.addSetCallCount)

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0, submissionId = 700L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, workoutRepo.addSetCallCount)
        assertFalse(700L in vm.uiState.value.logSuccessSubmissions)
    }

    @Test
    fun `logSet vor finishWorkout persistiert Satz und beendet normal`() = runTest {
        val vm = createViewModel()

        vm.logSet(exerciseId = 1L, reps = 10, weightKg = 80.0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, workoutRepo.addSetCallCount)

        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.finishWorkoutCallCount)
        assertEquals(0, workoutRepo.deleteSessionCallCount)
        assertEquals(1, workoutRepo.getSetsForSessionList(sessionId).size)
        assertNotNull(workoutRepo.getSessionById(sessionId)?.endTime)
    }

    @Test
    fun `updateSet emittiert verbesserte Records ohne PR-Schreibzugriff`() = runTest {
        val stats = mockk<StatisticsRepository>(relaxed = true)
        val beforeRecord = com.ironlog.app.domain.model.PersonalRecord(
            id = 1L,
            exerciseId = testExercise.id,
            type = RecordType.MAX_WEIGHT,
            value = 100.0,
            achievedAt = LocalDateTime.now()
        )
        val improvedRecord = beforeRecord.copy(value = 120.0)
        coEvery {
            stats.getRecordsForExercisesList(listOf(testExercise.id))
        } returnsMany listOf(listOf(beforeRecord), listOf(improvedRecord))

        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 600L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 100.0,
                isWarmup = false
            )
        )
        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { emitted += it } }

        vm.updateSet(setId = 600L, reps = 12, weightKg = 120.0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            emitted.any { event ->
                event is WorkoutEvent.NewRecord && event.type == RecordType.MAX_WEIGHT
            }
        )
        coVerify(exactly = 0) { stats.checkAndUpdateRecord(any(), any(), any()) }

        eventCollector.cancel()
    }

    @Test
    fun `updateSet fuehrt Mutation aus wenn PR-Snapshot fehlschlaegt`() = runTest {
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 650L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 100.0,
                isWarmup = false
            )
        )
        val stats = mockk<StatisticsRepository>(relaxed = true)
        coEvery { stats.getRecordsForExercisesList(listOf(testExercise.id)) } throws
            IllegalStateException("stats boom")
        val vm = ActiveWorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            workoutRepo,
            exerciseRepo,
            stats,
            planRepo,
            prefsRepo
        )
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { emitted += it } }

        withMockedAppLoggerWarnings {
            vm.updateSet(setId = 650L, reps = 12, weightKg = 120.0)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        assertEquals(1, workoutRepo.updateSetCallCount)
        val updated = workoutRepo.getSetsForSessionList(sessionId).first()
        assertEquals(12, updated.reps)
        assertEquals(120.0, updated.weightKg, 0.01)
        assertTrue(emitted.isEmpty())
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 0) { stats.checkAndUpdateRecord(any(), any(), any()) }

        eventCollector.cancel()
    }

    @Test
    fun `delete letzter Satz vor finishWorkout fuehrt zu Session-Discard statt empty completed`() = runTest {
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 700L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 80.0,
                isWarmup = false
            )
        )
        val vm = createViewModel()

        vm.deleteSet(700L)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.deleteSetCallCount)
        assertEquals(1, workoutRepo.deleteSessionCallCount)
        assertEquals(0, workoutRepo.finishWorkoutCallCount)
        assertNull(workoutRepo.getSessionById(sessionId))
        assertTrue(workoutRepo.getAllCompletedSessionsList().isEmpty())
    }

    @Test
    fun `finishWorkout vor update und delete verhindert beide Repo-Mutationen`() = runTest {
        workoutRepo.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 800L,
                sessionId = sessionId,
                exerciseId = testExercise.id,
                setNumber = 1,
                reps = 10,
                weightKg = 80.0,
                isWarmup = false
            )
        )
        val vm = createViewModel()
        val emitted = mutableListOf<WorkoutEvent>()
        val eventCollector = backgroundScope.launch { vm.events.collect { emitted += it } }

        vm.finishWorkout()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateSet(setId = 800L, reps = 12, weightKg = 90.0)
        vm.deleteSet(800L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepo.finishWorkoutCallCount)
        assertEquals(0, workoutRepo.updateSetCallCount)
        assertEquals(0, workoutRepo.deleteSetCallCount)
        assertNotNull(workoutRepo.getSessionById(sessionId)?.endTime)
        assertEquals(10, workoutRepo.getSetsForSessionList(sessionId).first().reps)
        assertTrue(vm.uiState.value.updateInFlightBySet.isEmpty())
        assertTrue(emitted.isEmpty())

        eventCollector.cancel()
    }
}

private fun previousSet(
    reps: Int,
    weightKg: Double,
    warmup: Boolean = false
) = WorkoutSet(
    sessionId = 1L,
    exerciseId = 2L,
    setNumber = 1,
    reps = reps,
    weightKg = weightKg,
    isWarmup = warmup
)
