package com.ironlog.app.presentation.history

import androidx.paging.PagingData
import androidx.paging.map
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelDeletionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: FakeWorkoutRepository
    private lateinit var planRepo: FakeTrainingPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
        planRepo = FakeTrainingPlanRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteSession removes session from repository`() = runTest {
        val now = LocalDateTime.now()
        workoutRepo.addSession(
            WorkoutSession(id = 1L, startTime = now.minusDays(2), endTime = now.minusDays(2).plusHours(1), durationSeconds = 3600),
            isActive = false
        )
        
        val vm = WorkoutHistoryViewModel(workoutRepo, planRepo)
        
        // delete the session
        vm.deleteSession(1L)
        advanceUntilIdle()

        // verify repository is empty
        val sessions = workoutRepo.getAllCompletedSessionsList()
        assertTrue(sessions.isEmpty())
        
        // error state should be null
        assertTrue(vm.uiState.value.error == null)
    }

    @Test
    fun `deleteSession failure sets an error and keeps the session`() = runTest {
        val now = LocalDateTime.now()
        workoutRepo.addSession(
            WorkoutSession(id = 2L, startTime = now.minusDays(1), endTime = now.minusDays(1).plusHours(1), durationSeconds = 3600),
            isActive = false
        )

        val vm = WorkoutHistoryViewModel(workoutRepo, planRepo)

        workoutRepo.failDeleteSession = true
        vm.deleteSession(2L)
        advanceUntilIdle()

        // session must survive a failed delete
        val sessions = workoutRepo.getAllCompletedSessionsList()
        assertEquals(1, sessions.size)
        assertEquals(2L, sessions.single().id)

        // error state must be set (toAppError: IllegalStateException -> Conflict)
        assertTrue(
            "delete failure must surface an error",
            vm.uiState.value.error?.contains("Training löschen") == true
        )
    }

    @Test
    fun `history filter requests plan and time predicates from the full dataset query`() = runTest {
        val vm = WorkoutHistoryViewModel(workoutRepo, planRepo)

        vm.setPlanFilter(42L)
        vm.setTimeRange(HistoryTimeRange.LAST_30_DAYS)
        vm.pagedWorkouts.first()

        val call = workoutRepo.lastPagedCompletedWorkoutSummariesFilter
        assertTrue("the filtered repository query must be invoked", call != null)
        assertEquals(42L, call!!.first)
        assertTrue("a bounded time range must provide a lower bound", call.second != null)
        assertEquals(null, call.third)
    }

    @Test
    fun `history time range converts to start of local calendar day`() {
        val anchor = LocalDateTime.of(2026, 4, 15, 18, 30)
        val expected = LocalDate.of(2026, 3, 16)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, HistoryTimeRange.LAST_30_DAYS.fromEpochMillis(anchor))
        assertEquals(null, HistoryTimeRange.ALL_TIME.fromEpochMillis(anchor))
    }
}
