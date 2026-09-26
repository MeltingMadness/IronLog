package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

interface StatisticsRepository {
    suspend fun checkAndUpdateRecord(exerciseId: Long, type: RecordType, value: Double): Boolean
    fun getRecordsForExercise(exerciseId: Long): Flow<List<PersonalRecord>>
    suspend fun getRecordsForExercisesList(exerciseIds: List<Long>): List<PersonalRecord>
    fun getRecentRecords(limit: Int = 5): Flow<List<PersonalRecord>>
    suspend fun getRecentRecordsList(limit: Int = 5): List<PersonalRecord>
    fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSet>>

    /** Per-exercise session count and last training time, from completed sessions only. */
    fun observeExerciseTrainingSummaries(): Flow<List<com.ironlog.app.domain.model.ExerciseTrainingSummary>>
    suspend fun getSetsForExerciseList(exerciseId: Long): List<WorkoutSet>
    /** Historical exercise statistics: completed sessions and timestamps up to [nowEpochMillis]. */
    suspend fun getCompletedSetsForExerciseList(exerciseId: Long, nowEpochMillis: Long): List<WorkoutSet>
    suspend fun getMaxWeightForExercise(exerciseId: Long): Double?
    suspend fun getMaxRepsForExercise(exerciseId: Long): Int?
    suspend fun getWorkSetsCompletedSince(sinceEpochMillis: Long): List<WorkoutSet>
    /** Analytics-only work sets bounded by both set completion and session start. */
    suspend fun getWorkSetsCompletedBetween(sinceEpochMillis: Long, untilEpochMillis: Long): List<WorkoutSet> =
        getWorkSetsCompletedSince(sinceEpochMillis)
}
