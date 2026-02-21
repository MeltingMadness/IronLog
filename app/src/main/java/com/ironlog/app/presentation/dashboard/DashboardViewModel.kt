package com.ironlog.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.domain.model.PersonalRecord
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
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

data class DashboardUiState(
    val activeSession: WorkoutSession? = null,
    val workoutsThisWeek: Int = 0,
    val workoutsThisMonth: Int = 0,
    val currentStreak: Int = 0,
    val recentRecords: List<Pair<PersonalRecord, String>> = emptyList(),
    val lastWorkout: WorkoutSession? = null,
    val lastWorkoutExerciseCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class DashboardViewModel(
    private val workoutRepository: WorkoutRepository,
    private val statisticsRepository: StatisticsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appPreferencesRepository: AppPreferencesRepository
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

                _uiState.value = DashboardUiState(
                    activeSession = _uiState.value.activeSession,
                    workoutsThisWeek = workoutsThisWeek,
                    workoutsThisMonth = workoutsThisMonth,
                    currentStreak = streak,
                    recentRecords = recordsWithNames,
                    lastWorkout = lastWorkout,
                    lastWorkoutExerciseCount = lastWorkoutExerciseCount,
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

    internal suspend fun calculateStreak(): Int {
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

    fun startNewWorkout(onSessionCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val autoName = "Training ${java.time.LocalDate.now().format(DateFormatting.DATE_SHORT)}"
                val sessionId = workoutRepository.startWorkout(autoName)
                onSessionCreated(sessionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Training konnte nicht gestartet werden: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
