package com.ironlog.app.presentation.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.progression.ProgressionConfigValidator
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.workout.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class ProgressionField {
    STEP,
    MIN_REPS,
    MAX_REPS,
    TOTAL_REPS,
    TARGET_RPE,
    RPE_TOLERANCE,
    STALL_THRESHOLD,
    BACKOFF_PERCENT
}

fun progressionFieldForValidationPath(path: String): ProgressionField? = when (path) {
    "config.step.originalValue", "config.step.kilograms" -> ProgressionField.STEP
    "config.minReps" -> ProgressionField.MIN_REPS
    "config.maxReps" -> ProgressionField.MAX_REPS
    "config.targetTotalReps" -> ProgressionField.TOTAL_REPS
    "config.targetRpe" -> ProgressionField.TARGET_RPE
    "config.tolerance" -> ProgressionField.RPE_TOLERANCE
    "config.failurePolicy.stallThreshold" -> ProgressionField.STALL_THRESHOLD
    "config.failurePolicy.backoffPercent" -> ProgressionField.BACKOFF_PERCENT
    else -> null
}

data class ProgressionEditorUi(
    val exerciseIndex: Int,
    val scheme: ProgressionScheme,
    val step: String,
    val minReps: String,
    val maxReps: String,
    val totalReps: String,
    val targetRpe: String,
    val rpeTolerance: String,
    val stallThreshold: String,
    val backoffPercent: String,
    val unitSystem: UnitSystem,
    val originalStep: WeightStep? = null,
    val stepWasEdited: Boolean = false,
    val errors: Map<ProgressionField, String> = emptyMap()
)

data class PlanExerciseUi(
    val planExercise: PlanExercise,
    val exercise: Exercise,
    /**
     * Keep editable numeric values as text until the user commits the plan.
     * This lets a user clear a field or type an intermediate value such as
     * `.`/`2,` without the controlled field snapping back to the last number.
     */
    val targetSetsInput: String = planExercise.targetSets.takeIf { it > 0 }?.toString() ?: "",
    val targetRepsInput: String = planExercise.targetReps.takeIf { it > 0 }?.toString() ?: "",
    val targetWeightInput: String = "",
    val targetWeightInputUnit: UnitSystem = UnitSystem.METRIC,
    val targetWeightInputDirty: Boolean = false
)

data class PlanEditorUiState(
    val planName: String = "",
    val exercises: List<PlanExerciseUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val showExercisePicker: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val progressionEditor: ProgressionEditorUi? = null,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val targetInputErrors: Map<Int, Set<PlanExerciseNumericField>> = emptyMap()
)

enum class PlanExerciseNumericField {
    SETS,
    REPS,
    WEIGHT
}

class PlanEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val planRepository: TrainingPlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val planId: Long = savedStateHandle["planId"] ?: 0L
    val isEditMode: Boolean = planId > 0L

    private val _uiState = MutableStateFlow(PlanEditorUiState())
    val uiState: StateFlow<PlanEditorUiState> = _uiState

    init {
        observeUnitSystem()
        if (isEditMode) {
            loadPlan()
        }
    }

    private fun observeUnitSystem() {
        viewModelScope.launch {
            appPreferencesRepository.preferences
                .map { it.unitSystem }
                .distinctUntilChanged()
                .collect { unitSystem ->
                    val current = _uiState.value
                    if (current.unitSystem == unitSystem) return@collect
                    val exercises = current.exercises.map { item ->
                        if (item.targetWeightInputDirty) {
                            item
                        } else {
                            item.copy(
                                targetWeightInput = targetWeightInputFor(item.planExercise, unitSystem),
                                targetWeightInputUnit = unitSystem
                            )
                        }
                    }
                    // A preference change only changes the display unit. It is
                    // not an edit to the plan itself.
                    _uiState.value = current.copy(
                        unitSystem = unitSystem,
                        exercises = exercises
                    )
                }
        }
    }

    private fun loadPlan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, notFound = false)
            try {
                val plan = planRepository.getPlanById(planId)
                if (plan != null) {
                    val exerciseUis = plan.exercises.map { pe ->
                        val exercise = exerciseRepository.getExerciseById(pe.exerciseId)
                        val resolvedExercise = exercise ?: Exercise(
                                id = pe.exerciseId,
                                name = "Unbekannt",
                                primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST,
                                category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL
                            )
                        createPlanExerciseUi(
                            planExercise = pe.copy(exerciseName = resolvedExercise.name),
                            exercise = resolvedExercise,
                            unitSystem = _uiState.value.unitSystem
                        )
                    }
                    val normalizedExercises = normalizeExercises(exerciseUis)
                    _uiState.value = _uiState.value.copy(
                        planName = plan.name,
                        exercises = normalizedExercises,
                        isLoading = false,
                        notFound = false,
                        error = null,
                        hasUnsavedChanges = false,
                        targetInputErrors = emptyMap()
                    )
                } else {
                    // Plan was deleted (e.g. from another screen) or the id is invalid —
                    // surface an explicit not-found state instead of spinning forever.
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        notFound = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Plan konnte nicht geladen werden: ${e.message}"
                )
            }
        }
    }

    fun updatePlanName(name: String) {
        val current = _uiState.value
        if (current.isSaving || current.isSaved || current.planName == name) return
        _uiState.value = current.copy(
            planName = name,
            hasUnsavedChanges = true
        )
    }

    fun showExercisePicker() {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.value = _uiState.value.copy(showExercisePicker = true)
    }

    fun dismissExercisePicker() {
        _uiState.value = _uiState.value.copy(showExercisePicker = false)
    }

    fun addExercise(exercise: Exercise) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        val current = _uiState.value.exercises
        val newIndex = current.size
        val planExercise = PlanExercise(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            orderIndex = newIndex,
            supersetGroupId = null,
            targetSets = 3,
            targetReps = 10,
            targetWeightKg = 0.0
        )
        _uiState.value = _uiState.value.copy(showExercisePicker = false)
        setExercises(current + createPlanExerciseUi(
            planExercise = planExercise,
            exercise = exercise,
            unitSystem = _uiState.value.unitSystem
        ))
    }

    fun removeExercise(index: Int) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        val current = _uiState.value.exercises.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            setExercises(current)
        }
    }

    fun moveUp(index: Int) {
        if (_uiState.value.isSaving || _uiState.value.isSaved || index <= 0) return
        val current = _uiState.value.exercises.toMutableList()
        val item = current.removeAt(index)
        current.add(index - 1, item)
        setExercises(current)
    }

    fun moveDown(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (_uiState.value.isSaving || _uiState.value.isSaved || index >= current.size - 1) return
        val item = current.removeAt(index)
        current.add(index + 1, item)
        setExercises(current)
    }

    fun groupWithPrevious(index: Int) {
        val current = _uiState.value.exercises
        if (_uiState.value.isSaving || _uiState.value.isSaved || index <= 0 || index >= current.size) return

        val previousGroup = current[index - 1].planExercise.supersetGroupId
        val currentGroup = current[index].planExercise.supersetGroupId
        val nextGroupId = (current.maxOfOrNull { it.planExercise.supersetGroupId ?: 0 } ?: 0) + 1
        val targetGroupId = previousGroup ?: currentGroup ?: nextGroupId

        val grouped = current.mapIndexed { itemIndex, item ->
            val belongsToPreviousGroup = previousGroup != null && item.planExercise.supersetGroupId == previousGroup
            val belongsToCurrentGroup = currentGroup != null && item.planExercise.supersetGroupId == currentGroup
            val shouldGroup = itemIndex == index - 1 ||
                itemIndex == index ||
                belongsToPreviousGroup ||
                belongsToCurrentGroup

            if (shouldGroup) {
                item.copy(planExercise = item.planExercise.copy(supersetGroupId = targetGroupId))
            } else {
                item
            }
        }

        setExercises(grouped)
    }

    fun ungroup(index: Int) {
        val current = _uiState.value.exercises
        if (_uiState.value.isSaving || _uiState.value.isSaved || index !in current.indices) return

        val ungrouped = current.mapIndexed { itemIndex, item ->
            if (itemIndex == index) {
                item.copy(planExercise = item.planExercise.copy(supersetGroupId = null))
            } else {
                item
            }
        }
        setExercises(ungrouped)
    }

    fun updateTargetSets(index: Int, sets: Int) {
        updateTargetSetsInput(index, sets.toString())
    }

    fun updateTargetReps(index: Int, reps: Int) {
        updateTargetRepsInput(index, reps.toString())
    }

    fun updateTargetWeightDisplay(index: Int, displayWeight: Double) {
        updateTargetWeightInput(index, editableNumber(displayWeight))
    }

    fun updateTargetSetsInput(index: Int, value: String) {
        updateTargetInput(index, PlanExerciseNumericField.SETS, value) { item ->
            parsePositiveTargetInt(value)?.let { sets ->
                item.copy(planExercise = item.planExercise.copy(targetSets = sets))
            } ?: item
        }
    }

    fun updateTargetRepsInput(index: Int, value: String) {
        updateTargetInput(index, PlanExerciseNumericField.REPS, value) { item ->
            parsePositiveTargetInt(value)?.let { reps ->
                item.copy(planExercise = item.planExercise.copy(targetReps = reps))
            } ?: item
        }
    }

    fun updateTargetWeightInput(index: Int, value: String) {
        updateTargetInput(index, PlanExerciseNumericField.WEIGHT, value) { item ->
            val weightKg = if (value.isBlank()) {
                0.0
            } else {
                parseNonNegativeDecimal(value)
                    ?.let { WeightFormatting.convertToKg(it, item.targetWeightInputUnit) }
            }
            weightKg?.let { parsed ->
                item.copy(planExercise = item.planExercise.copy(targetWeightKg = parsed))
            } ?: item
        }
    }

    fun validateTargetInput(index: Int, field: PlanExerciseNumericField) {
        val current = _uiState.value
        if (current.isSaving || current.isSaved) return
        val item = current.exercises.getOrNull(index) ?: return
        val valid = when (field) {
            PlanExerciseNumericField.SETS -> parsePositiveTargetInt(item.targetSetsInput) != null
            PlanExerciseNumericField.REPS -> parsePositiveTargetInt(item.targetRepsInput) != null
            PlanExerciseNumericField.WEIGHT -> item.targetWeightInput.isBlank() ||
                parseNonNegativeDecimal(item.targetWeightInput)
                    ?.let { WeightFormatting.convertToKg(it, item.targetWeightInputUnit) }
                    ?.isFinite() == true
        }
        val fields = if (valid) {
            current.targetInputErrors[index].orEmpty() - field
        } else {
            current.targetInputErrors[index].orEmpty() + field
        }
        _uiState.value = current.copy(
            targetInputErrors = if (fields.isEmpty()) {
                current.targetInputErrors - index
            } else {
                current.targetInputErrors + (index to fields)
            }
        )
    }

    fun openProgressionEditor(index: Int) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        val item = _uiState.value.exercises.getOrNull(index) ?: return
        val unitSystem = _uiState.value.unitSystem
        _uiState.value = _uiState.value.copy(
            progressionEditor = draftFor(
                exerciseIndex = index,
                planExercise = item.planExercise,
                unitSystem = unitSystem
            )
        )
    }

    fun dismissProgressionEditor() {
        _uiState.value = _uiState.value.copy(progressionEditor = null)
    }

    /** A tap in the compact chooser commits to the plan draft and closes the sheet. */
    fun chooseProgressionScheme(scheme: ProgressionScheme) {
        selectProgressionScheme(scheme)
        saveProgressionEditor()
    }

    fun selectProgressionScheme(scheme: ProgressionScheme) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        val current = _uiState.value.progressionEditor ?: return
        val exercise = _uiState.value.exercises.getOrNull(current.exerciseIndex)?.planExercise ?: return
        val next = when {
            scheme == ProgressionScheme.MANUAL -> manualDraft(
                exerciseIndex = current.exerciseIndex,
                unitSystem = current.unitSystem
            )
            current.scheme == ProgressionScheme.MANUAL -> defaultActiveDraft(
                exerciseIndex = current.exerciseIndex,
                planExercise = exercise,
                unitSystem = current.unitSystem,
                scheme = scheme
            )
            else -> current.copy(scheme = scheme, errors = emptyMap())
        }
        _uiState.value = _uiState.value.copy(progressionEditor = next)
    }

    fun updateProgressionField(field: ProgressionField, value: String) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        val current = _uiState.value.progressionEditor ?: return
        val updated = when (field) {
            ProgressionField.STEP -> current.copy(
                step = value,
                stepWasEdited = true
            )
            ProgressionField.MIN_REPS -> current.copy(minReps = value)
            ProgressionField.MAX_REPS -> current.copy(maxReps = value)
            ProgressionField.TOTAL_REPS -> current.copy(totalReps = value)
            ProgressionField.TARGET_RPE -> current.copy(targetRpe = value)
            ProgressionField.RPE_TOLERANCE -> current.copy(rpeTolerance = value)
            ProgressionField.STALL_THRESHOLD -> current.copy(stallThreshold = value)
            ProgressionField.BACKOFF_PERCENT -> current.copy(backoffPercent = value)
        }
        _uiState.value = _uiState.value.copy(
            progressionEditor = updated.copy(errors = updated.errors - field)
        )
    }

    fun saveProgressionEditor() {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        val draft = _uiState.value.progressionEditor ?: return
        val item = _uiState.value.exercises.getOrNull(draft.exerciseIndex) ?: return
        val parsed = parseProgressionConfig(draft)
        val config = parsed.config
        if (config == null) {
            _uiState.value = _uiState.value.copy(
                progressionEditor = draft.copy(errors = parsed.errors)
            )
            return
        }

        val validationPaths = ProgressionConfigValidator.validationErrors(
            target = item.planExercise.toProgressionTarget(),
            config = config
        )
        if (validationPaths.isNotEmpty()) {
            val fieldErrors = parsed.errors + validationPaths.mapNotNull { path ->
                progressionFieldForValidationPath(path)?.let { it to path }
            }.toMap()
            _uiState.value = _uiState.value.copy(
                progressionEditor = draft.copy(errors = fieldErrors),
                error = if (fieldErrors.isEmpty()) {
                    "Progression für ${item.exercise.name} ist unvollständig"
                } else {
                    _uiState.value.error
                }
            )
            return
        }

        updateExercise(draft.exerciseIndex) { exercise ->
            exercise.copy(progressionConfig = config)
        }
        _uiState.value = _uiState.value.copy(progressionEditor = null)
    }

    private fun updateExercise(index: Int, transform: (PlanExercise) -> PlanExercise) {
        val current = _uiState.value.exercises.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(planExercise = transform(item.planExercise))
            setExercises(current)
        }
    }

    private fun updateTargetInput(
        index: Int,
        field: PlanExerciseNumericField,
        value: String,
        transform: (PlanExerciseUi) -> PlanExerciseUi
    ) {
        val current = _uiState.value
        if (current.isSaving || current.isSaved || index !in current.exercises.indices) return
        val exercises = current.exercises.toMutableList()
        val item = exercises[index]
        val updated = transform(
            when (field) {
                PlanExerciseNumericField.SETS -> item.copy(targetSetsInput = value)
                PlanExerciseNumericField.REPS -> item.copy(targetRepsInput = value)
                PlanExerciseNumericField.WEIGHT -> item.copy(
                    targetWeightInput = value,
                    targetWeightInputDirty = true
                )
            }
        )
        exercises[index] = updated
        _uiState.value = current.copy(
            exercises = exercises,
            hasUnsavedChanges = true,
            targetInputErrors = current.targetInputErrors
                .mapValues { (errorIndex, fields) ->
                    if (errorIndex == index) fields - field else fields
                }
                .filterValues { it.isNotEmpty() }
        )
    }

    fun updateSetTargets(index: Int, targets: List<com.ironlog.shared.plans.PlannedSet>) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        if (!com.ironlog.shared.plans.PlannedSets.valid(targets)) return
        val current = _uiState.value
        val row = current.exercises.getOrNull(index) ?: return
        val work = targets.filter { it.kind != "WARMUP" }
        val first = work.firstOrNull()
        val plan = row.planExercise.copy(
            setTargets = targets,
            targetSets = if (targets.isEmpty()) row.planExercise.targetSets else work.size,
            targetReps = first?.reps ?: row.planExercise.targetReps,
            targetWeightKg = first?.weightKg ?: row.planExercise.targetWeightKg,
            progressionConfig = if (targets.isEmpty()) row.planExercise.progressionConfig else ProgressionConfig.Manual()
        )
        setExercises(current.exercises.mapIndexed { i, item ->
            if (i == index) createPlanExerciseUi(plan, row.exercise, current.unitSystem) else item
        })
    }

    fun savePlan() {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return

        val name = _uiState.value.planName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Bitte gib einen Namen ein")
            return
        }

        val normalizedExercises = normalizeExercises(_uiState.value.exercises)
        if (normalizedExercises.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Bitte füge mindestens eine Übung hinzu."
            )
            return
        }

        val parsedExercises = parseTargetInputs(normalizedExercises)
        if (parsedExercises.errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                targetInputErrors = parsedExercises.errors,
                error = "Bitte korrigiere die markierten Trainingsziele."
            )
            return
        }

        val exercisesWithTargets = parsedExercises.exercises
        val invalidExercise = exercisesWithTargets.firstOrNull { item ->
            ProgressionConfigValidator.validationErrors(
                target = item.planExercise.toProgressionTarget(),
                config = item.planExercise.progressionConfig
            ).isNotEmpty()
        }
        if (invalidExercise != null) {
            _uiState.value = _uiState.value.copy(
                error = "Progression für ${invalidExercise.exercise.name} ist unvollständig"
            )
            return
        }

        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val exercises = exercisesWithTargets.map { it.planExercise }
                val plan = TrainingPlan(
                    id = planId,
                    name = name,
                    exercises = exercises
                )
                planRepository.savePlan(plan)
                val cleanExercises = exercisesWithTargets.map { item ->
                    item.copy(
                        targetWeightInput = targetWeightInputFor(
                            item.planExercise,
                            _uiState.value.unitSystem
                        ),
                        targetWeightInputUnit = _uiState.value.unitSystem,
                        targetWeightInputDirty = false
                    )
                }
                _uiState.value = _uiState.value.copy(
                    exercises = cleanExercises,
                    isSaved = true,
                    isSaving = false,
                    hasUnsavedChanges = false,
                    targetInputErrors = emptyMap()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Plan konnte nicht gespeichert werden: ${e.message}",
                    isSaving = false
                )
            }
        }
    }

    private fun setExercises(exercises: List<PlanExerciseUi>) {
        _uiState.value = _uiState.value.copy(
            exercises = normalizeExercises(exercises),
            hasUnsavedChanges = true,
            targetInputErrors = emptyMap()
        )
    }

    private fun normalizeExercises(exercises: List<PlanExerciseUi>): List<PlanExerciseUi> {
        if (exercises.isEmpty()) return emptyList()

        val reindexed = exercises
            .mapIndexed { index, item ->
                item.copy(planExercise = item.planExercise.copy(orderIndex = index))
            }
            .toMutableList()

        // Collapse singleton runs and split reused non-contiguous IDs into independent runs.
        var cursor = 0
        while (cursor < reindexed.size) {
            val runGroupId = reindexed[cursor].planExercise.supersetGroupId
            if (runGroupId == null) {
                cursor++
                continue
            }
            var endExclusive = cursor + 1
            while (
                endExclusive < reindexed.size &&
                reindexed[endExclusive].planExercise.supersetGroupId == runGroupId
            ) {
                endExclusive++
            }
            if (endExclusive - cursor < 2) {
                for (index in cursor until endExclusive) {
                    val item = reindexed[index]
                    reindexed[index] = item.copy(
                        planExercise = item.planExercise.copy(supersetGroupId = null)
                    )
                }
            }
            cursor = endExclusive
        }

        // Reassign visible runs to compact IDs (S1..Sn).
        var nextGroupId = 1
        cursor = 0
        while (cursor < reindexed.size) {
            val runGroupId = reindexed[cursor].planExercise.supersetGroupId
            if (runGroupId == null) {
                cursor++
                continue
            }
            var endExclusive = cursor + 1
            while (
                endExclusive < reindexed.size &&
                reindexed[endExclusive].planExercise.supersetGroupId == runGroupId
            ) {
                endExclusive++
            }
            val normalizedGroupId = nextGroupId++
            for (index in cursor until endExclusive) {
                val item = reindexed[index]
                reindexed[index] = item.copy(
                    planExercise = item.planExercise.copy(supersetGroupId = normalizedGroupId)
                )
            }
            cursor = endExclusive
        }

        return reindexed
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun onPickerError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    private fun draftFor(
        exerciseIndex: Int,
        planExercise: PlanExercise,
        unitSystem: UnitSystem
    ): ProgressionEditorUi {
        val config = planExercise.progressionConfig
        if (config is ProgressionConfig.Manual) {
            return manualDraft(exerciseIndex, unitSystem)
        }
        if (config is ProgressionConfig.Invalid) {
            return if (config.scheme == ProgressionScheme.MANUAL) {
                manualDraft(exerciseIndex, unitSystem)
            } else {
                defaultActiveDraft(exerciseIndex, planExercise, unitSystem, config.scheme)
            }
        }

        val step = when (config) {
            is ProgressionConfig.Linear -> config.step
            is ProgressionConfig.DoubleProgression -> config.step
            is ProgressionConfig.TotalReps -> config.step
            is ProgressionConfig.RpeRir -> config.step
            is ProgressionConfig.Invalid,
            is ProgressionConfig.Manual -> error("Handled above")
        }
        val failurePolicy = when (config) {
            is ProgressionConfig.Linear -> config.failurePolicy
            is ProgressionConfig.DoubleProgression -> config.failurePolicy
            is ProgressionConfig.TotalReps -> config.failurePolicy
            is ProgressionConfig.RpeRir -> config.failurePolicy
            is ProgressionConfig.Invalid,
            is ProgressionConfig.Manual -> error("Handled above")
        }
        val base = defaultActiveDraft(
            exerciseIndex = exerciseIndex,
            planExercise = planExercise,
            unitSystem = unitSystem,
            scheme = config.scheme
        )
        return base.copy(
            step = editableNumber(WeightFormatting.convertToDisplay(step.kilograms, unitSystem)),
            minReps = if (config is ProgressionConfig.DoubleProgression) {
                config.minReps.toString()
            } else {
                base.minReps
            },
            maxReps = if (config is ProgressionConfig.DoubleProgression) {
                config.maxReps.toString()
            } else {
                base.maxReps
            },
            totalReps = if (config is ProgressionConfig.TotalReps) {
                config.targetTotalReps.toString()
            } else {
                base.totalReps
            },
            targetRpe = if (config is ProgressionConfig.RpeRir) {
                editableNumber(config.targetRpe)
            } else {
                base.targetRpe
            },
            rpeTolerance = if (config is ProgressionConfig.RpeRir) {
                editableNumber(config.tolerance)
            } else {
                base.rpeTolerance
            },
            stallThreshold = failurePolicy.stallThreshold.toString(),
            backoffPercent = editableNumber(failurePolicy.backoffPercent),
            originalStep = step,
            stepWasEdited = false
        )
    }

    private fun manualDraft(
        exerciseIndex: Int,
        unitSystem: UnitSystem
    ) = ProgressionEditorUi(
        exerciseIndex = exerciseIndex,
        scheme = ProgressionScheme.MANUAL,
        step = "",
        minReps = "",
        maxReps = "",
        totalReps = "",
        targetRpe = "",
        rpeTolerance = "",
        stallThreshold = "",
        backoffPercent = "",
        unitSystem = unitSystem
    )

    private fun defaultActiveDraft(
        exerciseIndex: Int,
        planExercise: PlanExercise,
        unitSystem: UnitSystem,
        scheme: ProgressionScheme
    ) = ProgressionEditorUi(
        exerciseIndex = exerciseIndex,
        scheme = scheme,
        step = if (unitSystem == UnitSystem.IMPERIAL) "5" else "2.5",
        minReps = planExercise.targetReps.toString(),
        maxReps = (planExercise.targetReps + 2).toString(),
        totalReps = planExercise.targetSets.toLong().times(planExercise.targetReps).toString(),
        targetRpe = "8",
        rpeTolerance = "0.5",
        stallThreshold = "2",
        backoffPercent = "10",
        unitSystem = unitSystem,
        originalStep = null,
        stepWasEdited = true
    )

    private fun parseProgressionConfig(draft: ProgressionEditorUi): ParsedProgressionConfig {
        if (draft.scheme == ProgressionScheme.MANUAL) {
            return ParsedProgressionConfig(ProgressionConfig.Manual(), emptyMap())
        }

        val errors = linkedMapOf<ProgressionField, String>()
        val step = if (!draft.stepWasEdited && draft.originalStep != null) {
            draft.originalStep
        } else {
            parseDecimal(draft.step)?.takeIf { it.isFinite() }?.let { originalValue ->
                WeightStep(
                    originalValue = originalValue,
                    originalUnit = draft.unitSystem,
                    kilograms = WeightFormatting.convertToKg(originalValue, draft.unitSystem)
                )
            } ?: run {
                errors[ProgressionField.STEP] = "config.step.originalValue"
                null
            }
        }
        val stallThreshold = parseInteger(draft.stallThreshold) ?: run {
            errors[ProgressionField.STALL_THRESHOLD] = "config.failurePolicy.stallThreshold"
            null
        }
        val backoffPercent = parseDecimal(draft.backoffPercent)?.takeIf { it.isFinite() } ?: run {
            errors[ProgressionField.BACKOFF_PERCENT] = "config.failurePolicy.backoffPercent"
            null
        }

        var minReps: Int? = null
        var maxReps: Int? = null
        var totalReps: Long? = null
        var targetRpe: Double? = null
        var tolerance: Double? = null
        when (draft.scheme) {
            ProgressionScheme.MANUAL,
            ProgressionScheme.LINEAR -> Unit
            ProgressionScheme.DOUBLE -> {
                minReps = parseInteger(draft.minReps) ?: run {
                    errors[ProgressionField.MIN_REPS] = "config.minReps"
                    null
                }
                maxReps = parseInteger(draft.maxReps) ?: run {
                    errors[ProgressionField.MAX_REPS] = "config.maxReps"
                    null
                }
            }
            ProgressionScheme.TOTAL_REPS -> {
                totalReps = parseLongInteger(draft.totalReps) ?: run {
                    errors[ProgressionField.TOTAL_REPS] = "config.targetTotalReps"
                    null
                }
            }
            ProgressionScheme.RPE_RIR -> {
                targetRpe = parseDecimal(draft.targetRpe)?.takeIf { it.isFinite() } ?: run {
                    errors[ProgressionField.TARGET_RPE] = "config.targetRpe"
                    null
                }
                tolerance = parseDecimal(draft.rpeTolerance)?.takeIf { it.isFinite() } ?: run {
                    errors[ProgressionField.RPE_TOLERANCE] = "config.tolerance"
                    null
                }
            }
        }

        if (errors.isNotEmpty()) {
            return ParsedProgressionConfig(null, errors)
        }

        val validStep = requireNotNull(step)
        val failurePolicy = FailurePolicy(
            stallThreshold = requireNotNull(stallThreshold),
            backoffPercent = requireNotNull(backoffPercent)
        )
        val config = when (draft.scheme) {
            ProgressionScheme.MANUAL -> ProgressionConfig.Manual()
            ProgressionScheme.LINEAR -> ProgressionConfig.Linear(validStep, failurePolicy)
            ProgressionScheme.DOUBLE -> ProgressionConfig.DoubleProgression(
                minReps = requireNotNull(minReps),
                maxReps = requireNotNull(maxReps),
                step = validStep,
                failurePolicy = failurePolicy
            )
            ProgressionScheme.TOTAL_REPS -> ProgressionConfig.TotalReps(
                targetTotalReps = requireNotNull(totalReps),
                step = validStep,
                failurePolicy = failurePolicy
            )
            ProgressionScheme.RPE_RIR -> ProgressionConfig.RpeRir(
                targetRpe = requireNotNull(targetRpe),
                tolerance = requireNotNull(tolerance),
                step = validStep,
                failurePolicy = failurePolicy
            )
        }
        return ParsedProgressionConfig(config, errors)
    }

    private fun parseInteger(value: String): Int? {
        val parsed = parseDecimal(value) ?: return null
        if (!parsed.isFinite() || parsed % 1.0 != 0.0 || parsed !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            return null
        }
        return parsed.toInt()
    }

    private fun parseLongInteger(value: String): Long? {
        val parsed = parseDecimal(value) ?: return null
        if (!parsed.isFinite() || parsed % 1.0 != 0.0 || parsed !in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            return null
        }
        return parsed.toLong()
    }

    private fun parsePositiveInt(value: String): Int? =
        parseInteger(value)?.takeIf { it > 0 }

    private fun parseNonNegativeDecimal(value: String): Double? =
        parseDecimal(value)?.takeIf { it.isFinite() && it >= 0.0 }

    private fun parseTargetInputs(
        exercises: List<PlanExerciseUi>
    ): ParsedTargetInputs {
        val errors = linkedMapOf<Int, Set<PlanExerciseNumericField>>()
        val parsed = exercises.mapIndexed { index, item ->
            val sets = parsePositiveTargetInt(item.targetSetsInput)
            val reps = parsePositiveTargetInt(item.targetRepsInput)
            val weight = if (item.targetWeightInput.isBlank()) {
                0.0
            } else {
                parseNonNegativeDecimal(item.targetWeightInput)?.let {
                    WeightFormatting.convertToKg(it, item.targetWeightInputUnit)
                }
            }

            val invalidFields = buildSet {
                if (sets == null) add(PlanExerciseNumericField.SETS)
                if (reps == null) add(PlanExerciseNumericField.REPS)
                if (weight == null || !weight.isFinite() || weight < 0.0) {
                    add(PlanExerciseNumericField.WEIGHT)
                }
            }
            if (invalidFields.isNotEmpty()) {
                errors[index] = invalidFields
            }

            item.copy(
                planExercise = item.planExercise.copy(
                    targetSets = sets ?: item.planExercise.targetSets,
                    targetReps = reps ?: item.planExercise.targetReps,
                    targetWeightKg = weight ?: item.planExercise.targetWeightKg
                )
            )
        }
        return ParsedTargetInputs(parsed, errors)
    }

    private fun parsePositiveTargetInt(value: String): Int? {
        val normalized = value.trim()
        if (normalized.contains('.') || normalized.contains(',')) return null
        return parsePositiveInt(normalized)
    }

    private fun createPlanExerciseUi(
        planExercise: PlanExercise,
        exercise: Exercise,
        unitSystem: UnitSystem
    ): PlanExerciseUi = PlanExerciseUi(
        planExercise = planExercise,
        exercise = exercise,
        targetSetsInput = planExercise.targetSets.takeIf { it > 0 }?.toString() ?: "",
        targetRepsInput = planExercise.targetReps.takeIf { it > 0 }?.toString() ?: "",
        targetWeightInput = targetWeightInputFor(planExercise, unitSystem),
        targetWeightInputUnit = unitSystem,
        targetWeightInputDirty = false
    )

    private fun targetWeightInputFor(planExercise: PlanExercise, unitSystem: UnitSystem): String =
        if (planExercise.targetWeightKg == 0.0) {
            ""
        } else if (planExercise.targetWeightKg.isFinite()) {
            editableNumber(WeightFormatting.convertToDisplay(planExercise.targetWeightKg, unitSystem))
        } else {
            planExercise.targetWeightKg.toString()
        }

    private fun editableNumber(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun PlanExercise.toProgressionTarget() = ProgressionTarget(
        sets = targetSets,
        reps = targetReps,
        weightKg = targetWeightKg
    )

    private data class ParsedProgressionConfig(
        val config: ProgressionConfig?,
        val errors: Map<ProgressionField, String>
    )

    private data class ParsedTargetInputs(
        val exercises: List<PlanExerciseUi>,
        val errors: Map<Int, Set<PlanExerciseNumericField>>
    )
}
