package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutRepositoryImplTest {

    private val sessionDao: WorkoutSessionDao = mockk()
    private val setDao: WorkoutSetDao = mockk(relaxed = true)
    private val repository = WorkoutRepositoryImpl(sessionDao, setDao)

    @Test
    fun `startWorkout returns existing active session id and does not insert`() = runTest {
        coEvery { sessionDao.getActiveSession() } returns WorkoutSessionEntity(
            id = 42L,
            startTime = 1_700_000_000_000,
            name = "Laufendes Training"
        )
        coEvery { sessionDao.insert(any()) } returns 99L

        val result = repository.startWorkout("Neues Training")

        assertEquals(42L, result)
        coVerify(exactly = 0) { sessionDao.insert(any()) }
    }

    @Test
    fun `startWorkout inserts new session when no active session exists`() = runTest {
        coEvery { sessionDao.getActiveSession() } returns null
        coEvery { sessionDao.insert(any()) } returns 99L

        val result = repository.startWorkout("Neues Training")

        assertEquals(99L, result)
        coVerify(exactly = 1) {
            sessionDao.insert(match { entity -> entity.name == "Neues Training" })
        }
    }

    @Test
    fun `getPreviousSessionDataForExercises returns empty map for empty exercise ids`() = runTest {
        val result = repository.getPreviousSessionDataForExercises(
            currentSessionId = 1L,
            exerciseIds = emptyList(),
            planId = null
        )

        assertEquals(emptyMap<Long, com.ironlog.app.domain.model.PreviousExerciseSession>(), result)
        coVerify(exactly = 0) { setDao.getMostRecentCompletedSetsForExercises(any(), any()) }
    }

    @Test
    fun `getPreviousSessionDataForExercises maps session and last work set`() = runTest {
        val currentSessionId = 1L
        val exerciseId = 11L
        val previousSessionId = 42L

        coEvery {
            setDao.getMostRecentCompletedSetsForExercises(currentSessionId, listOf(exerciseId))
        } returns listOf(
            WorkoutSetEntity(
                id = 100L,
                sessionId = previousSessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 10,
                weightKg = 30.0,
                isWarmup = true,
                completedAt = 1_000L
            ),
            WorkoutSetEntity(
                id = 101L,
                sessionId = previousSessionId,
                exerciseId = exerciseId,
                setNumber = 2,
                reps = 6,
                weightKg = 80.0,
                isWarmup = false,
                completedAt = 2_000L
            )
        )
        coEvery { sessionDao.getSessionsByIds(listOf(previousSessionId)) } returns listOf(
            WorkoutSessionEntity(
                id = previousSessionId,
                startTime = 1_700_000_000_000,
                endTime = 1_700_000_300_000
            )
        )

        val result = repository.getPreviousSessionDataForExercises(
            currentSessionId = currentSessionId,
            exerciseIds = listOf(exerciseId),
            planId = null
        )

        val previous = result[exerciseId]
        assertEquals(1, result.size)
        assertEquals(previousSessionId, previous?.sessionId)
        assertEquals(80.0, previous?.lastWorkSetWeightKg ?: 0.0, 0.01)
        assertEquals(2, previous?.sets?.size)
    }

    @Test
    fun `getPreviousSessionDataForExercises uses plan scoped query when plan id is provided`() = runTest {
        val currentSessionId = 1L
        val exerciseId = 11L
        val planId = 55L
        val previousSessionId = 42L

        coEvery {
            setDao.getMostRecentCompletedSetsForPlanExercises(currentSessionId, listOf(exerciseId), planId)
        } returns listOf(
            WorkoutSetEntity(
                id = 101L,
                sessionId = previousSessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 6,
                weightKg = 80.0,
                isWarmup = false,
                completedAt = 2_000L
            )
        )
        coEvery { sessionDao.getSessionsByIds(listOf(previousSessionId)) } returns listOf(
            WorkoutSessionEntity(
                id = previousSessionId,
                startTime = 1_700_000_000_000,
                endTime = 1_700_000_300_000,
                planId = planId
            )
        )

        val result = repository.getPreviousSessionDataForExercises(
            currentSessionId = currentSessionId,
            exerciseIds = listOf(exerciseId),
            planId = planId
        )

        assertEquals(previousSessionId, result[exerciseId]?.sessionId)
        coVerify(exactly = 1) {
            setDao.getMostRecentCompletedSetsForPlanExercises(currentSessionId, listOf(exerciseId), planId)
        }
        coVerify(exactly = 0) {
            setDao.getMostRecentCompletedSetsForExercises(any(), any())
        }
    }
}
