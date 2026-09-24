package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {

    @Query("SELECT * FROM workout_plan_targets WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun targetsForApply(sessionId: Long): List<com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY setNumber, id")
    suspend fun setsForApply(sessionId: Long): List<com.ironlog.app.data.local.entity.WorkoutSetEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun sessionForApply(sessionId: Long): com.ironlog.app.data.local.entity.WorkoutSessionEntity?

    @Update
    suspend fun updateExercise(exercise: PlanExerciseEntity)

    @Transaction
    suspend fun applyPerformedSetTargets(sessionId: Long) {
        val session = sessionForApply(sessionId) ?: error("Training nicht gefunden")
        check(session.endTime != null) { "Bitte Training zuerst beenden" }
        val sets = setsForApply(sessionId).filter { it.reps > 0 }
        val targets = targetsForApply(sessionId)
        check(targets.isNotEmpty()) { "Kein Plan für dieses Training vorhanden" }
        val updates = targets.mapNotNull { target ->
            val recorded = sets.filter { it.planTargetSnapshotId == target.id }
            if (recorded.isEmpty()) return@mapNotNull null
            val plan = getPlanExerciseAt(target.planId, target.exerciseId, target.orderIndex) ?: error("Plan wurde geändert. Bitte im Editor prüfen.")
            check(plan.targetSets == target.target.sets && plan.targetReps == target.target.reps &&
                plan.targetWeightKg == target.target.weightKg && plan.setTargetsJson == target.setTargetsJson &&
                plan.progression == target.progression && plan.supersetGroupId == target.supersetGroupId) {
                "Plan wurde seit dem Training geändert. Bitte im Editor prüfen."
            }
            val original = com.ironlog.shared.plans.PlannedSets.decode(target.setTargetsJson).ifEmpty {
                List(target.target.sets) { com.ironlog.shared.plans.PlannedSet(reps = target.target.reps, weightKg = target.target.weightKg) }
            }
            val merged = com.ironlog.shared.plans.PlannedSets.mergePerformed(original, recorded.map {
                com.ironlog.shared.plans.PlannedSet(it.setType, it.reps, it.weightKg)
            })
            if (merged == original) return@mapNotNull null
            val work = merged.filter { it.kind != "WARMUP" }
            plan.copy(setTargetsJson = com.ironlog.shared.plans.PlannedSets.encode(merged), targetSets = work.size,
                targetReps = work.first().reps, targetWeightKg = work.first().weightKg,
                progression = com.ironlog.app.data.local.entity.ProgressionConfigColumns())
        }
        updates.forEach { updateExercise(it) }
    }

    @Query("SELECT * FROM training_plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<TrainingPlanEntity>>

    @Query("SELECT * FROM training_plans ORDER BY id ASC")
    suspend fun getAllPlansList(): List<TrainingPlanEntity>

    @Query("SELECT * FROM plan_exercises ORDER BY id ASC")
    suspend fun getAllPlanExercisesList(): List<PlanExerciseEntity>

    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): TrainingPlanEntity?

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getExercisesForPlan(planId: Long): List<PlanExerciseEntity>

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId AND exerciseId = :exerciseId AND orderIndex = :orderIndex LIMIT 1")
    suspend fun getPlanExerciseAt(
        planId: Long,
        exerciseId: Long,
        orderIndex: Int
    ): PlanExerciseEntity?

    @Query("UPDATE plan_exercises SET targetSets = :sets, targetReps = :reps, targetWeightKg = :weightKg WHERE id = :id")
    suspend fun updatePlanExerciseTargetsById(
        id: Long,
        sets: Int,
        reps: Int,
        weightKg: Double
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TrainingPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllPlans(plans: List<TrainingPlanEntity>)

    @Update
    suspend fun updatePlan(plan: TrainingPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: PlanExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<PlanExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllExercises(exercises: List<PlanExerciseEntity>)

    @Query("DELETE FROM plan_exercises WHERE planId = :planId")
    suspend fun deleteExercisesForPlan(planId: Long)

    @Query("DELETE FROM plan_exercises")
    suspend fun deleteAllPlanExercises()

    @Query("DELETE FROM training_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("UPDATE workout_sessions SET planId = NULL WHERE planId = :planId")
    suspend fun detachSessionsFromPlan(planId: Long)

    /**
     * Atomically detaches referencing workout sessions before deleting the plan.
     * Sessions keep their own data and simply lose the plan reference, which keeps
     * the database and backups valid even for an active session.
     */
    @Transaction
    suspend fun deletePlanAndDetachSessions(planId: Long) {
        detachSessionsFromPlan(planId)
        deletePlan(planId)
    }

    @Query("DELETE FROM training_plans")
    suspend fun deleteAllPlans()

    @Query("SELECT COUNT(*) FROM plan_exercises WHERE planId = :planId")
    suspend fun getExerciseCountForPlan(planId: Long): Int

    @Transaction
    suspend fun replacePlanAndExercises(
        plan: TrainingPlanEntity,
        exercises: List<PlanExerciseEntity>
    ): Long {
        val planId = if (plan.id == 0L) {
            insertPlan(plan)
        } else {
            updatePlan(plan)
            plan.id
        }

        deleteExercisesForPlan(planId)
        val normalized = exercises.mapIndexed { index, exercise ->
            exercise.copy(
                id = 0L,
                planId = planId,
                orderIndex = index
            )
        }
        insertExercises(normalized)

        return planId
    }
}
