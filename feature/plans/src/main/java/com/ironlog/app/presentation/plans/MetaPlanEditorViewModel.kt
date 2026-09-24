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
    val error: String? = null,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false
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
            _uiState.update {
                it.copy(
                    metaPlanId = null,
                    name = "",
                    selectedPlanIds = emptyList(),
                    isLoading = false,
                    isSaved = false,
                    isSaving = false,
                    hasUnsavedChanges = false,
                    error = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                metaPlanId = metaPlanId,
                isLoading = true,
                isSaved = false,
                isSaving = false,
                hasUnsavedChanges = false,
                error = null
            )
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
                            .map { item -> item.trainingPlanId },
                        isLoading = false,
                        isSaved = false,
                        isSaving = false,
                        hasUnsavedChanges = false
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
                            error = "Unterpläne konnten nicht geladen werden: ${error.message}"
                        )
                    }
                }
                .collect { plans ->
                    _uiState.update { current ->
                        val availableIds = plans.map { it.id }.toSet()
                        current.copy(
                            availablePlans = plans,
                            selectedPlanIds = current.selectedPlanIds
                                .filter { it in availableIds },
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun updateName(name: String) {
        _uiState.update { current ->
            if (current.isSaving || current.isSaved || current.name == name) current else current.copy(
                name = name,
                hasUnsavedChanges = true
            )
        }
    }

    /** Adds one rotation entry.  The same plan may occur more than once. */
    fun addPlan(planId: Long) {
        _uiState.update { current ->
            if (current.isSaving || current.isSaved) return@update current
            current.copy(
                selectedPlanIds = current.selectedPlanIds + planId,
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Keeps the old toggle API for callers that use it as a checkbox action.
     * Removing a selected plan removes one occurrence only, so duplicate
     * rotation entries remain independently addressable.
     */
    fun togglePlan(planId: Long) {
        _uiState.update { current ->
            if (current.isSaving || current.isSaved) return@update current
            val selectedIndex = current.selectedPlanIds.indexOf(planId)
            val updated = if (selectedIndex >= 0) {
                current.selectedPlanIds.toMutableList().also { it.removeAt(selectedIndex) }
            } else {
                current.selectedPlanIds + planId
            }
            current.copy(
                selectedPlanIds = updated,
                hasUnsavedChanges = current.hasUnsavedChanges || updated != current.selectedPlanIds
            )
        }
    }

    fun moveSelectedPlanUp(index: Int) {
        if (index <= 0) return
        _uiState.update { current ->
            if (current.isSaving || current.isSaved) return@update current
            if (index !in current.selectedPlanIds.indices) return@update current
            val mutable = current.selectedPlanIds.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            current.copy(
                selectedPlanIds = mutable,
                hasUnsavedChanges = current.hasUnsavedChanges || mutable != current.selectedPlanIds
            )
        }
    }

    fun moveSelectedPlanDown(index: Int) {
        _uiState.update { current ->
            if (current.isSaving || current.isSaved) return@update current
            if (index < 0 || index >= current.selectedPlanIds.lastIndex) return@update current
            val mutable = current.selectedPlanIds.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            current.copy(
                selectedPlanIds = mutable,
                hasUnsavedChanges = current.hasUnsavedChanges || mutable != current.selectedPlanIds
            )
        }
    }

    fun removeSelectedPlanAt(index: Int) {
        _uiState.update { current ->
            if (current.isSaving || current.isSaved || index !in current.selectedPlanIds.indices) {
                return@update current
            }
            current.copy(
                selectedPlanIds = current.selectedPlanIds.toMutableList().also { it.removeAt(index) },
                hasUnsavedChanges = true
            )
        }
    }

    /** Removes the first occurrence for compatibility with older callers. */
    fun removeSelectedPlan(planId: Long) {
        _uiState.update { current ->
            if (current.isSaving || current.isSaved) return@update current
            val selectedIndex = current.selectedPlanIds.indexOf(planId)
            if (selectedIndex < 0) return@update current
            val updated = current.selectedPlanIds.toMutableList().also { it.removeAt(selectedIndex) }
            current.copy(
                selectedPlanIds = updated,
                hasUnsavedChanges = true
            )
        }
    }

    fun saveMetaPlan() {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return

        val name = _uiState.value.name.trim()
        val selectedPlans = _uiState.value.selectedPlanIds

        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Bitte gib einen Namen ein.") }
            return
        }
        if (selectedPlans.isEmpty()) {
            _uiState.update { it.copy(error = "Bitte waehle mindestens einen Unterplan.") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
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
                _uiState.update {
                    it.copy(
                        metaPlanId = savedId,
                        isSaved = true,
                        isSaving = false,
                        hasUnsavedChanges = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Meta-Plan konnte nicht gespeichert werden: ${e.message}",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
