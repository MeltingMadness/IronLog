package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.dao.MetaTrainingPlanWithItems
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private class FakeMetaTrainingPlanDao : MetaTrainingPlanDao {
        val metaPlans = linkedMapOf<Long, MetaTrainingPlanEntity>()
        val itemsByMetaPlan = linkedMapOf<Long, MutableList<MetaPlanItemEntity>>()
        val operationOrder = mutableListOf<String>()
        private val metaPlansFlow = MutableStateFlow<List<MetaTrainingPlanEntity>>(emptyList())
        var nextMetaPlanId = 100L
        var nextItemId = 1000L

        private fun publish() {
            metaPlansFlow.value = metaPlans.values.sortedByDescending { it.createdAt }
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
