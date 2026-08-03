package com.ironlog.shared.dashboard

import com.ironlog.shared.model.CompletedWorkoutSummary
import com.ironlog.shared.model.CursorPage
import com.ironlog.shared.model.CursorPageRequest
import com.ironlog.shared.model.LastMetaPlanSession
import com.ironlog.shared.model.LastPlanSession
import com.ironlog.shared.model.MetaTrainingPlan
import com.ironlog.shared.model.MetaTrainingPlanItem
import com.ironlog.shared.model.PersonalRecord
import com.ironlog.shared.model.PreviousExerciseSession
import com.ironlog.shared.model.RecordType
import com.ironlog.shared.model.TrainingPlan
import com.ironlog.shared.model.WorkoutSession
import com.ironlog.shared.model.WorkoutSet
import com.ironlog.shared.repository.SharedMetaTrainingPlanRepository
import com.ironlog.shared.repository.SharedStatisticsRepository
import com.ironlog.shared.repository.SharedTrainingPlanRepository
import com.ironlog.shared.repository.SharedWorkoutRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `exposes combined dashboard data from shared repositories`() {
        val timestamp = LocalDateTime.parse("2026-03-24T10:15:00")
        val activeSession = WorkoutSession(id = 8L, startTime = timestamp)
        val recentRecord = PersonalRecord(
            id = 11L,
            exerciseId = 3L,
            type = RecordType.MAX_WEIGHT,
            value = 120.0,
            achievedAt = timestamp,
        )
        val plan = TrainingPlan(id = 5L, name = "Upper", exercises = emptyList())
        val metaPlan = MetaTrainingPlan(
            id = 7L,
            name = "Rotation",
            items = listOf(MetaTrainingPlanItem(trainingPlanId = 5L, orderIndex = 0)),
        )
        val lastPlanSession = LastPlanSession(planId = 5L, lastStartTime = 1_711_274_500_000L)
        val lastMetaPlanSession = LastMetaPlanSession(
            planId = 5L,
            metaPlanId = 7L,
            lastStartTime = 1_711_274_500_000L,
        )

        val controller = DashboardController(
            scope = scope,
            workoutRepository = FakeSharedWorkoutRepository(activeSession = activeSession).apply {
                lastPlanSessions.value = listOf(lastPlanSession)
                lastMetaPlanSessions.value = listOf(lastMetaPlanSession)
            },
            statisticsRepository = FakeSharedStatisticsRepository().apply {
                recentRecords.value = listOf(recentRecord)
            },
            trainingPlanRepository = FakeSharedTrainingPlanRepository().apply {
                plans.value = listOf(plan)
            },
            metaTrainingPlanRepository = FakeSharedMetaTrainingPlanRepository().apply {
                metaPlans.value = listOf(metaPlan)
            },
        )

        scope.advanceUntilIdle()

        assertEquals(activeSession, controller.state.value.activeSession)
        assertEquals(listOf(recentRecord), controller.state.value.recentRecords)
        assertEquals(listOf(plan), controller.state.value.trainingPlans)
        assertEquals(listOf(metaPlan), controller.state.value.metaPlans)
        assertEquals(listOf(lastPlanSession), controller.state.value.lastPlanSessions)
        assertEquals(listOf(lastMetaPlanSession), controller.state.value.lastMetaPlanSessions)
    }
}

private class FakeSharedWorkoutRepository(
    activeSession: WorkoutSession? = null,
) : SharedWorkoutRepository {
    private val activeSessionFlow = MutableStateFlow(activeSession)
    val lastPlanSessions = MutableStateFlow<List<LastPlanSession>>(emptyList())
    val lastMetaPlanSessions = MutableStateFlow<List<LastMetaPlanSession>>(emptyList())

    override suspend fun startWorkout(name: String, planId: Long?, metaPlanId: Long?): Long = 1L
    override suspend fun finishWorkout(sessionId: Long) = Unit
    override suspend fun getActiveSession(): WorkoutSession? = activeSessionFlow.value
    override fun observeActiveSession(): Flow<WorkoutSession?> = activeSessionFlow
    override suspend fun addSet(set: WorkoutSet): Long = 1L
    override suspend fun updateSet(set: WorkoutSet) = Unit
    override suspend fun deleteSet(setId: Long) = Unit
    override fun observeSetsForSession(sessionId: Long): Flow<List<WorkoutSet>> = flowOf(emptyList())
    override suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet> = emptyList()
    override suspend fun getCompletedSessionSummariesPage(request: CursorPageRequest): CursorPage<CompletedWorkoutSummary> =
        CursorPage(emptyList(), null)

    override suspend fun getSessionById(id: Long): WorkoutSession? = activeSessionFlow.value
    override fun observeSessionById(id: Long): Flow<WorkoutSession?> = activeSessionFlow
    override suspend fun deleteSession(sessionId: Long) = Unit
    override suspend fun getPreviousSessionDataForExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        planId: Long?,
    ): Map<Long, PreviousExerciseSession> = emptyMap()

    override fun observeLastSessionPerPlan(): Flow<List<LastPlanSession>> = lastPlanSessions
    override fun observeLastSessionPerMetaPlanSubPlan(): Flow<List<LastMetaPlanSession>> = lastMetaPlanSessions
}

private class FakeSharedTrainingPlanRepository : SharedTrainingPlanRepository {
    val plans = MutableStateFlow<List<TrainingPlan>>(emptyList())

    override fun observePlans(): Flow<List<TrainingPlan>> = plans
    override suspend fun getPlanById(id: Long): TrainingPlan? = plans.value.firstOrNull { it.id == id }
    override suspend fun savePlan(plan: TrainingPlan): Long = plan.id
    override suspend fun deletePlan(planId: Long) = Unit
}

private class FakeSharedMetaTrainingPlanRepository : SharedMetaTrainingPlanRepository {
    val metaPlans = MutableStateFlow<List<MetaTrainingPlan>>(emptyList())

    override fun observeMetaPlans(): Flow<List<MetaTrainingPlan>> = metaPlans
    override suspend fun getMetaPlanById(id: Long): MetaTrainingPlan? = metaPlans.value.firstOrNull { it.id == id }
    override suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long = plan.id
    override suspend fun deleteMetaPlan(metaPlanId: Long) = Unit
}

private class FakeSharedStatisticsRepository : SharedStatisticsRepository {
    val recentRecords = MutableStateFlow<List<PersonalRecord>>(emptyList())

    override suspend fun checkAndUpdateRecord(exerciseId: Long, type: RecordType, value: Double): Boolean = false
    override fun observeRecordsForExercise(exerciseId: Long): Flow<List<PersonalRecord>> = flowOf(emptyList())
    override suspend fun getRecordsForExercises(exerciseIds: List<Long>): List<PersonalRecord> = emptyList()
    override fun observeRecentRecords(limit: Int): Flow<List<PersonalRecord>> = recentRecords
    override suspend fun getRecentRecords(limit: Int): List<PersonalRecord> = recentRecords.value
    override fun observeSetsForExercise(exerciseId: Long): Flow<List<WorkoutSet>> = flowOf(emptyList())
    override suspend fun getSetsForExercise(exerciseId: Long): List<WorkoutSet> = emptyList()
    override suspend fun getMaxWeightForExercise(exerciseId: Long): Double? = null
    override suspend fun getMaxRepsForExercise(exerciseId: Long): Int? = null
    override suspend fun getWorkSetsCompletedSince(sinceEpochMillis: Long): List<WorkoutSet> = emptyList()
}
