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
import com.ironlog.app.data.local.dao.ProgressionDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.MetaPlanSkipEntity
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.ProgressionConfigColumns
import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.ProgressionTargetColumns
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.repository.RecoveryBackup
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupMetaPlanItem
import com.ironlog.shared.backup.BackupMetaPlanSkip
import com.ironlog.shared.backup.BackupMetaTrainingPlan
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupPlanExercise
import com.ironlog.shared.backup.BackupProgressionConfig
import com.ironlog.shared.backup.BackupProgressionSuggestion
import com.ironlog.shared.backup.BackupProgressionTarget
import com.ironlog.shared.backup.BackupTrainingPlan
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.backup.BackupWorkoutSession
import com.ironlog.shared.backup.BackupWorkoutSet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
            planId = 5L,
            metaPlanId = null
        )
        val sets = progressionSetEntities()
        val skip = MetaPlanSkipEntity(
            id = 30L,
            metaPlanId = 40L,
            trainingPlanId = 50L,
            skippedAt = 1500L
        )
        harness.stubSnapshotReads(
            exercise = exercise,
            session = session,
            sets = sets,
            trainingPlan = progressionPlanEntity(),
            planExercise = progressionPlanExerciseEntity(),
            target = progressionTargetEntity(),
            suggestion = progressionSuggestionEntity(),
            skip = skip
        )

        runBlocking { harness.repository.exportBackup(URI) }

        val written = harness.documentIo.writtenBytes ?: throw AssertionError("no document written")
        val payload = json.decodeFromString(BackupPayloadV1.serializer(), written.decodeToString())
        assertEquals(11, payload.schemaVersion)
        assertEquals(8.5, payload.workoutSets.single().rpe)
        assertEquals(30L, payload.workoutSets.single().planTargetSnapshotId)
        assertEquals("notiz", payload.exercises.single().notes)
        assertEquals("LINEAR", payload.planExercises.single().progression.scheme)
        assertEquals(listOf(20L), payload.progressionSuggestions.single().countedSetIds)
        assertEquals(
            listOf("actualWorkSets", "targetSets"),
            payload.progressionSuggestions.single().reasonArguments.keys.toList()
        )
        assertEquals(1, payload.workoutPlanTargets.size)
        assertEquals(1, payload.progressionSuggestions.size)
        assertEquals(
            BackupMetaPlanSkip(id = 30L, metaPlanId = 40L, trainingPlanId = 50L, skippedAt = 1500L),
            payload.metaPlanSkips.single()
        )
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
    fun `preview and verified import preserve progression counts mapping and foreign key order`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        val payload = validProgressionPayload()
        harness.documentIo.bytes = json.encodeToString(
            BackupPayloadV1.serializer(),
            payload
        ).encodeToByteArray()

        val preview = runBlocking { harness.repository.previewImport(URI) }

        assertTrue(preview.isValid)
        assertEquals(1, preview.counts.workoutPlanTargets)
        assertEquals(1, preview.counts.progressionSuggestions)

        runBlocking { harness.repository.importBackup(URI, preview.sha256) }

        coVerifyOrder {
            harness.progressionDao.deleteAllSuggestions()
            harness.workoutSetDao.deleteAll()
            harness.progressionDao.deleteAllTargets()
            harness.progressionDao.replaceAllTargets(any())
            harness.workoutSetDao.replaceAll(any())
            harness.progressionDao.replaceAllSuggestions(any())
        }
        coVerify(exactly = 1) {
            harness.progressionDao.replaceAllSuggestions(
                match { suggestions ->
                    suggestions.single().reasonArgumentsJson ==
                        "{\"actualWorkSets\":3.0,\"targetSets\":3.0}" &&
                        suggestions.single().countedSetIdsJson == "[20,21,22]"
                }
            )
        }
    }

    @Test
    fun `progression validation failure performs no progression mutation`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        val valid = validProgressionPayload()
        val invalid = valid.copy(
            progressionSuggestions = valid.progressionSuggestions.map {
                it.copy(countedSetIds = listOf(999L))
            }
        )
        harness.documentIo.bytes = json.encodeToString(
            BackupPayloadV1.serializer(),
            invalid
        ).encodeToByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                harness.repository.importBackup(URI, harness.documentIo.bytes.sha256Hex())
            }
        }

        coVerify(exactly = 0) { harness.progressionDao.deleteAllSuggestions() }
        coVerify(exactly = 0) { harness.progressionDao.deleteAllTargets() }
        coVerify(exactly = 0) { harness.progressionDao.replaceAllTargets(any()) }
        coVerify(exactly = 0) { harness.progressionDao.replaceAllSuggestions(any()) }
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
    fun `import deletes skips before referenced plans and inserts them after plans exist`() {
        val harness = Harness()
        harness.stubSnapshotReads()
        harness.stubMutations()
        val payload = validPayload().copy(
            trainingPlans = listOf(
                BackupTrainingPlan(id = 5L, name = "Push", createdAt = 1000L)
            ),
            metaTrainingPlans = listOf(
                BackupMetaTrainingPlan(
                    id = 40L,
                    name = "Meta",
                    createdAt = 1000L
                )
            ),
            metaPlanItems = listOf(
                BackupMetaPlanItem(
                    id = 41L,
                    metaPlanId = 40L,
                    trainingPlanId = 5L,
                    orderIndex = 0
                )
            ),
            metaPlanSkips = listOf(
                BackupMetaPlanSkip(id = 42L, metaPlanId = 40L, trainingPlanId = 5L, skippedAt = 1500L)
            )
        )
        harness.documentIo.bytes = json.encodeToString(BackupPayloadV1.serializer(), payload).encodeToByteArray()

        runBlocking { harness.repository.importBackup(URI, harness.documentIo.bytes.sha256Hex()) }

        val events = harness.transactionRunner.events
        val deleteSkips = events.indexOf("delete-meta-skips")
        val deleteItems = events.indexOf("delete-meta-items")
        val deleteMetaPlans = events.indexOf("delete-meta-plans")
        val deletePlans = events.indexOf("delete-plans")
        assertTrue("skips must be deleted before meta plan items", deleteSkips in 0 until deleteItems)
        assertTrue("items must be deleted before meta plans", deleteItems in 0 until deleteMetaPlans)
        assertTrue("meta plans must be deleted before training plans", deleteMetaPlans in 0 until deletePlans)

        val insertPlans = events.indexOf("insert-plans")
        val insertMetaPlans = events.indexOf("insert-meta-plans")
        val insertSkips = events.indexOf("insert-meta-skips")
        assertTrue("plans must be inserted before skips", insertPlans in 0 until insertSkips)
        assertTrue("meta plans must be inserted before skips", insertMetaPlans in 0 until insertSkips)
        coVerify(exactly = 1) { harness.metaTrainingPlanDao.deleteAllMetaPlanSkips() }
        coVerify(exactly = 1) { harness.metaTrainingPlanDao.replaceAllMetaPlanSkips(any()) }
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
        coVerify(exactly = 0) { harness.progressionDao.deleteAllSuggestions() }
        coVerify(exactly = 0) { harness.progressionDao.deleteAllTargets() }
        coVerify(exactly = 0) { harness.progressionDao.replaceAllTargets(any()) }
        coVerify(exactly = 0) { harness.progressionDao.replaceAllSuggestions(any()) }
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
        coVerify(exactly = 0) { harness.progressionDao.deleteAllSuggestions() }
        coVerify(exactly = 0) { harness.progressionDao.deleteAllTargets() }
        coVerify(exactly = 0) { harness.progressionDao.replaceAllTargets(any()) }
        coVerify(exactly = 0) { harness.progressionDao.replaceAllSuggestions(any()) }
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
        coEvery { harness.metaTrainingPlanDao.getAllMetaPlanSkipsList() } returns emptyList()
        coEvery { harness.progressionDao.getAllTargets() } returns emptyList()
        coEvery { harness.progressionDao.getAllSuggestions() } returns emptyList()
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
        coVerify(exactly = 1) { harness.progressionDao.deleteAllSuggestions() }
        coVerify(exactly = 1) { harness.progressionDao.deleteAllTargets() }
        coVerify(exactly = 1) { harness.metaTrainingPlanDao.deleteAllMetaPlanSkips() }
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
        coVerifyOrder {
            harness.progressionDao.deleteAllSuggestions()
            harness.workoutSetDao.deleteAll()
            harness.progressionDao.deleteAllTargets()
        }
    }

    private fun validPayload(): BackupPayloadV1 = BackupPayloadV1(
        formatVersion = 1,
        schemaVersion = 11,
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
        metaPlanItems = emptyList(),
        metaPlanSkips = emptyList()
    )

    private fun validProgressionPayload(): BackupPayloadV1 {
        val progression = backupProgressionConfig()
        val target = BackupProgressionTarget(sets = 3, reps = 8, weightKg = 80.0)
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
            workoutSets = listOf(20L, 21L, 22L).mapIndexed { index, id ->
                BackupWorkoutSet(
                    id = id,
                    sessionId = 10L,
                    exerciseId = 1L,
                    setNumber = index + 1,
                    reps = 8,
                    weightKg = 80.0,
                    isWarmup = false,
                    completedAt = 1200L + index,
                    rpe = 8.5,
                    planTargetSnapshotId = 30L
                )
            },
            trainingPlans = listOf(BackupTrainingPlan(id = 5L, name = "Push", createdAt = 1000L)),
            planExercises = listOf(
                BackupPlanExercise(
                    id = 15L,
                    planId = 5L,
                    exerciseId = 1L,
                    orderIndex = 0,
                    targetSets = 3,
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
                    reasonArguments = linkedMapOf("targetSets" to 3.0, "actualWorkSets" to 3.0),
                    countedSetIds = listOf(20L, 21L, 22L),
                    streakEffect = "INCREMENT",
                    suggestedTarget = target.copy(weightKg = 82.5),
                    status = "PENDING",
                    createdAtEpochMillis = 2200L
                )
            )
        )
    }

    private fun backupProgressionConfig() = BackupProgressionConfig(
        scheme = "LINEAR",
        incrementValue = 2.5,
        incrementUnit = "METRIC",
        incrementKg = 2.5,
        stallThreshold = 2,
        backoffPercent = 10.0,
        ruleRevision = 1
    )

    private fun progressionConfigColumns() = ProgressionConfigColumns(
        scheme = "LINEAR",
        incrementValue = 2.5,
        incrementUnit = "METRIC",
        incrementKg = 2.5,
        stallThreshold = 2,
        backoffPercent = 10.0,
        ruleRevision = 1
    )

    private fun progressionPlanEntity() = TrainingPlanEntity(
        id = 5L,
        name = "Push",
        createdAt = 1000L
    )

    private fun progressionPlanExerciseEntity() = PlanExerciseEntity(
        id = 15L,
        planId = 5L,
        exerciseId = 1L,
        orderIndex = 0,
        targetSets = 1,
        targetReps = 8,
        targetWeightKg = 80.0,
        progression = progressionConfigColumns()
    )

    private fun progressionTargetEntity() = WorkoutPlanTargetEntity(
        id = 30L,
        sessionId = 10L,
        planId = 5L,
        exerciseId = 1L,
        orderIndex = 0,
        supersetGroupId = null,
        target = ProgressionTargetColumns(sets = 1, reps = 8, weightKg = 80.0),
        progression = progressionConfigColumns()
    )

    private fun progressionSuggestionEntity() = ProgressionSuggestionEntity(
        id = 40L,
        sourceSessionId = 10L,
        sourceTargetSnapshotId = 30L,
        planId = 5L,
        exerciseId = 1L,
        orderIndex = 0,
        supersetGroupId = null,
        sourceTarget = ProgressionTargetColumns(sets = 1, reps = 8, weightKg = 80.0),
        sourceProgression = progressionConfigColumns(),
        outcomeType = "PROPOSE_CHANGE",
        reasonCode = "LOAD_ADVANCED",
        reasonArgumentsJson = "{\"actualWorkSets\":1.0,\"targetSets\":1.0}",
        countedSetIdsJson = "[20]",
        streakEffect = "INCREMENT",
        suggestedTarget = ProgressionTargetColumns(sets = 1, reps = 8, weightKg = 82.5),
        status = "PENDING",
        wasEdited = false,
        finalTarget = null,
        createdAtEpochMillis = 2200L,
        decidedAtEpochMillis = null
    )

    private fun progressionSetEntities() = listOf(
        WorkoutSetEntity(
            id = 20L,
            sessionId = 10L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 8,
            weightKg = 80.0,
            isWarmup = false,
            completedAt = 1200L,
            rpe = 8.5,
            planTargetSnapshotId = 30L
        )
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
        val progressionDao = mockk<ProgressionDao>(relaxed = true)
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
            progressionDao = progressionDao,
            buildInfo = BuildInfo(versionName = "1.0", versionCode = 1)
        )

        fun stubSnapshotReads(
            exercise: ExerciseEntity? = null,
            session: WorkoutSessionEntity? = null,
            set: WorkoutSetEntity? = null,
            sets: List<WorkoutSetEntity> = listOfNotNull(set),
            trainingPlan: TrainingPlanEntity? = null,
            planExercise: PlanExerciseEntity? = null,
            target: WorkoutPlanTargetEntity? = null,
            suggestion: ProgressionSuggestionEntity? = null,
            skip: MetaPlanSkipEntity? = null
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
                sets
            }
            coEvery { trainingPlanDao.getAllPlansList() } answers {
                events += "read-plans"
                listOfNotNull(trainingPlan)
            }
            coEvery { trainingPlanDao.getAllPlanExercisesList() } answers {
                events += "read-plan-exercises"
                listOfNotNull(planExercise)
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
            coEvery { metaTrainingPlanDao.getAllMetaPlanSkipsList() } answers {
                events += "read-meta-skips"
                listOfNotNull(skip)
            }
            coEvery { progressionDao.getAllTargets() } answers {
                events += "read-targets"
                listOfNotNull(target)
            }
            coEvery { progressionDao.getAllSuggestions() } answers {
                events += "read-suggestions"
                listOfNotNull(suggestion)
            }
        }

        fun stubMutations() {
            coEvery { progressionDao.deleteAllSuggestions() } answers {
                events += "delete-suggestions"
                Unit
            }
            coEvery { personalRecordDao.deleteAll() } answers {
                events += "delete-records"
                Unit
            }
            coEvery { workoutSetDao.deleteAll() } answers {
                events += "delete-sets"
                Unit
            }
            coEvery { progressionDao.deleteAllTargets() } answers {
                events += "delete-targets"
                Unit
            }
            coEvery { metaTrainingPlanDao.deleteAllMetaPlanItems() } answers {
                events += "delete-meta-items"
                Unit
            }
            coEvery { metaTrainingPlanDao.deleteAllMetaPlanSkips() } answers {
                events += "delete-meta-skips"
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
            coEvery { trainingPlanDao.replaceAllPlans(any()) } answers {
                events += "insert-plans"
                Unit
            }
            coEvery { metaTrainingPlanDao.replaceAllMetaPlans(any()) } answers {
                events += "insert-meta-plans"
                Unit
            }
            coEvery { metaTrainingPlanDao.replaceAllMetaPlanSkips(any()) } answers {
                events += "insert-meta-skips"
                Unit
            }
            coEvery { metaTrainingPlanDao.replaceAllItems(any()) } answers {
                events += "insert-meta-items"
                Unit
            }
            coEvery { progressionDao.replaceAllTargets(any()) } answers {
                events += "insert-targets"
                Unit
            }
            coEvery { workoutSetDao.replaceAll(any()) } answers {
                events += "insert-sets"
                Unit
            }
            coEvery { progressionDao.replaceAllSuggestions(any()) } answers {
                events += "insert-suggestions"
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
