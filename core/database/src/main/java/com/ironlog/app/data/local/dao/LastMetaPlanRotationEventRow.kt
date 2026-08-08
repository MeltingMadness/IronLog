package com.ironlog.app.data.local.dao

data class LastMetaPlanRotationEventRow(
    val trainingPlanId: Long,
    val metaPlanId: Long,
    val lastEventAt: Long
)
