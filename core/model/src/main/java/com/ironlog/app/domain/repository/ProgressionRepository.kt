package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WorkoutPlanTarget
import kotlinx.coroutines.flow.Flow

interface ProgressionRepository {
    fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>>
    fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>>
    fun observePendingCount(): Flow<Int>
    suspend fun generateOutcomesForSession(sessionId: Long): ProgressionGenerationResult
    suspend fun generateMissingOutcomes(): Int
    suspend fun reconcileOutstandingSuggestions(): Set<Long>
    suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult
    suspend fun rejectSuggestion(suggestionId: Long)
}
