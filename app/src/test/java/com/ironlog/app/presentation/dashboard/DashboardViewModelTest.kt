package com.ironlog.app.presentation.dashboard

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeMetaTrainingPlanRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: FakeWorkoutRepository
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var statsRepo: FakeStatisticsRepository
    private lateinit var preferencesRepo: FakeAppPreferencesRepository
    private lateinit var planRepo: FakeTrainingPlanRepository
    private lateinit var metaPlanRepo: FakeMetaTrainingPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
        exerciseRepo = FakeExerciseRepository()
        statsRepo = FakeStatisticsRepository()
        preferencesRepo = FakeAppPreferencesRepository()
        planRepo = FakeTrainingPlanRepository()
        metaPlanRepo = FakeMetaTrainingPlanRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(
        workoutRepo,
        statsRepo,
        exerciseRepo,
        preferencesRepo,
        planRepo,
        metaPlanRepo
    )

    @Test
    fun `Streak ist 0 bei keinen Trainings`() = runTest {
        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(0, streak)
    }

    @Test
    fun `calculateStreak laedt nicht alle Sessions sondern nur Timestamps`() = runTest {
        val today = LocalDate.now()
        for (i in 0L..2L) {
            val dt = LocalDateTime.of(today.minusDays(i), LocalTime.of(10, 0))
            workoutRepo.addSession(WorkoutSession(id = i + 100L, startTime = dt,
                endTime = dt.plusHours(1), durationSeconds = 3600), isActive = false)
        }
        val vm = createViewModel()
        assertEquals(3, vm.calculateStreak())
        assertEquals(0, workoutRepo.getAllCompletedSessionsListCallCount) // heavyweight path not used
    }

    @Test
    fun `calculateStreak zaehlt Session kurz vor Mitternacht korrekt zum selben Tag`() = runTest {
        val today = LocalDate.now()
        // Session at 23:59 local time today — must count as today, not tomorrow
        val lateNight = LocalDateTime.of(today, LocalTime.of(23, 59))
        workoutRepo.addSession(
            WorkoutSession(id = 200L, startTime = lateNight,
                endTime = lateNight.plusMinutes(60), durationSeconds = 3600),
            isActive = false
        )
        val vm = createViewModel()
        assertEquals(1, vm.calculateStreak())
    }

    @Test
    fun `Streak ist 1 bei Training heute`() = runTest {
        val today = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 1, startTime = today, endTime = today.plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(1, streak)
    }

    @Test
    fun `Streak ist 1 bei Training gestern`() = runTest {
        val yesterday = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(10, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 1, startTime = yesterday, endTime = yesterday.plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(1, streak)
    }

    @Test
    fun `Streak zaehlt aufeinanderfolgende Tage`() = runTest {
        val today = LocalDate.now()
        for (i in 0L..4L) {
            val dt = LocalDateTime.of(today.minusDays(i), LocalTime.of(10, 0))
            workoutRepo.addSession(
                WorkoutSession(
                    id = i + 1,
                    startTime = dt,
                    endTime = dt.plusHours(1),
                    durationSeconds = 3600
                ),
                isActive = false
            )
        }

        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(5, streak)
    }

    @Test
    fun `Luecke bricht Streak`() = runTest {
        val today = LocalDate.now()
        val todayDt = LocalDateTime.of(today, LocalTime.of(10, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 1, startTime = todayDt, endTime = todayDt.plusHours(1), durationSeconds = 3600),
            isActive = false
        )
        val yesterdayDt = LocalDateTime.of(today.minusDays(1), LocalTime.of(10, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 2, startTime = yesterdayDt, endTime = yesterdayDt.plusHours(1), durationSeconds = 3600),
            isActive = false
        )
        val threeDaysAgoDt = LocalDateTime.of(today.minusDays(3), LocalTime.of(10, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 3, startTime = threeDaysAgoDt, endTime = threeDaysAgoDt.plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(2, streak)
    }

    @Test
    fun `Streak 0 wenn letztes Training vorgestern`() = runTest {
        val twoDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(2), LocalTime.of(10, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 1, startTime = twoDaysAgo, endTime = twoDaysAgo.plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(0, streak)
    }

    @Test
    fun `Mehrere Trainings am gleichen Tag zaehlen als 1`() = runTest {
        val today = LocalDate.now()
        val morning = LocalDateTime.of(today, LocalTime.of(8, 0))
        val evening = LocalDateTime.of(today, LocalTime.of(18, 0))
        workoutRepo.addSession(
            WorkoutSession(id = 1, startTime = morning, endTime = morning.plusHours(1), durationSeconds = 3600),
            isActive = false
        )
        workoutRepo.addSession(
            WorkoutSession(id = 2, startTime = evening, endTime = evening.plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(1, streak)
    }

    @Test
    fun `loadDashboard nutzt Batch-Query statt N einzelne getExerciseById Calls`() = runTest {
        exerciseRepo.addExercise(com.ironlog.app.domain.model.Exercise(id = 1L, name = "Kniebeuge", primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BEINE, category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL))
        exerciseRepo.addExercise(com.ironlog.app.domain.model.Exercise(id = 2L, name = "Bankdrücken", primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST, category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL))
        val now = LocalDateTime.now()
        statsRepo.addRecord(com.ironlog.app.domain.model.PersonalRecord(id = 1L, exerciseId = 1L, type = com.ironlog.app.domain.model.RecordType.MAX_WEIGHT, value = 100.0, achievedAt = now.minusHours(1)))
        statsRepo.addRecord(com.ironlog.app.domain.model.PersonalRecord(id = 2L, exerciseId = 2L, type = com.ironlog.app.domain.model.RecordType.MAX_WEIGHT, value = 80.0, achievedAt = now.minusHours(2)))

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, exerciseRepo.getExerciseByIdCallCount) // Must be 0 — batch used instead
        assertEquals(2, vm.uiState.value.recentRecords.size)
    }

    @Test
    fun `Dashboard laedt initial mit isLoading true`() = runTest {
        val vm = createViewModel()
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `Dashboard laedt erfolgreich`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `metaplan combine streamt keine vollen Sessions sondern Aggregate`() = runTest {
        val planId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val metaId = metaPlanRepo.saveMetaPlan(MetaTrainingPlan(name = "Meta",
            items = listOf(MetaTrainingPlanItem(trainingPlanId = planId, orderIndex = 0))))
        val yesterday = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(9, 0))
        workoutRepo.addSession(WorkoutSession(id = 99L, startTime = yesterday,
            endTime = yesterday.plusHours(1), durationSeconds = 3600,
            planId = planId, metaPlanId = metaId), isActive = false)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, workoutRepo.getAllCompletedSessionsListCallCount)
        assertNotNull(vm.uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaId })
    }

    @Test
    fun `startNewWorkout erstellt Session und ruft Callback`() = runTest {
        val vm = createViewModel()
        var receivedId: Long? = null

        vm.startNewWorkout { id, planId -> receivedId = id }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((receivedId ?: 0L) > 0L)
    }

    @Test
    fun `startNewWorkoutWithPlan creates session with plan name`() = runTest {
        val vm = createViewModel()
        var createdSessionId: Long? = null
        var planIdPass: Long? = null
        
        val testPlan = TrainingPlan(id = 99L, name = "My Test Plan", exercises = emptyList())
        
        vm.startNewWorkoutWithPlan(testPlan) { sessionId, planId ->
            createdSessionId = sessionId
            planIdPass = planId
        }
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(createdSessionId != null)
        assertEquals(99L, planIdPass)
        val session = workoutRepo.getSessionById(createdSessionId!!)
        assertEquals("My Test Plan", session?.name)
    }

    @Test
    fun `startNewWorkoutWithMetaPlan startet Session mit plan und meta ids`() = runTest {
        val planId = planRepo.savePlan(TrainingPlan(name = "Meta Subplan"))
        val metaPlanId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta 1",
                items = listOf(MetaTrainingPlanItem(trainingPlanId = planId, orderIndex = 0))
            )
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var createdSessionId: Long? = null
        var callbackPlanId: Long? = null
        var callbackMetaPlanId: Long? = null

        vm.startNewWorkoutWithMetaPlan(metaPlanId) { sessionId, planFromCallback, metaFromCallback ->
            createdSessionId = sessionId
            callbackPlanId = planFromCallback
            callbackMetaPlanId = metaFromCallback
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(createdSessionId)
        assertEquals(planId, callbackPlanId)
        assertEquals(metaPlanId, callbackMetaPlanId)
        val session = workoutRepo.getSessionById(createdSessionId!!)
        assertEquals(planId, session?.planId)
        assertEquals(metaPlanId, session?.metaPlanId)
    }

    @Test
    fun `startNewWorkoutWithMetaPlan setzt Fehler wenn kein gueltiger Unterplan vorhanden ist`() = runTest {
        val metaPlanId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Broken Meta",
                items = listOf(MetaTrainingPlanItem(trainingPlanId = 9999L, orderIndex = 0))
            )
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        var callbackCalled = false

        vm.startNewWorkoutWithMetaPlan(metaPlanId) { _, _, _ ->
            callbackCalled = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackCalled)
        assertTrue(vm.uiState.value.error?.contains("Meta-Plan ist unvollstaendig") == true)
        assertNull(workoutRepo.getActiveSession())
    }

    @Test
    fun `dashboard reloads stats when active workout is finished`() = runTest {
        val start = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 123L,
                startTime = start,
                endTime = null,
                durationSeconds = 0,
                name = "Active"
            ),
            isActive = true
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.activeSession)
        assertEquals(0, vm.uiState.value.workoutsThisWeek)

        workoutRepo.finishWorkout(123L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.activeSession)
        assertEquals(1, vm.uiState.value.workoutsThisWeek)
        assertNotNull(vm.uiState.value.lastWorkout)
    }

    @Test
    fun `dashboard reloads stats when completed sessions are deleted without active-session transition`() = runTest {
        val completed = LocalDateTime.of(LocalDate.now(), LocalTime.of(8, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 900L,
                startTime = completed,
                endTime = completed.plusHours(1),
                durationSeconds = 3600,
                name = "Done"
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.workoutsThisWeek)
        assertNotNull(vm.uiState.value.lastWorkout)

        workoutRepo.deleteSession(900L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.uiState.value.workoutsThisWeek)
        assertEquals(0, vm.uiState.value.workoutsThisMonth)
        assertEquals(0, vm.uiState.value.currentStreak)
        assertNull(vm.uiState.value.lastWorkout)
    }

    @Test
    fun `meta plan rotation uses last completed subplan as anchor`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val planCId = planRepo.savePlan(TrainingPlan(name = "Plan C"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta 1",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planCId, orderIndex = 2)
                )
            )
        )

        val twoDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(2), LocalTime.of(9, 0))
        val oneDayAgo = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(9, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 1,
                startTime = twoDaysAgo,
                endTime = twoDaysAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planAId,
                metaPlanId = metaId
            ),
            isActive = false
        )
        workoutRepo.addSession(
            WorkoutSession(
                id = 2,
                startTime = oneDayAgo,
                endTime = oneDayAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planBId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val option = vm.uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaId }
        assertNotNull(option)
        assertEquals(planCId, option?.nextPlan?.id)
        assertEquals(listOf(planCId, planAId, planBId), option?.rotationPlans?.map { it.plan.id })
    }

    @Test
    fun `meta plan rotation exposes per-subplan last done days`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta 2",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val threeDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(3), LocalTime.of(9, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 10,
                startTime = threeDaysAgo,
                endTime = threeDaysAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planAId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val option = vm.uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaId }
        assertNotNull(option)
        val statusByPlan = option!!.rotationPlans.associateBy({ it.plan.id }, { it.lastDoneDaysAgo })
        assertEquals(3L, statusByPlan[planAId])
        assertNull(statusByPlan[planBId])
    }
}
