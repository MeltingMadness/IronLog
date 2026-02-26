package com.ironlog.app.fakes

import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.ironlog.app.domain.model.CompletedWorkoutSummary
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWorkoutRepository : WorkoutRepository {

    private val sessions = MutableStateFlow<List<WorkoutSessionData>>(emptyList())
    private val sets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private var nextSessionId = 1L
    private var nextSetId = 1L

    var getExerciseIdsForSessionCallCount = 0
    var getSetCountForSessionCallCount = 0
    var getTotalVolumeForSessionCallCount = 0
    var getSetsForSessionsListCallCount = 0
    var getAllCompletedSessionsListCallCount = 0

    private fun java.time.LocalDateTime.toEpochMillis(): Long =
        atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    data class WorkoutSessionData(
        val session: WorkoutSession,
        val isActive: Boolean = false
    )

    // --- Controls for tests ---

    fun addSession(session: WorkoutSession, isActive: Boolean = false) {
        sessions.value = sessions.value + WorkoutSessionData(session, isActive)
    }

    fun addSetDirectly(set: WorkoutSet) {
        sets.value = sets.value + set
    }

    // --- WorkoutRepository ---

    suspend fun startWorkout(name: String = ""): Long =
        startWorkout(name = name, planId = null, metaPlanId = null)

    override suspend fun startWorkout(
        name: String,
        planId: Long?,
        metaPlanId: Long?
    ): Long {
        val id = nextSessionId++
        val session = WorkoutSession(
            id = id,
            startTime = java.time.LocalDateTime.now(),
            name = name,
            planId = planId,
            metaPlanId = metaPlanId
        )
        sessions.value = sessions.value + WorkoutSessionData(session, isActive = true)
        return id
    }

    override suspend fun finishWorkout(sessionId: Long) {
        sessions.value = sessions.value.map {
            if (it.session.id == sessionId) {
                val now = java.time.LocalDateTime.now()
                it.copy(
                    session = it.session.copy(endTime = now, durationSeconds = 3600),
                    isActive = false
                )
            } else it
        }
    }

    override suspend fun getActiveSession(): WorkoutSession? =
        sessions.value.find { it.isActive }?.session

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessions.map { list -> list.find { it.isActive }?.session }

    override suspend fun addSet(set: WorkoutSet): Long {
        val id = nextSetId++
        sets.value = sets.value + set.copy(id = id)
        return id
    }

    override suspend fun deleteSet(setId: Long) {
        sets.value = sets.value.filter { it.id != setId }
    }

    override fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSet>> =
        sets.map { list -> list.filter { it.sessionId == sessionId } }

    override suspend fun getSetsForSessionList(sessionId: Long): List<WorkoutSet> =
        sets.value.filter { it.sessionId == sessionId }

    override suspend fun getSetsForSessionsList(sessionIds: List<Long>): List<WorkoutSet> {
        getSetsForSessionsListCallCount++
        val idSet = sessionIds.toSet()
        return sets.value.filter { it.sessionId in idSet }
    }

    override fun getAllCompletedSessions(): Flow<List<WorkoutSession>> =
        sessions.map { list -> list.filter { !it.isActive }.map { it.session } }

    override fun getPagedCompletedSessions(): Flow<PagingData<WorkoutSession>> {
        val completed = sessions.value.filter { !it.isActive }.map { it.session }
        return kotlinx.coroutines.flow.flowOf(PagingData.from(completed))
    }

    override fun getPagedCompletedWorkoutSummaries(): Flow<PagingData<CompletedWorkoutSummary>> {
        val completed = sessions.value.filter { !it.isActive }.map {
            CompletedWorkoutSummary(
                session = it.session,
                exerciseCount = 0,
                setCount = 0,
                totalVolume = 0.0
            )
        }
        return kotlinx.coroutines.flow.flowOf(PagingData.from(completed))
    }

    override suspend fun getSessionById(id: Long): WorkoutSession? =
        sessions.value.find { it.session.id == id }?.session

    override fun observeSessionById(id: Long): Flow<WorkoutSession?> =
        sessions.map { list -> list.find { it.session.id == id }?.session }

    override suspend fun deleteSession(sessionId: Long) {
        sessions.value = sessions.value.filter { it.session.id != sessionId }
        sets.value = sets.value.filter { it.sessionId != sessionId }
    }

    override suspend fun getExerciseIdsForSession(sessionId: Long): List<Long> {
        getExerciseIdsForSessionCallCount++
        return sets.value.filter { it.sessionId == sessionId }.map { it.exerciseId }.distinct()
    }

    override suspend fun getSetCountForSession(sessionId: Long): Int {
        getSetCountForSessionCallCount++
        return sets.value.count { it.sessionId == sessionId }
    }

    override suspend fun getTotalVolumeForSession(sessionId: Long): Double {
        getTotalVolumeForSessionCallCount++
        return sets.value.filter { it.sessionId == sessionId }.sumOf { it.weightKg * it.reps }
    }

    override suspend fun getCompletedSessionCountSince(sinceEpochMillis: Long): Int =
        sessions.value.count { !it.isActive }

    override suspend fun getLastCompletedSession(): WorkoutSession? =
        sessions.value.filter { !it.isActive }.maxByOrNull { it.session.startTime }?.session

    override suspend fun getAllCompletedSessionsList(): List<WorkoutSession> {
        getAllCompletedSessionsListCallCount++
        return sessions.value.filter { !it.isActive }.map { it.session }
    }

    override suspend fun getCompletedWorkoutStartTimesDesc(): List<Long> =
        sessions.value
            .filter { !it.isActive }
            .map { it.session.startTime.toEpochMillis() }
            .sortedDescending()

    override fun observeLastSessionPerMetaPlanSubPlan(): Flow<List<com.ironlog.app.domain.model.LastMetaPlanSession>> =
        sessions.map { list ->
            list.filter { !it.isActive && it.session.metaPlanId != null && it.session.planId != null }
                .groupBy { it.session.planId!! to it.session.metaPlanId!! }
                .map { (key, entries) ->
                    com.ironlog.app.domain.model.LastMetaPlanSession(
                        planId = key.first,
                        metaPlanId = key.second,
                        lastStartTime = entries.maxOf { it.session.startTime.toEpochMillis() }
                    )
                }
        }
}
