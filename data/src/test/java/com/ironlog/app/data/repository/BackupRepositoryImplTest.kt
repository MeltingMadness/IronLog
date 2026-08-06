package com.ironlog.app.data.repository

import android.net.Uri
import com.ironlog.app.data.backup.BackupConcurrentModificationException
import com.ironlog.app.data.backup.BackupDocumentIo
import com.ironlog.app.data.backup.BackupHashMismatchException
import com.ironlog.app.data.backup.RecoveryBackupStore
import com.ironlog.app.data.backup.sha256Hex
import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.repository.RecoveryBackup
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupWorkoutSession
import com.ironlog.shared.backup.BackupWorkoutSet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class BackupRepositoryImplTest {

    @Test
    fun `export serializes one snapshot transaction before writing the document`() {
        val harness = Harness()
        val exercise = ExerciseEntity(
            id = 1L,
            name = "Bankdruecken",
            primaryMuscleGroup = "BRUST",
            secondaryMuscleGroups = "TRIZEPS",
            category = "LANGHANTEL",
            isCustom = false,
            notes = "notiz",
            isArchived = false
        )
        val session = WorkoutSessionEntity(
            id = 10L,
            startTime = 1000L,
            endTime = 2000L,
            durationSeconds = 1L,
            name = "Push",
            notes = "notes",
            planId = null,
            metaPlanId = null
        )
        val set = WorkoutSetEntity(
            id = 20L,
            sessionId = 10L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 8,
            weightKg = 80.0,
            isWarmup = false,
            completedAt = 1200L,
            rpe = 8.5
        )
        harness.stubSnapshotReads(exercise = exercise, session = session, set = set)

        runBlocking { harness.repository.exportBackup(URI) }

        val written = harness.documentIo.writtenBytes ?: throw AssertionError("no document written")
        val payload = json.decodeFromString(BackupPayloadV1.serializer(), written.decodeToString())
        assertEquals(9, payload.schemaVersion)
        assertEquals(8.5, payload.workoutSets.single().rpe)
        assertEquals("notiz", payload.exercises.single().notes)
        val txnEndIndex = harness.order.indexOf("txn-end")
        val writeIndex = harness.order.indexOf("write")
        assertTrue(
            "transaction must complete before the document write, saw $harness.order",
            txnEndIndex in 0 until writeIndex
        )
        assertEquals(1, harness.documentIo.writes)
    }

    @Test
    fun `verified export failure propagates without writing a document`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.documentIo.failWrite = IOException("disk full")

        assertThrows(IOException::class.java) {
            runBlocking { harness.repository.exportBackup(URI) }
        }

        assertNull(harness.documentIo.writtenBytes)
        assertTrue(harness.recoveryStore.saved.isEmpty())
    }

    @Test
    fun `preview reads and validates without touching the database`() {
        val harness = Harness()
        val payload = validPayload()
        harness.documentIo.bytes = json.encodeToString(BackupPayloadV1.serializer(), payload).encodeToByteArray()

        val preview = runBlocking { harness.repository.previewImport(URI) }

        assertTrue(preview.isValid)
        assertEquals(
            harness.documentIo.bytes.sha256Hex(),
            preview.sha256
        )
        assertEquals(1, preview.counts.exercises)
        assertEquals(1, preview.counts.workoutSessions)
        assertEquals(1, preview.counts.workoutSets)
        assertTrue(harness.transactionRunner.events.isEmpty())
        coVerify(exactly = 0) { harness.exerciseDao.getAllExercisesList() }
        coVerify(exactly = 0) { harness.exerciseDao.deleteAll() }
    }

    @Test
    fun `import saves recovery snapshot then runs guard before deletes in one transaction`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        harness.documentIo.bytes = json.encodeToString(BackupPayloadV1.serializer(), validPayload()).encodeToByteArray()
        val expectedSha256 = harness.documentIo.bytes.sha256Hex()

        runBlocking { harness.repository.importBackup(URI, expectedSha256) }

        assertEquals(1, harness.recoveryStore.saved.size)
        val events = harness.transactionRunner.events
        val secondTxnStart = events.lastIndexOf("txn-start")
        val secondTxnEnd = events.lastIndexOf("txn-end")
        val guardRead = events.subList(secondTxnStart, secondTxnEnd).indexOf("read-exercises")
        val delete = events.subList(secondTxnStart, secondTxnEnd).indexOf("delete-records")
        assertTrue("guard read must precede deletes", guardRead in 0 until delete)
        coVerify(exactly = 1) { harness.exerciseDao.deleteAll() }
        coVerify(exactly = 1) { harness.exerciseDao.replaceAll(any()) }
    }

    @Test
    fun `recovery snapshot failure produces zero deletes`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        harness.documentIo.bytes = json.encodeToString(BackupPayloadV1.serializer(), validPayload()).encodeToByteArray()
        harness.recoveryStore.failSave = IOException("no space")

        assertThrows(IOException::class.java) {
            runBlocking {
                harness.repository.importBackup(URI, harness.documentIo.bytes.sha256Hex())
            }
        }

        assertTrue(harness.transactionRunner.events.none { it.startsWith("delete-") })
        coVerify(exactly = 0) { harness.exerciseDao.deleteAll() }
        coVerify(exactly = 0) { harness.exerciseDao.replaceAll(any()) }
    }

    @Test
    fun `source hash change between preview and import is rejected without mutation`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        val original = json.encodeToString(BackupPayloadV1.serializer(), validPayload()).encodeToByteArray()
        harness.documentIo.bytes = original
        val preview = runBlocking { harness.repository.previewImport(URI) }

        harness.documentIo.bytes = json.encodeToString(
            BackupPayloadV1.serializer(),
            validPayload().copy(exportedAtEpochMillis = 999L)
        ).encodeToByteArray()

        val error = assertThrows(BackupHashMismatchException::class.java) {
            runBlocking { harness.repository.importBackup(URI, preview.sha256) }
        }

        assertEquals(preview.sha256, error.expectedSha256)
        assertTrue(harness.recoveryStore.saved.isEmpty())
        assertTrue(harness.transactionRunner.events.isEmpty())
        coVerify(exactly = 0) { harness.exerciseDao.deleteAll() }
    }

    @Test
    fun `repository serializes concurrent import calls`() {
        val harness = Harness(slowDocumentIo = true)
        harness.stubSnapshotReads()
        harness.documentIo.bytes = json.encodeToString(BackupPayloadV1.serializer(), validPayload()).encodeToByteArray()
        val expectedSha256 = harness.documentIo.bytes.sha256Hex()

        runBlocking {
            val jobs = List(5) {
                launch {
                    runCatching {
                        harness.repository.importBackup(URI, expectedSha256)
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        assertEquals(1, harness.documentIo.maxActive.get())
    }

    @Test
    fun `restore returns null when no recovery snapshot exists`() {
        val harness = Harness()

        val result = runBlocking { harness.repository.restoreLatestRecovery() }

        assertNull(result)
    }

    @Test
    fun `restore saves a fresh recovery snapshot before replacing current state`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        val recoveryBytes = json.encodeToString(
            BackupPayloadV1.serializer(),
            validPayload()
        ).encodeToByteArray()
        harness.recoveryStore.saved += recoveryBytes
        harness.recoveryStore.latestMeta = RecoveryBackup(
            timestampMillis = 1L,
            sha256 = recoveryBytes.sha256Hex(),
            sizeBytes = recoveryBytes.size.toLong()
        )

        val restored = runBlocking { harness.repository.restoreLatestRecovery() }

        assertTrue(restored != null)
        assertEquals(2, harness.recoveryStore.saved.size)
        coVerify(exactly = 1) { harness.exerciseDao.deleteAll() }
        coVerify(exactly = 2) { harness.exerciseDao.getAllExercisesList() }
    }

    @Test
    fun `database change between recovery and guard snapshot aborts with zero deletes and removes recovery`() {
        val harness = Harness()
        var snapshotReadCount = 0
        val firstSnapshotExercise = ExerciseEntity(
            id = 1L,
            name = "Bankdruecken",
            primaryMuscleGroup = "BRUST",
            secondaryMuscleGroups = "TRIZEPS",
            category = "LANGHANTEL",
            isCustom = false
        )
        val secondSnapshotExercise = firstSnapshotExercise.copy(name = "Changed after snapshot")
        coEvery { harness.exerciseDao.getAllExercisesList() } answers {
            snapshotReadCount += 1
            if (snapshotReadCount == 1) {
                listOf(firstSnapshotExercise)
            } else {
                listOf(secondSnapshotExercise)
            }
        }
        coEvery { harness.workoutSessionDao.getAllSessionsList() } returns emptyList()
        coEvery { harness.workoutSetDao.getAllSetsList() } returns emptyList()
        coEvery { harness.trainingPlanDao.getAllPlansList() } returns emptyList()
        coEvery { harness.trainingPlanDao.getAllPlanExercisesList() } returns emptyList()
        coEvery { harness.personalRecordDao.getAllRecordsList() } returns emptyList()
        coEvery { harness.metaTrainingPlanDao.getAllMetaPlansList() } returns emptyList()
        coEvery { harness.metaTrainingPlanDao.getAllMetaPlanItemsList() } returns emptyList()
        harness.stubMutations()
        harness.documentIo.bytes = json.encodeToString(BackupPayloadV1.serializer(), validPayload()).encodeToByteArray()

        val error = assertThrows(BackupConcurrentModificationException::class.java) {
            runBlocking { harness.repository.importBackup(URI, harness.documentIo.bytes.sha256Hex()) }
        }

        assertTrue(error.message.orEmpty().contains("no rows were deleted"))
        assertTrue(harness.transactionRunner.events.none { it.startsWith("delete-") })
        coVerify(exactly = 0) { harness.exerciseDao.deleteAll() }
        coVerify(exactly = 0) { harness.exerciseDao.replaceAll(any()) }
        assertEquals(1, harness.recoveryStore.deleted.size)
        assertEquals(1, harness.recoveryStore.saved.size)
        assertEquals(
            harness.recoveryStore.saved.single().sha256Hex(),
            harness.recoveryStore.deleted.single().sha256
        )
        assertEquals(2, snapshotReadCount)
    }

    @Test
    fun `reset deletes user data and custom exercises in one transaction without wiping the catalog`() {
        val harness = Harness()
        harness.stubMutations()

        runBlocking { harness.repository.resetUserData() }

        coVerify(exactly = 1) { harness.personalRecordDao.deleteAll() }
        coVerify(exactly = 1) { harness.workoutSetDao.deleteAll() }
        coVerify(exactly = 1) { harness.metaTrainingPlanDao.deleteAllMetaPlanItems() }
        coVerify(exactly = 1) { harness.trainingPlanDao.deleteAllPlanExercises() }
        coVerify(exactly = 1) { harness.workoutSessionDao.deleteAll() }
        coVerify(exactly = 1) { harness.metaTrainingPlanDao.deleteAllMetaPlans() }
        coVerify(exactly = 1) { harness.trainingPlanDao.deleteAllPlans() }
        coVerify(exactly = 1) { harness.exerciseDao.deleteAllCustomExercises() }
        coVerify(exactly = 0) { harness.exerciseDao.deleteAll() }
        val events = harness.transactionRunner.events
        assertEquals("txn-start", events.first())
        assertEquals("txn-end", events.last())
        assertTrue("delete-custom-exercises" in events)
    }

    private fun validPayload(): BackupPayloadV1 = BackupPayloadV1(
        formatVersion = 1,
        schemaVersion = 9,
        appVersion = "1.0",
        exportedAtEpochMillis = 42L,
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
                completedAt = 1200L,
                rpe = 8.5
            )
        ),
        trainingPlans = emptyList(),
        planExercises = emptyList(),
        personalRecords = emptyList(),
        metaTrainingPlans = emptyList(),
        metaPlanItems = emptyList()
    )

    private class Harness(
        slowDocumentIo: Boolean = false
    ) {
        val events = mutableListOf<String>()
        val order = mutableListOf<String>()
        val transactionRunner = FakeTransactionRunner(events, order)
        val documentIo = FakeDocumentIo().apply {
            readDelayMillis = if (slowDocumentIo) 20 else 0
            orderRef = this@Harness.order
        }
        val recoveryStore = FakeRecoveryStore()
        val exerciseDao = mockk<ExerciseDao>(relaxed = true)
        val workoutSessionDao = mockk<WorkoutSessionDao>(relaxed = true)
        val workoutSetDao = mockk<WorkoutSetDao>(relaxed = true)
        val trainingPlanDao = mockk<TrainingPlanDao>(relaxed = true)
        val metaTrainingPlanDao = mockk<MetaTrainingPlanDao>(relaxed = true)
        val personalRecordDao = mockk<PersonalRecordDao>(relaxed = true)
        val repository = BackupRepositoryImpl(
            transactionRunner = transactionRunner,
            documentIo = documentIo,
            recoveryStore = recoveryStore,
            exerciseDao = exerciseDao,
            workoutSessionDao = workoutSessionDao,
            workoutSetDao = workoutSetDao,
            trainingPlanDao = trainingPlanDao,
            metaTrainingPlanDao = metaTrainingPlanDao,
            personalRecordDao = personalRecordDao,
            buildInfo = BuildInfo(versionName = "1.0", versionCode = 1)
        )

        fun stubSnapshotReads(
            exercise: ExerciseEntity? = null,
            session: WorkoutSessionEntity? = null,
            set: WorkoutSetEntity? = null
        ) {
            coEvery { exerciseDao.getAllExercisesList() } answers {
                events += "read-exercises"
                listOfNotNull(exercise)
            }
            coEvery { workoutSessionDao.getAllSessionsList() } answers {
                events += "read-sessions"
                listOfNotNull(session)
            }
            coEvery { workoutSetDao.getAllSetsList() } answers {
                events += "read-sets"
                listOfNotNull(set)
            }
            coEvery { trainingPlanDao.getAllPlansList() } answers {
                events += "read-plans"
                emptyList()
            }
            coEvery { trainingPlanDao.getAllPlanExercisesList() } answers {
                events += "read-plan-exercises"
                emptyList()
            }
            coEvery { personalRecordDao.getAllRecordsList() } answers {
                events += "read-records"
                emptyList()
            }
            coEvery { metaTrainingPlanDao.getAllMetaPlansList() } answers {
                events += "read-meta-plans"
                emptyList()
            }
            coEvery { metaTrainingPlanDao.getAllMetaPlanItemsList() } answers {
                events += "read-meta-items"
                emptyList()
            }
        }

        fun stubMutations() {
            coEvery { personalRecordDao.deleteAll() } answers {
                events += "delete-records"
                Unit
            }
            coEvery { workoutSetDao.deleteAll() } answers {
                events += "delete-sets"
                Unit
            }
            coEvery { metaTrainingPlanDao.deleteAllMetaPlanItems() } answers {
                events += "delete-meta-items"
                Unit
            }
            coEvery { trainingPlanDao.deleteAllPlanExercises() } answers {
                events += "delete-plan-exercises"
                Unit
            }
            coEvery { workoutSessionDao.deleteAll() } answers {
                events += "delete-sessions"
                Unit
            }
            coEvery { metaTrainingPlanDao.deleteAllMetaPlans() } answers {
                events += "delete-meta-plans"
                Unit
            }
            coEvery { trainingPlanDao.deleteAllPlans() } answers {
                events += "delete-plans"
                Unit
            }
            coEvery { exerciseDao.deleteAll() } answers {
                events += "delete-exercises"
                Unit
            }
            coEvery { exerciseDao.deleteAllCustomExercises() } answers {
                events += "delete-custom-exercises"
                Unit
            }
            coEvery { exerciseDao.replaceAll(any()) } answers {
                events += "insert-exercises"
                Unit
            }
        }
    }

    private class FakeTransactionRunner(
        val events: MutableList<String>,
        private val order: MutableList<String>
    ) : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            events += "txn-start"
            order += "txn-start"
            val result = block()
            events += "txn-end"
            order += "txn-end"
            return result
        }
    }

    private open class FakeDocumentIo : BackupDocumentIo {
        lateinit var orderRef: MutableList<String>
        var bytes: ByteArray = ByteArray(0)
        var writtenBytes: ByteArray? = null
        var failWrite: Throwable? = null
        var writes = 0
        var readDelayMillis = 0L
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        override suspend fun writeVerified(uri: Uri, bytes: ByteArray) {
            failWrite?.let { throw it }
            writtenBytes = bytes
            writes += 1
            orderRef += "write"
        }

        override suspend fun readBytes(uri: Uri): ByteArray {
            val current = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, current) }
            if (readDelayMillis > 0) delay(readDelayMillis)
            active.decrementAndGet()
            orderRef += "read"
            return bytes
        }
    }

    private class FakeRecoveryStore : RecoveryBackupStore {
        var failSave: Throwable? = null
        val saved = mutableListOf<ByteArray>()
        val deleted = mutableListOf<RecoveryBackup>()
        var latestMeta: RecoveryBackup? = null

        override suspend fun latest(): RecoveryBackup? = latestMeta

        override suspend fun save(bytes: ByteArray): RecoveryBackup {
            failSave?.let { throw it }
            saved += bytes
            val meta = RecoveryBackup(
                timestampMillis = saved.size.toLong(),
                sha256 = bytes.sha256Hex(),
                sizeBytes = bytes.size.toLong()
            )
            latestMeta = meta
            return meta
        }

        override suspend fun loadLatestBytes(): ByteArray? = saved.lastOrNull()

        override suspend fun delete(backup: RecoveryBackup) {
            deleted += backup
            latestMeta = null
        }
    }

    private companion object {
        val URI = mockk<Uri>()
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
