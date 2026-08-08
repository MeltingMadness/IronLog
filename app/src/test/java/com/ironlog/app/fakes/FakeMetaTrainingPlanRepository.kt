package com.ironlog.app.fakes

import com.ironlog.app.domain.model.MetaPlanRotationEvent
import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.util.resolveMetaPlanRotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMetaTrainingPlanRepository : MetaTrainingPlanRepository {

    private val metaPlans = MutableStateFlow<List<MetaTrainingPlan>>(emptyList())
    private val rotationEvents = MutableStateFlow<List<MetaPlanRotationEvent>>(emptyList())
    private var nextId = 1L
    private var nextEventAt = 1L

    override fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>> = metaPlans

    override fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>> =
        rotationEvents

    override suspend fun getMetaPlanById(id: Long): MetaTrainingPlan? =
        metaPlans.value.find { it.id == id }

    override suspend fun saveMetaPlan(plan: MetaTrainingPlan): Long {
        val id = if (plan.id == 0L) nextId++ else plan.id
        val saved = plan.copy(id = id)
        metaPlans.value = metaPlans.value
            .filterNot { it.id == id } + saved
        return id
    }

    override suspend fun deleteMetaPlan(metaPlanId: Long) {
        metaPlans.value = metaPlans.value.filterNot { it.id == metaPlanId }
        rotationEvents.value = rotationEvents.value.filterNot { it.metaPlanId == metaPlanId }
    }

    override suspend fun skipCurrentSubPlan(
        metaPlanId: Long,
        expectedTrainingPlanId: Long
    ): Boolean {
        val metaPlan = metaPlans.value.find { it.id == metaPlanId } ?: return false
        val orderedIds = metaPlan.items
            .sortedBy { it.orderIndex }
            .map { it.trainingPlanId }
        if (orderedIds.size < 2 || expectedTrainingPlanId !in orderedIds) return false

        val anchors = rotationEvents.value
            .filter { it.metaPlanId == metaPlanId }
            .associate { it.trainingPlanId to it.lastEventAt }
        val current = resolveMetaPlanRotation(orderedIds, anchors).firstOrNull()
        if (current != expectedTrainingPlanId) return false

        val eventAt = maxOf(anchors.values.maxOrNull()?.plus(1L) ?: nextEventAt, nextEventAt)
        nextEventAt = eventAt + 1L
        rotationEvents.value = rotationEvents.value + MetaPlanRotationEvent(
            trainingPlanId = expectedTrainingPlanId,
            metaPlanId = metaPlanId,
            lastEventAt = eventAt
        )
        return true
    }
}
