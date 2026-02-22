package com.ironlog.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(sessions: List<WorkoutSessionEntity>)

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getAllCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getPagedCompletedSessions(): PagingSource<Int, WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY id ASC")
    suspend fun getAllSessionsList(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSessionById(id: Long): Flow<WorkoutSessionEntity?>

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE endTime IS NOT NULL AND startTime >= :sinceEpoch")
    suspend fun getCompletedSessionCountSince(sinceEpoch: Long): Int

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastCompletedSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun getAllCompletedSessionsList(): List<WorkoutSessionEntity>
}
