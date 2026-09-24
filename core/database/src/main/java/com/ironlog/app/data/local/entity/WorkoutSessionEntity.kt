package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.WorkoutSession

@Entity(
    tableName = "workout_sessions",
    indices = [Index("planId"), Index("metaPlanId")]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long, // epoch millis
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val name: String = "",
    val notes: String = "",
    val planId: Long? = null,
    val metaPlanId: Long? = null,
    /**
     * Explicit deload context for this session. `null` for every row written before
     * schema 13 (unknown), `true`/`false` for sessions recorded with a known mode.
     */
    val isDeload: Boolean? = null
) {
    fun toDomain(): WorkoutSession = WorkoutSession(
        id = id,
        startTime = EpochConverter.toLocalDateTime(startTime),
        endTime = endTime?.let { EpochConverter.toLocalDateTime(it) },
        durationSeconds = durationSeconds,
        name = name,
        notes = notes,
        planId = planId,
        metaPlanId = metaPlanId,
        isDeload = isDeload
    )

    companion object {
        fun fromDomain(session: WorkoutSession): WorkoutSessionEntity = WorkoutSessionEntity(
            id = session.id,
            startTime = EpochConverter.toLong(session.startTime),
            endTime = session.endTime?.let { EpochConverter.toLong(it) },
            durationSeconds = session.durationSeconds,
            name = session.name,
            notes = session.notes,
            planId = session.planId,
            metaPlanId = session.metaPlanId,
            isDeload = session.isDeload
        )
    }
}
