package com.ironlog.app.fakes

import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class FakeStatisticsRepository : StatisticsRepository {

    private val records = MutableStateFlow<List<PersonalRecord>>(emptyList())
    private val exerciseSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    private var nextId = 1L

    val updatedRecords = mutableListOf<Triple<Long, RecordType, Double>>()

    fun addRecord(record: PersonalRecord) {
        records.value = records.value + record
    }

    fun addExerciseSet(set: WorkoutSet) {
        exerciseSets.value = exerciseSets.value + set
    }

    override suspend fun checkAndUpdateRecord(exerciseId: Long, type: RecordType, value: Double): Boolean {
        updatedRecords.add(Triple(exerciseId, type, value))
        val existing = records.value.find { it.exerciseId == exerciseId && it.type == type }
        if (existing == null || value > existing.value) {
            val newRecord = PersonalRecord(
                id = nextId++,
                exerciseId = exerciseId,
                type = type,
                value = value,
                achievedAt = LocalDateTime.now()
            )
            records.value = records.value.filter { !(it.exerciseId == exerciseId && it.type == type) } + newRecord
            return true
        }
        return false
    }

    override fun getRecordsForExercise(exerciseId: Long): Flow<List<PersonalRecord>> =
        records.map { list -> list.filter { it.exerciseId == exerciseId } }

    override fun getRecentRecords(limit: Int): Flow<List<PersonalRecord>> =
        records.map { list -> list.sortedByDescending { it.achievedAt }.take(limit) }

    override suspend fun getRecentRecordsList(limit: Int): List<PersonalRecord> =
        records.value.sortedByDescending { it.achievedAt }.take(limit)

    override fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSet>> =
        exerciseSets.map { list -> list.filter { it.exerciseId == exerciseId } }

    override suspend fun getSetsForExerciseList(exerciseId: Long): List<WorkoutSet> =
        exerciseSets.value.filter { it.exerciseId == exerciseId }

    override suspend fun getMaxWeightForExercise(exerciseId: Long): Double? =
        exerciseSets.value.filter { it.exerciseId == exerciseId }.maxByOrNull { it.weightKg }?.weightKg

    override suspend fun getMaxRepsForExercise(exerciseId: Long): Int? =
        exerciseSets.value.filter { it.exerciseId == exerciseId }.maxByOrNull { it.reps }?.reps
}
