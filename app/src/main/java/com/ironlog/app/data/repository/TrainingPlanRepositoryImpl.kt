package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrainingPlanRepositoryImpl(
    private val trainingPlanDao: TrainingPlanDao
) : TrainingPlanRepository {

    override fun getAllPlans(): Flow<List<TrainingPlan>> {
        return trainingPlanDao.getAllPlans().map { plans ->
            plans.map { planEntity ->
                val exercises = trainingPlanDao.getExercisesForPlan(planEntity.id)
                planEntity.toDomain(exercises)
            }
        }
    }

    override suspend fun getPlanById(id: Long): TrainingPlan? {
        val entity = trainingPlanDao.getPlanById(id) ?: return null
        val exercises = trainingPlanDao.getExercisesForPlan(id)
        return entity.toDomain(exercises)
    }

    override suspend fun savePlan(plan: TrainingPlan): Long {
        val entity = TrainingPlanEntity.fromDomain(plan)
        val planId = if (plan.id == 0L) {
            trainingPlanDao.insertPlan(entity)
        } else {
            trainingPlanDao.updatePlan(entity.copy(id = plan.id))
            plan.id
        }

        // Replace all exercises
        trainingPlanDao.deleteExercisesForPlan(planId)
        val exerciseEntities = plan.exercises.mapIndexed { index, exercise ->
            PlanExerciseEntity.fromDomain(
                planId = planId,
                exercise = exercise.copy(id = 0, orderIndex = index)
            )
        }
        trainingPlanDao.insertExercises(exerciseEntities)

        return planId
    }

    override suspend fun deletePlan(planId: Long) {
        trainingPlanDao.deletePlan(planId)
        // plan_exercises cascade-deleted via FK
    }
}
