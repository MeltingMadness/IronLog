package com.ironlog.app.data.repository

import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.ReadinessDataDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ReadinessDataEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.shared.model.MuscleGroup
import com.ironlog.shared.readinessdata.ReadinessCheckIn
import com.ironlog.shared.readinessdata.ReadinessData
import com.ironlog.shared.readinessdata.ReadinessDataValidationException
import com.ironlog.shared.readinessdata.SetIntention
import com.ironlog.shared.readinessdata.SetIntentionRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Targeted coverage for the readiness write paths that the Android UI now depends on:
 * editing and deleting a single day, recording an intention only while its set exists,
 * and staying fail-closed when the stored row is corrupt.
 */
class ReadinessRepositoryImplTest {

    private val day11 = LocalDate(2026, 9, 11)
    private val day12 = LocalDate(2026, 9, 12)

    @Test
    fun `upsert check-in replaces the same day and delete removes exactly that day`() {
        val harness = Harness()

        runBlocking {
            harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 3))
            harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day12, sleepQuality = 4))
            harness.repository.upsertCheckIn(
                ReadinessCheckIn(
                    localDate = day11,
                    energy = 5,
                    muscleSoreness = mapOf(MuscleGroup.BEINE to 2)
                )
            )
        }

        val replaced = runBlocking { harness.repository.getReadinessData() }
        assertEquals(listOf(day11, day12), replaced.checkIns.map { it.localDate })
        assertEquals(5, replaced.checkIns.first().energy)
        assertNull("the overwritten answer must not survive", replaced.checkIns.first().sleepQuality)

        runBlocking { harness.repository.deleteCheckIn(day11) }

        val remaining = runBlocking { harness.repository.getReadinessData() }.checkIns
        assertEquals(1, remaining.size)
        assertEquals(day12, remaining.single().localDate)
        assertEquals(4, remaining.single().sleepQuality)
    }

    @Test
    fun `deleting a day without a check-in is a no-op`() {
        val harness = Harness()
        runBlocking { harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 3)) }

        runBlocking { harness.repository.deleteCheckIn(day12) }

        assertEquals(1, runBlocking { harness.repository.getReadinessData() }.checkIns.size)
    }

    @Test
    fun `an unanswered check-in is refused before anything is written`() {
        val harness = Harness()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11)) }
        }

        assertNull("an empty entry must never become a stored row", harness.dao.payload)
    }

    @Test
    fun `an intention for a missing set fails loudly instead of silently succeeding`() {
        val harness = Harness()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                harness.repository.setSetIntention(20L, SetIntention.PLANNED_FAILURE, "geplant")
            }
        }

        assertNull("no orphaned intention may be persisted", harness.dao.payload)
    }

    @Test
    fun `clearing an intention that was never set writes nothing`() {
        val harness = Harness(existingSetIds = mutableSetOf(20L))

        runBlocking { harness.repository.setSetIntention(20L, SetIntention.UNKNOWN) }

        assertNull("a no-op clear must not create a readiness row", harness.dao.payload)
    }

    @Test
    fun `an intention for an existing set is stored and UNKNOWN clears it again`() {
        val harness = Harness(existingSetIds = mutableSetOf(20L))

        runBlocking {
            harness.repository.setSetIntention(20L, SetIntention.UNEXPECTED_TARGET_MISS, "Ziel verfehlt")
        }

        val stored = runBlocking { harness.repository.getReadinessData() }
        assertEquals(
            listOf(
                SetIntentionRecord(
                    setId = 20L,
                    intention = SetIntention.UNEXPECTED_TARGET_MISS,
                    note = "Ziel verfehlt"
                )
            ),
            stored.setIntentions
        )
        val observed = runBlocking { harness.repository.observeSetIntentions().first() }
        assertEquals(SetIntention.UNEXPECTED_TARGET_MISS, observed.getValue(20L))

        runBlocking { harness.repository.setSetIntention(20L, SetIntention.UNKNOWN) }

        assertTrue(runBlocking { harness.repository.getReadinessData() }.setIntentions.isEmpty())
    }

    @Test
    fun `pruning drops only the intentions of deleted sets`() {
        val harness = Harness(existingSetIds = mutableSetOf(20L, 21L))

        runBlocking {
            harness.repository.setSetIntention(20L, SetIntention.PLANNED_FAILURE)
            harness.repository.setSetIntention(21L, SetIntention.UNEXPECTED_TARGET_MISS)
            harness.repository.pruneSetIntentions(listOf(20L))
        }

        val remaining = runBlocking { harness.repository.getReadinessData() }.setIntentions
        assertEquals(listOf(21L), remaining.map { it.setId })
    }

    @Test
    fun `a written document round trips unchanged through the codec`() {
        val harness = Harness()
        val source = ReadinessData(
            checkIns = listOf(
                ReadinessCheckIn(
                    localDate = day11,
                    energy = 3,
                    muscleSoreness = mapOf(MuscleGroup.BEINE to 4),
                    recordedAtEpochMillis = 1_757_500_000_000
                )
            ),
            setIntentions = listOf(
                SetIntentionRecord(setId = 20L, intention = SetIntention.PLANNED_FAILURE)
            )
        )

        runBlocking { harness.repository.replaceReadinessData(source) }

        assertEquals(source, runBlocking { harness.repository.getReadinessData() })
    }

    @Test
    fun `a new check-in stamps recordedAt from the clock`() {
        val harness = Harness(now = 1_000L)

        runBlocking { harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 3)) }

        val stored = runBlocking { harness.repository.getReadinessData() }.checkIns.single()
        assertEquals(1_000L, stored.recordedAtEpochMillis)
    }

    @Test
    fun `editing a check-in keeps recordedAt and advances updatedAt`() {
        val harness = Harness(now = 1_000L)
        runBlocking { harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 3)) }

        harness.now = 5_000L
        runBlocking { harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 5)) }

        val edited = runBlocking { harness.repository.getReadinessData() }.checkIns.single()
        assertEquals(5, edited.energy)
        assertEquals("an edit must not lose the original record time", 1_000L, edited.recordedAtEpochMillis)
        assertEquals("an edit must be visible as a later update", 5_000L, edited.updatedAtEpochMillis)
    }

    @Test
    fun `an explicit recordedAt survives an edit instead of being overwritten`() {
        val harness = Harness(now = 9_000L)
        runBlocking {
            harness.repository.upsertCheckIn(
                ReadinessCheckIn(
                    localDate = day11,
                    energy = 3,
                    recordedAtEpochMillis = 42L,
                    updatedAtEpochMillis = 42L
                )
            )
        }

        harness.now = 10_000L
        runBlocking { harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 4)) }

        val edited = runBlocking { harness.repository.getReadinessData() }.checkIns.single()
        assertEquals(42L, edited.recordedAtEpochMillis)
        assertEquals(10_000L, edited.updatedAtEpochMillis)
    }

    @Test
    fun `a corrupted stored document fails closed instead of reading as empty`() {
        val harness = Harness()
        harness.dao.payload = "{ not json"

        assertThrows(ReadinessDataValidationException::class.java) {
            runBlocking { harness.repository.getReadinessData() }
        }
    }

    @Test
    fun `an invalid document is refused before it reaches the stored row`() {
        val harness = Harness()
        runBlocking {
            harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 3))
        }

        assertThrows(ReadinessDataValidationException::class.java) {
            runBlocking {
                harness.repository.replaceReadinessData(
                    ReadinessData(checkIns = listOf(ReadinessCheckIn(localDate = day12, energy = 99)))
                )
            }
        }

        val stillStored = runBlocking { harness.repository.getReadinessData() }
        assertEquals(day11, stillStored.checkIns.single().localDate)
    }

    @Test
    fun `observe keys check-ins by date and rejects a corrupt payload`() {
        val harness = Harness()
        runBlocking {
            harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day11, energy = 3))
            harness.repository.upsertCheckIn(ReadinessCheckIn(localDate = day12, stress = 2))
        }

        val byDate = runBlocking { harness.repository.observeCheckIns().first() }
        assertEquals(setOf(day11, day12), byDate.keys)
        assertEquals(3, byDate.getValue(day11).energy)
        assertEquals(2, byDate.getValue(day12).stress)
    }

    private class Harness(
        val existingSetIds: MutableSet<Long> = mutableSetOf(),
        var now: Long = 1_000L
    ) {
        val dao = FakeReadinessDataDao()
        private val workoutSetDao = mockk<WorkoutSetDao>(relaxed = true)

        val repository = ReadinessRepositoryImpl(
            transactionRunner = object : TransactionRunner {
                override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
            },
            readinessDataDao = dao,
            workoutSetDao = workoutSetDao,
            nowEpochMillis = { now }
        )

        init {
            coEvery { workoutSetDao.getSetById(any()) } answers {
                val id = firstArg<Long>()
                if (id in existingSetIds) WorkoutSetEntity(
                    id = id,
                    sessionId = 10L,
                    exerciseId = 1L,
                    setNumber = 1,
                    reps = 8,
                    weightKg = 80.0,
                    setType = "NORMAL",
                    completedAt = 1200L
                ) else null
            }
        }
    }

    private class FakeReadinessDataDao : ReadinessDataDao {
        var payload: String? = null

        override fun observePayload(): Flow<String?> = flowOf(payload)

        override suspend fun getPayload(): String? = payload

        override suspend fun upsert(entity: ReadinessDataEntity) {
            payload = entity.payload
        }

        override suspend fun deleteAll() {
            payload = null
        }
    }
}
