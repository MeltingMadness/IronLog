package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.model.RecordType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutRepositoryImplTest {

    private val sessionDao: WorkoutSessionDao = mockk()
    private val setDao: WorkoutSetDao = mockk(relaxed = true)
    private val personalRecordDao: PersonalRecordDao = mockk(relaxed = true)
    private val repository = WorkoutRepositoryImpl(sessionDao, setDao, personalRecordDao)

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

    @Test
    fun `deleteSession lowers personal record to remaining set after deletion`() = runTest {
        val sessionId = 5L
        val exerciseId = 1L
        coEvery { setDao.getExerciseIdsForSession(sessionId) } returns listOf(exerciseId)
        coEvery { sessionDao.deleteSession(sessionId) } returns Unit
        // After the session (and its 120kg PR set) is deleted, only an 80kg set remains.
        coEvery { setDao.getSetsForExerciseList(exerciseId) } returns listOf(
            WorkoutSetEntity(
                id = 1L,
                sessionId = 2L,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 5,
                weightKg = 80.0,
                isWarmup = false,
                completedAt = 1_000L
            )
        )
        coEvery { personalRecordDao.getRecord(exerciseId, RecordType.MAX_WEIGHT.name) } returns
            PersonalRecordEntity(id = 9L, exerciseId = exerciseId, type = RecordType.MAX_WEIGHT.name, value = 120.0, achievedAt = 500L)
        coEvery { personalRecordDao.getRecord(exerciseId, RecordType.MAX_REPS.name) } returns null
        coEvery { personalRecordDao.getRecord(exerciseId, RecordType.MAX_E1RM.name) } returns null
        coEvery { personalRecordDao.getRecord(exerciseId, RecordType.MAX_VOLUME.name) } returns null

        repository.deleteSession(sessionId)

        // The stale 120kg MAX_WEIGHT record (id 9) is updated in place to the new 80kg max.
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match { it.id == 9L && it.type == RecordType.MAX_WEIGHT.name && it.value == 80.0 }
            )
        }
    }

    @Test
    fun `deleteSession removes personal record when no sets remain for exercise`() = runTest {
        val sessionId = 5L
        val exerciseId = 1L
        coEvery { setDao.getExerciseIdsForSession(sessionId) } returns listOf(exerciseId)
        coEvery { sessionDao.deleteSession(sessionId) } returns Unit
        coEvery { setDao.getSetsForExerciseList(exerciseId) } returns emptyList()
        coEvery { personalRecordDao.getRecord(exerciseId, any()) } returns
            PersonalRecordEntity(id = 9L, exerciseId = exerciseId, type = RecordType.MAX_WEIGHT.name, value = 120.0, achievedAt = 500L)

        repository.deleteSession(sessionId)

        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_WEIGHT.name) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_REPS.name) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_E1RM.name) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_VOLUME.name) }
        coVerify(exactly = 0) { personalRecordDao.insert(any()) }
    }

    @Test
    fun `deleteSession does not touch personal records for unaffected exercises`() = runTest {
        val sessionId = 5L
        coEvery { setDao.getExerciseIdsForSession(sessionId) } returns emptyList()
        coEvery { sessionDao.deleteSession(sessionId) } returns Unit

        repository.deleteSession(sessionId)

        coVerify(exactly = 0) { personalRecordDao.getRecord(any(), any()) }
        coVerify(exactly = 0) { personalRecordDao.deleteRecord(any(), any()) }
    }
}
