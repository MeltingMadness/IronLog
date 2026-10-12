package com.thrym.app.domain.model

data class MetaPlanRotationEvent(
    val trainingPlanId: Long,
    val metaPlanId: Long,
    val lastEventAt: Long // epoch millis
)
