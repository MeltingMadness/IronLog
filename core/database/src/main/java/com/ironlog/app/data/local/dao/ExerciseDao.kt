package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ironlog.app.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    suspend fun getAllExercisesList(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE primaryMuscleGroup = :muscleGroup ORDER BY name ASC")
    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getExercisesByIds(ids: List<Long>): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(exercises: List<ExerciseEntity>)

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET isArchived = 1 WHERE id = :id AND isCustom = 1")
    suspend fun archiveCustomExercise(id: Long)

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomExercise(id: Long)

    @Query(
        """
        SELECT CASE WHEN EXISTS(SELECT 1 FROM plan_exercises WHERE exerciseId = :exerciseId)
            OR EXISTS(SELECT 1 FROM workout_sets WHERE exerciseId = :exerciseId)
            OR EXISTS(SELECT 1 FROM personal_records WHERE exerciseId = :exerciseId)
            OR EXISTS(SELECT 1 FROM workout_plan_targets WHERE exerciseId = :exerciseId)
            OR EXISTS(SELECT 1 FROM progression_suggestions WHERE exerciseId = :exerciseId)
        THEN 1 ELSE 0 END
        """
    )
    suspend fun isExerciseReferenced(exerciseId: Long): Boolean

    @Query(
        """
        SELECT name FROM exercises
        WHERE isArchived = 0
          AND (:excludeId IS NULL OR id != :excludeId)
        ORDER BY name ASC
        """
    )
    suspend fun getActiveExerciseNames(excludeId: Long?): List<String>

    @Query("DELETE FROM exercises WHERE isCustom = 1")
    suspend fun deleteAllCustomExercises()

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int
}
