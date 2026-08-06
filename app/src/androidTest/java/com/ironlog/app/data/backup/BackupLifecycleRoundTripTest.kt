package com.ironlog.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.data.db.RoomTransactionRunner
import com.ironlog.app.data.local.IronLogDatabase
import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.data.repository.BackupRepositoryImpl
import com.ironlog.app.domain.repository.BackupImportPreview
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupMetaPlanItem
import com.ironlog.shared.backup.BackupMetaTrainingPlan
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupPersonalRecord
import com.ironlog.shared.backup.BackupPlanExercise
import com.ironlog.shared.backup.BackupTrainingPlan
import com.ironlog.shared.backup.BackupWorkoutSession
import com.ironlog.shared.backup.BackupWorkoutSet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Gate-3 lifecycle test: real Room database, real DAOs, real
 * [RoomTransactionRunner], real [FileRecoveryBackupStore] and the production
 * [BackupRepositoryImpl]. Only the content-provider boundary is faked
 * ([InMemoryBackupDocumentIo] keeps the export/import bytes in memory);
 * ContentResolver semantics are covered separately in
 * ContentResolverBackupDocumentIoTest.
 *
 * Flow: seed all eight workout-domain tables -> export -> mutate/empty the
 * database -> preview + hash-guarded import -> full canonical parity of all
 * eight tables plus PRAGMA foreign_key_check. A second test verifies that the
 * import produced a real file in the recovery store and that
 * restoreLatestRecovery brings back the exact pre-import state.
 */
@RunWith(AndroidJUnit4::class)
class BackupLifecycleRoundTripTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var harness: Harness

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.close()
        }
    }

    @Test
    fun exportMutateImport_restoresAllEightTablesWithCanonicalParityAndFkIntegrity() =
        runBlocking {
            harness = Harness(context)
            harness.seedFullDomain()

            // Export must produce a parseable document that describes the seeded state.
            harness.repository.exportBackup(MEMORY_URI)
            val exported = harness.documentIo.bytes
            assertTrue("export must produce bytes", exported.isNotEmpty())
            val payload = json.decodeFromString(BackupPayloadV1.serializer(), exported.decodeToString())
            assertTrue("export must contain non-seed exercises", payload.exercises.any { it.id == CUSTOM_SQUAT_ID })
            assertTrue("export must contain RPE", payload.workoutSets.any { it.rpe == 9.0 })
            assertTrue("export must contain notes", payload.exercises.any { it.notes.isNotBlank() })
            assertEquals(9, payload.schemaVersion)

            // Mutate/empty the database so the imported document must prove itself.
            harness.mutateAwayFromSeededState()

            val preview = harness.repository.previewImport(MEMORY_URI)
            assertTrue("preview must validate", preview.isValid)
            assertEquals(9, preview.schemaVersion)
            assertEquals(sha256Hex(exported), preview.sha256)
            assertPreviewCounts(preview, payload)

            harness.repository.importBackup(MEMORY_URI, preview.sha256)

            assertEightTableParity(harness, payload)
            assertForeignKeyIntegrity()
        }

    @Test
    fun importCreatesRealRecoveryFileAndRestoreLatestRecoveryReturnsPreImportState() =
        runBlocking {
            harness = Harness(context)
            harness.seedFullDomain()

            // Export the seeded state; the document is then re-imported after the
            // database was mutated (two-phase flow with a changed local state).
            harness.repository.exportBackup(MEMORY_URI)

            // Mutate the database so the recovery snapshot captures a state that
            // differs from the exported document (proves restore replaces data).
            harness.mutateAwayFromSeededState()

            val preImportPlanName = harness.preImportPlanName
            val preImportSetCount = harness.preImportSetCount

            // Two-phase import: preview (document hash) then guarded import.
            val preview = harness.repository.previewImport(MEMORY_URI)
            assertTrue("preview must validate", preview.isValid)
            harness.repository.importBackup(MEMORY_URI, preview.sha256)

            // A real FileRecoveryBackupStore file must now exist and be hash-verified.
            val stored = harness.listRecoveryFiles()
            assertEquals("exactly one recovery file after one import", 1, stored.size)
            val latest = requireNotNull(harness.repository.latestRecovery()) {
                "latestRecovery must be available"
            }
            val storedName = stored.single()
            val fileNameMatch = requireNotNull(RECOVERY_FILE_NAME.matchEntire(storedName)) {
                "recovery file name must match recovery-<timestamp>-<64hex>.json: $storedName"
            }
            assertNotNull(
                "recovery file name must contain a numeric timestamp",
                fileNameMatch.groupValues[1].toLongOrNull()
            )
            assertEquals(latest.sha256, fileNameMatch.groupValues[2])
            assertEquals(
                sha256Hex(requireNotNull(harness.loadLatestBytes())),
                latest.sha256
            )

            // The pre-import (mutated) state differs from the imported state: plan name
            // and set count prove that restore really replaces the current database.
            assertFalse(
                "imported state must differ from pre-import state",
                harness.readPlanName() == preImportPlanName &&
                    harness.readSetCount() == preImportSetCount
            )
            assertTrue(
                "import must have restored the exported sets",
                harness.readSetCount() == harness.exportedSetCount
            )

            // Mutate again, then restore the latest recovery snapshot.
            harness.mutateAfterImport()
            assertTrue(
                "post-import mutation must be visible",
                harness.readSetCount() != harness.exportedSetCount
            )
            val restored = harness.repository.restoreLatestRecovery()
                ?: throw AssertionError("restore must return the recovery metadata")
            assertEquals(latest.sha256, restored.sha256)
            assertEquals(
                "restore must first save a fresh recovery snapshot of the current state",
                2,
                harness.listRecoveryFiles().size
            )

            // restoreLatestRecovery must restore the exact pre-import mutated state.
            assertEquals(preImportPlanName, harness.readPlanName())
            assertEquals(preImportSetCount, harness.readSetCount())
            assertEquals(
                harness.preImportCustomExerciseIds,
                harness.readCustomExerciseIds()
            )
            assertFalse("restored sets must be the pre-import (deleted) state", harness.readSetCount() > 0)
            assertForeignKeyIntegrity()
        }

    private fun assertPreviewCounts(preview: BackupImportPreview, payload: BackupPayloadV1) {
        assertEquals(payload.exercises.size, preview.counts.exercises)
        assertEquals(payload.workoutSessions.size, preview.counts.workoutSessions)
        assertEquals(payload.workoutSets.size, preview.counts.workoutSets)
        assertEquals(payload.trainingPlans.size, preview.counts.trainingPlans)
        assertEquals(payload.planExercises.size, preview.counts.planExercises)
        assertEquals(payload.personalRecords.size, preview.counts.personalRecords)
        assertEquals(payload.metaTrainingPlans.size, preview.counts.metaTrainingPlans)
        assertEquals(payload.metaPlanItems.size, preview.counts.metaPlanItems)
    }

    private suspend fun assertEightTableParity(harness: Harness, payload: BackupPayloadV1) {
        assertEquals(payload.exercises, harness.readExercises())
        assertEquals(payload.workoutSessions, harness.readWorkoutSessions())
        assertEquals(payload.workoutSets, harness.readWorkoutSets())
        assertEquals(payload.trainingPlans, harness.readTrainingPlans())
        assertEquals(payload.planExercises, harness.readPlanExercises())
        assertEquals(payload.personalRecords, harness.readPersonalRecords())
        assertEquals(payload.metaTrainingPlans, harness.readMetaTrainingPlans())
        assertEquals(payload.metaPlanItems, harness.readMetaPlanItems())
    }

    private fun assertForeignKeyIntegrity() {
        // Room connections already enforce foreign keys; opening a raw connection
        // proves it for the persisted file, matching the migration-test pattern.
        val helper = openRawConnection(harness.dbName)
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(
                    "foreign_key_check must be empty after import",
                    cursor.moveToFirst()
                )
            }
        }
        helper.close()
    }

    private fun openRawConnection(dbName: String): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

            override fun onUpgrade(
                db: androidx.sqlite.db.SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private class InMemoryBackupDocumentIo : BackupDocumentIo {
        var bytes: ByteArray = ByteArray(0)
            private set

        override suspend fun writeVerified(uri: Uri, bytes: ByteArray) {
            require(bytes.isNotEmpty()) { "backup document must not be empty" }
            this.bytes = bytes.copyOf()
        }

        override suspend fun readBytes(uri: Uri): ByteArray = bytes.copyOf()
    }

    /**
     * Real database + DAOs + transaction runner + recovery store. Only the
     * content-provider document boundary is in-memory.
     */
    private class Harness(private val context: Context) {
        val dbName = "backup-lifecycle-${UUID.randomUUID()}.db"
        val documentIo = InMemoryBackupDocumentIo()
        val recoveryDir = File(
            context.cacheDir,
            "backup-lifecycle-recovery-${UUID.randomUUID()}"
        )

        val database: IronLogDatabase = Room.databaseBuilder(
            context.applicationContext,
            IronLogDatabase::class.java,
            dbName
        ).build()

        private val transactionRunner = RoomTransactionRunner(database)
        private val recoveryStore: FileRecoveryBackupStore = FileRecoveryBackupStore(recoveryDir)

        val exerciseDao: ExerciseDao = database.exerciseDao()
        val workoutSessionDao: WorkoutSessionDao = database.workoutSessionDao()
        val workoutSetDao: WorkoutSetDao = database.workoutSetDao()
        val trainingPlanDao: TrainingPlanDao = database.trainingPlanDao()
        val metaTrainingPlanDao: MetaTrainingPlanDao = database.metaTrainingPlanDao()
        val personalRecordDao: PersonalRecordDao = database.personalRecordDao()

        val repository: BackupRepositoryImpl = BackupRepositoryImpl(
            transactionRunner = transactionRunner,
            documentIo = documentIo,
            recoveryStore = recoveryStore,
            exerciseDao = exerciseDao,
            workoutSessionDao = workoutSessionDao,
            workoutSetDao = workoutSetDao,
            trainingPlanDao = trainingPlanDao,
            metaTrainingPlanDao = metaTrainingPlanDao,
            personalRecordDao = personalRecordDao,
            buildInfo = BuildInfo(versionName = "test", versionCode = 1)
        )

        lateinit var preImportPlanName: String
        var preImportSetCount: Int = 0
        var exportedSetCount: Int = 0
        var preImportCustomExerciseIds: List<Long> = emptyList()

        suspend fun seedFullDomain() {
            // Explicit, referentially valid IDs across all eight tables.
            val squat = ExerciseEntity(
                id = CUSTOM_SQUAT_ID,
                name = "Custom Squat",
                primaryMuscleGroup = "BEINE",
                secondaryMuscleGroups = "GESAESS,CORE",
                category = "LANGHANTEL",
                isCustom = true,
                notes = "Tiefe Kniebeuge mit Pause",
                isArchived = false
            )
            val deadlift = ExerciseEntity(
                id = CUSTOM_DEADLIFT_ID,
                name = "Custom Deadlift",
                primaryMuscleGroup = "RUECKEN",
                secondaryMuscleGroups = "BEINE,GESAESS",
                category = "LANGHANTEL",
                isCustom = true,
                notes = "",
                isArchived = true
            )
            exerciseDao.replaceAll(listOf(squat, deadlift))

            val plan = TrainingPlanEntity(
                id = PLAN_ID,
                name = "Gate3 Upper Body",
                createdAt = 1000L
            )
            trainingPlanDao.insertPlan(plan)

            val metaPlan = MetaTrainingPlanEntity(
                id = META_PLAN_ID,
                name = "Gate3 Block",
                createdAt = 2000L
            )
            metaTrainingPlanDao.insertMetaPlan(metaPlan)

            val session = WorkoutSessionEntity(
                id = SESSION_ID,
                startTime = 3000L,
                endTime = 3600L,
                durationSeconds = 600L,
                name = "Gate3 Session",
                notes = "notes survived",
                planId = PLAN_ID,
                metaPlanId = META_PLAN_ID
            )
            workoutSessionDao.insert(session)

            trainingPlanDao.insertExercise(
                PlanExerciseEntity(
                    id = PLAN_EXERCISE_ID,
                    planId = PLAN_ID,
                    exerciseId = CUSTOM_SQUAT_ID,
                    orderIndex = 0,
                    supersetGroupId = 7,
                    targetSets = 4,
                    targetReps = 6,
                    targetWeightKg = 120.0
                )
            )
            metaTrainingPlanDao.insertItems(
                listOf(
                    MetaPlanItemEntity(
                        id = META_PLAN_ITEM_ID,
                        metaPlanId = META_PLAN_ID,
                        trainingPlanId = PLAN_ID,
                        orderIndex = 0
                    )
                )
            )
            workoutSetDao.insert(
                WorkoutSetEntity(
                    id = SET_1_ID,
                    sessionId = SESSION_ID,
                    exerciseId = CUSTOM_SQUAT_ID,
                    setNumber = 1,
                    reps = 5,
                    weightKg = 100.0,
                    isWarmup = true,
                    completedAt = 3100L,
                    rpe = 7.5
                )
            )
            workoutSetDao.insert(
                WorkoutSetEntity(
                    id = SET_2_ID,
                    sessionId = SESSION_ID,
                    exerciseId = CUSTOM_SQUAT_ID,
                    setNumber = 2,
                    reps = 3,
                    weightKg = 130.0,
                    isWarmup = false,
                    completedAt = 3200L,
                    rpe = 9.0
                )
            )
            personalRecordDao.replaceAll(
                listOf(
                    PersonalRecordEntity(
                        id = RECORD_ID,
                        exerciseId = CUSTOM_SQUAT_ID,
                        type = "MAX_WEIGHT",
                        value = 130.0,
                        achievedAt = 3200L
                    )
                )
            )

            exportedSetCount = 2
            preImportPlanName = MUTATED_PLAN_NAME
            preImportSetCount = 0
            preImportCustomExerciseIds = listOf(CUSTOM_SQUAT_ID, CUSTOM_DEADLIFT_ID)
        }

        suspend fun mutateAwayFromSeededState() {
            workoutSetDao.deleteAll()
            val plan = requireNotNull(trainingPlanDao.getPlanById(PLAN_ID)) {
                "seeded plan must exist before mutation"
            }
            trainingPlanDao.updatePlan(plan.copy(name = MUTATED_PLAN_NAME))
        }

        suspend fun mutateAfterImport() {
            // Add a set so the post-import state clearly differs from the
            // pre-import recovery snapshot.
            workoutSetDao.insert(
                WorkoutSetEntity(
                    id = POST_IMPORT_SET_ID,
                    sessionId = SESSION_ID,
                    exerciseId = CUSTOM_SQUAT_ID,
                    setNumber = 3,
                    reps = 2,
                    weightKg = 140.0,
                    isWarmup = false,
                    completedAt = 9999L,
                    rpe = 10.0
                )
            )
        }

        suspend fun readExercises(): List<BackupExercise> =
            exerciseDao.getAllExercisesList().map { it.toBackup() }

        suspend fun readWorkoutSessions(): List<BackupWorkoutSession> =
            workoutSessionDao.getAllSessionsList().map { it.toBackup() }

        suspend fun readWorkoutSets(): List<BackupWorkoutSet> =
            workoutSetDao.getAllSetsList().map { it.toBackup() }

        suspend fun readTrainingPlans(): List<BackupTrainingPlan> =
            trainingPlanDao.getAllPlansList().map { it.toBackup() }

        suspend fun readPlanExercises(): List<BackupPlanExercise> =
            trainingPlanDao.getAllPlanExercisesList().map { it.toBackup() }

        suspend fun readPersonalRecords(): List<BackupPersonalRecord> =
            personalRecordDao.getAllRecordsList().map { it.toBackup() }

        suspend fun readMetaTrainingPlans(): List<BackupMetaTrainingPlan> =
            metaTrainingPlanDao.getAllMetaPlansList().map { it.toBackup() }

        suspend fun readMetaPlanItems(): List<BackupMetaPlanItem> =
            metaTrainingPlanDao.getAllMetaPlanItemsList().map { it.toBackup() }

        suspend fun readPlanName(): String =
            trainingPlanDao.getAllPlansList().single().name

        suspend fun readSetCount(): Int = workoutSetDao.getAllSetsList().size

        suspend fun readCustomExerciseIds(): List<Long> =
            exerciseDao.getAllExercisesList()
                .filter { it.isCustom }
                .map { it.id }

        fun listRecoveryFiles(): List<String> =
            recoveryDir.listFiles()
                ?.filter { it.name.startsWith("recovery-") && it.name.endsWith(".json") }
                ?.map { it.name }
                ?: emptyList()

        suspend fun loadLatestBytes(): ByteArray? = recoveryStore.loadLatestBytes()

        fun close() {
            database.close()
            context.deleteDatabase(dbName)
            recoveryDir.deleteRecursively()
        }
    }

    private companion object {
        val MEMORY_URI: Uri = Uri.parse("memory://backup/gate3-export.json")
        val json = Json { ignoreUnknownKeys = false }

        const val CUSTOM_SQUAT_ID = 1001L
        const val CUSTOM_DEADLIFT_ID = 1002L
        const val PLAN_ID = 2001L
        const val META_PLAN_ID = 3001L
        const val SESSION_ID = 4001L
        const val PLAN_EXERCISE_ID = 5001L
        const val META_PLAN_ITEM_ID = 6001L
        const val SET_1_ID = 7001L
        const val SET_2_ID = 7002L
        const val RECORD_ID = 8001L
        const val POST_IMPORT_SET_ID = 7003L
        const val MUTATED_PLAN_NAME = "Gate3 Renamed Before Import"
        val RECOVERY_FILE_NAME = Regex("^recovery-(\\d+)-([0-9a-f]{64})\\.json$")
    }
}

private fun ExerciseEntity.toBackup() = BackupExercise(
    id = id,
    name = name,
    primaryMuscleGroup = primaryMuscleGroup,
    secondaryMuscleGroups = secondaryMuscleGroups,
    category = category,
    isCustom = isCustom,
    notes = notes,
    isArchived = isArchived
)

private fun WorkoutSessionEntity.toBackup() = BackupWorkoutSession(
    id = id,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    name = name,
    notes = notes,
    planId = planId,
    metaPlanId = metaPlanId
)

private fun WorkoutSetEntity.toBackup() = BackupWorkoutSet(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    isWarmup = isWarmup,
    completedAt = completedAt,
    rpe = rpe
)

private fun TrainingPlanEntity.toBackup() = BackupTrainingPlan(
    id = id,
    name = name,
    createdAt = createdAt
)

private fun PlanExerciseEntity.toBackup() = BackupPlanExercise(
    id = id,
    planId = planId,
    exerciseId = exerciseId,
    orderIndex = orderIndex,
    supersetGroupId = supersetGroupId,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg
)

private fun PersonalRecordEntity.toBackup() = BackupPersonalRecord(
    id = id,
    exerciseId = exerciseId,
    type = type,
    value = value,
    achievedAt = achievedAt
)

private fun MetaTrainingPlanEntity.toBackup() = BackupMetaTrainingPlan(
    id = id,
    name = name,
    createdAt = createdAt
)

private fun MetaPlanItemEntity.toBackup() = BackupMetaPlanItem(
    id = id,
    metaPlanId = metaPlanId,
    trainingPlanId = trainingPlanId,
    orderIndex = orderIndex
)

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
