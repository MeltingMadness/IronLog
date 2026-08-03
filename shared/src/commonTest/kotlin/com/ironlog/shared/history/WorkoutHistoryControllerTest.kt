package com.ironlog.shared.history

import com.ironlog.shared.model.CompletedWorkoutSummary
import com.ironlog.shared.model.CursorPage
import com.ironlog.shared.model.CursorPageRequest
import com.ironlog.shared.model.LastMetaPlanSession
import com.ironlog.shared.model.LastPlanSession
import com.ironlog.shared.model.PreviousExerciseSession
import com.ironlog.shared.model.WorkoutSession
import com.ironlog.shared.model.WorkoutSet
import com.ironlog.shared.repository.SharedWorkoutRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val sessionTime = LocalDateTime.parse("2026-03-24T10:15:00")

    @Test
    fun `refresh loads first page`() {
        val repository = FakePagedWorkoutRepository(
            pages = mapOf(
                null to CursorPage(
                    items = listOf(
                        summary(id = 1L, name = "A"),
                        summary(id = 2L, name = "B"),
                    ),
                    nextCursor = "page-2",
                ),
            ),
        )

        val controller = WorkoutHistoryController(
            scope = scope,
            workoutRepository = repository,
            pageSize = 2,
        )

        scope.advanceUntilIdle()

        assertEquals(listOf(1L, 2L), controller.state.value.items.map { it.session.id })
        assertEquals("page-2", controller.state.value.nextCursor)
    }

    @Test
    fun `loadMore appends next page`() {
        val repository = FakePagedWorkoutRepository(
            pages = mapOf(
                null to CursorPage(
                    items = listOf(summary(id = 1L, name = "A")),
                    nextCursor = "page-2",
                ),
                "page-2" to CursorPage(
                    items = listOf(summary(id = 2L, name = "B")),
                    nextCursor = null,
                ),
            ),
        )

        val controller = WorkoutHistoryController(
            scope = scope,
            workoutRepository = repository,
            pageSize = 1,
        )
        scope.advanceUntilIdle()

        controller.loadMore()
        scope.advanceUntilIdle()

        assertEquals(listOf(1L, 2L), controller.state.value.items.map { it.session.id })
        assertEquals(null, controller.state.value.nextCursor)
    }

    private fun summary(id: Long, name: String): CompletedWorkoutSummary = CompletedWorkoutSummary(
        session = WorkoutSession(id = id, startTime = sessionTime, name = name),
        exerciseCount = 3,
        setCount = 9,
        totalVolume = 1000.0,
    )
}

private class FakePagedWorkoutRepository(
    private val pages: Map<String?, CursorPage<CompletedWorkoutSummary>>,
) : SharedWorkoutRepository {
    override suspend fun startWorkout(name: String, planId: Long?, metaPlanId: Long?): Long = 1L
    override suspend fun finishWorkout(sessionId: Long) = Unit
    override suspend fun getActiveSession(): WorkoutSession? = null
    override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun addSet(set: WorkoutSet): Long = 1L
    override suspend fun updateSet(set: WorkoutSet) = Unit
    override suspend fun deleteSet(setId: Long) = Unit
    override fun observeSetsForSession(sessionId: Long): Flow<List<WorkoutSet>> = flowOf(emptyList())
    override suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet> = emptyList()

    override suspend fun getCompletedSessionSummariesPage(request: CursorPageRequest): CursorPage<CompletedWorkoutSummary> =
        pages[request.cursor] ?: CursorPage(emptyList(), null)

    override suspend fun getSessionById(id: Long): WorkoutSession? = null
    override fun observeSessionById(id: Long): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun deleteSession(sessionId: Long) = Unit
    override suspend fun getPreviousSessionDataForExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        planId: Long?,
    ): Map<Long, PreviousExerciseSession> = emptyMap()

    override fun observeLastSessionPerPlan(): Flow<List<LastPlanSession>> = flowOf(emptyList())
    override fun observeLastSessionPerMetaPlanSubPlan(): Flow<List<LastMetaPlanSession>> = flowOf(emptyList())
}
