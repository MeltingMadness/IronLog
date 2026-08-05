package com.ironlog.shared.plans

import com.ironlog.shared.model.AppError
import com.ironlog.shared.model.MetaTrainingPlan
import com.ironlog.shared.model.TrainingPlan
import com.ironlog.shared.model.toAppError
import com.ironlog.shared.repository.SharedMetaTrainingPlanRepository
import com.ironlog.shared.repository.SharedTrainingPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PlansOverviewState(
    val trainingPlans: List<TrainingPlan> = emptyList(),
    val metaPlans: List<MetaTrainingPlan> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

class PlansOverviewController(
    scope: CoroutineScope,
    trainingPlanRepository: SharedTrainingPlanRepository,
    metaTrainingPlanRepository: SharedMetaTrainingPlanRepository,
) {
    private val mutableState = MutableStateFlow(PlansOverviewState())
    val state: StateFlow<PlansOverviewState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching {
                combine(
                    trainingPlanRepository.observePlans(),
                    metaTrainingPlanRepository.observeMetaPlans(),
                ) { trainingPlans, metaPlans ->
                    PlansOverviewState(
                        trainingPlans = trainingPlans,
                        metaPlans = metaPlans,
                        isLoading = false,
                    )
                }.collect { mutableState.value = it }
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toAppError(),
                )
            }
        }
    }
}
