package com.ironlog.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.local.entity.EpochConverter
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
    val rotationPlans: List<DashboardMetaSubPlanStatus>
)

data class DashboardMetaSubPlanStatus(
    val plan: TrainingPlan,
    val lastDoneDaysAgo: Long?
)

data class DashboardUiState(
    val activeSession: WorkoutSession? = null,
    val trainingPlans: List<TrainingPlan> = emptyList(),
    val metaPlanOptions: List<DashboardMetaPlanOption> = emptyList(),
    val showPlanSelectionSheet: Boolean = false,
    val workoutsThisWeek: Int = 0,
    val workoutsThisMonth: Int = 0,
    val currentStreak: Int = 0,
    val recentRecords: List<Pair<PersonalRecord, String>> = emptyList(),
    val lastWorkout: WorkoutSession? = null,
    val lastWorkoutExerciseCount: Int = 0,
    val muscleHeatmap: Map<MuscleGroup, Int> = emptyMap(),
    val weeklyVolume: List<Pair<String, Double>> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
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
            trainingPlanRepository.getAllPlans()
                .catchAndLog("DashboardVM_Plans")
                .collect { plans ->
                    _uiState.update { it.copy(trainingPlans = plans) }
                }
        }

        viewModelScope.launch {
            combine(
                trainingPlanRepository.getAllPlans(),
                workoutRepository.getAllCompletedSessions(),
                metaTrainingPlanRepository.getAllMetaPlans()
            ) { plans, completedSessions, metaPlans ->
                buildMetaPlanOptions(
                    plans = plans,
                    completedSessions = completedSessions,
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

                val streak = calculateStreak()

                val records = statisticsRepository.getRecentRecordsList(5)
                val recordsWithNames = records.map { record ->
                    val exercise = exerciseRepository.getExerciseById(record.exerciseId)
                    Pair(record, exercise?.name ?: "Unbekannt")
                }

                val lastWorkout = workoutRepository.getLastCompletedSession()
                val lastWorkoutExerciseCount = if (lastWorkout != null) {
                    workoutRepository.getExerciseIdsForSession(lastWorkout.id).size
                } else 0

                val weekSets = statisticsRepository.getWorkSetsCompletedSince(startOfWeekMillis)
                val exerciseIds = weekSets.map { it.exerciseId }.distinct()
                val exerciseMap = exerciseIds.mapNotNull { id ->
                    exerciseRepository.getExerciseById(id)?.let { id to it }
                }.toMap()

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
                        val year = setDate.year
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
                        currentStreak = streak,
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

    suspend fun calculateStreak(): Int {
        val sessions = workoutRepository.getAllCompletedSessionsList()
        if (sessions.isEmpty()) return 0

        val workoutDates = sessions.map { it.startTime.toLocalDate() }.distinct().sortedDescending()
        var streak = 0
        var expectedDate = LocalDate.now()

        if (workoutDates.firstOrNull() != expectedDate) {
            expectedDate = expectedDate.minusDays(1)
        }

        for (date in workoutDates) {
            if (date == expectedDate) {
                streak++
                expectedDate = expectedDate.minusDays(1)
            } else if (date.isBefore(expectedDate)) {
                break
            }
        }

        return streak
    }

    fun showPlanSelectionSheet() {
        _uiState.update { it.copy(showPlanSelectionSheet = true) }
    }

    fun dismissPlanSelectionSheet() {
        _uiState.update { it.copy(showPlanSelectionSheet = false) }
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
                        it.copy(error = "Meta-Plan ist unvollstaendig oder enthaelt keine gueltigen Unterplaene.")
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
        completedSessions: List<WorkoutSession>,
        metaPlans: List<MetaTrainingPlan>
    ): List<DashboardMetaPlanOption> {
        if (metaPlans.isEmpty()) return emptyList()

        val plansById = plans.associateBy { it.id }
        return metaPlans.map { metaPlan ->
            val orderedSubPlans = metaPlan.items
                .sortedBy { it.orderIndex }
                .mapNotNull { item -> plansById[item.trainingPlanId] }

            if (orderedSubPlans.isEmpty()) {
                return@map DashboardMetaPlanOption(
                    metaPlanId = metaPlan.id,
                    metaPlanName = metaPlan.name,
                    nextPlan = null,
                    rotationPlans = emptyList()
                )
            }

            val completedForMeta = completedSessions
                .filter { session ->
                    session.metaPlanId == metaPlan.id &&
                        session.planId != null &&
                        session.endTime != null
                }

            val lastCompleted = completedForMeta.maxByOrNull { it.endTime ?: it.startTime }
            val nextIndex = lastCompleted?.planId?.let { lastPlanId ->
                val lastIndex = orderedSubPlans.indexOfFirst { it.id == lastPlanId }
                if (lastIndex >= 0) (lastIndex + 1) % orderedSubPlans.size else 0
            } ?: 0

            val rotatedPlans = (0 until orderedSubPlans.size).map { offset ->
                orderedSubPlans[(nextIndex + offset) % orderedSubPlans.size]
            }

            val lastDoneByPlanId = completedForMeta
                .groupBy { it.planId }
                .mapValues { (_, sessions) ->
                    sessions.maxByOrNull { it.endTime ?: it.startTime }?.startTime
                }

            DashboardMetaPlanOption(
                metaPlanId = metaPlan.id,
                metaPlanName = metaPlan.name,
                nextPlan = rotatedPlans.firstOrNull(),
                rotationPlans = rotatedPlans.map { plan ->
                    DashboardMetaSubPlanStatus(
                        plan = plan,
                        lastDoneDaysAgo = lastDoneByPlanId[plan.id]?.toRelativeDaysAgo()
                    )
                }
            )
        }
    }
}

private fun LocalDateTime.toRelativeDaysAgo(): Long {
    return ChronoUnit.DAYS.between(this.toLocalDate(), LocalDate.now())
}
