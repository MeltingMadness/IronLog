package com.ironlog.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeProgressionRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import com.ironlog.app.domain.repository.ReadinessRepository
import io.mockk.mockk
import io.mockk.every
import io.mockk.coVerify
import io.mockk.coEvery
import kotlinx.coroutines.flow.flow
import com.ironlog.shared.readinessdata.SetIntention
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertFalse
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
    private lateinit var progressionRepo: FakeProgressionRepository
    private val readinessRepo = mockk<ReadinessRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { readinessRepo.observeSetIntentions() } returns flowOf(emptyMap())
        workoutRepo = FakeWorkoutRepository()
        exerciseRepo = FakeExerciseRepository()
        statisticsRepo = FakeStatisticsRepository()
        progressionRepo = FakeProgressionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `historical intention edits preserve completed workout values and reject foreign set ids`() = runTest {
        val now = LocalDateTime.now()
        val set = WorkoutSet(id = 101L, sessionId = 7L, exerciseId = 1L, setNumber = 1, reps = 8, weightKg = 80.0, completedAt = now.minusDays(1))
        workoutRepo.addSession(WorkoutSession(id = 7L, startTime = now.minusDays(1), endTime = now), isActive = false)
        workoutRepo.addSetDirectly(set)
        every { readinessRepo.observeSetIntentions() } returns flowOf(mapOf(101L to SetIntention.PLANNED_FAILURE))
        val vm = WorkoutDetailViewModel(SavedStateHandle(mapOf("sessionId" to 7L)), workoutRepo, exerciseRepo, statisticsRepo, progressionRepo, readinessRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SetIntention.PLANNED_FAILURE, vm.uiState.value.setIntentions[101L])
        vm.updateSetIntention(999L, SetIntention.UNKNOWN)
        vm.updateSetIntention(101L, SetIntention.UNKNOWN)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { readinessRepo.setSetIntention(999L, any(), any()) }
        coVerify(exactly = 1) { readinessRepo.setSetIntention(101L, SetIntention.UNKNOWN, any()) }
        assertEquals(set, workoutRepo.getSetsForSessionList(7L).single())
        assertEquals(now, workoutRepo.getSessionById(7L)?.endTime)
    }

    @Test
    fun `failed intention load stays visible and prevents overwriting unknown data`() = runTest {
        every { readinessRepo.observeSetIntentions() } returns flow { throw IllegalStateException("corrupt") }
        val vm = WorkoutDetailViewModel(SavedStateHandle(mapOf("sessionId" to 7L)), workoutRepo, exerciseRepo, statisticsRepo, progressionRepo, readinessRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.intentionsLoaded)
        assertTrue(vm.uiState.value.intentionError != null)
        vm.updateSetIntention(101L, SetIntention.UNKNOWN)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { readinessRepo.setSetIntention(any(), any(), any()) }
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
            statisticsRepository = statisticsRepo,
            progressionRepository = progressionRepo,
            readinessRepository = readinessRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val exercise2Detail = vm.uiState.value.exercises.firstOrNull { it.exercise.id == 2L }
        assertTrue(exercise2Detail != null)
        assertTrue(exercise2Detail!!.records.isNotEmpty())
    }

    @Test
    fun `workout detail zeigt Progressions-Outcomes der Session mit aufgeloesten Namen`() = runTest {
        val now = LocalDateTime.now()

        exerciseRepo.addExercise(
            Exercise(
                id = 1L,
                name = "Bankdruecken",
                primaryMuscleGroup = MuscleGroup.BRUST,
                category = ExerciseCategory.LANGHANTEL
            )
        )
        workoutRepo.addSession(
            WorkoutSession(id = 7L, startTime = now.minusDays(1), endTime = now, durationSeconds = 3600),
            isActive = false
        )
        workoutRepo.addSetDirectly(
            WorkoutSet(id = 101L, sessionId = 7L, exerciseId = 1L, setNumber = 1, reps = 8, weightKg = 80.0, completedAt = now.minusDays(1))
        )

        progressionRepo.setSuggestions(
            listOf(
                suggestion(
                    id = 11L,
                    sessionId = 7L,
                    exerciseId = 1L,
                    status = ProgressionSuggestionStatus.ACCEPTED,
                    outcome = acceptedOutcome()
                )
            )
        )

        val vm = WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 7L)),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo,
            progressionRepository = progressionRepo,
            readinessRepository = readinessRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val outcomes = vm.uiState.value.progressionOutcomes
        assertEquals(1, outcomes.size)
        val outcome = outcomes.single()
        assertEquals("Bankdruecken", outcome.exerciseName)
        assertEquals(ProgressionSuggestionStatus.ACCEPTED, outcome.status)
        // History is read-only: decision actions must never appear here.
        assertFalse(outcome.canDecide)
        // The detail load must not have wiped the concurrently collected outcomes.
        assertTrue(vm.uiState.value.exercises.isNotEmpty())
        assertEquals(listOf<Long?>(7L), progressionRepo.observedSessionIds)
    }

    @Test
    fun `workout detail ohne Progressions-Outcomes bleibt ohne Coach-Abschnitt`() = runTest {
        workoutRepo.addSession(
            WorkoutSession(id = 7L, startTime = LocalDateTime.now().minusDays(1)),
            isActive = false
        )
        val vm = WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 7L)),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo,
            progressionRepository = progressionRepo,
            readinessRepository = readinessRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.progressionOutcomes.isEmpty())
        assertFalse(vm.uiState.value.notFound)
    }

    @Test
    fun `missing session sets notFound and clears isLoading instead of an empty scaffold`() = runTest {
        val vm = WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 12345L)),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo,
            progressionRepository = progressionRepo,
            readinessRepository = readinessRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `history detail keeps zero rep failure rows while hiding empty normal rows`() {
        val base = WorkoutSet(
            id = 1L,
            sessionId = 7L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 0,
            weightKg = 80.0,
            setType = SetType.NORMAL
        )
        val failure = base.copy(id = 2L, setNumber = 2, setType = SetType.FAILURE)
        val drop = base.copy(id = 3L, setNumber = 3, setType = SetType.DROP_SET, reps = 5)

        assertEquals(listOf(failure, drop), visibleHistorySets(listOf(base, failure, drop)))
    }

    private fun suggestion(
        id: Long,
        sessionId: Long,
        exerciseId: Long,
        status: ProgressionSuggestionStatus,
        outcome: ProgressionOutcome
    ): ProgressionSuggestion {
        val sourceTarget = WorkoutPlanTarget(
            id = 1000L + id,
            sessionId = sessionId,
            planId = 3L,
            exerciseId = exerciseId,
            orderIndex = 0,
            supersetGroupId = null,
            target = ProgressionTarget(sets = 3, reps = 8, weightKg = 80.0),
            config = ProgressionConfig.Linear(
                step = WeightStep(
                    originalValue = 2.5,
                    originalUnit = UnitSystem.METRIC,
                    kilograms = 2.5
                )
            )
        )
        return ProgressionSuggestion(
            id = id,
            sourceTarget = sourceTarget,
            outcome = outcome,
            countedSets = emptyList(),
            status = status,
            wasEdited = false,
            finalTarget = null,
            createdAtEpochMillis = id,
            decidedAtEpochMillis = null
        )
    }

    private fun acceptedOutcome(): ProgressionOutcome = ProgressionOutcome.ProposeChange(
        sourceTarget = ProgressionTarget(sets = 3, reps = 8, weightKg = 80.0),
        proposedTarget = ProgressionTarget(sets = 3, reps = 8, weightKg = 82.5),
        reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
        reasonArguments = mapOf("stepOriginalValue" to 2.5),
        streakEffect = ProgressionStreakEffect.INCREMENT
    )
}
