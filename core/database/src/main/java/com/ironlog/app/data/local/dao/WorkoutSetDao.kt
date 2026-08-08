package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {

    @Insert
    suspend fun insert(set: WorkoutSetEntity): Long

    @Update
    suspend fun update(set: WorkoutSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(sets: List<WorkoutSetEntity>)

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    suspend fun getSetsForSessionList(sessionId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE sessionId IN (:sessionIds)")
    suspend fun getSetsForSessions(sessionIds: List<Long>): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY completedAt DESC")
    fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY completedAt DESC")
    suspend fun getSetsForExerciseList(exerciseId: Long): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workout_sessions s ON s.id = ws.sessionId
        WHERE ws.exerciseId IN (:exerciseIds)
          AND ws.sessionId != :currentSessionId
          AND s.endTime IS NOT NULL
          AND ws.sessionId = (
              SELECT ws2.sessionId
              FROM workout_sets ws2
              INNER JOIN workout_sessions s2 ON s2.id = ws2.sessionId
              WHERE ws2.exerciseId = ws.exerciseId
                AND ws2.sessionId != :currentSessionId
                AND s2.endTime IS NOT NULL
              ORDER BY s2.startTime DESC, ws2.sessionId DESC
              LIMIT 1
          )
        ORDER BY ws.exerciseId ASC, ws.setNumber ASC
    """
    )
    suspend fun getMostRecentCompletedSetsForExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>
    ): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workout_sessions s ON s.id = ws.sessionId
        WHERE ws.exerciseId IN (:exerciseIds)
          AND ws.sessionId != :currentSessionId
          AND s.endTime IS NOT NULL
          AND s.planId = :planId
          AND ws.sessionId = (
              SELECT ws2.sessionId
              FROM workout_sets ws2
              INNER JOIN workout_sessions s2 ON s2.id = ws2.sessionId
              WHERE ws2.exerciseId = ws.exerciseId
                AND ws2.sessionId != :currentSessionId
                AND s2.endTime IS NOT NULL
                AND s2.planId = :planId
              ORDER BY s2.startTime DESC, ws2.sessionId DESC
              LIMIT 1
          )
        ORDER BY ws.exerciseId ASC, ws.setNumber ASC
    """
    )
    suspend fun getMostRecentCompletedSetsForPlanExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        planId: Long
    ): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workout_sessions s ON s.id = ws.sessionId
        WHERE ws.exerciseId IN (:exerciseIds)
          AND ws.sessionId != :currentSessionId
          AND s.endTime IS NOT NULL
          AND s.planId = :planId AND s.metaPlanId IS NULL
          AND ws.sessionId = (
              SELECT ws2.sessionId
              FROM workout_sets ws2
              INNER JOIN workout_sessions s2 ON s2.id = ws2.sessionId
              WHERE ws2.exerciseId = ws.exerciseId
                AND ws2.sessionId != :currentSessionId
                AND s2.endTime IS NOT NULL
                AND s2.planId = :planId AND s2.metaPlanId IS NULL
              ORDER BY s2.startTime DESC, ws2.sessionId DESC
              LIMIT 1
          )
        ORDER BY ws.exerciseId ASC, ws.setNumber ASC
    """
    )
    suspend fun getMostRecentCompletedSetsForNormalPlanExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        planId: Long
    ): List<WorkoutSetEntity>

    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workout_sessions s ON s.id = ws.sessionId
        WHERE ws.exerciseId IN (:exerciseIds)
          AND ws.sessionId != :currentSessionId
          AND s.endTime IS NOT NULL
          AND s.planId = :planId AND s.metaPlanId = :metaPlanId
          AND ws.sessionId = (
              SELECT ws2.sessionId
              FROM workout_sets ws2
              INNER JOIN workout_sessions s2 ON s2.id = ws2.sessionId
              WHERE ws2.exerciseId = ws.exerciseId
                AND ws2.sessionId != :currentSessionId
                AND s2.endTime IS NOT NULL
                AND s2.planId = :planId AND s2.metaPlanId = :metaPlanId
              ORDER BY s2.startTime DESC, ws2.sessionId DESC
              LIMIT 1
          )
        ORDER BY ws.exerciseId ASC, ws.setNumber ASC
    """
    )
    suspend fun getMostRecentCompletedSetsForMetaPlanExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        planId: Long,
        metaPlanId: Long
    ): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets ORDER BY id ASC")
    suspend fun getAllSetsList(): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT exerciseId FROM workout_sets WHERE id = :id")
    suspend fun getExerciseIdForSet(id: Long): Long?

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT exerciseId FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun getExerciseIdsForSession(sessionId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun getSetCountForSession(sessionId: Long): Int

    @Query("SELECT SUM(weightKg * reps) FROM workout_sets WHERE sessionId = :sessionId AND isWarmup = 0")
    suspend fun getTotalVolumeForSession(sessionId: Long): Double?

    @Query("SELECT MAX(weightKg) FROM workout_sets WHERE exerciseId = :exerciseId AND isWarmup = 0")
    suspend fun getMaxWeightForExercise(exerciseId: Long): Double?

    @Query("SELECT MAX(reps) FROM workout_sets WHERE exerciseId = :exerciseId AND isWarmup = 0")
    suspend fun getMaxRepsForExercise(exerciseId: Long): Int?

    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE s.startTime >= :sinceEpochMillis AND s.endTime IS NOT NULL AND ws.isWarmup = 0
        ORDER BY s.startTime ASC
    """)
    suspend fun getWorkSetsCompletedSince(sinceEpochMillis: Long): List<WorkoutSetEntity>
}
