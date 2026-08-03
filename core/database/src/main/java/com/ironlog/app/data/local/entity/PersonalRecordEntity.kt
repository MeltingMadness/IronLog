package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.RecordType

@Entity(
    tableName = "personal_records",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val type: String,
    val value: Double,
    val achievedAt: Long // epoch millis
) {
    fun toDomain(): PersonalRecord = PersonalRecord(
        id = id,
        exerciseId = exerciseId,
        type = RecordType.safeValueOf(type),
        value = value,
        achievedAt = EpochConverter.toLocalDateTime(achievedAt)
    )

    companion object {
        fun fromDomain(record: PersonalRecord): PersonalRecordEntity = PersonalRecordEntity(
            id = record.id,
            exerciseId = record.exerciseId,
            type = record.type.name,
            value = record.value,
            achievedAt = EpochConverter.toLong(record.achievedAt)
        )
    }
}
