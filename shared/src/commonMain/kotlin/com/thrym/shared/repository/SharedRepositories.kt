package com.thrym.shared.repository

import com.thrym.shared.model.BackupBlob
import com.thrym.shared.model.CompletedWorkoutSummary
import com.thrym.shared.model.CursorPage
import com.thrym.shared.model.CursorPageRequest
import com.thrym.shared.model.Exercise
import com.thrym.shared.model.IncidentAttachment
import com.thrym.shared.model.LastMetaPlanSession
import com.thrym.shared.model.LastPlanSession
import com.thrym.shared.model.MetaTrainingPlan
import com.thrym.shared.model.MuscleGroup
import com.thrym.shared.model.PersonalRecord
import com.thrym.shared.model.PreviousExerciseSession
import com.thrym.shared.model.RecordType
import com.thrym.shared.model.TrainingPlan
import com.thrym.shared.model.WorkoutSession
import com.thrym.shared.model.WorkoutSet
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
