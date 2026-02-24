package com.ironlog.app.domain.model

data class MetaTrainingPlan(
    val id: Long = 0,
    val name: String,
    val items: List<MetaTrainingPlanItem> = emptyList()
)

data class MetaTrainingPlanItem(
    val id: Long = 0,
    val trainingPlanId: Long,
    val orderIndex: Int
)
