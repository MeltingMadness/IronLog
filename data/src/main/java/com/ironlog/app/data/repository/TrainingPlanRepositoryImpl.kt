package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.domain.model.PlanExercise
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
                val exercises = normalizePlanExercises(
                    trainingPlanDao.getExercisesForPlan(planEntity.id).map { it.toDomain() }
                ).map { normalized ->
                    PlanExerciseEntity.fromDomain(planId = planEntity.id, exercise = normalized)
                }
                planEntity.toDomain(exercises)
            }
        }
    }

    override suspend fun getPlanById(id: Long): TrainingPlan? {
        val entity = trainingPlanDao.getPlanById(id) ?: return null
        val exercises = normalizePlanExercises(
            trainingPlanDao.getExercisesForPlan(id).map { it.toDomain() }
        ).map { normalized ->
            PlanExerciseEntity.fromDomain(planId = id, exercise = normalized)
        }
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

        val normalizedExercises = normalizePlanExercises(plan.exercises)
        val exerciseEntities = normalizedExercises.map { exercise ->
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

    private fun normalizePlanExercises(exercises: List<PlanExercise>): List<PlanExercise> {
        if (exercises.isEmpty()) return emptyList()

        val reindexed = exercises
            .mapIndexed { index, exercise -> exercise.copy(orderIndex = index) }
            .toMutableList()

        // Collapse singleton runs and split non-contiguous reused group IDs into separate runs.
        var cursor = 0
        while (cursor < reindexed.size) {
            val runGroupId = reindexed[cursor].supersetGroupId
            if (runGroupId == null) {
                cursor++
                continue
            }
            var endExclusive = cursor + 1
            while (
                endExclusive < reindexed.size &&
                reindexed[endExclusive].supersetGroupId == runGroupId
            ) {
                endExclusive++
            }
            if (endExclusive - cursor < 2) {
                for (index in cursor until endExclusive) {
                    reindexed[index] = reindexed[index].copy(supersetGroupId = null)
                }
            }
            cursor = endExclusive
        }

        // Reassign visible runs to compact IDs (S1..Sn).
        var nextGroupId = 1
        cursor = 0
        while (cursor < reindexed.size) {
            val runGroupId = reindexed[cursor].supersetGroupId
            if (runGroupId == null) {
                cursor++
                continue
            }
            var endExclusive = cursor + 1
            while (
                endExclusive < reindexed.size &&
                reindexed[endExclusive].supersetGroupId == runGroupId
            ) {
                endExclusive++
            }
            val normalizedGroupId = nextGroupId++
            for (index in cursor until endExclusive) {
                reindexed[index] = reindexed[index].copy(supersetGroupId = normalizedGroupId)
            }
            cursor = endExclusive
        }

        return reindexed
    }
}
