package com.ironlog.app.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MetaPlanEditorUiState(
    val metaPlanId: Long? = null,
    val name: String = "",
    val availablePlans: List<TrainingPlan> = emptyList(),
    val selectedPlanIds: List<Long> = emptyList(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val error: String? = null
)

class MetaPlanEditorViewModel(
    private val trainingPlanRepository: TrainingPlanRepository,
    private val metaTrainingPlanRepository: MetaTrainingPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetaPlanEditorUiState())
    val uiState: StateFlow<MetaPlanEditorUiState> = _uiState

    private var initializedForMetaPlanId: Long? = null

    init {
        observeAvailablePlans()
    }

    fun initialize(metaPlanId: Long?) {
        if (initializedForMetaPlanId == metaPlanId) return
        initializedForMetaPlanId = metaPlanId

        if (metaPlanId == null || metaPlanId <= 0L) {
            _uiState.update { it.copy(metaPlanId = null, isLoading = false) }
            return
        }

        viewModelScope.launch {
            try {
                val metaPlan = metaTrainingPlanRepository.getMetaPlanById(metaPlanId)
                if (metaPlan == null) {
                    _uiState.update {
                        it.copy(
                            metaPlanId = null,
                            isLoading = false,
                            error = "Meta-Plan konnte nicht geladen werden."
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        metaPlanId = metaPlan.id,
                        name = metaPlan.name,
                        selectedPlanIds = metaPlan.items.sortedBy { item -> item.orderIndex }
                            .map { item -> item.trainingPlanId }
                            .distinct(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Meta-Plan konnte nicht geladen werden: ${e.message}"
                    )
                }
            }
        }
    }

    private fun observeAvailablePlans() {
        viewModelScope.launch {
            trainingPlanRepository.getAllPlans()
                .catch { error ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            error = "Unterplaene konnten nicht geladen werden: ${error.message}"
                        )
                    }
                }
                .collect { plans ->
                    _uiState.update { current ->
                        val availableIds = plans.map { it.id }.toSet()
                        current.copy(
                            availablePlans = plans,
                            selectedPlanIds = current.selectedPlanIds
                                .filter { it in availableIds }
                                .distinct(),
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun togglePlan(planId: Long) {
        _uiState.update { current ->
            val updated = if (planId in current.selectedPlanIds) {
                current.selectedPlanIds.filterNot { it == planId }
            } else {
                current.selectedPlanIds + planId
            }
            current.copy(selectedPlanIds = updated)
        }
    }

    fun moveSelectedPlanUp(index: Int) {
        if (index <= 0) return
        _uiState.update { current ->
            if (index !in current.selectedPlanIds.indices) return@update current
            val mutable = current.selectedPlanIds.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            current.copy(selectedPlanIds = mutable)
        }
    }

    fun moveSelectedPlanDown(index: Int) {
        _uiState.update { current ->
            if (index < 0 || index >= current.selectedPlanIds.lastIndex) return@update current
            val mutable = current.selectedPlanIds.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            current.copy(selectedPlanIds = mutable)
        }
    }

    fun removeSelectedPlan(planId: Long) {
        _uiState.update { current ->
            current.copy(selectedPlanIds = current.selectedPlanIds.filterNot { it == planId })
        }
    }

    fun saveMetaPlan() {
        val name = _uiState.value.name.trim()
        val selectedPlans = _uiState.value.selectedPlanIds.distinct()

        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Bitte gib einen Namen ein.") }
            return
        }
        if (selectedPlans.isEmpty()) {
            _uiState.update { it.copy(error = "Bitte waehle mindestens einen Unterplan.") }
            return
        }

        viewModelScope.launch {
            try {
                val metaPlan = MetaTrainingPlan(
                    id = _uiState.value.metaPlanId ?: 0L,
                    name = name,
                    items = selectedPlans.mapIndexed { index, trainingPlanId ->
                        MetaTrainingPlanItem(
                            trainingPlanId = trainingPlanId,
                            orderIndex = index
                        )
                    }
                )
                val savedId = metaTrainingPlanRepository.saveMetaPlan(metaPlan)
                _uiState.update { it.copy(metaPlanId = savedId, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Meta-Plan konnte nicht gespeichert werden: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
