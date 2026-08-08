package com.ironlog.app.presentation.dashboard

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
import com.ironlog.app.domain.model.MetaPlanRotationEvent
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.app.fakes.FakeExerciseRepository
import com.ironlog.app.fakes.FakeMetaTrainingPlanRepository
import com.ironlog.app.fakes.FakeStatisticsRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepo: FakeWorkoutRepository
    private lateinit var exerciseRepo: FakeExerciseRepository
    private lateinit var statsRepo: FakeStatisticsRepository
    private lateinit var preferencesRepo: FakeAppPreferencesRepository
    private lateinit var planRepo: FakeTrainingPlanRepository
    private lateinit var metaPlanRepo: MetaTrainingPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workoutRepo = FakeWorkoutRepository()
        exerciseRepo = FakeExerciseRepository()
        statsRepo = FakeStatisticsRepository()
        preferencesRepo = FakeAppPreferencesRepository()
        planRepo = FakeTrainingPlanRepository()
        metaPlanRepo = FakeMetaTrainingPlanRepository(workoutRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(
        workoutRepo,
        statsRepo,
        exerciseRepo,
        preferencesRepo,
        planRepo,
        metaPlanRepo
    )

    @Test
    fun `loadDashboard nutzt Batch-Query statt N einzelne getExerciseById Calls`() = runTest {
        exerciseRepo.addExercise(com.ironlog.app.domain.model.Exercise(id = 1L, name = "Kniebeuge", primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BEINE, category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL))
        exerciseRepo.addExercise(com.ironlog.app.domain.model.Exercise(id = 2L, name = "Bankdrücken", primaryMuscleGroup = com.ironlog.app.domain.model.MuscleGroup.BRUST, category = com.ironlog.app.domain.model.ExerciseCategory.LANGHANTEL))
        val now = LocalDateTime.now()
        statsRepo.addRecord(com.ironlog.app.domain.model.PersonalRecord(id = 1L, exerciseId = 1L, type = com.ironlog.app.domain.model.RecordType.MAX_WEIGHT, value = 100.0, achievedAt = now.minusHours(1)))
        statsRepo.addRecord(com.ironlog.app.domain.model.PersonalRecord(id = 2L, exerciseId = 2L, type = com.ironlog.app.domain.model.RecordType.MAX_WEIGHT, value = 80.0, achievedAt = now.minusHours(2)))

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, exerciseRepo.getExerciseByIdCallCount) // Must be 0 — batch used instead
        assertEquals(2, vm.uiState.value.recentRecords.size)
    }

    @Test
    fun `Dashboard laedt initial mit isLoading true`() = runTest {
        val vm = createViewModel()
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `Dashboard laedt erfolgreich`() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `metaplan combine streamt keine vollen Sessions sondern Aggregate`() = runTest {
        val planId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val metaId = metaPlanRepo.saveMetaPlan(MetaTrainingPlan(name = "Meta",
            items = listOf(MetaTrainingPlanItem(trainingPlanId = planId, orderIndex = 0))))
        val yesterday = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(9, 0))
        workoutRepo.addSession(WorkoutSession(id = 99L, startTime = yesterday,
            endTime = yesterday.plusHours(1), durationSeconds = 3600,
            planId = planId, metaPlanId = metaId), isActive = false)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, workoutRepo.getAllCompletedSessionsListCallCount)
        assertNotNull(vm.uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaId })
    }

    @Test
    fun `startNewWorkout erstellt Session und ruft Callback`() = runTest {
        val vm = createViewModel()
        var receivedId: Long? = null

        vm.startNewWorkout { id, planId -> receivedId = id }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue((receivedId ?: 0L) > 0L)
    }

    @Test
    fun `startNewWorkoutWithPlan creates session with plan name`() = runTest {
        val vm = createViewModel()
        var createdSessionId: Long? = null
        var planIdPass: Long? = null
        
        val testPlan = TrainingPlan(id = 99L, name = "My Test Plan", exercises = emptyList())
        
        vm.startNewWorkoutWithPlan(testPlan) { sessionId, planId ->
            createdSessionId = sessionId
            planIdPass = planId
        }
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(createdSessionId != null)
        assertEquals(99L, planIdPass)
        val session = workoutRepo.getSessionById(createdSessionId!!)
        assertEquals("My Test Plan", session?.name)
    }

    @Test
    fun `training plan options show last done across standalone and meta plan sessions`() = runTest {
        val planId = planRepo.savePlan(TrainingPlan(name = "Push A"))
        val standaloneDoneAt = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(18, 0))
        val metaDoneAt = LocalDateTime.of(LocalDate.now().minusDays(4), LocalTime.of(7, 0))

        workoutRepo.addSession(
            WorkoutSession(
                id = 301L,
                startTime = metaDoneAt,
                endTime = metaDoneAt.plusHours(1),
                durationSeconds = 3600,
                planId = planId,
                metaPlanId = 900L
            ),
            isActive = false
        )
        workoutRepo.addSession(
            WorkoutSession(
                id = 302L,
                startTime = standaloneDoneAt,
                endTime = standaloneDoneAt.plusHours(1),
                durationSeconds = 3600,
                planId = planId
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val planStatus = vm.uiState.value.trainingPlans.firstOrNull { it.plan.id == planId }
        assertNotNull(planStatus)
        assertEquals(1L, planStatus!!.lastDoneDaysAgo)
    }

    @Test
    fun `startNewWorkoutWithMetaPlan startet Session mit plan und meta ids`() = runTest {
        val planId = planRepo.savePlan(TrainingPlan(name = "Meta Subplan"))
        val metaPlanId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta 1",
                items = listOf(MetaTrainingPlanItem(trainingPlanId = planId, orderIndex = 0))
            )
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var createdSessionId: Long? = null
        var callbackPlanId: Long? = null
        var callbackMetaPlanId: Long? = null

        vm.startNewWorkoutWithMetaPlan(metaPlanId) { sessionId, planFromCallback, metaFromCallback ->
            createdSessionId = sessionId
            callbackPlanId = planFromCallback
            callbackMetaPlanId = metaFromCallback
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(createdSessionId)
        assertEquals(planId, callbackPlanId)
        assertEquals(metaPlanId, callbackMetaPlanId)
        val session = workoutRepo.getSessionById(createdSessionId!!)
        assertEquals(planId, session?.planId)
        assertEquals(metaPlanId, session?.metaPlanId)
    }

    @Test
    fun `startNewWorkoutWithMetaPlan setzt Fehler wenn kein gueltiger Unterplan vorhanden ist`() = runTest {
        val metaPlanId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Broken Meta",
                items = listOf(MetaTrainingPlanItem(trainingPlanId = 9999L, orderIndex = 0))
            )
        )
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        var callbackCalled = false

        vm.startNewWorkoutWithMetaPlan(metaPlanId) { _, _, _ ->
            callbackCalled = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackCalled)
        assertTrue(vm.uiState.value.error?.contains("Meta-Plan ist unvollständig") == true)
        assertNull(workoutRepo.getActiveSession())
    }

    @Test
    fun `dashboard reloads stats when active workout is finished`() = runTest {
        val start = LocalDateTime.of(LocalDate.now(), LocalTime.of(7, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 123L,
                startTime = start,
                endTime = null,
                durationSeconds = 0,
                name = "Active"
            ),
            isActive = true
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.activeSession)
        assertEquals(0, vm.uiState.value.workoutsThisWeek)

        workoutRepo.finishWorkout(123L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.activeSession)
        assertEquals(1, vm.uiState.value.workoutsThisWeek)
        assertEquals(1, vm.uiState.value.workoutsThisMonth)
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.lastWorkout)
    }

    @Test
    fun `dashboard reloads stats when completed sessions are deleted without active-session transition`() = runTest {
        val completed = LocalDateTime.of(LocalDate.now(), LocalTime.of(8, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 900L,
                startTime = completed,
                endTime = completed.plusHours(1),
                durationSeconds = 3600,
                name = "Done"
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.workoutsThisWeek)
        assertNotNull(vm.uiState.value.lastWorkout)

        workoutRepo.deleteSession(900L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.uiState.value.workoutsThisWeek)
        assertEquals(0, vm.uiState.value.workoutsThisMonth)
        assertNull(vm.uiState.value.lastWorkout)
    }

    @Test
    fun `meta plan rotation uses last completed subplan as anchor`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val planCId = planRepo.savePlan(TrainingPlan(name = "Plan C"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta 1",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planCId, orderIndex = 2)
                )
            )
        )

        val twoDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(2), LocalTime.of(9, 0))
        val oneDayAgo = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(9, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 1,
                startTime = twoDaysAgo,
                endTime = twoDaysAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planAId,
                metaPlanId = metaId
            ),
            isActive = false
        )
        workoutRepo.addSession(
            WorkoutSession(
                id = 2,
                startTime = oneDayAgo,
                endTime = oneDayAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planBId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val option = vm.uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaId }
        assertNotNull(option)
        assertEquals(planCId, option?.nextPlan?.id)
        assertEquals(listOf(planCId, planAId, planBId), option?.rotationPlans?.map { it.plan.id })
    }

    @Test
    fun `weekly volume groups ISO week days across a year boundary together`() = runTest {
        // Dec 30 2024 (Mon) and Jan 1 2025 (Wed) fall in the same ISO-style week
        // (week 1 of week-based-year 2025). Grouping by calendar year instead of
        // week-based-year would incorrectly split them into "2024/KW1" and "2025/KW1".
        val dec30 = LocalDateTime.of(2024, 12, 30, 10, 0)
        val jan1 = LocalDateTime.of(2025, 1, 1, 10, 0)
        statsRepo.addExerciseSet(
            com.ironlog.app.domain.model.WorkoutSet(
                sessionId = 1L, exerciseId = 1L, setNumber = 1, reps = 10,
                weightKg = 100.0, completedAt = dec30
            )
        )
        statsRepo.addExerciseSet(
            com.ironlog.app.domain.model.WorkoutSet(
                sessionId = 1L, exerciseId = 1L, setNumber = 1, reps = 10,
                weightKg = 50.0, completedAt = jan1
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val kw1Entries = vm.uiState.value.weeklyVolume.filter { it.first == "KW1" }
        assertEquals(1, kw1Entries.size)
        // volume = weight * reps, summed across both sessions: (100*10) + (50*10)
        assertEquals(1500.0, kw1Entries.first().second, 0.0001)
    }

    @Test
    fun `startNewWorkout navigates to existing active session instead of assuming new plan`() = runTest {
        val active = WorkoutSession(id = 555L, startTime = LocalDateTime.now(), planId = 42L)
        workoutRepo.addSession(active, isActive = true)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var receivedId: Long? = null
        var receivedPlanId: Long? = null
        vm.startNewWorkout { id, planId ->
            receivedId = id
            receivedPlanId = planId
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(555L, receivedId)
        assertEquals(42L, receivedPlanId)
    }

    @Test
    fun `startNewWorkoutWithPlan navigates to matching active session instead of starting a new one`() = runTest {
        val plan = TrainingPlan(id = 42L, name = "Push")
        val active = WorkoutSession(id = 555L, startTime = LocalDateTime.now(), planId = plan.id)
        workoutRepo.addSession(active, isActive = true)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var receivedId: Long? = null
        vm.startNewWorkoutWithPlan(plan) { id, _ -> receivedId = id }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(555L, receivedId)
    }

    @Test
    fun `startNewWorkoutWithPlan shows error when a different plan is already active`() = runTest {
        val plan = TrainingPlan(id = 42L, name = "Push")
        val active = WorkoutSession(id = 555L, startTime = LocalDateTime.now(), planId = 99L)
        workoutRepo.addSession(active, isActive = true)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackCalled = false
        vm.startNewWorkoutWithPlan(plan) { _, _ -> callbackCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackCalled)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `meta plan rotation exposes per-subplan last done days`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta 2",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val threeDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(3), LocalTime.of(9, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 10,
                startTime = threeDaysAgo,
                endTime = threeDaysAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planAId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val option = vm.uiState.value.metaPlanOptions.firstOrNull { it.metaPlanId == metaId }
        assertNotNull(option)
        val statusByPlan = option!!.rotationPlans.associateBy({ it.plan.id }, { it.lastDoneDaysAgo })
        assertEquals(3L, statusByPlan[planAId])
        assertNull(statusByPlan[planBId])
    }

    @Test
    fun `skipCurrentMetaSubPlan wechselt zum naechsten Teilplan ohne Session`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta Skip",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val before = vm.uiState.value.metaPlanOptions.single()
        assertEquals(planAId, before.nextPlan?.id)
        assertTrue(before.canSkip)

        vm.skipCurrentMetaSubPlan(metaId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(planBId, vm.uiState.value.metaPlanOptions.single().nextPlan?.id)
        assertNull(workoutRepo.getActiveSession())
        assertTrue(workoutRepo.getAllCompletedSessionsList().isEmpty())
    }

    @Test
    fun `skipCurrentMetaSubPlan ist bei genau einem Teilplan deaktiviert`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Single Meta",
                items = listOf(MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0))
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val option = vm.uiState.value.metaPlanOptions.single()
        assertFalse(option.canSkip)

        vm.skipCurrentMetaSubPlan(metaId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(planAId, vm.uiState.value.metaPlanOptions.single().nextPlan?.id)
        assertTrue(metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan().first().isEmpty())
        assertNull(workoutRepo.getActiveSession())
    }

    @Test
    fun `skipCurrentMetaSubPlan blockiert bei aktiver Session`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta Blocked",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )
        workoutRepo.addSession(
            WorkoutSession(id = 555L, startTime = LocalDateTime.now(), planId = 99L),
            isActive = true
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipCurrentMetaSubPlan(metaId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(555L, workoutRepo.getActiveSession()?.id)
        assertTrue(vm.uiState.value.error?.contains("anderes Training aktiv") == true)
        assertTrue(metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan().first().isEmpty())
    }

    @Test
    fun `skipCurrentMetaSubPlan sperrt waehrend des Schreibvorgangs`() = runTest {
        metaPlanRepo = DelayingMetaPlanRepo(FakeMetaTrainingPlanRepository(workoutRepo))
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta Lock",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipCurrentMetaSubPlan(metaId)
        testDispatcher.scheduler.runCurrent()

        assertEquals(metaId, vm.uiState.value.skippingMetaPlanId)

        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.skippingMetaPlanId)
        assertEquals(planBId, vm.uiState.value.metaPlanOptions.single().nextPlan?.id)
    }

    @Test
    fun `start waehrend laufendem Skip erzeugt keine Session`() = runTest {
        metaPlanRepo = DelayingMetaPlanRepo(FakeMetaTrainingPlanRepository(workoutRepo))
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta Start Lock",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipCurrentMetaSubPlan(metaId)
        testDispatcher.scheduler.runCurrent()
        assertEquals(metaId, vm.uiState.value.skippingMetaPlanId)

        var started = false
        vm.startNewWorkoutWithMetaPlan(metaId) { _, _, _ -> started = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(started)
        assertNull(workoutRepo.getActiveSession())
        assertTrue(workoutRepo.getAllCompletedSessionsList().isEmpty())
        assertEquals(planBId, vm.uiState.value.metaPlanOptions.single().nextPlan?.id)
    }

    @Test
    fun `skipCurrentMetaSubPlan behandelt veralteten Vorschlag ohne Mutation`() = runTest {
        metaPlanRepo = RejectingMetaPlanRepo(FakeMetaTrainingPlanRepository(workoutRepo))
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta Stale",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipCurrentMetaSubPlan(metaId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(planAId, vm.uiState.value.metaPlanOptions.single().nextPlan?.id)
        assertTrue(vm.uiState.value.error?.contains("hat sich geändert") == true)
        assertTrue(metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan().first().isEmpty())
        assertNull(workoutRepo.getActiveSession())
    }

    private class RejectingMetaPlanRepo(
        private val delegate: FakeMetaTrainingPlanRepository
    ) : MetaTrainingPlanRepository {
        override fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>> = delegate.getAllMetaPlans()
        override fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>> =
            delegate.observeLastRotationEventPerMetaPlanSubPlan()
        override suspend fun getMetaPlanById(id: Long): MetaTrainingPlan? = delegate.getMetaPlanById(id)
        override suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long = delegate.saveMetaPlan(plan)
        override suspend fun deleteMetaPlan(metaPlanId: Long) = delegate.deleteMetaPlan(metaPlanId)
        override suspend fun skipCurrentSubPlan(
            metaPlanId: Long,
            expectedTrainingPlanId: Long
        ): Boolean = false
    }

    private class DelayingMetaPlanRepo(
        private val delegate: FakeMetaTrainingPlanRepository
    ) : MetaTrainingPlanRepository {
        override fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>> = delegate.getAllMetaPlans()
        override fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>> =
            delegate.observeLastRotationEventPerMetaPlanSubPlan()
        override suspend fun getMetaPlanById(id: Long): MetaTrainingPlan? = delegate.getMetaPlanById(id)
        override suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long = delegate.saveMetaPlan(plan)
        override suspend fun deleteMetaPlan(metaPlanId: Long) = delegate.deleteMetaPlan(metaPlanId)
        override suspend fun skipCurrentSubPlan(
            metaPlanId: Long,
            expectedTrainingPlanId: Long
        ): Boolean {
            kotlinx.coroutines.delay(1000)
            return delegate.skipCurrentSubPlan(metaPlanId, expectedTrainingPlanId)
        }
    }
}
