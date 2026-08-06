package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class StatisticsRepositoryImpl(
    private val personalRecordDao: PersonalRecordDao,
    private val workoutSetDao: WorkoutSetDao
) : StatisticsRepository {

    override suspend fun checkAndUpdateRecord(exerciseId: Long, type: RecordType, value: Double): Boolean {
        val existing = personalRecordDao.getRecord(exerciseId, type.name)
        if (existing == null || value > existing.value) {
            val now = LocalDateTime.now()
            val entity = PersonalRecordEntity(
                id = existing?.id ?: 0,
                exerciseId = exerciseId,
                type = type.name,
                value = value,
                achievedAt = EpochConverter.toLong(now)
            )
            personalRecordDao.insert(entity)
            return true
        }
        return false
    }

    override fun getRecordsForExercise(exerciseId: Long): Flow<List<PersonalRecord>> =
        personalRecordDao.getRecordsForExercise(exerciseId).map { list -> list.map { it.toDomain() } }

    override suspend fun getRecordsForExercisesList(exerciseIds: List<Long>): List<PersonalRecord> {
        if (exerciseIds.isEmpty()) return emptyList()
        return personalRecordDao.getRecordsForExercisesList(exerciseIds).map { it.toDomain() }
    }

    override fun getRecentRecords(limit: Int): Flow<List<PersonalRecord>> =
        personalRecordDao.getRecentRecords(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getRecentRecordsList(limit: Int): List<PersonalRecord> =
        personalRecordDao.getRecentRecordsList(limit).map { it.toDomain() }

    override fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSet>> =
        workoutSetDao.getSetsForExercise(exerciseId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSetsForExerciseList(exerciseId: Long): List<WorkoutSet> =
        workoutSetDao.getSetsForExerciseList(exerciseId).map { it.toDomain() }

    override suspend fun getMaxWeightForExercise(exerciseId: Long): Double? =
        workoutSetDao.getMaxWeightForExercise(exerciseId)

    override suspend fun getMaxRepsForExercise(exerciseId: Long): Int? =
        workoutSetDao.getMaxRepsForExercise(exerciseId)

    override suspend fun getWorkSetsCompletedSince(sinceEpochMillis: Long): List<WorkoutSet> =
        workoutSetDao.getWorkSetsCompletedSince(sinceEpochMillis).map { it.toDomain() }
}
