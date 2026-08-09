package com.ironlog.app.presentation.progression

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.domain.util.WeightFormatting
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressionReviewItemUi(
    val id: Long,
    val sourceSessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val scheme: ProgressionScheme,
    val source: ProgressionTarget,
    val proposed: ProgressionTarget?,
    val countedSets: List<WorkoutSet>,
    val reasonCode: ProgressionReasonCode,
    val reasonArguments: Map<String, Double>,
    val status: ProgressionSuggestionStatus,
    val canDecide: Boolean,
    val configuredStep: WeightStep?
)

enum class ProgressionEditField { SETS, REPS, WEIGHT }

data class ProgressionEditDraft(
    val sets: String,
    val reps: String,
    val weight: String,
    val unitSystem: UnitSystem,
    val dirtyFields: Set<ProgressionEditField> = emptySet(),
    val errors: Map<ProgressionEditField, String> = emptyMap()
)

data class ProgressionReviewUiState(
    val items: List<ProgressionReviewItemUi> = emptyList(),
    val edits: Map<Long, ProgressionEditDraft> = emptyMap(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val isWorking: Boolean = false,
    val message: String? = null
)

class ProgressionReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val progressionRepository: ProgressionRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {
    private val sessionId = savedStateHandle.get<Long>(SESSION_ID_KEY)?.takeIf { it > 0L }
    private val _uiState = MutableStateFlow(ProgressionReviewUiState())
    val uiState: StateFlow<ProgressionReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { progressionRepository.reconcileOutstandingSuggestions() }
                .onFailure { error -> showFailure(error) }

            combine(
                progressionRepository.observeReviewItems(sessionId),
                appPreferencesRepository.preferences
            ) { suggestions, preferences ->
                suggestions.map { suggestion -> suggestion.toUi() } to preferences.unitSystem
            }.collect { (items, unitSystem) ->
                _uiState.update { current ->
                    current.copy(
                        items = items,
                        edits = current.edits.filterKeys { id ->
                            items.any { item -> item.id == id && item.canDecide }
                        },
                        unitSystem = unitSystem
                    )
                }
            }
        }
    }

    fun beginEdit(suggestionId: Long) {
        val state = _uiState.value
        val item = state.items.firstOrNull { it.id == suggestionId && it.canDecide } ?: return
        if (suggestionId in state.edits) return
        val draft = item.newDraft(state.unitSystem) ?: return
        _uiState.update { it.copy(edits = it.edits + (suggestionId to draft)) }
    }

    fun updateEdit(
        suggestionId: Long,
        sets: String,
        reps: String,
        weight: String
    ) {
        val state = _uiState.value
        val item = state.items.firstOrNull { it.id == suggestionId && it.canDecide } ?: return
        val currentDraft = state.edits[suggestionId] ?: item.newDraft(state.unitSystem) ?: return
        val original = item.newDraft(currentDraft.unitSystem) ?: return
        val dirtyFields = buildSet {
            if (sets != original.sets) add(ProgressionEditField.SETS)
            if (reps != original.reps) add(ProgressionEditField.REPS)
            if (weight != original.weight) add(ProgressionEditField.WEIGHT)
        }
        val updated = currentDraft.copy(
            sets = sets,
            reps = reps,
            weight = weight,
            dirtyFields = dirtyFields,
            errors = emptyMap()
        )
        _uiState.update { it.copy(edits = it.edits + (suggestionId to updated)) }
    }

    fun dismissEdit(suggestionId: Long) {
        _uiState.update { it.copy(edits = it.edits - suggestionId) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun acceptOne(suggestionId: Long) {
        val state = _uiState.value
        if (state.isWorking) return
        val item = state.items.firstOrNull { it.id == suggestionId && it.canDecide } ?: return
        val proposed = item.proposed ?: return
        val draft = state.edits[suggestionId]
        val finalTarget = if (draft == null) {
            proposed
        } else {
            validateEditedTarget(item, draft) ?: return
        }
        accept(mapOf(suggestionId to finalTarget))
    }

    fun acceptAllSafe() {
        val state = _uiState.value
        if (state.isWorking) return
        val selected = state.items
            .asSequence()
            .filter { item ->
                item.status == ProgressionSuggestionStatus.PENDING &&
                    item.proposed != null &&
                    item.canDecide
            }
            .sortedByDescending(ProgressionReviewItemUi::sourceSessionId)
            .distinctBy { item -> Triple(item.planId, item.exerciseId, item.orderIndex) }
            .associate { item -> item.id to requireNotNull(item.proposed) }
        if (selected.isEmpty()) return
        accept(selected)
    }

    fun reject(suggestionId: Long) {
        val state = _uiState.value
        if (state.isWorking || state.items.none { it.id == suggestionId && it.canDecide }) return
        viewModelScope.launch {
            setWorking(true)
            try {
                progressionRepository.rejectSuggestion(suggestionId)
                _uiState.update { it.copy(edits = it.edits - suggestionId) }
            } catch (error: Throwable) {
                showFailure(error)
            } finally {
                setWorking(false)
            }
        }
    }

    private fun accept(finalTargets: Map<Long, ProgressionTarget>) {
        viewModelScope.launch {
            setWorking(true)
            try {
                when (val result = progressionRepository.acceptSuggestions(finalTargets)) {
                    is ProgressionDecisionResult.Accepted -> {
                        _uiState.update { current ->
                            current.copy(edits = current.edits - result.suggestionIds)
                        }
                        progressionRepository.reconcileOutstandingSuggestions()
                    }
                    is ProgressionDecisionResult.Stale -> {
                        _uiState.update {
                            it.copy(message = STALE_MESSAGE)
                        }
                        progressionRepository.reconcileOutstandingSuggestions()
                    }
                    is ProgressionDecisionResult.Invalid -> {
                        _uiState.update { it.copy(message = result.message) }
                    }
                }
            } catch (error: Throwable) {
                showFailure(error)
            } finally {
                setWorking(false)
            }
        }
    }

    private fun validateEditedTarget(
        item: ProgressionReviewItemUi,
        draft: ProgressionEditDraft
    ): ProgressionTarget? {
        val proposed = item.proposed ?: return null
        val errors = linkedMapOf<ProgressionEditField, String>()

        val sets = if (ProgressionEditField.SETS in draft.dirtyFields) {
            draft.sets.toIntOrNull()?.takeIf { it > 0 } ?: run {
                errors[ProgressionEditField.SETS] = SETS_ERROR
                proposed.sets
            }
        } else {
            proposed.sets
        }

        val reps = if (ProgressionEditField.REPS in draft.dirtyFields) {
            draft.reps.toIntOrNull()?.takeIf { it > 0 } ?: run {
                errors[ProgressionEditField.REPS] = REPS_ERROR
                proposed.reps
            }
        } else {
            proposed.reps
        }

        val weightKg = if (ProgressionEditField.WEIGHT in draft.dirtyFields) {
            draft.weight
                .trim()
                .replace(',', '.')
                .toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { WeightFormatting.convertToKg(it, draft.unitSystem) }
                ?: run {
                    errors[ProgressionEditField.WEIGHT] = WEIGHT_ERROR
                    proposed.weightKg
                }
        } else {
            proposed.weightKg
        }

        _uiState.update { current ->
            val latest = current.edits[item.id] ?: draft
            current.copy(edits = current.edits + (item.id to latest.copy(errors = errors)))
        }
        return if (errors.isEmpty()) ProgressionTarget(sets, reps, weightKg) else null
    }

    private fun setWorking(working: Boolean) {
        _uiState.update { it.copy(isWorking = working, message = if (working) null else it.message) }
    }

    private fun showFailure(error: Throwable) {
        val detail = error.message?.takeIf(String::isNotBlank)
        _uiState.update {
            it.copy(message = if (detail == null) ACTION_FAILED else "$ACTION_FAILED: $detail")
        }
    }

    private fun ProgressionSuggestion.toUi(): ProgressionReviewItemUi {
        val proposed = (outcome as? ProgressionOutcome.ProposeChange)?.proposedTarget
        return ProgressionReviewItemUi(
            id = id,
            sourceSessionId = sourceTarget.sessionId,
            planId = sourceTarget.planId,
            exerciseId = sourceTarget.exerciseId,
            orderIndex = sourceTarget.orderIndex,
            scheme = sourceTarget.config.scheme,
            source = sourceTarget.target,
            proposed = proposed,
            countedSets = countedSets,
            reasonCode = outcome.reasonCode,
            reasonArguments = outcome.reasonArguments,
            status = status,
            canDecide = status == ProgressionSuggestionStatus.PENDING && proposed != null,
            configuredStep = sourceTarget.config.configuredStep()
        )
    }

    private fun ProgressionReviewItemUi.newDraft(unitSystem: UnitSystem): ProgressionEditDraft? {
        val target = proposed ?: return null
        return ProgressionEditDraft(
            sets = target.sets.toString(),
            reps = target.reps.toString(),
            weight = editableNumber(WeightFormatting.convertToDisplay(target.weightKg, unitSystem)),
            unitSystem = unitSystem
        )
    }

    private fun ProgressionConfig.configuredStep(): WeightStep? = when (this) {
        is ProgressionConfig.Linear -> step
        is ProgressionConfig.DoubleProgression -> step
        is ProgressionConfig.TotalReps -> step
        is ProgressionConfig.RpeRir -> step
        is ProgressionConfig.Manual,
        is ProgressionConfig.Invalid -> null
    }

    private fun editableNumber(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
        const val STALE_MESSAGE = "Dieser Vorschlag passt nicht mehr zum Plan."
        const val ACTION_FAILED = "Aktion fehlgeschlagen"
        const val SETS_ERROR = "Gib eine positive Satzzahl ein."
        const val REPS_ERROR = "Gib eine positive Wiederholungszahl ein."
        const val WEIGHT_ERROR = "Gib ein gültiges Gewicht ein."
    }
}
