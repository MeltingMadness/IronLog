package com.ironlog.app.fakes

import com.ironlog.app.domain.model.MetaPlanRotationEvent
import com.ironlog.app.domain.model.MetaTrainingPlan
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.util.resolveMetaPlanRotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FakeMetaTrainingPlanRepository(
    private val workoutRepository: FakeWorkoutRepository? = null
) : MetaTrainingPlanRepository {

    private val metaPlans = MutableStateFlow<List<MetaTrainingPlan>>(emptyList())
    private val skips = MutableStateFlow<List<MetaPlanRotationEvent>>(emptyList())
    private var nextId = 1L
    private var nextEventAt = 1L

    override fun getAllMetaPlans(): Flow<List<MetaTrainingPlan>> = metaPlans

    override fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<MetaPlanRotationEvent>> {
        val sessionEvents = workoutRepository
            ?.observeLastSessionPerMetaPlanSubPlan()
            ?.map { sessions ->
                sessions.map {
                    MetaPlanRotationEvent(
                        trainingPlanId = it.planId,
                        metaPlanId = it.metaPlanId,
                        lastEventAt = it.lastStartTime
                    )
                }
            }
            ?: MutableStateFlow(emptyList())

        return combine(metaPlans, skips, sessionEvents) { plans, skipEvents, sessionEventList ->
            val activeMetaPlanIds = plans.mapTo(mutableSetOf()) { it.id }
            (skipEvents + sessionEventList)
                .filter { it.metaPlanId in activeMetaPlanIds }
                .groupBy { it.trainingPlanId to it.metaPlanId }
                .map { (key, events) ->
                    MetaPlanRotationEvent(
                        trainingPlanId = key.first,
                        metaPlanId = key.second,
                        lastEventAt = events.maxOf { it.lastEventAt }
                    )
                }
        }
    }

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
        skips.value = skips.value.filterNot { it.metaPlanId == metaPlanId }
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

        val anchors = observeLastRotationEventPerMetaPlanSubPlan()
            .first()
            .associate { it.trainingPlanId to it.lastEventAt }
        val current = resolveMetaPlanRotation(orderedIds, anchors).firstOrNull()
        if (current != expectedTrainingPlanId) return false

        val eventAt = maxOf(anchors.values.maxOrNull()?.plus(1L) ?: nextEventAt, nextEventAt)
        nextEventAt = eventAt + 1L
        skips.value = skips.value + MetaPlanRotationEvent(
            trainingPlanId = expectedTrainingPlanId,
            metaPlanId = metaPlanId,
            lastEventAt = eventAt
        )
        return true
    }
}
