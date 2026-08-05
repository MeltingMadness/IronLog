package com.ironlog.shared.history

import com.ironlog.shared.model.AppError
import com.ironlog.shared.model.CompletedWorkoutSummary
import com.ironlog.shared.model.CursorPageRequest
import com.ironlog.shared.model.toAppError
import com.ironlog.shared.repository.SharedWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutHistoryState(
    val items: List<CompletedWorkoutSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

class WorkoutHistoryController(
    private val scope: CoroutineScope,
    private val workoutRepository: SharedWorkoutRepository,
    private val pageSize: Int = 20,
) {
    private val mutableState = MutableStateFlow(WorkoutHistoryState(isLoading = true))
    val state: StateFlow<WorkoutHistoryState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        loadPage(cursor = null, append = false)
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.isLoading || current.nextCursor == null && current.items.isNotEmpty()) {
            return
        }
        loadPage(cursor = current.nextCursor, append = true)
    }

    private fun loadPage(cursor: String?, append: Boolean) {
        scope.launch {
            val current = mutableState.value
            mutableState.value = current.copy(isLoading = true, error = null)

            runCatching {
                workoutRepository.getCompletedSessionSummariesPage(
                    CursorPageRequest(limit = pageSize, cursor = cursor),
                )
            }.onSuccess { page ->
                mutableState.value = WorkoutHistoryState(
                    items = if (append) current.items + page.items else page.items,
                    nextCursor = page.nextCursor,
                    isLoading = false,
                )
            }.onFailure { error ->
                mutableState.value = current.copy(
                    isLoading = false,
                    error = error.toAppError(),
                )
            }
        }
    }
}
