package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.WorkoutSession
import java.time.LocalDateTime

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
        startTime = Converters.longToDateTime(startTime),
        endTime = endTime?.let { Converters.longToDateTime(it) },
        durationSeconds = durationSeconds,
        name = name,
        notes = notes
    )

    companion object {
        fun fromDomain(session: WorkoutSession): WorkoutSessionEntity = WorkoutSessionEntity(
            id = session.id,
            startTime = Converters.dateTimeToLong(session.startTime),
            endTime = session.endTime?.let { Converters.dateTimeToLong(it) },
            durationSeconds = session.durationSeconds,
            name = session.name,
            notes = session.notes
        )
    }
}

internal object Converters {
    fun longToDateTime(epoch: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(epoch / 1000, ((epoch % 1000) * 1_000_000).toInt(), java.time.ZoneOffset.UTC)

    fun dateTimeToLong(dt: LocalDateTime): Long =
        dt.toEpochSecond(java.time.ZoneOffset.UTC) * 1000
}
