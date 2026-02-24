package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.MetaTrainingPlan
import kotlinx.coroutines.flow.Flow

interface MetaTrainingPlanRepository {
    fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>>
    suspend fun getMetaPlanById(id: Long): MetaTrainingPlan?
    suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long
    suspend fun deleteMetaPlan(metaPlanId: Long)
}
