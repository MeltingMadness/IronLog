package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.model.MetaTrainingPlanItem
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class MetaTrainingPlanRepositoryImpl(
    private val metaTrainingPlanDao: MetaTrainingPlanDao
) : MetaTrainingPlanRepository {

    override fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>> {
        return metaTrainingPlanDao.getAllMetaPlansWithItems().map { plans ->
            plans.map { relation ->
                val normalizedItems = normalizeItems(
                    relation.items
                        .sortedBy { it.orderIndex }
                        .map { it.toDomain() }
                )
                relation.metaPlan.toDomain(
                    items = normalizedItems.map { item ->
                        MetaPlanItemEntity.fromDomain(metaPlanId = relation.metaPlan.id, item = item)
                    }
                )
            }
        }
    }

    override suspend fun getMetaPlanById(id: Long): MetaTrainingPlan? {
        val relation = metaTrainingPlanDao.getMetaPlanWithItemsById(id) ?: return null
        val normalizedItems = normalizeItems(
            relation.items
                .sortedBy { it.orderIndex }
                .map { it.toDomain() }
        ).map { item ->
            MetaPlanItemEntity.fromDomain(metaPlanId = relation.metaPlan.id, item = item)
        }
        return relation.metaPlan.toDomain(normalizedItems)
    }

    override suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long {
        val nowEpochMillis = EpochConverter.toLong(LocalDateTime.now())

        val entity = if (plan.id == 0L) {
            MetaTrainingPlanEntity(
                id = 0L,
                name = plan.name,
                createdAt = nowEpochMillis
            )
        } else {
            val existing = metaTrainingPlanDao.getMetaPlanById(plan.id)
                ?: throw IllegalStateException("Meta-Plan ${plan.id} existiert nicht")
            MetaTrainingPlanEntity(
                id = plan.id,
                name = plan.name,
                createdAt = existing.createdAt
            )
        }

        val itemEntities = normalizeItems(plan.items).map { item ->
            MetaPlanItemEntity.fromDomain(
                metaPlanId = 0L,
                item = item.copy(id = 0L)
            )
        }

        return metaTrainingPlanDao.replaceMetaPlanAndItems(entity, itemEntities)
    }

    override suspend fun deleteMetaPlan(metaPlanId: Long) {
        metaTrainingPlanDao.deleteMetaPlan(metaPlanId)
    }

    private fun normalizeItems(items: List<MetaTrainingPlanItem>) =
        items.mapIndexed { index, item -> item.copy(orderIndex = index) }
}
