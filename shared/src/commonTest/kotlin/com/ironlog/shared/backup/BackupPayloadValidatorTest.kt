package com.ironlog.shared.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupPayloadValidatorTest {

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 10
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

    @Test
    fun payloadWithMetaPlanSkip_roundTrips() {
        val payload = validPayload().copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(id = 7L, name = "Meta 1", createdAt = 1000L)
            ),
            metaPlanItems = listOf(
                BackupMetaPlanItem(id = 11L, metaPlanId = 7L, trainingPlanId = 5L, orderIndex = 0)
            ),
            metaPlanSkips = listOf(
                BackupMetaPlanSkip(id = 12L, metaPlanId = 7L, trainingPlanId = 5L, skippedAt = 2000L)
            )
        )

        val encoded = Json.encodeToString(BackupPayloadV1.serializer(), payload)
        val decoded = Json.decodeFromString(BackupPayloadV1.serializer(), encoded)

        assertEquals(payload, decoded)
        assertEquals(listOf(payload.metaPlanSkips.single()), decoded.metaPlanSkips)
        assertTrue(
            BackupPayloadValidator.validate(decoded, CURRENT_SCHEMA_VERSION).isValid,
            BackupPayloadValidator.validate(decoded, CURRENT_SCHEMA_VERSION).errors.joinToString()
        )
    }

    @Test
    fun legacyJsonWithoutMetaPlanSkips_decodesToEmptyList() {
        val legacyJson = """
            {
              "formatVersion": 1,
              "schemaVersion": 9,
              "appVersion": "1.0",
              "exportedAtEpochMillis": 1000,
              "exercises": [
                {
                  "id": 1,
                  "name": "Bankdruecken",
                  "primaryMuscleGroup": "BRUST",
                  "secondaryMuscleGroups": "TRIZEPS",
                  "category": "LANGHANTEL",
                  "isCustom": false,
                  "notes": "",
                  "isArchived": false
                }
              ],
              "workoutSessions": [],
              "workoutSets": [],
              "trainingPlans": [],
              "planExercises": [],
              "personalRecords": [],
              "metaTrainingPlans": [],
              "metaPlanItems": []
            }
        """.trimIndent()

        val legacy = Json.decodeFromString<BackupPayloadV1>(legacyJson)

        assertEquals(9, legacy.schemaVersion)
        assertTrue(legacy.metaPlanSkips.isEmpty())
        assertTrue(
            BackupPayloadValidator.validate(legacy, CURRENT_SCHEMA_VERSION).isValid,
            BackupPayloadValidator.validate(legacy, CURRENT_SCHEMA_VERSION).errors.joinToString()
        )
    }

    @Test
    fun metaPlanSkip_withMissingMetaPlan_failsValidation() {
        val payload = validPayload().copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            metaTrainingPlans = emptyList(),
            metaPlanSkips = listOf(
                BackupMetaPlanSkip(id = 12L, metaPlanId = 999L, trainingPlanId = 5L, skippedAt = 2000L)
            )
        )

        val result = BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("missing meta plan", ignoreCase = true) })
    }

    @Test
    fun metaPlanSkip_withMissingTrainingPlan_failsValidation() {
        val payload = validPayload().copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            trainingPlans = emptyList(),
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(id = 7L, name = "Meta 1", createdAt = 1000L)
            ),
            metaPlanItems = emptyList(),
            metaPlanSkips = listOf(
                BackupMetaPlanSkip(id = 12L, metaPlanId = 7L, trainingPlanId = 99L, skippedAt = 2000L)
            )
        )

        val result = BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("missing training plan", ignoreCase = true) })
    }

    @Test
    fun metaPlanSkip_doesNotRequireMatchingCurrentMetaPlanItem() {
        val payload = validPayload().copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            trainingPlans = listOf(
                BackupTrainingPlan(id = 5L, name = "Push", createdAt = 1000L)
            ),
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(id = 7L, name = "Meta 1", createdAt = 1000L)
            ),
            metaPlanItems = emptyList(),
            metaPlanSkips = listOf(
                BackupMetaPlanSkip(id = 12L, metaPlanId = 7L, trainingPlanId = 5L, skippedAt = 2000L)
            )
        )

        val result = BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION)

        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun duplicateMetaPlanSkipIds_failValidation() {
        val payload = validPayload().copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(id = 7L, name = "Meta 1", createdAt = 1000L)
            ),
            metaPlanItems = listOf(
                BackupMetaPlanItem(id = 11L, metaPlanId = 7L, trainingPlanId = 5L, orderIndex = 0)
            ),
            metaPlanSkips = listOf(
                BackupMetaPlanSkip(id = 12L, metaPlanId = 7L, trainingPlanId = 5L, skippedAt = 2000L),
                BackupMetaPlanSkip(id = 12L, metaPlanId = 7L, trainingPlanId = 5L, skippedAt = 3000L)
            )
        )

        val result = BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("duplicate meta plan skip id: 12", ignoreCase = true) })
    }

    private fun validPayload(): BackupPayloadV1 = BackupPayloadV1(
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
            BackupTrainingPlan(id = 5L, name = "Push", createdAt = 1000L)
        ),
        planExercises = emptyList(),
        personalRecords = emptyList(),
        metaTrainingPlans = emptyList(),
        metaPlanItems = emptyList(),
        metaPlanSkips = emptyList()
    )
}
