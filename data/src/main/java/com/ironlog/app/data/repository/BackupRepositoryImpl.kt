package com.ironlog.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.app.data.backup.BackupExercise
import com.ironlog.app.data.backup.BackupPayloadV1
import com.ironlog.app.data.backup.BackupPayloadValidator
import com.ironlog.app.data.backup.BackupPersonalRecord
import com.ironlog.app.data.backup.BackupMetaPlanItem
import com.ironlog.app.data.backup.BackupMetaTrainingPlan
import com.ironlog.app.data.backup.BackupPlanExercise
import com.ironlog.app.data.backup.BackupTrainingPlan
import com.ironlog.app.data.backup.BackupWorkoutSession
import com.ironlog.app.data.backup.BackupWorkoutSet
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
import com.ironlog.app.domain.repository.BackupRepository
import kotlinx.serialization.json.Json
import java.io.IOException

class BackupRepositoryImpl(
    private val context: Context,
    private val database: IronLogDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val workoutSetDao: WorkoutSetDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val metaTrainingPlanDao: MetaTrainingPlanDao,
    private val personalRecordDao: PersonalRecordDao,
    private val buildInfo: BuildInfo
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    override suspend fun exportBackup(uri: Uri) {
        val payload = BackupPayloadV1(
            formatVersion = BACKUP_FORMAT_VERSION,
            schemaVersion = SCHEMA_VERSION,
            appVersion = buildInfo.versionName,
            exportedAtEpochMillis = System.currentTimeMillis(),
            exercises = exerciseDao.getAllExercisesList().map { it.toBackup() },
            workoutSessions = workoutSessionDao.getAllSessionsList().map { it.toBackup() },
            workoutSets = workoutSetDao.getAllSetsList().map { it.toBackup() },
            trainingPlans = trainingPlanDao.getAllPlansList().map { it.toBackup() },
            planExercises = trainingPlanDao.getAllPlanExercisesList().map { it.toBackup() },
            personalRecords = personalRecordDao.getAllRecordsList().map { it.toBackup() },
            metaTrainingPlans = metaTrainingPlanDao.getAllMetaPlansList().map { it.toBackup() },
            metaPlanItems = metaTrainingPlanDao.getAllMetaPlanItemsList().map { it.toBackup() }
        )

        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Backup output stream could not be opened")

        output.use { stream ->
            stream.write(json.encodeToString(BackupPayloadV1.serializer(), payload).encodeToByteArray())
            stream.flush()
        }
    }

    override suspend fun importBackup(uri: Uri) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Backup input stream could not be opened")

        val payload = input.use { stream ->
            val raw = stream.bufferedReader().readText()
            json.decodeFromString(BackupPayloadV1.serializer(), raw)
        }

        val validation = BackupPayloadValidator.validate(payload, SCHEMA_VERSION)
        require(validation.isValid) {
            "Backup validation failed: ${validation.errors.joinToString("; ")}"
        }

        val exercises = payload.exercises.distinctBy { it.id }.map { it.toEntity() }
        val sessions = payload.workoutSessions.distinctBy { it.id }.map { it.toEntity() }
        val sets = payload.workoutSets.distinctBy { it.id }.map { it.toEntity() }
        val plans = payload.trainingPlans.distinctBy { it.id }.map { it.toEntity() }
        val planExercises = payload.planExercises.distinctBy { it.id }.map { it.toEntity() }
        val metaPlans = payload.metaTrainingPlans.distinctBy { it.id }.map { it.toEntity() }
        val metaPlanItems = payload.metaPlanItems.distinctBy { it.id }.map { it.toEntity() }
        val records = payload.personalRecords.distinctBy { it.id }.map { it.toEntity() }

        database.withTransaction {
            personalRecordDao.deleteAll()
            workoutSetDao.deleteAll()
            metaTrainingPlanDao.deleteAllMetaPlanItems()
            trainingPlanDao.deleteAllPlanExercises()
            workoutSessionDao.deleteAll()
            metaTrainingPlanDao.deleteAllMetaPlans()
            trainingPlanDao.deleteAllPlans()
            exerciseDao.deleteAll()

            if (exercises.isNotEmpty()) exerciseDao.replaceAll(exercises)
            if (sessions.isNotEmpty()) workoutSessionDao.replaceAll(sessions)
            if (plans.isNotEmpty()) trainingPlanDao.replaceAllPlans(plans)
            if (metaPlans.isNotEmpty()) metaTrainingPlanDao.replaceAllMetaPlans(metaPlans)
            if (sets.isNotEmpty()) workoutSetDao.replaceAll(sets)
            if (planExercises.isNotEmpty()) trainingPlanDao.replaceAllExercises(planExercises)
            if (metaPlanItems.isNotEmpty()) metaTrainingPlanDao.replaceAllItems(metaPlanItems)
            if (records.isNotEmpty()) personalRecordDao.replaceAll(records)
        }
    }

    override suspend fun resetUserData() {
        database.withTransaction {
            personalRecordDao.deleteAll()
            workoutSetDao.deleteAll()
            metaTrainingPlanDao.deleteAllMetaPlanItems()
            trainingPlanDao.deleteAllPlanExercises()
            workoutSessionDao.deleteAll()
            metaTrainingPlanDao.deleteAllMetaPlans()
            trainingPlanDao.deleteAllPlans()
            exerciseDao.deleteAllCustomExercises()
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

    private fun WorkoutSetEntity.toBackup(): BackupWorkoutSet = BackupWorkoutSet(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        isWarmup = isWarmup,
        completedAt = completedAt
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
        targetWeightKg = targetWeightKg
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

    private fun BackupWorkoutSet.toEntity(): WorkoutSetEntity = WorkoutSetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        isWarmup = isWarmup,
        completedAt = completedAt
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
        targetWeightKg = targetWeightKg
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

    companion object {
        private const val BACKUP_FORMAT_VERSION = 1
        private const val SCHEMA_VERSION = 7
    }
}
