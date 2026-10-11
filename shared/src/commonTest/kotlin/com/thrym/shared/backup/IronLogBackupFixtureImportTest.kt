package com.thrym.shared.backup

import com.thrym.shared.model.MuscleGroup
import com.thrym.shared.plans.PlannedSet
import com.thrym.shared.readinessdata.SetIntention
import com.thrym.shared.store.DefaultExerciseCatalog
import com.thrym.shared.store.ProgressionLifecycle
import com.thrym.shared.store.RecoverySnapshot
import com.thrym.shared.store.SharedStatePersistence
import com.thrym.shared.store.SharedStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * Backups exported by IronLog before the Thrym rename must import unchanged.
 *
 * The documents in [IronLogBackupFixtures] are frozen output of the old export code. Exercise
 * names, enum codes and states asserted here are persisted data, not UI copy: they must stay
 * German canonical values regardless of the display language.
 */
class IronLogBackupFixtureImportTest {

    private val strictJson = Json { ignoreUnknownKeys = false }

    private val fixtures = listOf(
        IronLogBackupFixtures.ANDROID_EXPORT_JSON,
        IronLogBackupFixtures.IOS_EXPORT_JSON,
    )

    @Test
    fun fixturesAreTheUnmodifiedGoldenDocuments() {
        assertEquals(
            IronLogBackupFixtures.ANDROID_EXPORT_SIZE_BYTES,
            IronLogBackupFixtures.ANDROID_EXPORT_JSON.encodeToByteArray().size,
        )
        assertEquals(
            IronLogBackupFixtures.IOS_EXPORT_SIZE_BYTES,
            IronLogBackupFixtures.IOS_EXPORT_JSON.encodeToByteArray().size,
        )
        val android = decode(IronLogBackupFixtures.ANDROID_EXPORT_JSON)
        val ios = decode(IronLogBackupFixtures.IOS_EXPORT_JSON)
        assertEquals("1.4.0", android.appVersion)
        assertEquals(1_791_751_844_960L, android.exportedAtEpochMillis)
        assertEquals("0.1.0", ios.appVersion)
        assertEquals(1_789_128_000_000L, ios.exportedAtEpochMillis)
        // Both platforms exported the same graph; only the export metadata differs.
        assertEquals(
            android,
            ios.copy(appVersion = android.appVersion, exportedAtEpochMillis = android.exportedAtEpochMillis),
        )
    }

    @Test
    fun currentDecoderAndValidatorAcceptBothFixtures() {
        fixtures.forEach { json ->
            val payload = decode(json)

            assertEquals(1, payload.formatVersion)
            assertEquals(15, payload.schemaVersion)
            assertTrue(payload.schemaVersion <= CURRENT_BACKUP_SCHEMA_VERSION)
            val validation = BackupPayloadValidator.validate(payload, CURRENT_BACKUP_SCHEMA_VERSION)
            assertTrue(validation.isValid, "IronLog backup rejected: ${validation.errors}")
        }
    }

    @Test
    fun decodedFixtureKeepsCanonicalExercisesAndWorkoutHistory() {
        fixtures.forEach { json ->
            val payload = decode(json)

            assertEquals(82, payload.exercises.size)
            val builtIns = payload.exercises.filterNot { it.isCustom }
            assertEquals(DefaultExerciseCatalog.exercises(), builtIns)
            assertEquals((1L..79L).toList(), builtIns.map { it.id })
            val names = payload.exercises.associate { it.id to it.name }
            assertEquals("Bankdrücken (Langhantel)", names[1L])
            assertEquals("Rumänisches Kreuzheben", names[28L])
            assertEquals("Seitheben (Kurzhantel)", names[47L])
            assertEquals("Trizepsdrücken am Kabel", names[61L])
            assertEquals("Wadenheben an der Beinpresse", names[79L])
            val landmine = payload.exercises.single { it.id == 80L }
            assertEquals("Landmine Press (einarmig)", landmine.name)
            assertEquals("Griff „neutral“ – 45° Winkel; Ellbogen nah am Körper", landmine.notes)
            assertEquals("TRIZEPS,BRUST", landmine.secondaryMuscleGroups)
            val archived = payload.exercises.single { it.isArchived }
            assertEquals(82L, archived.id)
            assertEquals("Beinpresse 45° (Studio Süd)", archived.name)
            assertTrue(archived.isCustom)

            assertEquals(7, payload.workoutSessions.size)
            assertEquals(listOf(false, false, false, true, null, false, null), payload.workoutSessions.map { it.isDeload })
            assertEquals(listOf(7L), payload.workoutSessions.filter { it.endTime == null }.map { it.id })
            assertEquals(
                "Gute Einheit – Schulter etwas steif.\nNächstes Mal länger aufwärmen.",
                payload.workoutSessions.single { it.id == 1L }.notes,
            )
            assertEquals(
                "Sonderzeichen-Test: \"Anführungszeichen\", Backslash \\ und Tab\tEnde",
                payload.workoutSessions.single { it.id == 6L }.notes,
            )
            assertEquals(listOf(1L, 1L, 1L, 1L, null, null, null), payload.workoutSessions.map { it.metaPlanId })

            assertEquals(49, payload.workoutSets.size)
            assertEquals(SUPPORTED_BACKUP_SET_TYPES, payload.workoutSets.map { it.setType }.toSet())
            assertTrue(payload.workoutSets.all { it.isWarmup == null })
            val dropSet = payload.workoutSets.single { it.sessionId == 1L && it.setType == DROP_SET_TYPE }
            assertEquals(8, dropSet.reps)
            assertEquals(80.0, dropSet.weightKg)
            assertEquals(9.0, dropSet.rpe)
            val benchWorkSets = payload.workoutSets.filter {
                it.sessionId == 1L && it.exerciseId == 1L && it.setType == NORMAL_SET_TYPE
            }
            assertEquals(listOf(7.0, 7.5, 8.0), benchWorkSets.map { it.rpe })
            assertEquals(0, payload.workoutSets.single { it.id == 45L }.reps)
            assertEquals(41, payload.personalRecords.size)
            assertEquals(
                setOf("MAX_WEIGHT", "MAX_REPS", "MAX_E1RM", "MAX_VOLUME"),
                payload.personalRecords.map { it.type }.toSet(),
            )
        }
    }

    @Test
    fun decodedFixtureKeepsPlansProgressionAndReadiness() {
        fixtures.forEach { json ->
            val payload = decode(json)

            assertEquals(listOf("Push A (Kraft)", "Pull B", "Beine & Rumpf"), payload.trainingPlans.map { it.name })
            assertEquals("Ganzkörper-Rotation", payload.metaTrainingPlans.single().name)
            assertEquals(listOf(1L, 2L, 3L), payload.metaPlanItems.sortedBy { it.orderIndex }.map { it.trainingPlanId })
            assertEquals(3L, payload.metaPlanSkips.single().trainingPlanId)

            val plan = payload.planExercises.associateBy { it.id }
            assertEquals(
                setOf("MANUAL", "LINEAR", "DOUBLE", "TOTAL_REPS", "RPE_RIR"),
                plan.values.map { it.progression.scheme }.toSet(),
            )
            assertEquals(2, plan.getValue(1L).progression.successThreshold)
            assertEquals(8 to 12, plan.getValue(2L).progression.let { it.minReps to it.maxReps })
            val imperial = plan.getValue(3L).progression
            assertEquals("IMPERIAL", imperial.incrementUnit)
            assertEquals(5.0, imperial.incrementValue)
            assertEquals(5.0 / 2.2046226218, imperial.incrementKg)
            assertEquals(36L, imperial.targetTotalReps)
            assertEquals(3, imperial.stallThreshold)
            assertEquals(7.5, imperial.backoffPercent)
            assertEquals(1, plan.getValue(3L).supersetGroupId)
            assertEquals(1, plan.getValue(4L).supersetGroupId)
            assertEquals(8.0 to 0.5, plan.getValue(4L).progression.let { it.targetRpe to it.rpeTolerance })
            assertEquals(
                listOf(
                    PlannedSet("WARMUP", 12, 20.0),
                    PlannedSet("NORMAL", 10, 35.0),
                    PlannedSet("NORMAL", 10, 35.0),
                    PlannedSet("BACKOFF", 15, 25.0),
                ),
                plan.getValue(5L).setTargets,
            )
            assertEquals(BackupProgressionConfig(), plan.getValue(5L).progression)
            assertEquals(1, plan.getValue(6L).progression.ruleRevision)
            assertEquals(3, plan.getValue(8L).progression.ruleRevision)

            assertEquals(19, payload.workoutPlanTargets.size)
            assertEquals(3, payload.workoutPlanTargets.count { it.setTargets.isNotEmpty() })

            val suggestions = payload.progressionSuggestions
            assertEquals(14, suggestions.size)
            assertEquals(
                mapOf("INFORMATIONAL" to 9, "ACCEPTED" to 2, "REJECTED" to 1, "STALE" to 1, "PENDING" to 1),
                suggestions.groupingBy { it.status }.eachCount(),
            )
            assertEquals(
                setOf("PROPOSE_CHANGE", "KEEP_TARGET", "INSUFFICIENT_DATA"),
                suggestions.map { it.outcomeType }.toSet(),
            )
            val editedAccept = suggestions.single { it.wasEdited }
            assertEquals("ACCEPTED", editedAccept.status)
            assertEquals(80L, editedAccept.exerciseId)
            assertTrue(editedAccept.finalTarget != editedAccept.suggestedTarget)
            val unsupported = suggestions.single { it.sourceProgression.ruleRevision == 3 }
            assertEquals("RULE_REVISION_UNSUPPORTED", unsupported.reasonCode)
            val pending = suggestions.single { it.status == "PENDING" }
            assertEquals(5L, pending.sourceSessionId)
            assertEquals("LOAD_ADVANCED", pending.reasonCode)
            assertEquals(3, pending.countedSetIds.size)
            assertTrue(pending.reasonArguments.isNotEmpty())

            val readiness = payload.readinessData
            assertEquals(
                listOf(LocalDate(2026, 9, 1), LocalDate(2026, 9, 3), LocalDate(2026, 9, 5)),
                readiness.checkIns.map { it.localDate },
            )
            val partial = readiness.checkIns[1]
            assertEquals(2, partial.sleepQuality)
            assertNull(partial.energy)
            assertTrue(partial.muscleSoreness.isEmpty())
            val edited = readiness.checkIns[2]
            assertEquals(mapOf(MuscleGroup.BEINE to 4, MuscleGroup.GESAESS to 2), edited.muscleSoreness)
            assertNotNull(edited.updatedAtEpochMillis)
            assertEquals(5, readiness.setIntentions.size)
            assertEquals(
                setOf(SetIntention.PLANNED_FAILURE, SetIntention.UNEXPECTED_TARGET_MISS),
                readiness.setIntentions.map { it.intention }.toSet(),
            )
            assertEquals("Wollte 15, Griff rutschte", readiness.setIntentions.single { it.setId == 18L }.note)
        }
    }

    @Test
    fun sharedStoreImportsBothFixturesThroughTheIosConfirmFlow() = runTest {
        fixtures.forEach { json ->
            val store = SharedStateStore(MemoryPersistence(), nowEpochMillis = { NOW })
            val replacedSnapshot = store.snapshot()
            val preview = SharedStateStore.decodeAndUpgradeForPreview(json)
            var recovery: RecoverySnapshot? = null

            store.importJsonIfUnchangedWithRecovery(json, replacedSnapshot) { recovery = it }
            // IosBackupRepository runs the progression sweep after every import.
            val lifecycle = ProgressionLifecycle(store, nowEpochMillis = { NOW })
            lifecycle.generateMissingOutcomes()
            lifecycle.reconcileOutstandingSuggestions()

            assertEquals(replacedSnapshot, recovery?.payload)
            assertEquals(expectedImportedState(json), preview)
            assertEquals(preview, store.snapshot())
        }
    }

    @Test
    fun sharedStoreUpgradesOnlyLegacyLivePlanRevisions() = runTest {
        val store = SharedStateStore(MemoryPersistence(), nowEpochMillis = { NOW })

        store.importJson(IronLogBackupFixtures.IOS_EXPORT_JSON)

        val state = store.snapshot()
        val legacyRow = state.planExercises.single { it.id == 6L }
        assertEquals(2, legacyRow.progression.ruleRevision)
        assertEquals(3, state.planExercises.single { it.id == 8L }.progression.ruleRevision)
        // Session snapshots and decided outcomes keep the rules they were recorded with.
        assertTrue(state.workoutPlanTargets.filter { it.exerciseId == 13L }.all { it.progression.ruleRevision == 1 })
        assertEquals(1, state.progressionSuggestions.single { it.exerciseId == 13L }.sourceProgression.ruleRevision)
        assertEquals("", state.appVersion)
        assertEquals(0L, state.exportedAtEpochMillis)
    }

    @Test
    fun sharedStoreBootsFromAFixtureAsPersistedState() {
        fixtures.forEach { json ->
            val persistence = MemoryPersistence(json)

            val store = SharedStateStore(persistence, nowEpochMillis = { NOW })

            assertEquals(expectedImportedState(json), store.snapshot())
            assertEquals(store.snapshot(), SharedStateStore.decodeAndUpgradeForPreview(requireNotNull(persistence.value)))
        }
    }

    @Test
    fun reExportFromSharedStoreKeepsTheImportedGraph() = runTest {
        fixtures.forEach { json ->
            val store = SharedStateStore(MemoryPersistence(), appVersion = "9.9.9", nowEpochMillis = { NOW })
            store.importJson(json)

            val exported = decode(store.exportJson())

            assertEquals("9.9.9", exported.appVersion)
            assertEquals(NOW, exported.exportedAtEpochMillis)
            assertEquals(store.snapshot(), exported.copy(appVersion = "", exportedAtEpochMillis = 0L))
            assertEquals(decode(json).exercises, exported.exercises)
        }
    }

    private fun decode(json: String): BackupPayloadV1 = strictJson.decodeFromString(BackupPayloadV1.serializer(), json)

    /** The only changes an import may make: drop export metadata and upgrade live legacy plan rules. */
    private fun expectedImportedState(json: String): BackupPayloadV1 {
        val decoded = decode(json)
        return decoded.copy(
            schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            appVersion = "",
            exportedAtEpochMillis = 0L,
            planExercises = decoded.planExercises.map { it.copy(progression = it.progression.forCurrentPlan()) },
        )
    }

    private class MemoryPersistence(var value: String? = null) : SharedStatePersistence {
        override fun read(): String? = value

        override fun writeAtomically(serialized: String) {
            value = serialized
        }
    }

    private companion object {
        const val NOW = 1_789_200_000_000L
    }
}
