package com.ironlog.app.domain.model

data class LastMetaPlanSession(
    val planId: Long,
    val metaPlanId: Long,
    val lastStartTime: Long  // epoch millis
)