package com.ironlog.app.fakes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `finishWorkout berechnet durationSeconds aus startTime`() = runTest {
        val start = java.time.LocalDateTime.now().minusMinutes(30)
        repository.addSession(
            com.ironlog.app.domain.model.WorkoutSession(id = 5L, startTime = start),
            isActive = true
        )

        repository.finishWorkout(5L)

        val finished = repository.getSessionById(5L)
        assertNotNull(finished?.endTime)
        assertTrue((finished?.durationSeconds ?: 0L) >= 29 * 60)
        assertTrue((finished?.durationSeconds ?: Long.MAX_VALUE) < 31 * 60)
    }

    @Test
    fun `getTotalVolumeForSession ignoriert Warmup-Saetze`() = runTest {
        val sessionId = repository.startWorkout(name = "Vol")
        repository.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 1L,
                sessionId = sessionId,
                exerciseId = 1L,
                setNumber = 1,
                reps = 10,
                weightKg = 20.0,
                isWarmup = true,
                completedAt = java.time.LocalDateTime.now()
            )
        )
        repository.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 2L,
                sessionId = sessionId,
                exerciseId = 1L,
                setNumber = 2,
                reps = 5,
                weightKg = 100.0,
                isWarmup = false,
                completedAt = java.time.LocalDateTime.now()
            )
        )

        assertEquals(500.0, repository.getTotalVolumeForSession(sessionId), 0.01)
    }

    @Test
    fun `getPagedCompletedWorkoutSummaries berechnet Werte aus echten Sets`() = runTest {
        val completedSessionId = repository.startWorkout(name = "Done")
        repository.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 1L,
                sessionId = completedSessionId,
                exerciseId = 1L,
                setNumber = 1,
                reps = 5,
                weightKg = 100.0,
                isWarmup = false,
                completedAt = java.time.LocalDateTime.now()
            )
        )
        repository.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 2L,
                sessionId = completedSessionId,
                exerciseId = 1L,
                setNumber = 2,
                reps = 10,
                weightKg = 20.0,
                isWarmup = true,
                completedAt = java.time.LocalDateTime.now()
            )
        )
        repository.addSetDirectly(
            com.ironlog.app.domain.model.WorkoutSet(
                id = 3L,
                sessionId = completedSessionId,
                exerciseId = 2L,
                setNumber = 1,
                reps = 8,
                weightKg = 40.0,
                isWarmup = false,
                completedAt = java.time.LocalDateTime.now()
            )
        )
        repository.finishWorkout(completedSessionId)

        val summaries = repository.completedWorkoutSummaries()

        assertEquals(1, summaries.size)
        val summary = summaries.single()
        assertEquals(completedSessionId, summary.session.id)
        assertEquals(2, summary.exerciseCount)
        assertEquals(3, summary.setCount)
        // Warmup (10x20) is excluded from the volume.
        assertEquals(820.0, summary.totalVolume, 0.01)
    }

    @Test
    fun `getCompletedSessionCountSince respektiert sinceEpochMillis`() = runTest {
        val now = java.time.LocalDateTime.now()
        repository.addSession(
            com.ironlog.app.domain.model.WorkoutSession(
                id = 1L,
                startTime = now.minusDays(2),
                endTime = now.minusDays(2).plusHours(1)
            ),
            isActive = false
        )
        repository.addSession(
            com.ironlog.app.domain.model.WorkoutSession(
                id = 2L,
                startTime = now.minusHours(1),
                endTime = now
            ),
            isActive = false
        )
        repository.addSession(
            com.ironlog.app.domain.model.WorkoutSession(id = 3L, startTime = now),
            isActive = true
        )

        assertEquals(2, repository.getCompletedSessionCountSince(0L))
        val oneDayAgoMillis = now.minusDays(1)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(1, repository.getCompletedSessionCountSince(oneDayAgoMillis))
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
