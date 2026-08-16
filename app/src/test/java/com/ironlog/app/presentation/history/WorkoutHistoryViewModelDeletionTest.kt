package com.ironlog.app.presentation.history

import androidx.paging.PagingData
import androidx.paging.map
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.WorkoutRepository
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
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelDeletionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: FakeWorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
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
        
        val vm = WorkoutHistoryViewModel(workoutRepo)
        
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

        val vm = WorkoutHistoryViewModel(workoutRepo)

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
}
