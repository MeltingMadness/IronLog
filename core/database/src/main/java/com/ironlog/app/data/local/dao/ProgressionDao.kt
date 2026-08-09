package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTargets(targets: List<WorkoutPlanTargetEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSuggestion(suggestion: ProgressionSuggestionEntity): Long

    @Update
    suspend fun updateSuggestion(suggestion: ProgressionSuggestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllTargets(values: List<WorkoutPlanTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllSuggestions(values: List<ProgressionSuggestionEntity>)

    @Query("SELECT * FROM workout_plan_targets WHERE sessionId = :sessionId ORDER BY orderIndex, id")
    fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTargetEntity>>

    @Query("SELECT * FROM workout_plan_targets WHERE sessionId = :sessionId ORDER BY orderIndex, id")
    suspend fun getTargetsForSession(sessionId: Long): List<WorkoutPlanTargetEntity>

    @Query("SELECT * FROM workout_plan_targets WHERE id = :id LIMIT 1")
    suspend fun getTargetById(id: Long): WorkoutPlanTargetEntity?

    @Query("SELECT * FROM progression_suggestions WHERE sourceSessionId = :sessionId ORDER BY orderIndex, id")
    fun observeSuggestionsForSession(sessionId: Long): Flow<List<ProgressionSuggestionEntity>>

    @Query("SELECT p.* FROM progression_suggestions p JOIN workout_sessions s ON s.id = p.sourceSessionId WHERE p.status = 'PENDING' ORDER BY s.endTime DESC, s.id DESC, p.orderIndex, p.id")
    fun observePendingSuggestions(): Flow<List<ProgressionSuggestionEntity>>

    @Query("SELECT COUNT(*) FROM progression_suggestions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM progression_suggestions WHERE id IN (:ids) ORDER BY id")
    suspend fun getSuggestionsByIds(ids: Set<Long>): List<ProgressionSuggestionEntity>

    @Query("SELECT * FROM progression_suggestions WHERE status = 'PENDING' ORDER BY id")
    suspend fun getPendingSuggestions(): List<ProgressionSuggestionEntity>

    @Query(
        """
        SELECT t.* FROM workout_plan_targets t
        JOIN workout_sessions s ON s.id = t.sessionId
        WHERE t.planId = :planId
          AND t.exerciseId = :exerciseId
          AND t.orderIndex = :orderIndex
          AND s.endTime IS NOT NULL
          AND (s.endTime < :sourceEndTime OR (s.endTime = :sourceEndTime AND s.id < :sourceSessionId))
        ORDER BY s.endTime DESC, s.id DESC, t.id DESC
        """
    )
    suspend fun getPreviousTargets(
        planId: Long,
        exerciseId: Long,
        orderIndex: Int,
        sourceEndTime: Long,
        sourceSessionId: Long
    ): List<WorkoutPlanTargetEntity>

    @Query("SELECT * FROM progression_suggestions WHERE sourceTargetSnapshotId IN (:targetIds) ORDER BY sourceTargetSnapshotId, id")
    suspend fun getSuggestionsForTargetIds(targetIds: List<Long>): List<ProgressionSuggestionEntity>

    @Query(
        """
        SELECT DISTINCT t.sessionId FROM workout_plan_targets t
        JOIN workout_sessions s ON s.id = t.sessionId
        WHERE s.endTime IS NOT NULL
          AND NOT (
              t.progressionScheme = 'MANUAL'
              AND t.progressionIncrementValue IS NULL
              AND t.progressionIncrementUnit IS NULL
              AND t.progressionIncrementKg IS NULL
              AND t.progressionMinReps IS NULL
              AND t.progressionMaxReps IS NULL
              AND t.progressionTargetTotalReps IS NULL
              AND t.progressionTargetRpe IS NULL
              AND t.progressionRpeTolerance IS NULL
          )
          AND NOT EXISTS (
              SELECT 1 FROM progression_suggestions p
              WHERE p.sourceTargetSnapshotId = t.id
                AND p.sourceProgressionRuleRevision = t.progressionRuleRevision
          )
        ORDER BY s.endTime, s.id
        """
    )
    suspend fun getCompletedSessionIdsWithMissingOutcomes(): List<Long>

    @Query(
        """
        SELECT DISTINCT t.sessionId FROM workout_plan_targets t
        JOIN workout_sessions s ON s.id = t.sessionId
        WHERE s.endTime IS NOT NULL
          AND (s.endTime < :sourceEndTime OR (s.endTime = :sourceEndTime AND s.id < :sourceSessionId))
          AND NOT (
              t.progressionScheme = 'MANUAL'
              AND t.progressionIncrementValue IS NULL
              AND t.progressionIncrementUnit IS NULL
              AND t.progressionIncrementKg IS NULL
              AND t.progressionMinReps IS NULL
              AND t.progressionMaxReps IS NULL
              AND t.progressionTargetTotalReps IS NULL
              AND t.progressionTargetRpe IS NULL
              AND t.progressionRpeTolerance IS NULL
          )
          AND NOT EXISTS (
              SELECT 1 FROM progression_suggestions p
              WHERE p.sourceTargetSnapshotId = t.id
                AND p.sourceProgressionRuleRevision = t.progressionRuleRevision
          )
        ORDER BY s.endTime, s.id
        """
    )
    suspend fun getCompletedSessionIdsWithMissingOutcomesBefore(
        sourceEndTime: Long,
        sourceSessionId: Long
    ): List<Long>

    @Query("SELECT * FROM workout_plan_targets ORDER BY id")
    suspend fun getAllTargets(): List<WorkoutPlanTargetEntity>

    @Query("SELECT * FROM progression_suggestions ORDER BY id")
    suspend fun getAllSuggestions(): List<ProgressionSuggestionEntity>

    @Query("DELETE FROM progression_suggestions")
    suspend fun deleteAllSuggestions()

    @Query("DELETE FROM workout_plan_targets")
    suspend fun deleteAllTargets()
}
