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
    val exercise: Exercise
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
    val progressionEditor: ProgressionEditorUi? = null
)

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
                    _uiState.value = _uiState.value.copy(unitSystem = unitSystem)
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
                        PlanExerciseUi(
                            planExercise = pe.copy(exerciseName = exercise?.name ?: "Unbekannt"),
                            exercise = exercise ?: Exercise(
                                id = pe.exerciseId,
                                name = "Unbekannt",
                                primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST,
                                category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL
                            )
                        )
                    }
                    val normalizedExercises = normalizeExercises(exerciseUis)
                    _uiState.value = _uiState.value.copy(
                        planName = plan.name,
                        exercises = normalizedExercises,
                        isLoading = false,
                        notFound = false,
                        error = null
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
        _uiState.value = _uiState.value.copy(planName = name)
    }

    fun showExercisePicker() {
        _uiState.value = _uiState.value.copy(showExercisePicker = true)
    }

    fun dismissExercisePicker() {
        _uiState.value = _uiState.value.copy(showExercisePicker = false)
    }

    fun addExercise(exercise: Exercise) {
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
        setExercises(current + PlanExerciseUi(planExercise = planExercise, exercise = exercise))
    }

    fun removeExercise(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            setExercises(current)
        }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        val current = _uiState.value.exercises.toMutableList()
        val item = current.removeAt(index)
        current.add(index - 1, item)
        setExercises(current)
    }

    fun moveDown(index: Int) {
        val current = _uiState.value.exercises.toMutableList()
        if (index >= current.size - 1) return
        val item = current.removeAt(index)
        current.add(index + 1, item)
        setExercises(current)
    }

    fun groupWithPrevious(index: Int) {
        val current = _uiState.value.exercises
        if (index <= 0 || index >= current.size) return

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
        if (index !in current.indices) return

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
        updateExercise(index) { it.copy(targetSets = sets) }
    }

    fun updateTargetReps(index: Int, reps: Int) {
        updateExercise(index) { it.copy(targetReps = reps) }
    }

    fun updateTargetWeightDisplay(index: Int, displayWeight: Double) {
        val weightKg = WeightFormatting.convertToKg(displayWeight, _uiState.value.unitSystem)
        updateExercise(index) { it.copy(targetWeightKg = weightKg) }
    }

    fun openProgressionEditor(index: Int) {
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

    fun selectProgressionScheme(scheme: ProgressionScheme) {
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

    fun savePlan() {
        val name = _uiState.value.planName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Bitte gib einen Namen ein")
            return
        }

        val normalizedExercises = normalizeExercises(_uiState.value.exercises)
        val invalidExercise = normalizedExercises.firstOrNull { item ->
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

        viewModelScope.launch {
            try {
                val exercises = normalizedExercises.map { it.planExercise }
                val plan = TrainingPlan(
                    id = planId,
                    name = name,
                    exercises = exercises
                )
                planRepository.savePlan(plan)
                _uiState.value = _uiState.value.copy(
                    exercises = normalizedExercises,
                    isSaved = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Plan konnte nicht gespeichert werden: ${e.message}"
                )
            }
        }
    }

    private fun setExercises(exercises: List<PlanExerciseUi>) {
        _uiState.value = _uiState.value.copy(exercises = normalizeExercises(exercises))
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
}
