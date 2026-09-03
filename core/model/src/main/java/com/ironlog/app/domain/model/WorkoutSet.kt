package com.ironlog.app.domain.model

import java.time.LocalDateTime

data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val setType: SetType = SetType.NORMAL,
    val completedAt: LocalDateTime = LocalDateTime.now(),
    val rpe: Double? = null,
    val planTargetSnapshotId: Long? = null
) {
    /** Convenience view for legacy call sites: only [SetType.WARMUP] is a warmup. */
    val isWarmup: Boolean
        get() = setType == SetType.WARMUP
}
