package com.ironlog.app.data.repository

import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.model.PreviousSessionScope
import com.ironlog.app.domain.model.RecordType
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.WorkoutCalculations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime

class WorkoutRepositoryImplTest {

    private val sessionDao: WorkoutSessionDao = mockk()
    private val setDao: WorkoutSetDao = mockk(relaxed = true)
    private val personalRecordDao: PersonalRecordDao = mockk(relaxed = true)
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val repository = WorkoutRepositoryImpl(sessionDao, setDao, personalRecordDao, transactionRunner)

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
            scope = PreviousSessionScope.Global
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
            scope = PreviousSessionScope.Global
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
            scope = PreviousSessionScope.SharedPlan(planId)
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
    fun `getPreviousSessionDataForExercises dispatches normal plan scope to context query`() = runTest {
        repository.getPreviousSessionDataForExercises(
            currentSessionId = 99L,
            exerciseIds = listOf(7L),
            scope = PreviousSessionScope.NormalPlan(3L)
        )

        coVerify {
            setDao.getMostRecentCompletedSetsForNormalPlanExercises(99L, listOf(7L), 3L)
        }
    }

    @Test
    fun `getPreviousSessionDataForExercises dispatches meta plan scope to context query`() = runTest {
        repository.getPreviousSessionDataForExercises(
            currentSessionId = 99L,
            exerciseIds = listOf(7L),
            scope = PreviousSessionScope.MetaPlan(3L, 8L)
        )

        coVerify {
            setDao.getMostRecentCompletedSetsForMetaPlanExercises(99L, listOf(7L), 3L, 8L)
        }
    }

    @Test
    fun `getPreviousSessionDataForExercises dispatches shared plan scope to plan query`() = runTest {
        repository.getPreviousSessionDataForExercises(
            currentSessionId = 99L,
            exerciseIds = listOf(7L),
            scope = PreviousSessionScope.SharedPlan(3L)
        )

        coVerify {
            setDao.getMostRecentCompletedSetsForPlanExercises(99L, listOf(7L), 3L)
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

    @Test
    fun `updateSet lowers MAX_WEIGHT personal record after lowering the set`() = runTest {
        val set = WorkoutSetEntity(
            id = 10L,
            sessionId = 2L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 5,
            weightKg = 60.0,
            isWarmup = false,
            completedAt = 1_000L
        )
        coEvery { setDao.getExerciseIdForSet(10L) } returns 1L
        coEvery { setDao.getSetsForExerciseList(1L) } returns listOf(set)
        coEvery { personalRecordDao.getRecord(1L, RecordType.MAX_WEIGHT.name) } returns
            PersonalRecordEntity(id = 9L, exerciseId = 1L, type = RecordType.MAX_WEIGHT.name, value = 100.0, achievedAt = 500L)

        repository.updateSet(set.toDomain())

        coVerify(exactly = 1) { setDao.update(match { it.weightKg == 60.0 }) }
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match { it.id == 9L && it.type == RecordType.MAX_WEIGHT.name && it.value == 60.0 }
            )
        }
    }

    @Test
    fun `deleteSet rebuilds MAX_WEIGHT personal record from remaining sets after delete`() = runTest {
        val setId = 10L
        val exerciseId = 1L
        coEvery { setDao.getExerciseIdForSet(setId) } returns exerciseId
        coEvery { setDao.getSetsForExerciseList(exerciseId) } returns listOf(
            WorkoutSetEntity(
                id = 11L,
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

        repository.deleteSet(setId)

        coVerifyOrder {
            setDao.getExerciseIdForSet(setId)
            setDao.deleteSet(setId)
        }
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match { it.id == 9L && it.type == RecordType.MAX_WEIGHT.name && it.value == 80.0 }
            )
        }
    }

    @Test
    fun `deleteSet removes personal records when it was the only work set`() = runTest {
        val setId = 10L
        val exerciseId = 1L
        coEvery { setDao.getExerciseIdForSet(setId) } returns exerciseId
        coEvery { setDao.getSetsForExerciseList(exerciseId) } returns emptyList()
        coEvery { personalRecordDao.getRecord(exerciseId, any()) } returns
            PersonalRecordEntity(id = 9L, exerciseId = exerciseId, type = RecordType.MAX_WEIGHT.name, value = 120.0, achievedAt = 500L)

        repository.deleteSet(setId)

        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_WEIGHT.name) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_REPS.name) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_E1RM.name) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_VOLUME.name) }
        coVerify(exactly = 0) { personalRecordDao.insert(any()) }
    }

    @Test
    fun `deleteSet does nothing when the set no longer exists`() = runTest {
        coEvery { setDao.getExerciseIdForSet(10L) } returns null

        repository.deleteSet(10L)

        coVerify(exactly = 0) { setDao.deleteSet(10L) }
        coVerify(exactly = 0) { personalRecordDao.getRecord(any(), any()) }
    }

    @Test
    fun `updateSet runs update and record rebuild through one transaction`() = runTest {
        val runner = TrackingTransactionRunner()
        val repo = WorkoutRepositoryImpl(sessionDao, setDao, personalRecordDao, runner)
        val set = WorkoutSetEntity(
            id = 10L,
            sessionId = 2L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 5,
            weightKg = 60.0,
            isWarmup = false,
            completedAt = 1_000L
        )
        coEvery { setDao.getExerciseIdForSet(10L) } returns 1L
        coEvery { setDao.getSetsForExerciseList(1L) } returns listOf(set)

        repo.updateSet(set.toDomain())

        assertEquals(1, runner.invocations)
        coVerify(exactly = 1) { setDao.update(any()) }
        coVerify(atLeast = 1) { personalRecordDao.insert(any()) }
    }

    @Test
    fun `deleteSession runs session delete and record rebuild through one transaction`() = runTest {
        val runner = TrackingTransactionRunner()
        val repo = WorkoutRepositoryImpl(sessionDao, setDao, personalRecordDao, runner)
        val sessionId = 5L
        val exerciseId = 1L
        coEvery { setDao.getExerciseIdsForSession(sessionId) } returns listOf(exerciseId)
        coEvery { sessionDao.deleteSession(sessionId) } returns Unit
        coEvery { setDao.getSetsForExerciseList(exerciseId) } returns emptyList()
        coEvery { personalRecordDao.getRecord(exerciseId, any()) } returns
            PersonalRecordEntity(id = 9L, exerciseId = exerciseId, type = RecordType.MAX_WEIGHT.name, value = 120.0, achievedAt = 500L)

        repo.deleteSession(sessionId)

        assertEquals(1, runner.invocations)
        coVerify(exactly = 1) { sessionDao.deleteSession(sessionId) }
        coVerify { personalRecordDao.deleteRecord(exerciseId, RecordType.MAX_WEIGHT.name) }
    }

    @Test
    fun `updateSet propagates errors thrown inside the transaction block`() = runTest {
        val boom = IllegalStateException("DB boom")
        coEvery { setDao.getExerciseIdForSet(10L) } returns 1L
        coEvery { setDao.update(any()) } throws boom
        val set = WorkoutSetEntity(
            id = 10L,
            sessionId = 2L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 5,
            weightKg = 60.0,
            isWarmup = false,
            completedAt = 1_000L
        )

        try {
            repository.updateSet(set.toDomain())
            fail("Expected updateSet to propagate the DAO failure")
        } catch (e: IllegalStateException) {
            assertEquals(boom, e)
        }

        coVerify(exactly = 0) { personalRecordDao.insert(any()) }
    }

    @Test
    fun `addSet rekalkuliert alle vier PR-Typen exakt in einer Transaktion`() = runTest {
        val runner = TrackingTransactionRunner()
        val repo = WorkoutRepositoryImpl(sessionDao, setDao, personalRecordDao, runner)
        val newSet = WorkoutSet(
            sessionId = 3L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 8,
            weightKg = 100.0,
            isWarmup = false,
            completedAt = LocalDateTime.of(2026, 8, 6, 12, 0)
        )
        // After the insert, the rebuild reads exactly these sets (existing 80x5 + the new 100x8).
        coEvery { setDao.getSetsForExerciseList(1L) } returns listOf(
            WorkoutSetEntity(
                id = 1L,
                sessionId = 2L,
                exerciseId = 1L,
                setNumber = 1,
                reps = 5,
                weightKg = 80.0,
                isWarmup = false,
                completedAt = 1_000L
            ),
            WorkoutSetEntity(
                id = 2L,
                sessionId = 3L,
                exerciseId = 1L,
                setNumber = 1,
                reps = 8,
                weightKg = 100.0,
                isWarmup = false,
                completedAt = 2_000L
            )
        )

        repo.addSet(newSet)

        assertEquals(1, runner.invocations)
        coVerify(exactly = 1) {
            setDao.insert(match { !it.isWarmup && it.weightKg == 100.0 && it.exerciseId == 1L })
        }
        coVerifyOrder {
            setDao.insert(any())
            setDao.getSetsForExerciseList(1L)
        }
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match {
                    it.type == RecordType.MAX_WEIGHT.name &&
                        it.value == 100.0 &&
                        it.achievedAt == 2_000L
                }
            )
        }
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match {
                    it.type == RecordType.MAX_REPS.name &&
                        it.value == 8.0 &&
                        it.achievedAt == 2_000L
                }
            )
        }
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match {
                    it.type == RecordType.MAX_E1RM.name &&
                        it.value == WorkoutCalculations.calculateE1RM(100.0, 8) &&
                        it.achievedAt == 2_000L
                }
            )
        }
        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match {
                    it.type == RecordType.MAX_VOLUME.name &&
                        it.value == 800.0 &&
                        it.achievedAt == 2_000L
                }
            )
        }
    }

    @Test
    fun `addSet setzt MAX_E1RM bei 1 Wiederholung auf das Gewicht`() = runTest {
        coEvery { setDao.getSetsForExerciseList(1L) } returns listOf(
            WorkoutSetEntity(
                id = 1L,
                sessionId = 2L,
                exerciseId = 1L,
                setNumber = 1,
                reps = 1,
                weightKg = 120.0,
                isWarmup = false,
                completedAt = 1_000L
            )
        )

        repository.addSet(
            WorkoutSet(
                sessionId = 2L,
                exerciseId = 1L,
                setNumber = 1,
                reps = 1,
                weightKg = 120.0,
                isWarmup = false
            )
        )

        coVerify(exactly = 1) {
            personalRecordDao.insert(
                match { it.type == RecordType.MAX_E1RM.name && it.value == 120.0 }
            )
        }
    }

    @Test
    fun `addSet behandelt Warmup-Satz ohne PR-Rekalkulation`() = runTest {
        val runner = TrackingTransactionRunner()
        val repo = WorkoutRepositoryImpl(sessionDao, setDao, personalRecordDao, runner)

        repo.addSet(
            WorkoutSet(
                sessionId = 3L,
                exerciseId = 1L,
                setNumber = 1,
                reps = 10,
                weightKg = 50.0,
                isWarmup = true
            )
        )

        assertEquals(1, runner.invocations)
        coVerify(exactly = 1) { setDao.insert(match { it.isWarmup }) }
        coVerify(exactly = 0) { setDao.getSetsForExerciseList(any()) }
        coVerify(exactly = 0) { personalRecordDao.getRecord(any(), any()) }
        coVerify(exactly = 0) { personalRecordDao.insert(any()) }
        coVerify(exactly = 0) { personalRecordDao.deleteRecord(any(), any()) }
    }

    @Test
    fun `addSet propagates errors thrown by insert and skips record rebuild`() = runTest {
        val boom = IllegalStateException("DB boom")
        coEvery { setDao.insert(any()) } throws boom

        try {
            repository.addSet(
                WorkoutSet(
                    sessionId = 3L,
                    exerciseId = 1L,
                    setNumber = 1,
                    reps = 8,
                    weightKg = 100.0,
                    isWarmup = false
                )
            )
            fail("Expected addSet to propagate the DAO failure")
        } catch (e: IllegalStateException) {
            assertEquals(boom, e)
        }

        coVerify(exactly = 0) { setDao.getSetsForExerciseList(any()) }
        coVerify(exactly = 0) { personalRecordDao.getRecord(any(), any()) }
        coVerify(exactly = 0) { personalRecordDao.insert(any()) }
    }

    private class TrackingTransactionRunner : TransactionRunner {
        var invocations = 0

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            invocations++
            return block()
        }
    }
}
