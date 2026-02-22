package com.ironlog.app.presentation.dashboard

import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.app.fakes.FakeExerciseRepository
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
        exerciseRepo = FakeExerciseRepository()
        statsRepo = FakeStatisticsRepository()
        preferencesRepo = FakeAppPreferencesRepository()
        planRepo = FakeTrainingPlanRepository()
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
        planRepo
    )

    @Test
    fun `Streak ist 0 bei keinen Trainings`() = runTest {
        val vm = createViewModel()
        val streak = vm.calculateStreak()
        assertEquals(0, streak)
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
    fun `startNewWorkout erstellt Session und ruft Callback`() = runTest {
        val vm = createViewModel()
        var receivedId: Long? = null

        vm.startNewWorkout { id, planId -> receivedId = id }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(receivedId != null && receivedId!! > 0)
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
}
