package com.ironlog.app.data.repository

import android.net.Uri
import com.ironlog.app.data.backup.BackupConcurrentModificationException
import com.ironlog.app.data.backup.BackupDocumentIo
import com.ironlog.app.data.backup.BackupExercise
import com.ironlog.app.data.backup.BackupHashMismatchException
import com.ironlog.app.data.backup.BackupMetaPlanItem
import com.ironlog.app.data.backup.BackupMetaPlanSkip
import com.ironlog.app.data.backup.BackupMetaTrainingPlan
import com.ironlog.app.data.backup.BackupPayloadValidator
import com.ironlog.app.data.backup.BackupPayloadV1
import com.ironlog.app.data.backup.BackupPersonalRecord
import com.ironlog.app.data.backup.BackupPlanExercise
import com.ironlog.app.data.backup.BackupProgressionConfig
import com.ironlog.app.data.backup.BackupProgressionSuggestion
import com.ironlog.app.data.backup.BackupProgressionTarget
import com.ironlog.app.data.backup.BackupSnapshot
import com.ironlog.app.data.backup.BackupTrainingPlan
import com.ironlog.app.data.backup.BackupWorkoutSession
import com.ironlog.app.data.backup.BackupWorkoutPlanTarget
import com.ironlog.app.data.backup.BackupWorkoutSet
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
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaPlanSkipEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.ProgressionConfigColumns
import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.ProgressionTargetColumns
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.data.seed.ExerciseSeedData
import com.ironlog.app.domain.repository.BackupContentCounts
import com.ironlog.app.domain.repository.BackupImportPreview
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.RecoveryBackup
import com.ironlog.app.domain.util.BuildInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class BackupRepositoryImpl(
    private val transactionRunner: TransactionRunner,
    private val documentIo: BackupDocumentIo,
    private val recoveryStore: RecoveryBackupStore,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val workoutSetDao: WorkoutSetDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val metaTrainingPlanDao: MetaTrainingPlanDao,
    private val personalRecordDao: PersonalRecordDao,
    private val progressionDao: ProgressionDao,
    private val buildInfo: BuildInfo
) : BackupRepository {

    private val mutex = Mutex()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    private val progressionJson = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    override suspend fun exportBackup(uri: Uri) {
        mutex.withLock {
            val snapshot = readSnapshot()
            val bytes = exportBytes(snapshot)
            documentIo.writeVerified(uri, bytes)
        }
    }

    override suspend fun previewImport(uri: Uri): BackupImportPreview {
        return mutex.withLock {
            val bytes = documentIo.readBytes(uri)
            val payload = decodePayload(bytes)
            val validation = BackupPayloadValidator.validate(payload, SCHEMA_VERSION)
            BackupImportPreview(
                sha256 = bytes.sha256Hex(),
                schemaVersion = payload.schemaVersion,
                appVersion = payload.appVersion,
                exportedAtEpochMillis = payload.exportedAtEpochMillis,
                counts = payload.toCounts(),
                validationErrors = validation.errors
            )
        }
    }

    override suspend fun importBackup(uri: Uri, expectedSha256: String) {
        mutex.withLock {
            val bytes = documentIo.readBytes(uri)
            importVerifiedBytes(bytes, expectedSha256)
        }
    }

    override suspend fun latestRecovery(): RecoveryBackup? {
        return mutex.withLock { recoveryStore.latest() }
    }

    override suspend fun restoreLatestRecovery(): RecoveryBackup? {
        return mutex.withLock {
            val recovery = recoveryStore.latest() ?: return null
            val bytes = recoveryStore.loadLatestBytes() ?: return null
            importVerifiedBytes(bytes, recovery.sha256)
            recovery
        }
    }

    override suspend fun resetUserData() {
        mutex.withLock {
            transactionRunner.runInTransaction {
                progressionDao.deleteAllSuggestions()
                personalRecordDao.deleteAll()
                workoutSetDao.deleteAll()
                progressionDao.deleteAllTargets()
                metaTrainingPlanDao.deleteAllMetaPlanSkips()
                metaTrainingPlanDao.deleteAllMetaPlanItems()
                trainingPlanDao.deleteAllPlanExercises()
                workoutSessionDao.deleteAll()
                metaTrainingPlanDao.deleteAllMetaPlans()
                trainingPlanDao.deleteAllPlans()
                exerciseDao.deleteAllCustomExercises()
            }
        }
    }

    private suspend fun importVerifiedBytes(bytes: ByteArray, expectedSha256: String): RecoveryBackup {
        val actualSha256 = bytes.sha256Hex()
        if (actualSha256 != expectedSha256) {
            throw BackupHashMismatchException(expectedSha256, actualSha256)
        }

        val payload = decodePayload(bytes)
        validateOrThrow(payload)
        val importData = payload.toImportData()

        // Verified snapshot of the exact state we are about to delete. If this
        // fails, no transaction has started and therefore zero rows are deleted.
        val recoveryBytes = canonicalBytes(readSnapshot())
        val recovery = recoveryStore.save(recoveryBytes)

        try {
            transactionRunner.runInTransaction {
                // Guard against a writer changing the DB between the recovery
                // snapshot and this destructive transaction.
                val currentHash = canonicalBytes(readSnapshotBlock()).sha256Hex()
                if (currentHash != recovery.sha256) {
                    throw BackupConcurrentModificationException()
                }
                deleteAllInOrder()
                insertAllInOrder(importData)
            }
        } catch (error: BackupConcurrentModificationException) {
            // The just-written snapshot no longer represents current state.
            runCatching { recoveryStore.delete(recovery) }
            throw error
        }

        return recovery
    }

    private suspend fun readSnapshot(): BackupSnapshot =
        transactionRunner.runInTransaction { readSnapshotBlock() }

    private suspend fun readSnapshotBlock(): BackupSnapshot = BackupSnapshot(
        exercises = exerciseDao.getAllExercisesList().map { it.toBackup() },
        workoutSessions = workoutSessionDao.getAllSessionsList().map { it.toBackup() },
        workoutSets = workoutSetDao.getAllSetsList().map { it.toBackupWorkoutSet() },
        trainingPlans = trainingPlanDao.getAllPlansList().map { it.toBackup() },
        planExercises = trainingPlanDao.getAllPlanExercisesList().map { it.toBackup() },
        personalRecords = personalRecordDao.getAllRecordsList().map { it.toBackup() },
        metaTrainingPlans = metaTrainingPlanDao.getAllMetaPlansList().map { it.toBackup() },
        metaPlanItems = metaTrainingPlanDao.getAllMetaPlanItemsList().map { it.toBackup() },
        metaPlanSkips = metaTrainingPlanDao.getAllMetaPlanSkipsList().map { it.toBackup() },
        workoutPlanTargets = progressionDao.getAllTargets().map { it.toBackup() },
        progressionSuggestions = progressionDao.getAllSuggestions().map { it.toBackup() }
    )

    private suspend fun canonicalBytes(snapshot: BackupSnapshot): ByteArray =
        encodePayload(snapshot.canonicalPayload(SCHEMA_VERSION))

    private suspend fun exportBytes(snapshot: BackupSnapshot): ByteArray =
        encodePayload(
            snapshot.toExportPayload(
                schemaVersion = SCHEMA_VERSION,
                appVersion = buildInfo.versionName,
                exportedAtEpochMillis = System.currentTimeMillis()
            )
        )

    private suspend fun encodePayload(payload: BackupPayloadV1): ByteArray =
        withContext(Dispatchers.IO) {
            json.encodeToString(BackupPayloadV1.serializer(), payload).encodeToByteArray()
        }

    private suspend fun decodePayload(bytes: ByteArray): BackupPayloadV1 =
        withContext(Dispatchers.IO) {
            json.decodeFromString(BackupPayloadV1.serializer(), bytes.decodeToString())
        }

    private fun validateOrThrow(payload: BackupPayloadV1) {
        val validation = BackupPayloadValidator.validate(payload, SCHEMA_VERSION)
        require(validation.isValid) {
            "Backup validation failed: ${validation.errors.joinToString("; ")}"
        }
    }

    private fun BackupPayloadV1.toCounts(): BackupContentCounts = BackupContentCounts(
        exercises = exercises.size,
        workoutSessions = workoutSessions.size,
        workoutSets = workoutSets.size,
        trainingPlans = trainingPlans.size,
        planExercises = planExercises.size,
        personalRecords = personalRecords.size,
        metaTrainingPlans = metaTrainingPlans.size,
        metaPlanItems = metaPlanItems.size,
        metaPlanSkips = metaPlanSkips.size,
        workoutPlanTargets = workoutPlanTargets.size,
        progressionSuggestions = progressionSuggestions.size
    )

    private fun BackupPayloadV1.toImportData(): ImportData = ImportData(
        exercises = exercises.distinctBy { it.id }.map { it.toEntity() },
        workoutSessions = workoutSessions.distinctBy { it.id }.map { it.toEntity() },
        workoutSets = workoutSets.distinctBy { it.id }.map { it.toWorkoutSetEntity() },
        trainingPlans = trainingPlans.distinctBy { it.id }.map { it.toEntity() },
        planExercises = planExercises.distinctBy { it.id }.map { it.toEntity() },
        personalRecords = personalRecords.distinctBy { it.id }.map { it.toEntity() },
        metaTrainingPlans = metaTrainingPlans.distinctBy { it.id }.map { it.toEntity() },
        metaPlanItems = metaPlanItems.distinctBy { it.id }.map { it.toEntity() },
        metaPlanSkips = metaPlanSkips.distinctBy { it.id }.map { it.toEntity() },
        workoutPlanTargets = workoutPlanTargets.distinctBy { it.id }.map { it.toEntity() },
        progressionSuggestions = progressionSuggestions.distinctBy { it.id }.map { it.toEntity() }
    )

    private suspend fun deleteAllInOrder() {
        progressionDao.deleteAllSuggestions()
        personalRecordDao.deleteAll()
        workoutSetDao.deleteAll()
        progressionDao.deleteAllTargets()
        metaTrainingPlanDao.deleteAllMetaPlanSkips()
        metaTrainingPlanDao.deleteAllMetaPlanItems()
        trainingPlanDao.deleteAllPlanExercises()
        workoutSessionDao.deleteAll()
        metaTrainingPlanDao.deleteAllMetaPlans()
        trainingPlanDao.deleteAllPlans()
        exerciseDao.deleteAll()
    }

    private suspend fun insertAllInOrder(data: ImportData) {
        exerciseDao.replaceAll(data.exercises.ifEmpty { ExerciseSeedData.getAll() })
        if (data.trainingPlans.isNotEmpty()) trainingPlanDao.replaceAllPlans(data.trainingPlans)
        if (data.metaTrainingPlans.isNotEmpty()) {
            metaTrainingPlanDao.replaceAllMetaPlans(data.metaTrainingPlans)
        }
        if (data.workoutSessions.isNotEmpty()) {
            workoutSessionDao.replaceAll(data.workoutSessions)
        }
        if (data.planExercises.isNotEmpty()) {
            trainingPlanDao.replaceAllExercises(data.planExercises)
        }
        if (data.workoutPlanTargets.isNotEmpty()) {
            progressionDao.replaceAllTargets(data.workoutPlanTargets)
        }
        if (data.metaPlanItems.isNotEmpty()) {
            metaTrainingPlanDao.replaceAllItems(data.metaPlanItems)
        }
        if (data.metaPlanSkips.isNotEmpty()) {
            metaTrainingPlanDao.replaceAllMetaPlanSkips(data.metaPlanSkips)
        }
        if (data.workoutSets.isNotEmpty()) workoutSetDao.replaceAll(data.workoutSets)
        if (data.progressionSuggestions.isNotEmpty()) {
            progressionDao.replaceAllSuggestions(data.progressionSuggestions)
        }
        if (data.personalRecords.isNotEmpty()) {
            personalRecordDao.replaceAll(data.personalRecords)
        }
    }

    private fun ExerciseEntity.toBackup(): BackupExercise = BackupExercise(
        id = id,
        name = name,
        primaryMuscleGroup = primaryMuscleGroup,
        secondaryMuscleGroups = secondaryMuscleGroups,
        category = category,
        isCustom = isCustom,
        notes = notes,
        isArchived = isArchived
    )

    private fun WorkoutSessionEntity.toBackup(): BackupWorkoutSession = BackupWorkoutSession(
        id = id,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        name = name,
        notes = notes,
        planId = planId,
        metaPlanId = metaPlanId
    )

    private fun TrainingPlanEntity.toBackup(): BackupTrainingPlan = BackupTrainingPlan(
        id = id,
        name = name,
        createdAt = createdAt
    )

    private fun PlanExerciseEntity.toBackup(): BackupPlanExercise = BackupPlanExercise(
        id = id,
        planId = planId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        supersetGroupId = supersetGroupId,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
        progression = progression.toBackup()
    )

    private fun ProgressionConfigColumns.toBackup(): BackupProgressionConfig =
        BackupProgressionConfig(
            scheme = scheme,
            incrementValue = incrementValue,
            incrementUnit = incrementUnit,
            incrementKg = incrementKg,
            minReps = minReps,
            maxReps = maxReps,
            targetTotalReps = targetTotalReps,
            targetRpe = targetRpe,
            rpeTolerance = rpeTolerance,
            stallThreshold = stallThreshold,
            backoffPercent = backoffPercent,
            ruleRevision = ruleRevision
        )

    private fun ProgressionTargetColumns.toBackup(): BackupProgressionTarget =
        BackupProgressionTarget(sets = sets, reps = reps, weightKg = weightKg)

    private fun WorkoutPlanTargetEntity.toBackup(): BackupWorkoutPlanTarget =
        BackupWorkoutPlanTarget(
            id = id,
            sessionId = sessionId,
            planId = planId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            supersetGroupId = supersetGroupId,
            target = target.toBackup(),
            progression = progression.toBackup()
        )

    private fun ProgressionSuggestionEntity.toBackup(): BackupProgressionSuggestion =
        BackupProgressionSuggestion(
            id = id,
            sourceSessionId = sourceSessionId,
            sourceTargetSnapshotId = sourceTargetSnapshotId,
            planId = planId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            supersetGroupId = supersetGroupId,
            sourceTarget = sourceTarget.toBackup(),
            sourceProgression = sourceProgression.toBackup(),
            outcomeType = outcomeType,
            reasonCode = reasonCode,
            reasonArguments = progressionJson.decodeFromString(
                REASON_ARGUMENTS_SERIALIZER,
                reasonArgumentsJson
            ).toSortedMap(),
            countedSetIds = progressionJson.decodeFromString(
                COUNTED_SET_IDS_SERIALIZER,
                countedSetIdsJson
            ),
            streakEffect = streakEffect,
            suggestedTarget = suggestedTarget?.toBackup(),
            status = status,
            wasEdited = wasEdited,
            finalTarget = finalTarget?.toBackup(),
            createdAtEpochMillis = createdAtEpochMillis,
            decidedAtEpochMillis = decidedAtEpochMillis
        )

    private fun PersonalRecordEntity.toBackup(): BackupPersonalRecord = BackupPersonalRecord(
        id = id,
        exerciseId = exerciseId,
        type = type,
        value = value,
        achievedAt = achievedAt
    )

    private fun MetaTrainingPlanEntity.toBackup(): BackupMetaTrainingPlan = BackupMetaTrainingPlan(
        id = id,
        name = name,
        createdAt = createdAt
    )

    private fun MetaPlanItemEntity.toBackup(): BackupMetaPlanItem = BackupMetaPlanItem(
        id = id,
        metaPlanId = metaPlanId,
        trainingPlanId = trainingPlanId,
        orderIndex = orderIndex
    )

    private fun MetaPlanSkipEntity.toBackup(): BackupMetaPlanSkip = BackupMetaPlanSkip(
        id = id,
        metaPlanId = metaPlanId,
        trainingPlanId = trainingPlanId,
        skippedAt = skippedAt
    )

    private fun BackupExercise.toEntity(): ExerciseEntity = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscleGroup = primaryMuscleGroup,
        secondaryMuscleGroups = secondaryMuscleGroups,
        category = category,
        isCustom = isCustom,
        notes = notes,
        isArchived = isArchived
    )

    private fun BackupWorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
        id = id,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        name = name,
        notes = notes,
        planId = planId,
        metaPlanId = metaPlanId
    )

    private fun BackupTrainingPlan.toEntity(): TrainingPlanEntity = TrainingPlanEntity(
        id = id,
        name = name,
        createdAt = createdAt
    )

    private fun BackupPlanExercise.toEntity(): PlanExerciseEntity = PlanExerciseEntity(
        id = id,
        planId = planId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        supersetGroupId = supersetGroupId,
        targetSets = targetSets,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
        progression = progression.toEntity()
    )

    private fun BackupProgressionConfig.toEntity(): ProgressionConfigColumns =
        ProgressionConfigColumns(
            scheme = scheme,
            incrementValue = incrementValue,
            incrementUnit = incrementUnit,
            incrementKg = incrementKg,
            minReps = minReps,
            maxReps = maxReps,
            targetTotalReps = targetTotalReps,
            targetRpe = targetRpe,
            rpeTolerance = rpeTolerance,
            stallThreshold = stallThreshold,
            backoffPercent = backoffPercent,
            ruleRevision = ruleRevision
        )

    private fun BackupProgressionTarget.toEntity(): ProgressionTargetColumns =
        ProgressionTargetColumns(sets = sets, reps = reps, weightKg = weightKg)

    private fun BackupWorkoutPlanTarget.toEntity(): WorkoutPlanTargetEntity =
        WorkoutPlanTargetEntity(
            id = id,
            sessionId = sessionId,
            planId = planId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            supersetGroupId = supersetGroupId,
            target = target.toEntity(),
            progression = progression.toEntity()
        )

    private fun BackupProgressionSuggestion.toEntity(): ProgressionSuggestionEntity =
        ProgressionSuggestionEntity(
            id = id,
            sourceSessionId = sourceSessionId,
            sourceTargetSnapshotId = sourceTargetSnapshotId,
            planId = planId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            supersetGroupId = supersetGroupId,
            sourceTarget = sourceTarget.toEntity(),
            sourceProgression = sourceProgression.toEntity(),
            outcomeType = outcomeType,
            reasonCode = reasonCode,
            reasonArgumentsJson = progressionJson.encodeToString(
                REASON_ARGUMENTS_SERIALIZER,
                reasonArguments.toSortedMap()
            ),
            countedSetIdsJson = progressionJson.encodeToString(
                COUNTED_SET_IDS_SERIALIZER,
                countedSetIds
            ),
            streakEffect = streakEffect,
            suggestedTarget = suggestedTarget?.toEntity(),
            status = status,
            wasEdited = wasEdited,
            finalTarget = finalTarget?.toEntity(),
            createdAtEpochMillis = createdAtEpochMillis,
            decidedAtEpochMillis = decidedAtEpochMillis
        )

    private fun BackupPersonalRecord.toEntity(): PersonalRecordEntity = PersonalRecordEntity(
        id = id,
        exerciseId = exerciseId,
        type = type,
        value = value,
        achievedAt = achievedAt
    )

    private fun BackupMetaTrainingPlan.toEntity(): MetaTrainingPlanEntity = MetaTrainingPlanEntity(
        id = id,
        name = name,
        createdAt = createdAt
    )

    private fun BackupMetaPlanItem.toEntity(): MetaPlanItemEntity = MetaPlanItemEntity(
        id = id,
        metaPlanId = metaPlanId,
        trainingPlanId = trainingPlanId,
        orderIndex = orderIndex
    )

    private fun BackupMetaPlanSkip.toEntity(): MetaPlanSkipEntity = MetaPlanSkipEntity(
        id = id,
        metaPlanId = metaPlanId,
        trainingPlanId = trainingPlanId,
        skippedAt = skippedAt
    )

    private data class ImportData(
        val exercises: List<ExerciseEntity>,
        val workoutSessions: List<WorkoutSessionEntity>,
        val workoutSets: List<WorkoutSetEntity>,
        val trainingPlans: List<TrainingPlanEntity>,
        val planExercises: List<PlanExerciseEntity>,
        val personalRecords: List<PersonalRecordEntity>,
        val metaTrainingPlans: List<MetaTrainingPlanEntity>,
        val metaPlanItems: List<MetaPlanItemEntity>,
        val metaPlanSkips: List<MetaPlanSkipEntity>,
        val workoutPlanTargets: List<WorkoutPlanTargetEntity>,
        val progressionSuggestions: List<ProgressionSuggestionEntity>
    )

    private companion object {
        const val SCHEMA_VERSION = 11
        val REASON_ARGUMENTS_SERIALIZER = MapSerializer(String.serializer(), Double.serializer())
        val COUNTED_SET_IDS_SERIALIZER = ListSerializer(Long.serializer())
    }
}

internal fun WorkoutSetEntity.toBackupWorkoutSet(): BackupWorkoutSet = BackupWorkoutSet(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    isWarmup = isWarmup,
    completedAt = completedAt,
    rpe = rpe,
    planTargetSnapshotId = planTargetSnapshotId
)

internal fun BackupWorkoutSet.toWorkoutSetEntity(): WorkoutSetEntity = WorkoutSetEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    isWarmup = isWarmup,
    completedAt = completedAt,
    rpe = rpe,
    planTargetSnapshotId = planTargetSnapshotId
)
