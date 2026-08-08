package com.ironlog.app.presentation.plans

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
import com.ironlog.app.domain.model.MetaPlanRotationEvent
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.app.fakes.FakeMetaTrainingPlanRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import com.ironlog.app.fakes.FakeWorkoutRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetaPlanListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var planRepo: FakeTrainingPlanRepository
    private lateinit var workoutRepo: FakeWorkoutRepository
    private lateinit var metaPlanRepo: FakeMetaTrainingPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        planRepo = FakeTrainingPlanRepository()
        workoutRepo = FakeWorkoutRepository()
        metaPlanRepo = FakeMetaTrainingPlanRepository(workoutRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nextSubPlan uses last completed subplan as anchor`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val planCId = planRepo.savePlan(TrainingPlan(name = "Plan C"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planCId, orderIndex = 2)
                )
            )
        )

        val twoDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(2), LocalTime.of(8, 0))
        val oneDayAgo = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(8, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 1L,
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
                id = 2L,
                startTime = oneDayAgo,
                endTime = oneDayAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planBId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = MetaPlanListViewModel(planRepo, workoutRepo, metaPlanRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val item = vm.uiState.value.items.firstOrNull { it.metaPlan.id == metaId }
        assertNotNull(item)
        assertEquals(planCId, item?.nextSubPlan?.id)
    }

    @Test
    fun `nextSubPlan falls back to first subplan when last completed plan is missing`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val yesterday = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(9, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 9L,
                startTime = yesterday,
                endTime = yesterday.plusHours(1),
                durationSeconds = 3600,
                planId = 99999L,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = MetaPlanListViewModel(planRepo, workoutRepo, metaPlanRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val item = vm.uiState.value.items.firstOrNull { it.metaPlan.id == metaId }
        assertNotNull(item)
        assertEquals(planAId, item?.nextSubPlan?.id)
    }

    @Test
    fun `nextSubPlan ignores sessions from plans no longer in rotation`() = runTest {
        // Regression test for the Dashboard vs MetaPlanList divergence bug: a session for a
        // sub-plan that has since been removed from the rotation (but the TrainingPlan itself
        // still exists) must not be treated as "most recent" for rotation purposes.
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val removedPlanId = planRepo.savePlan(TrainingPlan(name = "Removed Plan"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1)
                )
            )
        )

        val twoDaysAgo = LocalDateTime.of(LocalDate.now().minusDays(2), LocalTime.of(8, 0))
        val oneDayAgo = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(8, 0))
        workoutRepo.addSession(
            WorkoutSession(
                id = 1L,
                startTime = twoDaysAgo,
                endTime = twoDaysAgo.plusHours(1),
                durationSeconds = 3600,
                planId = planAId,
                metaPlanId = metaId
            ),
            isActive = false
        )
        // Most recent session overall, but its plan is no longer part of the rotation.
        workoutRepo.addSession(
            WorkoutSession(
                id = 2L,
                startTime = oneDayAgo,
                endTime = oneDayAgo.plusHours(1),
                durationSeconds = 3600,
                planId = removedPlanId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val vm = MetaPlanListViewModel(planRepo, workoutRepo, metaPlanRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val item = vm.uiState.value.items.firstOrNull { it.metaPlan.id == metaId }
        assertNotNull(item)
        assertEquals(planBId, item?.nextSubPlan?.id)
    }

    @Test
    fun `skipCurrentSubPlan records rotation event in fake repository`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val planCId = planRepo.savePlan(TrainingPlan(name = "Plan C"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planCId, orderIndex = 2)
                )
            )
        )

        assertTrue(metaPlanRepo.skipCurrentSubPlan(metaId, planAId))
        val events = metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan().first()

        assertEquals(
            listOf(
                MetaPlanRotationEvent(
                    trainingPlanId = planAId,
                    metaPlanId = metaId,
                    lastEventAt = 1L
                )
            ),
            events
        )
    }

    @Test
    fun `skipCurrentSubPlan rejects stale expected plan in fake repository`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val planCId = planRepo.savePlan(TrainingPlan(name = "Plan C"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planCId, orderIndex = 2)
                )
            )
        )

        assertTrue(metaPlanRepo.skipCurrentSubPlan(metaId, planAId))
        assertFalse(metaPlanRepo.skipCurrentSubPlan(metaId, planAId))
        val events = metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan().first()

        assertEquals(
            listOf(
                MetaPlanRotationEvent(
                    trainingPlanId = planAId,
                    metaPlanId = metaId,
                    lastEventAt = 1L
                )
            ),
            events
        )
    }

    @Test
    fun `rotation events aggregate completed sessions and skips`() = runTest {
        val planAId = planRepo.savePlan(TrainingPlan(name = "Plan A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "Plan B"))
        val planCId = planRepo.savePlan(TrainingPlan(name = "Plan C"))
        val metaId = metaPlanRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planCId, orderIndex = 2)
                )
            )
        )

        val planAStart = LocalDateTime.of(2026, 8, 1, 8, 0)
        val planBStart = LocalDateTime.of(2026, 8, 2, 8, 0)
        workoutRepo.addSession(
            WorkoutSession(
                id = 1L,
                startTime = planAStart,
                endTime = planAStart.plusHours(1),
                durationSeconds = 3600,
                planId = planAId,
                metaPlanId = metaId
            ),
            isActive = false
        )
        workoutRepo.addSession(
            WorkoutSession(
                id = 2L,
                startTime = planBStart,
                endTime = planBStart.plusHours(1),
                durationSeconds = 3600,
                planId = planBId,
                metaPlanId = metaId
            ),
            isActive = false
        )

        val sessionEvents = metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan()
            .first()
        assertEquals(2, sessionEvents.size)
        // planC has no event yet, so it is the current rotation target.
        assertTrue(metaPlanRepo.skipCurrentSubPlan(metaId, planCId))
        val eventsByPlan = metaPlanRepo.observeLastRotationEventPerMetaPlanSubPlan()
            .first()
            .associateBy { it.trainingPlanId }

        assertEquals(planBStart.toEpochMillis(), eventsByPlan.getValue(planBId).lastEventAt)
        assertTrue(
            eventsByPlan.getValue(planCId).lastEventAt >
                eventsByPlan.getValue(planBId).lastEventAt
        )
    }
}

private fun java.time.LocalDateTime.toEpochMillis(): Long =
    atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
