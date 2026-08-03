package com.ironlog.shared.dashboard

import com.ironlog.shared.model.AppError
import com.ironlog.shared.model.LastMetaPlanSession
import com.ironlog.shared.model.LastPlanSession
import com.ironlog.shared.model.MetaTrainingPlan
import com.ironlog.shared.model.PersonalRecord
import com.ironlog.shared.model.TrainingPlan
import com.ironlog.shared.model.WorkoutSession
import com.ironlog.shared.model.toAppError
import com.ironlog.shared.repository.SharedMetaTrainingPlanRepository
import com.ironlog.shared.repository.SharedStatisticsRepository
import com.ironlog.shared.repository.SharedTrainingPlanRepository
import com.ironlog.shared.repository.SharedWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DashboardState(
    val activeSession: WorkoutSession? = null,
    val recentRecords: List<PersonalRecord> = emptyList(),
    val trainingPlans: List<TrainingPlan> = emptyList(),
    val metaPlans: List<MetaTrainingPlan> = emptyList(),
    val lastPlanSessions: List<LastPlanSession> = emptyList(),
    val lastMetaPlanSessions: List<LastMetaPlanSession> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

class DashboardController(
    scope: CoroutineScope,
    workoutRepository: SharedWorkoutRepository,
    statisticsRepository: SharedStatisticsRepository,
    trainingPlanRepository: SharedTrainingPlanRepository,
    metaTrainingPlanRepository: SharedMetaTrainingPlanRepository,
) {
    private data class DashboardCollections(
        val activeSession: WorkoutSession?,
        val recentRecords: List<PersonalRecord>,
        val trainingPlans: List<TrainingPlan>,
        val metaPlans: List<MetaTrainingPlan>,
    )

    private val mutableState = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching {
                combine(
                    workoutRepository.observeActiveSession(),
                    statisticsRepository.observeRecentRecords(),
                    trainingPlanRepository.observePlans(),
                    metaTrainingPlanRepository.observeMetaPlans(),
                ) { activeSession, records, trainingPlans, metaPlans ->
                    DashboardCollections(
                        activeSession = activeSession,
                        recentRecords = records,
                        trainingPlans = trainingPlans,
                        metaPlans = metaPlans,
                    )
                }.combine(
                    workoutRepository.observeLastSessionPerPlan(),
                ) { collections, lastPlanSessions ->
                    collections to lastPlanSessions
                }.combine(
                    workoutRepository.observeLastSessionPerMetaPlanSubPlan(),
                ) { collectionsAndPlanSessions, lastMetaPlanSessions ->
                    val (collections, lastPlanSessions) = collectionsAndPlanSessions
                    DashboardState(
                        activeSession = collections.activeSession,
                        recentRecords = collections.recentRecords,
                        trainingPlans = collections.trainingPlans,
                        metaPlans = collections.metaPlans,
                        lastPlanSessions = lastPlanSessions,
                        lastMetaPlanSessions = lastMetaPlanSessions,
                        isLoading = false,
                    )
                }.collect { state -> mutableState.value = state }
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toAppError(),
                )
            }
        }
    }
}
