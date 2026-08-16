package com.ironlog.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.ProgressionDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.ProgressionTargetColumns
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.model.CompletedWorkoutSummary
import com.ironlog.app.domain.model.PreviousExerciseSession
import com.ironlog.app.domain.model.PreviousSessionScope
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.WorkoutCalculations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

class WorkoutRepositoryImpl(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val personalRecordDao: PersonalRecordDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val progressionDao: ProgressionDao,
    private val transactionRunner: TransactionRunner
) : WorkoutRepository {
    private val startWorkoutMutex = Mutex()

    suspend fun startWorkout(name: String = ""): Long =
        startWorkout(name = name, planId = null, metaPlanId = null)

    override suspend fun startWorkout(
        name: String,
        planId: Long?,
        metaPlanId: Long?
    ): Long {
        return startWorkoutMutex.withLock {
            // Invariant: there can only be one active session.
            sessionDao.getActiveSession()?.let { return@withLock it.id }

            transactionRunner.runInTransaction {
                sessionDao.getActiveSession()?.let { return@runInTransaction it.id }
                if (planId != null) {
                    require(trainingPlanDao.getPlanById(planId) != null) {
                        "Training plan $planId does not exist"
                    }
                }
                val sessionId = sessionDao.insert(
                    WorkoutSessionEntity(
                        startTime = EpochConverter.toLong(LocalDateTime.now()),
                        name = name,
                        planId = planId,
                        metaPlanId = metaPlanId
                    )
                )
                if (planId != null) {
                    val targets = trainingPlanDao.getExercisesForPlan(planId)
                        .sortedBy { it.orderIndex }
                        .map { planExercise ->
                            WorkoutPlanTargetEntity(
                                sessionId = sessionId,
                                planId = planId,
                                exerciseId = planExercise.exerciseId,
                                orderIndex = planExercise.orderIndex,
                                supersetGroupId = planExercise.supersetGroupId,
                                target = ProgressionTargetColumns(
                                    sets = planExercise.targetSets,
                                    reps = planExercise.targetReps,
                                    weightKg = planExercise.targetWeightKg
                                ),
                                progression = planExercise.progression
                            )
                        }
                    val insertedIds = progressionDao.insertTargets(targets)
                    check(insertedIds.size == targets.size && insertedIds.all { it > 0L }) {
                        "Incomplete plan target snapshot"
                    }
                }
                sessionId
            }
        }
    }

    override suspend fun finishWorkout(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val now = LocalDateTime.now()
        val nowMillis = EpochConverter.toLong(now)
        val durationSeconds = (nowMillis - session.startTime) / 1000
        sessionDao.update(
            session.copy(
                endTime = nowMillis,
                durationSeconds = durationSeconds
            )
        )
        // Personal records only count finished workouts (see recalculatePersonalRecords), so
        // this session's sets become eligible for records exactly now that it is completed.
        setDao.getExerciseIdsForSession(sessionId).forEach { exerciseId ->
            recalculatePersonalRecords(exerciseId)
        }
    }

    override suspend fun getActiveSession(): WorkoutSession? =
        sessionDao.getActiveSession()?.toDomain()

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeActiveSession().map { it?.toDomain() }

    override suspend fun addSet(set: WorkoutSet): Long =
        transactionRunner.runInTransaction {
            requireActiveSession(set.sessionId)
            set.planTargetSnapshotId?.let { targetId ->
                val target = progressionDao.getTargetById(targetId)
                check(
                    target != null &&
                        target.sessionId == set.sessionId &&
                        target.exerciseId == set.exerciseId
                ) {
                    "Plan target $targetId does not belong to session ${set.sessionId} and exercise ${set.exerciseId}"
                }
            }
            val id = setDao.insert(WorkoutSetEntity.fromDomain(set))
            // The insert and the exact PR rebuild must share one transaction and run before
            // any observer can compare records, otherwise a concurrent delete/update could
            // recalculate first and the stale add-set values would resurrect a ghost PR.
            if (!set.isWarmup) {
                recalculatePersonalRecords(set.exerciseId)
            }
            id
        }

    override suspend fun updateSet(set: WorkoutSet) {
        transactionRunner.runInTransaction {
            val stored = setDao.getSetById(set.id)
                ?: throw IllegalStateException("Workout set ${set.id} does not exist")
            requireActiveSession(stored.sessionId)
            val updated = WorkoutSetEntity.fromDomain(set)
            check(
                updated.sessionId == stored.sessionId &&
                    updated.exerciseId == stored.exerciseId &&
                    updated.setNumber == stored.setNumber &&
                    updated.isWarmup == stored.isWarmup &&
                    updated.completedAt == stored.completedAt &&
                    updated.planTargetSnapshotId == stored.planTargetSnapshotId
            ) {
                "Workout set identity fields cannot be changed"
            }
            setDao.update(updated)
            recalculatePersonalRecords(stored.exerciseId)
        }
    }

    override suspend fun deleteSet(setId: Long) {
        transactionRunner.runInTransaction {
            val stored = setDao.getSetById(setId) ?: return@runInTransaction
            requireActiveSession(stored.sessionId)
            setDao.deleteSet(setId)
            recalculatePersonalRecords(stored.exerciseId)
        }
    }

    private suspend fun requireActiveSession(sessionId: Long): WorkoutSessionEntity {
        val session = sessionDao.getSessionById(sessionId)
        check(session != null && session.endTime == null) {
            "Workout session $sessionId is not active"
        }
        return session
    }

    override fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSet>> =
        setDao.getSetsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSetsForSessionList(sessionId: Long): List<WorkoutSet> =
        setDao.getSetsForSessionList(sessionId).map { it.toDomain() }

    override suspend fun getSetsForSessionsList(sessionIds: List<Long>): List<WorkoutSet> {
        if (sessionIds.isEmpty()) return emptyList()
        return setDao.getSetsForSessions(sessionIds).map { it.toDomain() }
    }

    override fun getAllCompletedSessions(): Flow<List<WorkoutSession>> =
        sessionDao.getAllCompletedSessions().map { list -> list.map { it.toDomain() } }

    override fun getPagedCompletedSessions(): Flow<PagingData<WorkoutSession>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { sessionDao.getPagedCompletedSessions() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override fun getPagedCompletedWorkoutSummaries(): Flow<PagingData<CompletedWorkoutSummary>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { sessionDao.getPagedCompletedSessionsWithSets() }
        ).flow.map { pagingData ->
            pagingData.map { relation ->
                val sets = relation.sets
                CompletedWorkoutSummary(
                    session = relation.session.toDomain(),
                    exerciseCount = sets.map { it.exerciseId }.distinct().size,
                    setCount = sets.size,
                    totalVolume = sets.filter { !it.isWarmup }.sumOf { it.weightKg * it.reps }
                )
            }
        }
    }

    override suspend fun getSessionById(id: Long): WorkoutSession? =
        sessionDao.getSessionById(id)?.toDomain()

    override fun observeSessionById(id: Long): Flow<WorkoutSession?> =
        sessionDao.observeSessionById(id).map { it?.toDomain() }

    override suspend fun deleteSession(sessionId: Long) {
        transactionRunner.runInTransaction {
            // Personal records have no FK/cascade relationship to sessions or sets, so capture the
            // affected exercises before deleting and rebuild their records afterwards, otherwise
            // stale ("orphaned") records referencing now-deleted sets would remain forever.
            val affectedExerciseIds = setDao.getExerciseIdsForSession(sessionId)
            sessionDao.deleteSession(sessionId)
            affectedExerciseIds.forEach { exerciseId -> recalculatePersonalRecords(exerciseId) }
        }
    }

    /**
     * Rebuilds MAX_WEIGHT / MAX_REPS / MAX_E1RM / MAX_VOLUME personal records for [exerciseId]
     * from whatever work sets remain, updating or removing PR rows as needed.
     */
    private suspend fun recalculatePersonalRecords(exerciseId: Long) {
        val allWorkSets = setDao.getSetsForExerciseList(exerciseId).filterNot { it.isWarmup }

        // Weight/reps/E1RM records are per-set achievements and stay live during a
        // session (a set just completed IS a personal record). Volume is per-session
        // and must only reflect finished workouts: the partial volume of an active or
        // abandoned session must never set or hold the MAX_VOLUME record. finishWorkout()
        // triggers this rebuild once the session is completed.
        val bestWeightSet = allWorkSets.maxByOrNull { it.weightKg }
        upsertOrClearRecord(exerciseId, RecordType.MAX_WEIGHT, bestWeightSet?.weightKg, bestWeightSet?.completedAt)

        val bestRepsSet = allWorkSets.maxByOrNull { it.reps }
        upsertOrClearRecord(exerciseId, RecordType.MAX_REPS, bestRepsSet?.reps?.toDouble(), bestRepsSet?.completedAt)

        val bestE1rmSet = allWorkSets.maxByOrNull { WorkoutCalculations.calculateE1RM(it.weightKg, it.reps) }
        upsertOrClearRecord(
            exerciseId,
            RecordType.MAX_E1RM,
            bestE1rmSet?.let { WorkoutCalculations.calculateE1RM(it.weightKg, it.reps) },
            bestE1rmSet?.completedAt
        )

        val completedSessionIds = if (allWorkSets.isEmpty()) {
            emptySet()
        } else {
            sessionDao
                .getSessionsByIds(allWorkSets.map { it.sessionId }.distinct())
                .filter { it.endTime != null }
                .map { it.id }
                .toSet()
        }
        val completedWorkSets = allWorkSets.filter { it.sessionId in completedSessionIds }

        val volumeBySession = completedWorkSets
            .groupBy { it.sessionId }
            .mapValues { (_, sets) -> sets.sumOf { it.weightKg * it.reps } }
        val bestVolumeSessionId = volumeBySession.maxByOrNull { it.value }?.key
        val bestVolume = bestVolumeSessionId?.let { volumeBySession[it] }
        val bestVolumeAchievedAt = bestVolumeSessionId
            ?.let { sid -> completedWorkSets.filter { it.sessionId == sid }.maxOfOrNull(WorkoutSetEntity::completedAt) }
        upsertOrClearRecord(exerciseId, RecordType.MAX_VOLUME, bestVolume, bestVolumeAchievedAt)
    }

    private suspend fun upsertOrClearRecord(
        exerciseId: Long,
        type: RecordType,
        value: Double?,
        achievedAt: Long?
    ) {
        val existing = personalRecordDao.getRecord(exerciseId, type.name)
        // A non-positive value (e.g. 0.0 volume from zero-weight sets) is no record at all:
        // clear the row instead of persisting a bogus "Rekord: 0,0 kg".
        val recordValue = value?.takeIf { it > 0.0 }
        if (recordValue == null || achievedAt == null) {
            if (existing != null) personalRecordDao.deleteRecord(exerciseId, type.name)
            return
        }
        personalRecordDao.insert(
            PersonalRecordEntity(
                id = existing?.id ?: 0,
                exerciseId = exerciseId,
                type = type.name,
                value = recordValue,
                achievedAt = achievedAt
            )
        )
    }

    override suspend fun getExerciseIdsForSession(sessionId: Long): List<Long> =
        setDao.getExerciseIdsForSession(sessionId)

    override suspend fun getSetCountForSession(sessionId: Long): Int =
        setDao.getSetCountForSession(sessionId)

    override suspend fun getTotalVolumeForSession(sessionId: Long): Double =
        setDao.getTotalVolumeForSession(sessionId) ?: 0.0

    override suspend fun getCompletedSessionCountSince(sinceEpochMillis: Long): Int =
        sessionDao.getCompletedSessionCountSince(sinceEpochMillis)

    override suspend fun getLastCompletedSession(): WorkoutSession? =
        sessionDao.getLastCompletedSession()?.toDomain()

    override suspend fun getAllCompletedSessionsList(): List<WorkoutSession> =
        sessionDao.getAllCompletedSessionsList().map { it.toDomain() }

    override suspend fun getPreviousSessionDataForExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        scope: PreviousSessionScope
    ): Map<Long, PreviousExerciseSession> {
        if (exerciseIds.isEmpty()) return emptyMap()

        val latestSets = when (scope) {
            PreviousSessionScope.Global -> setDao.getMostRecentCompletedSetsForExercises(
                currentSessionId = currentSessionId,
                exerciseIds = exerciseIds
            )
            is PreviousSessionScope.NormalPlan ->
                setDao.getMostRecentCompletedSetsForNormalPlanExercises(
                    currentSessionId = currentSessionId,
                    exerciseIds = exerciseIds,
                    planId = scope.planId
                )
            is PreviousSessionScope.MetaPlan ->
                setDao.getMostRecentCompletedSetsForMetaPlanExercises(
                    currentSessionId = currentSessionId,
                    exerciseIds = exerciseIds,
                    planId = scope.planId,
                    metaPlanId = scope.metaPlanId
                )
            is PreviousSessionScope.SharedPlan ->
                setDao.getMostRecentCompletedSetsForPlanExercises(
                    currentSessionId = currentSessionId,
                    exerciseIds = exerciseIds,
                    planId = scope.planId
                )
        }
        if (latestSets.isEmpty()) return emptyMap()

        val sessionIds = latestSets.map { it.sessionId }.distinct()
        val sessionById = sessionDao.getSessionsByIds(sessionIds).associateBy { it.id }

        return latestSets
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, setsForExercise) ->
                val sessionId = setsForExercise.first().sessionId
                val sessionStart = sessionById[sessionId]?.toDomain()?.startTime ?: return@mapNotNull null
                val domainSets = setsForExercise.map(WorkoutSetEntity::toDomain)
                exerciseId to PreviousExerciseSession(
                    sessionId = sessionId,
                    sessionStart = sessionStart,
                    sets = domainSets,
                    lastWorkSetWeightKg = domainSets.lastOrNull { !it.isWarmup }?.weightKg
                )
            }
            .toMap()
    }

    override fun observeLastSessionPerPlan(): Flow<List<com.ironlog.app.domain.model.LastPlanSession>> =
        sessionDao.observeLastSessionPerPlan().map { rows ->
            rows.map { com.ironlog.app.domain.model.LastPlanSession(it.planId, it.lastStartTime) }
        }

    override fun observeLastSessionPerMetaPlanSubPlan(): Flow<List<com.ironlog.app.domain.model.LastMetaPlanSession>> =
        sessionDao.observeLastSessionPerMetaPlanSubPlan().map { rows ->
            rows.map { com.ironlog.app.domain.model.LastMetaPlanSession(it.planId, it.metaPlanId, it.lastStartTime) }
        }
}
