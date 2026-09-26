package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WorkoutPlanTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ProgressionRepository {
    fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>>
    fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>>
    fun observePendingCount(): Flow<Int>

    /**
     * Already evaluated outcomes that are no longer pending (accepted, rejected, stale,
     * informational), newest first, so past decisions stay visible like on iOS.
     */
    fun observeRecentDecisions(limit: Int = RECENT_DECISIONS_LIMIT): Flow<List<ProgressionSuggestion>> =
        flowOf(emptyList())
    suspend fun generateOutcomesForSession(sessionId: Long): ProgressionGenerationResult
    suspend fun generateMissingOutcomes(): Int
    suspend fun reconcileOutstandingSuggestions(): Set<Long>
    suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult
    suspend fun rejectSuggestion(suggestionId: Long)
}

const val RECENT_DECISIONS_LIMIT = 50
