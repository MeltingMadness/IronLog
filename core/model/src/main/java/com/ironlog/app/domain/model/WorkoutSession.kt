package com.ironlog.app.domain.model

import java.time.LocalDateTime

data class WorkoutSession(
    val id: Long = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val durationSeconds: Long = 0,
    val name: String = "",
    val notes: String = ""
)
