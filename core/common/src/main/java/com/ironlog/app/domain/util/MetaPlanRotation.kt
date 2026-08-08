package com.ironlog.app.domain.util

/**
 * Orders the sub-plans of a meta plan by their last rotation event.
 *
 * Plans without any event come first (a fresh rotation starts at the first item),
 * then plans with the oldest event, with item order breaking timestamp ties.
 */
fun resolveMetaPlanRotation(
    orderedPlanIds: List<Long>,
    lastEventAtByPlanId: Map<Long, Long>
): List<Long> = orderedPlanIds
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<Long>> { lastEventAtByPlanId[it.value] != null }
            .thenBy { lastEventAtByPlanId[it.value] ?: Long.MIN_VALUE }
            .thenBy { it.index }
    )
    .map { it.value }
