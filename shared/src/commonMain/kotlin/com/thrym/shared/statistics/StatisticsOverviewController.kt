package com.thrym.shared.statistics

import com.thrym.shared.model.AppError
import com.thrym.shared.model.PersonalRecord
import com.thrym.shared.model.toAppError
import com.thrym.shared.repository.SharedStatisticsRepository
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
