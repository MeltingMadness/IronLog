package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.WorkoutSet

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("sessionId"),
        Index("exerciseId")
    ]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val isWarmup: Boolean = false,
    val completedAt: Long, // epoch millis
    val rpe: Double? = null
) {
    fun toDomain(): WorkoutSet = WorkoutSet(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        isWarmup = isWarmup,
        completedAt = EpochConverter.toLocalDateTime(completedAt),
        rpe = rpe
    )

    companion object {
        fun fromDomain(set: WorkoutSet): WorkoutSetEntity = WorkoutSetEntity(
            id = set.id,
            sessionId = set.sessionId,
            exerciseId = set.exerciseId,
            setNumber = set.setNumber,
            reps = set.reps,
            weightKg = set.weightKg,
            isWarmup = set.isWarmup,
            completedAt = EpochConverter.toLong(set.completedAt),
            rpe = set.rpe
        )
    }
}
