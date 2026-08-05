package com.ironlog.app.presentation.plans

import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.fakes.FakeMetaTrainingPlanRepository
import com.ironlog.app.fakes.FakeTrainingPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetaPlanEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `moveSelectedPlanUp ignores stale out-of-bounds index`() = runTest {
        val planRepo = FakeTrainingPlanRepository()
        val metaRepo = FakeMetaTrainingPlanRepository()
        val planAId = planRepo.savePlan(TrainingPlan(name = "A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "B"))

        val vm = MetaPlanEditorViewModel(planRepo, metaRepo)
        advanceUntilIdle()

        vm.togglePlan(planAId)
        vm.togglePlan(planBId)
        val before = vm.uiState.value.selectedPlanIds

        vm.moveSelectedPlanUp(index = 99)

        assertEquals(before, vm.uiState.value.selectedPlanIds)
    }

    @Test
    fun `observeAvailablePlans surfaces repository errors instead of crashing`() = runTest {
        val failingPlanRepo = object : TrainingPlanRepository {
            override fun getAllPlans(): Flow<List<TrainingPlan>> = flow {
                throw IllegalStateException("boom")
            }

            override suspend fun getPlanById(id: Long): TrainingPlan? = null

            override suspend fun savePlan(plan: TrainingPlan): Long = 0L

            override suspend fun deletePlan(planId: Long) = Unit
        }

        val vm = MetaPlanEditorViewModel(
            trainingPlanRepository = failingPlanRepo,
            metaTrainingPlanRepository = FakeMetaTrainingPlanRepository()
        )

        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error?.contains("Unterpläne konnten nicht geladen werden") == true)
    }

    @Test
    fun `initialize deduplicates repeated trainingPlanIds from legacy meta data`() = runTest {
        val planRepo = FakeTrainingPlanRepository()
        val metaRepo = FakeMetaTrainingPlanRepository()
        val planAId = planRepo.savePlan(TrainingPlan(name = "A"))
        val planBId = planRepo.savePlan(TrainingPlan(name = "B"))
        val metaId = metaRepo.saveMetaPlan(
            MetaTrainingPlan(
                name = "Meta",
                items = listOf(
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 0),
                    MetaTrainingPlanItem(trainingPlanId = planAId, orderIndex = 1),
                    MetaTrainingPlanItem(trainingPlanId = planBId, orderIndex = 2)
                )
            )
        )

        val vm = MetaPlanEditorViewModel(planRepo, metaRepo)
        vm.initialize(metaId)
        advanceUntilIdle()

        assertEquals(listOf(planAId, planBId), vm.uiState.value.selectedPlanIds)
    }
}
