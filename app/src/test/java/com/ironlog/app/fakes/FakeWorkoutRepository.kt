package com.ironlog.app.fakes

import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.ironlog.app.domain.model.CompletedWorkoutSummary
import com.ironlog.app.domain.model.PreviousExerciseSession
import com.ironlog.app.domain.model.PreviousSessionScope
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
    var addSetCallCount = 0
    var updateSetCallCount = 0
    var deleteSetCallCount = 0
    var finishWorkoutCallCount = 0
    var deleteSessionCallCount = 0

    var failAddSet = false
    var failUpdateSet = false
    var failDeleteSet = false
    var failFinishWorkout = false
    var failDeleteSession = false

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
        // Invariant: there can only be one active session, matching WorkoutRepositoryImpl -
        // if one already exists, return its id and ignore the requested name/planId/metaPlanId.
        sessions.value.find { it.isActive }?.let { return it.session.id }

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
        finishWorkoutCallCount++
        if (failFinishWorkout) {
            throw IllegalStateException("Injected finishWorkout failure")
        }
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
        addSetCallCount++
        if (failAddSet) {
            throw IllegalStateException("Injected addSet failure")
        }
        val id = nextSetId++
        sets.value = sets.value + set.copy(id = id)
        return id
    }

    override suspend fun updateSet(set: WorkoutSet) {
        updateSetCallCount++
        if (failUpdateSet) {
            throw IllegalStateException("Injected updateSet failure")
        }
        sets.value = sets.value.map { existing ->
            if (existing.id == set.id) set else existing
        }
    }

    override suspend fun deleteSet(setId: Long) {
        deleteSetCallCount++
        if (failDeleteSet) {
            throw IllegalStateException("Injected deleteSet failure")
        }
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
        deleteSessionCallCount++
        if (failDeleteSession) {
            throw IllegalStateException("Injected deleteSession failure")
        }
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

    override suspend fun getPreviousSessionDataForExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        scope: PreviousSessionScope
    ): Map<Long, PreviousExerciseSession> {
        if (exerciseIds.isEmpty()) return emptyMap()

        val completedById = sessions.value
            .filter { !it.isActive && it.session.id != currentSessionId }
            .filter { sessionData ->
                when (scope) {
                    PreviousSessionScope.Global -> true
                    is PreviousSessionScope.NormalPlan ->
                        sessionData.session.planId == scope.planId &&
                            sessionData.session.metaPlanId == null
                    is PreviousSessionScope.MetaPlan ->
                        sessionData.session.planId == scope.planId &&
                            sessionData.session.metaPlanId == scope.metaPlanId
                    is PreviousSessionScope.SharedPlan ->
                        sessionData.session.planId == scope.planId
                }
            }
            .associateBy { it.session.id }
        if (completedById.isEmpty()) return emptyMap()

        return exerciseIds.distinct().mapNotNull { exerciseId ->
            val previousSessionId = sets.value
                .filter { set ->
                    set.exerciseId == exerciseId &&
                        set.sessionId in completedById.keys
                }
                .sortedByDescending { completedById[it.sessionId]!!.session.startTime }
                .firstOrNull()
                ?.sessionId
                ?: return@mapNotNull null

            val session = completedById[previousSessionId]?.session ?: return@mapNotNull null
            val sessionSets = sets.value
                .filter { it.exerciseId == exerciseId && it.sessionId == previousSessionId }
                .sortedBy { it.setNumber }
            exerciseId to PreviousExerciseSession(
                sessionId = previousSessionId,
                sessionStart = session.startTime,
                sets = sessionSets,
                lastWorkSetWeightKg = sessionSets.lastOrNull { !it.isWarmup }?.weightKg
            )
        }.toMap()
    }

    override fun observeLastSessionPerPlan(): Flow<List<com.ironlog.app.domain.model.LastPlanSession>> =
        sessions.map { list ->
            list.filter { !it.isActive && it.session.planId != null }
                .groupBy { it.session.planId!! }
                .map { (planId, entries) ->
                    com.ironlog.app.domain.model.LastPlanSession(
                        planId = planId,
                        lastStartTime = entries.maxOf { it.session.startTime.toEpochMillis() }
                    )
                }
        }

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
