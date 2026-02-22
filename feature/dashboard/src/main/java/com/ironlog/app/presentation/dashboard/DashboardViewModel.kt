package com.ironlog.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.catchAndLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

data class DashboardUiState(
    val activeSession: WorkoutSession? = null,
    val trainingPlans: List<TrainingPlan> = emptyList(),
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
    private val trainingPlanRepository: TrainingPlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboard()
        observeActiveSession()
    }

    private fun observeActiveSession() {
        viewModelScope.launch {
            workoutRepository.observeActiveSession()
                .catchAndLog("DashboardVM")
                .collect { session ->
                    _uiState.value = _uiState.value.copy(activeSession = session)
                }
        }
        
        viewModelScope.launch {
            trainingPlanRepository.getAllPlans()
                .catchAndLog("DashboardVM_Plans")
                .collect { plans ->
                    _uiState.value = _uiState.value.copy(trainingPlans = plans)
                }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val preferences = appPreferencesRepository.preferences.first()
                val now = LocalDate.now()

                val weekAnchor = when (preferences.weekStart) {
                    WeekStart.MONDAY -> DayOfWeek.MONDAY
                    WeekStart.SUNDAY -> DayOfWeek.SUNDAY
                }

                val startOfWeek = now.with(TemporalAdjusters.previousOrSame(weekAnchor))
                val startOfWeekMillis = com.ironlog.app.data.local.entity.EpochConverter.toLong(startOfWeek.atStartOfDay())
                val workoutsThisWeek = workoutRepository.getCompletedSessionCountSince(startOfWeekMillis)

                val startOfMonth = now.withDayOfMonth(1)
                val startOfMonthMillis = com.ironlog.app.data.local.entity.EpochConverter.toLong(startOfMonth.atStartOfDay())
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

                // --- Muscle Heatmap: sets per muscle group this week ---
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

                // --- Weekly Volume Trend: last 8 weeks ---
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

                _uiState.value = DashboardUiState(
                    activeSession = _uiState.value.activeSession,
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Dashboard konnte nicht geladen werden: ${e.message}"
                )
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
        _uiState.value = _uiState.value.copy(showPlanSelectionSheet = true)
    }

    fun dismissPlanSelectionSheet() {
        _uiState.value = _uiState.value.copy(showPlanSelectionSheet = false)
    }

    fun startNewWorkout(onSessionCreated: (Long, Long?) -> Unit) {
        viewModelScope.launch {
            try {
                val autoName = "Training ${java.time.LocalDate.now().format(DateFormatting.DATE_SHORT)}"
                val sessionId = workoutRepository.startWorkout(autoName)
                onSessionCreated(sessionId, null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Training konnte nicht gestartet werden: ${e.message}"
                )
            }
        }
    }

    fun startNewWorkoutWithPlan(plan: TrainingPlan, onSessionCreated: (Long, Long?) -> Unit) {
        viewModelScope.launch {
            try {
                val sessionId = workoutRepository.startWorkout(plan.name)
                onSessionCreated(sessionId, plan.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Training nach Plan konnte nicht gestartet werden: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
