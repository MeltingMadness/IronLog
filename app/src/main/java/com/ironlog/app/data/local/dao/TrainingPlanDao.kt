package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {

    @Query("SELECT * FROM training_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<TrainingPlanEntity>>

    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): TrainingPlanEntity?

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getExercisesForPlan(planId: Long): List<PlanExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TrainingPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: TrainingPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: PlanExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<PlanExerciseEntity>)

    @Query("DELETE FROM plan_exercises WHERE planId = :planId")
    suspend fun deleteExercisesForPlan(planId: Long)

    @Query("DELETE FROM training_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("SELECT COUNT(*) FROM plan_exercises WHERE planId = :planId")
    suspend fun getExerciseCountForPlan(planId: Long): Int
}
