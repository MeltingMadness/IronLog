package com.ironlog.shared.statistics

import com.ironlog.shared.model.AppError
import com.ironlog.shared.model.PersonalRecord
import com.ironlog.shared.model.toAppError
import com.ironlog.shared.repository.SharedStatisticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatisticsOverviewState(
    val recentRecords: List<PersonalRecord> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

class StatisticsOverviewController(
    scope: CoroutineScope,
    statisticsRepository: SharedStatisticsRepository,
    private val recentRecordLimit: Int = 5,
) {
    private val mutableState = MutableStateFlow(StatisticsOverviewState())
    val state: StateFlow<StatisticsOverviewState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching {
                statisticsRepository.observeRecentRecords(recentRecordLimit).collect { recentRecords ->
                    mutableState.value = StatisticsOverviewState(
                        recentRecords = recentRecords,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toAppError(),
                )
            }
        }
    }
}
