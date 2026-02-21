package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.TrainingPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class TrainingPlanRepositoryImplTest {

    @Test
    fun `savePlan preserves createdAt when editing existing plan`() = runTest {
        val dao = FakeTrainingPlanDao().apply {
            plans[1L] = TrainingPlanEntity(id = 1L, name = "Push", createdAt = 123456789L)
            exercisesByPlan[1L] = mutableListOf(
                PlanExerciseEntity(id = 11L, planId = 1L, exerciseId = 101L, orderIndex = 0)
            )
        }
        val repository = TrainingPlanRepositoryImpl(dao)

        val updatedPlan = TrainingPlan(
            id = 1L,
            name = "Push A",
            exercises = listOf(
                PlanExercise(exerciseId = 101L, orderIndex = 0, targetSets = 4, targetReps = 8)
            )
        )

        repository.savePlan(updatedPlan)

        assertEquals(123456789L, dao.plans.getValue(1L).createdAt)
        assertEquals("Push A", dao.plans.getValue(1L).name)
    }

    @Test
    fun `savePlan is atomic and keeps old exercises when insertion fails`() = runTest {
        val dao = FakeTrainingPlanDao().apply {
            plans[1L] = TrainingPlanEntity(id = 1L, name = "Legs", createdAt = 999L)
            exercisesByPlan[1L] = mutableListOf(
                PlanExerciseEntity(id = 21L, planId = 1L, exerciseId = 201L, orderIndex = 0)
            )
            failOnInsertExercises = true
        }
        val repository = TrainingPlanRepositoryImpl(dao)

        val editedPlan = TrainingPlan(
            id = 1L,
            name = "Legs Heavy",
            exercises = listOf(
                PlanExercise(exerciseId = 202L, orderIndex = 0, targetSets = 5, targetReps = 5)
            )
        )

        assertFailsWith<IllegalStateException> {
            repository.savePlan(editedPlan)
        }

        val existingExercises = dao.exercisesByPlan.getValue(1L)
        assertEquals(1, existingExercises.size)
        assertEquals(201L, existingExercises.first().exerciseId)
        assertTrue(dao.plans.getValue(1L).name == "Legs")
    }

    private class FakeTrainingPlanDao : TrainingPlanDao {
        val plans = linkedMapOf<Long, TrainingPlanEntity>()
        val exercisesByPlan = linkedMapOf<Long, MutableList<PlanExerciseEntity>>()
        private val plansFlow = MutableStateFlow<List<TrainingPlanEntity>>(emptyList())
        var nextPlanId = 100L
        var nextExerciseId = 1000L
        var failOnInsertExercises = false

        private fun publish() {
            plansFlow.value = plans.values.sortedByDescending { it.createdAt }
        }

        override fun getAllPlans(): Flow<List<TrainingPlanEntity>> {
            publish()
            return plansFlow
        }

        override suspend fun getAllPlansList(): List<TrainingPlanEntity> =
            plans.values.sortedBy { it.id }

        override suspend fun getAllPlanExercisesList(): List<PlanExerciseEntity> =
            exercisesByPlan.values.flatten().sortedBy { it.id }

        override suspend fun getPlanById(id: Long): TrainingPlanEntity? = plans[id]

        override suspend fun getExercisesForPlan(planId: Long): List<PlanExerciseEntity> =
            exercisesByPlan[planId]?.sortedBy { it.orderIndex } ?: emptyList()

        override suspend fun insertPlan(plan: TrainingPlanEntity): Long {
            val id = nextPlanId++
            plans[id] = plan.copy(id = id)
            publish()
            return id
        }

        override suspend fun replaceAllPlans(plans: List<TrainingPlanEntity>) {
            this.plans.clear()
            plans.forEach { plan ->
                this.plans[plan.id] = plan
            }
            publish()
        }

        override suspend fun updatePlan(plan: TrainingPlanEntity) {
            plans[plan.id] = plan
            publish()
        }

        override suspend fun insertExercise(exercise: PlanExerciseEntity): Long {
            val id = nextExerciseId++
            val list = exercisesByPlan.getOrPut(exercise.planId) { mutableListOf() }
            list.add(exercise.copy(id = id))
            return id
        }

        override suspend fun insertExercises(exercises: List<PlanExerciseEntity>) {
            if (failOnInsertExercises) {
                throw IllegalStateException("insert failed")
            }
            exercises.forEach { insertExercise(it) }
        }

        override suspend fun replaceAllExercises(exercises: List<PlanExerciseEntity>) {
            exercisesByPlan.clear()
            exercises.groupBy { it.planId }.forEach { (planId, list) ->
                exercisesByPlan[planId] = list.toMutableList()
            }
        }

        override suspend fun deleteExercisesForPlan(planId: Long) {
            exercisesByPlan[planId] = mutableListOf()
        }

        override suspend fun deleteAllPlanExercises() {
            exercisesByPlan.clear()
        }

        override suspend fun deletePlan(planId: Long) {
            plans.remove(planId)
            exercisesByPlan.remove(planId)
            publish()
        }

        override suspend fun deleteAllPlans() {
            plans.clear()
            publish()
        }

        override suspend fun getExerciseCountForPlan(planId: Long): Int =
            exercisesByPlan[planId]?.size ?: 0

        override suspend fun replacePlanAndExercises(
            plan: TrainingPlanEntity,
            exercises: List<PlanExerciseEntity>
        ): Long {
            val plansSnapshot = linkedMapOf<Long, TrainingPlanEntity>().apply { putAll(plans) }
            val exercisesSnapshot = linkedMapOf<Long, MutableList<PlanExerciseEntity>>().apply {
                exercisesByPlan.forEach { (existingPlanId, list) ->
                    put(existingPlanId, list.toMutableList())
                }
            }

            return try {
                super.replacePlanAndExercises(plan, exercises)
            } catch (e: Exception) {
                plans.clear()
                plans.putAll(plansSnapshot)
                exercisesByPlan.clear()
                exercisesSnapshot.forEach { (existingPlanId, list) ->
                    exercisesByPlan[existingPlanId] = list.toMutableList()
                }
                publish()
                throw e
            }
        }
    }
}
