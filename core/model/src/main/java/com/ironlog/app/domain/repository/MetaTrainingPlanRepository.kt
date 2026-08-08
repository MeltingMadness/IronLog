package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaPlanRotationEvent
import kotlinx.coroutines.flow.Flow

interface MetaTrainingPlanRepository {
    fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>>
    fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>>
    suspend fun getMetaPlanById(id: Long): MetaTrainingPlan?
    suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long
    suspend fun deleteMetaPlan(metaPlanId: Long)
    suspend fun skipCurrentSubPlan(metaPlanId: Long, expectedTrainingPlanId: Long): Boolean
}
