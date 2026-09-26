package com.ironlog.app.presentation.workout

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerAdjustTest {
    private val start = Instant.parse("2026-09-26T10:00:00Z")

    @Test
    fun `plus 30 s verlaengert die Pause`() {
        val timer = RestTimerUi(startTime = start, durationSeconds = 120)
        assertEquals(150, timer.adjustedBy(30, start.plusSeconds(10)).durationSeconds)
    }

    @Test
    fun `minus 15 s verkuerzt die Pause`() {
        val timer = RestTimerUi(startTime = start, durationSeconds = 120)
        assertEquals(105, timer.adjustedBy(-15, start.plusSeconds(10)).durationSeconds)
    }

    @Test
    fun `verkuerzen endet spaetestens jetzt`() {
        val timer = RestTimerUi(startTime = start, durationSeconds = 120)
        assertEquals(110, timer.adjustedBy(-60, start.plusSeconds(110)).durationSeconds)
    }

    @Test
    fun `direkt nach dem Start bleibt mindestens eine Sekunde`() {
        val timer = RestTimerUi(startTime = start, durationSeconds = 10)
        assertEquals(1, timer.adjustedBy(-15, start).durationSeconds)
    }

    @Test
    fun `hochzaehlender Timer bleibt unveraendert`() {
        val timer = RestTimerUi(startTime = start, durationSeconds = 0)
        assertEquals(timer, timer.adjustedBy(30, start.plusSeconds(5)))
    }
}
