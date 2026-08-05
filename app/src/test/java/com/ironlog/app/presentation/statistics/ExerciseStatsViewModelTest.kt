package com.ironlog.app.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseStatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var statisticsRepo: FakeStatisticsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        exerciseRepo = FakeExerciseRepository()
        statisticsRepo = FakeStatisticsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateChartData single-pass produziert korrekte Ergebnisse fuer alle Metriken`() = runTest {
        val exercise = Exercise(id = 1L, name = "Kniebeuge", primaryMuscleGroup = MuscleGroup.BEINE, category = ExerciseCategory.LANGHANTEL)
        exerciseRepo.addExercise(exercise)
        val targetExerciseId = exercise.id

        // Session 1: two sets (base date)
        val base = LocalDateTime.of(2026, 1, 1, 10, 0)
        statisticsRepo.addExerciseSet(WorkoutSet(id = 1L, sessionId = 1L, exerciseId = targetExerciseId,
            setNumber = 1, reps = 5, weightKg = 80.0, isWarmup = false, completedAt = base))
        statisticsRepo.addExerciseSet(WorkoutSet(id = 2L, sessionId = 1L, exerciseId = targetExerciseId,
            setNumber = 2, reps = 8, weightKg = 70.0, isWarmup = false, completedAt = base.plusMinutes(5)))
        // Session 2: one heavier set, later date
        statisticsRepo.addExerciseSet(WorkoutSet(id = 3L, sessionId = 2L, exerciseId = targetExerciseId,
            setNumber = 1, reps = 3, weightKg = 90.0, isWarmup = false, completedAt = base.plusDays(3)))

        val vm = ExerciseStatsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to targetExerciseId)),
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // WEIGHT metric: max per session, sorted chronologically
        assertEquals(2, vm.uiState.value.chartData.size)
        assertEquals(80f, vm.uiState.value.chartData[0].value)
        assertEquals(90f, vm.uiState.value.chartData[1].value)

        // VOLUME metric: sum per session
        vm.onMetricSelected(ChartMetric.VOLUME)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(960f, vm.uiState.value.chartData[0].value) // 80*5 + 70*8
        assertEquals(270f, vm.uiState.value.chartData[1].value) // 90*3
    }

    @Test
    fun `chart data wird ueber Jahreswechsel chronologisch statt nach Label sortiert`() = runTest {
        val exercise = Exercise(
            id = 1L,
            name = "Kniebeuge",
            primaryMuscleGroup = MuscleGroup.BEINE,
            category = ExerciseCategory.LANGHANTEL
        )
        exerciseRepo.addExercise(exercise)

        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 1L,
                sessionId = 10L,
                exerciseId = exercise.id,
                setNumber = 1,
                reps = 5,
                weightKg = 100.0,
                isWarmup = false,
                completedAt = LocalDateTime.of(2025, 12, 31, 18, 0)
            )
        )
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 2L,
                sessionId = 11L,
                exerciseId = exercise.id,
                setNumber = 1,
                reps = 5,
                weightKg = 105.0,
                isWarmup = false,
                completedAt = LocalDateTime.of(2026, 1, 2, 18, 0)
            )
        )

        val vm = ExerciseStatsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exercise.id)),
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val labels = vm.uiState.value.chartData.map { it.dateLabel }
        assertEquals(listOf("31.12", "2.1"), labels)
    }
}
