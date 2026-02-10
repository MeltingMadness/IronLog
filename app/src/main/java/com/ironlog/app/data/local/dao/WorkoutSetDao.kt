package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {

    @Insert
    suspend fun insert(set: WorkoutSetEntity): Long

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    suspend fun getSetsForSessionList(sessionId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY completedAt DESC")
    fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY completedAt DESC")
    suspend fun getSetsForExerciseList(exerciseId: Long): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

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
}
