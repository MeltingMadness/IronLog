package com.ironlog.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class WorkoutRepositoryImpl(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao
) : WorkoutRepository {

    override suspend fun startWorkout(name: String): Long {
        // Invariant: there can only be one active session.
        sessionDao.getActiveSession()?.let { return it.id }

        val now = LocalDateTime.now()
        val entity = WorkoutSessionEntity(
            startTime = EpochConverter.toLong(now),
            name = name
        )
        return sessionDao.insert(entity)
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
    }

    override suspend fun getActiveSession(): WorkoutSession? =
        sessionDao.getActiveSession()?.toDomain()

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeActiveSession().map { it?.toDomain() }

    override suspend fun addSet(set: WorkoutSet): Long =
        setDao.insert(WorkoutSetEntity.fromDomain(set))

    override suspend fun deleteSet(setId: Long) =
        setDao.deleteSet(setId)

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

    override suspend fun getSessionById(id: Long): WorkoutSession? =
        sessionDao.getSessionById(id)?.toDomain()

    override fun observeSessionById(id: Long): Flow<WorkoutSession?> =
        sessionDao.observeSessionById(id).map { it?.toDomain() }

    override suspend fun deleteSession(sessionId: Long) =
        sessionDao.deleteSession(sessionId)

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
}
