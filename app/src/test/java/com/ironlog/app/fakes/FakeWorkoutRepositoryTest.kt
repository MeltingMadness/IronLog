package com.ironlog.app.fakes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeWorkoutRepositoryTest {

    private val repository = FakeWorkoutRepository()

    @Test
    fun `startWorkout returns existing active session id and ignores new planId`() = runTest {
        val firstId = repository.startWorkout(name = "Push Day", planId = 1L, metaPlanId = null)

        val secondId = repository.startWorkout(name = "Ignored", planId = 2L, metaPlanId = 9L)

        assertEquals(firstId, secondId)
        val active = repository.getActiveSession()
        assertEquals("Push Day", active?.name)
        assertEquals(1L, active?.planId)
    }

    @Test
    fun `startWorkout creates a new session when none is active`() = runTest {
        val id = repository.startWorkout(name = "Legs", planId = null, metaPlanId = null)

        val active = repository.getActiveSession()
        assertEquals(id, active?.id)
        assertEquals("Legs", active?.name)
    }

    @Test
    fun `startWorkout starts a new session after the active one finishes`() = runTest {
        val firstId = repository.startWorkout(name = "Push Day")
        repository.finishWorkout(firstId)

        val secondId = repository.startWorkout(name = "Pull Day")

        assertEquals(false, firstId == secondId)
        assertEquals("Pull Day", repository.getActiveSession()?.name)
    }

    @Test
    fun `deleteSession removes session and its sets`() = runTest {
        val sessionId = repository.startWorkout(name = "Push Day")
        repository.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 1L,
                sessionId = sessionId,
                exerciseId = 1L,
                setNumber = 1,
                reps = 5,
                weightKg = 100.0,
                isWarmup = false,
                completedAt = java.time.LocalDateTime.now()
            )
        )

        repository.deleteSession(sessionId)

        assertNull(repository.getSessionById(sessionId))
        assertEquals(emptyList<Any>(), repository.getSetsForSessionList(sessionId))
    }
}
