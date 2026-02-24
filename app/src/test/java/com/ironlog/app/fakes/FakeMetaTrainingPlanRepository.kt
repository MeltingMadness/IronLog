package com.ironlog.app.fakes

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMetaTrainingPlanRepository : MetaTrainingPlanRepository {

    private val metaPlans = MutableStateFlow<List<MetaTrainingPlan>>(emptyList())
    private var nextId = 1L

    override fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>> = metaPlans

    override suspend fun getMetaPlanById(id: Long): MetaTrainingPlan? =
        metaPlans.value.find { it.id == id }

    override suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long {
        val id = if (plan.id == 0L) nextId++ else plan.id
        val saved = plan.copy(id = id)
        metaPlans.value = metaPlans.value
            .filterNot { it.id == id } + saved
        return id
    }

    override suspend fun deleteMetaPlan(metaPlanId: Long) {
        metaPlans.value = metaPlans.value.filterNot { it.id == metaPlanId }
    }
}
