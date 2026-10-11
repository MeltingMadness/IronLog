package com.thrym.app.data.repository

import android.net.Uri
import com.thrym.app.data.backup.BackupDocumentIo
import com.thrym.app.data.backup.RecoveryBackupStore
import com.thrym.app.data.backup.sha256Hex
import com.thrym.app.data.db.TransactionRunner
import com.thrym.app.data.local.dao.ExerciseDao
import com.thrym.app.data.local.dao.MetaTrainingPlanDao
import com.thrym.app.data.local.dao.PersonalRecordDao
import com.thrym.app.data.local.dao.ProgressionDao
import com.thrym.app.data.local.dao.ReadinessDataDao
import com.thrym.app.data.local.dao.TrainingPlanDao
import com.thrym.app.data.local.dao.WorkoutSessionDao
import com.thrym.app.data.local.dao.WorkoutSetDao
import com.thrym.app.data.local.entity.ExerciseEntity
import com.thrym.app.data.local.entity.MetaPlanItemEntity
import com.thrym.app.data.local.entity.MetaPlanSkipEntity
import com.thrym.app.data.local.entity.MetaTrainingPlanEntity
import com.thrym.app.data.local.entity.PersonalRecordEntity
import com.thrym.app.data.local.entity.PlanExerciseEntity
import com.thrym.app.data.local.entity.ProgressionSuggestionEntity
import com.thrym.app.data.local.entity.ReadinessDataEntity
import com.thrym.app.data.local.entity.TrainingPlanEntity
import com.thrym.app.data.local.entity.WorkoutPlanTargetEntity
import com.thrym.app.data.local.entity.WorkoutSessionEntity
import com.thrym.app.data.local.entity.WorkoutSetEntity
import com.thrym.app.data.seed.ExerciseSeedData
import com.thrym.app.domain.model.ExerciseCategory
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.domain.model.RecordType
import com.thrym.app.domain.model.SetType
import com.thrym.app.domain.repository.BackupContentCounts
import com.thrym.app.domain.repository.RecoveryBackup
import com.thrym.app.domain.util.BuildInfo
import com.thrym.shared.backup.BackupPayloadV1
import com.thrym.shared.backup.forCurrentPlan
import com.thrym.shared.plans.PlannedSet
import com.thrym.shared.plans.PlannedSets
import com.thrym.shared.readinessdata.ReadinessDataCodec
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backups exported by IronLog before the Thrym rename must import unchanged on Android.
 *
 * The resources under `backup-fixtures/` are frozen output of the old export code (see
 * `IronLogBackupFixtures` in the shared module, which holds byte-identical copies). Exercise
 * names and enum codes asserted here are persisted data, not UI copy.
 */
class IronLogBackupFixtureImportTest {

    @Test
    fun `fixtures are byte identical to the golden IronLog exports`() {
        assertEquals(ANDROID_SHA256, fixture(ANDROID_EXPORT).sha256Hex())
        assertEquals(IOS_SHA256, fixture(IOS_EXPORT).sha256Hex())
    }

    @Test
    fun `preview accepts IronLog backups from both platforms`() {
        mapOf(ANDROID_EXPORT to "1.4.0", IOS_EXPORT to "0.1.0").forEach { (name, appVersion) ->
            val db = FakeDatabase()
            db.document = fixture(name)

            val preview = runBlocking { db.repository.previewImport(URI) }

            assertTrue("$name rejected: ${preview.validationErrors}", preview.isValid)
            assertEquals(db.document.sha256Hex(), preview.sha256)
            assertEquals(15, preview.schemaVersion)
            assertEquals(appVersion, preview.appVersion)
            assertEquals(
                BackupContentCounts(
                    exercises = 82,
                    workoutSessions = 7,
                    workoutSets = 49,
                    trainingPlans = 3,
                    planExercises = 9,
                    personalRecords = 41,
                    metaTrainingPlans = 1,
                    metaPlanItems = 3,
                    metaPlanSkips = 1,
                    workoutPlanTargets = 19,
                    progressionSuggestions = 14
                ),
                preview.counts
            )
            assertEquals(0, db.transactionCount)
        }
    }

    @Test
    fun `import stores IronLog rows with canonical names codes and progression state`() {
        listOf(ANDROID_EXPORT, IOS_EXPORT).forEach { name ->
            val db = FakeDatabase()
            db.document = fixture(name)
            val payload = decode(db.document)
            val seededCatalog = db.exercises

            runBlocking { db.repository.importBackup(URI, db.document.sha256Hex()) }

            // The replaced fresh-install catalog is kept as the recovery snapshot.
            assertEquals(seededCatalog.size, decode(db.recoveries.single()).exercises.size)

            // Built-in rows keep the German canonical names Thrym seeds with the same ids,
            // so display-time translation keyed on those names applies to imported data.
            val builtIns = db.exercises.filterNot { it.isCustom }
            assertEquals(seededCatalog.map { it.id to it.name }, builtIns.map { it.id to it.name })
            assertEquals("Bankdrücken (Langhantel)", builtIns.first().name)
            val custom = db.exercises.filter { it.isCustom }
            assertEquals(
                listOf("Landmine Press (einarmig)", "Kabel-Crunch kniend", "Beinpresse 45° (Studio Süd)"),
                custom.map { it.name }
            )
            assertEquals(listOf(false, false, true), custom.map { it.isArchived })
            assertEquals("Griff „neutral“ – 45° Winkel; Ellbogen nah am Körper", custom.first().notes)
            // Persisted codes are German enum constant names and must still resolve 1:1.
            assertEquals(MuscleGroup.entries.map { it.name }.toSet(), db.exercises.map { it.primaryMuscleGroup }.toSet())
            assertEquals(ExerciseCategory.entries.map { it.name }.toSet(), db.exercises.map { it.category }.toSet())

            assertEquals(listOf(false, false, false, true, null, false, null), db.sessions.map { it.isDeload })
            assertEquals(1, db.sessions.count { it.endTime == null })
            assertEquals(SetType.entries.map { it.name }.toSet(), db.sets.map { it.setType }.toSet())
            assertEquals(payload.workoutSets.map { it.rpe }, db.sets.map { it.rpe })
            assertEquals(payload.workoutSets.map { it.planTargetSnapshotId }, db.sets.map { it.planTargetSnapshotId })

            val planRows = db.planExercises.associateBy { it.id }
            assertEquals(
                listOf("LINEAR", "DOUBLE", "TOTAL_REPS", "RPE_RIR", "MANUAL", "LINEAR", "MANUAL", "LINEAR", "LINEAR"),
                db.planExercises.map { it.progression.scheme }
            )
            // Only live legacy rows are upgraded; the unsupported revision 3 stays as recorded.
            assertEquals(listOf(2, 2, 2, 2, 1, 2, 1, 3, 2), db.planExercises.map { it.progression.ruleRevision })
            assertEquals("IMPERIAL", planRows.getValue(3L).progression.incrementUnit)
            assertEquals(5.0 / 2.2046226218, planRows.getValue(3L).progression.incrementKg!!, 0.0)
            assertEquals(
                listOf(
                    PlannedSet("WARMUP", 12, 20.0),
                    PlannedSet("NORMAL", 10, 35.0),
                    PlannedSet("NORMAL", 10, 35.0),
                    PlannedSet("BACKOFF", 15, 25.0)
                ),
                PlannedSets.decode(planRows.getValue(5L).setTargetsJson)
            )
            assertTrue(db.targets.filter { it.exerciseId == 13L }.all { it.progression.ruleRevision == 1 })
            assertEquals(3, db.targets.count { PlannedSets.decode(it.setTargetsJson).isNotEmpty() })

            assertEquals(payload.progressionSuggestions.map { it.status }, db.suggestions.map { it.status })
            assertEquals(
                payload.progressionSuggestions.map { it.reasonArguments },
                db.suggestions.map { json.decodeFromString(REASON_ARGUMENTS, it.reasonArgumentsJson) }
            )
            assertEquals(
                payload.progressionSuggestions.map { it.countedSetIds },
                db.suggestions.map { json.decodeFromString(COUNTED_SET_IDS, it.countedSetIdsJson) }
            )
            val edited = db.suggestions.single { it.wasEdited }
            assertEquals("ACCEPTED", edited.status)
            assertNotEquals(edited.suggestedTarget, edited.finalTarget)

            assertEquals(41, db.records.size)
            assertEquals(RecordType.entries.map { it.name }.toSet(), db.records.map { it.type }.toSet())
            assertEquals(listOf(3L), db.metaPlanSkips.map { it.trainingPlanId })
            assertEquals(
                payload.readinessData,
                ReadinessDataCodec.decode(requireNotNull(db.readinessPayload))
            )
        }
    }

    @Test
    fun `export after import reproduces the IronLog graph`() {
        listOf(ANDROID_EXPORT, IOS_EXPORT).forEach { name ->
            val db = FakeDatabase()
            db.document = fixture(name)
            val original = decode(db.document)
            runBlocking { db.repository.importBackup(URI, db.document.sha256Hex()) }

            runBlocking { db.repository.exportBackup(URI) }

            val exported = decode(requireNotNull(db.written))
            assertEquals("9.9.9", exported.appVersion)
            assertEquals(
                original.copy(
                    appVersion = exported.appVersion,
                    exportedAtEpochMillis = exported.exportedAtEpochMillis,
                    planExercises = original.planExercises.map { it.copy(progression = it.progression.forCurrentPlan()) }
                ),
                exported
            )
        }
    }

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/backup-fixtures/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    private fun decode(bytes: ByteArray): BackupPayloadV1 =
        json.decodeFromString(BackupPayloadV1.serializer(), bytes.decodeToString())

    /**
     * In-memory Room stand-in: every DAO call the backup repository makes reads or replaces one
     * table, so an import can be exported again from exactly what it wrote. It starts like a
     * fresh install, with only the seeded exercise catalog.
     */
    private class FakeDatabase {
        var document: ByteArray = ByteArray(0)
        var written: ByteArray? = null
        val recoveries = mutableListOf<ByteArray>()
        var transactionCount = 0
            private set

        var exercises = ExerciseSeedData.getAll().mapIndexed { index, exercise -> exercise.copy(id = index + 1L) }
        var sessions = emptyList<WorkoutSessionEntity>()
        var sets = emptyList<WorkoutSetEntity>()
        var plans = emptyList<TrainingPlanEntity>()
        var planExercises = emptyList<PlanExerciseEntity>()
        var records = emptyList<PersonalRecordEntity>()
        var metaPlans = emptyList<MetaTrainingPlanEntity>()
        var metaPlanItems = emptyList<MetaPlanItemEntity>()
        var metaPlanSkips = emptyList<MetaPlanSkipEntity>()
        var targets = emptyList<WorkoutPlanTargetEntity>()
        var suggestions = emptyList<ProgressionSuggestionEntity>()
        var readinessPayload: String? = null

        private val exerciseDao = mockk<ExerciseDao>().also { dao ->
            coEvery { dao.getAllExercisesList() } answers { exercises }
            coEvery { dao.deleteAll() } answers { exercises = emptyList() }
            coEvery { dao.replaceAll(any()) } answers { exercises = firstArg() }
        }
        private val workoutSessionDao = mockk<WorkoutSessionDao>().also { dao ->
            coEvery { dao.getAllSessionsList() } answers { sessions }
            coEvery { dao.deleteAll() } answers { sessions = emptyList() }
            coEvery { dao.replaceAll(any()) } answers { sessions = firstArg() }
        }
        private val workoutSetDao = mockk<WorkoutSetDao>().also { dao ->
            coEvery { dao.getAllSetsList() } answers { sets }
            coEvery { dao.deleteAll() } answers { sets = emptyList() }
            coEvery { dao.replaceAll(any()) } answers { sets = firstArg() }
        }
        private val trainingPlanDao = mockk<TrainingPlanDao>().also { dao ->
            coEvery { dao.getAllPlansList() } answers { plans }
            coEvery { dao.getAllPlanExercisesList() } answers { planExercises }
            coEvery { dao.deleteAllPlans() } answers { plans = emptyList() }
            coEvery { dao.deleteAllPlanExercises() } answers { planExercises = emptyList() }
            coEvery { dao.replaceAllPlans(any()) } answers { plans = firstArg() }
            coEvery { dao.replaceAllExercises(any()) } answers { planExercises = firstArg() }
        }
        private val metaTrainingPlanDao = mockk<MetaTrainingPlanDao>().also { dao ->
            coEvery { dao.getAllMetaPlansList() } answers { metaPlans }
            coEvery { dao.getAllMetaPlanItemsList() } answers { metaPlanItems }
            coEvery { dao.getAllMetaPlanSkipsList() } answers { metaPlanSkips }
            coEvery { dao.deleteAllMetaPlans() } answers { metaPlans = emptyList() }
            coEvery { dao.deleteAllMetaPlanItems() } answers { metaPlanItems = emptyList() }
            coEvery { dao.deleteAllMetaPlanSkips() } answers { metaPlanSkips = emptyList() }
            coEvery { dao.replaceAllMetaPlans(any()) } answers { metaPlans = firstArg() }
            coEvery { dao.replaceAllItems(any()) } answers { metaPlanItems = firstArg() }
            coEvery { dao.replaceAllMetaPlanSkips(any()) } answers { metaPlanSkips = firstArg() }
        }
        private val personalRecordDao = mockk<PersonalRecordDao>().also { dao ->
            coEvery { dao.getAllRecordsList() } answers { records }
            coEvery { dao.deleteAll() } answers { records = emptyList() }
            coEvery { dao.replaceAll(any()) } answers { records = firstArg() }
        }
        private val progressionDao = mockk<ProgressionDao>().also { dao ->
            coEvery { dao.getAllTargets() } answers { targets }
            coEvery { dao.getAllSuggestions() } answers { suggestions }
            coEvery { dao.deleteAllTargets() } answers { targets = emptyList() }
            coEvery { dao.deleteAllSuggestions() } answers { suggestions = emptyList() }
            coEvery { dao.replaceAllTargets(any()) } answers { targets = firstArg() }
            coEvery { dao.replaceAllSuggestions(any()) } answers { suggestions = firstArg() }
        }
        private val readinessDataDao = mockk<ReadinessDataDao>().also { dao ->
            coEvery { dao.getPayload() } answers { readinessPayload }
            coEvery { dao.deleteAll() } answers { readinessPayload = null }
            coEvery { dao.upsert(any()) } answers { readinessPayload = firstArg<ReadinessDataEntity>().payload }
        }

        val repository = BackupRepositoryImpl(
            transactionRunner = object : TransactionRunner {
                override suspend fun <T> runInTransaction(block: suspend () -> T): T {
                    transactionCount += 1
                    return block()
                }
            },
            documentIo = object : BackupDocumentIo {
                override suspend fun writeVerified(uri: Uri, bytes: ByteArray) {
                    written = bytes
                }

                override suspend fun readBytes(uri: Uri): ByteArray = document
            },
            recoveryStore = object : RecoveryBackupStore {
                override suspend fun latest(): RecoveryBackup? = null

                override suspend fun save(bytes: ByteArray): RecoveryBackup {
                    recoveries += bytes
                    return RecoveryBackup(
                        timestampMillis = recoveries.size.toLong(),
                        sha256 = bytes.sha256Hex(),
                        sizeBytes = bytes.size.toLong()
                    )
                }

                override suspend fun loadLatestBytes(): ByteArray? = recoveries.lastOrNull()

                override suspend fun delete(backup: RecoveryBackup) = Unit
            },
            exerciseDao = exerciseDao,
            workoutSessionDao = workoutSessionDao,
            workoutSetDao = workoutSetDao,
            trainingPlanDao = trainingPlanDao,
            metaTrainingPlanDao = metaTrainingPlanDao,
            personalRecordDao = personalRecordDao,
            progressionDao = progressionDao,
            readinessDataDao = readinessDataDao,
            buildInfo = BuildInfo(versionName = "9.9.9", versionCode = 99)
        )
    }

    private companion object {
        const val ANDROID_EXPORT = "ironlog-android-export.json"
        const val IOS_EXPORT = "ironlog-ios-export.json"
        const val ANDROID_SHA256 = "51aad8e8cbab084d117c74ff54dbf17d2a43a78d22ed7752aaa95b45af1c4005"
        const val IOS_SHA256 = "e6a002112d9333d27622e74941faff00b2f2a2a6ff2dabeb9d22722f99ada36f"
        val URI = mockk<Uri>()
        val json = Json { ignoreUnknownKeys = false }
        val REASON_ARGUMENTS = MapSerializer(String.serializer(), Double.serializer())
        val COUNTED_SET_IDS = ListSerializer(Long.serializer())
    }
}
