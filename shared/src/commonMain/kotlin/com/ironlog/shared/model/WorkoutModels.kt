package com.ironlog.shared.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseCategory {
    LANGHANTEL,
    KURZHANTEL,
    MASCHINE,
    KABEL,
    EIGENGEWICHT;

    companion object {
        fun safeValueOf(name: String, fallback: ExerciseCategory = LANGHANTEL): ExerciseCategory =
            entries.firstOrNull { it.name == name } ?: fallback
    }
}

@Serializable
enum class MuscleGroup {
    BRUST,
    RUECKEN,
    BEINE,
    SCHULTERN,
    BIZEPS,
    TRIZEPS,
    GESAESS,
    CORE,
    UNTERARME,
    WADEN;

    companion object {
        fun safeValueOf(name: String, fallback: MuscleGroup = BRUST): MuscleGroup =
            entries.firstOrNull { it.name == name } ?: fallback
    }
}

@Serializable
data class Exercise(
    val id: Long = 0,
    val name: String,
    val primaryMuscleGroup: MuscleGroup,
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
    val category: ExerciseCategory,
    val isCustom: Boolean = false,
    val notes: String = "",
    val isArchived: Boolean = false
)

@Serializable
data class WorkoutSession(
    val id: Long = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val durationSeconds: Long = 0,
    val name: String = "",
    val notes: String = "",
    val planId: Long? = null,
    val metaPlanId: Long? = null
)

@Serializable
data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val isWarmup: Boolean = false,
    val completedAt: LocalDateTime,
    val rpe: Double? = null
)

@Serializable
data class PlanExercise(
    val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String = "",
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0
)

@Serializable
data class TrainingPlan(
    val id: Long = 0,
    val name: String,
    val exercises: List<PlanExercise> = emptyList()
)

@Serializable
data class MetaTrainingPlanItem(
    val id: Long = 0,
    val trainingPlanId: Long,
    val orderIndex: Int
)

@Serializable
data class MetaTrainingPlan(
    val id: Long = 0,
    val name: String,
    val items: List<MetaTrainingPlanItem> = emptyList()
)

@Serializable
data class CompletedWorkoutSummary(
    val session: WorkoutSession,
    val exerciseCount: Int,
    val setCount: Int,
    val totalVolume: Double
)

@Serializable
data class PreviousExerciseSession(
    val sessionId: Long,
    val sessionStart: LocalDateTime,
    val sets: List<WorkoutSet>,
    val lastWorkSetWeightKg: Double?
)

@Serializable
data class LastPlanSession(
    val planId: Long,
    val lastStartTime: Long
)

@Serializable
data class LastMetaPlanSession(
    val planId: Long,
    val metaPlanId: Long,
    val lastStartTime: Long
)

@Serializable
enum class RecordType {
    MAX_WEIGHT,
    MAX_REPS,
    MAX_VOLUME,
    MAX_E1RM;

    companion object {
        fun safeValueOf(name: String, fallback: RecordType = MAX_WEIGHT): RecordType =
            entries.firstOrNull { it.name == name } ?: fallback
    }
}

@Serializable
data class PersonalRecord(
    val id: Long = 0,
    val exerciseId: Long,
    val type: RecordType,
    val value: Double,
    val achievedAt: LocalDateTime
)
