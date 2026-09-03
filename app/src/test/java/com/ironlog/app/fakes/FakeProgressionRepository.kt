package com.ironlog.app.fakes

import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.repository.ProgressionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake of [ProgressionRepository] for ViewModel tests. Suggestions
 * are exposed via [reviewItems]; every mutation mirrors into that flow like
 * the real Room-backed implementation would.
 */
class FakeProgressionRepository : ProgressionRepository {

    val targets = MutableStateFlow<List<WorkoutPlanTarget>>(emptyList())
    val reviewItems = MutableStateFlow<List<ProgressionSuggestion>>(emptyList())
    val observedSessionIds = mutableListOf<Long?>()

    var generateResult = ProgressionGenerationResult(insertedCount = 0, reviewItemCount = 0, pendingCount = 0)
    var generateError: Throwable? = null
    var acceptResult: ProgressionDecisionResult? = null
    var acceptError: Throwable? = null
    var rejectError: Throwable? = null
    var reconcileError: Throwable? = null

    var generateCalls = 0
    var acceptCalls = 0
    var rejectedIds = mutableListOf<Long>()

    val lastAccepted = mutableMapOf<Long, ProgressionTarget>()

    fun setSuggestions(suggestions: List<ProgressionSuggestion>) {
        reviewItems.value = suggestions
    }

    override fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>> = targets

    override fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>> {
        observedSessionIds += sessionId
        return reviewItems
    }

    override fun observePendingCount(): Flow<Int> =
        MutableStateFlow(reviewItems.value.count { it.status.name == "PENDING" })

    override suspend fun generateOutcomesForSession(sessionId: Long): ProgressionGenerationResult {
        generateCalls += 1
        generateError?.let { throw it }
        return generateResult
    }

    override suspend fun generateMissingOutcomes(): Int = 0

    override suspend fun reconcileOutstandingSuggestions(): Set<Long> {
        reconcileError?.let { throw it }
        return emptySet()
    }

    override suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult {
        acceptCalls += 1
        lastAccepted.putAll(finalTargetsBySuggestionId)
        acceptError?.let { throw it }
        return acceptResult ?: ProgressionDecisionResult.Accepted(finalTargetsBySuggestionId.keys)
    }

    override suspend fun rejectSuggestion(suggestionId: Long) {
        rejectError?.let { throw it }
        rejectedIds += suggestionId
    }
}
