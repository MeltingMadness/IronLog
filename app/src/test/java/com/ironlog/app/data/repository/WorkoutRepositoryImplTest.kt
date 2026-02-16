package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
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
}
