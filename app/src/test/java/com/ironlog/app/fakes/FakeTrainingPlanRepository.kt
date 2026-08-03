package com.ironlog.app.fakes

import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTrainingPlanRepository : TrainingPlanRepository {

    private val plans = MutableStateFlow<List<TrainingPlan>>(emptyList())
    private var nextId = 1L

    override fun getAllPlans(): Flow<List<TrainingPlan>> = plans

    override suspend fun getPlanById(id: Long): TrainingPlan? {
        return plans.value.find { it.id == id }
    }

    override suspend fun savePlan(plan: TrainingPlan): Long {
        val id = if (plan.id == 0L) nextId++ else plan.id
        val saved = plan.copy(id = id)
        val current = plans.value.toMutableList()
        current.removeAll { it.id == id }
        current.add(saved)
        plans.value = current
        return id
    }

    override suspend fun deletePlan(planId: Long) {
        plans.value = plans.value.filter { it.id != planId }
    }
}
