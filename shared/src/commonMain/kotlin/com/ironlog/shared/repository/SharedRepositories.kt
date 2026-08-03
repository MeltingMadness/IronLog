package com.ironlog.shared.repository

import com.ironlog.shared.model.BackupBlob
import com.ironlog.shared.model.CompletedWorkoutSummary
import com.ironlog.shared.model.CursorPage
import com.ironlog.shared.model.CursorPageRequest
import com.ironlog.shared.model.Exercise
import com.ironlog.shared.model.IncidentAttachment
import com.ironlog.shared.model.LastMetaPlanSession
import com.ironlog.shared.model.LastPlanSession
import com.ironlog.shared.model.MetaTrainingPlan
import com.ironlog.shared.model.MuscleGroup
import com.ironlog.shared.model.PersonalRecord
import com.ironlog.shared.model.PreviousExerciseSession
import com.ironlog.shared.model.RecordType
import com.ironlog.shared.model.TrainingPlan
import com.ironlog.shared.model.WorkoutSession
import com.ironlog.shared.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

interface SharedExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>>
    fun searchExercises(query: String): Flow<List<Exercise>>
    suspend fun getExerciseById(id: Long): Exercise?
    suspend fun getExercisesByIds(ids: List<Long>): List<Exercise>
    suspend fun saveExercise(exercise: Exercise): Long
    suspend fun deleteExercise(id: Long)
}

interface SharedWorkoutRepository {
    suspend fun startWorkout(
        name: String = "",
        planId: Long? = null,
        metaPlanId: Long? = null,
    ): Long

    suspend fun finishWorkout(sessionId: Long)
    suspend fun getActiveSession(): WorkoutSession?
    fun observeActiveSession(): Flow<WorkoutSession?>
    suspend fun addSet(set: WorkoutSet): Long
    suspend fun updateSet(set: WorkoutSet)
    suspend fun deleteSet(setId: Long)
    fun observeSetsForSession(sessionId: Long): Flow<List<WorkoutSet>>
    suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet>
    suspend fun getCompletedSessionSummariesPage(request: CursorPageRequest): CursorPage<CompletedWorkoutSummary>
    suspend fun getSessionById(id: Long): WorkoutSession?
    fun observeSessionById(id: Long): Flow<WorkoutSession?>
    suspend fun deleteSession(sessionId: Long)
    suspend fun getPreviousSessionDataForExercises(
        currentSessionId: Long,
        exerciseIds: List<Long>,
        planId: Long? = null,
    ): Map<Long, PreviousExerciseSession>

    fun observeLastSessionPerPlan(): Flow<List<LastPlanSession>>
    fun observeLastSessionPerMetaPlanSubPlan(): Flow<List<LastMetaPlanSession>>
}

interface SharedTrainingPlanRepository {
    fun observePlans(): Flow<List<TrainingPlan>>
    suspend fun getPlanById(id: Long): TrainingPlan?
    suspend fun savePlan(plan: TrainingPlan): Long
    suspend fun deletePlan(planId: Long)
}

interface SharedMetaTrainingPlanRepository {
    fun observeMetaPlans(): Flow<List<MetaTrainingPlan>>
    suspend fun getMetaPlanById(id: Long): MetaTrainingPlan?
    suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long
    suspend fun deleteMetaPlan(metaPlanId: Long)
}

interface SharedStatisticsRepository {
    suspend fun checkAndUpdateRecord(exerciseId: Long, type: RecordType, value: Double): Boolean
    fun observeRecordsForExercise(exerciseId: Long): Flow<List<PersonalRecord>>
    suspend fun getRecordsForExercises(exerciseIds: List<Long>): List<PersonalRecord>
    fun observeRecentRecords(limit: Int = 5): Flow<List<PersonalRecord>>
    suspend fun getRecentRecords(limit: Int = 5): List<PersonalRecord>
    fun observeSetsForExercise(exerciseId: Long): Flow<List<WorkoutSet>>
    suspend fun getSetsForExercise(exerciseId: Long): List<WorkoutSet>
    suspend fun getMaxWeightForExercise(exerciseId: Long): Double?
    suspend fun getMaxRepsForExercise(exerciseId: Long): Int?
    suspend fun getWorkSetsCompletedSince(sinceEpochMillis: Long): List<WorkoutSet>
}

interface SharedBackupRepository {
    suspend fun exportBackup(): BackupBlob
    suspend fun importBackup(bytes: ByteArray)
    suspend fun resetUserData()
}

interface SharedIncidentReportRepository {
    suspend fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        throwableDescription: String? = null,
    ): IncidentAttachment
}
