package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.WorkoutSession

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long, // epoch millis
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val name: String = "",
    val notes: String = ""
) {
    fun toDomain(): WorkoutSession = WorkoutSession(
        id = id,
        startTime = EpochConverter.toLocalDateTime(startTime),
        endTime = endTime?.let { EpochConverter.toLocalDateTime(it) },
        durationSeconds = durationSeconds,
        name = name,
        notes = notes
    )

    companion object {
        fun fromDomain(session: WorkoutSession): WorkoutSessionEntity = WorkoutSessionEntity(
            id = session.id,
            startTime = EpochConverter.toLong(session.startTime),
            endTime = session.endTime?.let { EpochConverter.toLong(it) },
            durationSeconds = session.durationSeconds,
            name = session.name,
            notes = session.notes
        )
    }
}
