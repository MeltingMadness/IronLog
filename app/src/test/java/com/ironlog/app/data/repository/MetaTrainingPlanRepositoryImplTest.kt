package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.dao.MetaTrainingPlanWithItems
import com.ironlog.app.data.local.dao.LastMetaPlanRotationEventRow
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaPlanSkipEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.domain.model.MetaPlanRotationEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaTrainingPlanRepositoryImplTest {

    @Test
    fun `deleteMetaPlan uses atomic detach-then-delete dao method`() = runTest {
        val dao = FakeMetaTrainingPlanDao().apply {
            metaPlans[1L] = MetaTrainingPlanEntity(id = 1L, name = "Push Split", createdAt = 1L)
        }
        val repository = MetaTrainingPlanRepositoryImpl(dao)

        repository.deleteMetaPlan(1L)

        assertEquals(listOf("atomic:1", "detach:1", "delete:1"), dao.operationOrder)
        assertTrue(dao.metaPlans.isEmpty())
    }

    @Test
    fun `skipCurrentSubPlan delegates to transactional dao method`() = runTest {
        val dao = mockk<MetaTrainingPlanDao>()
        coEvery { dao.skipCurrentSubPlanIfCurrent(5L, 10L, any<Long>()) } returns true
        val repository = MetaTrainingPlanRepositoryImpl(dao)

        val skipped = repository.skipCurrentSubPlan(
            metaPlanId = 5L,
            expectedTrainingPlanId = 10L
        )

        assertTrue(skipped)
        coVerify(exactly = 1) { dao.skipCurrentSubPlanIfCurrent(5L, 10L, any<Long>()) }
    }

    @Test
    fun `skipCurrentSubPlan rejects stale expected plan without inserting skip`() = runTest {
        val dao = mockk<MetaTrainingPlanDao>()
        coEvery { dao.skipCurrentSubPlanIfCurrent(5L, 20L, any<Long>()) } returns false
        val repository = MetaTrainingPlanRepositoryImpl(dao)

        val skipped = repository.skipCurrentSubPlan(
            metaPlanId = 5L,
            expectedTrainingPlanId = 20L
        )

        assertFalse(skipped)
        coVerify(exactly = 0) { dao.insertMetaPlanSkip(match { it.trainingPlanId == 20L }) }
    }

    @Test
    fun `observeLastRotationEventPerMetaPlanSubPlan maps dao rows`() = runTest {
        val dao = mockk<MetaTrainingPlanDao>()
        val rows = listOf(
            LastMetaPlanRotationEventRow(trainingPlanId = 10L, metaPlanId = 5L, lastEventAt = 200L),
            LastMetaPlanRotationEventRow(trainingPlanId = 20L, metaPlanId = 5L, lastEventAt = 100L)
        )
        every { dao.observeLastRotationEventPerMetaPlanSubPlan() } returns flowOf(rows)
        val repository = MetaTrainingPlanRepositoryImpl(dao)

        val events = repository.observeLastRotationEventPerMetaPlanSubPlan().first()

        assertEquals(
            listOf(
                MetaPlanRotationEvent(trainingPlanId = 10L, metaPlanId = 5L, lastEventAt = 200L),
                MetaPlanRotationEvent(trainingPlanId = 20L, metaPlanId = 5L, lastEventAt = 100L)
            ),
            events
        )
    }

    private class FakeMetaTrainingPlanDao : MetaTrainingPlanDao {
        val metaPlans = linkedMapOf<Long, MetaTrainingPlanEntity>()
        val itemsByMetaPlan = linkedMapOf<Long, MutableList<MetaPlanItemEntity>>()
        val skipsByMetaPlan = linkedMapOf<Long, MutableList<MetaPlanSkipEntity>>()
        val operationOrder = mutableListOf<String>()
        private val metaPlansFlow = MutableStateFlow<List<MetaTrainingPlanEntity>>(emptyList())
        private val rotationEventsFlow =
            MutableStateFlow<List<LastMetaPlanRotationEventRow>>(emptyList())
        var nextMetaPlanId = 100L
        var nextItemId = 1000L
        var nextSkipId = 10000L

        private fun publish() {
            metaPlansFlow.value = metaPlans.values.sortedByDescending { it.createdAt }
        }

        private fun publishRotationEvents() {
            rotationEventsFlow.value = skipsByMetaPlan.values.flatten().map { skip ->
                LastMetaPlanRotationEventRow(
                    trainingPlanId = skip.trainingPlanId,
                    metaPlanId = skip.metaPlanId,
                    lastEventAt = skip.skippedAt
                )
            }
        }

        override fun getAllMetaPlans(): Flow<List<MetaTrainingPlanEntity>> {
            publish()
            return metaPlansFlow
        }

        override fun getAllMetaPlansWithItems(): Flow<List<MetaTrainingPlanWithItems>> =
            MutableStateFlow(metaPlans.values.map { plan ->
                MetaTrainingPlanWithItems(
                    metaPlan = plan,
                    items = itemsByMetaPlan[plan.id]?.sortedBy { it.orderIndex } ?: emptyList()
                )
            })

        override suspend fun getAllMetaPlansList(): List<MetaTrainingPlanEntity> =
            metaPlans.values.sortedBy { it.id }

        override suspend fun getAllMetaPlanItemsList(): List<MetaPlanItemEntity> =
            itemsByMetaPlan.values.flatten().sortedBy { it.id }

        override suspend fun getMetaPlanById(id: Long): MetaTrainingPlanEntity? = metaPlans[id]

        override suspend fun getMetaPlanWithItemsById(id: Long): MetaTrainingPlanWithItems? =
            metaPlans[id]?.let { plan ->
                MetaTrainingPlanWithItems(
                    metaPlan = plan,
                    items = itemsByMetaPlan[id]?.sortedBy { it.orderIndex } ?: emptyList()
                )
            }

        override suspend fun getItemsForMetaPlan(metaPlanId: Long): List<MetaPlanItemEntity> =
            itemsByMetaPlan[metaPlanId]?.sortedBy { it.orderIndex } ?: emptyList()

        override fun observeLastRotationEventPerMetaPlanSubPlan():
            Flow<List<LastMetaPlanRotationEventRow>> {
            publishRotationEvents()
            return rotationEventsFlow
        }

        override suspend fun getLastRotationEventsForMetaPlan(
            metaPlanId: Long
        ): List<LastMetaPlanRotationEventRow> =
            skipsByMetaPlan[metaPlanId]
                ?.map { skip ->
                    LastMetaPlanRotationEventRow(
                        trainingPlanId = skip.trainingPlanId,
                        metaPlanId = skip.metaPlanId,
                        lastEventAt = skip.skippedAt
                    )
                }
                ?: emptyList()

        override suspend fun insertMetaPlanSkip(skip: MetaPlanSkipEntity) {
            val id = nextSkipId++
            skipsByMetaPlan
                .getOrPut(skip.metaPlanId) { mutableListOf() }
                .add(skip.copy(id = id))
            publishRotationEvents()
        }

        override suspend fun getAllMetaPlanSkipsList(): List<MetaPlanSkipEntity> =
            skipsByMetaPlan.values.flatten().sortedBy { it.id }

        override suspend fun replaceAllMetaPlanSkips(skips: List<MetaPlanSkipEntity>) {
            skipsByMetaPlan.clear()
            skips.groupBy { it.metaPlanId }.forEach { (metaPlanId, list) ->
                skipsByMetaPlan[metaPlanId] = list.toMutableList()
            }
            publishRotationEvents()
        }

        override suspend fun deleteAllMetaPlanSkips() {
            skipsByMetaPlan.clear()
            publishRotationEvents()
        }

        override suspend fun insertMetaPlan(plan: MetaTrainingPlanEntity): Long {
            val id = nextMetaPlanId++
            metaPlans[id] = plan.copy(id = id)
            publish()
            return id
        }

        override suspend fun replaceAllMetaPlans(plans: List<MetaTrainingPlanEntity>) {
            metaPlans.clear()
            plans.forEach { metaPlans[it.id] = it }
            publish()
        }

        override suspend fun updateMetaPlan(plan: MetaTrainingPlanEntity) {
            metaPlans[plan.id] = plan
            publish()
        }

        override suspend fun insertItems(items: List<MetaPlanItemEntity>) {
            items.forEach { item ->
                val id = nextItemId++
                itemsByMetaPlan.getOrPut(item.metaPlanId) { mutableListOf() }
                    .add(item.copy(id = id))
            }
        }

        override suspend fun replaceAllItems(items: List<MetaPlanItemEntity>) {
            itemsByMetaPlan.clear()
            items.groupBy { it.metaPlanId }.forEach { (metaPlanId, list) ->
                itemsByMetaPlan[metaPlanId] = list.toMutableList()
            }
        }

        override suspend fun deleteItemsForMetaPlan(metaPlanId: Long) {
            itemsByMetaPlan[metaPlanId] = mutableListOf()
        }

        override suspend fun deleteAllMetaPlanItems() {
            itemsByMetaPlan.clear()
        }

        override suspend fun deleteMetaPlan(metaPlanId: Long) {
            operationOrder += "delete:$metaPlanId"
            metaPlans.remove(metaPlanId)
            itemsByMetaPlan.remove(metaPlanId)
            publish()
        }

        override suspend fun detachSessionsFromMetaPlan(metaPlanId: Long) {
            operationOrder += "detach:$metaPlanId"
        }

        override suspend fun deleteMetaPlanAndDetachSessions(metaPlanId: Long) {
            operationOrder += "atomic:$metaPlanId"
            super.deleteMetaPlanAndDetachSessions(metaPlanId)
        }

        override suspend fun deleteAllMetaPlans() {
            metaPlans.clear()
            publish()
        }

        override suspend fun replaceMetaPlanAndItems(
            plan: MetaTrainingPlanEntity,
            items: List<MetaPlanItemEntity>
        ): Long = super.replaceMetaPlanAndItems(plan, items)
    }
}
