package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.TrainingPlan
import kotlinx.coroutines.flow.Flow

interface TrainingPlanRepository {
    fun getAllPlans(): Flow<List<TrainingPlan>>
    suspend fun getPlanById(id: Long): TrainingPlan?
    suspend fun savePlan(plan: TrainingPlan): Long
    suspend fun deletePlan(planId: Long)
}
