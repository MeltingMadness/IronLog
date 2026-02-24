package com.ironlog.app.presentation.plans

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        metaPlanRepo = FakeMetaTrainingPlanRepository()
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
}
