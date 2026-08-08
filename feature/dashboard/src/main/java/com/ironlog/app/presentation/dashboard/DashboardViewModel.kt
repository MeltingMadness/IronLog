package com.ironlog.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.domain.model.MetaPlanRotationEvent
import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.catchAndLog
import com.ironlog.app.domain.util.resolveMetaPlanRotation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardMetaPlanOption(
    val metaPlanId: Long,
    val metaPlanName: String,
    val nextPlan: TrainingPlan?,
    val rotationPlans: List<DashboardMetaSubPlanStatus>,
    val canSkip: Boolean
)

data class DashboardMetaSubPlanStatus(
    val plan: TrainingPlan,
    val lastDoneDaysAgo: Long?
)

data class DashboardPlanStatus(
    val plan: TrainingPlan,
    val lastDoneDaysAgo: Long?
)

data class DashboardUiState(
    val activeSession: WorkoutSession? = null,
    val trainingPlans: List<DashboardPlanStatus> = emptyList(),
    val metaPlanOptions: List<DashboardMetaPlanOption> = emptyList(),
    val showPlanSelectionSheet: Boolean = false,
    val workoutsThisWeek: Int = 0,
    val workoutsThisMonth: Int = 0,
    val recentRecords: List<Pair<PersonalRecord, String>> = emptyList(),
    val lastWorkout: WorkoutSession? = null,
    val lastWorkoutExerciseCount: Int = 0,
    val muscleHeatmap: Map<MuscleGroup, Int> = emptyMap(),
    val weeklyVolume: List<Pair<String, Double>> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val skippingMetaPlanId: Long? = null
)

class DashboardViewModel(
    private val workoutRepository: WorkoutRepository,
    private val statisticsRepository: StatisticsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val metaTrainingPlanRepository: MetaTrainingPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboard()
        observeActiveSession()
        observeDashboardRefreshSignals()
    }

    private fun observeActiveSession() {
        viewModelScope.launch {
            workoutRepository.observeActiveSession()
                .catchAndLog("DashboardVM")
                .collect { session ->
                    val previousSession = _uiState.value.activeSession
                    _uiState.update { it.copy(activeSession = session) }
                    if (previousSession != null && session == null) {
                        loadDashboard()
                    }
                }
        }

        viewModelScope.launch {
            combine(
                trainingPlanRepository.getAllPlans(),
                workoutRepository.observeLastSessionPerPlan()
            ) { plans, lastPlanSessions ->
                buildPlanOptions(
                    plans = plans,
                    lastSessionsPerPlan = lastPlanSessions
                )
            }
                .catchAndLog("DashboardVM_Plans")
                .collect { plans ->
                    _uiState.update { it.copy(trainingPlans = plans) }
                }
        }

        viewModelScope.launch {
            combine(
                trainingPlanRepository.getAllPlans(),
                workoutRepository.observeLastSessionPerMetaPlanSubPlan(),
                metaTrainingPlanRepository.observeLastRotationEventPerMetaPlanSubPlan(),
                metaTrainingPlanRepository.getAllMetaPlans()
            ) { plans, lastSessions, rotationEvents, metaPlans ->
                buildMetaPlanOptions(
                    plans = plans,
                    lastSessionsPerSubPlan = lastSessions,
                    rotationEvents = rotationEvents,
                    metaPlans = metaPlans
                )
            }
                .catchAndLog("DashboardVM_MetaPlans")
                .collect { options ->
                    _uiState.update { it.copy(metaPlanOptions = options) }
                }
        }
    }

    fun loadDashboard() {
        loadDashboard(showLoadingIndicator = true)
    }

    private fun loadDashboard(showLoadingIndicator: Boolean) {
        viewModelScope.launch {
            if (showLoadingIndicator) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }

            try {
                val preferences = appPreferencesRepository.preferences.first()
                val now = LocalDate.now()

                val weekAnchor = when (preferences.weekStart) {
                    WeekStart.MONDAY -> DayOfWeek.MONDAY
                    WeekStart.SUNDAY -> DayOfWeek.SUNDAY
                }

                val startOfWeek = now.with(TemporalAdjusters.previousOrSame(weekAnchor))
                val startOfWeekMillis = EpochConverter.toLong(startOfWeek.atStartOfDay())
                val workoutsThisWeek = workoutRepository.getCompletedSessionCountSince(startOfWeekMillis)

                val startOfMonth = now.withDayOfMonth(1)
                val startOfMonthMillis = EpochConverter.toLong(startOfMonth.atStartOfDay())
                val workoutsThisMonth = workoutRepository.getCompletedSessionCountSince(startOfMonthMillis)

                val records = statisticsRepository.getRecentRecordsList(5)
                val recordExerciseMap = exerciseRepository.getExercisesByIds(records.map { it.exerciseId })
                    .associateBy { it.id }
                val recordsWithNames = records.map { record ->
                    Pair(record, recordExerciseMap[record.exerciseId]?.name ?: "Unbekannt")
                }

                val lastWorkout = workoutRepository.getLastCompletedSession()
                val lastWorkoutExerciseCount = if (lastWorkout != null) {
                    workoutRepository.getExerciseIdsForSession(lastWorkout.id).size
                } else 0

                val weekSets = statisticsRepository.getWorkSetsCompletedSince(startOfWeekMillis)
                val exerciseMap = exerciseRepository
                    .getExercisesByIds(weekSets.map { it.exerciseId }.distinct())
                    .associateBy { it.id }

                val heatmap = mutableMapOf<MuscleGroup, Int>()
                for (set in weekSets) {
                    val exercise = exerciseMap[set.exerciseId] ?: continue
                    heatmap[exercise.primaryMuscleGroup] =
                        (heatmap[exercise.primaryMuscleGroup] ?: 0) + 1
                    for (secondary in exercise.secondaryMuscleGroups) {
                        heatmap[secondary] = (heatmap[secondary] ?: 0) + 1
                    }
                }

                val eightWeeksAgo = now.minusWeeks(7).with(TemporalAdjusters.previousOrSame(weekAnchor))
                val eightWeeksAgoMillis = EpochConverter.toLong(eightWeeksAgo.atStartOfDay())
                val trendSets = statisticsRepository.getWorkSetsCompletedSince(eightWeeksAgoMillis)

                val weekFields = WeekFields.of(weekAnchor, 1)
                val volumeByWeek = trendSets
                    .groupBy { set ->
                        val setDate = set.completedAt.toLocalDate()
                        val weekNum = setDate.get(weekFields.weekOfWeekBasedYear())
                        val year = setDate.get(weekFields.weekBasedYear())
                        year to weekNum
                    }
                    .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
                    .map { (key, sets) ->
                        val label = "KW${key.second}"
                        val volume = sets.sumOf { it.weightKg * it.reps }
                        label to volume
                    }

                _uiState.update {
                    it.copy(
                        workoutsThisWeek = workoutsThisWeek,
                        workoutsThisMonth = workoutsThisMonth,
                        recentRecords = recordsWithNames,
                        lastWorkout = lastWorkout,
                        lastWorkoutExerciseCount = lastWorkoutExerciseCount,
                        muscleHeatmap = heatmap,
                        weeklyVolume = volumeByWeek,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Dashboard konnte nicht geladen werden: ${e.message}"
                    )
                }
            }
        }
    }

    private fun observeDashboardRefreshSignals() {
        viewModelScope.launch {
            combine(
                workoutRepository.getAllCompletedSessions(),
                statisticsRepository.getRecentRecords(limit = 1),
                appPreferencesRepository.preferences
            ) { _, _, _ -> Unit }
                .drop(1)
                .catchAndLog("DashboardVM_Refresh")
                .collect {
                    loadDashboard(showLoadingIndicator = false)
                }
        }
    }

    fun showPlanSelectionSheet() {
        _uiState.update { it.copy(showPlanSelectionSheet = true) }
    }

    fun dismissPlanSelectionSheet() {
        _uiState.update { it.copy(showPlanSelectionSheet = false) }
    }

    fun skipCurrentMetaSubPlan(metaPlanId: Long) {
        viewModelScope.launch {
            val option = _uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaPlanId }
                ?: return@launch
            val expectedPlanId = option.nextPlan?.id ?: return@launch
            if (!option.canSkip) return@launch
            if (workoutRepository.getActiveSession() != null) {
                _uiState.update {
                    it.copy(error = "Es ist bereits ein anderes Training aktiv. Bitte setze es fort oder beende es zuerst.")
                }
                return@launch
            }
            if (_uiState.value.skippingMetaPlanId != null) return@launch

            _uiState.update { it.copy(skippingMetaPlanId = metaPlanId) }
            try {
                val skipped = metaTrainingPlanRepository.skipCurrentSubPlan(metaPlanId, expectedPlanId)
                if (!skipped) {
                    _uiState.update {
                        it.copy(error = "Der vorgeschlagene Plan hat sich geändert. Bitte erneut versuchen.")
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(error = "Teilplan konnte nicht übersprungen werden: ${error.message}")
                }
            } finally {
                _uiState.update { it.copy(skippingMetaPlanId = null) }
            }
        }
    }

    fun startNewWorkoutWithMetaPlan(
        metaPlanId: Long,
        onSessionCreated: (Long, Long?, Long?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val option = _uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaPlanId }
                val nextPlan = option?.nextPlan
                if (option == null || nextPlan == null) {
                    _uiState.update {
                        it.copy(error = "Meta-Plan ist unvollständig oder enthält keine gültigen Unterpläne.")
                    }
                    return@launch
                }

                val activeSession = workoutRepository.getActiveSession()
                if (activeSession != null) {
                    if (activeSession.planId == nextPlan.id && activeSession.metaPlanId == option.metaPlanId) {
                        onSessionCreated(activeSession.id, nextPlan.id, option.metaPlanId)
                    } else {
                        _uiState.update {
                            it.copy(error = "Es ist bereits ein anderes Training aktiv. Bitte setze es fort oder beende es zuerst.")
                        }
                    }
                    return@launch
                }

                val sessionId = workoutRepository.startWorkout(
                    name = nextPlan.name,
                    planId = nextPlan.id,
                    metaPlanId = option.metaPlanId
                )
                onSessionCreated(sessionId, nextPlan.id, option.metaPlanId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Meta-Plan-Training konnte nicht gestartet werden: ${e.message}")
                }
            }
        }
    }

    fun startNewWorkout(onSessionCreated: (Long, Long?) -> Unit) {
        viewModelScope.launch {
            try {
                val activeSession = workoutRepository.getActiveSession()
                if (activeSession != null) {
                    onSessionCreated(activeSession.id, activeSession.planId)
                    return@launch
                }

                val autoName = "Training ${LocalDate.now().format(DateFormatting.DATE_SHORT)}"
                val sessionId = workoutRepository.startWorkout(
                    name = autoName,
                    planId = null,
                    metaPlanId = null
                )
                onSessionCreated(sessionId, null)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Training konnte nicht gestartet werden: ${e.message}") }
            }
        }
    }

    fun startNewWorkoutWithPlan(plan: TrainingPlan, onSessionCreated: (Long, Long?) -> Unit) {
        viewModelScope.launch {
            try {
                val activeSession = workoutRepository.getActiveSession()
                if (activeSession != null) {
                    if (activeSession.planId == plan.id) {
                        onSessionCreated(activeSession.id, plan.id)
                    } else {
                        _uiState.update {
                            it.copy(error = "Es ist bereits ein anderes Training aktiv. Bitte setze es fort oder beende es zuerst.")
                        }
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
                _uiState.update {
                    it.copy(error = "Training nach Plan konnte nicht gestartet werden: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun buildMetaPlanOptions(
        plans: List<TrainingPlan>,
        lastSessionsPerSubPlan: List<com.ironlog.app.domain.model.LastMetaPlanSession>,
        rotationEvents: List<MetaPlanRotationEvent>,
        metaPlans: List<MetaTrainingPlan>
    ): List<DashboardMetaPlanOption> {
        if (metaPlans.isEmpty()) return emptyList()

        val plansById = plans.associateBy { it.id }
        val lastTimeIndex = lastSessionsPerSubPlan.associateBy(
            keySelector = { it.planId to it.metaPlanId },
            valueTransform = { it.lastStartTime }
        )

        return metaPlans.map { metaPlan ->
            val orderedSubPlans = metaPlan.items
                .sortedBy { it.orderIndex }
                .mapNotNull { item -> plansById[item.trainingPlanId] }

            if (orderedSubPlans.isEmpty()) {
                return@map DashboardMetaPlanOption(
                    metaPlanId = metaPlan.id,
                    metaPlanName = metaPlan.name,
                    nextPlan = null,
                    rotationPlans = emptyList(),
                    canSkip = false
                )
            }

            val eventIndexForMetaPlan = rotationEvents
                .filter { it.metaPlanId == metaPlan.id }
                .associate { it.trainingPlanId to it.lastEventAt }

            val rotationIds = resolveMetaPlanRotation(
                orderedPlanIds = orderedSubPlans.map { it.id },
                lastEventAtByPlanId = eventIndexForMetaPlan
            )

            val rotatedPlans = rotationIds.mapNotNull(plansById::get)

            DashboardMetaPlanOption(
                metaPlanId = metaPlan.id,
                metaPlanName = metaPlan.name,
                nextPlan = rotatedPlans.firstOrNull(),
                rotationPlans = rotatedPlans.map { plan ->
                    val lastMillis = lastTimeIndex[plan.id to metaPlan.id]
                    DashboardMetaSubPlanStatus(
                        plan = plan,
                        lastDoneDaysAgo = lastMillis?.let { millis ->
                            java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.Instant.ofEpochMilli(millis)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                                LocalDate.now()
                            )
                        }
                    )
                },
                canSkip = orderedSubPlans.size > 1
            )
        }
    }

    private fun buildPlanOptions(
        plans: List<TrainingPlan>,
        lastSessionsPerPlan: List<com.ironlog.app.domain.model.LastPlanSession>
    ): List<DashboardPlanStatus> {
        if (plans.isEmpty()) return emptyList()

        val lastTimeByPlanId = lastSessionsPerPlan.associateBy(
            keySelector = { it.planId },
            valueTransform = { it.lastStartTime }
        )

        return plans.map { plan ->
            DashboardPlanStatus(
                plan = plan,
                lastDoneDaysAgo = lastTimeByPlanId[plan.id]?.let { millis ->
                    java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate(),
                        LocalDate.now()
                    )
                }
            )
        }
    }
}

private fun LocalDateTime.toRelativeDaysAgo(): Long {
    return ChronoUnit.DAYS.between(this.toLocalDate(), LocalDate.now())
}
