package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

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
        val nowEpochMillis = EpochConverter.toLong(LocalDateTime.now())

        val entity = if (plan.id == 0L) {
            TrainingPlanEntity(
                id = 0L,
                name = plan.name,
                createdAt = nowEpochMillis
            )
        } else {
            val existing = trainingPlanDao.getPlanById(plan.id)
                ?: throw IllegalStateException("Plan ${plan.id} existiert nicht")
            TrainingPlanEntity(
                id = plan.id,
                name = plan.name,
                createdAt = existing.createdAt
            )
        }

        val exerciseEntities = plan.exercises.map { exercise ->
            PlanExerciseEntity.fromDomain(
                planId = 0L,
                exercise = exercise.copy(id = 0L)
            )
        }

        return trainingPlanDao.replacePlanAndExercises(entity, exerciseEntities)
    }

    override suspend fun deletePlan(planId: Long) {
        trainingPlanDao.deletePlan(planId)
        // plan_exercises cascade-deleted via FK
    }
}
