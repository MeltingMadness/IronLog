package com.ironlog.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.DeloadMode
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.PreviousSessionScope
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.AppLogger
import com.ironlog.app.domain.util.RpeAutoregulation
import com.ironlog.app.domain.util.WorkoutNumericValidation
import com.ironlog.shared.readinessdata.SetIntention
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface WorkoutExerciseKey {
    data class Planned(val snapshotId: Long) : WorkoutExerciseKey
    data class AdHoc(val exerciseId: Long) : WorkoutExerciseKey
}

data class PreviousExerciseSessionUi(
    val sessionId: Long,
    val sessionStart: LocalDateTime,
    val sets: List<WorkoutSet>,
    val lastWorkSetWeightKg: Double?,
    val lastWorkSetReachedTarget: Boolean = false
)

data class ExerciseWithSets(
    val key: WorkoutExerciseKey,
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val planTarget: WorkoutPlanTarget? = null,
    val originalPlanTarget: WorkoutPlanTarget? = null,
    val previousSession: PreviousExerciseSessionUi? = null
) {
    val supersetGroupId: Int?
        get() = planTarget?.supersetGroupId
}

sealed interface ActiveWorkoutSessionPhase {
    data object Loading : ActiveWorkoutSessionPhase
    data class Active(val session: WorkoutSession) : ActiveWorkoutSessionPhase
    data object Missing : ActiveWorkoutSessionPhase
}

sealed interface WorkoutFinishState {
    data object Idle : WorkoutFinishState
    data object Completing : WorkoutFinishState
    data object Generating : WorkoutFinishState
    data class ReviewReady(val sessionId: Long) : WorkoutFinishState
    data object CompletedWithoutReview : WorkoutFinishState
    data class GenerationFailed(val sessionId: Long, val message: String) : WorkoutFinishState
}

data class RestTimerUi(
    val startTime: Instant,
    val durationSeconds: Int
)

/**
 * Intra-session autoregulation hint for the next work set of an exercise,
 * derived from the RPE of the last logged work set (see [RpeAutoregulation]).
 */
data class NextSetRecommendationUi(
    val recommendedWeightKg: Double,
    val lastWeightKg: Double,
    val lastRpe: Double,
    val targetRpe: Double?,
    val backoffPercent: Double = RpeAutoregulation.DEFAULT_BACKOFF_PERCENT
) {
    val isOvershoot: Boolean
        get() = lastRpe >= RpeAutoregulation.OVERSHOOT_RPE

    /** Dedicated backoff-set load; only meaningful after an overshoot. */
    val backoffWeightKg: Double?
        get() = if (isOvershoot) {
            RpeAutoregulation.backoffSetWeightKg(lastWeightKg, backoffPercent)
        } else {
            null
        }
}

/**
 * Describes the last failed mutation so the UI can offer a real retry without
 * storing composable callbacks inside the ViewModel.
 */
sealed interface WorkoutRetryDescriptor {
    data class LogSet(
        val key: WorkoutExerciseKey,
        val exerciseId: Long,
        val reps: Int,
        val weightKg: Double,
        val setType: SetType,
        val intensity: String,
        val submissionId: Long,
        val intention: SetIntention
    ) : WorkoutRetryDescriptor

    data class UpdateSet(
        val setId: Long,
        val reps: Int,
        val weightKg: Double,
        val intensity: String,
        val intention: SetIntention?
    ) : WorkoutRetryDescriptor

    data class DeleteSet(val setId: Long) : WorkoutRetryDescriptor

    data class FinishWorkout(val discardEmptySession: Boolean) : WorkoutRetryDescriptor
}

data class WorkoutErrorUi(
    val message: String,
    val retry: WorkoutRetryDescriptor?,
    val id: Long
)

data class ActiveWorkoutUiState(
    val sessionPhase: ActiveWorkoutSessionPhase = ActiveWorkoutSessionPhase.Loading,
    val exercisesWithSets: List<ExerciseWithSets> = emptyList(),
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimers: Map<WorkoutExerciseKey, RestTimerUi> = emptyMap(),
    val nextSetRecommendations: Map<WorkoutExerciseKey, NextSetRecommendationUi> = emptyMap(),
    val error: WorkoutErrorUi? = null,
    val logInFlightByExercise: Map<WorkoutExerciseKey, Int> = emptyMap(),
    val logSuccessSubmissions: Set<Long> = emptySet(),
    val updateInFlightBySet: Map<Long, Int> = emptyMap(),
    val updateSuccessCountBySet: Map<Long, Int> = emptyMap(),
    /**
     * Why each logged set ended, keyed by durable set id. A set without a record is
     * [SetIntention.UNKNOWN]; the map never invents an answer for legacy data.
     */
    val setIntentions: Map<Long, SetIntention> = emptyMap(),
    /**
     * True once the stored intentions were read at least once. While false the picker must
     * not present [SetIntention.UNKNOWN] as if it were a stored answer.
     */
    val setIntentionsLoaded: Boolean = false,
    /**
     * True when the readiness side channel could not be read. The failure is surfaced in
     * the UI and never crashes the view model scope.
     */
    val setIntentionsFailed: Boolean = false,
    val finishState: WorkoutFinishState = WorkoutFinishState.Idle
)

private data class ActiveWorkoutChromeState(
    val showExercisePicker: Boolean = false,
    val showFinishDialog: Boolean = false,
    val restTimers: Map<WorkoutExerciseKey, RestTimerUi> = emptyMap(),
    val error: WorkoutErrorUi? = null
)

private data class OperationUiState(
    val logInFlightByExercise: Map<WorkoutExerciseKey, Int> = emptyMap(),
    val logSuccessSubmissions: Set<Long> = emptySet(),
    val updateInFlightBySet: Map<Long, Int> = emptyMap(),
    val updateSuccessCountBySet: Map<Long, Int> = emptyMap(),
    val finishState: WorkoutFinishState = WorkoutFinishState.Idle
)

private val submissionIdSequence = java.util.concurrent.atomic.AtomicLong(0L)

internal fun nextSubmissionId(): Long = submissionIdSequence.incrementAndGet()

sealed class WorkoutEvent {
    data class NewRecord(val exerciseName: String, val type: RecordType) : WorkoutEvent()
}

/**
 * Normalizes user-typed decimal input (weight/intensity) so that comma decimal
 * separators (common on non-US keyboards) are parsed the same as dots.
 */
fun parseDecimal(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()

fun lastWorkSetReachedTarget(
    planTarget: WorkoutPlanTarget?,
    previousSets: List<WorkoutSet>
): Boolean {
    val target = planTarget ?: return false
    if (target.target.reps <= 0 || target.target.weightKg <= 0.0) return false
    val lastWorkSet = previousSets.lastOrNull { !it.isWarmup } ?: return false
    return lastWorkSet.reps >= target.target.reps &&
        lastWorkSet.weightKg >= target.target.weightKg
}

/**
 * Passt ein Planziel an den aktiven Deload-Modus an (nur Anzeigeebene — die
 * gespeicherten Planziele und die Progressions-Auswertung bleiben unverändert).
 * Ohne aktiven Modus wird das Ziel unverändert zurückgegeben.
 */
internal fun applyDeloadToTarget(
    target: WorkoutPlanTarget,
    deloadMode: DeloadMode?
): WorkoutPlanTarget = when (deloadMode) {
    null -> target
    DeloadMode.HALVE_SET_VOLUME -> target.copy(
        target = target.target.copy(
            sets = maxOf(1, (target.target.sets + 1) / 2)
        ),
        setTargets = target.setTargets.let { slots ->
            var work = 0
            slots.filter { it.kind == "WARMUP" || ++work <= maxOf(1, (target.target.sets + 1) / 2) }
        }
    )
    DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT -> target.copy(
        target = target.target.copy(
            weightKg = roundToOneDecimal(target.target.weightKg * 0.85)
        ),
        setTargets = target.setTargets.map { it.copy(weightKg = roundToOneDecimal(it.weightKg * 0.85)) }
    )
}

private fun roundToOneDecimal(value: Double): Double =
    kotlin.math.round(value * 10.0) / 10.0

/**
 * Returns whether logging [newSetType] completes the planned slots for one exact row.
 * Planned slots are fulfilled by NORMAL sets only; warmups, drop sets and failure sets remain
 * useful workout evidence but must not make a planned exercise appear complete. The active
 * deload mode changes the effective set target used for this decision.
 */
internal fun isPlannedExerciseComplete(
    planTarget: WorkoutPlanTarget?,
    deloadMode: DeloadMode?,
    previousSets: List<WorkoutSet>,
    newSetType: SetType
): Boolean {
    val effectiveTarget = planTarget?.let { applyDeloadToTarget(it, deloadMode) } ?: return false
    val targetSetCount = effectiveTarget.target.sets
    if (targetSetCount <= 0) return false

    val normalSetCount = previousSets.count { it.setType == SetType.NORMAL } +
        if (newSetType == SetType.NORMAL) 1 else 0
    return normalSetCount >= targetSetCount
}

private fun configuredBackoffPercent(config: ProgressionConfig?): Double = when (config) {
    is ProgressionConfig.Linear -> config.failurePolicy.backoffPercent
    is ProgressionConfig.DoubleProgression -> config.failurePolicy.backoffPercent
    is ProgressionConfig.TotalReps -> config.failurePolicy.backoffPercent
    is ProgressionConfig.RpeRir -> config.failurePolicy.backoffPercent
    null,
    is ProgressionConfig.Manual,
    is ProgressionConfig.Invalid -> RpeAutoregulation.DEFAULT_BACKOFF_PERCENT
}

private const val REST_TIMER_PAYLOAD_SEPARATOR = "#"
private const val REST_TIMER_STATE_SEPARATOR = ";"
private const val REST_TIMER_FIELD_SEPARATOR = ","
private const val REST_TIMER_PLANNED = "planned"
private const val REST_TIMER_AD_HOC = "adhoc"

private fun encodeRestTimerState(
    timers: Map<WorkoutExerciseKey, RestTimerUi>,
    sessionStartEpochMillis: Long
): String {
    val records = timers.entries.joinToString(REST_TIMER_STATE_SEPARATOR) { (key, timer) ->
        val keyType = when (key) {
            is WorkoutExerciseKey.Planned -> REST_TIMER_PLANNED
            is WorkoutExerciseKey.AdHoc -> REST_TIMER_AD_HOC
        }
        val keyId = when (key) {
            is WorkoutExerciseKey.Planned -> key.snapshotId
            is WorkoutExerciseKey.AdHoc -> key.exerciseId
        }
        listOf(
            keyType,
            keyId.toString(),
            timer.startTime.toEpochMilli().toString(),
            timer.durationSeconds.toString()
        ).joinToString(REST_TIMER_FIELD_SEPARATOR)
    }
    return sessionStartEpochMillis.toString() + REST_TIMER_PAYLOAD_SEPARATOR + records
}

private fun decodeRestTimerState(
    encodedState: String,
    expectedSessionStartEpochMillis: Long
): Map<WorkoutExerciseKey, RestTimerUi> {
    val payloadSeparatorIndex = encodedState.indexOf(REST_TIMER_PAYLOAD_SEPARATOR)
    if (payloadSeparatorIndex <= 0) return emptyMap()
    val storedSessionStart = encodedState
        .substring(0, payloadSeparatorIndex)
        .toLongOrNull()
        ?: return emptyMap()
    if (storedSessionStart != expectedSessionStartEpochMillis) return emptyMap()

    val records = encodedState.substring(payloadSeparatorIndex + 1)
    if (records.isBlank()) return emptyMap()
    return records.split(REST_TIMER_STATE_SEPARATOR).mapNotNull { record ->
        val fields = record.split(REST_TIMER_FIELD_SEPARATOR, limit = 4)
        if (fields.size != 4) return@mapNotNull null
        val keyId = fields[1].toLongOrNull() ?: return@mapNotNull null
        val startEpochMillis = fields[2].toLongOrNull() ?: return@mapNotNull null
        val durationSeconds = fields[3].toIntOrNull()?.takeIf { it >= 0 }
            ?: return@mapNotNull null
        val key = when (fields[0]) {
            REST_TIMER_PLANNED -> WorkoutExerciseKey.Planned(keyId)
            REST_TIMER_AD_HOC -> WorkoutExerciseKey.AdHoc(keyId)
            else -> return@mapNotNull null
        }
        key to RestTimerUi(
            startTime = Instant.ofEpochMilli(startEpochMillis),
            durationSeconds = durationSeconds
        )
    }.toMap()
}

class ActiveWorkoutViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statisticsRepository: StatisticsRepository,
    private val progressionRepository: ProgressionRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L
    private val planId: Long = savedStateHandle["planId"] ?: 0L
    private val metaPlanId: Long = savedStateHandle["metaPlanId"] ?: 0L

    private val showExercisePicker = MutableStateFlow(false)
    private val showFinishDialog = MutableStateFlow(false)
    private val addedExercises = MutableStateFlow<List<Exercise>>(emptyList())
    private val _error = MutableStateFlow<WorkoutErrorUi?>(null)
    private val _restTimers = MutableStateFlow<Map<WorkoutExerciseKey, RestTimerUi>>(emptyMap())
    private var restTimerSessionStartEpochMillis: Long? = null
    /**
     * Serializes restore, in-memory timer updates and their durable writes. The restore read can
     * suspend on DataStore; keeping it under this mutex prevents a later cleanup write from
     * racing a timer mutation that started while the read was in flight.
     */
    private val restTimerMutationMutex = Mutex()
    private var restTimerMutationGeneration = 0L
    private val operationState = MutableStateFlow(OperationUiState())
    private var errorSequence = 0L
    private val mutationMutex = Mutex()

    private val sessionPhase = workoutRepository.observeSessionById(sessionId)
        .map<WorkoutSession?, ActiveWorkoutSessionPhase> { session ->
            if (session != null) {
                ActiveWorkoutSessionPhase.Active(session)
            } else {
                ActiveWorkoutSessionPhase.Missing
            }
        }
        .onStart { emit(ActiveWorkoutSessionPhase.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutSessionPhase.Loading)

    private val sessionSets = workoutRepository.getSetsForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val planTargets = progressionRepository.observeTargetsForSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val shareWeightHistoryAcrossContexts =
        appPreferencesRepository.preferences
            .map { it.shareWeightHistoryAcrossContexts }
            .distinctUntilChanged()

    private val deloadMode =
        appPreferencesRepository.preferences
            .map { it.deloadMode }
            .distinctUntilChanged()

    private val exercisesWithSets = combine(
        sessionSets,
        addedExercises,
        planTargets,
        shareWeightHistoryAcrossContexts,
        deloadMode
    ) { sets, added, targets, shareAcrossContexts, activeDeloadMode ->
        val orderedTargets = targets.sortedWith(
            compareBy(WorkoutPlanTarget::orderIndex, WorkoutPlanTarget::id)
        )
        val adHocExercises = added.distinctBy { it.id }
        val exerciseIds = (
            orderedTargets.map { it.exerciseId } + adHocExercises.map { it.id }
        ).distinct()
        val exercisesById = exerciseRepository.getExercisesByIds(exerciseIds).associateBy { it.id }
        val previousSessionsByExercise = try {
            workoutRepository.getPreviousSessionDataForExercises(
                currentSessionId = sessionId,
                exerciseIds = exerciseIds,
                scope = previousSessionScope(
                    planId = planId,
                    metaPlanId = metaPlanId,
                    shareAcrossContexts = shareAcrossContexts
                )
            )
        } catch (e: Exception) {
            AppLogger.w("ActiveWorkoutVM", "Vorherige Sessiondaten konnten nicht geladen werden: ${e.message}", e)
            emptyMap()
        }

        val plannedRows = orderedTargets.mapNotNull { target ->
            val exercise = exercisesById[target.exerciseId] ?: return@mapNotNull null
            val previousSession = previousSessionsByExercise[exercise.id]
            ExerciseWithSets(
                key = WorkoutExerciseKey.Planned(target.id),
                exercise = exercise,
                sets = sets.filter { it.planTargetSnapshotId == target.id }
                    .sortedBy { it.setNumber },
                planTarget = applyDeloadToTarget(target, activeDeloadMode),
                originalPlanTarget = target,
                previousSession = previousSession?.let {
                    PreviousExerciseSessionUi(
                        sessionId = it.sessionId,
                        sessionStart = it.sessionStart,
                        sets = it.sets,
                        lastWorkSetWeightKg = it.lastWorkSetWeightKg,
                        lastWorkSetReachedTarget = lastWorkSetReachedTarget(
                            planTarget = target,
                            previousSets = it.sets
                        )
                    )
                }
            )
        }
        val adHocRows = adHocExercises.mapNotNull { exercise ->
            val resolvedExercise = exercisesById[exercise.id] ?: exercise
            val previousSession = previousSessionsByExercise[exercise.id]
            ExerciseWithSets(
                key = WorkoutExerciseKey.AdHoc(exercise.id),
                exercise = resolvedExercise,
                sets = sets.filter {
                    it.exerciseId == exercise.id && it.planTargetSnapshotId == null
                }.sortedBy { it.setNumber },
                previousSession = previousSession?.let {
                    PreviousExerciseSessionUi(
                        sessionId = it.sessionId,
                        sessionStart = it.sessionStart,
                        sets = it.sets,
                        lastWorkSetWeightKg = it.lastWorkSetWeightKg
                    )
                }
            )
        }
        plannedRows + adHocRows
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Real-time next-set load hints: built from the last work set of each
     * exercise and its RPE_RIR target (when present). Updates whenever a set
     * is logged, edited or deleted through [exercisesWithSets], so the chip
     * always reflects the newest RPE.
     */
    private val nextSetRecommendations = combine(
        exercisesWithSets,
        appPreferencesRepository.preferences
    ) { rows, prefs ->
        rows.mapNotNull { row ->
            if (
                effectiveRowIntensitySystem(prefs.intensitySystem, row.planTarget) ==
                IntensitySystem.OFF
            ) {
                return@mapNotNull null
            }
            val lastWorkSet = row.sets
                .filter { it.setType == SetType.NORMAL }
                .sortedBy { it.setNumber }
                .lastOrNull()
                ?: return@mapNotNull null
            val lastRpe = lastWorkSet.rpe ?: return@mapNotNull null
            val targetRpe = (row.planTarget?.config as? ProgressionConfig.RpeRir)?.targetRpe
            val recommended = RpeAutoregulation.recommendNextSetWeightKg(
                lastWeightKg = lastWorkSet.weightKg,
                lastRpe = lastRpe,
                targetRpe = targetRpe
            ) ?: return@mapNotNull null
            row.key to NextSetRecommendationUi(
                recommendedWeightKg = recommended,
                lastWeightKg = lastWorkSet.weightKg,
                lastRpe = lastRpe,
                targetRpe = targetRpe,
                backoffPercent = configuredBackoffPercent(row.planTarget?.config)
            )
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    internal fun previousSessionScope(
        planId: Long,
        metaPlanId: Long,
        shareAcrossContexts: Boolean
    ): PreviousSessionScope = when {
        planId <= 0L -> PreviousSessionScope.Global
        shareAcrossContexts -> PreviousSessionScope.SharedPlan(planId)
        metaPlanId > 0L -> PreviousSessionScope.MetaPlan(planId, metaPlanId)
        else -> PreviousSessionScope.NormalPlan(planId)
    }

    private val _events = MutableSharedFlow<WorkoutEvent>()
    val events = _events.asSharedFlow()

    private val chromeState = combine(
        showExercisePicker,
        showFinishDialog,
        _restTimers,
        _error
    ) { pickerVisible, finishDialogVisible, restTimers, error ->
        ActiveWorkoutChromeState(
            showExercisePicker = pickerVisible,
            showFinishDialog = finishDialogVisible,
            restTimers = restTimers,
            error = error
        )
    }

    /**
     * The stored intention per durable set id plus whether that read actually succeeded.
     * The loaded/failed pair is what keeps an unread or unreadable channel from being
     * presented as "no answer", which would silently erase a stored intention on edit.
     */
    private data class IntentionSnapshot(
        val bySetId: Map<Long, SetIntention> = emptyMap(),
        val loaded: Boolean = false,
        val failed: Boolean = false
    )

    private val intentions: StateFlow<IntentionSnapshot> =
        workoutRepository.observeSetIntentions()
            .map { IntentionSnapshot(bySetId = it, loaded = true) }
            .catch { error ->
                // A corrupt readiness row degrades the picker instead of crashing the
                // workout screen; the UI shows a note rather than faking UNKNOWN.
                AppLogger.w(
                    "ActiveWorkoutVM",
                    "Satzabsichten konnten nicht geladen werden: ${error.message}",
                    error
                )
                emit(IntentionSnapshot(failed = true))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                IntentionSnapshot()
            )

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionPhase,
        combine(exercisesWithSets, intentions) { exercises, snapshot -> exercises to snapshot },
        nextSetRecommendations,
        chromeState,
        operationState
    ) { phase, exercisesAndIntentions, recommendations, chrome, operation ->
        val (exercises, intentionSnapshot) = exercisesAndIntentions
        ActiveWorkoutUiState(
            sessionPhase = phase,
            exercisesWithSets = exercises,
            nextSetRecommendations = recommendations,
            showExercisePicker = chrome.showExercisePicker,
            showFinishDialog = chrome.showFinishDialog,
            restTimers = chrome.restTimers,
            error = chrome.error,
            logInFlightByExercise = operation.logInFlightByExercise,
            logSuccessSubmissions = operation.logSuccessSubmissions,
            updateInFlightBySet = operation.updateInFlightBySet,
            updateSuccessCountBySet = operation.updateSuccessCountBySet,
            setIntentions = intentionSnapshot.bySetId,
            setIntentionsLoaded = intentionSnapshot.loaded,
            setIntentionsFailed = intentionSnapshot.failed,
            finishState = operation.finishState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveWorkoutUiState())

    init {
        restoreAddedExercises()
        persistAddedExerciseIds()
        restoreAndPersistRestTimers()
        observeExerciseReconciliation()
        recoverCompletedSession()
    }

    /**
     * Restores exercises added to this session (e.g. via the exercise picker) after
     * process death, using the exercise IDs persisted in [savedStateHandle].
     */
    private fun restoreAddedExercises() {
        val savedIds: List<Long> = savedStateHandle[KEY_ADDED_EXERCISE_IDS] ?: emptyList()
        if (savedIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val restored = exerciseRepository.getExercisesByIds(savedIds)
                if (restored.isEmpty()) return@launch
                val restoredById = restored.associateBy { it.id }
                val orderedRestored = savedIds.mapNotNull { restoredById[it] }
                addedExercises.update { current ->
                    val currentIds = current.map { it.id }.toSet()
                    (orderedRestored.filterNot { it.id in currentIds } + current).distinctBy { it.id }
                }
            } catch (e: Exception) {
                AppLogger.w("ActiveWorkoutVM", "Hinzugefuegte Uebungen konnten nicht wiederhergestellt werden: ${e.message}", e)
            }
        }
    }

    private fun persistAddedExerciseIds() {
        viewModelScope.launch {
            addedExercises.collect { exercises ->
                savedStateHandle[KEY_ADDED_EXERCISE_IDS] = exercises.map { it.id }
            }
        }
    }

    /**
     * Rest timers are runtime UI state, but losing one during an Android process restart is
     * surprising while the workout session itself remains active. The app preferences DataStore
     * provides durable storage, keyed by session, so the timer can be reconstructed in a fresh
     * ViewModel. The payload stays opaque to the preferences repository.
     */
    private fun restoreAndPersistRestTimers() {
        viewModelScope.launch {
            restTimerMutationMutex.withLock {
                val hasLiveMutation = restTimerMutationGeneration > 0L
                val sessionResult = runCatching { workoutRepository.getSessionById(sessionId) }
                    .onFailure { e ->
                        AppLogger.w(
                            "ActiveWorkoutVM",
                            "Aktive Session fuer Rest-Timer konnte nicht geladen werden: ${e.message}",
                            e
                        )
                    }
                val session = sessionResult.getOrNull()
                val sessionStartEpochMillis = session?.startTime
                    ?.atZone(ZoneId.systemDefault())
                    ?.toInstant()
                    ?.toEpochMilli()
                restTimerSessionStartEpochMillis = sessionStartEpochMillis

                val encodedStateResult = runCatching {
                    appPreferencesRepository.readRestTimerState(sessionId)
                }
                val encodedState = encodedStateResult.getOrNull()

                if (sessionResult.isFailure || encodedStateResult.isFailure) {
                    // Keep any live state intact when either read fails. A transient DataStore or
                    // database error must not turn a visible timer into an empty map. Subsequent
                    // timer mutations still persist through [replaceRestTimers]. If state is
                    // already present, refresh its durable copy while the mutex is held.
                    if (_restTimers.value.isNotEmpty() && sessionStartEpochMillis != null) {
                        persistRestTimerState(
                            encodeRestTimerState(_restTimers.value, sessionStartEpochMillis)
                        )
                    }
                    if (encodedStateResult.isFailure) {
                        AppLogger.w(
                            "ActiveWorkoutVM",
                            "Rest-Timer konnten nicht gelesen werden: ${encodedStateResult.exceptionOrNull()?.message}",
                            encodedStateResult.exceptionOrNull()
                        )
                    }
                    return@withLock
                }

                // Restore only a still-active session. A completed or missing session must clear
                // any stale payload left behind by navigation, import, or a reused database id.
                // If a timer mutation won the mutex before this restore started, preserve that
                // live state and make it the durable source of truth instead of overwriting it
                // with an older payload (or an empty read after a failed write).
                if (session?.endTime != null || session == null || sessionStartEpochMillis == null) {
                    _restTimers.value = emptyMap()
                } else if (!hasLiveMutation) {
                    if (session?.endTime == null && sessionStartEpochMillis != null && encodedState != null) {
                        _restTimers.value = decodeRestTimerState(
                            encodedState = encodedState,
                            expectedSessionStartEpochMillis = sessionStartEpochMillis
                        )
                    } else {
                        _restTimers.value = emptyMap()
                    }
                }

                if (session?.endTime != null || session == null || sessionStartEpochMillis == null) {
                    persistRestTimerState(null)
                } else if (hasLiveMutation) {
                    persistRestTimerState(
                        if (_restTimers.value.isEmpty()) {
                            null
                        } else {
                            encodeRestTimerState(_restTimers.value, sessionStartEpochMillis)
                        }
                    )
                } else if (encodedState != null && _restTimers.value.isEmpty()) {
                    // Invalid or stale payload (including a different session start) is removed
                    // so it cannot be reconsidered on every subsequent ViewModel recreation.
                    persistRestTimerState(null)
                }
            }
        }
    }

    private suspend fun persistRestTimerState(encodedState: String?) {
        runCatching {
            appPreferencesRepository.writeRestTimerState(
                sessionId = sessionId,
                encodedState = encodedState
            )
        }.onFailure { e ->
            AppLogger.w(
                "ActiveWorkoutVM",
                "Rest-Timer konnten nicht gespeichert werden: ${e.message}",
                e
            )
        }
    }

    private suspend fun replaceRestTimers(
        transform: (Map<WorkoutExerciseKey, RestTimerUi>) -> Map<WorkoutExerciseKey, RestTimerUi>
    ) {
        restTimerMutationMutex.withLock {
            restTimerMutationGeneration += 1L
            val updatedTimers = transform(_restTimers.value)
            _restTimers.value = updatedTimers
            val sessionStartEpochMillis = restTimerSessionStartEpochMillis
            val encodedState = if (updatedTimers.isEmpty() || sessionStartEpochMillis == null) {
                null
            } else {
                encodeRestTimerState(updatedTimers, sessionStartEpochMillis)
            }
            persistRestTimerState(encodedState)
        }
    }

    private suspend fun clearRestTimers() {
        replaceRestTimers { emptyMap() }
    }

    private fun observeExerciseReconciliation() {
        viewModelScope.launch {
            sessionSets.collect { sets ->
                val knownIds = addedExercises.value.map { it.id }.toSet()
                val missingExercises = sets
                    .filter { it.planTargetSnapshotId == null }
                    .map { it.exerciseId }
                    .distinct()
                    .filterNot { it in knownIds }
                    .mapNotNull { exerciseRepository.getExerciseById(it) }

                if (missingExercises.isNotEmpty()) {
                    addedExercises.update { current ->
                        val currentIds = current.map { it.id }.toSet()
                        current + missingExercises.filterNot { it.id in currentIds }
                    }
                }
            }
        }
    }

    fun addExercise(exercise: Exercise) {
        addedExercises.update { current ->
            if (current.any { it.id == exercise.id }) current else current + exercise
        }
    }

    fun showExercisePicker() {
        showExercisePicker.value = true
    }

    fun dismissExercisePicker() {
        showExercisePicker.value = false
    }

    fun showFinishDialog() {
        showFinishDialog.value = true
    }

    fun dismissFinishDialog() {
        showFinishDialog.value = false
    }

    fun logSet(
        exerciseId: Long,
        reps: Int,
        weightKg: Double,
        setType: SetType = SetType.NORMAL,
        intensity: String = "",
        submissionId: Long = nextSubmissionId(),
        key: WorkoutExerciseKey = WorkoutExerciseKey.AdHoc(exerciseId),
        intention: SetIntention = SetIntention.UNKNOWN
    ) {
        viewModelScope.launch {
            if ((operationState.value.logInFlightByExercise[key] ?: 0) > 0) return@launch
            operationState.update {
                it.copy(logInFlightByExercise = incrementCounter(it.logInFlightByExercise, key))
            }
            var persisted = false
            try {
                mutationMutex.withLock {
                    if (!sessionIsMutable()) return@withLock
                    requireValidSetInput(reps, weightKg)
                    require(key !is WorkoutExerciseKey.AdHoc || key.exerciseId == exerciseId) {
                        "Ad-hoc exercise key does not match exercise"
                    }
                    val persistedSets = workoutRepository.getSetsForSessionList(sessionId)
                        .filter { existing ->
                            when (key) {
                                is WorkoutExerciseKey.Planned ->
                                    existing.planTargetSnapshotId == key.snapshotId
                                is WorkoutExerciseKey.AdHoc ->
                                    existing.exerciseId == key.exerciseId &&
                                        existing.planTargetSnapshotId == null
                            }
                        }
                    val setNumber = (persistedSets.maxOfOrNull { it.setNumber } ?: 0) + 1
                    val prefs = appPreferencesRepository.preferences.first()
                    val parsedRpe = computeIntensity(
                        intensity,
                        effectiveIntensitySystem(prefs.intensitySystem, key)
                    )
                    val completedAtInstant = Instant.now()

                    val set = WorkoutSet(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        setNumber = setNumber,
                        reps = reps,
                        weightKg = weightKg,
                        setType = setType,
                        completedAt = LocalDateTime.ofInstant(
                            completedAtInstant,
                            ZoneId.systemDefault()
                        ),
                        rpe = parsedRpe,
                        planTargetSnapshotId = (key as? WorkoutExerciseKey.Planned)?.snapshotId
                    )
                    // The repository owns record recalculation and does it inside the same
                    // transaction as the insert. The ViewModel only compares before/after and
                    // must stay inside mutationMutex until that comparison is done, so a
                    // concurrent delete/update cannot interleave and later be overwritten by
                    // a stale add-based PR write.
                    // Best-effort snapshot: a statistics-read failure must never block the
                    // repository mutation. Without a snapshot no NewRecord event is emitted,
                    // because an unknown baseline could otherwise produce false positives.
                    val recordsBefore: Map<RecordType, PersonalRecord>? = if (setType != SetType.WARMUP) {
                        snapshotRecordsBefore(exerciseId)
                    } else {
                        null
                    }
                    workoutRepository.addSet(set, intention)
                    persisted = true
                    if (setType != SetType.WARMUP && recordsBefore != null) {
                        emitImprovedRecords(exerciseId, recordsBefore)
                    }
                    // A zero duration selects the elapsed-time display. Count only NORMAL sets
                    // against the deload-adjusted target for this exact planned row.
                    val plannedTarget = (key as? WorkoutExerciseKey.Planned)?.let { planned ->
                        planTargets.value.find { it.id == planned.snapshotId }
                    }
                    val exerciseComplete = isPlannedExerciseComplete(
                        planTarget = plannedTarget,
                        deloadMode = prefs.deloadMode,
                        previousSets = persistedSets,
                        newSetType = setType
                    )
                    replaceRestTimers { currentTimers ->
                        if (setType == SetType.WARMUP || exerciseComplete) {
                            currentTimers - key
                        } else {
                            currentTimers + (
                                key to RestTimerUi(
                                    startTime = completedAtInstant,
                                    durationSeconds = if (prefs.autoRestTimerEnabled) prefs.defaultRestTimeSeconds else 0
                                )
                            )
                        }
                    }
                }
                if (!persisted) return@launch

                operationState.update {
                    it.copy(logSuccessSubmissions = it.logSuccessSubmissions + submissionId)
                }
            } catch (e: Exception) {
                setError(
                    message = "Satz konnte nicht gespeichert werden: ${e.message}",
                    retry = WorkoutRetryDescriptor.LogSet(
                        key = key,
                        exerciseId = exerciseId,
                        reps = reps,
                        weightKg = weightKg,
                        setType = setType,
                        intensity = intensity,
                        submissionId = submissionId,
                        intention = intention
                    )
                )
            } finally {
                operationState.update {
                    it.copy(
                        logInFlightByExercise = decrementCounter(
                            it.logInFlightByExercise,
                            key
                        )
                    )
                }
            }
        }
    }

    fun dismissRestTimer(key: WorkoutExerciseKey) {
        viewModelScope.launch {
            mutationMutex.withLock {
                replaceRestTimers { current ->
                    current - key
                }
            }
        }
    }

    private fun effectiveIntensitySystem(
        configured: IntensitySystem,
        key: WorkoutExerciseKey
    ): IntensitySystem {
        if (configured != IntensitySystem.OFF || key !is WorkoutExerciseKey.Planned) {
            return configured
        }
        val target = planTargets.value.find { it.id == key.snapshotId }
        return effectiveRowIntensitySystem(configured, target)
    }

    private fun effectiveRowIntensitySystem(
        configured: IntensitySystem,
        planTarget: WorkoutPlanTarget?
    ): IntensitySystem = if (
        configured == IntensitySystem.OFF && planTarget?.config is ProgressionConfig.RpeRir
    ) {
        IntensitySystem.RPE
    } else {
        configured
    }

    private fun computeIntensity(intensity: String, intensitySystem: IntensitySystem): Double? {
        if (intensity.isBlank()) return null
        val rawVal = parseDecimal(intensity)
            ?: throw IllegalArgumentException("Ungültiger ${intensitySystem.displayName}-Wert")
        return WorkoutNumericValidation.storedRpe(rawVal, intensitySystem)
    }

    private fun requireValidSetInput(reps: Int, weightKg: Double) {
        require(WorkoutNumericValidation.isValidReps(reps)) {
            "Wiederholungen müssen größer als 0 sein"
        }
        require(WorkoutNumericValidation.isValidWeightKg(weightKg)) {
            "Gewicht muss endlich und nicht negativ sein"
        }
    }

    /**
     * Emits NewRecord only for values that actually improved after a successful
     * repository mutation; the repository owns record recalculation, so this
     * never writes a record itself.
     */
    private suspend fun emitImprovedRecords(
        exerciseId: Long,
        recordsBefore: Map<RecordType, PersonalRecord>
    ) {
        try {
            val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return
            val recordsAfter = statisticsRepository.getRecordsForExercisesList(listOf(exerciseId))
                .associateBy { it.type }
            RecordType.entries.forEach { type ->
                val current = recordsAfter[type]?.value ?: return@forEach
                val previous = recordsBefore[type]?.value
                if (previous == null || current > previous) {
                    _events.emit(WorkoutEvent.NewRecord(exercise.name, type))
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ActiveWorkoutVM", "PR-Pruefung nach Mutation fehlgeschlagen: ${e.message}", e)
        }
    }

    /**
     * Loads the current personal records as a comparison baseline before a repository
     * mutation. Best-effort by design: when statistics are temporarily unavailable the
     * mutation still proceeds and the caller simply skips NewRecord emission.
     */
    private suspend fun snapshotRecordsBefore(exerciseId: Long): Map<RecordType, PersonalRecord>? =
        try {
            statisticsRepository.getRecordsForExercisesList(listOf(exerciseId))
                .associateBy { it.type }
        } catch (e: Exception) {
            AppLogger.w("ActiveWorkoutVM", "PR-Snapshot konnte nicht geladen werden: ${e.message}", e)
            null
        }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            mutationMutex.withLock {
                if (!sessionIsMutable()) return@withLock
                try {
                    workoutRepository.deleteSet(setId)
                } catch (e: Exception) {
                    setError(
                        message = "Satz konnte nicht gelöscht werden: ${e.message}",
                        retry = WorkoutRetryDescriptor.DeleteSet(setId = setId)
                    )
                }
            }
        }
    }

    fun updateSet(
        setId: Long,
        reps: Int,
        weightKg: Double,
        intensity: String = "",
        intention: SetIntention? = null
    ) {
        viewModelScope.launch {
            operationState.update {
                it.copy(updateInFlightBySet = incrementCounter(it.updateInFlightBySet, setId))
            }
            try {
                mutationMutex.withLock {
                    if (!sessionIsMutable()) return@withLock
                    // Failed validation must leave the edit row open so the user's values can be
                    // corrected in place. The retry descriptor below also keeps the submitted
                    // values available when the repository is temporarily unavailable.
                    requireValidSetInput(reps, weightKg)

                    val sets = workoutRepository.getSetsForSessionList(sessionId)
                    val set = sets.find { it.id == setId } ?: return@withLock

                    val prefs = appPreferencesRepository.preferences.first()
                    // When intensity tracking is OFF, the intensity UI is hidden and the
                    // incoming string is always blank — preserve the existing RPE instead
                    // of wiping it out. When intensity tracking is on, a blank string means
                    // the user intentionally cleared the field.
                    val intensitySystem = effectiveIntensitySystem(
                        configured = prefs.intensitySystem,
                        key = set.planTargetSnapshotId?.let { WorkoutExerciseKey.Planned(it) }
                            ?: WorkoutExerciseKey.AdHoc(set.exerciseId)
                    )
                    val newRpe = if (intensitySystem == IntensitySystem.OFF) {
                        set.rpe
                    } else {
                        computeIntensity(intensity, intensitySystem)
                    }

                    val updatedSet = set.copy(
                        reps = reps,
                        weightKg = weightKg,
                        rpe = newRpe
                    )
                    val recordsBefore = snapshotRecordsBefore(updatedSet.exerciseId)
                    workoutRepository.updateSet(updatedSet, intention)
                    if (recordsBefore != null) {
                        emitImprovedRecords(updatedSet.exerciseId, recordsBefore)
                    }
                    operationState.update {
                        it.copy(
                            updateSuccessCountBySet = incrementCounter(
                                it.updateSuccessCountBySet,
                                setId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                setError(
                    message = "Satz konnte nicht aktualisiert werden: ${e.message}",
                    retry = WorkoutRetryDescriptor.UpdateSet(
                        setId = setId,
                        reps = reps,
                        weightKg = weightKg,
                        intensity = intensity,
                        intention = intention
                    )
                )
            } finally {
                operationState.update {
                    it.copy(updateInFlightBySet = decrementCounter(it.updateInFlightBySet, setId))
                }
            }
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            var generateAfterFinish = false
            mutationMutex.withLock {
                if (operationState.value.finishState != WorkoutFinishState.Idle) {
                    return@withLock
                }
                operationState.update { it.copy(finishState = WorkoutFinishState.Completing) }
                var discardEmptySession = false
                try {
                    val session = workoutRepository.getSessionById(sessionId)
                    when {
                        session == null -> {
                            showFinishDialog.value = false
                            operationState.update {
                                it.copy(finishState = WorkoutFinishState.CompletedWithoutReview)
                            }
                            // No session left to rest between sets of.
                            clearRestTimers()
                        }
                        session.endTime != null -> {
                            showFinishDialog.value = false
                            operationState.update {
                                it.copy(finishState = WorkoutFinishState.Generating)
                            }
                            generateAfterFinish = true
                            // The session is over; pending rest timers must not linger.
                            clearRestTimers()
                        }
                        else -> {
                            discardEmptySession =
                                workoutRepository.getSetCountForSession(sessionId) == 0
                            if (discardEmptySession) {
                                workoutRepository.deleteSession(sessionId)
                                showFinishDialog.value = false
                                operationState.update {
                                    it.copy(
                                        finishState = WorkoutFinishState.CompletedWithoutReview
                                    )
                                }
                                clearRestTimers()
                            } else {
                                workoutRepository.finishWorkout(sessionId)
                                showFinishDialog.value = false
                                operationState.update {
                                    it.copy(finishState = WorkoutFinishState.Generating)
                                }
                                generateAfterFinish = true
                                // The session is over; pending rest timers must not linger.
                                clearRestTimers()
                            }
                        }
                    }
                } catch (e: Exception) {
                    setError(
                        message = "Training konnte nicht beendet werden: ${e.message}",
                        retry = WorkoutRetryDescriptor.FinishWorkout(
                            discardEmptySession = discardEmptySession
                        )
                    )
                    operationState.update { it.copy(finishState = WorkoutFinishState.Idle) }
                }
            }
            if (generateAfterFinish) {
                generateProgression(sessionId)
            }
        }
    }

    fun retryProgressionGeneration() {
        viewModelScope.launch {
            var retrySessionId: Long? = null
            mutationMutex.withLock {
                val failed = operationState.value.finishState as? WorkoutFinishState.GenerationFailed
                    ?: return@withLock
                retrySessionId = failed.sessionId
                operationState.update { it.copy(finishState = WorkoutFinishState.Generating) }
            }
            retrySessionId?.let { generateProgression(it) }
        }
    }

    private fun recoverCompletedSession() {
        viewModelScope.launch {
            var completedSessionId: Long? = null
            try {
                mutationMutex.withLock {
                    if (operationState.value.finishState != WorkoutFinishState.Idle) {
                        return@withLock
                    }
                    val session = workoutRepository.getSessionById(sessionId)
                    if (session?.endTime != null) {
                        completedSessionId = session.id
                        clearRestTimers()
                        operationState.update { it.copy(finishState = WorkoutFinishState.Generating) }
                    }
                }
                completedSessionId?.let { generateProgression(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                runCatching {
                    AppLogger.w(
                        "ActiveWorkoutVM",
                        "Recovery-Pruefung fuer beendetes Training fehlgeschlagen: ${e.message}",
                        e
                    )
                }
            }
        }
    }

    private suspend fun generateProgression(completedSessionId: Long) {
        try {
            val result = progressionRepository.generateOutcomesForSession(completedSessionId)
            operationState.update {
                it.copy(
                    finishState = if (result.reviewItemCount > 0 || result.insertedCount > 0) {
                        // Fresh outcomes exist: either decisions are pending or the coach
                        // evaluated the session and recorded informational results
                        // (KEEP_TARGET / INSUFFICIENT_DATA). Both deserve the review
                        // screen instead of a silent close without feedback.
                        WorkoutFinishState.ReviewReady(completedSessionId)
                    } else {
                        WorkoutFinishState.CompletedWithoutReview
                    }
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            operationState.update {
                it.copy(
                    finishState = WorkoutFinishState.GenerationFailed(
                        sessionId = completedSessionId,
                        message = GENERATION_FAILURE_MESSAGE
                    )
                )
            }
            runCatching {
                AppLogger.w(
                    "ActiveWorkoutVM",
                    "Progressionsvorschlaege konnten nicht erzeugt werden: ${e.message}",
                    e
                )
            }
        }
    }

    private suspend fun sessionIsMutable(): Boolean {
        val session = workoutRepository.getSessionById(sessionId)
        return session != null && session.endTime == null
    }

    fun retryLastError() {
        val retry = _error.value?.retry ?: return
        _error.value = null
        when (retry) {
            is WorkoutRetryDescriptor.LogSet -> logSet(
                key = retry.key,
                exerciseId = retry.exerciseId,
                reps = retry.reps,
                weightKg = retry.weightKg,
                setType = retry.setType,
                intensity = retry.intensity,
                submissionId = retry.submissionId,
                intention = retry.intention
            )
            is WorkoutRetryDescriptor.UpdateSet -> updateSet(
                setId = retry.setId,
                reps = retry.reps,
                weightKg = retry.weightKg,
                intensity = retry.intensity,
                intention = retry.intention
            )
            is WorkoutRetryDescriptor.DeleteSet -> deleteSet(retry.setId)
            is WorkoutRetryDescriptor.FinishWorkout -> finishWorkout()
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Surfaces picker-level failures (loading or creating exercises) through the regular
     * error snackbar. Method reference is stable across recompositions, so the picker's
     * exercise flow is not restarted on every recomposition.
     */
    fun reportPickerError(message: String) {
        setError(message = message)
    }

    private fun setError(message: String, retry: WorkoutRetryDescriptor? = null) {
        _error.value = WorkoutErrorUi(
            message = message,
            retry = retry,
            id = errorSequence++
        )
    }

    private fun <K> incrementCounter(counter: Map<K, Int>, key: K): Map<K, Int> =
        counter + (key to ((counter[key] ?: 0) + 1))

    private fun <K> decrementCounter(counter: Map<K, Int>, key: K): Map<K, Int> {
        val next = (counter[key] ?: 0) - 1
        return if (next <= 0) counter - key else counter + (key to next)
    }

    companion object {
        private const val KEY_ADDED_EXERCISE_IDS = "addedExerciseIds"
        private const val GENERATION_FAILURE_MESSAGE = "progression_generation_failed"
    }
}


