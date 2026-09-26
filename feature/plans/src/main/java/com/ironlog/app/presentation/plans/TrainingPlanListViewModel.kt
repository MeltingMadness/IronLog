package com.ironlog.app.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.error.toAppError
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.presentation.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class PlanListItem(
    val plan: TrainingPlan,
    val exerciseNames: List<String>,
    /** Days since the last completed session of this plan, `null` if never trained. */
    val lastDoneDaysAgo: Long? = null
)

/** Compact meta plan row so rotations are visible without opening the meta plan screen. */
data class PlanListMetaPlan(
    val id: Long,
    val name: String,
    val subPlanNames: List<String>
)

data class PlanListUiState(
    val plans: List<PlanListItem> = emptyList(),
    val metaPlans: List<PlanListMetaPlan> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class TrainingPlanListViewModel(
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val metaTrainingPlanRepository: MetaTrainingPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanListUiState())
    val uiState: StateFlow<PlanListUiState> = _uiState

    init {
        loadPlans()
    }

    private fun loadPlans() {
        viewModelScope.launch {
            combine(
                planRepository.getAllPlans(),
                exerciseRepository.getAllExercises(),
                workoutRepository.observeLastSessionPerPlan(),
                metaTrainingPlanRepository.getAllMetaPlans()
            ) { plans, exercises, lastSessions, metaPlans ->
                val exerciseNameById = exercises.associateBy({ it.id }, { it.name })
                val lastStartByPlanId = lastSessions.associateBy({ it.planId }, { it.lastStartTime })
                val today = LocalDate.now()
                val items = plans.map { plan ->
                    val names = plan.exercises.map { exercise ->
                        exerciseNameById[exercise.exerciseId] ?: "Unbekannt"
                    }
                    PlanListItem(
                        plan = plan,
                        exerciseNames = names,
                        lastDoneDaysAgo = lastStartByPlanId[plan.id]?.let { millis ->
                            ChronoUnit.DAYS.between(
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(),
                                today
                            )
                        }
                    )
                }
                val planNameById = plans.associateBy({ it.id }, { it.name })
                val metaItems = metaPlans.map { meta ->
                    PlanListMetaPlan(
                        id = meta.id,
                        name = meta.name,
                        subPlanNames = meta.items
                            .sortedBy { it.orderIndex }
                            .mapNotNull { planNameById[it.trainingPlanId] }
                    )
                }
                items to metaItems
            }.collect { (items, metaItems) ->
                _uiState.value = _uiState.value.copy(
                    plans = items,
                    metaPlans = metaItems,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    fun deletePlan(planId: Long) {
        viewModelScope.launch {
            try {
                planRepository.deletePlan(planId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.toAppError().toUserMessage("Plan loeschen")
                )
            }
        }
    }

    fun startPlanWorkout(plan: TrainingPlan, onSessionCreated: (Long, Long) -> Unit) {
        viewModelScope.launch {
            try {
                val activeSession = workoutRepository.getActiveSession()
                if (activeSession != null) {
                    if (activeSession.planId == plan.id) {
                        onSessionCreated(activeSession.id, plan.id)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Es ist bereits ein anderes Training aktiv. Bitte setze es fort oder beende es zuerst."
                        )
                    }
                    return@launch
                }

                val sessionId = workoutRepository.startWorkout(
                    name = plan.name,
                    planId = plan.id,
                    metaPlanId = null
                )
                onSessionCreated(sessionId, plan.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.toAppError().toUserMessage("Training starten")
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
