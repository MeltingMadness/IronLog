package com.ironlog.app.domain.deload

import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.shared.readiness.DeloadState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SessionDeloadContextTest {

    private val start = LocalDateTime.of(2026, 9, 11, 18, 0)

    private fun session(isDeload: Boolean?) =
        WorkoutSession(startTime = start, isDeload = isDeload)

    @Test
    fun `planned deload maps to PLANNED_DELOAD`() {
        assertEquals(DeloadState.PLANNED_DELOAD, session(true).deloadState())
    }

    @Test
    fun `explicitly non-deload session maps to NONE`() {
        assertEquals(DeloadState.NONE, session(false).deloadState())
    }

    @Test
    fun `legacy session without stored context stays UNKNOWN`() {
        assertEquals(DeloadState.UNKNOWN, session(null).deloadState())
    }
}
