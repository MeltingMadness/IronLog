package com.ironlog.app.presentation.statistics

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.MuscleVolume
import com.ironlog.app.domain.util.VolumeStatus
import com.ironlog.app.fakes.FakeAppPreferencesRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseStatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var statisticsRepo: FakeStatisticsRepository
    private lateinit var preferencesRepo: FakeAppPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        exerciseRepo = FakeExerciseRepository()
        statisticsRepo = FakeStatisticsRepository()
        preferencesRepo = FakeAppPreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(exerciseId: Long): ExerciseStatsViewModel =
        ExerciseStatsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exerciseId)),
            exerciseRepository = exerciseRepo,
            statisticsRepository = statisticsRepo,
            appPreferencesRepository = preferencesRepo
        )

    @Test
    fun `updateChartData single-pass produziert korrekte Ergebnisse fuer alle Metriken`() = runTest {
        val exercise = Exercise(id = 1L, name = "Kniebeuge", primaryMuscleGroup = MuscleGroup.BEINE, category = ExerciseCategory.LANGHANTEL)
        exerciseRepo.addExercise(exercise)
        val targetExerciseId = exercise.id

        // Session 1: two sets (base date)
        val base = LocalDateTime.of(2026, 1, 1, 10, 0)
        statisticsRepo.addExerciseSet(WorkoutSet(id = 1L, sessionId = 1L, exerciseId = targetExerciseId,
            setNumber = 1, reps = 5, weightKg = 80.0, setType = SetType.NORMAL, completedAt = base))
        statisticsRepo.addExerciseSet(WorkoutSet(id = 2L, sessionId = 1L, exerciseId = targetExerciseId,
            setNumber = 2, reps = 8, weightKg = 70.0, setType = SetType.NORMAL, completedAt = base.plusMinutes(5)))
        // Session 2: one heavier set, later date
        statisticsRepo.addExerciseSet(WorkoutSet(id = 3L, sessionId = 2L, exerciseId = targetExerciseId,
            setNumber = 1, reps = 3, weightKg = 90.0, setType = SetType.NORMAL, completedAt = base.plusDays(3)))

        val vm = createViewModel(targetExerciseId)
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
                setType = SetType.NORMAL,
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
                setType = SetType.NORMAL,
                completedAt = LocalDateTime.of(2026, 1, 2, 18, 0)
            )
        )

        val vm = createViewModel(exercise.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val labels = vm.uiState.value.chartData.map { it.dateLabel }
        assertEquals(listOf("31.12", "2.1"), labels)
    }

    @Test
    fun `e1rmProgression leitet ersten aktuellen und besten Session-Wert ab`() = runTest {
        val exercise = Exercise(
            id = 1L,
            name = "Bankdrücken",
            primaryMuscleGroup = MuscleGroup.BRUST,
            category = ExerciseCategory.LANGHANTEL
        )
        exerciseRepo.addExercise(exercise)
        val base = LocalDateTime.of(2026, 1, 1, 10, 0)

        // Session 1: 80x5 -> Epley 93,33
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 1L, sessionId = 1L, exerciseId = exercise.id, setNumber = 1,
                reps = 5, weightKg = 80.0, setType = SetType.NORMAL, completedAt = base
            )
        )
        // Session 2: 90x3 -> Epley 99,0
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 2L, sessionId = 2L, exerciseId = exercise.id, setNumber = 1,
                reps = 3, weightKg = 90.0, setType = SetType.NORMAL, completedAt = base.plusDays(2)
            )
        )
        // Session 3 (aktuellste): 85x8 -> Epley 107,67 (auch Bestwert)
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 3L, sessionId = 3L, exerciseId = exercise.id, setNumber = 1,
                reps = 8, weightKg = 85.0, setType = SetType.NORMAL, completedAt = base.plusDays(5)
            )
        )

        val vm = createViewModel(exercise.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val progression = vm.uiState.value.e1rmProgression
        assertNotNull(progression)
        assertEquals(93.3333f, progression!!.first, 0.01f)
        assertEquals(107.6667f, progression.latest, 0.01f)
        assertEquals(107.6667f, progression.best, 0.01f)
        assertEquals(14.3334f, progression.delta, 0.01f)
        assertEquals(15.3571f, progression.deltaPercent, 0.01f)
    }

    @Test
    fun `e1rmProgression ist null ohne abgeschlossene Sätze`() = runTest {
        val exercise = Exercise(
            id = 1L,
            name = "Kreuzheben",
            primaryMuscleGroup = MuscleGroup.RUECKEN,
            category = ExerciseCategory.LANGHANTEL
        )
        exerciseRepo.addExercise(exercise)

        val vm = createViewModel(exercise.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.e1rmProgression)
    }

    @Test
    fun `weeklyMuscleVolume aggregiert gewichtete Arbeitssaetze der Woche je Muskelgruppe`() = runTest {
        val benchPress = Exercise(
            id = 1L,
            name = "Bankdrücken",
            primaryMuscleGroup = MuscleGroup.BRUST,
            secondaryMuscleGroups = listOf(MuscleGroup.TRIZEPS),
            category = ExerciseCategory.LANGHANTEL
        )
        val fly = Exercise(
            id = 2L,
            name = "Fliegende",
            primaryMuscleGroup = MuscleGroup.BRUST,
            secondaryMuscleGroups = listOf(MuscleGroup.SCHULTERN),
            category = ExerciseCategory.KURZHANTEL
        )
        exerciseRepo.addExercise(benchPress)
        exerciseRepo.addExercise(fly)

        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // Diese Woche: 6 Arbeitssaetze Bankdruecken (Brust + Trizeps)
        repeat(6) { i ->
            statisticsRepo.addExerciseSet(
                WorkoutSet(
                    id = i + 1L, sessionId = 1L, exerciseId = benchPress.id, setNumber = i + 1,
                    reps = 8, weightKg = 60.0, setType = SetType.NORMAL, completedAt = monday.atTime(10, 0)
                )
            )
        }
        // Diese Woche: 6 Arbeitssaetze Fliegende (Brust + Schultern), davon 1 FAILURE
        repeat(6) { i ->
            statisticsRepo.addExerciseSet(
                WorkoutSet(
                    id = 10L + i, sessionId = 2L, exerciseId = fly.id, setNumber = i + 1,
                    reps = 10, weightKg = 20.0,
                    setType = if (i == 0) SetType.FAILURE else SetType.NORMAL,
                    completedAt = monday.plusDays(2).atTime(18, 0)
                )
            )
        }
        // Vorige Woche: zaehlt nicht fuer das Wochenvolumen
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 30L, sessionId = 3L, exerciseId = benchPress.id, setNumber = 1,
                reps = 8, weightKg = 60.0, setType = SetType.NORMAL,
                completedAt = monday.minusDays(1).atTime(10, 0)
            )
        )
        // Warmup diese Woche: zaehlt nicht
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 31L, sessionId = 4L, exerciseId = benchPress.id, setNumber = 1,
                reps = 5, weightKg = 20.0, setType = SetType.WARMUP, completedAt = monday.atTime(9, 0)
            )
        )

        listOf(1L, 2L, 3L, 4L).forEach { statisticsRepo.markSessionCompleted(it) }

        val vm = createViewModel(benchPress.id)
        testDispatcher.scheduler.advanceUntilIdle()

        // Nur Muskelgruppen der Uebung (Brust primaer, Trizeps sekundaer) - Schultern fehlt
        val volume = vm.uiState.value.weeklyMuscleVolume
        assertEquals(listOf(MuscleGroup.BRUST, MuscleGroup.TRIZEPS), volume.map { it.muscleGroup })
        assertTrue(volume.none { it.muscleGroup == MuscleGroup.SCHULTERN })

        assertEquals(12.0, volume[0].weeklySets, 0.0) // 6 + 6 Arbeitssaetze, primaer 1,0
        assertEquals(3.0, volume[1].weeklySets, 0.0)  // 6 x 0,5 sekundaer
        assertEquals(VolumeStatus.OPTIMAL, volume[0].status) // 12 >= MEV 10 (Brust)
        assertEquals(VolumeStatus.LOW, volume[1].status)     // 3 < MEV 6 (Trizeps)
    }

    @Test
    fun `weeklyMuscleVolume ist leer ohne abgeschlossene Sessions`() = runTest {
        val exercise = Exercise(
            id = 1L,
            name = "Kreuzheben",
            primaryMuscleGroup = MuscleGroup.RUECKEN,
            secondaryMuscleGroups = listOf(MuscleGroup.BIZEPS),
            category = ExerciseCategory.LANGHANTEL
        )
        exerciseRepo.addExercise(exercise)

        // Sets vorhanden, aber Session nicht abgeschlossen -> zaehlt nicht als Wochenvolumen
        statisticsRepo.addExerciseSet(
            WorkoutSet(
                id = 1L, sessionId = 1L, exerciseId = exercise.id, setNumber = 1,
                reps = 8, weightKg = 60.0, setType = SetType.NORMAL, completedAt = LocalDateTime.now()
            )
        )

        val vm = createViewModel(exercise.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<MuscleVolume>(), vm.uiState.value.weeklyMuscleVolume)
    }
}
