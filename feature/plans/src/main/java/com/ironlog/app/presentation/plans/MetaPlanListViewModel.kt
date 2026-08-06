package com.ironlog.app.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.AppLogger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MetaPlanListItemUi(
    val metaPlan: MetaTrainingPlan,
    val subPlans: List<TrainingPlan>,
    val nextSubPlan: TrainingPlan?,
    val lastDoneDaysAgo: Long?
)

data class MetaPlanListUiState(
    val items: List<MetaPlanListItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class MetaPlanListViewModel(
    private val trainingPlanRepository: TrainingPlanRepository,
    private val workoutRepository: WorkoutRepository,
    private val metaTrainingPlanRepository: MetaTrainingPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetaPlanListUiState())
    val uiState: StateFlow<MetaPlanListUiState> = _uiState

    init {
        observeMetaPlans()
    }

    private fun observeMetaPlans() {
        viewModelScope.launch {
            combine(
                trainingPlanRepository.getAllPlans(),
                workoutRepository.observeLastSessionPerMetaPlanSubPlan(),
                metaTrainingPlanRepository.getAllMetaPlans()
            ) { plans, lastSessionsPerSubPlan, metaPlans ->
                val plansById = plans.associateBy { it.id }
                val lastTimeIndex = lastSessionsPerSubPlan.associateBy(
                    keySelector = { it.planId to it.metaPlanId },
                    valueTransform = { it.lastStartTime }
                )
                metaPlans.map { metaPlan ->
                    val subPlans = metaPlan.items
                        .sortedBy { it.orderIndex }
                        .mapNotNull { item -> plansById[item.trainingPlanId] }

                    // Only consider sub-plans still present in the current rotation, matching
                    // DashboardViewModel.buildMetaPlanOptions so both screens agree on "next up".
                    val latestPlanId = subPlans
                        .mapNotNull { plan -> lastTimeIndex[plan.id to metaPlan.id]?.let { plan.id to it } }
                        .maxByOrNull { it.second }?.first

                    val nextSubPlan = if (subPlans.isEmpty()) {
                        null
                    } else {
                        val nextIndex = latestPlanId?.let { lastId ->
                            val lastIndex = subPlans.indexOfFirst { it.id == lastId }
                            if (lastIndex >= 0) (lastIndex + 1) % subPlans.size else 0
                        } ?: 0
                        subPlans[nextIndex]
                    }

                    val lastDoneMillis = subPlans
                        .mapNotNull { plan -> lastTimeIndex[plan.id to metaPlan.id] }
                        .maxOrNull()

                    MetaPlanListItemUi(
                        metaPlan = metaPlan,
                        subPlans = subPlans,
                        nextSubPlan = nextSubPlan,
                        lastDoneDaysAgo = lastDoneMillis?.toRelativeDaysAgo()
                    )
                }
            }
                .catch { error ->
                    AppLogger.e("MetaPlanListVM", "Flow-Fehler: ${error.message}", error)
                    _uiState.value = MetaPlanListUiState(
                        items = emptyList(),
                        isLoading = false,
                        error = "Meta-Pläne konnten nicht geladen werden: ${error.message}"
                    )
                }
                .collect { items ->
                    _uiState.value = MetaPlanListUiState(
                        items = items,
                        isLoading = false,
                        error = null
                    )
                }
        }
    }

    fun deleteMetaPlan(metaPlanId: Long) {
        viewModelScope.launch {
            try {
                metaTrainingPlanRepository.deleteMetaPlan(metaPlanId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Meta-Plan konnte nicht gelöscht werden: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

private fun Long.toRelativeDaysAgo(): Long {
    val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(date, LocalDate.now())
}
