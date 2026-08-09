package com.ironlog.shared.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupPayloadValidatorTest {

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 11
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
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
    fun `legacy schema ten json defaults progression to manual and empty evidence`() {
        val legacy = json.decodeFromString<BackupPayloadV1>(legacySchemaTenJson)

        assertEquals(10, legacy.schemaVersion)
        assertEquals("MANUAL", legacy.planExercises.single().progression.scheme)
        assertNull(legacy.workoutSets.single().planTargetSnapshotId)
        assertTrue(legacy.workoutPlanTargets.isEmpty())
        assertTrue(legacy.progressionSuggestions.isEmpty())
        assertTrue(
            BackupPayloadValidator.validate(legacy, CURRENT_SCHEMA_VERSION).isValid,
            BackupPayloadValidator.validate(legacy, CURRENT_SCHEMA_VERSION).errors.joinToString()
        )
    }

    @Test
    fun `schema eleven progression payload survives json round trip`() {
        val source = validPayloadWithProgression()

        val decoded = json.decodeFromString<BackupPayloadV1>(json.encodeToString(source))

        assertEquals(source.planExercises.single().progression, decoded.planExercises.single().progression)
        assertEquals(source.workoutPlanTargets, decoded.workoutPlanTargets)
        assertEquals(source.progressionSuggestions, decoded.progressionSuggestions)
        assertEquals(
            source.workoutSets.single().planTargetSnapshotId,
            decoded.workoutSets.single().planTargetSnapshotId
        )
    }

    @Test
    fun `validator accepts every valid progression scheme`() {
        val schemes = listOf(
            BackupProgressionConfig(),
            linearConfig(),
            linearConfig().copy(
                scheme = "DOUBLE",
                minReps = 6,
                maxReps = 10
            ),
            linearConfig().copy(
                scheme = "TOTAL_REPS",
                targetTotalReps = 24L
            ),
            linearConfig().copy(
                scheme = "RPE_RIR",
                targetRpe = 8.0,
                rpeTolerance = 0.5
            )
        )

        schemes.forEach { config ->
            val payload = validPayload().copy(
                planExercises = listOf(
                    BackupPlanExercise(
                        id = 15L,
                        planId = 5L,
                        exerciseId = 1L,
                        orderIndex = 0,
                        targetSets = 3,
                        targetReps = 8,
                        targetWeightKg = 80.0,
                        progression = config
                    )
                )
            )

            val result = BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION)

            assertTrue(result.isValid, "${config.scheme}: ${result.errors.joinToString()}")
        }
    }

    @Test
    fun `validator rejects dangling or cross position progression evidence`() {
        val valid = validPayloadWithProgression()
        val brokenSetReference = valid.copy(
            workoutSets = valid.workoutSets.map { it.copy(planTargetSnapshotId = 999L) }
        )
        val brokenSuggestionEvidence = valid.copy(
            progressionSuggestions = valid.progressionSuggestions.map {
                it.copy(countedSetIds = listOf(999L))
            }
        )
        val duplicatedRevision = valid.copy(
            progressionSuggestions = valid.progressionSuggestions +
                valid.progressionSuggestions.single().copy(id = 99L)
        )
        val secondSession = valid.workoutSessions.single().copy(id = 11L)
        val crossSessionSet = valid.copy(
            workoutSessions = valid.workoutSessions + secondSession,
            workoutSets = valid.workoutSets.map { it.copy(sessionId = secondSession.id) }
        )
        val secondExercise = valid.exercises.single().copy(id = 2L, name = "Schraegbankdruecken")
        val crossExerciseSet = valid.copy(
            exercises = valid.exercises + secondExercise,
            workoutSets = valid.workoutSets.map { it.copy(exerciseId = secondExercise.id) }
        )

        listOf(
            brokenSetReference,
            brokenSuggestionEvidence,
            duplicatedRevision,
            crossSessionSet,
            crossExerciseSet
        ).forEach { broken ->
            assertFalse(BackupPayloadValidator.validate(broken, CURRENT_SCHEMA_VERSION).isValid)
        }
    }

    @Test
    fun `validator rejects warmup sets as progression evidence`() {
        val valid = validPayloadWithProgression()
        val warmupEvidence = valid.copy(
            workoutSets = valid.workoutSets.map { it.copy(isWarmup = true) }
        )

        assertFalse(BackupPayloadValidator.validate(warmupEvidence, CURRENT_SCHEMA_VERSION).isValid)
    }

    @Test
    fun `validator rejects invalid target and suggestion identity`() {
        val valid = validPayloadWithProgression()
        val target = valid.workoutPlanTargets.single()
        val suggestion = valid.progressionSuggestions.single()
        val duplicateTargetPosition = valid.copy(
            workoutPlanTargets = valid.workoutPlanTargets + target.copy(id = 31L)
        )
        val targetForWrongPlan = valid.copy(
            workoutPlanTargets = listOf(target.copy(planId = 6L))
        )
        val targetWithNegativeOrder = valid.copy(
            workoutPlanTargets = listOf(target.copy(orderIndex = -1))
        )
        val nonPositiveIds = listOf(
            valid.copy(workoutPlanTargets = listOf(target.copy(id = 0L))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(id = 0L)))
        )
        val mismatchedSuggestionIdentity = listOf(
            valid.copy(progressionSuggestions = listOf(suggestion.copy(sourceSessionId = 11L))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(planId = 6L))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(exerciseId = 2L))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(orderIndex = 1))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(supersetGroupId = 7))),
            valid.copy(
                progressionSuggestions = listOf(
                    suggestion.copy(sourceTarget = suggestion.sourceTarget.copy(reps = 7))
                )
            ),
            valid.copy(
                progressionSuggestions = listOf(
                    suggestion.copy(sourceProgression = BackupProgressionConfig())
                )
            )
        )

        (listOf(duplicateTargetPosition, targetForWrongPlan, targetWithNegativeOrder) +
            nonPositiveIds + mismatchedSuggestionIdentity).forEach { broken ->
            assertFalse(BackupPayloadValidator.validate(broken, CURRENT_SCHEMA_VERSION).isValid)
        }
    }

    @Test
    fun `validator allows active target snapshots but rejects active session outcomes`() {
        val valid = validPayloadWithProgression()
        val active = valid.copy(
            workoutSessions = valid.workoutSessions.map { it.copy(endTime = null) },
            progressionSuggestions = emptyList()
        )
        val activeWithOutcome = active.copy(
            progressionSuggestions = valid.progressionSuggestions
        )

        assertTrue(
            BackupPayloadValidator.validate(active, CURRENT_SCHEMA_VERSION).isValid,
            BackupPayloadValidator.validate(active, CURRENT_SCHEMA_VERSION).errors.joinToString()
        )
        assertFalse(BackupPayloadValidator.validate(activeWithOutcome, CURRENT_SCHEMA_VERSION).isValid)
    }

    @Test
    fun `validator rejects unknown non finite or inconsistent progression metadata`() {
        val valid = validPayloadWithProgression()
        val suggestion = valid.progressionSuggestions.single()
        val unknownReason = valid.copy(
            progressionSuggestions = listOf(suggestion.copy(reasonCode = "NOT_A_REASON"))
        )
        val nonFiniteArgument = valid.copy(
            progressionSuggestions = listOf(
                suggestion.copy(reasonArguments = mapOf("actualReps" to Double.NaN))
            )
        )
        val unknownArgument = valid.copy(
            progressionSuggestions = listOf(
                suggestion.copy(reasonArguments = mapOf("unknown" to 8.0))
            )
        )
        val informationalPending = valid.copy(
            progressionSuggestions = listOf(
                suggestion.copy(
                    outcomeType = "KEEP_TARGET",
                    reasonCode = "REPEAT_TARGET",
                    suggestedTarget = null,
                    status = "PENDING"
                )
            )
        )
        val incompleteEvidence = valid.copy(
            progressionSuggestions = listOf(
                suggestion.copy(countedSetIds = suggestion.countedSetIds.dropLast(1))
            )
        )
        val unknownEnums = listOf(
            valid.copy(progressionSuggestions = listOf(suggestion.copy(outcomeType = "NOT_APPLICABLE"))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(reasonCode = "MANUAL_SCHEME"))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(streakEffect = "UNKNOWN"))),
            valid.copy(progressionSuggestions = listOf(suggestion.copy(status = "UNKNOWN")))
        )

        (listOf(
            unknownReason,
            nonFiniteArgument,
            unknownArgument,
            informationalPending,
            incompleteEvidence
        ) + unknownEnums).forEach { broken ->
            assertFalse(BackupPayloadValidator.validate(broken, CURRENT_SCHEMA_VERSION).isValid)
        }
    }

    @Test
    fun `validator rejects malformed progression configs and targets`() {
        val valid = validPayloadWithProgression()
        val target = valid.workoutPlanTargets.single()
        val invalidConfigs = listOf(
            linearConfig().copy(scheme = "UNKNOWN"),
            BackupProgressionConfig(incrementValue = 2.5),
            linearConfig().copy(incrementKg = null),
            linearConfig().copy(scheme = "DOUBLE", minReps = 0, maxReps = 8),
            linearConfig().copy(scheme = "DOUBLE", minReps = 9, maxReps = 8),
            linearConfig().copy(scheme = "TOTAL_REPS", targetTotalReps = 0L),
            linearConfig().copy(scheme = "RPE_RIR", targetRpe = 11.0, rpeTolerance = 0.5),
            linearConfig().copy(scheme = "RPE_RIR", targetRpe = 8.0, rpeTolerance = 2.5),
            linearConfig().copy(incrementValue = Double.POSITIVE_INFINITY),
            linearConfig().copy(incrementUnit = "UNKNOWN"),
            linearConfig().copy(incrementKg = 3.0),
            linearConfig().copy(stallThreshold = 0),
            linearConfig().copy(backoffPercent = 31.0),
            linearConfig().copy(ruleRevision = 0)
        )
        val invalidTargets = listOf(
            target.target.copy(sets = 0),
            target.target.copy(reps = 0),
            target.target.copy(weightKg = -1.0),
            target.target.copy(weightKg = Double.NaN)
        )

        invalidConfigs.forEach { config ->
            val broken = valid.copy(
                workoutPlanTargets = listOf(target.copy(progression = config))
            )
            assertFalse(BackupPayloadValidator.validate(broken, CURRENT_SCHEMA_VERSION).isValid)
        }
        invalidTargets.forEach { invalidTarget ->
            val broken = valid.copy(
                workoutPlanTargets = listOf(target.copy(target = invalidTarget))
            )
            assertFalse(BackupPayloadValidator.validate(broken, CURRENT_SCHEMA_VERSION).isValid)
        }
    }

    @Test
    fun `validator enforces outcome evidence and suggested target matrix`() {
        val valid = validPayloadWithProgression()
        val suggestion = valid.progressionSuggestions.single()
        val kept = valid.copy(
            progressionSuggestions = listOf(
                suggestion.copy(
                    outcomeType = "KEEP_TARGET",
                    reasonCode = "REPEAT_TARGET",
                    streakEffect = "RESET",
                    suggestedTarget = null,
                    status = "INFORMATIONAL"
                )
            )
        )
        val insufficient = valid.copy(
            progressionSuggestions = listOf(
                suggestion.copy(
                    outcomeType = "INSUFFICIENT_DATA",
                    reasonCode = "TOO_FEW_WORK_SETS",
                    streakEffect = "IGNORE",
                    countedSetIds = emptyList(),
                    suggestedTarget = null,
                    status = "INFORMATIONAL"
                )
            )
        )
        val broken = listOf(
            valid.copy(progressionSuggestions = listOf(suggestion.copy(suggestedTarget = null))),
            valid.copy(
                progressionSuggestions = listOf(
                    suggestion.copy(
                        outcomeType = "KEEP_TARGET",
                        reasonCode = "REPEAT_TARGET",
                        suggestedTarget = suggestion.suggestedTarget,
                        status = "INFORMATIONAL"
                    )
                )
            ),
            valid.copy(
                progressionSuggestions = listOf(
                    suggestion.copy(
                        countedSetIds = emptyList()
                    )
                )
            )
        )

        assertTrue(
            BackupPayloadValidator.validate(kept, CURRENT_SCHEMA_VERSION).isValid,
            BackupPayloadValidator.validate(kept, CURRENT_SCHEMA_VERSION).errors.joinToString()
        )
        assertTrue(
            BackupPayloadValidator.validate(insufficient, CURRENT_SCHEMA_VERSION).isValid,
            BackupPayloadValidator.validate(insufficient, CURRENT_SCHEMA_VERSION).errors.joinToString()
        )
        broken.forEach {
            assertFalse(BackupPayloadValidator.validate(it, CURRENT_SCHEMA_VERSION).isValid)
        }
    }

    @Test
    fun `validator enforces progression decision state and timestamps`() {
        val valid = validPayloadWithProgression()
        val suggestion = valid.progressionSuggestions.single()
        val accepted = suggestion.copy(
            status = "ACCEPTED",
            finalTarget = suggestion.suggestedTarget,
            decidedAtEpochMillis = 2_300L
        )
        val editedAcceptance = accepted.copy(
            finalTarget = requireNotNull(suggestion.suggestedTarget).copy(weightKg = 85.0),
            wasEdited = true
        )
        val rejected = suggestion.copy(status = "REJECTED", decidedAtEpochMillis = 2_300L)
        val stale = suggestion.copy(status = "STALE", decidedAtEpochMillis = 2_300L)
        val broken = listOf(
            suggestion.copy(decidedAtEpochMillis = 2_300L),
            suggestion.copy(finalTarget = suggestion.suggestedTarget),
            suggestion.copy(wasEdited = true),
            accepted.copy(finalTarget = null),
            accepted.copy(decidedAtEpochMillis = null),
            accepted.copy(wasEdited = true),
            editedAcceptance.copy(wasEdited = false),
            rejected.copy(finalTarget = suggestion.suggestedTarget),
            rejected.copy(wasEdited = true),
            stale.copy(decidedAtEpochMillis = null),
            suggestion.copy(createdAtEpochMillis = -1L),
            rejected.copy(decidedAtEpochMillis = 2_100L)
        )

        listOf(accepted, editedAcceptance, rejected, stale).forEach { validState ->
            val payload = valid.copy(progressionSuggestions = listOf(validState))
            assertTrue(
                BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION).isValid,
                BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION).errors.joinToString()
            )
        }
        broken.forEach { invalidState ->
            val payload = valid.copy(progressionSuggestions = listOf(invalidState))
            assertFalse(BackupPayloadValidator.validate(payload, CURRENT_SCHEMA_VERSION).isValid)
        }
    }

    @Test
    fun `schema before eleven rejects progression additions`() {
        val valid = validPayloadWithProgression()
        val schemaTenGraph = valid.copy(schemaVersion = 10)
        val schemaTenConfig = valid.copy(
            schemaVersion = 10,
            workoutPlanTargets = emptyList(),
            progressionSuggestions = emptyList(),
            workoutSets = valid.workoutSets.map { it.copy(planTargetSnapshotId = null) }
        )
        val schemaTenSnapshotLink = validPayload().copy(
            schemaVersion = 10,
            workoutSets = validPayload().workoutSets.map { it.copy(planTargetSnapshotId = 30L) }
        )

        assertFalse(BackupPayloadValidator.validate(schemaTenGraph, CURRENT_SCHEMA_VERSION).isValid)
        assertFalse(BackupPayloadValidator.validate(schemaTenConfig, CURRENT_SCHEMA_VERSION).isValid)
        assertFalse(BackupPayloadValidator.validate(schemaTenSnapshotLink, CURRENT_SCHEMA_VERSION).isValid)
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

    private fun validPayloadWithProgression(): BackupPayloadV1 {
        val progression = linearConfig()
        val target = BackupProgressionTarget(sets = 1, reps = 8, weightKg = 80.0)
        val suggested = BackupProgressionTarget(sets = 1, reps = 8, weightKg = 82.5)
        return validPayload().copy(
            workoutSessions = listOf(
                BackupWorkoutSession(
                    id = 10L,
                    startTime = 1000L,
                    endTime = 2000L,
                    durationSeconds = 1L,
                    name = "Push",
                    notes = "",
                    planId = 5L
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
                    completedAt = 1200L,
                    rpe = 8.0,
                    planTargetSnapshotId = 30L
                )
            ),
            planExercises = listOf(
                BackupPlanExercise(
                    id = 15L,
                    planId = 5L,
                    exerciseId = 1L,
                    orderIndex = 0,
                    targetSets = 1,
                    targetReps = 8,
                    targetWeightKg = 80.0,
                    progression = progression
                )
            ),
            workoutPlanTargets = listOf(
                BackupWorkoutPlanTarget(
                    id = 30L,
                    sessionId = 10L,
                    planId = 5L,
                    exerciseId = 1L,
                    orderIndex = 0,
                    target = target,
                    progression = progression
                )
            ),
            progressionSuggestions = listOf(
                BackupProgressionSuggestion(
                    id = 40L,
                    sourceSessionId = 10L,
                    sourceTargetSnapshotId = 30L,
                    planId = 5L,
                    exerciseId = 1L,
                    orderIndex = 0,
                    sourceTarget = target,
                    sourceProgression = progression,
                    outcomeType = "PROPOSE_CHANGE",
                    reasonCode = "LOAD_ADVANCED",
                    reasonArguments = mapOf(
                        "expectedWeightKg" to 80.0,
                        "actualWeightKg" to 80.0
                    ),
                    countedSetIds = listOf(20L),
                    streakEffect = "INCREMENT",
                    suggestedTarget = suggested,
                    status = "PENDING",
                    createdAtEpochMillis = 2200L
                )
            )
        )
    }

    private fun linearConfig() = BackupProgressionConfig(
        scheme = "LINEAR",
        incrementValue = 2.5,
        incrementUnit = "METRIC",
        incrementKg = 2.5,
        stallThreshold = 2,
        backoffPercent = 10.0,
        ruleRevision = 1
    )

    private val legacySchemaTenJson =
        """
        {
          "formatVersion": 1,
          "schemaVersion": 10,
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
          "workoutSessions": [
            {
              "id": 10,
              "startTime": 1000,
              "endTime": 2000,
              "durationSeconds": 1,
              "name": "Push",
              "notes": "",
              "planId": 5,
              "metaPlanId": null
            }
          ],
          "workoutSets": [
            {
              "id": 20,
              "sessionId": 10,
              "exerciseId": 1,
              "setNumber": 1,
              "reps": 8,
              "weightKg": 80.0,
              "isWarmup": false,
              "completedAt": 1200,
              "rpe": 8.5
            }
          ],
          "trainingPlans": [
            { "id": 5, "name": "Push", "createdAt": 1000 }
          ],
          "planExercises": [
            {
              "id": 15,
              "planId": 5,
              "exerciseId": 1,
              "orderIndex": 0,
              "supersetGroupId": null,
              "targetSets": 3,
              "targetReps": 8,
              "targetWeightKg": 80.0
            }
          ],
          "personalRecords": [],
          "metaTrainingPlans": [],
          "metaPlanItems": [],
          "metaPlanSkips": []
        }
        """.trimIndent()
}
