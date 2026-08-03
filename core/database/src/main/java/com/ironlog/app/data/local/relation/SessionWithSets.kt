package com.ironlog.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity

data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val sets: List<WorkoutSetEntity>
)
