package com.ironlog.app.data.backup

import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupMetaPlanItem
import com.ironlog.shared.backup.BackupMetaTrainingPlan
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupPayloadValidator
import com.ironlog.shared.backup.BackupPersonalRecord
import com.ironlog.shared.backup.BackupPlanExercise
import com.ironlog.shared.backup.BackupTrainingPlan
import com.ironlog.shared.backup.BackupWorkoutSession
import com.ironlog.shared.backup.BackupWorkoutSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadValidatorTest {

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 8
    }

    @Test
    fun validPayload_passesValidation() {
        val payload = BackupPayloadV1(
            formatVersion = 1,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appVersion = "1.0",
            exportedAtEpochMillis = 1000L,
            exercises = listOf(
                BackupExercise(
                    id = 1,
                    name = "Bankdruecken",
                    primaryMuscleGroup = "BRUST",
                    secondaryMuscleGroups = "TRIZEPS",
                    category = "LANGHANTEL",
                    isCustom = false
                )
            ),
            workoutSessions = listOf(
                BackupWorkoutSession(
                    id = 10,
                    startTime = 1000L,
                    endTime = 2000L,
                    durationSeconds = 1,
                    name = "Push",
                    notes = ""
                )
            ),
            workoutSets = listOf(
                BackupWorkoutSet(
                    id = 20,
                    sessionId = 10,
                    exerciseId = 1,
                    setNumber = 1,
                    reps = 8,
                    weightKg = 80.0,
                    isWarmup = false,
                    completedAt = 1200L
                )
            ),
            trainingPlans = listOf(
                BackupTrainingPlan(
                    id = 5L,
                    name = "Push",
                    createdAt = 1000L
                )
            ),
            planExercises = emptyList(),
            personalRecords = emptyList(),
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(
                    id = 7L,
                    name = "Meta 1",
                    createdAt = 1000L
                )
            ),
            metaPlanItems = listOf(
                BackupMetaPlanItem(
                    id = 11L,
                    metaPlanId = 7L,
                    trainingPlanId = 5L,
                    orderIndex = 0
                )
            )
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertTrue(result.errors.joinToString(), result.isValid)
    }

    @Test
    fun multipleActiveSessions_failValidation() {
        val payload = BackupPayloadV1(
            formatVersion = 1,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appVersion = "1.0",
            exportedAtEpochMillis = 1000L,
            exercises = listOf(
                BackupExercise(
                    id = 1,
                    name = "Kniebeugen",
                    primaryMuscleGroup = "BEINE",
                    secondaryMuscleGroups = "",
                    category = "LANGHANTEL",
                    isCustom = false
                )
            ),
            workoutSessions = listOf(
                BackupWorkoutSession(1, 1000L, null, 0L, "A", ""),
                BackupWorkoutSession(2, 1100L, null, 0L, "B", "")
            ),
            workoutSets = emptyList(),
            trainingPlans = emptyList(),
            planExercises = emptyList(),
            personalRecords = emptyList()
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("active session", ignoreCase = true) })
    }

    @Test
    fun danglingForeignKey_failsValidation() {
        val payload = BackupPayloadV1(
            formatVersion = 1,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appVersion = "1.0",
            exportedAtEpochMillis = 1000L,
            exercises = emptyList(),
            workoutSessions = listOf(
                BackupWorkoutSession(1, 1000L, 1200L, 200L, "A", "")
            ),
            workoutSets = listOf(
                BackupWorkoutSet(1, sessionId = 1, exerciseId = 99, setNumber = 1, reps = 10, weightKg = 20.0, isWarmup = false, completedAt = 1010L)
            ),
            trainingPlans = emptyList(),
            planExercises = emptyList(),
            personalRecords = emptyList()
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("exercise", ignoreCase = true) })
    }

    @Test
    fun metaPlanItem_withMissingTrainingPlan_failsValidation() {
        val payload = BackupPayloadV1(
            formatVersion = 1,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appVersion = "1.0",
            exportedAtEpochMillis = 1000L,
            exercises = emptyList(),
            workoutSessions = emptyList(),
            workoutSets = emptyList(),
            trainingPlans = emptyList(),
            planExercises = emptyList(),
            personalRecords = emptyList(),
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(
                    id = 1L,
                    name = "Meta",
                    createdAt = 1000L
                )
            ),
            metaPlanItems = listOf(
                BackupMetaPlanItem(
                    id = 2L,
                    metaPlanId = 1L,
                    trainingPlanId = 99L,
                    orderIndex = 0
                )
            )
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("missing training plan", ignoreCase = true) })
    }
}
