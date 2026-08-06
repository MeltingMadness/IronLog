package com.ironlog.shared.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupPayloadValidatorTest {

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 9
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
                    id = 1L,
                    name = "Bankdruecken",
                    primaryMuscleGroup = "BRUST",
                    secondaryMuscleGroups = "TRIZEPS",
                    category = "LANGHANTEL",
                    isCustom = false
                )
            ),
            workoutSessions = listOf(
                BackupWorkoutSession(
                    id = 10L,
                    startTime = 1000L,
                    endTime = 2000L,
                    durationSeconds = 1L,
                    name = "Push",
                    notes = ""
                )
            ),
            workoutSets = listOf(
                BackupWorkoutSet(
                    id = 20L,
                    sessionId = 10L,
                    exerciseId = 1L,
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

        assertTrue(result.isValid, result.errors.joinToString())
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
                    id = 1L,
                    name = "Kniebeugen",
                    primaryMuscleGroup = "BEINE",
                    secondaryMuscleGroups = "",
                    category = "LANGHANTEL",
                    isCustom = false
                )
            ),
            workoutSessions = listOf(
                BackupWorkoutSession(1L, 1000L, null, 0L, "A", ""),
                BackupWorkoutSession(2L, 1100L, null, 0L, "B", "")
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
    fun emptyExercises_failsValidation() {
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
            personalRecords = emptyList()
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("no exercises", ignoreCase = true) })
    }

    @Test
    fun legacySchema8_payloadRemainsImportable() {
        val payload = BackupPayloadV1(
            formatVersion = 1,
            schemaVersion = 8,
            appVersion = "1.0",
            exportedAtEpochMillis = 1000L,
            exercises = listOf(
                BackupExercise(
                    id = 1L,
                    name = "Bankdruecken",
                    primaryMuscleGroup = "BRUST",
                    secondaryMuscleGroups = "TRIZEPS",
                    category = "LANGHANTEL",
                    isCustom = false
                )
            ),
            workoutSessions = emptyList(),
            workoutSets = emptyList(),
            trainingPlans = emptyList(),
            planExercises = emptyList(),
            personalRecords = emptyList()
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun duplicateExerciseIds_failValidation() {
        val payload = BackupPayloadV1(
            formatVersion = 1,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appVersion = "1.0",
            exportedAtEpochMillis = 1000L,
            exercises = listOf(
                BackupExercise(
                    id = 1L,
                    name = "Bankdruecken",
                    primaryMuscleGroup = "BRUST",
                    secondaryMuscleGroups = "TRIZEPS",
                    category = "LANGHANTEL",
                    isCustom = false
                ),
                BackupExercise(
                    id = 1L,
                    name = "Bankdruecken 2",
                    primaryMuscleGroup = "BRUST",
                    secondaryMuscleGroups = "",
                    category = "LANGHANTEL",
                    isCustom = true
                )
            ),
            workoutSessions = emptyList(),
            workoutSets = emptyList(),
            trainingPlans = emptyList(),
            planExercises = emptyList(),
            personalRecords = emptyList()
        )

        val result = BackupPayloadValidator.validate(payload, currentSchemaVersion = CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("duplicate exercise id: 1", ignoreCase = true) })
    }
}
