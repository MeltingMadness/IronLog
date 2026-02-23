package com.ironlog.app.domain.repository

import androidx.paging.PagingData
import com.ironlog.app.domain.model.CompletedWorkoutSummary
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    suspend fun startWorkout(name: String = ""): Long
    suspend fun finishWorkout(sessionId: Long)
    suspend fun getActiveSession(): WorkoutSession?
    fun observeActiveSession(): Flow<WorkoutSession?>
    suspend fun addSet(set: WorkoutSet): Long
    suspend fun deleteSet(setId: Long)
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSet>>
    suspend fun getSetsForSessionList(sessionId: Long): List<WorkoutSet>
    suspend fun getSetsForSessionsList(sessionIds: List<Long>): List<WorkoutSet>
    fun getAllCompletedSessions(): Flow<List<WorkoutSession>>
    fun getPagedCompletedSessions(): Flow<PagingData<WorkoutSession>>
    fun getPagedCompletedWorkoutSummaries(): Flow<PagingData<CompletedWorkoutSummary>>
    suspend fun getSessionById(id: Long): WorkoutSession?
    fun observeSessionById(id: Long): Flow<WorkoutSession?>
    suspend fun deleteSession(sessionId: Long)
    suspend fun getExerciseIdsForSession(sessionId: Long): List<Long>
    suspend fun getSetCountForSession(sessionId: Long): Int
    suspend fun getTotalVolumeForSession(sessionId: Long): Double
    suspend fun getCompletedSessionCountSince(sinceEpochMillis: Long): Int
    suspend fun getLastCompletedSession(): WorkoutSession?
    suspend fun getAllCompletedSessionsList(): List<WorkoutSession>
}
