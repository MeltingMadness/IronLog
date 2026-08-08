package com.ironlog.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MetaPlanRotationTest {

    @Test
    fun `never used plan comes first and removed plans are ignored`() {
        val result = resolveMetaPlanRotation(
            orderedPlanIds = listOf(10L, 20L, 30L),
            lastEventAtByPlanId = mapOf(10L to 300L, 20L to 100L, 99L to 0L)
        )

        assertEquals(listOf(30L, 20L, 10L), result)
    }

    @Test
    fun `oldest event is next in rotation`() {
        val result = resolveMetaPlanRotation(
            orderedPlanIds = listOf(10L, 20L, 30L),
            lastEventAtByPlanId = mapOf(10L to 300L, 20L to 100L, 30L to 200L)
        )

        assertEquals(listOf(20L, 30L, 10L), result)
    }

    @Test
    fun `equal timestamps keep item order`() {
        val result = resolveMetaPlanRotation(
            orderedPlanIds = listOf(10L, 20L, 30L),
            lastEventAtByPlanId = mapOf(10L to 100L, 20L to 100L, 30L to 200L)
        )

        assertEquals(listOf(10L, 20L, 30L), result)
    }

    @Test
    fun `skip event newer than session delays skipped plan`() {
        val result = resolveMetaPlanRotation(
            orderedPlanIds = listOf(10L, 20L, 30L),
            lastEventAtByPlanId = mapOf(10L to 100L, 20L to 300L)
        )

        assertEquals(listOf(30L, 10L, 20L), result)
    }
}
